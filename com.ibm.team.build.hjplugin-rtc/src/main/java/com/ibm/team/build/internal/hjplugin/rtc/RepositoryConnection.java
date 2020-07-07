/*******************************************************************************
 * Copyright © 2013, 2019 IBM Corporation and others.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import com.ibm.team.build.internal.hjplugin.rtc.RTCSnapshotUtils.BuildSnapshotContext;
import com.ibm.team.build.internal.hjplugin.rtc.RTCWorkspaceUtils.LoadConfigurationDescriptor;
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
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.ServerVersionCheckException;
import com.ibm.team.repository.client.internal.ItemManager;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.transport.client.AuthenticationException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.BaselineSetFlags;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IChangeHistorySyncReport;

/**
 * A connection to the Jazz repository.
 */
public class RepositoryConnection {

	private static final Logger LOGGER = Logger.getLogger(RepositoryConnection.class.getName());
    private static final String DEFAULTWORKSPACEPREFIX = "HJP"; //$NON-NLS-1$

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
			RTCWorkspaceUtils.getInstance().getWorkspace(workspaceName, getTeamRepository(), monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * Tests that the specified stream is a valid build stream.
	 * 
	 * @param processAreaName - the name of the owning project or team area
	 * @param streamName The name of the stream. Never <code>null</code>.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @throw RTCValidationException if validation fails
	 * @throw {@link TeamRepositoryException} if there is an exception when communicating with the repository
	 * @throws Exception if an error occurs
	 */
	public void testBuildStream(String processAreaName, String streamName, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(50));
		try {
			getBuildStream(processAreaName, streamName, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * Tests that the specified snapshot is valid
	 * 
	 * @param buildSnapshotContext Object containing the snapshot owner details
	 * @param snapshotNameUUID The name or UUID of the snapshot. Never <code>null</code>
	 * @param progress A progress monitor to check for cancellation and mark progress
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception 
	 */
	public void testBuildSnapshot(BuildSnapshotContext buildSnapshotContext, String snapshotNameUUID, IProgressMonitor progress, Locale clientLocale)
			throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(50));
		try {
			RTCSnapshotUtils.getSnapshot(fRepository, buildSnapshotContext, snapshotNameUUID, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}	
	
	/**
	 * Returns the repository stream with the given streams name. If there is more than 1 repository stream with the name
	 * it is an error.  If there are no repository streams with the name it is an error.
	 * 
	 * @param processAreaName - the name of the owning project or team area
	 * @param streamName The name of the stream. Never <code>null</code>
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return The workspace connection for the workspace. Never <code>null</code>
	 * @throws Exception if an error occurs
	 */
	public IWorkspaceHandle getBuildStream(String processAreaName, String buildStream, IProgressMonitor progress, Locale clientLocale)
			throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
		return RTCWorkspaceUtils.getInstance().getStream(processAreaName, buildStream, getTeamRepository(), monitor.newChild(75), clientLocale);
	}
	
	/**
	 * Returns streamUUID corresponding to a build stream.
	 * 
	 * @param processAreaName - the name of the owning project or team area
	 * @param streamName - the name of the RTC stream
	 * @param progress - a progress monitor
	 * @param clientLocale - the locale of the client
	 * @returns the UUID of the stream as {@link String}
	 * @throws RTCConfigurationException if no stream is found or more than one stream with the same name is found
	 * @throws TeamRepositoryException if there is an exception when communicating with the Jazz repository
	 * @throws Exception if an error occurs
	 */
	public String getBuildStreamUUID(String processAreaName, String buildStream, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(25));
		return RTCWorkspaceUtils.getInstance().getStreamUUID(processAreaName, buildStream, getTeamRepository(), progress, clientLocale);
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
		LOGGER.finest("RepositoryConnection.incomingChanges: Enter");
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(10));
		BuildWorkspaceDescriptor workspace;
		if (buildDefinitionId != null && buildDefinitionId.length() > 0) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("RepositoryConnection.incomingChanges: Computing incoming changes for buildDefinition '" + buildDefinitionId + "'");
			}
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
			if (LOGGER.isLoggable(Level.FINER) && buildWorkspaceName != null) {
				LOGGER.finer("RepositoryConnection.incomingChanges: Computing incoming changes for buildWorkspace '"
								+ buildWorkspaceName + "'");
			}
			IWorkspaceHandle workspaceHandle = RTCWorkspaceUtils.getInstance().getWorkspace(buildWorkspaceName, getTeamRepository(), monitor.newChild(10), clientLocale);
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
	 * @param processAreaName - the name of the owning project or team area. May be <code>null</code>
	 * @param buildResultUUID The id of the build result (which also contains the build request & build definition instance)
	 * May be <code>null</code> if buildWorkspaceName is supplied. Only one of buildResultUUID/buildWorkspaceName/buildStream should be
	 * supplied
	 * @param buildWorkspaceName Name of the RTC build workspace (changes will be accepted into it)
	 * May be <code>null</code> if buildResultUUID is supplied. Only one of buildResultUUID/buildWorkspaceName/buildStream should be
	 * supplied
	 * @param buildSnapshotContext Object containing the snapshot owner details. May be <code>null<code>
	 * @param buildSnapshot Name of the RTC buildsnapshot. An empty changelog with snapshot link will be created. May be <code>null</code>
	 * @param buildStream Name of the RTC build stream
	 * May be <code>null</code> if buildResultUUID or buildWorkspace is supplied. Only one of buildResultUUID/buildWorkspaceName/buildStream should be
	 * supplied
	 * @param hjFetchDestination The location the build workspace is to be loaded
	 * @param changeReport The report to be built of all the changes accepted/discarded. May be <code> null </code>.
	 * @param isCustomSnapshotName Indicates if a custom snapshot name is configured in the Job
	 * @param snapshotName The name to set on the snapshot that is created during accept 
	 * @param previousSnapshotUUID The uuid of the previous snapshot for comparison. Used for generating changelog for buildStream. May be <code>null</code>
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @param callConnectorTimeout user defined call connector timeout
     * @param acceptBeforeLoad If <code>true</code> then, the changes from the flow target is synced to the repository workspace
     * @param addLinksToWorkItems If <code>true</code> then, Jenkins build link will be added to all the work items 
     * 							  in the accepted change sets
	 * @param jenkinsBuildUrls 
	 * 				The URL of the previous and current Jenkins builds. 
	 * 				The URL of the previous Jenkins build used for comparison and is persisted into the changelog
	 * @param temporaryWorkspaceComment
	 * @return <code>Map<String, Object></code> containing build properties, and CallConnector details
	 * @throws Exception Thrown if anything goes wrong
	 */
	public Map<String, Object> accept(String processAreaName, String buildResultUUID, String buildWorkspaceName,
			BuildSnapshotContext buildSnapshotContext, String buildSnapshot, String buildStream, String hjFetchDestination,
			ChangeReport changeReport, boolean isCustomSnapshotName, String snapshotName, final String previousSnapshotUUID,
			final IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale, String callConnectorTimeout,
			boolean acceptBeforeLoad, boolean addLinksToWorkItems,
			Map<String, String> jenkinsBuildUrls, String temporaryWorkspaceComment, 
			Map<String, Object> options)
			throws Exception {
		LOGGER.finest("RepositoryConnection.accept : Enter"); //$NON-NLS-1$

		String previousBuildUrl = (jenkinsBuildUrls.containsKey(Constants.PREVIOUS_BUILD_URL)) ? jenkinsBuildUrls.get(Constants.PREVIOUS_BUILD_URL) : null;
		String currentBuildFullUrl = (jenkinsBuildUrls.containsKey(Constants.CURRENT_BUILD_URL)) ?
					jenkinsBuildUrls.get(Constants.CURRENT_BUILD_FULL_URL) : null;
		String currentBuildLabel = (jenkinsBuildUrls.containsKey(Constants.CURRENT_BUILD_LABEL)) ?
					jenkinsBuildUrls.get(Constants.CURRENT_BUILD_LABEL) : Constants.GENERIC_JENKINS_BUILD;
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(1));
		
		Map<String, Object> metronomeOptions = (Map<String,Object>)getMetronomeOptions(options);
		boolean shouldCreateMetronomeReport = shouldCreateMetronomeReport(metronomeOptions);
        MetronomeReporter reporter = null;
        if (shouldCreateMetronomeReport) {
        	reporter = new MetronomeReporter(getTeamRepository()); 
        }
        
		// Because of this return, we will put the metronome reporter into call connector
        // Since we have started setting call connector for stream and snapshot configuration, 
        // in load() method, these should deal with null workspaceConnection.
        // Also set call connector for build stream and snapshot only if metronome should be created.
        // Therefore, metronome reporter will be null whether shouldReportMetronome is false
		if (buildStream != null) {
			return acceptForBuildStream(processAreaName, buildStream, changeReport, snapshotName, previousSnapshotUUID, previousBuildUrl,
					currentBuildFullUrl, currentBuildLabel, temporaryWorkspaceComment, 
						addLinksToWorkItems, reporter, callConnectorTimeout,
					listener, monitor, clientLocale);
		}
		else if (buildSnapshot != null) {
			return acceptForBuildSnapshot(buildSnapshotContext, buildSnapshot, changeReport, reporter, 
						callConnectorTimeout, listener, progress, clientLocale);
		} else {
		
			BuildWorkspaceDescriptor workspace;
			
			BuildConfiguration buildConfiguration = new BuildConfiguration(getTeamRepository(), hjFetchDestination);	
	
			// If we have a build result, the build definition that describes what is to be accepted into & how to load
			// Otherwise, the build workspace on the Jenkins definition is used.
			IBuildResultHandle buildResultHandle = null;
			if (buildResultUUID != null && buildResultUUID.length() > 0) {
				buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
				buildConfiguration.initialize(buildResultHandle, isCustomSnapshotName, snapshotName, false, listener, monitor.newChild(1),
						clientLocale);
				// If Jenkins build says don't create metronome report,
				// then check whether build definition says create metronome report
				// If that is true, then initialize metronome reporter.
				if (shouldCreateMetronomeReport == false) {
					shouldCreateMetronomeReport =  shouldCreateMetronomeReport(buildConfiguration);
					// If Build Definition says that metronome log has to be created, then create it now
					if (shouldCreateMetronomeReport) {
			        	reporter = new MetronomeReporter(getTeamRepository()); 
			        }
				}
			} else {
				IWorkspaceHandle workspaceHandle = RTCWorkspaceUtils.getInstance().getWorkspace(buildWorkspaceName, getTeamRepository(),
						monitor.newChild(1), clientLocale);
				buildConfiguration.initialize(workspaceHandle, buildWorkspaceName, snapshotName, acceptBeforeLoad, null, null, false, null, null, listener,
						clientLocale, monitor.newChild(1));
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
	            	// If we are dealing with a pre-70 toolkit, then we cannot control creation of build result links 
	            	// to work items
	            	if (VersionCheckerUtil.isPre70BuildToolkit()) {
	            		workItemPublisher.publish(buildResultHandle, acceptReport.getAcceptChangeSets(), getTeamRepository());	
	            	} else {
		            	// If the property IJazzScmConfigurationElement.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS is true,
		            	// then continue to publish the build links in work item
	            		// Default value if the property doesn't exist is true.
	            		String includeBuildResultLinksInWorkItems = 
	            						buildConfiguration.getBuildProperty(Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "true");
	            		workItemPublisher.publish(buildResultHandle, acceptReport.getAcceptChangeSets(), 
	            						Boolean.parseBoolean(includeBuildResultLinksInWorkItems), getTeamRepository());
	            	}
	            }
	            
	            if (monitor.isCanceled()) {
	            	throw new InterruptedException();
	            }
	            if (changeReport != null) {
		            	IBuildDefinition definition = null;
		        		if (buildResultHandle != null) {
		        			IBuildResult result = (IBuildResult) fRepository.itemManager().fetchPartialItem(buildResultHandle, ItemManager.REFRESH, Arrays.asList(new String[] {IBuildResult.PROPERTY_BUILD_DEFINITION}), monitor.newChild(1) );
		        			definition = (IBuildDefinition) fRepository.itemManager().fetchPartialItem(result.getBuildDefinition(), ItemManager.REFRESH, Arrays.asList(new String[] {IBuildDefinition.PROPERTY_ID}), monitor.newChild(1));
		        		}
			            // build change report
			            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
			            changeReportBuilder.populateChangeReport(changeReport,
			            		workspaceConnection.getResolvedWorkspace(), workspaceConnection.getName(),
			            		acceptReport,
			            		(definition != null) ? definition : null, (definition != null) ? definition.getId() : null,
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
	        	
	        	// Create a link in all the work items to the current jenkins build url if it is a build from repository worksapce
	        	if (buildConfiguration.isRepositoryWorkspaceConfiguration() && 
	        			currentBuildFullUrl != null) {
	        		if (addLinksToWorkItems) {
		        		LOGGER.info("Adding Jenkins build URL as \"Related Artifacts\" to work items"); //$NON-NLS-1$
		        		// Get the work items
		        		List<Integer> workItems = changeReport.getAcceptedWorkItems();
		        		try {
			        		// Create a link to the current Jenkins build in the work items
			        		WorkItemUtils.addRelatedLinkToWorkItems(this.fRepository, workItems, currentBuildFullUrl, currentBuildLabel);
		        		} catch (Exception exp) {
		        			// Log the exception but do not fail the build
		        			LOGGER.log(Level.WARNING, 
		        					String.format("Error adding Jenkins build URL as \"Related Artifacts\" to work items %s", workItems.toString()), //$NON-NLS-1$ 
		        					exp);
		        		}
	        		}
	        	}
	        }
	
	        buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
	        Map<String, Object> result = new HashMap<String, Object>();
	        result.put(Constants.BUILD_PROPERTIES, buildProperties);
		        
	        // lets cache the workspace connection object for subsequent "load" call...
	        CallConnector<CallConnectorData> callConnector = null;
	        String connectorId = ""; //$NON-NLS-1$
	        try {
	        	CallConnectorData cData = new CallConnectorData(workspaceConnection, reporter);
	            if ((callConnectorTimeout != null) && (!"".equals(callConnectorTimeout)) && callConnectorTimeout.matches("\\d+")) { //$NON-NLS-1$ //$NON-NLS-2$
	            	long timeout = Long.parseLong(callConnectorTimeout) * 1000;
	            	callConnector = new CallConnector<CallConnectorData>(cData, timeout);
	            } else {
	            	callConnector = new CallConnector<CallConnectorData>(cData);
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
    }

	private Map<String, Object> getMetronomeOptions(Map<String, Object> options) {
		Map<String, Object> metronomeOptions = new HashMap<String, Object>();
		if (options != null && options.containsKey(Constants.METRONOME_OPTIONS_PROPERTY_NAME)) {
			return (Map<String, Object>) options.get(Constants.METRONOME_OPTIONS_PROPERTY_NAME);
		}
		return metronomeOptions;
	}

	/**
	 * Load the build workspace. This call is expected to follow a call to accept method.
	 * 
	 * @param processAreaName - the name of the owning project or team area. May be <code>null</code>
	 * @param buildResultUUID The id of the build result (which also contains the build request & build definition
	 *            instance) May be <code>null</code> if buildWorkspaceName is supplied. Only one of
	 *            buildResultUUID/buildWorkspaceName should be supplied
	 * @param buildWorkspaceName Name of the RTC build workspace (changes will be accepted into it) May be
	 *            <code>null</code> if buildResultUUID is supplied. Only one of buildResultUUID/buildWorkspaceName
	 *            should be supplied
	 * @param buildSnapshotContext Object containing the snapshot owner details. May be <code>null</code>
	 * @param buildSnapshot The name or UUID of the RTC build snapshot.
	 * @param buildStream The name or UUID of the RTC build stream.
	 * @param buildStreamData The additional stream data for stream load.
	 * @param hjFetchDestination The location the build workspace is to be loaded
	 * @param isCustomSnapshotName Indicates if a custom snapshot name is configured in the Job
	 * @param snapshotName The name of the snapshot created during accept
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @param buildProperties buildProperties created so far by accept call
	 * @param connectorId CallConnector id to retrieve shared data (workspaceConnection) from, that was created by
	 *            accept call
	 * @param extProvider Extension provider
	 * @param logger logger object
	 * @param loadPolicy load policy value that determines whether to use a load rule file or component load
	 *            configuration. We expect a null value or a non-null trimmed value.
	 * @param componentLoadConfig when load policy is set to use component load config this field determines whether to
	 *            load all components or exclude some components. We expect a non-null value or a null trimmed value.
	 * @param componentsToExclude List of components to exclude
	 * @param pathToLoadRuleFile Path to the load rule file.
	 * @param isDeleteNeeded true if Jenkins job is configured to delete load directory before fetching
	 * @param createFoldersForComponents create folders for components if true
     * @param acceptBeforeLoad If <code>true</code> then, the changes from the flow target is synced to the repository workspace
	 * @param temporaryWorkspaceComment - Description to be used when creating the repository workspace
	 * @param shouldDeleteTemporaryWorkspace whether the temporary workspace created for snapshot/stream case should be
	 *            deleted before returning(irrespective of failure or not)
	 * @throws Exception Thrown if anything goes wrong
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> load(String processAreaName, String buildResultUUID, String buildWorkspaceName,
			BuildSnapshotContext buildSnapshotContext, String buildSnapshot, String buildStream, Map<String, String> buildStreamData,
			String hjFetchDestination, boolean isCustomSnapshotName, String snapshotName, final IConsoleOutput listener, IProgressMonitor progress,
			Locale clientLocale, String parentActivityId, String connectorId, Object extProvider, final PrintStream logger, String loadPolicy,
			String componentLoadConfig, String componentsToExclude, String pathToLoadRuleFile, boolean isDeleteNeeded,
			boolean createFoldersForComponents, boolean acceptBeforeLoad, String temporaryWorkspaceComment, boolean shouldDeleteTemporaryWorkspace,
			Map<String, Object>options)
			throws Exception {
		LOGGER.finest("RepositoryConnection.load : Enter"); //$NON-NLS-1$

		// Lets get same workspaceConnection as created by accept call so that if
        // we are synchronizing, we use the same cached sync times.
        CallConnectorData callConnectorData = null;
        IWorkspaceConnection workspaceConnection  = null;
        MetronomeReporter reporter = null;
        // Earlier callConnector was set only for build definition or workspace
        // configuration and not for stream and snapshot.
        // So for stream and snapshot, conenctorId is null.
        // Also for stream and snapshot, workspaceConnection was null.
        // From now on, for stream and snapshot, call connector id will be non null 
        // only if metronome needs to be collected.
        // TODO Add these to test cases.
        if (connectorId != null &&  !("".equals(connectorId))) { //$NON-NLS-1$
        	callConnectorData = (CallConnectorData) CallConnector.getValue(Long.parseLong(connectorId));
        	if (callConnectorData != null) {
        		workspaceConnection = callConnectorData.workspaceConnection;
        		reporter = callConnectorData.metronome;
        	}
        }

		SubMonitor monitor = SubMonitor.convert(progress, 100);

		ensureLoggedIn(monitor.newChild(1));
		
		// If metronome reporter is null, then reinitailize it
		// Since we are sharing the reporter, the details get added up
		Map<String, String> metronomeData = new HashMap<String, String>();
		
		Map<String, Object> metronomeOptions = (Map<String,Object>)getMetronomeOptions(options);
		boolean shouldCreateMetronomeReport = shouldCreateMetronomeReport(metronomeOptions);
		if (shouldCreateMetronomeReport) {
			reporter = initializeMetronomeReporter(getTeamRepository(), reporter, listener);
		}
		
        BuildConfiguration buildConfiguration = new BuildConfiguration(getTeamRepository(), hjFetchDestination);
		
		// If we have a build result, the build definition that describes 
        // what is to be accepted into & how to load
		// Otherwise, the build workspace on the Jenkins definition is used.
		IBuildResultHandle buildResultHandle = null;
		if (buildResultUUID != null && buildResultUUID.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_definition_configuration());
			buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
			buildConfiguration.initialize(buildResultHandle, isCustomSnapshotName, snapshotName, loadPolicy != null
					&& Constants.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES.equals(loadPolicy), listener, monitor.newChild(1), clientLocale);
			// Check if the property is set in the build definition. If the value in the Jenkins job is true, then it will override 
			// the build definition property's value.
			shouldCreateMetronomeReport = (shouldCreateMetronomeReport == false) ? shouldCreateMetronomeReport(buildConfiguration) : shouldCreateMetronomeReport;
		} else if (buildWorkspaceName != null && buildWorkspaceName.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_workspace_configuration());
			IWorkspaceHandle workspaceHandle = RTCWorkspaceUtils.getInstance().getWorkspace(buildWorkspaceName, getTeamRepository(),
					monitor.newChild(1), clientLocale);
			buildConfiguration.initialize(workspaceHandle, buildWorkspaceName, snapshotName, acceptBeforeLoad, loadPolicy, componentLoadConfig,
					createFoldersForComponents, componentsToExclude, pathToLoadRuleFile, listener, clientLocale, monitor.newChild(1));
		} else if (buildSnapshot != null && buildSnapshot.length() > 0) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_using_build_snapshot_configuration());
			String workspaceNamePrefix = getWorkspaceNamePrefix();
			IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshot(getTeamRepository(), buildSnapshotContext, buildSnapshot, monitor.newChild(1), clientLocale);
			IContributor contributor = fRepository.loggedInContributor();
			buildConfiguration.initialize(baselineSet, contributor, workspaceNamePrefix, temporaryWorkspaceComment, loadPolicy, componentLoadConfig,
					createFoldersForComponents, componentsToExclude, pathToLoadRuleFile, listener, clientLocale, monitor.newChild(3));
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
			IWorkspace workspace = null;
			try {
				IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshotByUUID(getTeamRepository(), snapshotUUID, monitor.newChild(1), clientLocale);
				workspace = RTCWorkspaceUtils.getInstance().getWorkspace(UUID.valueOf(workspaceUUID), getTeamRepository(), monitor.newChild(3), clientLocale);
				IContributor contributor = fRepository.loggedInContributor();
				IWorkspaceHandle streamHandle = getBuildStream(processAreaName, buildStream, monitor.newChild(1), clientLocale);
				buildConfiguration.initialize(streamHandle, buildStream, workspace, baselineSet, shouldDeleteTemporaryWorkspace, contributor,
						loadPolicy, componentLoadConfig, createFoldersForComponents, componentsToExclude, pathToLoadRuleFile, listener, clientLocale, monitor.newChild(3));
			} catch (Exception exp) {
				if (workspace != null) {
					RTCWorkspaceUtils.getInstance().deleteSilent(workspace,
							getTeamRepository(), progress, listener, clientLocale);
				}
				throw exp;
			}
		} else {
    		String errorMessage = Messages.get(clientLocale).RepositoryConnection_invalid_load_configuration();
    		TeamBuildException exception = new TeamBuildException(errorMessage);
    		listener.log(errorMessage, exception);
    		throw exception;
		}
		
		boolean exceptionOccured = false;
		try {
		
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
	        	boolean isLoadPolicySetToUseDynamicLoadRules = buildConfiguration.isLoadPolicySetToUseDynamicLoadRules();

				if (extProvider != null && isLoadPolicySetToUseDynamicLoadRules) {
					compLoadRules = RtcExtensionProviderUtil.getComponentLoadRules(extProvider, logger, workspace.getWorkspaceHandle().getItemId()
							.getUuidValue(), workspaceConnection.getName(), snapshotUUID, buildResultUUID, componentInfo,
							fConnectionDetails.getRepositoryAddress(), fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
					excludeComponents = RtcExtensionProviderUtil.getExcludeComponentList(extProvider, logger, workspace.getWorkspaceHandle()
							.getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, buildResultUUID, componentInfo,
							fConnectionDetails.getRepositoryAddress(), fConnectionDetails.getUserId(), fConnectionDetails.getPassword());

					if (excludeComponents != null && excludeComponents.trim().length() > 0) {
						includedComponents = false;
						components = new LoadComponents(getTeamRepository(), excludeComponents).getComponentHandles();
					}
				}
	            
	    		if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("RepositoryConnection.load : updatingFileCopyArea");
				}
	    		
				// if load policy is set to useLoadRules or (useDynamicLoadRules && old interface for component load
				// rules doesn't return load rules), drive the load exclusively using load rules
				if (buildConfiguration.isLoadPolicySetToUseLoadRules() || (isLoadPolicySetToUseDynamicLoadRules && compLoadRules == null)) {
					if (LOGGER.isLoggable(Level.FINER)) {
						LOGGER.finer("If load rules are specified only those components included in the load rules will be loaded");
					}
					
					String pathToDynamicLoadRuleFile = null;
					if (extProvider != null && isLoadPolicySetToUseDynamicLoadRules) {
						pathToDynamicLoadRuleFile = RtcExtensionProviderUtil.getPathToLoadRuleFile(extProvider, logger, workspace
								.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, buildResultUUID,
								componentInfo, fConnectionDetails.getRepositoryAddress(), fConnectionDetails.getUserId(),
								fConnectionDetails.getPassword());
					}
		            
					Collection<ILoadRule2> loadRulesInstance = buildConfiguration.getComponentLoadRules(workspaceConnection, pathToDynamicLoadRuleFile, logger,
							monitor.newChild(1), clientLocale);
					// if load rules are configured we need a 603 or above build toolkit
					if (loadRulesInstance.size() > 0 && VersionCheckerUtil.isPre603BuildToolkit()) {
						throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_load_rules_pre_603_build_toolkit());
					}
					// if load rules are specified use the interface introduced in 603
					if (loadRulesInstance.size() > 0) {
						if (LOGGER.isLoggable(Level.FINER)) {
							LOGGER.finer("RepositoryConnection.load : Load rules are specified. Using 603 interface of SourceControlUtility.updateFileCopyArea");
						}
						// we will always have a single entry in the loadRules collection, as specifying multiple load
						// rules file will result in an error
						SourceControlUtility.updateFileCopyArea(workspaceConnection, fetchDestinationFile.getCanonicalPath(), synchronizeLoad,
								loadRulesInstance.iterator().next(), false, monitor.newChild(39));
					} else {
						if (LOGGER.isLoggable(Level.FINER)) {
							LOGGER.finer("RepositoryConnection.load : Load rules are not specified. Using pre-603 interface of SourceControlUtility.updateFileCopyArea");
						}
						SourceControlUtility.updateFileCopyArea(workspaceConnection, fetchDestinationFile.getCanonicalPath(), false,
								Collections.EMPTY_LIST, synchronizeLoad, Collections.EMPTY_LIST, false, monitor.newChild(39));
					}
				} else {
					// either load policy is not set or it is set to use component load config or load policy is set to
					// useDynamicLoadRules and the dynamic load rule generator still returns the load rules using the
					// deprecated getComponentLoadRules interface
					if (LOGGER.isLoggable(Level.FINER)) {
						if (buildConfiguration.isLoadPolicySet()) {
							if (buildConfiguration.isLoadPolicySetToUseComponentLoadConfig()) {
								LOGGER.finer("RepositoryConnection.load: Load Policy is set to " + Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG
										+ ". Any specified load rules will be ignored.");
								if (buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents()) {
									LOGGER.finer("RepositoryConnection.load: Component load config set to "
											+ Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS
											+ ". All components in the workspace will be loaded.");
								} else {
									LOGGER.finer("RepositoryConnection.load: Component load config set to "
											+ Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS
											+ ". Components specified to be excluded will not be loaded.");
								}
							} else if (buildConfiguration.isLoadPolicySetToUseDynamicLoadRules()) {
								LOGGER.finer("RepositoryConnection.load: Load Policy is set to " + Constants.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES
										+ " and dynamic load rules are provided by the deprecated getComponentLoadRules method");
							}
						} else {
							LOGGER.finer("RepositoryConnection.load: Load policy is not set. "
									+ "All components in the workspace will be loaded. If load rules are specified then components included "
									+ "in the load rules will be loaded according to the load rules ");
						}
					}
					// dynamic load rules take precedence over configured load rules
					Collection<ILoadRule2> lRules = buildConfiguration.isLoadPolicySetToUseDynamicLoadRules() ? buildConfiguration
							.getCustomLoadRules(workspaceConnection, compLoadRules, logger, clientLocale) : buildConfiguration.getComponentLoadRules(
							workspaceConnection, null, logger, monitor.newChild(1), clientLocale);
					SourceControlUtility.updateFileCopyArea(workspaceConnection, fetchDestinationFile.getCanonicalPath(), includedComponents,
							components, synchronizeLoad, lRules, buildConfiguration.createFoldersForComponents(), monitor.newChild(39));
				}
	         
	         
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
	            
	            // Try to push the log but don't fail the build if you can't
	            // We try to minimize the number of variable that can be null.
	            // What we know at this point is metronomeOptions, metronomeData, listener, clientLocale and monitor are non null
	            publishMetronomeLog(reporter, shouldCreateMetronomeReport, buildResultHandle, metronomeData, metronomeOptions, 
	            		listener, clientLocale, monitor);	            	 
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
		} catch (Exception exp) {
			exceptionOccured = true;
			throw exp;
		} finally {
			// Finalize the buildConfiguration
			buildConfiguration.tearDown(fRepositoryManager, exceptionOccured, monitor.newChild(1), listener, clientLocale);
		}

		// Add temporary workspace details as the return value
		Map<String, Object> loadResult = new HashMap<String, Object> ();
		loadResult.put(Constants.TEMPORARY_REPO_WORKSPACE_DATA, buildConfiguration.getTemporaryRepositoryWorkspaceProperties());
		// Again, to reduce null handling, we might end up sending empty data. Hence this has not been wrapped into 
		// a shouldCreateMetronome check.
		loadResult.put(Constants.METRONOME_DATA_PROPERTY_NAME, metronomeData);
 		return loadResult;
	}

	private MetronomeReporter initializeMetronomeReporter(ITeamRepository teamRepository, MetronomeReporter param, 
												IConsoleOutput listener) throws Exception {
		MetronomeReporter reporter = param;
		if (reporter == null) {
        	if (listener != null) {
        		listener.log("Reinitializing metronome data"); //$NON-NLS-1$
        	}
        	reporter = new MetronomeReporter(getTeamRepository());
		} else {
			reporter.addTeamRepository(getTeamRepository());
		}
		return reporter;
	}

	/**
	 * Push the log to the build result if required and store the content in a hashmap.
	 * @param reporter The metronome reporter, may be <code>null</code>
	 * @param buildResultHandle The build result to publish metronome logs to.
	 * 				May be <code>null</code>
	 * @param metronomeData The map into which metronome data should be put into
	 * @param metronomeOptions Additional metronome options sent from hjplugin layer.
	 * @param listener Listener to output messages. Never <code>null</code>
	 * @param monitor To monitor progress. May be <code>null</code>
	 * @param createMetronomeReport. Whether to create metrnome report. Use this to determine to 
	 * 								publish metronome logs
	 */
	private void publishMetronomeLog(MetronomeReporter reporter, boolean createMetronomeReport, IBuildResultHandle buildResultHandle,
			Map<String, String> metronomeData, Map<String, Object> metronomeOptions, final IConsoleOutput listener,
			 Locale clientLocale, SubMonitor monitor) {
		try {
			if (createMetronomeReport && reporter != null) {
				// For Build result, push the data to RTC
				// For all configurations, add the metronome data to
				// properties

	    		String reportCSV = reporter.reportCSVFormat();
	    		String report = reporter.report("statistics");

				if (buildResultHandle != null) {
		    		BuildConnection.publishLog(getTeamRepository(), buildResultHandle, report, 
		    											getStatsReportFilePrefix(metronomeOptions),
		    											getStatisticsReportFileSuffix(metronomeOptions), 
		    											getStatisticsReportLabel(metronomeOptions),
		    											monitor.newChild(1));
					BuildConnection.publishLog(getTeamRepository(), buildResultHandle, reportCSV, 
									getStatsDataFilePrefix(metronomeOptions), 
									getStatisticsDataFileSuffix(metronomeOptions), 
									getStatisticsDataLabel(metronomeOptions),
									monitor.newChild(1));
				} 
				metronomeData.put(Constants.STATISTICS_REPORT_PROPERTY_NAME, report);
				metronomeData.put(Constants.STATISTICS_DATA_PROPERTY_NAME, reportCSV);
			}
		} catch (Exception e) {
			listener.log(Messages.get(clientLocale).RepositoryConnection_unable_to_publish_metronome_log(), e); //$NON-NLS-1$
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
	        buildConfiguration.initialize(buildResultHandle, false, defaultSnapshotName, false, listener, monitor.newChild(1), clientLocale);

		} else {
			IWorkspaceHandle workspaceHandle = RTCWorkspaceUtils.getInstance().getWorkspace(buildWorkspaceName, getTeamRepository(),
					monitor.newChild(1), clientLocale);
			buildConfiguration.initialize(workspaceHandle, buildWorkspaceName, defaultSnapshotName);
		}
		
		Map<String, String> buildProperties = buildConfiguration.getBuildProperties();

        workspace = buildConfiguration.getBuildWorkspaceDescriptor();

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
		            		workspaceConnection.getResolvedWorkspace(), workspaceConnection.getName(),
		            		acceptReport,
		            		null, null,
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
            
            String pathToDynamicLoadRuleFile = null;
            boolean includedComponents = buildConfiguration.includeComponents();
            Collection<IComponentHandle> components = buildConfiguration.getComponents();
            String snapshotUUID = buildProperties.get(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID);
        	Map<String, String> componentInfo = getComponentInfoFromWorkspace(workspaceConnection.getComponents(), monitor.newChild(2));
            if(extProvider != null) {
				pathToDynamicLoadRuleFile = RtcExtensionProviderUtil.getPathToLoadRuleFile(
						extProvider, logger, workspace.getWorkspaceHandle().getItemId().getUuidValue(), workspaceConnection.getName(), snapshotUUID, 
						buildResultUUID, componentInfo, fConnectionDetails.getRepositoryAddress(), 
						fConnectionDetails.getUserId(), fConnectionDetails.getPassword());
			}
            
			SourceControlUtility.updateFileCopyArea(workspaceConnection, fetchDestinationFile.getCanonicalPath(), includedComponents, components,
					synchronizeLoad,
					buildConfiguration.getComponentLoadRules(workspaceConnection, pathToDynamicLoadRuleFile, logger, monitor.newChild(1), clientLocale),
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
	 * @param buildLabel - the label for the build result
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
			personalBuildWorkspace = RTCWorkspaceUtils.getInstance().getWorkspace(personalBuildWorkspaceName, getTeamRepository(),
					monitor.newChild(25), clientLocale);
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
	 * Given a build stream, find whether the stream has new changes when compared to some previous state 
	 * by first computing the state of the stream and subtracting it from the previous state.
	 *  
	 * @param processAreaName - the name of the owning project or team area
	 * @param buildStream - the stream name
	 * @param streamChangesData - the previous state of the stream.
	 * @param clientConsole
	 * @param progress
	 * @param clientLocale
	 * @return a {@link BigInteger} that is 0 if there is no new changes, non zero otherwise. 
	 * @throws TeamRepositoryException - if there is an error communicating with repository.
	 * @throws RTCConfigurationException - if there is no stream with the given name or more than one stream with the same name. 
	 * @throws IOException - if there is an error computing stream state.
	 * @throws NoSuchAlgorithmException - if there is an error computing stream state. 
	 * @throws Exception if an error occurs
	 */
	public BigInteger computeIncomingChangesForStream(String processAreaName, String buildStream, String streamChangesData,
			IConsoleOutput clientConsole, IProgressMonitor progress, Locale clientLocale) throws Exception {
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
		IWorkspaceHandle streamHandle = getBuildStream(processAreaName, buildStream, monitor.newChild(30), clientLocale);
		BigInteger streamDataHash = RTCWorkspaceUtils.getInstance().getDigestNumber(getTeamRepository(), streamHandle, monitor.newChild(10));

		return (streamDataHash.subtract(previousStreamHash));
	}
	
	/**
	 * Given a build stream by name, perform an accept.
	 * This involves the following
	 * <ol>
	 * <li>Create a workspace from the stream</li>
	 * <li>Take a snapshot on the workspace</li>
	 * <li>Change the owner of the snapshot back to the stream</li>
	 * <li>If previous snapshot UUID is given, compare that with the snapshot created and create a {@link ChangeReport}</li>
	 * <li>Write the change report into change log</li>
	 * <li>Add some properties to build properties and store it in the map (which is the return value) </li>
	 * </ol>
	 * @param processAreaName - the name of the owning project or team area
	 * @param buildStream - the name of the RTC stream
	 * @param changeReport - the change report to which the change log has to be written into
	 * @param snapshotName The name to set on the snapshot that is created during accept 
	 * @param previousSnapshotUUID - the UUID of the previous snapshot
	 * @param previousBuildURL - the URL of the previous Jenkins build from which the previous snapshot uuid was taken
	 * @param currentBuildURL - the URL of the current Jenkins build
	 * @param temporaryWorkspaceComment -
	 * @param listener
	 * @param progress
	 * @param clientLocale
	 * @return a {@link Map} of String to {@link Object}s
	 * @throws {@link Exception} - if there is any error during the operation
	 */
	private Map<String, Object> acceptForBuildStream(final String processAreaName, final String buildStream, final ChangeReport changeReport,
			final String snapshotName, final String previousSnapshotUUID, final String previousBuildURL, 
			String currentBuildURL, String currentBuildLabel, String temporaryWorkspaceComment, boolean addLinksToWorkItems,
			MetronomeReporter reporter, String callConnectorTimeout, final IConsoleOutput listener, 
			final IProgressMonitor progress, final Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);

        Map<String, Object> result = new HashMap<String, Object>();
		Map<String, String> buildProperties = new HashMap<String, String>();

		IContributor contributor = fRepository.loggedInContributor();
		IWorkspaceHandle streamHandle = getBuildStream(processAreaName, buildStream, monitor.newChild(1), clientLocale);
    	
    	String workspaceName = getWorkspaceNamePrefix() + "_" + Long.toString(System.currentTimeMillis());

		/*
		 *  Create a workspace from the stream
		 *  Take a snapshot on the workspace
		 *  Change the snapshot owner to the stream
		 *  Generate the change report
		 *  Create a link in the work items to the Jenkins build  
		 */

    	// Get the workspace connection for the stream
		IWorkspaceConnection streamConnection = SCMPlatform.getWorkspaceManager(getTeamRepository()).getWorkspaceConnection(streamHandle, monitor.newChild(1));
		
		IWorkspaceConnection workspaceConnection = null;
		try {
			// Create a workspace from the stream
			workspaceConnection = SCMPlatform.getWorkspaceManager(getTeamRepository()).createWorkspace(contributor, workspaceName, temporaryWorkspaceComment, null, streamConnection, monitor.newChild(5));
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finest("RepositoryConnection.accept for stream : Created temporary workspace '" + workspaceName + "'");
			}
	
			// Create a baseline set for the workspace
			IBaselineSetHandle baselineSet = workspaceConnection.createBaselineSet(null, snapshotName, null, BaselineSetFlags.DEFAULT, monitor.newChild(3));
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finest("RepositoryConnection.accep for stream : Created snapshot '" + snapshotName + "'.");
			}
			
			// Change the owner of the baselineset to the stream
			streamConnection.addBaselineSet(baselineSet, monitor.newChild(2));
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finest("RepositoryConnection.accep for stream : Changed owner of snapshot '" + snapshotName + "' to stream '" + buildStream +"'.");
			}
			
			// Get the {@IBaselineHandle} of the previous snapshot
			IBaselineSetHandle previousSnapshot = null;
			if (previousSnapshotUUID != null) {
				try {
					previousSnapshot = RTCSnapshotUtils.getSnapshotByUUID(getTeamRepository(), previousSnapshotUUID, monitor.newChild(1), clientLocale);
				} catch (Exception exp) {
					if (LOGGER.isLoggable(Level.WARNING)) {
						LOGGER.warning("Unable to locate snapshot with UUID : " + previousSnapshotUUID + ". The exception text is " + exp.getMessage());
					}					
				}
			}
			
			// Build the change report
			// If the previousSnapshot is not null, then compare 
			// Otherwise skip the compare put the link to the current snapshot in the change report
			if (previousSnapshot != null) {
				// Create the changeReport
				IChangeHistorySyncReport compareReport = SCMPlatform.getWorkspaceManager(getTeamRepository()).compareBaselineSets(baselineSet, previousSnapshot, null, monitor.newChild(2));
				if (changeReport != null) {
		            // build change report
		            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
		            changeReportBuilder.populateChangeReport(changeReport,
		            		streamConnection.getResolvedWorkspace(), streamConnection.getName(), 
		            		baselineSet, snapshotName, compareReport,
		            		previousBuildURL, listener, monitor.newChild(2));
		        	changeReport.prepareChangeSetLog();
		        	
		        	// From the compareReport, get the count of accepted, discarded, components added/removed count
		        	int acceptCount = getAcceptChangesCount(changeReport);
		        	if (acceptCount > 0) {
		        		buildProperties.put(IJazzScmConfigurationElement.PROPERTY_CHANGES_ACCEPTED, String.valueOf(acceptCount));
		        	}
		        	
		        	if (addLinksToWorkItems) {
			        	LOGGER.info("Adding Jenkins build URL as \"Related Artifacts\" to work items"); //$NON-NLS-1$
			        	// From the compareReport, get the list of work items included in this build.
			        	// We will create link to the current Jenkins build in those work items
			        	List<Integer> workItemIds = changeReport.getAcceptedWorkItems();
			        	try {
			        		WorkItemUtils.addRelatedLinkToWorkItems(fRepository, workItemIds, currentBuildURL,
			        				currentBuildLabel);
			        	} catch (Exception exp) {
		        			// Log the exception but do not fail the build
		        			LOGGER.log(Level.WARNING, 
		        					String.format("Error adding Jenkins build URL as \"Related Artifacts\" to work items %s", workItemIds.toString()), //$NON-NLS-1$ 
		        					exp);
			        	}
		        	}
				}
			} else { // Fill in just the snapshot UUID in the change log
				ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
				changeReportBuilder.populateChangeReport(changeReport, streamConnection.getResolvedWorkspace(), streamConnection.getName(), baselineSet, snapshotName, listener, monitor.newChild(10));
				changeReport.prepareChangeSetLog();
			}
			
			// Create streamChangesData and add it to build properties
			String streamDataHashS = RTCWorkspaceUtils.getInstance().getDigest(getTeamRepository(), workspaceConnection.getResolvedWorkspace(), monitor.newChild(10)); 
			LOGGER.finer("Stream's data hash during accept is " +  streamDataHashS.toString());
			buildProperties.put(Constants.TEAM_SCM_STREAM_DATA_HASH, streamDataHashS.toString());
			
			// Put the snapshotUUID in the buildProperties and add it to result
			// Put the snapshotUUID in the buildProperties and add it to result
			// Put the previous snapshot UUID into buildProperties and add it to the result
			// This will be added in 1.2.0.5. Older builds will not have this property.
			// For repository workspaces, capture operation history state of the workspace before 
			// accepting/discarding/adding/removing components. That is actually a lightweight 
			// previous snapshot UUID 
			if (previousSnapshotUUID != null) {
				buildProperties.put(Constants.TEAM_SCM_PREVIOUS_SNAPSHOT_UUID, previousSnapshotUUID);
			}
			buildProperties.put(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID, baselineSet.getItemId().getUuidValue());
			buildProperties.put(Constants.TEAM_SCM_SNAPSHOT_OWNER, streamConnection.getResolvedWorkspace().getItemId().getUuidValue());
	        buildProperties.put(Constants.TEAM_SCM_ACCEPT_PHASE_OVER, "true");

	        // Add RepositoryAddress to build properties
			buildProperties.put(Constants.REPOSITORY_ADDRESS, getTeamRepository().getRepositoryURI());
	        
			buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
	        result.put(Constants.BUILD_PROPERTIES, buildProperties);
	        	        
	        // Create streamData map and add it to result
	        Map<String, String> streamData = new HashMap<String, String>();
	        streamData.put(Constants.STREAM_DATA_WORKSPACEUUID, workspaceConnection.getResolvedWorkspace().getItemId().getUuidValue());
	        streamData.put(Constants.STREAM_DATA_SNAPSHOTUUID, baselineSet.getItemId().getUuidValue());
	        result.put(Constants.STREAM_DATA, streamData);
	        
	        // Create a call connector only if metronome reporting is required.
	        // If metronome reporter is not null, then add it to callconnector and 
	        // return. If we are unable to start call connector, it is OK. 
	        // The downside is that the the compare call's stats are not recored 
	        // in the final metronome reporter data from the load phase.
	        if (reporter != null) {
	        	CallConnectorData cData = new CallConnectorData(null, reporter);
        		createCallConnectorForMetronomeData(cData, callConnectorTimeout, result, listener);
	        }
	        
	        return result;

		} catch (Exception exp) {
			if (workspaceConnection != null) {
				RTCWorkspaceUtils.getInstance().deleteSilent(workspaceConnection.getResolvedWorkspace(), getTeamRepository(), monitor.newChild(5), listener, clientLocale);
			}
			throw exp;
		}
	}

	/**
	 * Given a build snapshot by name or item id, perform accept operation for the same.
	 * Since a snapshot is an immutable object, there are no changes to accept/discard.
	 * A change report containing the snapshot link is created and written to the changelog file.
	 * Build properties is populated with team_scm_snapshotUUD value. 
	 * @param buildSnapshotContext Object containing the snapshot owner details
	 * @param buildSnapshot  the name or itemid of the RTC snapshot.
	 * @param changeReport - the change report to which the changelog has to be written into.
	 * @param listener
	 * @param progress
	 * @param clientLocale
	 * @return {@link Map} from keys to {@link Object}
	 * @throws Exception 
	 * @throws {@link RTCConfigurationException} - if there is no snapshot with the given name or UUID or more than snapshot with the same name 
	 * @throws {@link TeamRepositoryException} - if there is any other error when communicating with RTC repository
	 * @throws {@link IOException} - if there any error when writing the changelog.
	 */
	private Map<String, Object> acceptForBuildSnapshot(BuildSnapshotContext buildSnapshotContext, final String buildSnapshot, final ChangeReport changeReport, 
									MetronomeReporter reporter, String callConnectorTimeout, final IConsoleOutput listener,
									 final IProgressMonitor progress, final Locale clientLocale) throws Exception {

		SubMonitor monitor = SubMonitor.convert(progress, 100);
        Map<String, Object> result = new HashMap<String, Object>();

		// Fetch the build snapshot
		IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshot(getTeamRepository(), buildSnapshotContext, buildSnapshot, monitor.newChild(40), clientLocale);

		// build change report
		if (changeReport != null) {
            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
            changeReportBuilder.populateChangeReport(changeReport, baselineSet, baselineSet.getName(), listener, monitor.newChild(40)); 
        	changeReport.prepareChangeSetLog();
		}
		
		Map<String, String> buildProperties = new HashMap<String, String>();

		// Add RepositoryAddress to build properties
		buildProperties.put(Constants.REPOSITORY_ADDRESS, getTeamRepository().getRepositoryURI());
		
		// Put the snapshotUUID in the buildProperties and add it to result
		buildProperties.put(IJazzScmConfigurationElement.PROPERTY_SNAPSHOT_UUID, baselineSet.getItemId().getUuidValue());
		buildProperties = BuildConfiguration.formatAsEnvironmentVariables(buildProperties);
        result.put(Constants.BUILD_PROPERTIES, buildProperties);
        
        // Create a call connector only if metronome reporting is required.
        // If metronome reporter is not null, then add it to callconnector and return.
        // If we are unable to start call connector, it is OK. The downside is that the 
        // the compare call's stats are not recored in the final metronome reporter data 
        // from the load phase.
        if (reporter != null) {
        	CallConnectorData cData = new CallConnectorData(null, reporter);
        	createCallConnectorForMetronomeData(cData, callConnectorTimeout, result, listener);
        }
        
        return result;
	}
	
	/**
	 * Validate if load rule file exists in the specified component.
	 * 
	 * @param processAreaName - the name of the owning project or team area
	 * @param isStreamConfiguration Flag that determines if the <code>workspaceName</code> corresponds to a workspace or a stream
	 * @param workspaceName Name of the workspace specified in the build configuration
	 * @param pathToLoadRuleFile Path to the load rule file of the format <component name>/<remote path of the load rule file>
	 * @param progress Progress monitor
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception
	 */
	public void testLoadRules(String processAreaName, boolean isStreamConfiguration, String workspaceName, String pathToLoadRuleFile,
			IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(40));
		try {
			IWorkspaceHandle workspaceHandle = isStreamConfiguration ? getBuildStream(processAreaName, workspaceName, monitor.newChild(40),
					clientLocale)
					: RTCWorkspaceUtils.getInstance().getWorkspace(workspaceName, getTeamRepository(), monitor.newChild(40), clientLocale);
			BuildWorkspaceDescriptor wsDescriptor = new BuildWorkspaceDescriptor(getTeamRepository(), workspaceHandle.getItemId().getUuidValue(),
					workspaceName);
			IWorkspaceConnection wsConnection = wsDescriptor.getConnection(fRepositoryManager, false, monitor.newChild(5));
			RTCWorkspaceUtils.getInstance().getComponentLoadRuleString(new LoadConfigurationDescriptor(isStreamConfiguration, workspaceName), wsConnection, pathToLoadRuleFile, clientLocale,
					monitor.newChild(5));

		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}
	
	/**
	 * Validate if the given project/team area exists in the repository.
	 * 
	 * @param processAreaName - the name of the owning project or team area
	 * @param progress Progress monitor
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception
	 */
	public void testProcessArea(String processAreaName, IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(40));
		try {
			RTCWorkspaceUtils.getInstance().getProcessAreaByName(processAreaName, fRepository, progress, clientLocale);
		} catch (RTCConfigurationException e) {
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * Delete the repository workspace having the given UUID 
	 * Does not handle {@link ItemNotFoundException} 
	 * 
	 * @param workspaceUUID The UUID of the repository workspace to delete
	 * @param workspaceName The name of the repository workspace.
	 * @param listener
	 * @param clientLocale The locale of the requesting client.
	 * @param progress Progress Monitor to report progress.
	 * @throws TeamRepositoryException - if anything goes wrong when deleting the repository workspace, including 
	 * 		item not found exception
	 */
	public void deleteWorkspace(String workspaceUUID, String workspaceName, IConsoleOutput listener, Locale clientLocale, IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(50));
		RTCWorkspaceUtils.getInstance().delete(workspaceUUID, workspaceName, getTeamRepository(), monitor.newChild(40), listener, clientLocale);
	}

	/**
	 * Get the build definition information stored in the build result.
	 * The build result's request has a reference to build definition instance used 
	 * in constructing the request.
	 * @param buildResultUUID The build result UUID to work with
	 * @param listener
	 * @param clientLocale The locale of the requesting client.
	 * @param progress Progress Monitor to report progress.
	 * @return a map with Object as values. This object could be another map. 
	 */
	public Map<String, Object> getBuildDefinitionInfoFromBuildResult(String buildResultUUID, IConsoleOutput listener, Locale clientLocale,
							IProgressMonitor progress) throws TeamRepositoryException {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Entering RepositoryConnection : getBuildDefinitionInfo");
		}
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		ensureLoggedIn(monitor.newChild(10));
		Map<String, Object> result = RTCBuildUtils.getInstance().getBuildDefinitionInfoFromBuildResult(buildResultUUID, getTeamRepository(), listener, 
										clientLocale, monitor.newChild(90));
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Leaving RepositoryConnection : getBuildDefinitionInfo");
		}
		return result;
	}
	
	private String getWorkspaceNamePrefix() {
		return DEFAULTWORKSPACEPREFIX; 
	}

	/**
	 * Return the count of processed change sets (accepted or discarded)
	 *  + processed components (adds/removes)
	 */
	private int getAcceptChangesCount(ChangeReport changeReport) {
		if (changeReport == null) {
			return 0;
		}
		int changesCount = 0;
		if (changeReport.getChangeSets() != null) {
			changesCount += changeReport.getChangeSets().size();
		}
		if (changeReport.getComponentChanges() != null) {
			changesCount += changeReport.getComponentChanges().size();
		}
		return changesCount;
	}

	private boolean shouldCreateMetronomeReport(Map<String, Object> options) {
		boolean shouldCreateMetronomeReport = false;
		if (options.containsKey(Constants.TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME)) { //$NON-NLS-1$
			Boolean value = (Boolean) options.get(Constants.TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME); //$NON-NLS-1$
			shouldCreateMetronomeReport = value.booleanValue();
		}
		return shouldCreateMetronomeReport;
	}
	
	private boolean shouldCreateMetronomeReport(BuildConfiguration buildConfiguration) {
		return Boolean.parseBoolean(buildConfiguration.getBuildProperty(Constants.TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME, "false"));
	}
	
	/**
	 * Create a callConnector with the given data and add the id to the result map
	 * Swallows exception that occurs while creating call connector
	 * 
	 * @param cData
	 * @param callConnectorTimeoutParam
	 * @param properties
	 * @param listener
	 * 
	 */
	private void createCallConnectorForMetronomeData(final CallConnectorData cData, final String callConnectorTimeoutParam, 
					final Map<String, Object> result, IConsoleOutput listener) {
        CallConnector<CallConnectorData> callConnector = null;
        String connectorId = ""; //$NON-NLS-1$
        try {
        	String callConnectorTimeout = Utils.fixEmptyAndTrim(callConnectorTimeoutParam);
            if ((callConnectorTimeout != null) && (!"".equals(callConnectorTimeout)) && callConnectorTimeout.matches("\\d+")) { //$NON-NLS-1$ //$NON-NLS-2$
            	long timeout = Long.parseLong(callConnectorTimeout) * 1000;
            	callConnector = new CallConnector<CallConnectorData>(cData, timeout);
            } else {
            	callConnector = new CallConnector<CallConnectorData>(cData);
            }
    		callConnector.start();
    		connectorId = (new Long(callConnector.getId())).toString();
    		result.put(Constants.CONNECTOR_ID, connectorId);
        } catch(Exception e) {
    		String errorMessage = Messages.getDefault().RepositoryConnection_accept_unable_to_start_call_connector() + 
    				Messages.getDefault().RepositoryConnection_metronome_data_might_be_unavailable();
    		listener.log(errorMessage);
    		LOGGER.log(Level.WARNING, errorMessage, e);
        }
	}
	
	private static String getStatsDataFilePrefix(Map<String, Object> metronomeOptions) {
		return getStringValue(Constants.STATISTICS_DATA_FILE_PREFIX_PROPERTY_NAME, 
					Constants.STATISTICS_DATA_FILE_DEFAULT_PREFIX_VALUE, metronomeOptions);
	}

	private static String getStatsReportFilePrefix(Map<String, Object> metronomeOptions) {
		return getStringValue(Constants.STATISTICS_REPORT_FILE_PREFIX_PROPERTY_NAME, 
					Constants.STATISTICS_REPORT_FILE_DEFAULT_PREFIX_VALUE, metronomeOptions);
	}
	
	private static String getStatisticsReportFileSuffix(Map<String, Object> metronomeOptions) {
		return getStringValue(Constants.STATISTICS_REPORT_FILE_SUFFIX_PROPERTY_NAME, 
					Constants.STATISTICS_REPORT_FILE_DEFAULT_SUFFIX_VALUE, metronomeOptions);
	}

	private static String getStatisticsDataFileSuffix(Map<String, Object> metronomeOptions) {
		return getStringValue(Constants.STATISTICS_DATA_FILE_SUFFIX_PROPERTY_NAME, 
					Constants.STATISTICS_DATA_FILE_DEFAULT_SUFFIX_VALUE, metronomeOptions);
	}

	private static String getStatisticsReportLabel(Map<String, Object> metronomeOptions) {
		return getStringValue(Constants.STATISTICS_REPORT_LABEL_PROPERTY_NAME, 
					Constants.STATISTICS_REPORT_DEFAULT_LABEL_VALUE, metronomeOptions);
	}
	
	private static String getStatisticsDataLabel(Map<String, Object> metronomeOptions) {
		return getStringValue(Constants.STATISTICS_DATA_LABEL_PROPERTY_NAME, 
					Constants.STATISTICS_DATA_DEFAULT_LABEL_VALUE, metronomeOptions);
	}

	private static String getStringValue(String key, String defaultValue, Map<String, Object> metronomeOptions) {
		String value = defaultValue;
		if (metronomeOptions != null && metronomeOptions.containsKey(key)) {
			value = (String) metronomeOptions.get(key);
		}
		return value;
	}
}
