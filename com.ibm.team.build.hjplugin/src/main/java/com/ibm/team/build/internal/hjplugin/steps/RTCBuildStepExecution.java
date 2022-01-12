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

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.util.Helper;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;

public abstract class RTCBuildStepExecution<T extends RTCBuildStepResponse>
						extends AbstractSynchronousStepExecution<RTCBuildStepResponse> {
	
	private static final Logger LOGGER = Logger.getLogger(RTCBuildStepExecution.class.getName());

	private static final long serialVersionUID = 1L;
	
	private final transient RTCBuildStep step;
	private final StepContext context;

	public RTCBuildStepExecution(RTCBuildStep step, 
						StepContext context) {
		super(context);
		this.step = step;
		this.context = context;
	}

	// We have one step execution per step and inherit
	// from this common step to perform
	// error checking and context setting.
	@Override
	protected abstract RTCBuildStepResponse run() throws Exception;
	
	@Override
	public StepContext getContext() {
		return context;
	}
	
	protected RTCBuildStep getStep() {
		return step;
	}

	protected Computer getComputer() throws IOException, InterruptedException {
		return context.get(Computer.class);
	}
	
	protected FilePath getWorkspace() throws IOException, InterruptedException {
		return context.get(FilePath.class);
	}

	protected TaskListener getTaskListener() throws IOException, InterruptedException {
		return context.get(TaskListener.class);
	}

	protected Run<?, ?> getRun() throws IOException, InterruptedException {
		return context.get(Run.class);
	}
	
	/**
	 * Before using any of the protected methods that use a <code>Computer</code> or
	 * <code>FilePath</code>, assert the following from the subclasses.
	 * 
	 * @param context
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws RTCConfigurationException 
	 */
	protected void assertRequiredContext(StepContext context) throws IOException, InterruptedException, RTCConfigurationException {
		LOGGER.entering(this.getClass().getName(), "assertRequiredContext");
		// Find a better way to perform these validations.
		// And these exceptions may not be easy for the user to understand.
		try {
			FilePath workspace = getWorkspace();
			TaskListener listener = getTaskListener();
			Run<?, ?> run = getRun();
			Computer c = getComputer();
			if (workspace == null) {
				throw new RTCConfigurationException(Messages.MissingContext_required("workspace"));
			}
			if (listener == null) {
				throw new RTCConfigurationException(Messages.MissingContext_required("listener"));
			}
			if (run == null) {
				throw new RTCConfigurationException(Messages.MissingContext_required("run"));
			}
			if (c == null) {
				throw new RTCConfigurationException(Messages.MissingContext_required("computer"));
			}
		} catch (IOException | InterruptedException exp) {
			throw new RTCConfigurationException(Messages.MissingContext_error(exp.getMessage()));
		}
	}
	
	protected static boolean isDebug(Run<?, ?> run, TaskListener listener) throws 
										IOException, InterruptedException {
		return Helper.isDebugEnabled(run, listener);
	}

	protected static String getBuildToolkitPath(TaskListener listener, Node node, 
							String buildTool) throws IOException, InterruptedException {
		LOGGER.entering(RTCBuildStepExecution.class.getName(), "getBuildToolkitPath");
		String buildToolkitPath = null;
	    RTCBuildToolInstallation[] installations = RTCBuildToolInstallation.allInstallations();
        for (RTCBuildToolInstallation buildToolInstallation : installations) {
        	if (buildToolInstallation.getName().equals(buildTool)) {
        		buildToolkitPath = buildToolInstallation.forNode(node, listener).getHome();
        		break;
        	}
        }
		return Util.fixEmptyAndTrim(buildToolkitPath);
	}
	
	protected static StandardUsernamePasswordCredentials getCredentials(Run<?, ?> run,
								String serverURI,
								String credentialsId) {
		LOGGER.entering(RTCBuildStepExecution.class.getName(), "getCredentials");
		if (credentialsId == null || 
				(credentialsId != null && credentialsId.isEmpty())) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_credentials_id());
		}
		// Get username, password from credentialsId
		// Will be uncommented once we do credentials tracking adoption 
		// StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(credentialsId,
 		//		StandardUsernamePasswordCredentials.class, run, URIRequirementBuilder.fromUri(serverURI).build());
 		List<StandardUsernamePasswordCredentials> allMatchingCredentials = 
 				CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, run.getParent(), ACL.SYSTEM,
 				URIRequirementBuilder.fromUri(serverURI).build());
 		StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(allMatchingCredentials, 
 				CredentialsMatchers.withId(credentialsId));
		return credentials;
	}
	
	protected static void sendJarsIfRequired(FilePath workspace) throws MalformedURLException, 
								IOException, InterruptedException {
		if (workspace.isRemote()) {
			LOGGER.fine("Sending jars to agent");
			Helper.sendJarsToAgent(workspace);
		}
	}
	
	protected void validateGenericArguments(String serverURI, int timeout, 
			String buildTool, String buildToolkitPath, String crdentialsId,
			StandardUsernamePasswordCredentials credentials) throws IllegalArgumentException {
		LOGGER.entering(this.getClass().getName(), "validateGenericArguments");

		if (serverURI == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_serverURI());
		}
		
		if (timeout <= 0) {
			throw new IllegalArgumentException(
					Messages.RTCBuildStep_invalid_timeout(Integer.toString(timeout)));
		}
		
		if (buildTool == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_buildTool());
		}
		
		if (buildToolkitPath == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_buildToolkit(buildTool));
		}
		
		if (credentials == null) { // happens if the credentials is deleted
			throw new IllegalArgumentException(Messages.RTCBuildStep_missing_credentials(crdentialsId));
		}
	}

}
