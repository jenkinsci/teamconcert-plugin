/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.util;

import hudson.model.AbstractProject;
import hudson.model.TaskListener;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;

public class RTCBuildResultHelper {
	private static final Logger LOGGER = Logger.getLogger(RTCBuildResultHelper.class.getName());

	/**
	 * Delete the RTC Build result(s) that were associated with the Jenkins build
	 * @param buildResultActions The actions identifying the build results to delete
	 * @param project The project that was built (its necessary to resolve Jenkins Credentials)
	 * @param rtcScmConfigs The current RTC SCM configuration(s) of the project that did the build.
	 */
	public static void deleteRTCBuildResults(
			List<RTCBuildResultAction> buildResultActions,
			AbstractProject<?, ?> project, Set<RTCScm> rtcScmConfigs) {
		
		// Delete the rtc build result for each action
		for (RTCBuildResultAction buildResultAction : buildResultActions) {
			try {
				// attempt to delete the build result
				// buildResultUUID will be null if dealing with a workspace build
				String buildResultUUID = buildResultAction.getBuildResultUUID();
				if (buildResultUUID != null && !buildResultUUID.isEmpty()) {
					// attempt to use the latest toolkit/credentials associated with the project
					// if its a MultiScm project that will be difficult
					RTCScm rtcScm = RTCScmConfigHelper.findRTCScm(rtcScmConfigs, buildResultAction);
					if (rtcScm != null) {
						// Use the toolkit the project is currently configured to use (if we need the toolkit)
						String masterBuildToolkit = rtcScm.getDescriptor().getMasterBuildToolkit(
								rtcScm.getBuildTool(), TaskListener.NULL);
						
						// Use the credentials that the project is currently configured to use (may of changed
						// since the build was originally run)
						RTCLoginInfo loginInfo = rtcScm.getLoginInfo(
								project, masterBuildToolkit);
						RTCFacadeFacade.deleteBuild(masterBuildToolkit,
								loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
								loginInfo.getTimeout(), rtcScm.getAvoidUsingToolkit(), buildResultUUID);
					}
				}
			} catch (Exception e) {
				LOGGER.log(Level.WARNING, Messages.RTCBuildResultHelper_delete_build_result_failed(buildResultAction.getUrlName(), e.getMessage()));
				LOGGER.log(Level.FINER, "failed to delete build result", e);
			}
		}
	}
}
