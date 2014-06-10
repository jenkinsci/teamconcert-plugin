/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.common.TeamBuildException;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.publishing.WorkItemPublisher;
import com.ibm.team.build.internal.scm.AcceptReport;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.build.internal.scm.RepositoryManager;
import com.ibm.team.build.internal.scm.SourceControlUtility;
import com.ibm.team.filesystem.client.FileSystemCore;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.ISandbox;
import com.ibm.team.filesystem.client.ISharingManager;
import com.ibm.team.filesystem.client.internal.SharingManager;
import com.ibm.team.filesystem.client.internal.copyfileareas.ICorruptCopyFileAreaEvent;
import com.ibm.team.filesystem.client.internal.copyfileareas.ICorruptCopyFileAreaListener;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.ServerVersionCheckException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.transport.client.AuthenticationException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IWorkspaceSearchCriteria;

/**
 * A connection to the Jazz repository.
 */
@SuppressWarnings("restriction")
public class RepositoryConnection {

    private static final Logger LOGGER = Logger.getLogger(RepositoryConnection.class.getName());

	private final AbstractBuildClient fBuildClient;
	private final ConnectionDetails fConnectionDetails;
	private final RepositoryManager fRepositoryManager;
	private final ITeamRepository fRepository;
	private BuildConnection fBuildConnection;
	
	public RepositoryConnection(AbstractBuildClient buildClient, ConnectionDetails connectionDetails, RepositoryManager repositoryManager, ITeamRepository repository) {
		this.fBuildClient = buildClient;
		this.fConnectionDetails = connectionDetails;
		this.fRepositoryManager = repositoryManager;
		this.fRepository = repository;
	}
	
	public AbstractBuildClient getBuildClient() {
		return fBuildClient;
	}
	
	public ConnectionDetails getConnectionDetails() {
		return fConnectionDetails;
	}
	
	public RepositoryManager getRepositoryManager() {
		return fRepositoryManager;
	}
	
	public ITeamRepository getTeamRepository() {
		return fRepository;
	}
	
	public BuildConnection getBuildConnection() {
		if (fBuildConnection == null) {
			fBuildConnection = new BuildConnection(getTeamRepository());
		}
		return fBuildConnection;
	}

	/**
	 * Tests the given connection by logging in.
	 * 
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @throw Exception if an error occurs
	 * @LongOp
	 */
	public void testConnection(IProgressMonitor progress) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress);
		ITeamRepository repo = (ITeamRepository) getTeamRepository();
		if (repo.loggedIn()) {
			repo.logout();
		}
		try {
			repo.login(monitor);
		} catch (ServerVersionCheckException e) {
			// This exception was Deprecated in 4.0.3. When we nolonger need
			// to support 4.0.2 and earlier toolkit releases, this catch block
			// can be deleted
		    // We need to still handle the deprecated exception since earlier 
		    // toolkit versions may still throw it
			throw new RTCValidationException(e.getMessage());
		} catch (AuthenticationException e) {
			fBuildClient.removeRepositoryConnection(getConnectionDetails());
			throw new RTCValidationException(e.getMessage());
		} catch (TeamRepositoryException e) {
			// This exception is only present in 4.0.3 libraries and later
			// So refer by name to prevent class loading failures
			// When we nolonger need to support 4.0.2 and earlier toolkits, the textual compare for ServerVersionCheckException
			// can be turned into an actual catch block
			if ("com.ibm.team.repository.common.ServerVersionCheckException".equals(e.getClass().getName())) {
				throw new RTCValidationException(e.getMessage());
			} else {
				throw e;
			}
		}
	}

	/**
	 * Tests that the specified workspace is a valid build workspace.
	 * 
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @throw Exception if an error occurs
	 * @LongOp
	 */
	public void testBuildWorkspace(String workspaceName, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(50));
		try {
			getWorkspace(workspaceName, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * Returns the repository workspace with the given name. The workspace connection can
	 * be to a workspace or a stream. If there is more than 1 repository workspace with the name
	 * it is an error.  If there are no repository workspaces with the name it is an error.
	 * 
	 * @param workspaceName The name of the workspace. Never <code>null</code>
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return The workspace connection for the workspace. Never <code>null</code>
	 * @throws Exception if an error occurs
	 */
	protected IWorkspaceHandle getWorkspace(String workspaceName, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(getTeamRepository());
		
		IWorkspaceSearchCriteria searchCriteria = IWorkspaceSearchCriteria.FACTORY
				.newInstance().setExactName(workspaceName)
				.setKind(IWorkspaceSearchCriteria.WORKSPACES);
		List<IWorkspaceHandle> workspaceHandles = workspaceManager.findWorkspaces(searchCriteria, 2, monitor);
		if (workspaceHandles.size() > 1) {
			throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_name_not_unique(workspaceName));
		}
		if (workspaceHandles.size() == 0) {
			throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_workspace_not_found(workspaceName));
		}
		return workspaceHandles.get(0);
	}

	/**
	 * Tests that the specified build definition is valid.
	 * 
	 * @param buildDefinitionId ID of the RTC build definition
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @throw Exception if an error occurs
     * @throws RTCValidationException
     *             If a build definition for the build definition id could not
     *             be found.
	 * @LongOp
	 */
	public void testBuildDefinition(String buildDefinitionId, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
		getBuildConnection().testBuildDefinition(buildDefinitionId, monitor.newChild(75), clientLocale);
	}

	/**
	 * Determines if there are changes for the build workspace which would result in a need for a build.
	 * We are interested in components to be added/removed, change sets to be added/removed.
	 * 
	 * @param buildDefinitionId The name (id) of the build definition to use. May be <code>null</code>
	 * if buildWorkspaceName is supplied instead. Only one of buildWorkspaceName/buildDefinitionId
	 * should be supplied
	 * @param buildWorkspaceName Name of the RTC build workspace. May be <code>null</code>
	 * if buildDefinitionId is supplied instead. Only one of buildWorkspaceName/buildDefinitionId
	 * should be supplied
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return <code>true</code> if there are changes for the build workspace
	 * <code>false</code> otherwise
	 * @throws Exception Thrown if anything goes wrong.
	 */
	public boolean incomingChanges(String buildDefinitionId, String buildWorkspaceName, IConsoleOutput listener,
			IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(10));
		BuildWorkspaceDescriptor workspace;
		if (buildDefinitionId != null && buildDefinitionId.length() > 0) {
			BuildConnection buildConnection = getBuildConnection();
			IBuildDefinition buildDefinition = buildConnection.getBuildDefinition(buildDefinitionId, monitor.newChild(10));
			if (buildDefinition == null) {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_build_definition_not_found(buildDefinitionId));
			}
				
			IBuildProperty property = buildDefinition.getProperty(
                    IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
            if (property != null && property.getValue().length() > 0) {
                workspace = new BuildWorkspaceDescriptor(getTeamRepository(), property.getValue(), null);
            } else {
            	throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_build_definition_no_workspace(buildDefinitionId));
            }
		} else {
			IWorkspaceHandle workspaceHandle = getWorkspace(buildWorkspaceName, monitor.newChild(10), clientLocale);
			workspace = new BuildWorkspaceDescriptor(fRepository, workspaceHandle.getItemId().getUuidValue(), buildWorkspaceName);
		}

		AcceptReport report = SourceControlUtility.checkForIncoming(fRepositoryManager, workspace, monitor.newChild(80));
		return report.getChangesAcceptedCount() > 0;
	}

	/**
	 * Update the build workspace and load it. Create a snapshot of the build workspace and
	 * report on the changes made to it. Update the build result with information about the build.
	 * 
	 * @param buildResultUUID The id of the build result (which also contains the build request & build definition instance)
	 * May be <code>null</code> if buildWorkspaceName is supplied. Only one of buildResultUUID/buildWorkspaceName should be
	 * supplied
	 * @param buildWorkspaceName Name of the RTC build workspace (changes will be accepted into it)
	 * May be <code>null</code> if buildResultUUID is supplied. Only one of buildResultUUID/buildWorkspaceName should be
	 * supplied
	 * @param fetchDestination The location the build workspace is to be loaded
	 * @param changeReport The report to be built of all the changes accepted/discarded
	 * @param defaultSnapshotName The name to give the snapshot created if one can't be determined some other way
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return <code>Map<String, String></code> of build properties
	 * @throws Exception Thrown if anything goes wrong
	 */
	public Map<String, String> checkout(String buildResultUUID, String buildWorkspaceName, String hjFetchDestination, ChangeReport changeReport,
			String defaultSnapshotName, final IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale) throws Exception {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		
		ensureLoggedIn(monitor.newChild(1));
		
		BuildWorkspaceDescriptor workspace;
        
		BuildConfiguration buildConfiguration = new BuildConfiguration(getTeamRepository(), hjFetchDestination);
		
		// If we have a build result, the build definition that describes what is to be accepted into & how to load
		// Otherwise, the build workspace on the Jenkins definition is used.
		IBuildResultHandle buildResultHandle = null;
		if (buildResultUUID != null && buildResultUUID.length() > 0) {
			buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
	        buildConfiguration.initialize(buildResultHandle, defaultSnapshotName, listener, monitor.newChild(1), clientLocale);

		} else {
			IWorkspaceHandle workspaceHandle = getWorkspace(buildWorkspaceName, monitor.newChild(1), clientLocale);
			buildConfiguration.initialize(workspaceHandle, buildWorkspaceName, defaultSnapshotName);
		}
		
		Map<String, String> buildProperties = buildConfiguration.getBuildProperties();

        workspace = buildConfiguration.getBuildWorkspaceDescriptor();

        listener.log(Messages.getDefault().RepositoryConnection_checkout_setup());
        String parentActivityId = getBuildConnection().startBuildActivity(buildResultHandle,
                Messages.getDefault().RepositoryConnection_pre_build_activity(), null, false, monitor.newChild(1));

		AcceptReport acceptReport = null;
        
        getBuildConnection().addWorkspaceContribution(workspace.getWorkspace(fRepositoryManager, monitor.newChild(1)),
        		buildResultHandle, monitor.newChild(1));

        // Ensure we hang onto this between the accept and the load steps so that if 
        // we are synchronizing, we use the same cached sync times.
        IWorkspaceConnection workspaceConnection = workspace.getConnection(fRepositoryManager, false, monitor.newChild(1));
        boolean synchronizeLoad = false;

        if (!buildConfiguration.isPersonalBuild() && buildConfiguration.acceptBeforeFetch()) {
            listener.log(Messages.getDefault().RepositoryConnection_checkout_accept(
            		workspaceConnection.getName()));

            getBuildConnection().startBuildActivity(buildResultHandle,
                    Messages.getDefault().RepositoryConnection_activity_accepting_changes(), parentActivityId, true,
                    monitor.newChild(1));

            if (monitor.isCanceled()) {
            	throw new InterruptedException();
            }
            
            acceptReport = SourceControlUtility.acceptAllIncoming(
                    fRepositoryManager, workspace, buildConfiguration.getSnapshotName(),
                    monitor.newChild(40));
            getBuildConnection().addSnapshotContribution(acceptReport.getSnapshot(), buildResultHandle, monitor.newChild(1));
            
            int acceptCount = acceptReport.getChangesAcceptedCount();
            if (acceptCount > 0) {
                buildProperties.put(IJazzScmConfigurationElement.PROPERTY_CHANGES_ACCEPTED, String.valueOf(acceptCount));
            }

            IBaselineSet snapshot = acceptReport.getSnapshot();
            if (snapshot != null) {
            	buildProperties.put(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID, snapshot.getItemId().getUuidValue());
            }
            
            if (buildResultHandle != null) {
            	WorkItemPublisher workItemPublisher = new WorkItemPublisher();
            	workItemPublisher.publish(buildResultHandle, acceptReport.getAcceptChangeSets(), getTeamRepository());
            }

            if (monitor.isCanceled()) {
            	throw new InterruptedException();
            }
            
            // build change report
            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
            changeReportBuilder.populateChangeReport(changeReport,
            		workspaceConnection.getResolvedWorkspace(), acceptReport,
            		listener, monitor.newChild(2));
            
            synchronizeLoad = true;
        } else {
        	
            // build change report
            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
            changeReportBuilder.populateChangeReport(changeReport,
            		buildConfiguration.isPersonalBuild(),
            		listener);
        }
		changeReport.prepareChangeSetLog();

        ISharingManager manager = FileSystemCore.getSharingManager();
        final ILocation fetchLocation = buildConfiguration.getFetchDestinationPath();
        ISandbox sandbox = manager.getSandbox(fetchLocation, false);

        // Create a listener to handle corrupt states which may happen during loading
        ICorruptCopyFileAreaListener corruptSandboxListener = new ICorruptCopyFileAreaListener () {
            public void corrupt(ICorruptCopyFileAreaEvent event) {
                if (event.isCorrupt() && event.getRoot().equals(fetchLocation)) {
                    listener.log(Messages.getDefault().RepositoryConnection_corrupt_metadata(
                    		fetchLocation.toOSString()));
                }
            }
        };
        
        try {
            
            // we don't need to check for a corrupt sandbox if it doesn't exist or is marked as "Delete Before Fetch"
        	File fetchDestinationFile = buildConfiguration.getFetchDestinationFile();
        	boolean deleteNeeded = buildConfiguration.isDeleteNeeded();
            if (fetchDestinationFile.exists() && !deleteNeeded) { 
               
                if (!sandbox.isRegistered()) {
                    // the sandbox must be registered in order to call .isCorrupted()
                    manager.register(sandbox, false, monitor.newChild(1));
                }
                
                if (sandbox.isCorrupted(monitor.newChild(1))) {
                    deleteNeeded = true;
                    listener.log(Messages.getDefault().RepositoryConnection_corrupt_metadata_found(
                            fetchDestinationFile.getCanonicalPath()));
                    LOGGER.finer("Corrupt metadata for sandbox " +  //$NON-NLS-1$
                            fetchDestinationFile.getCanonicalPath());
                }
            }

            if (deleteNeeded) {
                listener.log(Messages.getDefault().RepositoryConnection_checkout_clean_sandbox(
                        fetchDestinationFile.getCanonicalPath()));

                File toDelete = fetchDestinationFile;
                // the sandbox must be deregistered in order to delete
                manager.deregister(sandbox, monitor.newChild(1));
                
                boolean deleteSucceeded = delete(toDelete, listener, monitor.newChild(1));

                if (!deleteSucceeded || fetchDestinationFile.exists()) {
                    throw new TeamBuildException(Messages.getDefault().RepositoryConnection_checkout_clean_failed(
                            fetchDestinationFile.getCanonicalPath()));
                }
            }

            // Add the listener just before the fetch stage
            ((SharingManager) FileSystemCore.getSharingManager()).addListener(corruptSandboxListener);

            listener.log(Messages.getDefault().RepositoryConnection_checkout_fetch_start(
                    fetchDestinationFile.getCanonicalPath()));
            
            getBuildConnection().startBuildActivity(buildResultHandle, Messages.getDefault().RepositoryConnection_activity_fetching(),
                    parentActivityId, true, monitor.newChild(1));

            // TODO This affects all loads ever after (and with multi-threaded ...)
            // setScmMaxContentThreads(getMaxScmContentThreads(buildDefinitionInstance));

            if (monitor.isCanceled()) {
            	throw new InterruptedException();
            }

            SourceControlUtility.updateFileCopyArea(workspaceConnection,
                    fetchDestinationFile.getCanonicalPath(), buildConfiguration.includeComponents(),
                    buildConfiguration.getComponents(),
                    synchronizeLoad, buildConfiguration.getComponentLoadRules(workspaceConnection, monitor.newChild(1)),
                    buildConfiguration.createFoldersForComponents(), monitor.newChild(40));

            listener.log(Messages.getDefault().RepositoryConnection_checkout_fetch_complete());
            
            try {
            	getBuildConnection().completeBuildActivity(buildResultHandle, parentActivityId, monitor.newChild(1));
            } catch (TeamRepositoryException e) {
            	listener.log(Messages.getDefault().RepositoryConnection_complete_checkout_activity_failed(e.getMessage()), e);
            }

        } finally {
            /*
             * Force a deregistration of the sandbox so that its Metadata is guaranteed to be flushed
             * And possibly so the next time we try to delete the file copy area, it will succeed.
             */
            ((SharingManager) FileSystemCore.getSharingManager()).removeListener(corruptSandboxListener);
            try {
                manager.deregister(sandbox, monitor.newChild(1));
            } catch (OperationCanceledException e) {
            	// propagate the cancel (we don't want it logged).
            	// It may mean though that an error is lost if it was a different error that
            	// caused us to exit out to this finally block. The alternative is to make the
            	// thread interrupted for the next task to handle the cancel.
            	throw e;
            } catch (Exception e) {
            	listener.log(Messages.getDefault().RepositoryConnection_checkout_termination_error(e.getMessage()), e);
            }
        }
        
        buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
        return buildProperties;
	}


	/**
	 * Create an RTC build result.
	 * @param buildDefinition The name of the build definition that is behind the request
	 * for the build.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return The item id of the build result created
	 * @throws Exception Thrown if anything goes wrong
	 */
	public String createBuildResult(String buildDefinition, String personalBuildWorkspaceName, String buildLabel,
			IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
		
		IWorkspaceHandle personalBuildWorkspace = null;
		if (personalBuildWorkspaceName != null && personalBuildWorkspaceName.length() > 0) {
			personalBuildWorkspace = getWorkspace(personalBuildWorkspaceName, monitor.newChild(25), clientLocale);
		}
		BuildConnection buildConnection = new BuildConnection(getTeamRepository());
		IBuildResultHandle buildResult = buildConnection.createBuildResult(buildDefinition,
				personalBuildWorkspace, buildLabel, listener, monitor.newChild(50), clientLocale);
		
		return buildResult.getItemId().getUuidValue();
	}

	/**
	 * Mark a build result as started, if it is not already started
	 * @param buildResultInfo A structure in which to record if we own the build and 
	 * info about how it was started (who and is it personal).
	 * @param buildLabel The label to give to the RTC build
	 * @param listener Listener to provide log info to.
	 * @param progress Monitor to listen for cancellation on.
	 * @param clientLocale The client's locale for error/log messages
	 * @throws Exception Thrown if anything goes wrong.
	 */
	public void startBuild(IBuildResultInfo buildResultInfo, String buildLabel,
			IConsoleOutput listener, IProgressMonitor progress,
			Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
		
		BuildConnection buildConnection = new BuildConnection(getTeamRepository());
		buildConnection.startBuild(buildResultInfo, buildLabel,
				listener, monitor.newChild(75), clientLocale);
	}

	public void terminateBuild(String buildResultUUID,  boolean aborted, int buildState,
			IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(5));

		BuildConnection buildConnection = new BuildConnection(getTeamRepository());
		buildConnection.terminateBuild(buildResultUUID, aborted, buildState, listener,
				monitor.newChild(95), clientLocale);
	}

	public void ensureLoggedIn(IProgressMonitor progress) throws TeamRepositoryException {
		if (!fRepository.loggedIn()) {
			try {
				fRepository.login(progress);
			} catch (AuthenticationException e) {
				fBuildClient.removeRepositoryConnection(getConnectionDetails());
				throw e;
			} catch (ServerVersionCheckException e) {
				// This exception was Deprecated in 4.0.3. When we nolonger need
				// to support 4.0.2 and earlier toolkit releases, this catch
				// block can be deleted
	            // We need to still handle the deprecated exception since earlier 
	            // toolkit versions may still throw it
				fBuildClient.removeRepositoryConnection(getConnectionDetails());
				throw e;
			} catch (TeamRepositoryException e) {
				if ("com.ibm.team.repository.common.ServerVersionCheckException".equals(e.getClass().getName()) //$//$NON-NLS-1$
						|| "com.ibm.team.repository.client.ServerStateCheckException".equals(e.getClass().getName())) { //$NON-NLS-1$
					// the ServerVersionCheckException is only in RTC 4.0.3 and later releases
					// When we nolonger need to support 4.0.2 and earlier toolkits, the textual compare for ServerVersionCheckException
					// can be turned into an actual catch block
					
					// the ServerStateCheckException is only in RTC releases that support Server rename (not 3.0.1.x).
					// When we nolonger need to support 3.0.1.x toolkits, the textual compare for ServerStateCheckException
					// can be turned into an actual catch block.
					
					// This is a simple check, doesn't handle subclasses, but during my search,
					// I didn't find any.
					fBuildClient.removeRepositoryConnection(getConnectionDetails());
				}
				throw e;
			}
		}
	}

    /**
     * Recursively delete starting at the given file.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     */
    private boolean delete(File file, IConsoleOutput listener, IProgressMonitor progress) throws Exception {
    	// paranoia check... Don't delete root directory because somehow the path was empty
    	LOGGER.finer("Deleting " + file.getAbsolutePath()); //$NON-NLS-1$
    	IPath path = new Path(file.getCanonicalPath());
    	if (path.segmentCount() == 0) {
    		listener.log(Messages.getDefault().RepositoryConnection_checkout_clean_root_disallowed(path.toOSString()));
    		LOGGER.finer("Tried to delete root directory " + path.toOSString()); //$NON-NLS-1$
    		return false;
    	}
    	return deleteUsingJavaIO(file, listener, progress);
    }
    
    private boolean deleteUsingJavaIO(File file, IConsoleOutput listener, IProgressMonitor progress) throws IOException, InterruptedException {
    	if (progress.isCanceled()) {
    		throw new InterruptedException();
    	}
    	
        // take into account file or its children may be valid symbolic links.
        // we do not want to follow them.
        // The alternative to this is to use EFS to do the delete which does
        // essentially the same thing.
        // The other alternative is to use EFS to get the attributes and see
        // if it is a symbolic link and tailor the delete according to that.
        // This may have incorrect behaviour if the file is a link
        // and read-only.
        if (file.delete() || !file.exists()) {
            // Try to delete the file/link first without recursing to avoid
            // traversing into links
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                listener.log(Messages.getDefault().RepositoryConnection_checkout_clean_error(file.getCanonicalPath()));
                LOGGER.finer("Unexpected null children for " + file.getCanonicalPath()); //$NON-NLS-1$
            } else {
                for (File child : children) {
                	deleteUsingJavaIO(child, listener, progress);
                }
            }
        }
        return file.delete();
    }

	public void createBuildLinks(String buildResultUUID, String rootUrl,
			String projectUrl, String buildUrl, IConsoleOutput clientConsole,
			IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(50));
		
		getBuildConnection().createBuildLinks(buildResultUUID, rootUrl, projectUrl,
				buildUrl, clientConsole, monitor.newChild(50));
	}
}
