/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.client.ITeamBuildRequestClient;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.ScmConstants;
import com.ibm.team.build.common.TeamBuildException;
import com.ibm.team.build.common.TeamBuildStateException;
import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.BuildStatus;
import com.ibm.team.build.common.model.IBuildAction;
import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildEngine;
import com.ibm.team.build.common.model.IBuildEngineHandle;
import com.ibm.team.build.common.model.IBuildItemHandle;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestHandle;
import com.ibm.team.build.common.model.IBuildRequestParams;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultContribution;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.common.model.query.IBaseBuildEngineQueryModel.IBuildEngineQueryModel;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.common.schedule.IBuildScheduleTaskProperties;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.query.IItemQuery;
import com.ibm.team.repository.common.query.IItemQueryPage;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;

public class BuildConnection {

    public static final int OK = 0;
    public static final int UNSTABLE = 1;
    public static final int ERROR = 2;

	private static final Logger LOGGER = Logger.getLogger(BuildConnection.class.getName());

    /**
     * Minimum properties required when retrieving the build result for setting status
     */
    private Collection<String> PROPERTIES = Arrays.asList(new String[] { IBuildResult.PROPERTY_BUILD_STATE,
            IBuildResult.PROPERTY_BUILD_DEFINITION, IBuildResult.PROPERTY_BUILD_STATUS });

    /**
     * The engine element ID.  Constant from HudsonConfigurationElement
     */
    public static final String HJ_ENGINE_ELEMENT_ID = "com.ibm.rational.connector.hudson.engine"; //$NON-NLS-1$

	/**
	 * The Hudson URL. Constant from HudsonConfigurationElement
	 */
	public static final String PROPERTY_HUDSON_URL = "com.ibm.rational.connector.hudson.url"; //$NON-NLS-1$

	private static final String SLASH = "/"; //$NON-NLS-1$

	final private ITeamRepository fTeamRepository;
	private ITeamBuildClient fTeamBuildClient;
	private ITeamBuildRequestClient fTeamBuildRequestClient;
	
	public BuildConnection(ITeamRepository repository) {
		this.fTeamRepository = repository;
	}

	/**
	 * Create a build result to report back the build progress in RTC
	 * @param buildDefinitionId The id of the build definition. There must be a build definition and it
	 * will need to have an active build engine associated with it.
	 * @param personalBuildWorkspace Override the build workspace in the build definition with a personal workspace
	 * @param buildLabel The label to assign to the build
	 * @param listener A log to report progress and failures to.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return The build result to update with progress of the Jenkins build. May be <code>null</code>
	 * if there is no build engine associated with the build definition
	 * @throws TeamRepositoryException Thrown if problems are encountered
	 * @throws RTCConfigurationException Thrown if the build definition is not valid
	 */
	public IBuildResultHandle createBuildResult(String buildDefinitionId,
			IWorkspaceHandle personalBuildWorkspace, String buildLabel,
			IConsoleOutput listener, IProgressMonitor progress, Locale clientLocale)
			throws TeamRepositoryException, RTCConfigurationException {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IBuildDefinition buildDefinition = getBuildDefinition(buildDefinitionId, monitor.newChild(20));
		if (buildDefinition == null) {
			throw new RTCConfigurationException(Messages.get(clientLocale).BuildConnection_build_definition_not_found(buildDefinitionId));
		}
		List<IBuildProperty> modifiedProperties = new ArrayList<IBuildProperty>(1);
		boolean isPersonal = false;
		if (personalBuildWorkspace != null) {
			isPersonal = true;
			IBuildProperty originalProperty = buildDefinition.getProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
			if (originalProperty != null && !originalProperty.getValue().equals(personalBuildWorkspace.getItemId().getUuidValue())) {
				modifiedProperties.add(BuildItemFactory.createBuildProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, personalBuildWorkspace.getItemId().getUuidValue()));
			}
		}
		
		IBuildEngineHandle buildEngine = getBuildEngine(buildDefinition, monitor.newChild(20));
		if (buildEngine == null) {
			LOGGER.finer("There are no RTC build engines associated with the RTC build definition. The build definition must have a supported active build engine"); //$NON-NLS-1$
			LOGGER.finer("Unable to create a build result for the build"); //$NON-NLS-1$
			// TODO? just use the build definition as is and don't publish the results to RTC?
			// Right now we expect build result or workspace uuids on checkout
			throw new RTCConfigurationException(Messages.get(clientLocale).BuildConnection_no_build_engine_for_defn(buildDefinitionId));
		}

        IBuildRequestParams params = BuildItemFactory.createBuildRequestParams();
        params.setBuildDefinition(buildDefinition);
        params.getNewOrModifiedBuildProperties().addAll(modifiedProperties);
        params.setAllowDuplicateRequests(true);
        params.setPersonalBuild(isPersonal);
        params.getPotentialHandlers().add(buildEngine);
        params.setStartBuild(true);
        IBuildRequest buildRequest = getTeamBuildRequestClient().requestBuild(params, monitor.newChild(20));
        IBuildResultHandle buildResultHandle = buildRequest.getBuildResult();

        if (buildLabel != null) {
            IBuildResult buildResult = (IBuildResult) getTeamRepository().itemManager().fetchPartialItem(
                    buildResultHandle, IItemManager.REFRESH,
                    Arrays.asList(IBuildResult.PROPERTIES_VIEW_ITEM), monitor.newChild(20));

            buildResult = (IBuildResult) buildResult.getWorkingCopy();
            buildResult.setLabel(buildLabel);
            getTeamBuildClient().save(buildResult, monitor.newChild(20));
        }

		return buildResultHandle;
	}

	/**
	 * Mark the build result as terminated
	 * @param buildResultUUID
	 * @param aborted Whether the Jenkins build was aborted
	 * @param buildState Whether the Jenkins build was a success, failure, or unstable
	 * 0 = success, 1 = unstable, 2 = not success or unstable. We can't create constants
	 * because the -rtc plugin doesn't expose anything other than the facade.
	 * @param listener
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @throws TeamRepositoryException
	 */
	public void terminateBuild(String buildResultUUID, boolean aborted, int buildState,
			IConsoleOutput listener, IProgressMonitor progress) throws TeamRepositoryException {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);

        IBuildResult buildResult = (IBuildResult) getTeamRepository().itemManager().fetchPartialItem(
                buildResultHandle, IItemManager.REFRESH, PROPERTIES, monitor.newChild(10));

        // Do nothing if the build result is already in a final state.
        if (buildResult.getState() == BuildState.CANCELED || buildResult.getState() == BuildState.INCOMPLETE
                || buildResult.getState() == BuildState.COMPLETED) {
            return;
        }

        if (aborted) {
        	getTeamBuildRequestClient().makeBuildIncomplete(buildResultHandle, IBuildItemHandle.PROPERTIES_REQUIRED, monitor.newChild(90));
        } else {
        	if (buildState == UNSTABLE && BuildStatus.WARNING.isMoreSevere(buildResult.getStatus())) {
            	buildResult = (IBuildResult) buildResult.getWorkingCopy();
        		buildResult.setStatus(BuildStatus.WARNING);
            	getTeamBuildClient().save(buildResult, monitor.newChild(50));
        	} else if (buildState > UNSTABLE && BuildStatus.ERROR.isMoreSevere(buildResult.getStatus())) {
            	buildResult = (IBuildResult) buildResult.getWorkingCopy();
        		buildResult.setStatus(BuildStatus.ERROR);
            	getTeamBuildClient().save(buildResult, monitor.newChild(50));
        	}
        	getTeamBuildRequestClient().makeBuildComplete(buildResultHandle, false, IBuildItemHandle.PROPERTIES_REQUIRED, monitor.newChild(40));
        }
	}

	/**
	 * Helper method to get a valid build engine a build for the definition
	 * supplied can be recorded against.
	 * 
	 * @param buildDefinition The definition of the build that will be started
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @return Handle to a build engine that can run a build with the definition supplied
	 * @throws TeamRepositoryException Thrown if anything goes wrong
	 */
    public IBuildEngineHandle getBuildEngine(IBuildDefinition buildDefinition, IProgressMonitor progress) throws TeamRepositoryException {
    	SubMonitor monitor = SubMonitor.convert(progress, 100);

        IBuildEngineQueryModel queryModel = IBuildEngineQueryModel.ROOT;
        IItemQuery query = IItemQuery.FACTORY.newInstance(queryModel);
        query.filter(queryModel.active()._isTrue()._and(
                queryModel.supportedBuildDefinitions().itemId()._eq(query.newUUIDArg())));
        query.setResultLimit(1);

        IItemQueryPage page = getTeamBuildClient().queryItems(query, new Object[] { buildDefinition.getItemId() }, 1, monitor);
        if (page.getResultSize() != 0) {
        	return (IBuildEngineHandle) page.getItemHandles().get(0);
        }
		return null;
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
		
		// validate the build definition exists
		IBuildDefinition buildDefinition = getBuildDefinition(buildDefinitionId, monitor.newChild(25));
        if (buildDefinition == null) {
            throw new RTCValidationException(Messages.get(clientLocale).BuildConnection_build_definition_not_found(buildDefinitionId));
        }
        
        // validate the build definition has a supporting build engine
		IBuildEngineHandle buildEngineHandle = getBuildEngine(buildDefinition, monitor.newChild(25));
		if (buildEngineHandle == null) {
            throw new RTCValidationException(Messages.get(clientLocale).BuildConnection_build_definition_missing_build_engine());
		}
		
		// validate the build definition is a hudson/jenkins build definition
        IBuildConfigurationElement hudsonDefinitionBuildConfigurationElement = buildDefinition.getConfigurationElement("com.ibm.rational.connector.hudson"); //$NON-NLS-1$
        if (hudsonDefinitionBuildConfigurationElement == null) {
            throw new RTCValidationException(Messages.get(clientLocale).BuildConnection_build_definition_missing_hudson_config());
        }
        
        // validate the build definition has a hudson/jenkins build engine
		IBuildEngine buildEngine = (IBuildEngine) getTeamRepository().itemManager().fetchPartialItem(
                buildEngineHandle, IItemManager.REFRESH,
                Arrays.asList(IBuildEngine.PROPERTY_CONFIGURATION_ELEMENTS), monitor.newChild(50));
		IBuildConfigurationElement hudsonEngineBuildConfigurationElement = buildEngine.getConfigurationElement("com.ibm.rational.connector.hudson.engine"); //$NON-NLS-1$
		if (hudsonEngineBuildConfigurationElement == null) {
            throw new RTCValidationException(Messages.get(clientLocale).BuildConnection_build_definition_missing_build_engine_hudson_config());
		}
		
		// validate the build definition as a Jazz Source Control option
		IBuildConfigurationElement jazzScmDefinitionConfigurationElement = buildDefinition.getConfigurationElement("com.ibm.team.build.jazzscm"); //$NON-NLS-1$
		if (jazzScmDefinitionConfigurationElement == null) {
			throw new RTCValidationException(Messages.get(clientLocale).BuildConnection_build_definition_missing_jazz_scm_config());
		}
	}

	/**
     * Helper method to get the build definition.
     * 
     * @param buildDefinitionId
     *            The id of the build definition to retrieve.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @return The build definition represented by the build definition id, or null
     * @throws TeamRepositoryException
     *             If an error occurred retrieving the build definition.
     */
    public IBuildDefinition getBuildDefinition(String buildDefinitionId, IProgressMonitor progress) throws TeamRepositoryException {
        return getTeamBuildClient().getBuildDefinition(buildDefinitionId, progress);
    }

    /**
     * Retrieves the team build client associated with the logged in repository.
     * 
     * @return The team build client associated with the logged in repository.
     * @throws TeamRepositoryException
     *             If the client could not be acquired.
     */
    protected ITeamBuildClient getTeamBuildClient() {
        if (fTeamBuildClient == null) {
            fTeamBuildClient = ClientFactory.getTeamBuildClient(getTeamRepository());
        }

        return fTeamBuildClient;
    }

    /**
     * Retrieves the team build client associated with the logged in repository.
     * 
     * @return The team build client associated with the logged in repository.
     * @throws TeamRepositoryException
     *             If the client could not be acquired.
     */
    protected ITeamBuildRequestClient getTeamBuildRequestClient() {
        if (fTeamBuildRequestClient == null) {
            fTeamBuildRequestClient = ClientFactory.getTeamBuildRequestClient(getTeamRepository());
        }

        return fTeamBuildRequestClient;
    }

    private ITeamRepository getTeamRepository() {
		return fTeamRepository;
	}


    /**
     * Adds a workspace contribution to the build result.
     * 
     * @param workspace
     *            The workspace to add.
     * @param resultHandle Build result handle.
     * 			  If <code>null</code> no contribution will be added.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @throws TeamRepositoryException
     * @throws IllegalArgumentException
     */
    public void addWorkspaceContribution(IWorkspace workspace, IBuildResultHandle resultHandle,
    		IProgressMonitor progress) throws IllegalArgumentException,
            TeamRepositoryException {

    	// if no build result, we are done
    	if (resultHandle == null) {
    		return;
    	}

        IBuildResultContribution contribution = BuildItemFactory.createBuildResultContribution();
        contribution.setExtendedContributionTypeId(ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_WORKSPACE);
        contribution.setImpactsPrimaryResult(false);
        contribution.setLabel(workspace.getName());
        contribution.setExtendedContribution(workspace);

        getTeamBuildClient().addBuildResultContribution(resultHandle, contribution, progress);
    }

    /**
     * Adds a snapshot contribution to the build result.
     * 
     * @param snapshot
     *            The snapshot to add.
     * @param resultHandle Build result handle.
     * 			  If <code>null</code> no contribution will be added.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @throws TeamRepositoryException
     * @throws IllegalArgumentException
     */
    public void addSnapshotContribution(IBaselineSet snapshot, IBuildResultHandle resultHandle,
    		IProgressMonitor progress) throws IllegalArgumentException,
            TeamRepositoryException {
    	// if no build result, we are done
    	if (resultHandle == null || snapshot == null) {
    		return;
    	}

        IBuildResultContribution contribution = BuildItemFactory.createBuildResultContribution();
        contribution.setExtendedContributionTypeId(ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_SNAPSHOT);
        contribution.setImpactsPrimaryResult(false);
        contribution.setLabel(Messages.getDefault().BuildConnection_snapshot_label(snapshot.getName()));
        contribution.setExtendedContribution(snapshot);

        getTeamBuildClient().addBuildResultContribution(resultHandle, contribution, progress);
    }

    /**
     * Start a build activity with the label in the specified build result.
     * Returns a unique id for the newly started activity.
     * <p>
     * If <code>parentId</code> is set, the new activity is created as a child
     * of the existing activity with that id.
     * <p>
     * If <code>autoComplete</code> is true, then this activity will be
     * automatically completed when either the build is completed, this
     * activity's parent activity is completed or when the next peer activity is
     * started.
     * <p>
     * 
     * @param buildResultHandle
     *            The build result to start the activity in.
     * 			  If <code>null</code> no activity will be started.
     * @param label
     *            The label describing the activity.
     * @param parentId
     *            Optional id of a parent activity.
     * @param autoComplete
     *            Whether or not to automatically complete this activity.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @return The unique id of the new activity.
     * @throws TeamBuildStateException
     *             If the build is not currently in progress.
     * @throws TeamBuildException
     *             If <code>parentId</code> is set but an activity with that
     *             id is not found or is found but has already completed.
     * @throws TeamRepositoryException
     *             If an error occurs while accessing the repository.
     * @throws IllegalArgumentException
     *             If <code>buildResultHandle</code> or <code>label</code>
     *             is <code>null</code>, or if <code>label</code> is empty.
     */
    public String startBuildActivity(IBuildResultHandle buildResultHandle, String label, String parentId,
            boolean autoComplete, IProgressMonitor progress) throws TeamRepositoryException,
            IllegalArgumentException {
    	// if no build result, we are done
    	if (buildResultHandle == null) {
    		return "";
    	}

		return getTeamBuildClient().startBuildActivity(buildResultHandle, label, parentId, autoComplete, progress);
	}

    /**
     * Complete the build activity with the specified id in the specified build
     * result.
     * 
     * @param buildResultHandle
     *            The build result to complete the activity in.
     * 			  If <code>null</code> no activity will be completed.
     * @param id
     *            The id of the activity to complete.
 	 * @param progress A progress monitor to check for cancellation with (and mark progress).
     * @throws TeamRepositoryException
     *             If an error occurs while accessing the repository.
     * @throws IllegalArgumentException
     *             If <code>buildResultHandle</code> or <code>id</code> is
     *             <code>null</code> or if <code>id</code> is empty.
     */
	public void completeBuildActivity(IBuildResultHandle buildResultHandle,
			String activityId, IProgressMonitor progress) throws IllegalArgumentException, TeamRepositoryException {
    	// if no build result, we are done
    	if (buildResultHandle == null) {
    		return;
    	}

		getTeamBuildClient().completeBuildActivity(buildResultHandle, activityId, progress);
	}

	/**
	 * Add to the build result, links to H/J artifacts. In particular the project and the build.
	 * @param buildResultUUID The uuid of the build result that the links are to be added to
	 * @param rootUrl The root url of the H/J server
	 * @param projectUrl The relative link to the H/J project
	 * @param buildUrl The relative link to the H/J build
	 * @param clientConsole The console to send  interesting messages to.
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @throws TeamRepositoryException Thrown if anything goes wrong
	 */
	public void createBuildLinks(String buildResultUUID, String rootUrl,
			String projectUrl, String buildUrl, IConsoleOutput clientConsole,
			IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		if (buildResultUUID == null) {
			return;
		}
		
		IBuildResultHandle resultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
		if (rootUrl == null) {
			LOGGER.finer("Hudson/Jenkins root url has not been configured. Attempting to use the one specified by the RTC build engine for build " + buildUrl); //$NON-NLS-1$

			// get the root url from the build engine
			IBuildResult result = (IBuildResult) getTeamRepository()
					.itemManager()
					.fetchPartialItem(
							resultHandle,
							IItemManager.REFRESH,
							Collections
									.singleton(IBuildResult.PROPERTY_BUILD_REQUESTS),
							monitor.newChild(5));
            Collection<String> requestProperties = Arrays.asList(new String[] { IBuildRequest.PROPERTY_BUILD_ACTION,
                    IBuildRequest.PROPERTY_HANDLER });
            monitor.setWorkRemaining(50 + 10 * result.getBuildRequests().size());
            for (IBuildRequestHandle buildRequestHandle : (List<IBuildRequestHandle>) result.getBuildRequests()) {
                IBuildRequest buildRequest = (IBuildRequest) getTeamRepository().itemManager().fetchPartialItem(
                        buildRequestHandle, IItemManager.REFRESH, requestProperties, monitor.newChild(5));
                if (buildRequest.getBuildAction().getAction().equals(IBuildAction.REQUEST_BUILD)
                        && buildRequest.getHandler() != null) {
					IBuildEngine buildEngine = (IBuildEngine) getTeamRepository()
							.itemManager()
							.fetchPartialItem(
									buildRequest.getHandler(),
									IItemManager.REFRESH,
									Collections
											.singleton(IBuildEngine.PROPERTY_CONFIGURATION_ELEMENTS),
									monitor.newChild(5));
                    rootUrl = buildEngine.getConfigurationPropertyValue(HJ_ENGINE_ELEMENT_ID, PROPERTY_HUDSON_URL, null);
                    break;
                }
            }
		}
		
		if (rootUrl == null) {
			clientConsole.log(Messages.getDefault().BuildConnection_missing_root_url());
		} else {
			if (!rootUrl.endsWith(SLASH)) {
				rootUrl = rootUrl + SLASH;
			}
			addLinkContribution(Messages.getDefault().BuildConnection_hj_job(), rootUrl + projectUrl, resultHandle, monitor.newChild(25));
			addLinkContribution(Messages.getDefault().BuildConnection_hj_build(), rootUrl + buildUrl, resultHandle, monitor.newChild(25));
		}
	}

    private void addLinkContribution(String label, String url, IBuildResultHandle resultHandle,
    		IProgressMonitor monitor) throws TeamRepositoryException {
    	// if no build result, we are done
    	if (resultHandle == null) {
    		return;
    	}

		IBuildResultContribution contribution = BuildItemFactory
				.createBuildResultContribution();
		contribution.setExtendedContributionTypeId(IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID);
		contribution.setLabel(label);
		contribution.setExtendedContributionProperty(
				IBuildResultContribution.PROPERTY_NAME_URL, url);

        getTeamBuildClient().addBuildResultContribution(resultHandle, contribution, monitor);
    }

    /**
     * Provides information contained in the build result/request to the caller
     * Initially provides information about the cause of the build (scheduled, personal, etc.).
     * 
     * @param buildResultInfo The structure to be updated with the build result info.
     * @param clientConsole The console to put messages
     * @param progress Monitor to mark progress on
     * @throws TeamRepositoryException Thrown if the information can not be retrieved
     */
	public void getBuildResultInfo(IBuildResultInfo buildResultInfo,
			IConsoleOutput clientConsole, IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IItemManager itemManager = getTeamRepository().itemManager();

		IBuildResultHandle resultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultInfo.getBuildResultUUID()), null);
		IBuildResult buildResult = (IBuildResult) itemManager.fetchCompleteItem(resultHandle, IItemManager.DEFAULT, monitor.newChild(25));
        if (!buildResult.getBuildRequests().isEmpty()) {
	        IBuildRequest buildRequest = (IBuildRequest) itemManager.fetchCompleteItem((IBuildRequestHandle) buildResult.getBuildRequests().iterator().next(),
	        		IItemManager.REFRESH, monitor.newChild(10));
	        boolean isScheduled = isScheduledRequest(buildRequest);
	        buildResultInfo.setScheduled(isScheduled);
	        
	        if (!isScheduled) {
	        	buildResultInfo.setPersonalBuild(buildResult.isPersonalBuild());
	        	
	        	IContributorHandle requestor = buildRequest.getInitiatingContributor();
	        	if (requestor != null) {
	        		IContributor contributor = (IContributor) itemManager.fetchCompleteItem(requestor, IItemManager.DEFAULT, monitor.newChild(25));
	        		buildResultInfo.setRequestor(contributor.getName());
	        	}
	        }
        }
	}
	
    /**
     * Determines if the build request is a scheduled request.
     * 
     * @param buildRequest
     *            the request to determine if it's scheduled
     * @return <code>true</code> if the build request is a scheduled request.
     */
    private boolean isScheduledRequest(IBuildRequest buildRequest) {
        return containsProperty(buildRequest, IBuildScheduleTaskProperties.PROPERTY_SCHEDULED_BUILD, "true"); //$NON-NLS-1$
    }

    private boolean containsProperty(IBuildRequest request, String name, String value) {
        Map properties = request.getBuildDefinitionProperties();
        if (properties != null) {
            String propertyValue = (String) properties.get(name);
            if (propertyValue != null && propertyValue.equals(value)) {
                return true;
            }
        }
        return false;
    }

}
