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
package com.ibm.team.build.internal.hjplugin.steps.tests;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.Action;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tools.ToolProperty;

public abstract class AbstractRTCBuildStepTest extends AbstractTestCase {
	
	protected static final String CONFIG_TOOLKIT_NAME = "config_toolkit";

	protected String getLog(WorkflowRun r) throws IOException {
		File f = r.getLogFile();
		StringWriter w = new StringWriter(250);
		Files.copy(f, Charset.forName("utf-8"), w);
		return w.toString();
	}
	
	protected void setupValidCredentials(String credId) {
		Credentials c = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credId,
				"test", Config.DEFAULT.getUserId(), Config.DEFAULT.getPassword());
		SystemCredentialsProvider.getInstance().getCredentials().add(c);
	}
	
	protected WorkflowJob setupWorkflowJob(JenkinsRule r) throws Exception {
		WorkflowJob j = r.createProject(WorkflowJob.class);
		j.addProperty(new ParametersDefinitionProperty(
				new StringParameterDefinition("com.ibm.team.build.debug", "true")));
		return j;
	}
	
	protected void setupFlowDefinition(WorkflowJob j, String contents) {
		CpsFlowDefinition d = new CpsFlowDefinition("node { echo \"hello world\" \n " + 
				contents + "\n }", true);
		j.setDefinition(d);
	}

	protected WorkflowRun requestJenkinsBuild(WorkflowJob j) throws InterruptedException, ExecutionException {
		List<ParametersAction> pActions = new ArrayList<ParametersAction> ();
		pActions.add(new ParametersAction(new StringParameterValue("com.ibm.team.build.debug", "true")));
		QueueTaskFuture<WorkflowRun> future = j.scheduleBuild2(0, pActions.toArray(new Action[0]));
		while(!future.isDone()) {
			// Intentionally empty
		}
		WorkflowRun r = future.get();
		return r;
	}
	protected void helperMissingBuildToolkit(JenkinsRule rule, String prefix, String rtcBuildStepFragment)
			throws Exception, InterruptedException, ExecutionException, IOException {
		// Empty build toolkit name
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
	
		WorkflowJob j = setupWorkflowJob(rule);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s'," + rtcBuildStepFragment + ","
				+ " timeout: 480", "", credId, Config.DEFAULT.getServerURI());
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildTool()));
		Utils.dumpLogFile(run,  prefix, "missingBuildTool", ".log");
		
		// Build toolkit name is not a valid build toolkit
		String unknownBuildToolkit = "unknownBuildToolkit";
	    j = setupWorkflowJob(rule);
		rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s'," + rtcBuildStepFragment + ","
				+ " timeout: 480", unknownBuildToolkit, credId, Config.DEFAULT.getServerURI());
		setupFlowDefinition(j, rtcBuildStep);
		
		run = requestJenkinsBuild(j);
		
		log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildToolkit(unknownBuildToolkit)));
		Utils.dumpLogFile(run, prefix, "missingBuildToolkitPath", ".log");
	}

	protected void installBuildToolkitIntoJenkins() {
		RTCBuildToolInstallation tool = new RTCBuildToolInstallation(CONFIG_TOOLKIT_NAME, 
				Config.DEFAULT.getToolkit(), Collections.<ToolProperty<?>> emptyList());
		tool.getDescriptor().setInstallations(tool);
	}
	
	protected void helperMissingCreds(JenkinsRule rule, String prefix, String taskFragment)
			throws Exception, InterruptedException, ExecutionException, IOException {
		WorkflowJob j = setupWorkflowJob(rule);
		// No creds provided
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s'," + taskFragment + ","
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, "", Config.DEFAULT.getServerURI());
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_credentials_id()));
		Utils.dumpLogFile(run, prefix, "emptyCreds", ".log");
		
		// Non existent creds
		j = setupWorkflowJob(rule);
		rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s'," + taskFragment + ","
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, "abcd", Config.DEFAULT.getServerURI());
		setupFlowDefinition(j, rtcBuildStep);
		
		run = requestJenkinsBuild(j);
		
		log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_credentials("abcd")));
		Utils.dumpLogFile(run, prefix, "invalidCreds", ".log");
	}
	
	protected void helperTestNoServerURI(JenkinsRule rule, String prefix, String taskFragment)
			throws Exception, InterruptedException, ExecutionException, IOException {
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
	
		WorkflowJob j = setupWorkflowJob(rule);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s'," + taskFragment +","
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, "");
		setupFlowDefinition(j, rtcBuildStep);
		WorkflowRun run = requestJenkinsBuild(j);
		String log = getLog(run);
		// The default server URI is https://localhost:9443/ccm
		Assert.assertTrue(log, log.contains("CRJAZ1371E The following URL cannot be reached:"));
		Assert.assertTrue(log, log.contains("localhost:9443"));
		Utils.dumpLogFile(run, prefix, "noServerURI", ".log");
	}

	/**
	 * Utility method to setup a build definition and then request a build. 
	 * This build request is queued and the corresponding build result is in 
	 * NOT_STARTED state
	 * 
	 * @param prefix               A prefix for the artifacts to be created
	 * @return                     A map containing UUIDs of all the artifacts created
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, String> setupBuildDefinitionWithoutSCMAndResult(String prefix) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String buildDefinitionId = getBuildDefinitionUniqueName(prefix);	
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
				.invoke("setupBuildDefinitionWithoutSCMWithQueuedBuild",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildDefinitionId
								String.class, // processAreaName
								boolean.class, // createBuildEngine
								Map.class, // buildProperties
								boolean.class, // requestBuild
								String.class,  // build definition config element id
								String.class}, // build element config element id  
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						buildDefinitionId, null,
						true, null, true, null, null);
	}

	/**
	 * Complete the given build result UUID
	 * 
	 * @param buildResultUUID
	 */
	protected void completeBuild(String buildResultUUID) throws Exception {
		try {
			Config defaultC = Config.DEFAULT;
			RTCLoginInfo loginInfo = defaultC.getLoginInfo();
			RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
			testingFacade
					.invoke("completeBuild", 
							new Class[] { String.class, // serverURI
									String.class, // userId
									String.class, // password
									int.class, // timeout
									String.class, // buildResultUUID
									String.class }, // buildEngineUUID
							defaultC.getServerURI(), loginInfo.getUserId(), 
							loginInfo.getPassword(), defaultC.getTimeout(), 
							buildResultUUID, null);
		} catch (Exception e) {
			File f = File.createTempFile("complete", "Build");
			writeExpDetails(f, e);
			throw e;
		}
		
	}

	/**
	 * Utility method to abandon a build result
	 * 
	 * @param buildResultUUID      The build result UUID to cancel
	 * @throws Exception
	 */
	protected void abandonBuild(final String buildResultUUID) throws Exception {
		try {
			Config defaultC = Config.DEFAULT;
			RTCLoginInfo loginInfo = defaultC.getLoginInfo();
			RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
			testingFacade
					.invoke("abandonBuild", 
							new Class[] { String.class, // serverURI
									String.class, // userId
									String.class, // password
									int.class, // timeout
									String.class }, 
							defaultC.getServerURI(), loginInfo.getUserId(), 
							loginInfo.getPassword(), defaultC.getTimeout(), 
							buildResultUUID);
		} catch (Exception e) {
			File f = File.createTempFile("abandon", "Build");
			writeExpDetails(f, e);
			throw e;
		}
	}

	/**
	 * Cancel the given build result.
	 * 
	 * @param buildResultUUID      The build result UUID to cancel
	 * @throws Exception
	 */
	protected void cancelBuild(final String buildResultUUID) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		testingFacade
				.invoke("cancelBuild", 
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								String.class }, 
						defaultC.getServerURI(), loginInfo.getUserId(), 
						loginInfo.getPassword(), defaultC.getTimeout(), 
						buildResultUUID);
	}
}
