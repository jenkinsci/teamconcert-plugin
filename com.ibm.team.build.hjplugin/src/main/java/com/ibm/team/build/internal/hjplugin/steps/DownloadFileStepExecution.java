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
import com.ibm.team.build.internal.hjplugin.tasks.DownloadFileTask;
import com.ibm.team.build.internal.hjplugin.util.ValidationHelper;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;

public class DownloadFileStepExecution extends RTCBuildStepExecution<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(RequestBuildStepExecution.class.getName());

	private static final long serialVersionUID = 1L;
	
	private final String contributionType;

	public DownloadFileStepExecution(RTCBuildStep step, StepContext context, 
				String contributionType) {
		super(step, context);
		this.contributionType = contributionType;
	}
	
	@Override
	protected RTCBuildStepResponse run() throws Exception {
		LOGGER.entering(this.getClass().getName(), "run");
		// assertAllFieldsInContext are not null
		assertRequiredContext(getContext());
		
		FilePath workspace = getWorkspace();
		TaskListener listener = getTaskListener();
		Run<?, ?> run = getRun();
		Node node = getComputer().getNode();
		
		// if the execution happens in agent, send the -rtc jar to 
		// the agent.
		sendJarsIfRequired(workspace);
		
		// Generic variables
		String serverURI = Util.fixEmptyAndTrim(getStep().getServerURI()); 
		int timeout = getStep().getTimeout();
		String buildTool = Util.fixEmptyAndTrim(getStep().getBuildTool());
		String credentialsId = Util.fixEmptyAndTrim(getStep().getCredentialsId());
		String buildToolkitPath = Util.fixEmptyAndTrim(getBuildToolkitPath(listener, node, buildTool));
		StandardUsernamePasswordCredentials credentials = getCredentials(run, serverURI, credentialsId);
		
		validateGenericArguments(serverURI, timeout, buildTool, 
										buildToolkitPath, credentials);

		// Task specific variables
		String buildResultUUID = Util.fixEmptyAndTrim(getStep().getTask().getBuildResultUUID());
		String fileName = Util.fixEmptyAndTrim(getStep().getTask().getFileName());
		String componentName = Util.fixEmptyAndTrim(getStep().getTask().getComponentName());
		String contentId = Util.fixEmptyAndTrim(getStep().getTask().getContentId());
		String destinationFileName = Util.fixEmptyAndTrim(getStep().getTask().getDestinationFileName());

		validateArguments(buildResultUUID, fileName, componentName,
				contentId, destinationFileName);
		
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(this.getClass().getName() + ":run() - creating DownloadFileTask");
		}
		// Create task and run it
		DownloadFileTask task = new DownloadFileTask(buildToolkitPath, serverURI, 
				credentials.getUsername(), credentials.getPassword().getPlainText(), timeout,
				buildResultUUID, fileName, componentName, contentId, contributionType, 
				workspace.getRemote(), destinationFileName, isDebug(run, listener), listener);
		return workspace.act(task);
	}

	private void validateArguments(String buildResultUUID, String fileName, 
						String componentName, String contentId, String destinationFileName) {
		
		// Validate empty fields
		if (buildResultUUID == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_buildResultUUID());
		}
		
		// An empty fileName is allowed only if contentId is not null and vice versa	
		if (fileName == null && contentId == null) {
			throw new IllegalArgumentException(
							Messages.RTCBuildStep_contentId_destination_path_none_provided());
		}
		
		// If both fileNameOrPattern and contentId is provided, we should error out
		if (fileName != null && contentId != null) {
			throw new IllegalArgumentException(
						Messages.RTCBuildStep_contentId_destination_path_both_provided());
		}
		
		if (destinationFileName != null) {
			// This is still an OS agnostic validation, we don't want to have any 
			// characters that might be construed as path either in Windows or Linux.
			FormValidation fv = ValidationHelper.validateFileName(destinationFileName);
			if (fv.kind == FormValidation.Kind.ERROR) {
				throw new IllegalArgumentException(fv.getMessage());
			}
		}
	}
}
