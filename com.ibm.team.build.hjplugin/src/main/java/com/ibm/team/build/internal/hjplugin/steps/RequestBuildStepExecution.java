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

import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.model.Run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
import com.ibm.team.build.hjplugin.steps.RequestBuildStepResponse;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCTask;
import com.ibm.team.build.internal.hjplugin.steps.RTCBuildStep.BuildProperty;
import com.ibm.team.build.internal.hjplugin.tasks.RequestBuildTask;

public class RequestBuildStepExecution extends RTCBuildStepExecution<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(RequestBuildStepExecution.class.getName());

	private static final long serialVersionUID = 1L;
	
	public RequestBuildStepExecution(RTCBuildStep step, StepContext context) {
		super(step, context);
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
		
		// if the execution happens 
		sendJarsIfRequired(workspace);
		
		String serverURI = Util.fixEmptyAndTrim(getStep().getServerURI()); 
		int timeout = getStep().getTimeout();
		String buildTool = Util.fixEmptyAndTrim(getStep().getBuildTool());
		String credentialsId = Util.fixEmptyAndTrim(getStep().getCredentialsId());
		String buildToolkitPath = Util.fixEmptyAndTrim(getBuildToolkitPath(listener, node, buildTool));
		StandardUsernamePasswordCredentials credentials = getCredentials(run, serverURI, credentialsId);
		
		// Task specific variables
		String buildDefinitionId = Util.fixEmptyAndTrim(getStep().getTask().getBuildDefinitionId());
		boolean shouldDeleteProperties = getStep().getTask().getDeleteProperties();
		List<BuildProperty> propertiesToDelete = getStep().getTask().getPropertiesToDelete();
		boolean shouldAddOrOverrideProperties = getStep().getTask().getAddOrOverrideProperties();
		List<BuildProperty> propertiesToAddOrOverride = getStep().getTask().getPropertiesToAddOrOverride();
		boolean linkEWMBuild = getStep().getTask().getLinkEWMBuild();

		validateArguments(serverURI, timeout, buildTool, buildToolkitPath, credentials, 
				buildDefinitionId);

		String[] buildPropertiesToDelete = new String[] {};
		if (shouldDeleteProperties && propertiesToDelete != null && !propertiesToDelete.isEmpty()) {
			List<String> namesOfPropertiesToDelete = new ArrayList<String>();
			for (BuildProperty buildProperty : propertiesToDelete) {
				String propertyName = Util.fixEmptyAndTrim(buildProperty.getPropertyName());
				// ignore properties with empty names
				if (propertyName != null) {
					namesOfPropertiesToDelete.add(propertyName);
				}
			}
			buildPropertiesToDelete = namesOfPropertiesToDelete.toArray(new String[namesOfPropertiesToDelete.size()]);
		}

		HashMap<String, String> buildPropertiesToAddOrOverride = new HashMap<String, String>();
		if (shouldAddOrOverrideProperties && propertiesToAddOrOverride != null && !propertiesToAddOrOverride.isEmpty()) {
			for (BuildProperty buildProperty : propertiesToAddOrOverride) {
				String propertyName = Util.fixEmptyAndTrim(buildProperty.getPropertyName());
				// ignore properties with empty names
				if (propertyName != null) {
					buildPropertiesToAddOrOverride.put(propertyName, buildProperty.getPropertyValue());
				}
			}
		}

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(this.getClass().getName() + ":run() - creating RequestBuildTask");
		}
		
		RTCTask<RTCBuildStepResponse> task = new RequestBuildTask(buildToolkitPath,
				serverURI, credentials.getUsername(), 
				credentials.getPassword().getPlainText(), timeout, 
				buildDefinitionId, buildPropertiesToDelete, buildPropertiesToAddOrOverride, 
				isDebug(run, listener), listener);

		// For now, all exceptions are thrown back instead of being caught and the build set 
		// to an error. We can consider how to deal with this later after consultation with Jenkins devs.
		RTCBuildStepResponse response = workspace.act(task);
		if (linkEWMBuild) {
			run.addAction(new RTCBuildResultAction(serverURI, ((RequestBuildStepResponse)response).getBuildResultUUID()));
		}
		return response;
	}
	
	private void validateArguments(String serverURI, int timeout, 
			String buildTool, String buildToolkitPath, 
			StandardUsernamePasswordCredentials credentials, String buildDefinitionId) 
			throws IllegalArgumentException {
		LOGGER.entering(this.getClass().getName(), "validateArguments");

		validateGenericArguments(serverURI, timeout, buildTool, 
				buildToolkitPath, credentials);
		
		if (buildDefinitionId == null) {
			throw new IllegalArgumentException(
					Messages.RTCBuildStep_missing_buildDefinitionId());
		}
	}
}
