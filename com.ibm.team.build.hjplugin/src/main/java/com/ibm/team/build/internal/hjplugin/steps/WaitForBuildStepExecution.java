/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.steps;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.tasks.WaitForBuildTask;
import com.ibm.team.build.internal.hjplugin.util.Helper;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class WaitForBuildStepExecution extends RTCBuildStepExecution<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(WaitForBuildStepExecution.class.getName());

	private static final long serialVersionUID = 1L;
	
	public WaitForBuildStepExecution(RTCBuildStep step, StepContext context) {
		super(step, context);
	}
	
	@Override
	protected RTCBuildStepResponse run() throws Exception {
		LOGGER.entering(this.getClass().getName(), "run");

		// assertAllFieldsInContext are not null
		assertRequiredContext(getContext());

		// From the context, get the workspace to compute the build tool.
		FilePath workspace = getWorkspace();
		TaskListener listener = getTaskListener();
		Run<?, ?> run = getRun();
		Node node = getComputer().getNode();

		sendJarsIfRequired(workspace);
		
		// Get the toolkit path on the computer
		String serverURI = Util.fixEmptyAndTrim(getStep().getServerURI());
		int timeout = getStep().getTimeout();
		
		String buildTool = Util.fixEmptyAndTrim(getStep().getBuildTool());
		String buildToolkitPath =  getBuildToolkitPath(listener, node, buildTool);
		StandardUsernamePasswordCredentials credentials = getCredentials(run,
									getStep().getServerURI(), getStep().getCredentialsId());

		// Task specific variables
		String buildResultUUID = Util.fixEmptyAndTrim(getStep().getTask().getBuildResultUUID());
		String [] buildStates = parseBuildStates(Util.fixEmptyAndTrim(getStep().getTask().getBuildStates()));
		long waitBuildTimeout = getStep().getTask().getWaitBuildTimeout();
		
		validateArguments(serverURI, timeout, buildTool, buildToolkitPath, credentials, 
						buildResultUUID, buildStates, waitBuildTimeout);
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(this.getClass().getName() + ":run() - creating WaitBuildTask");
		}
		WaitForBuildTask task = new WaitForBuildTask(buildToolkitPath,
				serverURI, credentials.getUsername(), 
				credentials.getPassword().getPlainText(), timeout, 
				buildResultUUID, buildStates, 
				waitBuildTimeout,
				isDebug(run, listener), listener);
		return workspace.act(task);
	}
	
	private String [] parseBuildStates(String buildStatesStr) {
		if (buildStatesStr == null) {
			return new String[0];			
		}
        return Helper.extractBuildStates(buildStatesStr);
	}
	
	private void validateArguments(String serverURI, int timeout, String buildTool,
					String buildToolkitPath, StandardUsernamePasswordCredentials credentials,
					String buildResultUUID, String [] buildStates, 
					long waitBuildTimeout) throws IllegalArgumentException {
		LOGGER.entering(this.getClass().getName(), "validateArguments");
	
		validateGenericArguments(serverURI, timeout, 
				buildTool, buildToolkitPath, credentials);
		
		if (buildResultUUID == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_buildResultUUID());
		}
		
		validateBuildStates(buildStates);

		if (waitBuildTimeout == 0 || waitBuildTimeout < -1) {
			throw new IllegalArgumentException(
					Messages.RTCBuildStep_invalid_waitBuildTimeout(Long.toString(waitBuildTimeout)));
		}
	}

	private void validateBuildStates(String[] buildStates) {
		LOGGER.entering(this.getClass().getName(), "validateBuildStates");
		if (buildStates == null || (buildStates != null && 
				buildStates.length == 0)) {
			throw new IllegalArgumentException(
					Messages.RTCBuildStep_missing_build_states());
		}
		String [] invalidStates = Helper.getInvalidStates(buildStates);
		if (invalidStates.length > 0) {
			throw new IllegalArgumentException(
					Messages.RTCBuildStep_invalid_build_states_2(String.join(",", invalidStates)));
		}
	}
}
