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
package com.ibm.team.build.internal.hjplugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.BuildDefinitionInfo.BuildDefinitionInfoBuilder;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildConstants;

import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class RTCBuildDefinitionDetailsTask extends RTCTask<BuildDefinitionInfo> {

	private static final Logger LOGGER = Logger.getLogger(RTCBuildDefinitionDetailsTask.class.getName());

	private final String buildtoolkit;
	private final String serverURI;
	private final String password;
	private final String buildResultItemId;
	private final int timeout;
	private final String userId;
	private final Locale clientLocale;

	/**
	 * Retrieves build definition details for the given build result
	 * 
	 * @param buildtoolkit
	 * @param serverURI
	 * @param userId
	 * @param password
	 * @param timeout
	 * @param buildDefinitionId - The id of the build definition. Cannot be <code>null</code>
	 * @param clientLocale
	 * @param isDebug
	 * @param listener
	 */
	public RTCBuildDefinitionDetailsTask(
			String buildtoolkit, String serverURI, String userId, 
			String password, int timeout, String buildResultItemId, 
			Locale clientLocale, boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.buildtoolkit = buildtoolkit;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildResultItemId = buildResultItemId;
		this.clientLocale = clientLocale;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public BuildDefinitionInfo invoke(File arg0, VirtualChannel arg1) throws IOException, InterruptedException {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Fetching build definition details"); //$NON-NLS-1$
		}
		if (getIsDebug()) {
			debug("serverURI " + serverURI);
			debug("userId " + userId);
			debug("timeout " + timeout);
			debug("buildResultItemId " + buildResultItemId);
			debug("listener is " + (getListener() == null ? "n/a" : getListener()));
			debug("buildtoolkit " + buildtoolkit);
		}
		try {
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildtoolkit, (getIsDebug() && getListener() != null) ? getListener().getLogger() : null);
			if (getIsDebug() && getListener() != null) {
				debug("hjplugin-rtc.jar" + RTCFacadeFactory.getFacadeJarURL(getListener().getLogger()).toString());
			}
			
			@SuppressWarnings("unchecked")
			Map<String, Object> buildDefinitionInfo = (Map<String, Object>) facade.invoke("getBuildDefinitionInfoFromBuildResult", new Class[] {
					String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // buildResultItemId
					Object.class, // listener
					Locale.class, // clientLocale
					
			}, serverURI, userId, password, timeout,
			   buildResultItemId, getListener(),
			   clientLocale);
			
			LOGGER.finest("Retrieved build definition info");

			// First retrieve the pb deliver details
			@SuppressWarnings("unchecked")
			Map<String, String> pbDeliverInfo = (Map<String, String>) buildDefinitionInfo.get(RTCBuildConstants.PB_INFO_ID);
			boolean pbConfigured = Boolean.parseBoolean(pbDeliverInfo.get(RTCBuildConstants.PB_CONFIGURED_KEY));
			boolean pbEnabled = false;
			if (pbConfigured) {
				pbEnabled = Boolean.parseBoolean(pbDeliverInfo.get(RTCBuildConstants.PB_ENABLED_KEY));
			}
			boolean pbTriggerPolicyUnknown = false;
			String pbTriggerPolicy = pbDeliverInfo.get(RTCBuildConstants.PB_TRIGGER_POLICY_KEY);
			// Check the trigger policy only if pb deliver is configured
			if (pbConfigured && RTCBuildConstants.PB_TRIGGER_POLICY_UNKNOWN_VALUE.equals(pbTriggerPolicy)) {
				pbTriggerPolicyUnknown = true;
			}
			@SuppressWarnings("unchecked")
			Map<String, String> genericInfo = (Map<String, String>) buildDefinitionInfo.get(RTCBuildConstants.GENERIC_INFO_ID);
			String buildDefinitionId = genericInfo.get(RTCBuildConstants.GENERIC_BUILD_DEFINITION_ID_KEY);
			
			LOGGER.finest("Contructing build definition info");
			BuildDefinitionInfoBuilder bdBuilder = new BuildDefinitionInfoBuilder();
			bdBuilder.setPBConfigured(pbConfigured)
				     .setPBEnabled(pbEnabled)
					 .setPBTriggerPolicy(pbTriggerPolicy)
					 .setPBTriggerPolicyUnknown(pbTriggerPolicyUnknown)
					 .setId(buildDefinitionId);

			return bdBuilder.build();
		} catch (Exception e) {
			Throwable eToReport = e;
			if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
				eToReport = e.getCause();
			}
			if (eToReport instanceof InterruptedException) {
				// Should be caught by the caller and RTC specific message should be put into the log
				throw (InterruptedException) eToReport;
			}
			// The exception should be logged before its lost
			if (LOGGER.isLoggable(Level.SEVERE)) {
				LOGGER.log(Level.SEVERE, Messages.RTCBuildDefinitionDetailsTask_unable_to_fetch_details2(buildResultItemId), e);
			}
			throw new AbortException(Messages.RTCBuildDefinitionDetailsTask_unable_to_fetch_details(buildResultItemId, 
									eToReport.getMessage()));
		}
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
