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
import com.ibm.team.build.internal.hjplugin.tasks.RetrieveSnapshotTask;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * 
 * Represents the execution of the retrieve snapshot task 
 *
 */
public class RetrieveSnapshotStepExecution extends RTCBuildStepExecution<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(RetrieveSnapshotStepExecution.class.getName());

	private static final long serialVersionUID = 1L;
	
	public RetrieveSnapshotStepExecution(RTCBuildStep step, StepContext context) {
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
		
		String serverURI = Util.fixEmptyAndTrim(getStep().getServerURI());
		int timeout = getStep().getTimeout();
		
		String buildTool = Util.fixEmptyAndTrim(getStep().getBuildTool());
		// Get the toolkit path on the computer
		String buildToolkitPath =  getBuildToolkitPath(listener, node, buildTool);
		StandardUsernamePasswordCredentials credentials = getCredentials(run,
									getStep().getServerURI(), getStep().getCredentialsId());
		// Task specific variables
		String buildResultUUID = Util.fixEmptyAndTrim(getStep().getTask().getBuildResultUUID());
		
		validateArguments(serverURI, timeout, buildTool, buildToolkitPath,  
						credentials, buildResultUUID);
		
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(this.getClass().getName() + ":run() - creating RetrieveSnapshotTask");
		}
		RetrieveSnapshotTask task = new RetrieveSnapshotTask(buildToolkitPath,
				serverURI, credentials.getUsername(), 
				credentials.getPassword().getPlainText(), timeout, 
				buildResultUUID, isDebug(run, listener), listener);
		return workspace.act(task);
	}
	
	private void validateArguments(String serverURI, int timeout, String buildTool,
					String buildToolkitPath, StandardUsernamePasswordCredentials credentials,
					String buildResultUUID) throws IllegalArgumentException {
		LOGGER.entering(this.getClass().getName(), "validateArguments");
	
		validateGenericArguments(serverURI, timeout, 
				buildTool, buildToolkitPath, credentials);
		
		if (buildResultUUID == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_buildResultUUID());
		}
	}
}
