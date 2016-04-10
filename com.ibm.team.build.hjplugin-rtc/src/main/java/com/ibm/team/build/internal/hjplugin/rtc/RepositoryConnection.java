/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
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
import java.io.PrintStream;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
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
import com.ibm.team.build.internal.scm.LoadComponents;
import com.ibm.team.build.internal.scm.RepositoryManager;
import com.ibm.team.build.internal.scm.SourceControlUtility;
import com.ibm.team.filesystem.client.FileSystemCore;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.ISandbox;
import com.ibm.team.filesystem.client.ISharingManager;
import com.ibm.team.filesystem.client.internal.SharingManager;
import com.ibm.team.filesystem.client.internal.copyfileareas.ICorruptCopyFileAreaEvent;
import com.ibm.team.filesystem.client.internal.copyfileareas.ICorruptCopyFileAreaListener;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.ServerVersionCheckException;
import com.ibm.team.repository.client.internal.ItemManager;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.repository.transport.client.AuthenticationException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.BaselineSetFlags;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IChangeHistorySyncReport;
import com.ibm.team.scm.common.dto.IComponentSearchCriteria;
import com.ibm.team.scm.common.dto.IWorkspaceSearchCriteria;

/**
 * A connection to the Jazz repository.
 */
@SuppressWarnings("restriction")
public class RepositoryConnection {

    private static final Logger LOGGER = Logger.getLogger(RepositoryConnection.class.getName());
    private static final String DEFAULTWORKSPACEPREFIX = "HJP"; //$NON-NLS-1$

	private final AbstractBuildClient fBuildClient;
	private final ConnectionDetails fConnectionDetails;
	private final RepositoryManager fRepositoryManager;
	private final ITeamRepository fRepository;
	private BuildConnection fBuildConnection;
	
	// field names expected in the json text passed from the RTC jenkins extension
	private static final String COMPONENTS_TO_EXCLUDE = "componentsToExclude"; //$NON-NLS-1$
	private static final String LOAD_RULES = "loadRules"; //$NON-NLS-1$
	private static final String COMPONENT_ID = "componentId"; //$NON-NLS-1$
	private static final String COMPONENT_NAME = "componentName"; //$NON-NLS-1$
	private static final String FILE_ITEM_ID = "fileItemId"; //$NON-NLS-1$
	private static final String FILE_PATH = "filePath"; //$NON-NLS-1$
	
	// file path segment to be used when specifying the load rule file path
	private static final String SEPARATOR = "/"; //$NON-NLS-1$

	
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
			if ("com.ibm.team.repository.common.ServerVersionCheckException".equals(e.getClass().getName())) { //$NON-NLS-1$
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
     * Returns a {@link IWorkspace} for a given workspaceUUID 
     * @param repository - the Jazz repository connection
     * @param workspaceUUID - the UUID of the workspace
     * @param progress - progress monitor
     * @param clientLocale - locale of the client
     * @return a {@link IWorkspace}
     * @throws TeamRepositoryException
     */
	public static IWorkspace getWorkspace(ITeamRepository repository, UUID workspaceUUID, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException  {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IItemHandle itemHandle = IBaselineSet.ITEM_TYPE.createItemHandle(workspaceUUID, null);
		IWorkspace workspace = (IWorkspace) repository.itemManager().fetchCompleteItem(itemHandle, ItemManager.REFRESH, monitor);
		return workspace;
	}
	
	/**
	 * Tests that the specified stream is a valid build stream.
	 * 
	 * @param streamName The name of the stream. Never <code>null</code>.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @throw RTCValidationException if validation fails 
	 * @throw {@link TeamRepositoryException} if there is an exception when communicating with the repository
	 */
	public void testBuildStream(String streamName, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException, RTCValidationException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(50));
		try {
			getBuildStream(streamName, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}
	
	/**
	 * Returns the repository stream with the given streams name. If there is more than 1 repository stream with the name
	 * it is an error.  If there are no repository streams with the name it is an error.
	 * 
	 * @param streamName The name of the stream. Never <code>null</code>
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return The workspace connection for the workspace. Never <code>null</code>
	 * @throws Exception if an error occurs
	 */
	public IWorkspaceHandle getBuildStream(String buildStream, IProgressMonitor progress, Locale clientLocale) throws RTCConfigurationException, TeamRepositoryException{
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
		return RTCWorkspaceUtils.getInstance().getBuildStream(buildStream, getTeamRepository(), monitor.newChild(75), clientLocale);
	}
	
	/**
	 * Returns streamUUID corresponding to a build stream.
	 * 
	 * @param streamName - the name of the RTC stream
	 * @param progress - a progress monitor
	 * @param clientLocale - the locale of the client
	 * @returns the UUID of the stream as {@link String}
	 * @throws RTCConfigurationException if no stream is found or more than one stream with the same name is found
	 * @throws TeamRepositoryException if there is an exception when communicating with the Jazz repository
	 */
	public String getBuildStreamUUID(String buildStream, IProgressMonitor progress, Locale clientLocale) throws RTCConfigurationException, TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
        return RTCWorkspaceUtils.getInstance().getBuildStreamUUID(buildStream, getTeamRepository(), progress, clientLocale);
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
	 * @param ignoreOutgoingFromBuildWorkspace if true, then ignore outgoing changes from build workspace
	 * @return <code>Non zero</code> if there are changes for the build workspace
	 * <code>0</code> otherwise
	 * @throws Exception Thrown if anything goes wrong.
	 */
	public int incomingChanges(String buildDefinitionId, String buildWorkspaceName, IConsoleOutput listener,
			IProgressMonitor progress, Locale clientLocale, boolean ignoreOutgoingFromBuildWorkspace) throws Exception {
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
		return RTCAcceptReportUtility.hashCode(report, ignoreOutgoingFromBuildWorkspace);
	}
	
	/**
	 * Get details regarding build workspace name, and build definition id from buildResultUUID.
	 * @param buildResultUUID  The id of the build result (which also contains the build request & build definition instance)
	 * Cannot be <code>null</code>.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return
	 * @throws Exception Thrown if anything goes wrong
	 */
	public Map<String, String> getBuildResultUUIDDetails(String buildResultUUID, 
			final IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale) throws Exception {
		if (buildResultUUID == null) {
			return new HashMap<String, String>();
		}
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		
		ensureLoggedIn(monitor.newChild(1));
		
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
		String buildDefinitionId = BuildConfiguration.getBuildDefinitionId(getTeamRepository(), buildResultHandle, monitor.newChild(1), clientLocale);
		
		Map<String, String> result = new HashMap<String, String>();
		result.put(Constants.BUILD_DEFINITION_ID, buildDefinitionId); //$NON-NLS-1$

		return result;
	}

	/**
	 * Update the build workspace for BuildWorkspace and BuildDefinition. Create a snapshot of the build workspace and
	 * report on the changes made to it. Update the build result with information about the build.
	 * A call to this method should be followed by call to load method.
	 * 
	 * @param buildResultUUID The id of the build result (which also contains the build request & build definition instance)
	 * May be <code>null</code> if buildWorkspaceName is supplied. Only one of buildResultUUID/buildWorkspaceName/buildStream should be
	 * supplied
	 * @param buildWorkspaceName Name of the RTC build workspace (changes will be accepted into it)
	 * May be <code>null</code> if buildResultUUID is supplied. Only one of buildResultUUID/buildWorkspaceName/buildStream should be
	 * supplied
	 * @param buildSnapshot Name of the RTC buildsnapshot. An empty changelog with snapshot link will be created. May be <code>null</code>
	 * @param buildStream Name of the RTC build stream
	 * May be <code>null</code> if buildResultUUID or buildWorkspace is supplied. Only one of buildResultUUID/buildWorkspaceName/buildStream should be
	 * supplied
	 * @param hjFetchDestination The location the build workspace is to be loaded
	 * @param changeReport The report to be built of all the changes accepted/discarded. May be <code> null </code>.
	 * @param defaultSnapshotName The name to give the snapshot created if one can't be determined some other way
	 * @param previousSnapshotUUID The uuid of the previous snapshot for comparison. Used for generating changelog for buildStream. May be <code>null</code>
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @param callConnectorTimeout user defined call connector timeout
	 * @param acceptBeforeLoad Accept latest changes before loading if true
	 * @return <code>Map<String, Object></code> containing build properties, and CallConnector details
	 * @throws Exception Thrown if anything goes wrong
	 */
	public Map<String, Object> accept(String buildResultUUID, String buildWorkspaceName, String buildSnapshot, String buildStream, String hjFetchDestination, ChangeReport changeReport,
			String defaultSnapshotName, final String previousSnapshotUUID, final IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale,
			String callConnectorTimeout, boolean acceptBeforeLoad) throws Exception {
		LOGGER.finest("RepositoryConnection.accept : Enter");

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(1));
		
		if (buildStream != null) {
			return acceptForBuildStream(buildStream, changeReport, defaultSnapshotName, previousSnapshotUUID, listener, monitor, clientLocale);
		}
		else if (buildSnapshot != null) {
			return acceptForBuildSnapshot(buildSnapshot, changeReport, listener, progress, clientLocale);
		}
		
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
			buildConfiguration.initialize(workspaceHandle, buildWorkspaceName, defaultSnapshotName, acceptBeforeLoad);
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
        
        // Warn if the build user id can't see all the components in the build workspace
        if (!workspaceConnection.getUnreadableComponents().isEmpty()) {
            listener.log(Messages.getDefault().RepositoryConnection_hidden_components(
                    workspaceConnection.getName(), workspaceConnection.getUnreadableComponents().size()));
        }

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
            buildProperties.put(Constants.TEAM_SCM_ACCEPT_PHASE_OVER, "true"); //$NON-NLS-1$
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
            if (changeReport != null) {
		            // build change report
		            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
		            changeReportBuilder.populateChangeReport(changeReport,
		            		workspaceConnection.getResolvedWorkspace(), acceptReport,
		            		listener, monitor.newChild(2));
            }
            
        } else {
        	if (changeReport != null) {
	            // build change report
	            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
	            changeReportBuilder.populateChangeReport(changeReport,
	            		buildConfiguration.isPersonalBuild(),
	            		listener);
        	}
        }
        if ( changeReport != null ) {
        	changeReport.prepareChangeSetLog();
        }

        buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put(Constants.BUILD_PROPERTIES, buildProperties); //$NON-NLS-1$
        
        // lets cache the workspace connection object for subsequent "load" call...
        CallConnector<IWorkspaceConnection> callConnector = null;
        String connectorId = ""; //$NON-NLS-1$
        try {
            if ((callConnectorTimeout != null) && (!"".equals(callConnectorTimeout)) && callConnectorTimeout.matches("\\d+")) { //$NON-NLS-1$ //$NON-NLS-2$
            	long timeout = Long.parseLong(callConnectorTimeout) * 1000;
            	callConnector = new CallConnector<IWorkspaceConnection>(workspaceConnection, timeout);
            } else {
            	callConnector = new CallConnector<IWorkspaceConnection>(workspaceConnection);
            }
    		callConnector.start();
    		connectorId = (new Long(callConnector.getId())).toString();
        } catch(Exception e) {
    		String errorMessage = Messages.getDefault().RepositoryConnection_accept_unable_to_start_call_connector();
    		// can't proceed if load has to be synchronized, 
    		// since we are unable to obtain workspace connection object
    		TeamBuildException exception = new TeamBuildException(errorMessage);
    		listener.log(errorMessage, exception);
    		throw exception;
        }
        result.put(Constants.CONNECTOR_ID, connectorId); //$NON-NLS-1$
        result.put(Constants.PARENT_ACTIVITY_ID, parentActivityId); //$NON-NLS-1$
        
        return result;
	}

	/**
	 * Load the build workspace. This call is expected to follow a call to accept method.
	 * 
	 * @param buildResultUUID The id of the build result (which also contains the build request & build definition instance)
	 * May be <code>null</code> if buildWorkspaceName is supplied. Only one of buildResultUUID/buildWorkspaceName should be
	 * supplied
	 * @param buildWorkspaceName Name of the RTC build workspace (changes will be accepted into it)
	 * May be <code>null</code> if buildResultUUID is supplied. Only one of buildResultUUID/buildWorkspaceName should be
	 * supplied
	 * @param buildSnapshot The name or UUID of the RTC build snapshot.
	 * @param buildStream The name or UUID of the RTC build stream.
	 * @param buildStreamData The additional stream data for stream load.
	 * @param hjFetchDestination The location the build workspace is to be loaded
	 * @param defaultSnapshotName The name to give the snapshot created if one can't be determined some other way
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @param buildProperties buildProperties created so far by accept call
	 * @param connectorId CallConnector id to retrieve shared data (workspaceConnection) from, that was created by accept call
	 * @param extProvider Extension provider
	 * @param logger logger object
	 * @param isDeleteNeeded true if Jenkins job is configured to delete load directory before fetching
	 * @param createFoldersForComponents create folders for components if true
	 * @param componentsToExclude json text representing the list of components to exclude during load
	 * @param loadRules json text representing the component to load rule file mapping
	 * @param acceptBeforeLoad Accept latest changes before loading, if true
	 * @throws Exception Thrown if anything goes wrong
	 */
	public void load(String buildResultUUID, String buildWorkspaceName, String buildSnapshot, String buildStream,
			Map<String, String> buildStreamData, String hjFetchDestination, String defaultSnapshotName, final IConsoleOutput listener,
		    IProgressMonitor progress, Locale clientLocale, String parentActivityId, String connectorId, Object extProvider, final PrintStream logger, 
			boolean isDeleteNeeded, boolean createFoldersForComponents, String componentsToExclude, String loadRules, boolean acceptBeforeLoad) throws Exception {
		 LOGGER.finest("RepositoryConnection.load : Enter");
		
        // Lets get same workspaceConnection as created by accept call so that if 
        // we are synchronizing, we use the same cached sync times.
        IWorkspaceConnection workspaceConnection = null;
        if (connectorId != null &&  !("".equals(connectorId))) { //$NON-NLS-1$
        	workspaceConnection = (IWorkspaceConnection)CallConnector.getValue(Long.parseLong(connectorId));
        }

		SubMonitor monitor = SubMonitor.convert(progress, 100);

		ensureLoggedIn(monitor.newChild(1));
		
        BuildConfiguration buildConfiguration = new BuildConfiguration(getTeamRepository(), hjFetchDestination);
		
		// If we have a build result, the build definition that describes what is to be accepted into & how to load
		// Otherwise, the build workspace on the Jenkins definition is used.
		IBuildResultHandle buildResultHandle = null;
		if (buildResultUUID != null && buildResultUUID.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_definition_configuration());
			buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
                        buildConfiguration.initialize(buildResultHandle, defaultSnapshotName, listener, monitor.newChild(1), clientLocale);
		} else if (buildWorkspaceName != null && buildWorkspaceName.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_workspace_configuration());
			IWorkspaceHandle workspaceHandle = getWorkspace(buildWorkspaceName, monitor.newChild(1), clientLocale);
			buildConfiguration.initialize(workspaceHandle, buildWorkspaceName, defaultSnapshotName, acceptBeforeLoad);
		} else if (buildSnapshot != null && buildSnapshot.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_snapshot_configuration());
			String workspaceNamePrefix = getWorkspaceNamePrefix();
			IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshot(getTeamRepository(), buildSnapshot, monitor.newChild(1), clientLocale);
			IContributor contributor = fRepository.loggedInContributor();
			buildConfiguration.initialize(baselineSet, contributor, workspaceNamePrefix, monitor.newChild(3));
		} else if (buildStream != null && buildStream.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_stream_configuration());
			String workspaceUUID = buildStreamData.get(Constants.STREAM_DATA_WORKSPACEUUID);
			String snapshotUUID = buildStreamData.get(Constants.STREAM_DATA_SNAPSHOTUUID);
			if (workspaceUUID == null || snapshotUUID == null) {
				String errorMessage = Messages.get(clientLocale).RepositoryConnection_stream_load_no_workspace_snapshot_uuid();
	    		TeamBuildException exception = new TeamBuildException(errorMessage);
	    		listener.log(errorMessage, exception);
	    		throw exception;
			}
			IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshotByUUID(getTeamRepository(), snapshotUUID, monitor.newChild(1), clientLocale);
			IWorkspace workspace = getWorkspace(getTeamRepository(), UUID.valueOf(workspaceUUID), monitor.newChild(3), clientLocale);
			IContributor contributor = fRepository.loggedInContributor();
	    	IWorkspaceHandle streamHandle = getBuildStream(buildStream, monitor.newChild(1), clientLocale);
			buildConfiguration.initialize(streamHandle, buildStream, workspace, baselineSet, contributor, monitor.newChild(3));
		} else {
    		String errorMessage = Messages.get(clientLocale).RepositoryConnection_invalid_load_configuration();
    		TeamBuildException exception = new TeamBuildException(errorMessage);
    		listener.log(errorMessage, exception);
    		throw exception;
		}
		
        boolean synchronizeLoad = !buildConfiguration.isPersonalBuild() && buildConfiguration.acceptBeforeFetch();
        BuildWorkspaceDescriptor workspace = buildConfiguration.getBuildWorkspaceDescriptor();

        // if we could not get workspace connection created by accept call or this is a snapshot/stream load,
        // lets create it afresh
        if (workspaceConnection == null) {
    		String errorMessage = Messages.getDefault().RepositoryConnection_load_no_workspace_connection_for_synched_load();
    		if (!buildConfiguration.isSnapshotLoad() && !buildConfiguration.isStreamLoad()) {
	        	if (synchronizeLoad) {
	        		// can't proceed if load has to be synchronized, 
	        		// since we are unable to obtain workspace connection object
	        		TeamBuildException exception = new TeamBuildException(errorMessage);
	        		listener.log(errorMessage, exception);
	        		throw exception;
	        	}
	        	// otherwise just log error message
	        	listener.log(errorMessage);
    		}
        	// and proceed by creating a new workspace connection...
        	workspaceConnection = workspace.getConnection(fRepositoryManager, false, monitor.newChild(1));
        }
        
		listener.log(Messages.get(clientLocale).RepositoryConnection_fetching_files_from_workspace(workspaceConnection.getName()));
        
       // If this is a not a build definition based build type, set the following
        if (buildResultUUID == null || (buildResultUUID != null && buildResultUUID.length() == 0)) {
            if (createFoldersForComponents) {
             buildConfiguration.setCreateFoldersForComponents(true);
			}
			
			if (componentsToExclude != null && componentsToExclude.trim().length() > 0) {
				buildConfiguration.setIncludeComponents(false);
				buildConfiguration.setComponents(new LoadComponents(getTeamRepository(), getComponentsToExclude(new LoadConfigurationDescriptor(
						buildConfiguration), workspaceConnection, componentsToExclude, clientLocale, monitor.newChild(1))).getComponentHandles());
			}

			if (loadRules != null && loadRules.trim().length() > 0) {
				buildConfiguration.setLoadRules(getComponentLoadRules(new LoadConfigurationDescriptor(buildConfiguration), workspaceConnection,
						loadRules, clientLocale, monitor.newChild(1)));
			}
        }
        
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
        	if(extProvider != null) {
        		if (LOGGER.isLoggable(Level.FINER)) {
    				LOGGER.finer("RepositoryConnection.load : Calling preUpdateCopyFileArea of extensions");
    			}
        		RtcExtensionProviderUtil.preUpdateFileCopyArea(extProvider, logger, fetchDestinationFile,
        				workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(),
						buildResultUUID, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
        	}
        	
        	boolean deleteNeeded = isDeleteNeeded || buildConfiguration.isDeleteNeeded();
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
            
            // Warn if the build user id can't see all the components in the build workspace
            if (!workspaceConnection.getUnreadableComponents().isEmpty()) {
                listener.log(Messages.getDefault().RepositoryConnection_hidden_components(
                        workspaceConnection.getName(), workspaceConnection.getUnreadableComponents().size()));
            }
            
            Map<String, String> buildProperties = buildConfiguration.getBuildProperties();
            Map<String, String> compLoadRules = null;
            String excludeComponents = null;
            boolean includedComponents = buildConfiguration.includeComponents();
            Collection<IComponentHandle> components = buildConfiguration.getComponents();
            String snapshotUUID = buildProperties.get(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID);
        	Map<String, String> componentInfo = getComponentInfoFromWorkspace(workspaceConnection.getComponents(), monitor.newChild(2));
            if(extProvider != null) {
				compLoadRules = RtcExtensionProviderUtil.getComponentLoadRules(
						extProvider, logger, workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, 
						buildResultUUID, componentInfo, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
				excludeComponents = RtcExtensionProviderUtil.getExcludeComponentList(
						extProvider, logger, workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, 
						buildResultUUID, componentInfo, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
			}
            
            if(excludeComponents != null && excludeComponents.trim().length() > 0) {
            	includedComponents = false;
            	components = new LoadComponents(getTeamRepository(), excludeComponents).getComponentHandles();
            }
            
    		if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("RepositoryConnection.load : updatingFileCopyArea");
			}

            SourceControlUtility.updateFileCopyArea(workspaceConnection,
                    fetchDestinationFile.getCanonicalPath(), includedComponents,
                    components,
                    synchronizeLoad, buildConfiguration.getComponentLoadRules(workspaceConnection, compLoadRules, monitor.newChild(1), clientLocale),
                    buildConfiguration.createFoldersForComponents(), monitor.newChild(39));
         
            listener.log(Messages.getDefault().RepositoryConnection_checkout_fetch_complete());
            
            if(extProvider != null) {
        		RtcExtensionProviderUtil.postUpdateFileCopyArea(extProvider, logger, fetchDestinationFile,
        				workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(),
						buildResultUUID, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
        	}
            
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
            	/*
            	 * check if the monitor is cancelled if it is then pass a NullProgressMonitor
            	 * deregister of the sandbox should happen irrespective of the operation.
            	 */
            	
            	IProgressMonitor newMonitor = new NullProgressMonitor();
            	if(monitor != null && !monitor.isCanceled()) {
            		newMonitor = monitor.newChild(1);
            	}
                manager.deregister(sandbox, newMonitor);
            } catch (OperationCanceledException e) {
            	// propagate the cancel (we don't want it logged).
            	// It may mean though that an error is lost if it was a different error that
            	// caused us to exit out to this finally block. The alternative is to make the
            	// thread interrupted for the next task to handle the cancel.
            	throw e;
            } catch (Exception e) {
            	listener.log(Messages.getDefault().RepositoryConnection_checkout_termination_error(e.getMessage()), e);
            }
            // Finalize the buildConfiguration
           	buildConfiguration.tearDown(fRepositoryManager, monitor.newChild(1), listener, clientLocale);
        }
        
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
	 * @param changeReport The report to be built of all the changes accepted/discarded. May be <code> null </code>.
	 * @param defaultSnapshotName The name to give the snapshot created if one can't be determined some other way
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return <code>Map<String, String></code> of build properties
	 * @throws Exception Thrown if anything goes wrong
	 */
	@Deprecated
	public Map<String, String> checkout(String buildResultUUID, String buildWorkspaceName, String hjFetchDestination, ChangeReport changeReport,
			String defaultSnapshotName, final IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale, Object extProvider, final PrintStream logger) throws Exception {
		
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
        
        // Warn if the build user id can't see all the components in the build workspace
        if (!workspaceConnection.getUnreadableComponents().isEmpty()) {
            listener.log(Messages.getDefault().RepositoryConnection_hidden_components(
                    workspaceConnection.getName(), workspaceConnection.getUnreadableComponents().size()));
        }

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
            if (changeReport != null) {
		            // build change report
		            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
		            changeReportBuilder.populateChangeReport(changeReport,
		            		workspaceConnection.getResolvedWorkspace(), acceptReport,
		            		listener, monitor.newChild(2));
            }
            
            synchronizeLoad = true;
        } else {
        	if (changeReport != null) {
	            // build change report
	            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
	            changeReportBuilder.populateChangeReport(changeReport,
	            		buildConfiguration.isPersonalBuild(),
	            		listener);
        	}
        }
        if ( changeReport != null ) {
        	changeReport.prepareChangeSetLog();
        }
        
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
        	
        	if(extProvider != null) {
        		RtcExtensionProviderUtil.preUpdateFileCopyArea(extProvider, logger, fetchDestinationFile,
        				workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(),
						buildResultUUID, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
        	}
        	
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
            
            Map<String, String> compLoadRules = null;
            String excludeComponents = null;
            boolean includedComponents = buildConfiguration.includeComponents();
            Collection<IComponentHandle> components = buildConfiguration.getComponents();
            String snapshotUUID = buildProperties.get(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID);
        	Map<String, String> componentInfo = getComponentInfoFromWorkspace(workspaceConnection.getComponents(), monitor.newChild(2));
            if(extProvider != null) {
				compLoadRules = RtcExtensionProviderUtil.getComponentLoadRules(
						extProvider, logger, workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, 
						buildResultUUID, componentInfo, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
				excludeComponents = RtcExtensionProviderUtil.getExcludeComponentList(
						extProvider, logger, workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, 
						buildResultUUID, componentInfo, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
			}
            
            if(excludeComponents != null && excludeComponents.trim().length() > 0) {
            	includedComponents = false;
            	components = new LoadComponents(getTeamRepository(), excludeComponents).getComponentHandles();
            }
            
            SourceControlUtility.updateFileCopyArea(workspaceConnection,
                    fetchDestinationFile.getCanonicalPath(), includedComponents,
                    components,
                    synchronizeLoad, buildConfiguration.getComponentLoadRules(workspaceConnection, compLoadRules, monitor.newChild(1), clientLocale),
                    buildConfiguration.createFoldersForComponents(), monitor.newChild(39));

            listener.log(Messages.getDefault().RepositoryConnection_checkout_fetch_complete());
            
            if(extProvider != null) {
        		RtcExtensionProviderUtil.postUpdateFileCopyArea(extProvider, logger, fetchDestinationFile,
        				workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(),
						buildResultUUID, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
        	}
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
            	/*
            	 * check if the monitor is cancelled if it is then pass a NullProgressMonitor
            	 * deregister of the sandbox should happen irrespective of the operation.
            	 */
            	
            	IProgressMonitor newMonitor = new NullProgressMonitor();
            	if(monitor != null && !monitor.isCanceled()) {
            		newMonitor = monitor.newChild(1);
            	}
                manager.deregister(sandbox, newMonitor);
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


	private Map<String, String> getComponentInfoFromWorkspace(List components, SubMonitor newChild) {
		Map<String, String> compMap = new HashMap<String, String>();
		try {
			List<IComponent> componentsInfo = fRepository.itemManager().fetchCompleteItems(components, IItemManager.DEFAULT, newChild);
			for (IComponent iComponent : componentsInfo) {
				compMap.put(iComponent.getItemId().getUuidValue(), iComponent.getName());
			}
		} catch (TeamRepositoryException e) {
		}
		return compMap;
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

	/**
	 * Delete build result
	 * @param buildResultUUID The UUID of the build result to delete
	 * @param progress Monitor to handle cancellation
	 * @param clientLocale Locale of the calling client
	 * @throws TeamRepositoryException Thrown if anything goes wrong
	 */
	public void deleteBuildResult(String buildResultUUID, IConsoleOutput clientConsole,
			IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(5));

		getBuildConnection().deleteBuildResult(buildResultUUID, clientConsole,
				monitor.newChild(95), clientLocale);
	}

	/**
	 * 
	 * @param buildStream
	 * @param streamChangesData
	 * @param clientConsole
	 * @param progress
	 * @param clientLocale
	 * @return
	 * @throws TeamRepositoryException
	 * @throws RTCConfigurationException
	 * @throws IOException 
	 * @throws NoSuchAlgorithmException 
	 */
	public BigInteger computeIncomingChangesForStream(String buildStream, String streamChangesData,
			IConsoleOutput clientConsole, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException, IOException, NoSuchAlgorithmException {		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		if (buildStream == null) {
			throw new RTCConfigurationException("Stream name cannot be null");
		}
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Computing incoming changes for stream " + buildStream);
		}
		ensureLoggedIn(monitor.newChild(10));
		BigInteger previousStreamHash = (streamChangesData == null) ? (new BigInteger("0")):  RTCWorkspaceUtils.getInstance().getDigestNumber(streamChangesData);
		
		// Get component entries for all components in the stream and compute the digest from concatenating 
		// item ids of ChangeHistory of each component 
		IWorkspaceHandle streamHandle = getBuildStream(buildStream, monitor.newChild(30), clientLocale);
		BigInteger streamDataHash = RTCWorkspaceUtils.getInstance().getDigestNumber(getTeamRepository(), streamHandle, monitor.newChild(10));

		return (streamDataHash.subtract(previousStreamHash));
	}
	
	/**
	 * 
	 * @param buildStream
	 * @param changeReport
	 * @param defaultSnapshotName
	 * @param previousSnapshotUUID
	 * @param listener
	 * @param progress
	 * @param clientLocale
	 * @return
	 * @throws {@link TeamRepositoryException}
	 * @throws {@link RTCConfigurationException}
	 * @throws {@link IOException}
	 * @throws {@link NoSuchAlgorithmException} 
	 */
	private Map<String, Object> acceptForBuildStream(final String buildStream, final ChangeReport changeReport, final String defaultSnapshotName, 
						final String previousSnapshotUUID, final IConsoleOutput listener, final IProgressMonitor progress,
						final Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException, IOException, NoSuchAlgorithmException{
		SubMonitor monitor = SubMonitor.convert(progress, 100);

        Map<String, Object> result = new HashMap<String, Object>();

		IContributor contributor = fRepository.loggedInContributor();
    	IWorkspaceHandle streamHandle = getBuildStream(buildStream, monitor.newChild(1), clientLocale);
    	
    	String workspaceName = getWorkspaceNamePrefix() + "_" + Long.toString(System.currentTimeMillis());
    	String snapshotName = defaultSnapshotName;

		// Take a snapshot on the stream and prepare changesetLog
		// Get the workspace connection for the stream
		IWorkspaceConnection streamConnection = SCMPlatform.getWorkspaceManager(getTeamRepository()).getWorkspaceConnection(streamHandle, monitor.newChild(1));
		
		// Create a workspace from the stream
		IWorkspaceConnection workspaceConnection = SCMPlatform.getWorkspaceManager(getTeamRepository()).createWorkspace(contributor, workspaceName, null, null, streamConnection, monitor.newChild(5));
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finest("RepositoryConnection.accept for stream : Created temporary workspace '" + workspaceName + "'");
		}

		// Create a baselineset for the workspace
		IBaselineSetHandle baselineSet = workspaceConnection.createBaselineSet(null, snapshotName, null, BaselineSetFlags.DEFAULT, monitor.newChild(3));
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finest("RepositoryConnection.accep for stream : Created snapshot '" + snapshotName + "'.");
		}
		
		// Change the owner of the baselineset to the stream
		streamConnection.addBaselineSet(baselineSet, monitor.newChild(2));
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finest("RepositoryConnection.accep for stream : Changed owner of snapshot '" + snapshotName + "' to stream '" + buildStream +"'.");
		}
		
		// Build the change report
		if (previousSnapshotUUID != null) {
			IBaselineSetHandle previousSnapshot = RTCSnapshotUtils.getSnapshot(getTeamRepository(), previousSnapshotUUID, monitor.newChild(1), clientLocale);
			// TODO do we need to exclude components from components to exclude
			// Create the changeReport
			IChangeHistorySyncReport compareReport = SCMPlatform.getWorkspaceManager(getTeamRepository()).compareBaselineSets(baselineSet, previousSnapshot, null, monitor.newChild(2));
			if (changeReport != null) {
	            // build change report
	            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
	            changeReportBuilder.populateChangeReport(changeReport,
	            		streamConnection.getResolvedWorkspace(), baselineSet, snapshotName, compareReport,
	            		listener, monitor.newChild(2));
	        	changeReport.prepareChangeSetLog();
			}
		} else { // Fill in just the snapshot UUID in the change log
			ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
			changeReportBuilder.populateChangeReport(changeReport, baselineSet, snapshotName, listener, monitor.newChild(10));
			changeReport.prepareChangeSetLog();
		}
		
		Map<String, String> buildProperties = new HashMap<String, String>();
       
		// Create streamChangesData and add it to build properties
		String streamDataHashS = RTCWorkspaceUtils.getInstance().getDigest(getTeamRepository(), workspaceConnection.getResolvedWorkspace(), monitor.newChild(10)); 
		LOGGER.finer("Stream's data hash in polling is " +  streamDataHashS.toString());
		buildProperties.put(Constants.TEAM_SCM_STREAM_DATA_HASH, streamDataHashS.toString());
		
		// Put the snapshotUUID in the buildProperties and add it to result
		buildProperties.put(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID, baselineSet.getItemId().getUuidValue());
		buildProperties.put(Constants.TEAM_SCM_SNAPSHOT_OWNER, streamConnection.getResolvedWorkspace().getItemId().getUuidValue());
        buildProperties.put(Constants.TEAM_SCM_ACCEPT_PHASE_OVER, "true");
		buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
        result.put(Constants.BUILD_PROPERTIES, buildProperties);
        	        
        // Create streamData map and add it to result
        Map<String, String> streamData = new HashMap<String, String>();
        streamData.put(Constants.STREAM_DATA_WORKSPACEUUID, workspaceConnection.getResolvedWorkspace().getItemId().getUuidValue());
        streamData.put(Constants.STREAM_DATA_SNAPSHOTUUID, baselineSet.getItemId().getUuidValue());
        result.put(Constants.STREAM_DATA, streamData);
        
        return result;
	}

	/**
	 * 
	 * @param buildSnapshot
	 * @param changeReport
	 * @param listener
	 * @param progress
	 * @param clientLocale
	 * @return
	 * @throws {@link RTCConfigurationException}
	 * @throws {@link TeamRepositoryException}
	 * @throws {@link IOException}
	 */
	private Map<String, Object> acceptForBuildSnapshot(final String buildSnapshot, final ChangeReport changeReport, 
									final IConsoleOutput listener, final IProgressMonitor progress, 
									final Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException, IOException {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
        Map<String, Object> result = new HashMap<String, Object>();

		// Fetch the build snapshot
		IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshot(getTeamRepository(), buildSnapshot, monitor.newChild(40), clientLocale);

		// build change report
		if (changeReport != null) {
            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
            changeReportBuilder.populateChangeReport(changeReport, baselineSet, baselineSet.getName(), listener, monitor.newChild(40)); 
        	changeReport.prepareChangeSetLog();
		}
		
		Map<String, String> buildProperties = new HashMap<String, String>();

		// Put the snapshotUUID in the buildProperties and add it to result
		buildProperties.put(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID, baselineSet.getItemId().getUuidValue());
		buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
        result.put(Constants.BUILD_PROPERTIES, buildProperties);
        
        return result;
	}

	
	private String getWorkspaceNamePrefix() {
		return DEFAULTWORKSPACEPREFIX; 
	}
	
	/**
	 * Validate if the specified components exist in the repository and included in the given workspace.
	 * 
	 * @param isStreamConfiguration Flag that determines if the <code>workspaceName</code> corresponds to a workspace or a stream
	 * @param workspaceName Name of the workspace specified in the build configuration
	 * @param componentsToExclude Json text specifying the list of components to exclude
	 * @param progress Progress monitor
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception
	 */
	public void testComponentsToExclude(boolean isStreamConfiguration, String workspaceName, String componentsToExclude, IProgressMonitor progress,
			Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(40));
		try {
			IWorkspaceHandle workspaceHandle = isStreamConfiguration ? getBuildStream(workspaceName, monitor.newChild(40), clientLocale)
					: getWorkspace(workspaceName, monitor.newChild(40), clientLocale);
			BuildWorkspaceDescriptor wsDescriptor = new BuildWorkspaceDescriptor(getTeamRepository(), workspaceHandle.getItemId().getUuidValue(),
					workspaceName);
			IWorkspaceConnection wsConnection = wsDescriptor.getConnection(fRepositoryManager, false, monitor.newChild(5));
			getComponentsToExclude(new LoadConfigurationDescriptor(isStreamConfiguration, workspaceName), wsConnection, componentsToExclude,
					clientLocale, monitor.newChild(5));

		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * Validate if the specified components/load rule files exist in the repository and included in the given workspace.
	 * 
	 * @param isStreamConfiguration Flag that determines if the <code>workspaceName</code> corresponds to a workspace or a stream
	 * @param workspaceName Name of the workspace specified in the build configuration
	 * @param loadRules Json text specifying the component-to-load-rule file mapping
	 * @param progress Progress monitor
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception
	 */
	public void testLoadRules(boolean isStreamConfiguration, String workspaceName, String loadRules, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(40));
		try {
			IWorkspaceHandle workspaceHandle = isStreamConfiguration ? getBuildStream(workspaceName, monitor.newChild(40), clientLocale)
					: getWorkspace(workspaceName, monitor.newChild(40), clientLocale);
			BuildWorkspaceDescriptor wsDescriptor = new BuildWorkspaceDescriptor(getTeamRepository(), workspaceHandle.getItemId().getUuidValue(),
					workspaceName);
			IWorkspaceConnection wsConnection = wsDescriptor.getConnection(fRepositoryManager, false, monitor.newChild(5));
			getComponentLoadRules(new LoadConfigurationDescriptor(isStreamConfiguration, workspaceName), wsConnection, loadRules, clientLocale,
					monitor.newChild(5));

		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * This method parses the json text expected in the following format
	 * 
	 * <code>
	 * 	{
	 * 		"componentsToExclude":[
	 * 			{
	 * 				"componentId":"_ITkVUNlIEeWHquPepBzvOQ",
	 * 				"componentName":"JUnit"
	 * 			},
	 * 			{
	 * 				"componentId":"_xYEzgdlTEeWHquPepBzvOQ",
	 * 				"componentName":"JUnit Component1",
	 * 			}
	 * 		]
	 * 	}
	 * </code>
	 * 
	 * and returns a list of space separated component ids.
	 * 
	 * When only 'componentName' is given, the component id is determined by looking for the component instance by the
	 * given name in the given workspace.
	 * 
	 * When both 'componentId' and 'componentName' are given then the specified componentId is added to the final list
	 * and the componentName is ignored
	 * 
	 * @param loadConfigurationDescriptor Descriptor instance that represents load configuration. A build can be
	 *            configured to load from a workspace, snapshot, or a stream.
	 * @param wsConnection workspace connection object
	 * @param excludedComponents json text representing the components to exclude during load
	 * @param clientLocale locale
	 * @param progress progress monitor
	 * 
	 * @return space separated list of component ids
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private String getComponentsToExclude(LoadConfigurationDescriptor loadConfigurationDescriptor, IWorkspaceConnection wsConnection, String excludedComponents,
			Locale clientLocale, IProgressMonitor progress) throws Exception {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		StringBuffer result = new StringBuffer();
		// excludedComponents is always non-null here
		JSONObject json = (JSONObject)JSONObject.parse(new StringReader(excludedComponents));
		if (json.get(COMPONENTS_TO_EXCLUDE) == null) {
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_components_to_exclude_required());
		}
		JSONArray componentArray = JSONArray.parse(new StringReader(json.get(COMPONENTS_TO_EXCLUDE).toString()));		
		if (componentArray.size() == 0) {
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_components_to_exclude_required());
		}
		HashMap<String, IComponent> nameToComponentMap = new HashMap<String, IComponent>();
		HashMap<String, IComponent> uuidToComponentMap = new HashMap<String, IComponent>();
		List<String> duplicateComponentNames = new ArrayList<String>();
		List<IComponent> components = fRepository.itemManager().fetchCompleteItems(wsConnection.getComponents(), IItemManager.DEFAULT,
				monitor.newChild(50));
		for (IComponent component : components) {
			if (component != null) {
				if (nameToComponentMap.get(component.getName()) != null) {
					duplicateComponentNames.add(component.getName());
				}
				nameToComponentMap.put(component.getName(), component);
				uuidToComponentMap.put(component.getItemId().getUuidValue(), component);
			}
		}

		// iterate through the list of specified component ids
		for (int i = 0; i < componentArray.size(); i++) {

			JSONObject inner = (JSONObject)componentArray.get(i);
			String componentId = (String)inner.get(COMPONENT_ID);
			if (componentId != null) {
				// validate component id
				validateComponentWithIdExistsInWorkspace(loadConfigurationDescriptor, wsConnection, componentId, uuidToComponentMap, clientLocale,
						monitor.newChild(50));
			} else if (inner.get(COMPONENT_NAME) != null) {
				// if componentId is not given and only componentName is given
				// lookup by the component name in the given workspace and get the component id
				String componentName = inner.get(COMPONENT_NAME).toString();
				componentId = validateComponentWithNameExistsInWorkspace(loadConfigurationDescriptor, wsConnection, componentName,
						nameToComponentMap, duplicateComponentNames, clientLocale, monitor.newChild(50));

			} else {
				// no id or name specified
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_id_or_name_required());
			}
			result.append(componentId);

			if (i < componentArray.size() - 1) {
				result.append(" "); //$NON-NLS-1$
			}
		}
		return result.toString();
	}

	/**
	 * This method parses the json text expected in the following format
	 * 
	 * <code>
	 * 	{ 
	 * 		"loadRules":[ 
	 * 			{ 
	 * 				"componentId":"_xYEzgdlTEeWHquPepBzvOQ",
	 * 				"componentName":"JUnit Component1",
	 * 				"fileItemId":"_oxfJMNn4EeW6lec1vN4vpA",
	 * 				"filePath":"JUnit Component1 Project1/JUnit-Component1-lr.loadrule" 
	 * 			}, 
	 * 			{
	 * 				"componentId":"_xZBOsdlTEeWHquPepBzvOQ",
	 * 				"componentName":"JUnit Component2",
	 * 				"fileItemId":"_9-qRkNn4EeW6lec1vN4vpA",
	 * 				"filePath":"JUnit Component2 Project1/JUnit-Component2-lr.loadrule" 
	 * 			} 
	 * 		]
	 *  }
	 *  </code>
	 * 
	 * and returns a list of LoadRule objects representing the component to load rule file mapping.
	 * 
	 * The load rule file is expected to be contained in the component.
	 * 
	 * When both componentId and componentName are given, componentName is ignored and the componentId is set on the
	 * LoadRule object.
	 * 
	 * When only 'componentName' is given, the component id is determined by looking for the component instance by the
	 * given name in the given workspace.
	 * 
	 * When both fileItemId and filePath are given, filePath is ignored and the fileItemId is set on the LoadRule
	 * object.
	 * 
	 * When only filePath is given, the file item id is determined by looking for the file versionable by the
	 * 
	 * 
	 * @param loadConfigurationDescriptor Descriptor instance that represents load configuration. A build can be
	 *            configured to load from a workspace, snapshot, or a stream.
	 * @param wsConnection workspace connection object
	 * @param loadRules json text representing the component to load rule file mapping
	 * @param clientLocale locale of the requesting client
	 * @param progress progress monitor
	 * 
	 * @return a string that represent the component to load rule file mapping as expected by the build tool kit
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private String getComponentLoadRules(LoadConfigurationDescriptor loadConfigurationDescriptor, IWorkspaceConnection wsConnection, String loadRules,
			Locale clientLocale, IProgressMonitor progress) throws Exception {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		List<LoadRule> loadRuleObjects = new ArrayList<LoadRule>();
		JSONObject json = (JSONObject)JSONObject.parse(new StringReader(loadRules));
		if (json.get(LOAD_RULES) == null) {
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rules_required());
		}
		JSONArray loadRulesArray = JSONArray.parse(new StringReader(json.get(LOAD_RULES).toString()));
		if (loadRulesArray.size() == 0) {
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rules_required());
		}
		HashMap<String, IComponent> nameToComponentMap = new HashMap<String, IComponent>();
		HashMap<String, IComponent> uuidToComponentMap = new HashMap<String, IComponent>();
		List<String> duplicateComponentNames = new ArrayList<String>();
		List<IComponent> components = fRepository.itemManager().fetchCompleteItems(wsConnection.getComponents(), IItemManager.DEFAULT,
				monitor.newChild(50));
		for (IComponent component : components) {
			if (component != null) {
				if (nameToComponentMap.get(component.getName()) != null) {
					duplicateComponentNames.add(component.getName());
				}
				nameToComponentMap.put(component.getName(), component);
				uuidToComponentMap.put(component.getItemId().getUuidValue(), component);
			}
		}

		for (int i = 0; i < loadRulesArray.size(); i++) {
			LoadRule lr = new LoadRule();
			JSONObject inner = (JSONObject)loadRulesArray.get(i);
			String componentId = (String)inner.get(COMPONENT_ID);
			if (componentId != null) {
				// validate component id
				validateComponentWithIdExistsInWorkspace(loadConfigurationDescriptor, wsConnection, componentId, uuidToComponentMap, clientLocale,
						monitor.newChild(25));
			} else if (inner.get(COMPONENT_NAME) != null) {
				String componentName = inner.get(COMPONENT_NAME).toString();
				componentId = validateComponentWithNameExistsInWorkspace(loadConfigurationDescriptor, wsConnection, componentName,
						nameToComponentMap, duplicateComponentNames, clientLocale, monitor.newChild(25));
			} else {
				// component id or name should be specified
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_id_or_name_required());
			}
			// set the determined/specified component id
			lr.setComponentId(componentId);
			// we know that the specified component exists in the given workspace
			IComponent component = uuidToComponentMap.get(componentId);

			String fileItemId = (String)inner.get(FILE_ITEM_ID);
			if (fileItemId != null) {
				// validate file item id
				validateFileWithIdExistsInWorkspace(loadConfigurationDescriptor, wsConnection, component, fileItemId, clientLocale,
						monitor.newChild(25));
			} else if (inner.get(FILE_PATH) != null) {
				// validate file path and get the file item id
				String filePath = inner.get(FILE_PATH).toString();
				fileItemId = validateFileInPathExistsInWorkspace(loadConfigurationDescriptor, wsConnection, component, filePath, clientLocale,
						monitor.newChild(25));
			} else {
				// fileItemId or filePath should be specified
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_file_item_id_or_name_required());
			}
			lr.setFileItemId(fileItemId);
			loadRuleObjects.add(lr);
		}

		StringBuffer res = new StringBuffer();
		for (LoadRule lr : loadRuleObjects) {
			res.append(((LoadRule)lr).getLoadRuleAsExpectedByToolkit()).append(" "); //$NON-NLS-1$
		}
		return res.toString();
	}

	/**
	 * Validate if a component with the given item ID exists in the repository and included in the specified workspace.
	 * 
	 * @param loadConfigurationDescriptor Descriptor instance that represents load configuration. A build can be
	 *            configured to load from a workspace, snapshot, or a stream.
	 * @param wsConnection Workspace connection corresponding to the workspace in context/configured in the build
	 * @param componentId Item ID of the component
	 * @param uuidToComponentMap Item ID to IComponent mapping for the components present in the workspace in context
	 * @param clientLocale locale of the requesting client
	 * @param progress progress monitor
	 * @throws TeamRepositoryException
	 */
	private void validateComponentWithIdExistsInWorkspace(LoadConfigurationDescriptor loadConfigurationDescriptor, IWorkspaceConnection wsConnection,
			String componentId, HashMap<String, IComponent> uuidToComponentMap, Locale clientLocale, IProgressMonitor progress)
			throws TeamRepositoryException {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		UUID componentItemId = null;
		try {
			// validate UUID
			componentItemId = UUID.valueOf(componentId);
		} catch (IllegalArgumentException ex) {
			// invalid component uuid
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_invalid_component_uuid(componentId), ex);
		}
		if (uuidToComponentMap.get(componentId) == null) {
			// if the component is not found in the workspace, check if it exists in the repository
			IComponentHandle componentHandle = (IComponentHandle)IComponent.ITEM_TYPE.createItemHandle(fRepository, componentItemId, null);
			try {
				fRepository.itemManager().fetchCompleteItem(componentHandle, ItemManager.DEFAULT, monitor);
			} catch (ItemNotFoundException e) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_id_not_found(componentId));
			}
			
			// the component exists in the repository, but not included in the workspace/snapshot
			if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_id_not_found_snapshot(componentId,
						loadConfigurationDescriptor.getSnapshotName()));
			} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_id_not_found_stream(componentId,
						loadConfigurationDescriptor.getStreamName()));
			} else {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_id_not_found_ws(componentId,
						wsConnection.getName()));
			}
		}

	}

	/**
	 * Validate if a component with the given item ID exists in the repository and included in the specified workspace.
	 * 
	 * @param loadConfigurationDescriptor Descriptor instance that represents load configuration. A build can be
	 *            configured to load from a workspace, snapshot, or a stream.
	 * @param wsConnection Workspace connection corresponding to the workspace in context/configured in the build
	 * @param componentName Name of the component
	 * @param nameToComponentMap Name to IComponent mapping for the components present in the workspace in context
	 * @param duplicateComponentNames Names associated with more than one component in the workspace
	 * @param clientLocale Locale of the requesting client
	 * @param progress progress monitor
	 * @return
	 * @throws TeamRepositoryException
	 */
	private String validateComponentWithNameExistsInWorkspace(LoadConfigurationDescriptor loadConfigurationDescriptor, IWorkspaceConnection wsConnection,
			String componentName, HashMap<String, IComponent> nameToComponentMap, List<String> duplicateComponentNames, Locale clientLocale,
			IProgressMonitor progress) throws TeamRepositoryException {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		String componentId = null;
		// first eliminate the duplicate components with same name case
		if (duplicateComponentNames.contains(componentName)) {
			if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_multiple_components_with_name_in_snapshot(
						componentName, loadConfigurationDescriptor.getSnapshotName()));
			} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_multiple_components_with_name_in_stream(
						componentName, loadConfigurationDescriptor.getStreamName()));
			} else {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_multiple_components_with_name_in_ws(componentName,
						wsConnection.getName()));
			}
		}

		// if component exists in workspace then we are fine
		if (nameToComponentMap.get(componentName) != null) {
			componentId = nameToComponentMap.get(componentName).getItemId().getUuidValue();
		} else {
			// see if a component with the given name exists in the repository
			IComponentSearchCriteria componentSearchCriteria = IComponentSearchCriteria.FACTORY.newInstance();
			componentSearchCriteria.setExactName(componentName);
			Collection<IComponentHandle> components = SCMPlatform.getWorkspaceManager(fRepository).findComponents(componentSearchCriteria,
					Integer.MAX_VALUE, monitor);
			if (components.size() == 0) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_name_not_found(componentName));
			} else {
				// component with the given name exists in the repository but not included in the workspace/snapshot
				if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
					throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_name_not_found_snapshot(
							componentName, loadConfigurationDescriptor.getSnapshotName()));
				} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
					throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_name_not_found_stream(
							componentName, loadConfigurationDescriptor.getStreamName()));
				} else {
					throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_component_with_name_not_found_ws(
							componentName, wsConnection.getName()));
				}
			}

		}
		return componentId;

	}

	/**
	 * Validate if the load rule file with the given id exists in the repository and included in the specified
	 * workspace/component context.
	 * 
	 * @param loadConfigurationDescriptor Descriptor instance that represents load configuration. A build can be
	 *            configured to load from a workspace, snapshot, or a stream.
	 * @param wsConnection Workspace connection corresponding to the workspace in context/configured in the build
	 * @param component Component in context
	 * @param fileItemId UUID of the load rule file
	 * @param clientLocale Locale of the requesting client
	 * @param progress progress monitor
	 * @throws TeamRepositoryException
	 */
	private void validateFileWithIdExistsInWorkspace(LoadConfigurationDescriptor loadConfigurationDescriptor, IWorkspaceConnection wsConnection, IComponent component,
			String fileItemId, Locale clientLocale, IProgressMonitor progress) throws TeamRepositoryException {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		UUID uuid = null;
		try {
			uuid = UUID.valueOf(fileItemId);
		} catch (IllegalArgumentException ex) {
			// invalid file item id
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_invalid_item_uuid(fileItemId,
					component.getName()), ex);
		}

		// check if a file with the given item id exists in the workspace/component
		// we can't check for the existence of the file in the repository using itemManager as it is an unmanaged item
		IFileItemHandle fileItemHandle = (IFileItemHandle)IFileItem.ITEM_TYPE.createItemHandle(fRepository, uuid, null);
		try {
			IVersionable versionable = wsConnection.configuration(component).fetchCompleteItem(fileItemHandle, monitor.newChild(50));
			// The item exists in the workspace/component context but not a file
			if (!(versionable instanceof IFileItem)) {
				if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
					throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_with_id_not_a_file_snapshot(
							fileItemId, component.getName(), loadConfigurationDescriptor.getSnapshotName()));

				} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
					throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_with_id_not_a_file_stream(
							fileItemId, component.getName(), loadConfigurationDescriptor.getStreamName()));
				} else {
					throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_with_id_not_a_file_ws(fileItemId,
							component.getName(), wsConnection.getName()));
				}

			}
		} catch (ItemNotFoundException e) {
			// The item does not exist in the workspace/component context
			if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_with_id_not_found_snapshot(
						fileItemId, component.getName(), loadConfigurationDescriptor.getSnapshotName()));

			} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_with_id_not_found_stream(fileItemId,
						component.getName(), loadConfigurationDescriptor.getStreamName()));
			} else {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_with_id_not_found_ws(fileItemId,
						component.getName(), wsConnection.getName()));
			}
		}
	}

	/**
	 * Validate if the load rule file in the given path exists in the workspace/component context.
	 * 
	 * @param loadConfigurationDescriptor Descriptor instance that represents load configuration. A build can be
	 *            configured to load from a workspace, snapshot, or a stream.
	 * @param wsConnection Workspace connection corresponding to the workspace in context/configured in the build
	 * @param component Component in context
	 * @param filePath Path to the load file in the repository
	 * @param clientLocale Locale of the requesting client
	 * @param progress progress monitor
	 * @return Item ID of the load rule file
	 * @throws TeamRepositoryException
	 */
	private String validateFileInPathExistsInWorkspace(LoadConfigurationDescriptor loadConfigurationDescriptor, IWorkspaceConnection wsConnection,
			IComponent component, String filePath, Locale clientLocale, IProgressMonitor progress) throws TeamRepositoryException {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
		// empty file path
		if (filePath.length() == 0) {
			throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_path_empty(component.getName()));
		}
		String tmpFilePath = filePath;
		// Having "/" at the beginning results in an empty path segment which is not accepted in resolvePath
		if (tmpFilePath.startsWith(SEPARATOR)) {
			tmpFilePath = tmpFilePath.substring(1);
		}
		String[] pathSegments = tmpFilePath.split(SEPARATOR);
		IVersionableHandle versionableHandle = wsConnection.configuration(component).resolvePath(component.getRootFolder(), pathSegments, monitor);

		if (versionableHandle == null) {
			// file not found
			if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_not_found_snapshot(filePath,
						component.getName(), loadConfigurationDescriptor.getSnapshotName()));

			} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_not_found_stream(filePath,
						component.getName(), loadConfigurationDescriptor.getStreamName()));
			} else {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_file_not_found_ws(filePath,
						component.getName(), wsConnection.getName()));
			}
		} else if (!(versionableHandle instanceof IFileItemHandle)) {
			// path doesn't resolve to a file
			if (loadConfigurationDescriptor.isSnapshotConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_not_a_file_snapshot(filePath,
						component.getName(), loadConfigurationDescriptor.getSnapshotName()));

			} else if (loadConfigurationDescriptor.isStreamConfiguration()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_not_a_file_stream(filePath,
						component.getName(), loadConfigurationDescriptor.getStreamName()));
			} else {
				throw new IllegalArgumentException(Messages.get(clientLocale).RepositoryConnection_load_rule_not_a_file_ws(filePath,
						component.getName(), wsConnection.getName()));
			}
		}
		return versionableHandle.getItemId().getUuidValue();
	}
	
	/**
	 * Inner class is used to represent the type of load configured in the build in both the build execution and field
	 * validation scenarios. We use different parameters to determine the load configuration across these two scenarios.
	 * This class acts wraps those parameters and provides an uniform interface to determine the load type.
	 */
	private static class LoadConfigurationDescriptor {
		@SuppressWarnings("unused")
		private BuildConfiguration buildConfiguration;
		private boolean isSnapshotConfiguration;
		private boolean isStreamConfiguration;
		private String snapshotName;
		private String streamName;
		
		/**
		 * Use build configuration to construct this instance. This constructor is used when the build is being executed.
		 */
		public LoadConfigurationDescriptor(BuildConfiguration buildConfiguration) {
			this.buildConfiguration = buildConfiguration;
			if (buildConfiguration != null && buildConfiguration.isSnapshotLoad()) {
				this.isSnapshotConfiguration = true;
				this.snapshotName = buildConfiguration.getBuildSnapshotDescriptor().getSnapshotName();
			} else if (buildConfiguration != null && buildConfiguration.isStreamLoad()) {
				this.isStreamConfiguration = true;
				this.streamName = buildConfiguration.getBuildStreamDescriptor().getName();
			}
		}
		
		/**
		 * Use the flag and stream name to construct this instance. This constructor is used during field validation.
		 */
		public LoadConfigurationDescriptor(boolean isStreamConfiguration, String streamName) {
			this.isStreamConfiguration = isStreamConfiguration;
			this.streamName = streamName;
		}

		public boolean isSnapshotConfiguration() {
			return isSnapshotConfiguration;
		}

		public boolean isStreamConfiguration() {
			return isStreamConfiguration;
		}

		public String getSnapshotName() {
			return snapshotName;
		}

		public String getStreamName() {
			return streamName;
		}
	}
}
