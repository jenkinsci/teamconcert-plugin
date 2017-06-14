/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.common.builddefinition.AutoDeliverTriggerPolicy;
import com.ibm.team.build.common.builddefinition.IAutoDeliverConfigurationElement;
import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IBuildDefinitionInstance;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestHandle;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.ItemManager;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;

/**
 * 
 * Utility class for getting information on RTC Build artifacts like definitions, engines a
 * and results
 *
 */
public class RTCBuildUtils {
	private static RTCBuildUtils instance = null;
	
	static {
		instance = new RTCBuildUtils();
	}
	
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
		IBuildRequest buildRequest = (IBuildRequest) teamRepository.itemManager().fetchCompleteItem((IBuildRequestHandle) buildRequestHandles.iterator().next(),
        									IItemManager.REFRESH, monitor.newChild(2));
		IBuildDefinitionInstance buildDefinitionInstance = buildRequest.getBuildDefinitionInstance();
		
		HashMap<String, String> pbDeliverConfigInfo = new HashMap<String, String>();

		// Fill up some information required for post build deliver.
		// Check whether post build deliver configuration element is present
		IBuildConfigurationElement pbDeliverConfigElement = buildDefinitionInstance.getConfigurationElement(IAutoDeliverConfigurationElement.ELEMENT_ID);
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
					pbDeliverConfigInfo.put(Constants.PB_TRIGGER_POLICY_KEY, Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE); // This is a flag to tell the client that we don't know what the trigger policy is
				}
			} else {
				pbDeliverConfigInfo.put(Constants.PB_TRIGGER_POLICY_KEY, Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE); // This is a flag to tell the client that we don't know what the trigger policy is
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
}
