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
import com.ibm.team.build.internal.hjplugin.rtc.RTCValidationException;
import com.ibm.team.build.internal.hjplugin.tasks.ListFilesTask;
import com.ibm.team.build.internal.hjplugin.util.ValidationHelper;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

public class ListFilesStepExecution extends RTCBuildStepExecution<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(RequestBuildStepExecution.class.getName());

	private static final long serialVersionUID = 1L;
	
	private final String contributionType;
	
	public ListFilesStepExecution(RTCBuildStep step, StepContext context,
						String contributionType) {
		super(step, context);
		this.contributionType = contributionType;
	}
	
	@Override
	protected RTCBuildStepResponse run() throws Exception {
		LOGGER.entering(this.getClass().getName(), "run");
		
		// assertAllFieldsInContext are not null
		assertRequiredContext(getContext());
		
		// Get the context
		FilePath workspace = getWorkspace();
		TaskListener listener = getTaskListener();
		Run<?, ?> run = getRun();
		Node node = getComputer().getNode();
		
		// If the execution happens in an agent, send the jars to the agent. 
		sendJarsIfRequired(workspace);
		
		// Generic variables needed for all steps
		String serverURI = Util.fixEmptyAndTrim(getStep().getServerURI()); 
		int timeout = getStep().getTimeout();
		String buildTool = Util.fixEmptyAndTrim(getStep().getBuildTool());
		String credentialsId = Util.fixEmptyAndTrim(getStep().getCredentialsId());
		String buildToolkitPath = Util.fixEmptyAndTrim(getBuildToolkitPath(listener, node, buildTool));
		StandardUsernamePasswordCredentials credentials = getCredentials(run, serverURI, credentialsId);
		
		validateGenericArguments(serverURI, timeout, buildTool, 
										buildToolkitPath,
										credentialsId, credentials);

		// Task specific variables
		String buildResultUUID = Util.fixEmptyAndTrim(getStep().getTask().getBuildResultUUID());
		String fileNameOrPattern = Util.fixEmptyAndTrim(getStep().getTask().getFileNameOrPattern());
		String componentName = Util.fixEmptyAndTrim(getStep().getTask().getComponentName());
		int maxResults = getStep().getTask().getMaxResults();

		validateArguments(buildResultUUID, fileNameOrPattern, componentName, maxResults);

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(this.getClass().getName() + ":run() - creating ListFilesTask");
		}
		
		// Create task and run it
		ListFilesTask task = new ListFilesTask(buildToolkitPath, serverURI, 
							credentials.getUsername(),
							credentials.getPassword().getPlainText(),
							timeout,
							buildResultUUID,
							fileNameOrPattern,
							componentName,
							contributionType,
							maxResults,
							isDebug(run, listener),
							listener);
		return workspace.act(task);
	}
	
	private void validateArguments(String buildResultUUID, String fileNameOrPattern, 
						String componentName, int maxResults) throws RTCValidationException {
		
		// validate empty fields
		if (buildResultUUID == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_buildResultUUID());
		}
		
		// An empty fileNameOrPattern is allowed, hence validate only if the argument is non empty	
		if (fileNameOrPattern != null) {
			FormValidation result = ValidationHelper.validatePattern(fileNameOrPattern);
			if (result.kind == FormValidation.Kind.ERROR) {
				throw new IllegalArgumentException(result.getMessage());
			}
		}

		// validate maxResults to be greater than 0  and not greater than 2048
		FormValidation result =  ValidationHelper.validateMaxResultsParm(Integer.toString(maxResults));
		if (result.kind == FormValidation.Kind.ERROR) {
			throw new IllegalArgumentException(result.getMessage());
		}
	}
}
