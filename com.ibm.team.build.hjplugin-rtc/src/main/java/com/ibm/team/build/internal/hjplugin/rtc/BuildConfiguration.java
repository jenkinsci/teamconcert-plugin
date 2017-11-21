/*******************************************************************************
 * Copyright (c) 2013, 2017 IBM Corporation and others.
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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IBuildDefinitionInstance;
import com.ibm.team.build.common.model.IBuildEngine;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestHandle;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.build.internal.scm.ComponentLoadRules;
import com.ibm.team.build.internal.scm.LoadComponents;
import com.ibm.team.build.internal.scm.RepositoryManager;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.InternalRepositoryException;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;

@SuppressWarnings("restriction")
public class BuildConfiguration {
    private static final Logger LOGGER = Logger.getLogger(BuildConfiguration.class.getName());
	private static final String eol = System.getProperty("line.separator");  //$NON-NLS-1$
	private static final int MAX_RETRY_COUNT = 3;
	private static final long SLEEP_TIMEOUT_SECONDS = 60; // seconds

	private File fetchDestinationFile;
	private Path fetchDestinationPath;
	private boolean deleteNeeded = false;
	private boolean isPersonalBuild = false;
	private boolean acceptBeforeFetch = true;
	private boolean includeComponents = false;
	private String loadRuleUUIDs = null;
	private Collection<IComponentHandle> components = Collections.emptyList();
	private boolean createFoldersForComponents = false;
	private ITeamRepository teamRepository;
	private String hjWorkspace;
	private BuildStreamDescriptor stream;
	private BuildWorkspaceDescriptor workspace;
	private BuildSnapshotDescriptor snapshot;
	private String snapshotName;
	private Map<String, String> buildProperties = new HashMap<String, String>();
	private Map<String, String> temporaryRepositoryWorkspaceProperties = new HashMap<String, String>();
	private boolean shouldDeleteTemporaryWorkspace = true;
	private String loadPolicy;
	private String componentLoadConfig;
	private boolean isBuildDefinitionConfiguration = false;

	/**
	 * Basic build configuration. Caller should also initialize prior to calling
	 * the get methods.
	 * 
	 * @param teamRepository The team repository that contains the build artifacts
	 * @param hjWorkspace The Hudson/Jenkins specified build location
	 */
	public BuildConfiguration(ITeamRepository teamRepository, String hjWorkspace) {
		this.teamRepository = teamRepository;
		this.hjWorkspace = hjWorkspace;
	}
	
	/**
	 * Initialize configuration that describes how to build & load.
	 * @param workspaceHandle The build workspace. Never <code>null</code>
	 * @param workspaceName Name of the build workspace. Never <code>null</code>
	 * @param snapshotName The name to give any snapshot created. Never <code>null</code>
	 * @throws IOException If anything goes wrong during the initialization
	 */
	@Deprecated
	public void initialize(IWorkspaceHandle workspaceHandle, String workspaceName, String snapshotName) throws IOException {
		LOGGER.finest("BuildConfiguration.initialize for workspaceHandle : Enter");

		this.workspace = new BuildWorkspaceDescriptor(getTeamRepository(), workspaceHandle.getItemId().getUuidValue(), workspaceName);
		this.snapshotName = snapshotName;
		this.fetchDestinationFile = new File(hjWorkspace);
		this.fetchDestinationPath = new Path(fetchDestinationFile.getCanonicalPath());
		
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Building workspace: " + workspaceName + " snapshotName " + snapshotName);
		}
	}

	/**
	 * Initialize a configuration that describes how to load from a RTC SCM snapshot
	 * 
	 * @param baselineSet The snapshot configured in the Jenkins job. Never <code>null</code>.
	 * @param contributorHandle Handle to the logged in contributor. Required to create the workspace.
	 * @param workspacePrefix Prefix to be used in the name of the workspace.
	 * @param workspaceComment Description set on the workspace.
	 * @param loadPolicy load policy value that determines whether to use load rules or load options during load
	 * @param componentLoadConfig component load configuration value that determines whether to load all components or
	 *            exclude some components
	 * @param createFoldersForComponents determines whether to component root folders
	 * @param componentsToExclude list of components to exclude. comma separated list of id/name of components
	 * @param pathToLoadRuleFile Path to the load rule file. Can be <code>null</code>.
	 * @param listener A listener to report error/progress
	 * @param monitor Progress monitor.
	 * @throws Exception
	 */
	public void initialize(IBaselineSet baselineSet, IContributorHandle contributorHandle, String workspacePrefix, String workspaceComment,
			String loadPolicy, String componentLoadConfig, boolean createFoldersForComponents, String componentsToExclude, String pathToLoadRuleFile, IConsoleOutput listener,
			Locale clientLocale, IProgressMonitor monitor) throws Exception {
		LOGGER.finest("BuildConfiguration.initialize for baselineSetHandle : Enter");
		SubMonitor progress = SubMonitor.convert(monitor, 30);

		IWorkspaceConnection workspaceConnection = null;
		String workspaceName =  workspacePrefix + "_" + Long.toString(System.currentTimeMillis()) + "_" + Long.toString(System.nanoTime());
		try {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finest("BuildConfiguration.initialize for baselineSetHandle : Creating workspace '" + workspaceName + "'");
			}
			
			workspaceConnection = createRepositoryWorkspaceWithRetry(getTeamRepository(), baselineSet, workspaceName,
					workspaceComment, contributorHandle, listener, progress.newChild(5));
			String snapshotUUID =  baselineSet.getItemId().getUuidValue();
			this.snapshot = new BuildSnapshotDescriptor(teamRepository, snapshotUUID, baselineSet);
			this.workspace = new BuildWorkspaceDescriptor(teamRepository, 
											workspaceConnection.getResolvedWorkspace().getItemId().getUuidValue(), 
										    workspaceName);
			this.fetchDestinationFile = new File(hjWorkspace);
			this.fetchDestinationPath = new Path(fetchDestinationFile.getCanonicalPath());
			this.acceptBeforeFetch = false;
			this.isPersonalBuild = false;
			this.includeComponents = false;
			setLoadPolicy(loadPolicy);
			setComponentLoadConfig(componentLoadConfig);
			setCreateFoldersForComponents(createFoldersForComponents);
			setComponents(workspaceConnection, componentsToExclude, listener, clientLocale, progress.newChild(10));
			setComponentLoadRules(workspaceConnection, pathToLoadRuleFile, clientLocale, progress.newChild(10));

			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Loading from snapshot: " + snapshotUUID + "  using temporary workspace '" +  workspaceName + "'");
			}
		} catch (Exception exp) {
			if (workspaceConnection != null) {
				getRTCWorkspaceUtils().deleteSilent(workspaceConnection.getResolvedWorkspace(),
								getTeamRepository(), progress, listener, clientLocale);
			}
			throw exp;
		}
	}
	
	/**
	 * Initialize configuration that describes how to build & load with a RTC SCM workspace
	 * 
	 * @param workspaceHandle The build workspace. Never <code>null</code>
	 * @param workspaceName Name of the build workspace. Never <code>null</code>
	 * @param snapshotName The name to give any snapshot created. Never <code>null</code>
	 * @param acceptBeforeLoad Accept latest changes before loading if true
	 * @param loadPolicy load policy value that determines whether to use load rules or load options during load
	 * @param componentLoadConfig component load configuration value that determines whether to load all components or
	 *            exclude some components
	 * @param createFoldersForComponents determines whether to component root folders
	 * @param componentsToExclude list of components to exclude. comma separated list of id/name of components
	 * @param pathToLoadRuleFile Path to the load rule file. Can be <code>null</code>.
	 * @param listener A listener to report error/progress
	 * @param clientLocale Locale of the requesting client.
	 * @param monitor Progress monitor.
	 * @throws Exception
	 */
	public void initialize(IWorkspaceHandle workspaceHandle, String workspaceName, String snapshotName, boolean acceptBeforeLoad, String loadPolicy,
			String componentLoadConfig, boolean createFoldersForComponents, String componentsToExclude, String pathToLoadRuleFile,
			IConsoleOutput listener, Locale clientLocale, IProgressMonitor monitor) throws Exception {
		LOGGER.finest("BuildConfiguration.initialize for workspaceHandle : Enter");
		SubMonitor progress = SubMonitor.convert(monitor, 30);

		this.workspace = new BuildWorkspaceDescriptor(getTeamRepository(), workspaceHandle.getItemId().getUuidValue(), workspaceName);
		this.snapshotName = snapshotName;
		this.fetchDestinationFile = new File(hjWorkspace);
		this.fetchDestinationPath = new Path(fetchDestinationFile.getCanonicalPath());
		this.acceptBeforeFetch = acceptBeforeLoad;
		this.includeComponents = false;
		setLoadPolicy(loadPolicy);
		setComponentLoadConfig(componentLoadConfig);
		IWorkspaceConnection wsConnection = SCMPlatform.getWorkspaceManager(teamRepository).getWorkspaceConnection(workspaceHandle,
				progress.newChild(10));
		setCreateFoldersForComponents(createFoldersForComponents);
		setComponents(wsConnection, componentsToExclude, listener, clientLocale, progress.newChild(10));
		setComponentLoadRules(wsConnection, pathToLoadRuleFile, clientLocale, progress.newChild(10));

		addCommonBuildProperties();
		
		// Add the workspace UUID property
		buildProperties.put(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, workspaceHandle.getItemId().getUuidValue());

		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Building workspace: " + workspaceName + " snapshotName " + snapshotName);
		}
	}
	
	/**
	 * Initialize configuration that describes how to build & load with a RTC SCM stream
	 * 
	 * @param streamHandle The stream handle. Never <code>null</code>
	 * @param streamName - the name of the stream
	 * @param snapshotName The name to give any snapshot created. Never <code>null</code>
	 * @param workspacePrefix Prefix for the temporary build workspace to be created. Never <code>null</code>
	 * @param contributorHandle The handle to the logged in contributor
	 * @param loadPolicy load policy value that determines whether to use load rules or load options during load
	 * @param componentLoadConfig component load configuration value that determines whether to load all components or
	 *            exclude some components
	 * @param createFoldersForComponents determines whether to component root folders
	 * @param componentsToExclude list of components to exclude. comma separated list of id/name of components
	 * @param pathToLoadRuleFile Path to the load rule file. Can be <code>null</code>.
	 * @param listener A listener to report error/progress
	 * @param clientLocale Locale of the requesting client.
	 * @param monitor Progress Monitor
	 * @throws Exception
	 */
	public void initialize(IWorkspaceHandle streamHandle, String streamName, IWorkspace workspace, IBaselineSet baselineSet,
			boolean shouldDeleteTemporaryWorkspace, IContributorHandle contributorHandle, String loadPolicy, String componentLoadConfig,
			boolean createFoldersForComponents, String componentsToExclude, String pathToLoadRuleFile, IConsoleOutput listener, Locale clientLocale,
			IProgressMonitor monitor) throws Exception {
		LOGGER.finest("BuildConfiguration.initialize for stream : Enter");

		SubMonitor progress = SubMonitor.convert(monitor, 30);

		try {
			this.stream = new BuildStreamDescriptor(streamName, baselineSet.getItemId().getUuidValue());
			this.workspace = new BuildWorkspaceDescriptor(teamRepository, workspace.getItemId().getUuidValue(), workspace.getName()) ;
			this.snapshotName = baselineSet.getName();
			this.fetchDestinationFile = new File(hjWorkspace);
			this.fetchDestinationPath = new Path(fetchDestinationFile.getCanonicalPath());
			this.acceptBeforeFetch = false;
			this.includeComponents = false;
			this.isPersonalBuild = false;
			this.shouldDeleteTemporaryWorkspace = shouldDeleteTemporaryWorkspace;
			// Set the flow target if temporary workspace will live on after load is over
			if (!shouldDeleteTemporaryWorkspace) {
				// Set the flow target
				getRTCWorkspaceUtils().setFlowTarget(getTeamRepository(), workspace, streamHandle, progress.newChild(5));
				setTemporaryRepositoryWorkspaceProperties(workspace);
			}			
			setLoadPolicy(loadPolicy);
			setComponentLoadConfig(componentLoadConfig);
			IWorkspaceConnection wsConnection = SCMPlatform.getWorkspaceManager(teamRepository).getWorkspaceConnection(streamHandle,
					progress.newChild(10));
			setCreateFoldersForComponents(createFoldersForComponents);
			setComponents(wsConnection, componentsToExclude, listener, clientLocale, progress.newChild(10));
			setComponentLoadRules(wsConnection, pathToLoadRuleFile, clientLocale, progress.newChild(10));

			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Building from stream : '" + streamName + "' with temporary workspace '" + workspace.getName() + "'. Created snapshot '" + snapshotName + "'.");
			}
		} finally {
			progress.done();
		}
	}

	/**
	 * From the given build result, get the corresponding build definition's id.   
	 * @param teamRepository team repository instance
	 * @param buildResultHandle The build result that will reference the build request that contains the
	 * configuration details. Never <code>null</code>.
	 * @param snapshotName The name to give any snapshot created. Never <code>null</code>
	 * @param clientLocale The locale of the requesting client
	 * @return
	 * @throws TeamRepositoryException If anything goes wrong while fetching configuration
	 */
	public static String getBuildDefinitionId(ITeamRepository teamRepository, IBuildResultHandle buildResultHandle, IProgressMonitor progress,
			Locale clientLocale) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IItemManager itemManager = teamRepository.itemManager();

        IBuildResult result = (IBuildResult) itemManager.fetchCompleteItem(
                buildResultHandle, IItemManager.REFRESH, monitor.newChild(5));

        if (result.getBuildRequests().isEmpty()) {
        	throw new IllegalStateException("No build request for the build result");
        }
        
        IBuildRequest buildRequest = (IBuildRequest) itemManager.fetchCompleteItem((IBuildRequestHandle) result.getBuildRequests().iterator().next(),
        		IItemManager.REFRESH, monitor.newChild(10));
        IBuildDefinitionInstance buildDefinitionInstance = buildRequest.getBuildDefinitionInstance();
		String buildDefinitionId = buildDefinitionInstance.getBuildDefinitionId();
		return buildDefinitionId;
	}
	
	/**
	 * Initialize configuration that describes how to build & load with a build definition
	 * @param buildResultHandle The build result that will reference the build request that contains the
	 * configuration details. Never <code>null</code>. If there is no build result available, you should
	 * be calling {@link #initialize(IWorkspaceHandle, String, String)}
	 * @param isCustomSnapshotName Indicates if a custom snapshot name is configured in the Job
	 * @param useDynamicLoadRules Determines whether to use dynamic load rules for loading components
	 * @param snapshotName The name to give any snapshot created. Never <code>null</code>
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @throws IOException If anything is wrong with the destination location
	 * @throws RTCConfigurationException If validation fails
	 * @throws TeamRepositoryException If anything goes wrong during the initialization
	 */
	public void initialize(IBuildResultHandle buildResultHandle, boolean isCustomSnapshotName, String snapshotName, boolean useDynamicLoadRules, final IConsoleOutput listener,
			IProgressMonitor progress, Locale clientLocale) throws IOException, RTCConfigurationException, TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IItemManager itemManager = getTeamRepository().itemManager();

        IBuildResult result = (IBuildResult) itemManager.fetchCompleteItem(
                buildResultHandle, IItemManager.REFRESH, monitor.newChild(5));

        if (result.getBuildRequests().isEmpty()) {
        	throw new IllegalStateException("No build request for the build result");
        }
        
        IBuildRequest buildRequest = (IBuildRequest) itemManager.fetchCompleteItem((IBuildRequestHandle) result.getBuildRequests().iterator().next(),
        		IItemManager.REFRESH, monitor.newChild(10));
        IBuildDefinitionInstance buildDefinitionInstance = buildRequest.getBuildDefinitionInstance();
        IBuildConfigurationElement element = buildDefinitionInstance.getConfigurationElement(
                IJazzScmConfigurationElement.ELEMENT_ID);

        if (element == null) {
        	throw new RTCConfigurationException(Messages.get(clientLocale).BuildConfiguration_scm_not_configured(buildDefinitionInstance.getBuildDefinitionId()));
        }
        
        String workspaceUuid = getWorkspaceUuid(buildDefinitionInstance);
        deleteNeeded = getDeleteBeforeFetch(buildDefinitionInstance);
        acceptBeforeFetch = getAcceptBeforeFetch(buildDefinitionInstance);
        isPersonalBuild = result.isPersonalBuild();
		this.loadPolicy = useDynamicLoadRules ? Constants.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES : getLoadPolicy(buildDefinitionInstance);
		this.componentLoadConfig = getComponentLoadConfig(buildDefinitionInstance);
        createFoldersForComponents = getCreateFoldersForComponents(buildDefinitionInstance);
        loadRuleUUIDs = getComponentLoadRuleUUIDs(buildDefinitionInstance);
        components = getLoadComponents(buildDefinitionInstance);
        includeComponents = getIncludeComponents(buildDefinitionInstance);
		isBuildDefinitionConfiguration = true;

        if (workspaceUuid == null) {
            throw new IllegalStateException("Missing build workspace specification from the build definition"); //$NON-NLS-1$
        }
        
		// If a custom snapshot name is configured in the job, hjplugin resolves any references to environment variables
		// and build variables(includes the parameters) and provides the resultant snapshot name. In this case set what
		// is provided.
		// If a custom snapshot name is not given fallback to the default pattern - <Build Definition
		// Id>_<snapshotName>, by default the snapshotName passed from hjplugin contains "#<Build_Number>" string
		this.snapshotName = isCustomSnapshotName ? snapshotName : buildRequest.getBuildDefinitionInstance().getBuildDefinitionId()
				+ "_" + snapshotName; //$NON-NLS-1$
        
        workspace = new BuildWorkspaceDescriptor(getTeamRepository(), workspaceUuid, null);
        
        // Collect Build Properties
        IBuildEngine buildEngine = (IBuildEngine) itemManager.fetchCompleteItem(buildRequest.getHandler(), IItemManager.REFRESH, monitor.newChild(10));
			
        // Add built-in properties.
        // Do not add the buildResultUUID property. It will be made available in the RTCBuildResultUUID property
        // We want to be clear on when it is supplied in the original env. vs. build result is created later by us.
        // buildProperties.put("buildResultUUID", buildResultHandle.getItemId().getUuidValue()); //$NON-NLS-1$
        buildProperties.put("requestUUID", buildRequest.getItemId().getUuidValue()); //$NON-NLS-1$
        buildProperties.put("buildDefinitionId", buildRequest.getBuildDefinitionInstance().getBuildDefinitionId()); //$NON-NLS-1$
        buildProperties.put("repositoryAddress", ((ITeamRepository) buildRequest.getOrigin()).getRepositoryURI()); //$NON-NLS-1$
        buildProperties.put("buildEngineId", buildEngine.getId()); //$NON-NLS-1$
        buildProperties.put("buildEngineHostName", getLocalHostNoException()); //$NON-NLS-1$
        
        try {
            IContributor contributor = (IContributor) itemManager.fetchCompleteItem(buildRequest.getInitiatingContributor(),
            		IItemManager.REFRESH, monitor.newChild(10));
            buildProperties.put("buildRequesterUserId", contributor.getUserId()); //$NON-NLS-1$
        } catch (ItemNotFoundException itemNotFoundException) {
            // Will not add property since contributor not found
        } catch (PermissionDeniedException permissionDeniedException) {
            // Will not add property in case of permission denied
        }

        if (result.isPersonalBuild()) {
            buildProperties.put("personalBuild", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Engine properties override built-in properties.
        if (buildEngine != null) {
            for (Object object : buildEngine.getProperties()) {
                IBuildProperty property = (IBuildProperty) object;
                buildProperties.put(property.getName(), property.getValue());
            }
        }

        // Definition properties override engine properties.
        for (Object object : buildRequest.getBuildDefinitionInstance().getProperties()) {
            IBuildProperty property = (IBuildProperty) object;
            buildProperties.put(property.getName(), property.getValue());
        }
        
		buildProperties.put(Constants.BUILD_RESULT_LABEL, result.getLabel()); //$NON-NLS-1$

		// Substitute variables in the build and config properties.
		IBuildRequest workingCopy = (IBuildRequest) buildRequest.getWorkingCopy();
        performPropertyVariableSubstitutions(workingCopy, buildProperties, listener);

        // include the hj prefix path in the fetch destination
        String fetchDestination = getFetchDestination(buildProperties, clientLocale);
        fetchDestinationFile = new File(fetchDestination);
        fetchDestinationPath = new Path(fetchDestinationFile.getCanonicalPath());
        
        File workingDirectory = new File(System.getProperty("user.dir")); //$NON-NLS-1$
        Path workingDirectoryPath = new Path(workingDirectory.getCanonicalPath()); 

        if (deleteNeeded && fetchDestinationPath.isPrefixOf(workingDirectoryPath)) {
            throw new RTCConfigurationException(Messages.get(clientLocale).BuildConfiguration_deleting_working_directory(
                    fetchDestinationFile.getCanonicalPath()));
        }

        if (LOGGER.isLoggable(Level.FINER)) {
			StringBuilder message = new StringBuilder("Building ").append(result.getLabel()).append(eol); //$NON-NLS-1$
			message.append("WorkspaceUUID ").append(workspaceUuid).append(eol); //$NON-NLS-1$
			message.append("H/J workspace ").append(hjWorkspace).append(eol); //$NON-NLS-1$
		    message.append("fetchDestinationPath ").append(fetchDestinationPath).append(eol); //$NON-NLS-1$
		    message.append("deleteNeeded ").append(deleteNeeded).append(eol); //$NON-NLS-1$
		    message.append("isPersonalBuild ").append(isPersonalBuild).append(eol); //$NON-NLS-1$
		    message.append("acceptBeforeFetch ").append(acceptBeforeFetch).append(eol); //$NON-NLS-1$
		    message.append("number Of Components to "); //$NON-NLS-1$
		    if (includeComponents) {
		    	message.append("include "); //$NON-NLS-1$
		    } else {
		    	message.append("exclude "); //$NON-NLS-1$
		    }
		    message.append(components.size()).append(eol); //$NON-NLS-1$
		    message.append("createFoldersForComponents ").append(createFoldersForComponents).append(eol); //$NON-NLS-1$
		    message.append("loadRuleUUIDs ").append(loadRuleUUIDs).append(eol); //$NON-NLS-1$
			message.append("snapshotName ").append(snapshotName).append(eol); //$NON-NLS-1$
			message.append("buildResultUUID ").append(buildResultHandle.getItemId().getUuidValue()).append(eol); //$NON-NLS-1$
			message.append("buildDefinitionId ").append(buildRequest.getBuildDefinitionInstance().getBuildDefinitionId()).append(eol); //$NON-NLS-1$
	        message.append("buildRequestUUID ").append(buildRequest.getItemId().getUuidValue()).append(eol); //$NON-NLS-1$
	        message.append("buildEngineId ").append(buildEngine.getId()).append(eol); //$NON-NLS-1$
	        LOGGER.finer(message.toString());
        }
	}

    public static Map<String, String> formatAsEnvironmentVariables(Map<String, String> properties) {
    	Map<String, String> result = new HashMap<String, String>(properties.size());
		for (Map.Entry<String, String> property : properties.entrySet()) {
			String newKey = formatAsEnvironmentVariable(property.getKey());
			if (newKey != null) {
				result.put(newKey, property.getValue());
			}
		}
		return result;
	}
    
    /**
	 * Check and set the loadPolicy value. When loadPolicy value is not set, default to "useComponentLoadConfig". For
	 * this reason, this method should not be invoked when initializing build definition configuration. For build
	 * definition configuration if load policy is not set then we continue to support the old load rules behavior i.e.
	 * load all components and load those components for which load rules are specified according to those rules.
	 * 
	 * @param loadPolicy
	 */
	private void setLoadPolicy(String loadPolicy) {
		// if loadPolicy is not set then treat it as useComponentLoadConfig 
		this.loadPolicy = (loadPolicy != null) ? loadPolicy : Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG;
	}
	
	private void setComponentLoadConfig(String componentLoadConfig) {
		// if componentLoadConfig is not set then treat it as loadAllComponents
		this.componentLoadConfig = (componentLoadConfig != null) ? componentLoadConfig : Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS;
	}

	private void setCreateFoldersForComponents(boolean createFoldersForComponents) throws Exception {
		this.createFoldersForComponents = createFoldersForComponents && (!isLoadPolicySet() || isLoadPolicySetToUseComponentLoadConfig());
	}
	
	@SuppressWarnings("unchecked")
	private void setComponents(IWorkspaceConnection workspaceConnection, String loadComponents, IConsoleOutput listener, Locale clientLocale,
			IProgressMonitor monitor) throws Exception {
		if (!isLoadPolicySet() || isLoadPolicySetToUseComponentLoadConfig() && isComponentLoadConfigSetToExcludeSomeComponents()) {
			this.components = RTCWorkspaceUtils.getInstance().getComponentHandles(new RTCWorkspaceUtils.LoadConfigurationDescriptor(this),
					workspaceConnection, loadComponents, false, listener, clientLocale, monitor);
		} else {
			this.components = Collections.EMPTY_LIST;
		}
	}
	
	private void setComponentLoadRules(IWorkspaceConnection workspaceConnection, String pathToLoadRuleFile, Locale clientLocale,
			IProgressMonitor monitor) throws Exception {
		if (isLoadPolicySetToUseLoadRules()) {
			this.loadRuleUUIDs = getRTCWorkspaceUtils().getComponentLoadRuleString(new RTCWorkspaceUtils.LoadConfigurationDescriptor(this),
					workspaceConnection, pathToLoadRuleFile, clientLocale, monitor);
		} else {
			this.loadRuleUUIDs = null;
		}
	}

	private static String formatAsEnvironmentVariable(String key) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			Character ch = key.charAt(i);
			if (Character.isLetterOrDigit(ch)) {
				sb.append(ch);
			} else if (ch == '.'){
				sb.append('_');
			}
		}
		
		String newKey = sb.toString();
		
		if (newKey.length() == 0 || Character.isDigit(newKey.charAt(0))) {
			newKey = null;
		}
		
		return newKey;
	}

	/**
     * @return this host name, or a fixed message if it is unknown
     */
    private String getLocalHostNoException() {
        String result = "{cannot determine host name}";
        try {
            result = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // nothing to do
        }

        return result;
    }

    private String getWorkspaceUuid(IBuildDefinitionInstance instance) {
        IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
        if (property != null && property.getValue().length() > 0) {
            return property.getValue();
        }
        return null;
    }

    private String getFetchDestination(Map<String, String> buildProperties, Locale clientLocale) throws RTCConfigurationException {
        String loadDirectory = buildProperties.get(IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION);
        if (loadDirectory != null && loadDirectory.length() > 0) {
        	File loadDirectoryFile = new File(loadDirectory);
        	if (!loadDirectoryFile.isAbsolute()) {
        		loadDirectoryFile = new File(hjWorkspace, loadDirectory);
        	}
			try {
				String path = loadDirectoryFile.getCanonicalPath();
				return path;
			} catch (IOException e) {
				throw new RTCConfigurationException(Messages.get(clientLocale).BuildConfiguration_invalid_fetch_destination(loadDirectoryFile.getPath(), e.getMessage()));
			}
        } else {
        	// default to the Hudson/Jenkins destination
        	return hjWorkspace;
        }
    }


	private String getComponentLoadRuleUUIDs(IBuildDefinitionInstance instance) throws TeamRepositoryException {
		IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES);
		if (property != null && property.getValue() != null && property.getValue().length() > 0
				&& (!isLoadPolicySet() || isLoadPolicySetToUseLoadRules())) {
			return property.getValue();
		}
		return null;
	}

    /**
     * The load rules to be used during the load.
     * @param workspaceConnection The workspace containing the load rules
     * @param pathToDynamicLoadRuleFile Path to the file containing the dynamic load rules
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @return Collection of load rules for the load. The return collection
     * is deliberately not typed because releases later than 3.0.1.5 use ILoadRule2
     * as opposed to ILoadRule. If there are no load rules, an empty collection is
     * returned. Never <code>null</code>
     * @throws TeamRepositoryException If anything goes wrong retrieving the load rules
     */
    @SuppressWarnings("rawtypes")
	public Collection getComponentLoadRules(IWorkspaceConnection workspaceConnection, String pathToDynamicLoadRuleFile, PrintStream buildLog,
			IProgressMonitor progress, Locale clientLocale) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		// Determine if the new format of load rules is supported and if so, will it be able to find
		// the schema to validate against.
		boolean useOverride = false;
		try {
			BuildConfiguration.class.getClassLoader().loadClass("com.ibm.team.filesystem.client.operations.ILoadRule2");
			// New load rule format supported.
			try {
				URL schemaURL = new URL("platform:/plugin/com.ibm.team.filesystem.client/schema/LoadRule.xsd");
				schemaURL.getContent();
			} catch (MalformedURLException e) {
				LOGGER.log(Level.FINER, e.getMessage(), e);
				useOverride = true;
			} catch (IOException e) {
				// can't read the contents of the schema
				useOverride = true;
			}
		} catch (ClassNotFoundException e) {
			// we support only new load rule format when load policy is set to use load rules
			if (isLoadPolicySetToUseLoadRules()
					|| (isLoadPolicySetToUseDynamicLoadRules() && pathToDynamicLoadRuleFile != null && pathToDynamicLoadRuleFile.trim().length() > 0)) {
				e.printStackTrace();
				throw new RTCConfigurationException(Messages.get(clientLocale).BuildConfiguration_loadrule2_class_not_found());
			}
			// uses old load rule support
		}
		// first preference given to dynamic load rules
		if (isLoadPolicySetToUseDynamicLoadRules()) {
			Collection<Object> loadRules = new ArrayList<Object>();
			ILoadRule2 customLoadRules = getCustomLoadRules(workspaceConnection, pathToDynamicLoadRuleFile, buildLog, clientLocale);
			if (customLoadRules != null) {
				LOGGER.finest("BuildConfiguration.getComponentLoadRules: there are load rules provided through extension."); //$NON-NLS-1$
				loadRules.add(customLoadRules);
			}
			if (loadRules.size() > 0) {
				return loadRules;
			}
		} else if (loadRuleUUIDs != null) {

			Collection loadRules = new ArrayList<Object>();
			Map<String, Object> allLoadRules = new HashMap<String, Object>();

			ComponentLoadRules rules = new ComponentLoadRules(loadRuleUUIDs);
			Map<IComponentHandle, IFileItemHandle> ruleFiles = rules.getLoadRuleFiles();
			// support for specifying multiple load rule files, one per component is deprecated from 1.2.0.4
			// fail or log a warning
			if (ruleFiles.size() > 1) {
				// if load policy set to use load rules fail otherwise add warning
				if (isLoadPolicySetToUseLoadRules()) {
					throw new RTCConfigurationException(Messages.get(clientLocale).BuildConfiguration_multiple_load_rule_files_not_supported());
				} else {
					String messageString = Messages.get(clientLocale).BuildConfiguration_multiple_load_rule_files_deprecated();
					buildLog.println(messageString);
					if (LOGGER.isLoggable(Level.WARNING)) {
						LOGGER.warning(messageString);
					}
				}
			}
			if (useOverride) {
				monitor.setWorkRemaining(20 * ruleFiles.size());
				for (Map.Entry<IComponentHandle, IFileItemHandle> entry : ruleFiles.entrySet()) {
					IComponentHandle componentHandle = entry.getKey();
					try {
						Object rule = NonValidatingLoadRuleFactory.getLoadRule(workspaceConnection, componentHandle, entry.getValue(),
								isLoadPolicySetToUseLoadRules(), buildLog, monitor.newChild(10), clientLocale);
						allLoadRules.put(componentHandle.getItemId().getUuidValue(), rule);
					} catch (TeamRepositoryException e1) {
						// Generalized to handle more than just the VersionablePermissionException
						// That exception can't be handled directly because its not defined in older releases
						// which would cause class loading issues.

						// Lets try to build up a more informative error message.
						ITeamRepository repo = (ITeamRepository)workspaceConnection.getResolvedWorkspace().getOrigin();
						IContributor contributor = repo.loggedInContributor();
						IComponent component = null;
						try {
							component = (IComponent)repo.itemManager().fetchCompleteItem(componentHandle, IItemManager.DEFAULT, monitor.newChild(10));
						} catch (TeamRepositoryException e2) {
							// Just throw the original error.
							throw e1;
						}
						if (contributor != null && component != null) {
							throw new TeamRepositoryException(Messages.getDefault().BuildConfiguration_load_rule_access_failed(
									contributor.getUserId(), component.getName(), e1.getMessage()), e1);
						} else {
							throw e1;
						}
					}
				}

			} else {
				for (Map.Entry<IComponentHandle, IFileItemHandle> entry : ruleFiles.entrySet()) {
					IComponentHandle componentHandle = entry.getKey();
					try {
						Map<IComponentHandle, IFileItemHandle> cFile = new HashMap<IComponentHandle, IFileItemHandle>(1);
						cFile.put(entry.getKey(), entry.getValue());
						// check if load rule is in xml format. ComponentLoadRules.getLoadRules in turn invokes
						// filesystem utilities to construct the loadrules instance, so do the validation before hand
						NonValidatingLoadRuleFactory.checkForObsoleteLoadRuleFormat(workspaceConnection, componentHandle, entry.getValue(),
								isLoadPolicySetToUseLoadRules(), buildLog, monitor, clientLocale);
						ComponentLoadRules cRule = new ComponentLoadRules(cFile);
						Collection<ILoadRule2> cRuleObj = cRule.getLoadRules(workspaceConnection, monitor.newChild(1));
						if (cRuleObj != null && cRuleObj.size() == 1) {
							allLoadRules.put(componentHandle.getItemId().getUuidValue(), cRuleObj.iterator().next());
						}
					} catch (Exception e) {
						throw new TeamRepositoryException(e);
					}
				}
			}

			if (allLoadRules != null && allLoadRules.size() > 0) {
				loadRules = allLoadRules.values();
			}
			return loadRules;
		}
		return Collections.EMPTY_LIST;
	}
       
    /**
     * Finalizer for a {@link BuildConfiguration} object
     * 
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @param listener A listener that will be notified of the progress and errors encountered.
     * @param clientLocale The locale of the requesting client
     * 
     */
	public void tearDown(RepositoryManager repositoryManager, boolean forceDelete, IProgressMonitor monitor, IConsoleOutput listener, Locale clientLocale){
		LOGGER.finest("BuildConfiguration.tearDown : Enter");
		SubMonitor progress = SubMonitor.convert(monitor, 5);
		if (isSnapshotLoad() || isStreamLoad()) {
			try {
				if (forceDelete || shouldDeleteTemporaryWorkspace) {
					IWorkspace workspaceToDelete = workspace.getWorkspace(repositoryManager, progress.newChild(50));
					deleteWorkspace(workspaceToDelete, progress, listener, clientLocale);
				}
			} catch (TeamRepositoryException exp) {
				if (LOGGER.isLoggable(Level.WARNING)) {
					LOGGER.warning("BuildConfiguration.tearDown : Unable to get workspace details. Log message is " + exp.getMessage());
				}
			}
		}
	}
	
	private void deleteWorkspace(IWorkspace workspaceToDelete, IProgressMonitor monitor, IConsoleOutput listener, Locale clientLocale) {
		SubMonitor progress = SubMonitor.convert(monitor, 10);
		if (LOGGER.isLoggable(Level.INFO)) {
			LOGGER.info("BuildConfiguration.deleteWorkspace : Deleting temporary workspace '" + workspaceToDelete.getName() + "'");
		}
		if (workspaceToDelete != null) {
			getRTCWorkspaceUtils().deleteSilent(workspaceToDelete, 
										getTeamRepository(), progress.newChild(10), listener, clientLocale);
		}
	}

	public ILoadRule2 getCustomLoadRules(IWorkspaceConnection connection, String pathToDynamicLoadRuleFile, PrintStream buildLog, Locale clientLocale)
			throws Exception {
		ILoadRule2 lRules = null;
		if (pathToDynamicLoadRuleFile != null && pathToDynamicLoadRuleFile.trim().length() > 0) {
			File fileHandle = new File(pathToDynamicLoadRuleFile.trim());
			if (!fileHandle.exists()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).BuildConfiguration_load_rule_file_does_not_exist(
						pathToDynamicLoadRuleFile));
			}
			if (!fileHandle.isFile()) {
				throw new IllegalArgumentException(Messages.get(clientLocale).BuildConfiguration_load_rule_file_not_file(pathToDynamicLoadRuleFile));
			}
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("BuildConfiguration.getCustomLoadRules: reading load rule ' from '" + pathToDynamicLoadRuleFile + "'."); //$NON-NLS-1$ //$NON-NLS-2$
			}

			lRules = NonValidatingLoadRuleFactory.getLoadRule(connection, pathToDynamicLoadRuleFile, true, buildLog, null, clientLocale);
		}

		return lRules;
	}
	
	public Collection<ILoadRule2> getCustomLoadRules(IWorkspaceConnection connection, Map<String, String> compLoadRules, PrintStream buildLog,
			Locale clientLocale) throws Exception {
		Collection<ILoadRule2> lRules = new ArrayList<ILoadRule2>();
		if (compLoadRules != null && compLoadRules.size() > 0) {
			for (String comp : compLoadRules.keySet()) {
				String lrFile = compLoadRules.get(comp);
				if (lrFile == null || lrFile.trim().length() == 0) {
					continue;
				}

				File fileHandle = new File(lrFile);
				if (!fileHandle.exists()) {
					throw new IllegalArgumentException(Messages.get(clientLocale).BuildConfiguration_load_rule_file_does_not_exist(lrFile));
				}
				if (!fileHandle.isFile()) {
					throw new IllegalArgumentException(Messages.get(clientLocale).BuildConfiguration_load_rule_file_not_file(lrFile));
				}
				if (LOGGER.isLoggable(Level.FINEST)) {
					LOGGER.finest("BuildConfiguration.getCustomLoadRules: reading load rule for component '" + comp + "' from '" + lrFile + "'."); //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
				}

				lRules.add(NonValidatingLoadRuleFactory
						.getLoadRule(connection, lrFile, false, buildLog, null, clientLocale));
			}
		}
		return lRules;
	}

	private boolean getIncludeComponents(IBuildDefinitionInstance instance) {
		IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_INCLUDE_COMPONENTS);
		if (property != null && property.getValue().length() > 0
				&& (!isLoadPolicySet() || isLoadPolicySetToUseComponentLoadConfig() && isComponentLoadConfigSetToExcludeSomeComponents())) {
			return Boolean.valueOf(property.getValue());
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private Collection<IComponentHandle> getLoadComponents(IBuildDefinitionInstance instance) {
		IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS);
		if (property != null && property.getValue().length() > 0
				&& (!isLoadPolicySet() || isLoadPolicySetToUseComponentLoadConfig() && isComponentLoadConfigSetToExcludeSomeComponents())) {
			return new LoadComponents(getTeamRepository(), property.getValue()).getComponentHandles();

		}
		return Collections.EMPTY_LIST;
	}
    
    private boolean getDeleteBeforeFetch(IBuildDefinitionInstance instance) {
        IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH);
        if (property != null && property.getValue().length() > 0) {
            return Boolean.valueOf(property.getValue());
        }
        return false;
    }

	private boolean getCreateFoldersForComponents(IBuildDefinitionInstance instance) {
		IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS);
		if (property != null && property.getValue().length() > 0 && (!isLoadPolicySet() || isLoadPolicySetToUseComponentLoadConfig())) {
			return Boolean.valueOf(property.getValue());
		}
		return false;
	}

    private boolean getAcceptBeforeFetch(IBuildDefinitionInstance instance) {
        IBuildProperty property = instance.getProperty(IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH);
        if (property != null && property.getValue().length() > 0) {
            return Boolean.valueOf(property.getValue());
        }
        return false;
    }
    
    private String getLoadPolicy(IBuildDefinitionInstance instance) {
        IBuildProperty property = instance.getProperty(Constants.PROPERTY_LOAD_POLICY);
        if (property != null && property.getValue().length() > 0) {
            return property.getValue();
        }
        return null;
    }
    
    private String getComponentLoadConfig(IBuildDefinitionInstance instance) {
        IBuildProperty property = instance.getProperty(Constants.PROPERTY_COMPONENT_LOAD_CONFIG);
        if (property != null && property.getValue().length() > 0) {
            return property.getValue();
        }
        return null;
    }
   

	public ITeamRepository getTeamRepository() {
		return teamRepository;
	}

	/**
	 * @return Descriptor of the build workspace
	 */
	public BuildWorkspaceDescriptor getBuildWorkspaceDescriptor() {
		return workspace;
	}
	
	/**
	 * @return Descriptor of the build snapshot
	 */
	public BuildSnapshotDescriptor getBuildSnapshotDescriptor() {
		return snapshot;
	}
	
	/**
	 * @return Descriptor of the build stream
	 */
	public BuildStreamDescriptor getBuildStreamDescriptor() {
		return stream;
	}

	/**
	 * @return <code>true</code> if an accept is to be performed
	 * prior to the loading of the workspace. <code>false</code>
	 * otherwise
	 */
	public boolean acceptBeforeFetch() {
		return acceptBeforeFetch;
	}

	/**
	 * @return <code>true</code> if this is a personal build, <code>false</code>
	 * otherwise.
	 */
	public boolean isPersonalBuild() {
		return isPersonalBuild;
	}

	/**
	 * @return The location where the contents of the workspace should be loaded
	 * Never <code>null</code>
	 */
	public ILocation getFetchDestinationPath() {
		return new PathLocation(fetchDestinationPath);
	}

	/**
	 * @return The location where the contents of the workspace should be loaded
	 * Never <code>null</code>
	 */
	public File getFetchDestinationFile() {
		return fetchDestinationFile;
	}

	/**
	 * @return <code>true</code> if the contents of the fetch destination directory should be
	 * deleted prior to the load. <code>false</code> otherwise
	 */
	public boolean isDeleteNeeded() {
		return deleteNeeded;
	}

	/**
	 * @return <code>true</code>> if the list of components configured are intended to be
	 * loaded. <code>false</code> if the list of components configured are intended to be
	 * excluded from the load.
	 */
	public boolean includeComponents() {
		return includeComponents;
	}

	/**
	 * @return The components to be either included/excluded from the load. Never
	 * <code>null</code>
	 */
	public Collection<IComponentHandle> getComponents() {
		return components;
	}

	/**
	 * @return <code>true</code> if the component root directory should be loaded (and named the same
	 * as the component), not just the contents under the component root. <code>false</code> just load
	 * the contents defined by the load rule, or the items directly under the component root.
	 */
	public boolean createFoldersForComponents() {
		return createFoldersForComponents;
	}
	
	public boolean isLoadPolicySetToUseLoadRules() {
		return loadPolicy != null && Constants.LOAD_POLICY_USE_LOAD_RULES.equals(loadPolicy);
	}

	public boolean isLoadPolicySetToUseComponentLoadConfig() {
		return loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy);
	}
	
	public boolean isLoadPolicySetToUseDynamicLoadRules() {
		return loadPolicy != null && Constants.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES.equals(loadPolicy);
	}

	public boolean isLoadPolicySet() {
		return loadPolicy != null;
	}

	public boolean isComponentLoadConfigSetToExcludeSomeComponents() {
		return componentLoadConfig != null && Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS.equals(componentLoadConfig);
	}
	
	public boolean isBuildDefinitionConfiguration() {
		return isBuildDefinitionConfiguration;
	}
	
	/**
	 * @return The name to give the snapshot created. Never <code>null</code>
	 */
	public String getSnapshotName() {
		return snapshotName;
	}
	
	/**
	 * @return The uuid of the snapshot generated. May be <code>null</code>
	 */
	public String getSnapshotUUID() {
		if (snapshot != null) {
			return snapshot.getSnapshotUUID();
		}
		if (stream != null) {
			return stream.getSnapshotUUID();
		}
		return null;
	}
	
	/**
	 * Returns the build properties.
	 * 
	 * @return Map<String, String> of build properties
	 */
	public Map<String, String> getBuildProperties() {
		return buildProperties;
	}
	
	/**
	 * 
	 * @return Map<String, String> of temporary workspace details
	 */
    public Map<String, String> getTemporaryRepositoryWorkspaceProperties() {
    	return temporaryRepositoryWorkspaceProperties;
    }
	   
	public boolean isSnapshotLoad() {
		return (snapshot != null && workspace != null);
	}
	
	public boolean isStreamLoad() {
		return (stream != null && workspace != null);
	}

    /**
     * Substitute for variables in the build and configuration properties.
     * 
     * @param buildRequest
     *            The request. Must be a working copy.
     * @param buildProperties
     *            The collected build properties. Includes built-in, engine, and
     *            definition properties.
	 * @param listener
	 *            A listener that will be notified of the progress and errors encountered.
     */
    private void performPropertyVariableSubstitutions(IBuildRequest buildRequest, Map<String, String> buildProperties, final IConsoleOutput listener) {
        List<String> substitutions = PropertyVariableHelper.substituteBuildPropertyVariables(buildProperties);

        updateRequestWithSubstitutedProperties(buildRequest, buildProperties);

        printSubstitutedProperties(listener, substitutions, Messages.getDefault().BuildConfiguration_substituted_build_variables());

        substitutions = PropertyVariableHelper.substituteConfigurationElementPropertyVariables(
                buildRequest.getBuildDefinitionInstance().getConfigurationElements(), buildProperties);

        printSubstitutedProperties(listener, substitutions, Messages.getDefault().BuildConfiguration_substituted_config_variables());
    }

    /**
     * Take any of the substituted properties that were part of the generic
     * properties in the request and update them.
     * 
     * @param buildRequest
     *            The request to update. Must be a working copy.
     * @param buildProperties
     *            The substituted property name/value pairs.
     */
    private void updateRequestWithSubstitutedProperties(IBuildRequest buildRequest, Map<String, String> buildProperties) {
        IBuildDefinitionInstance definitionInstance = buildRequest.getBuildDefinitionInstance();

        for (Map.Entry<String, String> buildProperty : buildProperties.entrySet()) {
            IBuildProperty property = definitionInstance.getProperty(buildProperty.getKey());
            if (property != null) {
                property.setValue(buildProperty.getValue());
            }
        }
    }

    private void printSubstitutedProperties(final IConsoleOutput listener, List<String> substitutedBuildProperties, String title) {
        if (!substitutedBuildProperties.isEmpty()) {
        	listener.log(""); //$NON-NLS-1$
        	listener.log(title);
            for (String description : substitutedBuildProperties) {
            	listener.log("\t" + description); //$NON-NLS-1$
            }
            listener.log(""); //$NON-NLS-1$
        }
    }

	private void setTemporaryRepositoryWorkspaceProperties(IWorkspace workspace) {
		if (workspace != null) {
			this.temporaryRepositoryWorkspaceProperties.put(Constants.TEMPORARY_WORKSPACE_NAME, workspace.getName());
			this.temporaryRepositoryWorkspaceProperties.put(Constants.TEMPORARY_WORKSPACE_UUID, workspace.getItemId().getUuidValue());
		}
	}
	
	/**
	 * Add Build Properties for Repository Workspace, Snapshot, Stream load type
	 * However, Stream and snapshot do not use BuildConfiguration during accept
	 * and only accept returns properties built in BuildConfiguration to export
	 * to environment.
	 * TODO One possible way to solve this is to share the BuildConfiguration created during
	 * accept for load, which also means we create the workspace during accept for 
	 * snapshot type, to maintain consistency).
	 * 
	 */
	private void addCommonBuildProperties() {
		if (getTeamRepository() != null) {
			buildProperties.put(Constants.REPOSITORY_ADDRESS, getTeamRepository().getRepositoryURI());
		}
	}
	
	/**
	 * Creates a repository workspace with retry logic. Retries for <code>MAX_RETRY_COUNT</code>
	 * times until a successful Repository Workspace is created. This is intended as a workaround 
	 * to address defect 404637, until the problem is fixed in RTC server
	 * 
	 * @param repository  The RTC repository to work with 
	 * @param baselineSet The Snapshot from which a new repository workspace should be created 
	 * @param workspaceName  The name for the new repository workspace 
	 * @param workspaceComment The description for the new repository workspace
	 * @param contributor The owner for the Repository workspace
	 * @param progress A progress monitor
	 * @return a {@link IWorkspaceConnection} for the newly created Repository Workspace
	 * @throws TeamRepositoryException only if there is an exception on all tries to create a 
	 * Repository Workspace. If the creation of Repository Workspace is successful in at least
	 * one attempt, then any previous exceptions are ignored and are not logged or thrown.
	 * @throws RTCInternalException 
	 * 		
	 */
	public IWorkspaceConnection createRepositoryWorkspaceWithRetry(ITeamRepository repository, 
										IBaselineSet baselineSet, String workspaceName, 
										String workspaceComment, IContributorHandle contributor, 
										IConsoleOutput listener,
										IProgressMonitor progress) throws TeamRepositoryException, RTCInternalException {
		TeamRepositoryException caughtException = null;
		IWorkspaceConnection workspaceConnection = null;
		SubMonitor monitor = SubMonitor.convert(progress, 10);
		try {
			for (int i = 0 ; i < getRetryCount(); i++) {
				try {
					workspaceConnection = getRTCWorkspaceUtils().createWorkspace(repository, 
										baselineSet, workspaceName, workspaceComment, 
										contributor, monitor.newChild(8));
					break;
				} catch (InternalRepositoryException exp) {
					caughtException = exp;
				} catch (TeamRepositoryException exp) {
					throw exp;
				} 
				listener.log(Messages.getDefault().BuildConfiguration_repo_workspace_retry());
				try {
					if (i < getRetryCount() - 1) {
						Thread.sleep(getSleepTimeout());
					}
				} catch (InterruptedException exp ) {
					// Silently ignore and continue
				}
			}
			monitor.worked(2);
			if (workspaceConnection == null) {
				listener.log(Messages.getDefault().BuildConfiguration_repo_workspace_create_failed(String.valueOf(getRetryCount())));
				// Return the last caught InternalRepositoryException
				throw new RTCInternalException(String.format("Failed to create a Repository Workspace after %s retries", getRetryCount()), caughtException);
			}
			return workspaceConnection;
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * Supply a different timeout for testing
	 * 
	 * @return the number of milliseconds to sleep
	 */
	protected long getSleepTimeout() {
		return SLEEP_TIMEOUT_SECONDS * 1000;
	}

	/**
	 * Supply a different retry count for testing
	 * 
	 * @return the number of times to retry
	 */
	protected int getRetryCount() {
		return MAX_RETRY_COUNT;
	}
	
	/**
	 * Supply a mock RTCWorkspaceUtils for testing
	 * 
	 * @return {@link RTCWorkspaceUtils} instance.
	 * 
	 */
	protected RTCWorkspaceUtils getRTCWorkspaceUtils() {
		return RTCWorkspaceUtils.getInstance();
	}
}
