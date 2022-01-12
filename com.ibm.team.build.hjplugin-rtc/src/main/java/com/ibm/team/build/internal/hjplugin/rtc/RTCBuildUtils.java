/*******************************************************************************
 * Copyright (c) 2017, 2021 IBM Corporation and others.
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.client.ITeamBuildRequestClient;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.ScmConstants;
import com.ibm.team.build.common.builddefinition.AutoDeliverTriggerPolicy;
import com.ibm.team.build.common.builddefinition.IAutoDeliverConfigurationElement;
import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildDefinitionInstance;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestHandle;
import com.ibm.team.build.common.model.IBuildRequestParams;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultContribution;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.client.util.ContentUtil;
import com.ibm.team.build.internal.common.model.BuildResultContribution;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.ItemManager;
import com.ibm.team.repository.common.IContent;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.common.IBaselineSet;

/**
 * 
 * Utility class for getting information on RTC Build artifacts 
 * like definitions, engines and results.
 *
 */
public class RTCBuildUtils {
	

	private static final String NO_BUILD_REQUEST_LOG_MSG = "Request build did not " //$NON-NLS-1$
			+ "return a valid build request. The build request may not have any "//$NON-NLS-1$
			+ "supporting engines or all supporting engines are inactive"; //$NON-NLS-1$

	private static final Logger LOGGER = Logger.getLogger(RTCBuildUtils.class.getName());

	/**
	 * The singleton
	 */
	private static RTCBuildUtils instance = null;
	
	/**
	 * Max time limit to wait for a build in seconds. This is a very *long* time.
	 */
	private static long BUILD_WAIT_TIMEOUT_MAX = Long.MAX_VALUE;
	
	/**
	 * The maximum number of results to be listed for {@link #listFiles}
	 */
	public static int LIST_FILES_MAX_RESULTS = 2048;
	
	/**
	 * The number of attempts made to choose a unique destination file
	 */
	private static final int DESTINATION_FILE_NAME_CHOOSER_MAX_ATTEMPTS = 100;

	/**
	 * The maximum length of the description that will be considered. 
	 * This provides an upper bound of the memory requirements of the 
	 * return value. 
	 */
	private static final int FILE_CONTRIBUTION_LABEL_MAX_LENGTH = 80;
	
	static {
		instance = new RTCBuildUtils();
	}
	
	
	/**
	 * Private constructor
	 */
	private RTCBuildUtils() {
		
	}
	
	public static RTCBuildUtils getInstance() {
		return instance;
	}
	
	/**
	 * Provide information about a build definition instance associated with a build result
	 * Currently, only information about post build deliver trigger policy is added to the map 
	 * 
	 * The value to the map is kept as an Object to allow for nested maps.
	 * 
	 * @param buildResultUUID - The id of the build definition 
	 * @param teamRepository - The repository in which build definition resides
	 * @param listener - To log messages
	 * @param clientLocale - The locale in which messages have to be logged
	 * @param progress - Non null if progress monitoring is required
	 * @return - A Map from String to Objects. These objects could be maps themselves.
	 * @throws TeamRepositoryException - if anything goes wrong
	 */
	public Map<String, Object> getBuildDefinitionInfoFromBuildResult(String buildResultItemId, ITeamRepository teamRepository,
				IConsoleOutput listener, Locale clientLocale, IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 10);
		List<IBuildRequestHandle> buildRequestHandles = new ArrayList<IBuildRequestHandle>();
		{
			IBuildResultHandle buildResultItemHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			IBuildResult buildResult = (IBuildResult) teamRepository.itemManager().fetchPartialItem(buildResultItemHandle, 
					ItemManager.REFRESH, Arrays.asList(new String[] {IBuildResult.PROPERTY_BUILD_REQUESTS}), monitor.newChild(2));
			// Get the build request from the result
			buildRequestHandles = (List<IBuildRequestHandle>) buildResult.getBuildRequests();
		}

        if (buildRequestHandles.isEmpty()) {
        	throw new IllegalStateException(Messages.getDefault().RTCBuildUtils_unexpected_zero_requests());
        }
		
		// Fetch the build request  and get the details from the build definition instance
		IBuildRequest buildRequest = (IBuildRequest) teamRepository.itemManager().fetchCompleteItem(
								(IBuildRequestHandle) buildRequestHandles.iterator().next(),
								IItemManager.REFRESH, monitor.newChild(2));
		IBuildDefinitionInstance buildDefinitionInstance = buildRequest.getBuildDefinitionInstance();
		
		HashMap<String, String> pbDeliverConfigInfo = new HashMap<String, String>();

		// Fill up some information required for post build deliver.
		// Check whether post build deliver configuration element is present
		IBuildConfigurationElement pbDeliverConfigElement = buildDefinitionInstance.
							getConfigurationElement(IAutoDeliverConfigurationElement.ELEMENT_ID);
		if (pbDeliverConfigElement != null) {
			pbDeliverConfigInfo.put(Constants.PB_CONFIGURED_KEY, Constants.TRUE);
			pbDeliverConfigInfo.put(Constants.PB_ENABLED_KEY, Constants.FALSE);
			// Check whether PB deliver is enabled.
			IBuildProperty pbDeliverEnabled = buildDefinitionInstance.getProperty(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ENABLED);
			if (pbDeliverEnabled != null && pbDeliverEnabled.getValue() !=  null) {
				pbDeliverConfigInfo.put(Constants.PB_ENABLED_KEY, pbDeliverEnabled.getValue());
			}
			IBuildProperty triggerPolicy = buildDefinitionInstance.getProperty(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY);
			if (triggerPolicy != null) {
				// Check whether the trigger policy is one among the enum
				try {
					AutoDeliverTriggerPolicy.valueOf(triggerPolicy.getValue());
					pbDeliverConfigInfo.put(Constants.PB_TRIGGER_POLICY_KEY, triggerPolicy.getValue()); 
				} catch (IllegalArgumentException exp) {
					pbDeliverConfigInfo.put(Constants.PB_TRIGGER_POLICY_KEY, 
								Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE); // This is a flag to tell the client that we don't know what the trigger policy is
				}
			} else {
				pbDeliverConfigInfo.put(Constants.PB_TRIGGER_POLICY_KEY, 
						Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE); // This is a flag to tell the client that we don't know what the trigger policy is
			}
		} else {
			pbDeliverConfigInfo.put(Constants.PB_CONFIGURED_KEY, Constants.FALSE);
		}
		
		HashMap<String, String> genericConfigInfo = new HashMap<String, String>();
		genericConfigInfo.put(Constants.GENERIC_BUILD_DEFINITION_ID_KEY, buildDefinitionInstance.getBuildDefinitionId());
		
		HashMap<String, Object> buildDefinitionInfo = new HashMap<String, Object>();
		buildDefinitionInfo.put(Constants.PB_INFO_ID, pbDeliverConfigInfo);
		buildDefinitionInfo.put(Constants.GENERIC_INFO_ID, genericConfigInfo);
		return buildDefinitionInfo;
	}
	
	
	/**
	 * Request a build for the given build definition ID.
     * The following exceptional scenarios are possible.
     * 1. Build definition does not exist
     * 2. No permissions to request build
	 * 3. No supporting engines
	 * 4. All supporting engines have been deactivated.
	 * 
	 * @param buildDefinitionId The build definition ID. Cannot be <code>null</code>.
	 * @param propertiesToDelete		The list of properties to ignore when requesting the build.
	 * @param propertiesToAddOrOverride The list of properties to add or override when requesting the build.
	 * @param teamRepository    The repository that contains the build definition. It is 
	 * 							assumed that the repository is already logged into.
	 * @param listener			The stream that accepts messages to output to the user
	 * @param clientLocale		The locale in which the messages should be formatted.
	 * @param progress			A progress monitor
	 * @return					A map containing the build result's UUID. The key to get the 
	 * 							build result UUID is 'buildResultUUID'.
	 * @throws TeamRepositoryException If something went wrong while requesting the build.
	 * 								   Some known failure scenarios are noted above.
	 */
	public Map<String, String> requestBuild(String buildDefinitionId, String[] propertiesToDelete, 
				Map<String, String> propertiesToAddOrOverride, ITeamRepository teamRepository,
				IConsoleOutput listener, Locale clientLocale, 
				IProgressMonitor progress) throws TeamRepositoryException, RTCConfigurationException {
		LOGGER.entering(this.getClass().getName(), "requestBuild");
		SubMonitor monitor = SubMonitor.convert(progress, 10);
		IBuildRequestParams buildRequestParms = BuildItemFactory.createBuildRequestParams();
		try {
			buildDefinitionId = Utils.fixEmptyAndTrim(buildDefinitionId);
			if (buildDefinitionId == null) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_build_definition_id_is_null());
			}
			ITeamBuildClient buildClient = (ITeamBuildClient) teamRepository.getClientLibrary(ITeamBuildClient.class);
			IBuildDefinition buildDefinition = buildClient.getBuildDefinition(buildDefinitionId, monitor.newChild(4));
			// If build definition is null, throw an error.
			if (buildDefinition == null) {
				throw new RTCConfigurationException(Messages.getDefault().
									RTCBuildUtils_build_definition_not_found(buildDefinitionId));
			}

			if (propertiesToDelete != null && propertiesToDelete.length > 0) {
				List<IBuildProperty> buildPropertiesToDelete = new ArrayList<IBuildProperty>(propertiesToDelete.length);
				LOGGER.fine("Properties to delete:");
				for (String propertyName : propertiesToDelete) {
					LOGGER.fine(propertyName);
					IBuildProperty buildProperty = BuildItemFactory.createBuildProperty();
					// we do not expect empty or null property names at this point
					buildProperty.setName(propertyName);
					buildPropertiesToDelete.add(buildProperty);
				}
				buildRequestParms.getDeletedBuildProperties().addAll(buildPropertiesToDelete);
			}
			if (propertiesToAddOrOverride != null && !propertiesToAddOrOverride.isEmpty()) {
				List<IBuildProperty> buildPropertiesToAddOrOverride = new ArrayList<IBuildProperty>(propertiesToAddOrOverride.size());
				LOGGER.fine("Properties to add or override:");
				for (String propertyName : propertiesToAddOrOverride.keySet()) {
					String propertyValue = propertiesToAddOrOverride.get(propertyName);
					LOGGER.fine("Name: " + propertyName + " Value: " + propertyValue);
					IBuildProperty buildProperty = BuildItemFactory.createBuildProperty();
					// we do not expect empty or null property name at this point
					buildProperty.setName(propertyName);
					buildProperty.setValue(propertyValue);
					buildPropertiesToAddOrOverride.add(buildProperty);
				}
				buildRequestParms.getNewOrModifiedBuildProperties().addAll(buildPropertiesToAddOrOverride);
			}

			ITeamBuildRequestClient requestClient = (ITeamBuildRequestClient)teamRepository.getClientLibrary(ITeamBuildRequestClient.class);
			buildRequestParms.setBuildDefinition(buildDefinition);
			buildRequestParms.setAllowDuplicateRequests(true);

			IBuildRequest request = requestClient.requestBuild(buildRequestParms, monitor.newChild(6));
			Map<String, String> ret = new HashMap<>();
			if (request != null) {
				ret.put(Constants.RTCBuildUtils_BUILD_RESULT_UUID, 
						request.getBuildResult().getItemId().getUuidValue());
			} else {
				LOGGER.fine(NO_BUILD_REQUEST_LOG_MSG);
				ret.put(Constants.RTCBuildUtils_BUILD_RESULT_UUID, "");
			}
			return ret;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Returns a {@link IBuildDefinition} for the given <code>buildDefinitionId</code>
	 * 
	 * @param buildDefinitionId          The ID of the build definition. Never <code>null</code> 
	 * @param teamRepository             The repository that contains the build definition. It is 
	 * 							         assumed that the repository is already logged into.
	 * @param listener                   A stream to output messages to. These messages will be output 
	 *                                   to the user.
	 * @param clientLocale               Locale in which messages should be formatted
	 * @param progress                   A progress monitor.
	 * @return                           A {@link IBuildDefinition}
	 * @throws TeamRepositoryException   If there is an issue while communicating with the EWM server.
	 * @throws RTCConfigurationException If build definition is not found.
	 */
	public IBuildDefinition getBuildDefinition(String buildDefinitionId, 
			ITeamRepository teamRepository,
			IConsoleOutput listener, Locale clientLocale, 
			IProgressMonitor progress) throws TeamRepositoryException, RTCConfigurationException {
		LOGGER.entering(this.getClass().getName(), "getBuildDefinition");
		SubMonitor monitor = SubMonitor.convert(progress, 10);
		try {
			buildDefinitionId = Utils.fixEmptyAndTrim(buildDefinitionId);
			if (buildDefinitionId == null) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_build_definition_id_is_null());
			}
			
			ITeamBuildClient buildClient = (ITeamBuildClient) teamRepository.getClientLibrary(ITeamBuildClient.class);
			IBuildDefinition buildDefinition = buildClient.getBuildDefinition(buildDefinitionId, monitor.newChild(4));
			// If build definition is null, throw an error.
			if (buildDefinition == null) {
				throw new RTCConfigurationException(Messages.getDefault().
									RTCBuildUtils_build_definition_not_found(buildDefinitionId));
			}
			return buildDefinition;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Wait for the build result to reach one of the states mentioned in <code>buildStatesToWaitFor</code> 
	 * for a specific timeout. The method returns if the build result reaches if one of the states 
	 * or the timeout expires.
	 * 
	 * @param buildResultUUID          The build result to wait on
	 * @param buildStatesToWaitFor     The states to wait on. If the build result reaches 
	 *                                 one of the states, then the method will return 
	 *                                 Each entry of the array should be one of {@link BuildState}.
	 * @param waitBuildTimeoutSeconds  Number of seconds to wait for. Can be <code>-1</code> or 
	 *                                 any value greater than <code>0</code>
  	 * @param waitBuildIntervalSeconds Number of seconds between each check made by this method to EWM 
	 *                                 server. The value should be greater than 1 and less than waitBuildTimeout,
	 *                                 if waitBuildTimeout is not -1. If waitBuildTimeout is -1, this number 
	 *                                 can be any positive integer greater than 1.
	 * @param teamRepository           An instance of {@link ITeamRepository}
	 * @param listener                 A stream to output messages to. These messages will be output 
	 *                                 to the user.
	 * @param clientLocale             Locale in which messages should be formatted
	 * @param progress                 A progress monitor
	 * @return                         A map that has the return values mentioned in the beginning
	 * @throws TeamRepositoryException
	 * @throws RTCConfigurationException
	 */
	public Map<String, String> waitForBuild(String buildResultUUID , String [] buildStatesToWaitFor,
			long waitBuildTimeoutSeconds, long waitBuildIntervalSeconds, ITeamRepository teamRepository,
			IConsoleOutput listener, Locale clientLocale, 
			IProgressMonitor progress) throws TeamRepositoryException, RTCConfigurationException {
		LOGGER.entering(this.getClass().getName(), "waitForBuild");
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			buildResultUUID = Utils.fixEmptyAndTrim(buildResultUUID);
			validateBuildResultUUIDParam(buildResultUUID);	
			
			if (buildStatesToWaitFor == null || buildStatesToWaitFor.length == 0) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_build_states_array_empty());
			}
			
			if (waitBuildTimeoutSeconds == 0 || waitBuildTimeoutSeconds < -1) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_build_wait_timeout_invalid(
								Long.toString(waitBuildTimeoutSeconds)));
			}

			if (waitBuildTimeoutSeconds == -1) {
				// set waitBuildTimeoutSeconds to be max value
				waitBuildTimeoutSeconds = BUILD_WAIT_TIMEOUT_MAX;
			} 
			
			// Validate whether waitBuildInterval is less than waitBuildTimeoutSeconds
			if (waitBuildIntervalSeconds <= 0) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_build_wait_interval_invalid(
								Long.toString(waitBuildIntervalSeconds)));
			}
			
			// If waitBuildInterval is greater than waitBuildTimeoutSeconds, then 
			// it is an error
			if (waitBuildIntervalSeconds > waitBuildTimeoutSeconds) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_build_wait_interval_cannot_be_greater(
								Long.toString(waitBuildIntervalSeconds), 
								Long.toString(waitBuildTimeoutSeconds)));
			}
			
			// Convert build states and validate them
			BuildState[] buildStates = convertBuildStates(buildStatesToWaitFor); 
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) 
						IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
			
			// Get build result
			IBuildResult result = waitBuildHelper(buildResultHandle, buildStates, waitBuildTimeoutSeconds, waitBuildIntervalSeconds, 
					teamRepository, listener, monitor.newChild(100));			
			return composeReturnPropertiesForWaitBuild(result, buildStates);
		} finally {
			monitor.done();
		}
	}
	
	/**
	 * 
	 * Retrieve the snapshot details for the given build result.
	 * If the build contains a snapshot contribution and the snapshot exists 
	 * in the repository, then two keys "snapshotUUID" and "snapshotName" will 
	 * be present in the map with the values being the UUID and name of the 
	 * snapshot respectively.
	 * 
	 * If the snapshot contribution is not found or if the snapshot contribution is 
	 * found but has been deleted from the repository, then the map will still have 
	 * the two keys but with empty strings as values.
	 * 
	 * @param buildResultUUID    - The UUID for the build result. 
	 * 							   Cannot be <code>null>.
	 * @param teamRepository       An instance of {@link ITeamRepository}
	 * @param listener             A stream to output messages to. These messages will be output 
	 *                             to the user.
	 * @param clientLocale         Locale in which messages should be formatted
	 * @param progressMonitor      A progress monitor
	 * @return                     A map that contains two keys "snapshotUUID" and "snapshotName".
	 *                             If a snapshot contribution is found in the build result and 
	 *                             the snapshot does exist in the repository, the UUID and the name 
	 *                             of the snapshot will be the values of the two keys.
	 *                             If the snapshot contribution is not found or if the snapshot has been 
	 *                             deleted from the repository, empty strings will put as the values of 
	 *                             the two keys.  
	 *                             
	 * Special handling for {@link ItemNotFoundException}. If the snapshot is not found, then the keys will 
	 * be inserted into the map with empty values.
	 * 
	 * @throws RTCConfigurationException If the build result is not found
	 * @throws TeamRepositoryException   If there is a communication issue or some other problem when 
	 *                                   communicating with the EWM server. 
	 *   
	 */
	public Map<String, String> retrieveSnapshotFromBuild(String buildResultUUID, ITeamRepository teamRepository,
			IConsoleOutput listener, Locale clientLocale, IProgressMonitor progress) 
				throws TeamRepositoryException, RTCConfigurationException {
		LOGGER.entering(this.getClass().getName(), "retrieveSnapshotForBuild");
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			buildResultUUID = Utils.fixEmptyAndTrim(buildResultUUID);
			validateBuildResultUUIDParam(buildResultUUID);
			
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) 
					IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null);
			IBuildResult result = getBuildResultWithValidation(buildResultHandle, null, 
					teamRepository, monitor.newChild(20));
			
			Map<String, String> properties = new HashMap<String, String>();
			properties.put(Constants.RTCBuildUtils_SNAPSHOT_NAME_KEY, "");
			properties.put(Constants.RTCBuildUtils_SNAPSHOT_UUID_KEY, "");
			ITeamBuildClient buildClient = (ITeamBuildClient) teamRepository.
					getClientLibrary(ITeamBuildClient.class);
			IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(result, 
											ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_SNAPSHOT,
											monitor.newChild(20));
			if (contributions != null && contributions.length > 0) {
				// Usually there is only one contribution
				// Regardless, let us pick the first one.
				IBuildResultContribution contribution = contributions[0];	
				IItemHandle snapshotHandle = contribution.getExtendedContribution();
				
				try {
					IBaselineSet snapshot = RTCSnapshotUtils.getSnapshotByUUID(teamRepository,
						 snapshotHandle.getItemId().getUuidValue(), monitor.newChild(10), clientLocale);
					properties.put(Constants.RTCBuildUtils_SNAPSHOT_NAME_KEY, snapshot.getName());
					properties.put(Constants.RTCBuildUtils_SNAPSHOT_UUID_KEY, snapshot.getItemHandle().getItemId().getUuidValue());
				} catch (ItemNotFoundException exp) {
					// Log the exception.
					LOGGER.info(String.format("EWM snapshot with UUID '%s' was not found. It may have been deleted. "
							+ "Error: %s", snapshotHandle.getItemId().getUuidValue(), exp.getMessage()));
				}
			}
			return properties;
		} finally {
			monitor.done();
	 		LOGGER.exiting(this.getClass().getName(), "retrieveSnapshotFromBuild");
		}
	}
	
	/**
	 * Given <code>totalwbTimeoutSeconds</code> in seconds , try to wait till that time 
	 * limit in intervals of <code>waitBuildIntervalSeconds</code> seconds. 
	 * If totalwbTimeoutSeconds is less than <code>waitBuildIntervalSeconds</code> seconds 
	 * or if the remaining time (totalwbTimeoutSeconds - (n*<code>waitBuildIntervalSeconds</code>)), 
	 * where n is the number of  iterations spent waiting, is less than 30 seconds, 
	 * then wait till those many seconds. 
	 * 
	 * In between this waiting, if the build result state is in one of the desired states, 
	 * then the loop will break and the method will return the latest build result state.
	 * 
	 * @param buildResultHandle               The build result to wait on
	 * @param desiredBuildStates              The desired build states to reach 
	 * @param totalwbTimeoutSeconds           Total number of seconds to wait.
	 * @param waitBuildIntervalSeconds               The number of seconds to wait in between checks 
	 *                                        to the server 
	 * @param teamRepository                  Connection to the EWM server.
	 * @param listener                        Listener for accepting messages
	 * @param progress                        A progress monitor
	 * @return                                The last obtained version of the build result at the time 
	 *                                        the loop exited. 
	 * @throws RTCConfigurationException      If build result is not found 
	 * @throws TeamRepositoryException        If there is an issue communicating with the server. Includes 
	 * 									      many other exceptional scenarios that might occur at the server 
	 *                                        side. 
	 */
	private IBuildResult waitBuildHelper(IBuildResultHandle buildResultHandle, BuildState[] desiredBuildStates, 
			long totalwbTimeoutSeconds, long waitBuildIntervalSeconds, ITeamRepository teamRepository, 
			IConsoleOutput listener, IProgressMonitor progress) throws
			RTCConfigurationException, TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			IBuildResult result = getBuildResultWithValidation(buildResultHandle, null, 
								teamRepository, monitor.newChild(20));
	
			// Variables for time management
			long timeRemaining = totalwbTimeoutSeconds;
			long currentDecrement = (totalwbTimeoutSeconds < waitBuildIntervalSeconds)? 
							totalwbTimeoutSeconds : waitBuildIntervalSeconds;
	
			while(!isInState(result, desiredBuildStates) && !(timeRemaining <= 0)) {
				listener.log(String.format("waitForBuild: Sleeping for %d seconds", currentDecrement));
				boolean sleepBroken = sleep(1000 * currentDecrement);

				// Get the latest result after sleeping.
				result = getBuildResult(buildResultHandle, teamRepository, monitor.newChild(5));
				if (sleepBroken) {
					// Sleep was broken, break out of this loop
					// At this point, build result has the latest item state 
					// from the database.
					break;
				}
	
				// Set the remaining time
				timeRemaining = timeRemaining - currentDecrement;
				// Set the currentDecrement properly based on 
				// timeRemaining
				if (timeRemaining <= 0) {
					// Just making sure that we don't keep decreasing
					// timeRemaining beyond 0 
					currentDecrement = 0;  
				} else {
					currentDecrement = (timeRemaining < waitBuildIntervalSeconds)? 
						timeRemaining : waitBuildIntervalSeconds;
				}
			}
			return result;
		} finally {
			monitor.done();
		}
	}

	/**
	 * 
	 * List information about logs or artifacts (henceforth known as files) 
	 * from a given build result. The behavior to list logs or artifacts is controlled by 
	 * <code>fileType</code> argument.
	 * 
	 * Logs and artifacts can be either directly uploaded to the repository or 
	 * point to a file in an external repository.  
	 * 
	 * This is differentiated by two attributes on {@link IBuildResultContribution} 
	 * {@link IBuildResultContribution#PROPERTY_NAME_FILE_NAME} 
	 * {@link IBuildResultContribution#PROPERTY_NAME_URL}
	 * 
	 * For logs/artifacts available in the repository, the first property will be 
	 * non empty and {@link IBuildResultContribution#getExtendedContributionData()) 
	 * will point to a {@link IIContent}. 
	 * 
	 * If both fileNameOrPattern and <code>componentName</code> is empty,
	 * then up to 2048 logs or artifacts will be listed.
	 * 
	 * If <code>fileNameOrPattern</code> is non empty, then the value of 
	 * {@link IBuildResultContribution#PROPERTY_NAME_FILE_NAME} will be  
	 * pattern matched and only those that match will be considered.
	 * 
	 * If <code>componentName</code> is non empty, then the value of 
	 * {@link IBuildResultContribution#getComponentName()} will be matched  
	 * exactly and only those contributions that match will be considered.
	 * 
	 * If both are non empty, then both matches should be <code>true<code>. If 
	 * one or both of the above is empty, then the match is considered true and 
	 * the contribution will be included in the filtered list.
	 * 
	 * Up to <code>maxResults</code> contributions will be added to the filtered 
	 * contributions list. 
	 *   
	 * @param buildResultUUID    The UUID of the build result. Cannot be 
	 *                           <code>null</code>
	 * @param fileNameOrPattern  The file name to match, can be a pattern. 
     *                           Can be <code>null</code>.
	 * @param componentName      The name of the component that the contribution 
	 *                           should belong to. Can be <code>null</code>.
	 * @param fileType           The type of the contribution. Valid values are 
	 *                           <code>log</code> and <code>artifact</code>.
	 * @param maxResults         The maximum number of results to be provided in the 
	 *                           return value. Should be less than 
	 *                           {@link Constants#LIST_FILES_MAX_RESULTS}
	 * @param teamRepository     An instance of {@link ITeamRepository}. Ensure that the server 
	 *                           is logged into before calling this API.
	 * @param consoleOutput      An output stream to send messages into. This will be 
	 *                           in the build log.
	 * @param clientLocale       The locale in which user visible messages should be output
	 * @param progress           A progress monitor
	 * @return                   A map in the following format
	 *                           key - {@link Constants#RTCBuildUtils_FILEINFOS_KEY}
	 *                           value - An {@link List} of {@link List<String>} 
	 *                            Each inner list contains the following fields
	 *                            1. file name
	 *                            2. component name
	 *                            3. description
	 *                            4. contribution type 
	 *                            5. content UUID
	 *                            6. file size in bytes
	 * @throws TeamRepositoryException   If there is an issue in service calls to the EWM server
	 * @throws RTCConfigurationException If any of the input fails validation.
	 */
	public Map<String, Object> listFiles(String buildResultUUID, String fileNameOrPattern,
						String componentName, String contributionType, int maxResults, 
						ITeamRepository teamRepository, IConsoleOutput listener, 
						Locale clientLocale, IProgressMonitor progress) throws TeamRepositoryException, 
								RTCConfigurationException {
		LOGGER.entering(this.getClass().getName(), "listFiles");
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			// Validate buildResultUUID != null
			buildResultUUID = Utils.fixEmptyAndTrim(buildResultUUID);
			validateBuildResultUUIDParam(buildResultUUID);
			
			// Validate proper regex for fileNameOrPattern
			fileNameOrPattern = Utils.fixEmptyAndTrim(fileNameOrPattern);
			if (fileNameOrPattern != null) {
				try {
					@SuppressWarnings("unused")
					Pattern p = Pattern.compile(fileNameOrPattern);
				} catch (PatternSyntaxException exp) {
						throw new RTCConfigurationException(
							 Messages.getDefault().
							 RTCBuildUtils_fileNamePattern_is_invalid(fileNameOrPattern,
							 exp.getMessage()));
				}
			}
				
			// validate contributionType
			contributionType = Utils.fixEmptyAndTrim(contributionType);
			validateContributionTypeParam(contributionType);
			
			// validate maxResults > 0 and less than 2048
			if (maxResults <= 0 || maxResults > LIST_FILES_MAX_RESULTS) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_maxResults_is_invalid(
								maxResults, LIST_FILES_MAX_RESULTS));				
			}
			
			ITeamBuildClient buildClient = (ITeamBuildClient) teamRepository.
												getClientLibrary(ITeamBuildClient.class);
			IBuildResultHandle brHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.
								createItemHandle(UUID.valueOf(buildResultUUID), null);
			
			// Validate the build result first and then fetch the contributions
			// Although this looks wasteful, we will get a better exception message
			// if the build result is not found.
			IBuildResult buildResult = getBuildResultWithValidation(brHandle,
						new String[] {IBuildResult.PROPERTY_LABEL},
						teamRepository, monitor.newChild(1));
			IBuildResultContribution[] contributions = getBuildResultContributions(
								buildClient, (IBuildResultHandle) buildResult.getItemHandle(), 
								contributionType, monitor.newChild(19));

			List<IBuildResultContribution> filteredContributions = new ArrayList<>();
			for (IBuildResultContribution contribution : contributions) {
				// If the number of filtered contributions is greater than maxResults,
				// quit the loop. 
				if (filteredContributions.size() >= maxResults) {
					break;
				}
				
				// Once the contributions are fetched, filter out the ones that have 
				// PROPERTY_FILE_NAME set and extenedContributionData not null 
				String contributionFileName = contribution.getExtendedContributionProperty(
											IBuildResultContribution.PROPERTY_NAME_FILE_NAME);

				if (contributionFileName == null || 
							contribution.getExtendedContributionData() == null) {
					continue;
				}
				
				// Within those, filter out the ones whose fileNameOrPattern 
				// and/or componentName matches, if each of them is non empty.
				boolean componentNameMatch = true;
				if (componentName != null && 
							!(componentName.equals(contribution.getComponentName()))) {
					LOGGER.finest(String.format("Contribution's component name %s did not match %s", //
							(contribution.getComponentName() == null)? "null":
								contribution.getComponentName(), componentName));
					componentNameMatch = false;
				}
				
				boolean fileNameOrPatternMatch = true;
				if (fileNameOrPattern != null && !contributionFileName.matches(fileNameOrPattern)) {
					LOGGER.finest(String.format("Contribution's file name %s did not match %s", 
												contributionFileName, fileNameOrPattern));
					fileNameOrPatternMatch = false;
				}
				
				if (componentNameMatch == true && fileNameOrPatternMatch == true) {
					filteredContributions.add(contribution);		
					LOGGER.finest(String.format("Filtered contribution with file name %s",
										contributionFileName));
				}
			}
			monitor.worked(40);

			List<List<String>> filteredFiles =new ArrayList<List<String>>();
			for (IBuildResultContribution contribution : filteredContributions) {
				ArrayList<String> file = new ArrayList<String>();
				file.add(contribution.getExtendedContributionProperty(
										IBuildResultContribution.PROPERTY_NAME_FILE_NAME));
				file.add(contribution.getComponentName() == null ? "" : 
								contribution.getComponentName());
				// Limit label to 80 characters
				file.add(getTruncatedLabelForListFiles(contribution));
				// Get the extended contribution type ID
				file.add(getContributionTypeFromExtendedContributionTypeId(
											contribution.getExtendedContributionTypeId()));
				// Get the content UUID from the contribution
				file.add(contribution.getExtendedContributionData().getContentId().getUuidValue());
				file.add(Long.toString(contribution.getExtendedContributionData().getRawLength()));
				
				// Add the internal id for future use
				file.add(getInternalIdFromBuildResultContribution(contribution));
				filteredFiles.add(file);
			}
			monitor.worked(40);
			Map<String, Object> properties = new HashMap<>();
			properties.put(Constants.RTCBuildUtils_FILEINFOS_KEY, filteredFiles);
			return properties;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Download the file  (log or artifact) that is part of the build result 
	 * from the content repository. You can provide either filename + 
	 * component name or a component UUID for a more accurate match, especially if 
	 * you have multiple contributions have the same file name.
	 * 
	 * Only logs/artifacts that have their content in the EWM repository are considered,
	 * as opposed to those which have their content in an external repository.
	 * 
	 * If <code>contentId</code> is not found in any the log/artifact contributions, 
	 * an exception is thrown.
	 * 
	 * If the <code>fileName</code> does not match file names of any of the 
	 * contributions, then an exception is thrown. 
	 *    
	 * @param buildResultUUID            The UUID of the build result. Cannot be
	 *                                   <code>null</code>.
	 * @param fileName                   The file name to match, can be a pattern. 
     *                                   Can be <code>null</code> if <code>contentId</code> 
     *                                   is not <code>null</code> or empty.
	 * @param componentName              The name of the component that the contribution 
	 *                                   should belong to. Can be <code>null</code>.
	 * @param contentId                  UUID of content blob. This value is usually obtained 
	 *                                   from {@link RepositoryConnection#listFiles}. Can be 
	 *                                   <code>null</code> only if <code>fileName</code> is 
	 *                                   not null or empty.
	 * @param fileType                   The type of the contribution. Valid values are 
	 *                                   <code>log</code> and <code>artifact</code>.
	 * @param destinationFolder          The folder in which the file should be downloaded to.
	 * @param destinationFileName        The name of the file destination file. Can be 
	 *                                   <code>null</code>. If null, then the file name from the 
	 *                                   contribution is used as the destination file name.
	 *                                   If the file with that name exists, then a new file name is 
	 *                                   used filename-suffix.filetype. The suffix is a timestamp in 
	 *                                   the following pattern  yyyyMMdd-HHmmss-SSS.
	 * @param teamRepository             An instance of {@link ITeamRepository}. Ensure that the server 
	 *                                   is logged into before calling this API.
	 * @param consoleOutput              An output stream to send messages into. This will be 
	 *                                   in the build log.
	 * @param clientLocale               The locale in which user visible messages should be output
	 * @param progress                   A progress monitor 
	 * @return                           A map in the following format
	 *                                   key 1 - {@link Constants#RTCBuildUtils_FILENAME_KEY}
	 *                                   value - The name of the destination file. It may nor may not be the same 
	 *                                           as the destinationFileName parameter passed into this method.
	 *                                   key 2 - {@link Constants#RTCBuildUtils_FILEPATH_KEY}
	 *                                   value - The path of the destination file.
	 * @throws TeamRepositoryException   If there is an issue in service calls to the EWM server
	 * @throws IOException               If there is an issue with creating/writing to the file. Issues 
	 *             				         like lack of permissions, disk out of space, invalid characters in the 
	 *                                   destination file name.
	 * @throws RTCConfigurationException If any of the input fails validation.
	 */
	public Map<String, String> downloadFile(String buildResultUUID, String fileName, 
			String componentName, String contentId, String fileType, String destinationFolder, 
			String destinationFileName, ITeamRepository teamRepository, IConsoleOutput consoleOutput,
			Locale clientLocale, IProgressMonitor progress) 
					throws TeamRepositoryException, RTCConfigurationException, IOException {

		LOGGER.entering(this.getClass().getName(), "downloadFile");
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			// Perform validations 
			// buildResultUUID is empty
			buildResultUUID = Utils.fixEmptyAndTrim(buildResultUUID);
			validateBuildResultUUIDParam(buildResultUUID);

			// fileName and contentId cannot be null
			// fileName and contentId both cannot be non null
			fileName= Utils.fixEmptyAndTrim(fileName);
			contentId = Utils.fixEmptyAndTrim(contentId);
			validateFileNameAndContentIdParms(fileName, contentId);
			
			componentName = Utils.fixEmptyAndTrim(componentName);
			// destinationFolder cannot be null
			if (destinationFolder == null || destinationFolder.isEmpty()) {
				throw new RTCConfigurationException(
						Messages.getDefault().RTCBuildUtils_destination_folder_null());
			}

			// destination file name is not a valid file name, has relative path characters
			// If destinationFileName is null, we will attempt to use the file name from the contribution
			// It will be validated for a valid relative leaf name at that point
			if (destinationFileName != null) {
				validateRelativeLeafName(destinationFileName);
			}
			
			ITeamBuildClient buildClient = (ITeamBuildClient) 
							teamRepository.getClientLibrary(ITeamBuildClient.class);
			// Get build result with validation
			IBuildResult buildResult = getBuildResultWithValidation((IBuildResultHandle) 
						IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultUUID), null),
						new String[] {IBuildResult.PROPERTY_LABEL},
						teamRepository, monitor.newChild(1));
			IBuildResultContribution filteredContribution = null;
			
			// If fileNameOrPattern is not null, filter through 
			// all contributions based on file name
			if (fileName != null) {
				filteredContribution = getContributionFromFileName(buildClient, buildResult, 
								fileName, componentName, fileType, monitor.newChild(39));
			}
			
			// If contentId is not null, filter through all 
			// contributions based on content Id.
			if (contentId != null) {
				filteredContribution = getContributionFromContentId(buildClient, buildResult, 
								contentId, fileType, monitor.newChild(40));
			}
			
			// If we don't have a contribution, throw an exception.
			if (filteredContribution == null) {
				String message = getMessageForContributionNotFound(
						fileType, fileName, componentName, contentId);
				throw new RTCConfigurationException(message);
			}
			
			// At the end of this, we have a single contribution with 
			// extended contribution data
			LOGGER.finest(String.format("Filtered contribution with filename %s", 
					filteredContribution.getExtendedContributionProperty(
							IBuildResultContribution.PROPERTY_NAME_FILE_NAME)));
			
			// Set a default destination file name 
			if (destinationFileName == null) {
				destinationFileName = filteredContribution.getExtendedContributionProperty(
							IBuildResultContribution.PROPERTY_NAME_FILE_NAME);
			}
			
			// Make sure that the destinationFolder with destinationFileName 
			// is still a valid file name, especially since that name is user provided
			validateRelativeLeafName(destinationFileName);

			// Check if the file name - with the download folder path already exists 
			// If yes, then choose another name with the current time stamp, 
			// do this for a few times before giving up.		
			// Once a path is decided, create a temp file and see if it is OK. 
			// If not, then throw an exception that we could find a proper file name to use.
			String fixedDestinationFileName = fixDestinationFileName(destinationFolder, destinationFileName);
			if (fixedDestinationFileName == null) {
				throw new IOException(Messages.getDefault().
						RTCBuildUtils_unique_destinationFileName_not_found(
								destinationFileName, DESTINATION_FILE_NAME_CHOOSER_MAX_ATTEMPTS));
			}
			
			LOGGER.finest(String.format("Fixed destination file name is %s", fixedDestinationFileName));
			
			// At this point, we have created the file and reserved it for our own use.
			// If some other program is using the file in between, either 
			// our attempt to overwrite the file could result in an error 
			// or the file will be left in an inconsistent state.
			// The caller should not use the file if an exception is thrown from this 
			// method. This should be made clear to the clients of this method.
			File destinationFile = new File(destinationFolder, fixedDestinationFileName);
			
			// Perform the download, if possible, catch some known exceptions 
			// and provide a proper error message to the client.
			downloadContent(teamRepository, filteredContribution.getExtendedContributionData(), 
						destinationFile.getCanonicalPath(), monitor.newChild(30));
			
			HashMap<String, String> buildProperties = new HashMap<>();
			buildProperties.put(Constants.RTCBuildUtils_FILENAME_KEY, destinationFile.getName());
			buildProperties.put(Constants.RTCBuildUtils_FILEPATH_KEY, destinationFile.getCanonicalPath());
			buildProperties.put(Constants.RTCBuildUtils_INTERNAL_UUID_KEY, 
					getInternalIdFromBuildResultContribution(filteredContribution));
			return buildProperties;
		} finally {
			monitor.done();
		}
	}
	

	/**
	 * Download content from the repository to the given file
	 *  
	 * @param repository               The repository to download the content from. Never <code>null</code>.
	 * @param content                  The content object. Never <code>null</code>
	 * @param destinationPath          Canonical path to the destination file. Never <code>null</code>
	 * @param progress                 Progress monitor.
	 * @return                         A {@link File} object to the destination file path
	 * @throws TeamRepositoryException If something goes wrong when contacting the repository.
	 */
	@SuppressWarnings("restriction")
	private static File downloadContent(ITeamRepository repository, IContent content,  
				String destinationPath,	IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			return ContentUtil.contentToFile(repository, content,
				monitor.newChild(30), destinationPath);
		} finally {
			monitor.done();
		}
	}

	/**
	 * Validate fileName and contentId parameters 
	 * 
	 * @param fileName                    The file name parameter
	 * @param contentId                   The content ID parameter
	 * @throws RTCConfigurationException  If the validation fails, this exception 
	 *                                     is thrown.
	 */
	private void validateFileNameAndContentIdParms(String fileName, String contentId) 
									throws RTCConfigurationException {
		if (fileName == null && contentId == null) {
			throw new RTCConfigurationException(
					Messages.getDefault().RTCBuiltUtils_fileNameContentId_null());
		}

		if (fileName != null && contentId != null) {
			throw new RTCConfigurationException(
					Messages.getDefault().RTCBuiltUtils_fileNameContentId_nonnull());
		}
	}

	/**
	 * Validate the relative leaf name.
	 * 
	 * @param leafName      The leaf name to validate 
	 * @throws IOException  If the validation fails since the leaf name is relative.
	 */
	private void validateRelativeLeafName(String leafName) throws IOException {
		// Check for OS file separator and path separator characters.
		if (leafName.contains(File.separator) || 
				leafName.contains(File.pathSeparator)) {
			throw new IOException(
					Messages.getDefault().
					 RTCBuildUtils_destinationFileName_is_a_path(leafName));
		}
	}
	
	/**
	 * Given a folder and a file name, check if a file with the given file name can be 
	 * created in the folder. If the file cannot be created, an exception is thrown.
	 * 
	 * If the file already exists, then a different file name is chosen in the following way 
	 * take the existing prefix, add a time stamp and then a suffix (which is the file type), if 
	 * the contribution's file name or destination file name has one. 
	 * 
	 * 
	 * @param destinationFolder      The destination folder's full path. Usually a 
	 *                               canonical path is preferred.
	 * @param destinationFileName    The suggested destination file name. If the 
	 *                               path including the destination file name exists, 
	 *                               then a new name is chosen upto {@link #DESTINATION_FILE_NAME_CHOOSER_MAX_ATTEMPTS}
	 * @return                       A new destination file name (or same as <code>destinationFileName</code>)
	 * @throws IOException           If the file could not be created on disk due to a variety of reasons.
	 */
	private String fixDestinationFileName(String destinationFolder, 
							String destinationFileName) throws IOException {

		File destinationFile = new File(destinationFolder, destinationFileName);
		if (destinationFile.createNewFile()) {
			return destinationFileName;
		}

		// Continue attempting creating files with unique time stamp
		for (int i = 0; i < DESTINATION_FILE_NAME_CHOOSER_MAX_ATTEMPTS; i++) {
			destinationFileName = destinationFileName + 
						(new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")).
						format(new java.util.Date(System.currentTimeMillis()));
			destinationFile = new File(destinationFolder, destinationFileName);
			if (destinationFile.createNewFile()) {
				return destinationFileName;
			}
			// If we are here, then destination file exists.
			// Do a small sleep and continue
			sleep(100);
		}
		return destinationFileName;
	}

	/**
	 * @param buildClient                The build client for the EWM server.
	 * @param brHandle                   The handle to the build result.
	 * @param contentId                  The UUID of the content blob
	 * @param contributionType           The type of the contribution.
	 * @param listener                   Listener for the messages to be sent. 
	 * @param progress                   Progress monitor
	 * @return                           {@link IBuildResultContribution} if a matching 
	 *                                   contribution is found. Or <code>null</code> if 
	 *                                   no contribution is found.
	 * @throws TeamRepositoryException   If there is an issue when communicating with the server
	 * @throws RTCConfigurationException If one of the arguments is not valid.
	 */
	private IBuildResultContribution getContributionFromContentId(ITeamBuildClient buildClient, 
						IBuildResultHandle brHandle, String contentId, String contributionType,
						SubMonitor progress) throws TeamRepositoryException, RTCConfigurationException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			IBuildResultContribution[] contributions = getBuildResultContributions(buildClient, 
					brHandle, contributionType, monitor.newChild(40));
			IBuildResultContribution matchingContribution = null;
			for (IBuildResultContribution contribution : contributions) {
				String contributionContentID = contribution.getExtendedContributionData().
											getContentId().getUuidValue();
				LOGGER.finest(String.format("Looking for exact match of %s", contentId));
				if (contentId.equals(contributionContentID)) {
					matchingContribution = contribution;
					break;
				}
			}
			return matchingContribution;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Return the {@link IBuildResultContribution} from the build result based on the 
	 * <code>fileName</code> and <code>componentName</code> parameters. 
	 * 
	 * @param buildClient                The build client for the EWM server.
	 * @param brHandle                   The handle to the build result.
	 * @param fileName                   Name of the file to match with the contribution.
	 * @param componentName              Component name for the contribution.
	 * @param contributionType           The type of the contribution.
	 * @param listener                   Listener for the messages to be sent. 
	 * @param progress                   Progress monitor
	 * @return                           {@link IBuildResultContribution} if a matching 
	 *                                   contribution is found. Or <code>null</code> if 
	 *                                   no contribution is found.
	 * @throws TeamRepositoryException   If there is an issue when communicating with the server
	 * @throws RTCConfigurationException If one of the arguments is not valid.
	 */
	private IBuildResultContribution getContributionFromFileName(ITeamBuildClient buildClient,
							IBuildResultHandle brHandle, String fileName, String componentName, 
							String contributionType, SubMonitor progress) 
							throws TeamRepositoryException, RTCConfigurationException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			IBuildResultContribution[] contributions = getBuildResultContributions(buildClient, 
					brHandle, contributionType, monitor.newChild(40));
			IBuildResultContribution matchingContribution = null;
			// Once the contributions are fetched, filter out the ones that have 
			// PROPERTY_FILE_NAME set and extenedContributionData not null
			for (IBuildResultContribution contribution : contributions) {
				String contributionFileName = contribution.getExtendedContributionProperty(
											IBuildResultContribution.PROPERTY_NAME_FILE_NAME);
				LOGGER.finest(String.format("Looking for exact match of %s", fileName));
				boolean componentNameMatch = true;
				if (componentName != null && !(componentName.equals(contribution.getComponentName()))) {
					componentNameMatch = false;
				}
				if (contributionFileName != null && fileName.equals(contributionFileName)
						 && componentNameMatch) {
					matchingContribution = contribution;
					break;
				}
			}
			return matchingContribution;
		} finally {
			monitor.done();
		}
	}

	/**
	 * Get all file (log/artifact) related contributions 
	 * 
	 * @param buildClient                  The build client for the EWM server.
	 * @param brHandle                     The handle to the build result.
	 * @param contributionType             The type of the contribution.
	 * @param progress                     Progress monitor
	 * @return                             All file related contributions 
	 * @throws RTCConfigurationException   If one of the input arguments is invalid.
	 * @throws TeamRepositoryException     If there is an issue when communicating with the server
	 */
	private IBuildResultContribution[] getBuildResultContributions(ITeamBuildClient buildClient, 
								IBuildResultHandle brHandle, String contributionType, SubMonitor progress) 
								throws RTCConfigurationException, TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			String extendedContributionTypeId = getExtendedContributionTypeIdFromContributionType(contributionType); 
			return buildClient.getBuildResultContributions(brHandle,
							extendedContributionTypeId,
							monitor.newChild(20));
		} finally {
			monitor.done();
		}
	}

	/**
	 * Compose the return values for wait for build
	 * 
	 * @param result         The build result 
	 * @param buildStates    The states on which the build was waiting for
	 * @return
	 */
	private Map<String, String> composeReturnPropertiesForWaitBuild(IBuildResult result, 
									BuildState[] buildStates) {
		HashMap<String, String> buildProperties = new HashMap<>();
		buildProperties.put(Constants.RTCBuildUtils_BUILD_STATE, result.getState().toString());
		buildProperties.put(Constants.RTCBuildUtils_BUILD_STATUS, result.getStatus().toString());
		// We consider both interrupted or timed out from waiting for build state as the same 
		// for now and set the timed out value to true.
		// Also, if the build result is in one of the states, we consider timed out 
		// as false, even if it might have been a race condition and both could have 
		// both timed out and reached the build state at the same time.
		if (isInState(result, buildStates)) {
			buildProperties.put(Constants.RTCBuildUtils_TIMEDOUT, Boolean.FALSE.toString());
		} else {
			buildProperties.put(Constants.RTCBuildUtils_TIMEDOUT, Boolean.TRUE.toString());
		}
		return buildProperties;
	}

	/**
	 * Returns the build result for the given buildResultHandle
	 * 
	 * @param buildResultHandle         The handle of the build result.
	 * @param teamRepository            An instance of {@link ITeamRepository}
	 * @param monitor                   A progress monitor
	 * @return                          An instance of {@link IBuildResult}  
	 *                                  If the build is not found, then it throws an 
	 *                                  {@link ItemNotFoundException} 
	 * @throws TeamRepositoryException
	 */
	private IBuildResult getBuildResult(IBuildResultHandle buildResultHandle, 
					ITeamRepository teamRepository, SubMonitor monitor) throws TeamRepositoryException {
		IBuildResult result = null;
		try {
			result = (IBuildResult) teamRepository.itemManager().fetchCompleteItem(
								buildResultHandle, IItemManager.REFRESH, monitor.newChild(1));
		} catch (ItemNotFoundException exp) {
			throw new TeamRepositoryException(
						Messages.getDefault().RTCBuildUtils_invalid_build_result_provided(
								buildResultHandle.getItemId().getUuidValue()), exp);
		}
		return result;
	}

	/**
	 * Returns an {@link RTCConfiguration} exception to indicate that the given handle 
	 * does not exist
	 * 
	 */
	private IBuildResult getBuildResultWithValidation(IBuildResultHandle buildResultHandle,
						String [] propertiesToFetch, ITeamRepository teamRepository, SubMonitor monitor)
						throws TeamRepositoryException, RTCConfigurationException {
		IBuildResult result = null;
		try {
			if (propertiesToFetch == null) {
				result = (IBuildResult) teamRepository.itemManager().fetchCompleteItem(
					 		buildResultHandle, IItemManager.REFRESH, monitor.newChild(23));
			} else {
				result = (IBuildResult) teamRepository.itemManager().fetchPartialItem(
				 		buildResultHandle, IItemManager.REFRESH, Arrays.asList(propertiesToFetch), 
				 		monitor.newChild(23));
			}
		} catch (ItemNotFoundException | ClassCastException exp) {
			// If build result is not found, throw an error
			throw new RTCConfigurationException(
					Messages.getDefault().RTCBuildUtils_build_result_id_not_found(
							buildResultHandle.getItemId().getUuidValue()));
		}
		return result;
	}

	/**
	 * Checks whether the given build result is in one of the states mentioned in 
	 * <code>buildStates</code>
	 * 
	 * @param result
	 * @param buildStates
	 * @return
	 */
	private boolean isInState(IBuildResult result, BuildState [] buildStates) {
		for (BuildState buildState : buildStates) {
			if (result.getState().equals(buildState)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Convert each state entry which is a {@link String} in the array to an  
	 * entry of type {@link BuildState}
	 * 
	 * @param buildStatesToWaitFor          An array of buildStates, where each entry is a 
	 *                                      {@link String}. Never <code>null</code>
	 * @return                              An array of build states, where each entry is a 
	 *                                      {@link BuildState}. Never <code>null</code> 
	 * @throws RTCConfigurationException
	 */
	private BuildState[] convertBuildStates(String [] buildStatesToWaitFor) throws RTCConfigurationException {
		List<BuildState> buildStates = new ArrayList<BuildState>();
		for (String buildStateToWaitFor : buildStatesToWaitFor) {
			try {
				BuildState state = BuildState.valueOf(buildStateToWaitFor);
				buildStates.add(state);
			} catch (IllegalArgumentException exp) {
				throw new RTCConfigurationException(Messages.getDefault().
							RTCBuildUtils_invalid_build_state(buildStateToWaitFor));
			}
		}
		return buildStates.toArray(new BuildState[buildStates.size()]);
	}
	
	/**
	 * Sleep for given milliseconds 
	 * 
	 * @param timeout    A value in milliseconds.
 	 * @return           <code>true</code> if the sleep was interrupted,
 	 *                   </code>false</code> otherwise.
	 */
	private boolean sleep(long timeout) {
		try {
			Thread.sleep(timeout);
		} catch (InterruptedException exp) {
			return true;
        }
		return false;
	}
	
	/**
	 * Truncate the label for the contribution's description
	 * 
	 * @param contribution     The build result contribution
	 * @return                 The truncated description. Max length is 
	 *                         {@link #FILE_CONTRIBUTION_LABEL_MAX_LENGTH}
	 */
	private String getTruncatedLabelForListFiles(IBuildResultContribution contribution) {
		String label = contribution.getLabel();
		if (label == null) {
			return "";
		}
		if (label.length() >= FILE_CONTRIBUTION_LABEL_MAX_LENGTH) {
			label = label.substring(0, FILE_CONTRIBUTION_LABEL_MAX_LENGTH);
		}
		return label;
	}
	
	/**
	 * Get the short form contribution type from the extended contribution type id
	 * 
	 * @param extendedContributionTypeId    The long form of contributionType string. 
	 *                                      Also known as extended contribution type id.
	 * @return                              A String which is the short form contribution type. 
	 *  
	 * @throws RTCConfigurationException    If the given parameter is not one of the acceptable values
	 *                                      Valid values are 
	 *                                      <ul>
	 *                                        <li>{@link IBuildResultContribution#ARTIFACT_EXTENDED_CONTRIBUTION_ID}</li>
	 *                                        <li>{@link IBuildResultContribution#LOG_EXTENDED_CONTRIBUTION_ID}</li>
	 *                                      </ul>
	 */
	private String getContributionTypeFromExtendedContributionTypeId(
					String extendedContributionTypeId) throws RTCConfigurationException {
		String contributionType = null;
		if (IBuildResultContribution.
					LOG_EXTENDED_CONTRIBUTION_ID.equals(extendedContributionTypeId)) {
			contributionType = Constants.RTCBuildUtils_LOG_TYPE_KEY;
		} else if (IBuildResultContribution.
					ARTIFACT_EXTENDED_CONTRIBUTION_ID.equals(extendedContributionTypeId)) {
			contributionType = Constants.RTCBuildUtils_ARTIFACT_TYPE_KEY;
		} else {
			throw new RTCConfigurationException(Messages.getDefault().
					RTCBuildUtils_invalid_extendedContributionTypeId_specified(extendedContributionTypeId));
		}
		return contributionType;
	}
	
	/**
	 * Get the long form of the contribution type 
	 * 
	 * @param  contributionType           The short form contribution type
	 * @return                            A string representation of the long form of contribution type,
	 *                                    called extended contribution type id.
	 * @throws RTCConfigurationException  If the given contribution type is not one of the valid values.
	 */
	private String getExtendedContributionTypeIdFromContributionType(String contributionType) 
							throws RTCConfigurationException {
		String extendedContributionTypeId = null;
		switch(contributionType) {
		   case Constants.RTCBuildUtils_LOG_TYPE_KEY:
			   extendedContributionTypeId = IBuildResultContribution.LOG_EXTENDED_CONTRIBUTION_ID;
			   break;
		   case Constants.RTCBuildUtils_ARTIFACT_TYPE_KEY:
			   extendedContributionTypeId = IBuildResultContribution.ARTIFACT_EXTENDED_CONTRIBUTION_ID;
			   break;
		   default:
			   throw new RTCConfigurationException(Messages.getDefault().
					   RTCBuildUtils_invalid_contribution_type_specified(contributionType));
		}
		return extendedContributionTypeId;
	}
	
	/**
	 * Validate the contribution type parameter. Valid values are 
	 * <ul>
	 * 	<li>{@link Constants#RTCBuildUtils_ARTIFACT_TYPE_KEY}</li>
	 *  <li>{@link Constants#RTCBuildUtils_LOG_TYPE_KEY}</li>
	 * </ul>
	 * 
	 * @param contributionType             The contributionType parameter as a String
	 * @throws RTCConfigurationException   If the validation fails, this exception is thrown
	 */
	private void validateContributionTypeParam(String contributionType) throws RTCConfigurationException {
		if (contributionType == null) {
			throw new RTCConfigurationException(
					Messages.getDefault().RTCBuildUtils_invalid_contribution_type_specified("null"));
		}
		switch(contributionType) {
			case Constants.RTCBuildUtils_ARTIFACT_TYPE_KEY:
			case Constants.RTCBuildUtils_LOG_TYPE_KEY:
				break;
			default:
				throw new RTCConfigurationException(
					Messages.getDefault().RTCBuildUtils_invalid_contribution_type_specified(contributionType));
		}
	}

	/**
	 * 
	 * Validate the given build result UUID parameter
	 * 
	 * @param buildResultUUID             Build Result UUID
	 * @throws RTCConfigurationException  If validation fails, this exception is thrown.
	 */
	private void validateBuildResultUUIDParam(String buildResultUUID) throws RTCConfigurationException {
		if (buildResultUUID == null) {
			throw new RTCConfigurationException(
					Messages.getDefault().RTCBuildUtils_build_result_id_is_null());
		}
		try {
			UUID.valueOf(buildResultUUID);
		} catch (IllegalArgumentException exp) {
			throw new IllegalArgumentException(
					Messages.getDefault().RTCBuildUtils_build_result_UUID_invalid(buildResultUUID));
		}
	}
	
	private String getMessageForContributionNotFound(String fileType,
			String fileName, String componentName, String contentId) {
		String message = null;
		if (Constants.RTCBuildUtils_LOG_TYPE_KEY.equals(fileType)) {
			if (fileName != null) {
				if (componentName != null) {
					message = Messages.getDefault().
					RTCBuildUtils_no_log_file_download_component(
							fileName, "fileName", componentName);
				} else {
					message = Messages.getDefault().
							RTCBuildUtils_no_log_file_download(
									fileName, "fileName");
				}
			} else {
				message = Messages.getDefault().
						RTCBuildUtils_no_log_file_download(
								contentId, "contentId");
			}
		} else {
			if (fileName != null) {
				if (componentName != null) {
					message = Messages.getDefault().
					RTCBuildUtils_no_artifact_file_download_component(
							fileName, "fileName", componentName);
				} else {
					message = Messages.getDefault().
							RTCBuildUtils_no_artifact_file_download(
									fileName, "fileName");
				}
			} else {
				message = Messages.getDefault().
						RTCBuildUtils_no_artifact_file_download(
								contentId, "contentId");
			}
		}
		return message;
	}

	@SuppressWarnings("restriction")
	private String getInternalIdFromBuildResultContribution(IBuildResultContribution contribution) {
		return ((BuildResultContribution) contribution).getInternalId().getUuidValue();
	}
}
