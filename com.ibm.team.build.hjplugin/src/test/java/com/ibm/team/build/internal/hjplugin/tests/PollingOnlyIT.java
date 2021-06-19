/*******************************************************************************
 * Copyright © 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCRevisionState;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl;
import com.ibm.team.build.internal.hjplugin.RTCScm.PollingOnlyData;
import com.ibm.team.build.internal.hjplugin.steps.tests.AbstractRTCBuildStepTest;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.Launcher;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PollingOnlyIT extends  AbstractRTCBuildStepTest {
	private static final String CONFIG_TOOLKIT_NAME = "config_toolkit";

	@Rule
	public JenkinsRule r = new JenkinsRule();
	
	@Before
	public void setup() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
		createSandboxDirectory();
		installBuildToolkitIntoJenkins();
	}
	
	/**
	 * If a freestyle job configured with build definition is set to polling -only,
	 * then during polling, the plugin does not perform polling but instead throws a 
	 * message that polling cannot be performed in this configuration.
	 *  
	 * @throws Exception
	 */
	@Test
	public void testFreestyleBuildDefinitionBuildError() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	
		String buildDefinitionId = "dummyBuildDefinition";
	
		// Setup a build with build definition configuration with polling Only
		// set to true
		FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinitionForPollingOnly(r, 
												buildDefinitionId);
		
		FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		Utils.dumpLogFile(build, "test", "testFreestyleBuildDefinitionBuildError", "BuildRun");

		// Build should have failed because this is an invalid configuration
		assertEquals(Result.FAILURE, build.getResult());
		assertTrue("Expected unsupported operation exception",
				build.getLog().contains("java.lang.UnsupportedOperationException: "
						+ "Polling-only is available for Pipeline jobs only.")); 
		// Run polling
		try { 
			File pollingFile = Utils.getTemporaryFile(true);
			PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
	
			// TODO Verify the error in the polling file.
			Utils.dumpFile(pollingFile, "testFreestyleBuildDefinitionBuildError");
			fail("Was excepting an UnsupportedOperation exception");
		} catch (UnsupportedOperationException exp) {
			Assert.assertTrue("Expected UnsupportedOperationException", 
					exp.getMessage().contains(Messages.Helper_polling_supported_only_for_pipeline()));
		}
	}
	
	@Test
	public void testFreestyleBuildWorkspacePollingOnlyError() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	
		String workspaceName = "dummyWorkspaceName";
	
		// Setup a build with build workspace configuration with pollingOnly
		// set to a non null object. That indicates that polling only 
		RTCScm.BuildType bt = new RTCScm.BuildType(RTCScm.BUILD_WORKSPACE_TYPE, 
									null, workspaceName, null, null);
		// Set pollingOnly to true by setting the pollingOnlyData to an 
		// object with no snapshot UUID
		bt.setPollingOnlyData(new PollingOnlyData(""));
		FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName, 
								bt);
		FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		Utils.dumpLogFile(build, "test", "testFreestyleWorkspaceBuildError", "BuildRun");

		// Build should have fail because this is an invalid configuration
		assertEquals(Result.FAILURE, build.getResult());
		assertTrue("Expected unsupported operation exception",
				build.getLog().contains("java.lang.UnsupportedOperationException: "
						+ "Polling-only is available for Pipeline jobs only."));
		
		// Run polling
		try { 
			File pollingFile = Utils.getTemporaryFile(true);
			PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
			
			// TODO Verify the error in the polling file.
			Utils.dumpFile(pollingFile, "testFreestyleBuildWorkspaceBuildError");
			fail("Was excepting an UnsupportedOperation exception");
		} catch (UnsupportedOperationException exp) {
			Assert.assertTrue("Expected UnsupportedOperationException", 
					exp.getMessage().contains(Messages.Helper_polling_supported_only_for_pipeline()));
		}
	}

	@Test
	public void testFreestyleBuildStreamPollingOnlyError() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	
		String streamName = "dummyStreamName";
	
		// Setup a build with build workspace configuration with pollingOnly
		// set to true
		RTCScm.BuildType bt = new RTCScm.BuildType(RTCScm.BUILD_STREAM_TYPE, 
									null, null, null, streamName);
		// Set pollingOnly to true by setting the pollingOnlyData to an 
		// object with no snapshot UUID
		bt.setPollingOnlyData(new PollingOnlyData(""));
		FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, Config.DEFAULT, bt); 
		FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		Utils.dumpLogFile(build, "test", "testFreestyleStreamBuildError", "BuildRun");
		
		// Build should have fail because this is an invalid configuration
		assertEquals(Result.FAILURE, build.getResult());
		assertTrue("Expected unsupported operation exception",
				build.getLog().contains("java.lang.UnsupportedOperationException: "
						+ "Polling-only is available for Pipeline jobs only."));
		// Run polling
		try { 
			File pollingFile = Utils.getTemporaryFile(true);
			PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
			
			// TODO Verify the error in the polling file.
			Utils.dumpFile(pollingFile, "testFreestyleBuildStreamBuildError");
			fail("Was excepting an UnsupportedOperation exception");
		} catch (UnsupportedOperationException exp) {
			Assert.assertTrue("Expected UnsupportedOperationException", 
					exp.getMessage().contains(Messages.Helper_polling_supported_only_for_pipeline()));
		}
	}
	
	@Test
	public void testFreestyleBuildSnapshotPollingOnlyError() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	
		String snapshotName = "dummySnapshot";
	
		// Setup a build with build workspace configuration with pollingOnly
		// set to true
		RTCScm.BuildType bt = new RTCScm.BuildType(RTCScm.BUILD_SNAPSHOT_TYPE, 
									null, null, snapshotName, null);
		bt.setPollingOnlyData(new PollingOnlyData(""));
		FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, bt); 
		FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		Utils.dumpLogFile(build, "test", "testFreestyleSnapshotBuildError", "BuildRun");

		// Build should have fail because this is an invalid configuration
		assertEquals(Result.FAILURE, build.getResult());
		assertTrue("Expected unsupported operation exception",
				build.getLog().contains("java.lang.UnsupportedOperationException: "
						+ "Polling-only is available for Pipeline jobs only."));
		
		// Run polling
		try { 
			File pollingFile = Utils.getTemporaryFile(true);
			PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
			
			// TODO Verify the error in the polling file.
			Utils.dumpFile(pollingFile, "testFreestyleBuildSnapshotBuildError");
			fail("Was excepting an UnsupportedOperation exception");
		} catch (UnsupportedOperationException exp) {
			// This is expected
			Assert.assertTrue("Expected UnsupportedOperationException", 
					exp.getMessage().contains(Messages.Helper_polling_supported_only_for_pipeline()));
		}
	}
	
	@Test
	public void testPipelineBuildDefinitionPollingOnlySuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupBuildResultContributions",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName
								String.class}, // buildDefinitionId
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						componentName, buildDefinitionId);

		try {
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(r);
			String checkoutStep = String.format(
					"node {"
					+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
					+ "buildTool: '%s', buildType: [buildDefinition: '%s', "
					+ "pollingOnly: true, value: 'buildDefinition'], "
					+ "credentialsId: '%s', "
					+ "overrideGlobal: true, "
					+ "serverURI: '%s', timeout: 480])"
					+ "}",
					CONFIG_TOOLKIT_NAME, buildDefinitionId, credId, 
					Config.DEFAULT.getServerURI());
			setupFlowDefinition(j, checkoutStep);
			
			// Run the  build once before starting to poll
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "runBuild", "testPipelineBuildDefinitionPollingOnlySuccess", "log");
			
			// Build should have succeeded but accept/load would have been skipped
			assertEquals(Result.SUCCESS, run.getResult());
			assertTrue("Expected accept/load skipped message",
					run.getLog().contains("Polling-only is selected. "
							+ "Accept and load of the build workspace will be skipped."));

			Launcher launcher = r.createLocalLauncher();
			Collection<? extends SCM> scms = j.getSCMs();
			RTCScm rtcScm = null;
			for (SCM scm : scms) {
				if (scm instanceof RTCScm) {
					rtcScm = (RTCScm) scm;
					break;
				}
			}
			assertNotNull(Integer.toString(scms.size()), rtcScm);
			File f = File.createTempFile("pollingTestPipelineBuildDefinitionPollingOnlySuccess", "log");
			PollingResult pr = rtcScm.compareRemoteRevisionWith(j, launcher, null, 
						new StreamTaskListener(f, Charset.forName("UTF-8")),
						new RTCRevisionState(BigInteger.ZERO));
			assertTrue("polling should say significant changes", Change.SIGNIFICANT.equals(pr.change));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	} 
	
	@Test
	public void testPipelineBuildWorkspacePollingOnlySuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupBuildResultContributions",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName
								String.class}, // buildDefinitionId
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						componentName, buildDefinitionId);

		try {
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(r);
			String checkoutStep = String.format(
					"node {"
					+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
					+ "buildTool: '%s', buildType: [buildWorkspace: '%s', "
					+ "pollingOnly: true, value: 'buildWorkspace'], "
					+ "credentialsId: '%s', "
					+ "overrideGlobal: true, "
					+ "serverURI: '%s', timeout: 480])"
					+ "}",
					CONFIG_TOOLKIT_NAME, workspaceName, credId, 
					Config.DEFAULT.getServerURI());
			setupFlowDefinition(j, checkoutStep);
			
			// Run the  build once before starting to poll
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "runBuild", "testPipelineBuildWorkspacePollingOnlySuccess", "log");
			
			// Build should have succeeded but accept/load would have been skipped
			assertEquals(Result.SUCCESS, run.getResult());
			assertTrue("Expected accept/load skipped message",
					run.getLog().contains("Polling-only is selected. "
							+ "Accept and load of the build workspace will be skipped."));

			Launcher launcher = r.createLocalLauncher();
			Collection<? extends SCM> scms = j.getSCMs();
			RTCScm rtcScm = null;
			for (SCM scm : scms) {
				if (scm instanceof RTCScm) {
					rtcScm = (RTCScm) scm;
					break;
				}
			}
			assertNotNull(Integer.toString(scms.size()), rtcScm);
			File f = File.createTempFile("pollingTestPipelineBuildWorkspacePollingOnlySuccess", "log");
			PollingResult pr = rtcScm.compareRemoteRevisionWith(j, launcher, null, 
						new StreamTaskListener(f, Charset.forName("UTF-8")),
						new RTCRevisionState(BigInteger.ZERO));
			assertTrue("polling should say significant changes", Change.SIGNIFICANT.equals(pr.change));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	@Test
	public void testPipelineBuildStreamPollingOnlyFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}	
		
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		try {
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(r);
			String checkoutStep = String.format(
					"node {"
					+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
					+ "buildTool: '%s', buildType: [buildStream: '%s', "
					+ "pollingOnly: true, value: 'buildStream'], "
					+ "credentialsId: '%s', "
					+ "overrideGlobal: true, "
					+ "serverURI: '%s', timeout: 480])"
					+ "}",
					CONFIG_TOOLKIT_NAME, streamName, credId, 
					Config.DEFAULT.getServerURI());
			setupFlowDefinition(j, checkoutStep);
			
			// Run the  build once before starting to poll
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "runBuild", 
					"testPipelineBuildStreamPollingOnlyFailure", "log");
			String logContents = run.getLog();
			org.junit.Assert.assertTrue("Expected UnSupportedOperationException", 
					logContents.contains("java.lang.UnsupportedOperationException: Polling-only is "
							+ "available for build definition and repository workspace configurations only."));
			
			// Since RTCScm fails in the build, Jenkins does not create the SCM instance 
			// in the job. Let us instantiate our own RTCM for this purpose
			Launcher launcher = r.createLocalLauncher();
			BuildType bt = new BuildType(RTCScm.BUILD_STREAM_TYPE, null, null, null, streamName);
			
			// Set pollingOnly to true by setting the pollingOnlyData to an 
			// object with no snapshot UUID
			bt.setPollingOnlyData(new PollingOnlyData(""));
			
			RTCScm rtcScm = new RTCScm(true, CONFIG_TOOLKIT_NAME, 
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getTimeout(),
					null, null, null, credId, bt, false);
			
			try {
				File f = File.createTempFile("pollingTestPipelineBuildStreamPollingOnlyFailure", "log");
				PollingResult pr = rtcScm.compareRemoteRevisionWith(j, launcher, null, 
							new StreamTaskListener(f, Charset.forName("UTF-8")),
							new RTCRevisionState(BigInteger.ZERO));
				fail("Polling should fail with UnsupportedOperation Exception");
			} catch (UnsupportedOperationException exp) {
				Assert.assertTrue("Expected UnsupportedOperationException", 
						exp.getMessage().contains(Messages.Helper_polling_supported_only_for_buildTypes()));
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	@Test
	public void testPipelineBuildSnapshotPollingOnlyFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(loginInfo, 
				workspaceName, snapshotName, componentName, testingFacade);
		try {
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(r);
			String checkoutStep = String.format(
					"node {"
					+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
					+ "buildTool: '%s', buildType: [buildSnapshot: '%s', "
					+ "pollingOnly: true, value: 'buildSnapshot'], "
					+ "credentialsId: '%s', "
					+ "overrideGlobal: true, "
					+ "serverURI: '%s', timeout: 480])"
					+ "}",
					CONFIG_TOOLKIT_NAME, snapshotName, credId, 
					Config.DEFAULT.getServerURI());
			setupFlowDefinition(j, checkoutStep);
			
			// Run the  build once before starting to poll
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "runBuild", "testPipelineBuildSnapshotPollingOnlyFailure", "log");
			String logContents = run.getLog();
			org.junit.Assert.assertTrue("Expected UnSupportedOperationException", 
					logContents.contains("java.lang.UnsupportedOperationException: Polling-only is "
							+ "available for build definition and repository workspace configurations only."));
			
			Launcher launcher = r.createLocalLauncher();
			Collection<? extends SCM> scms = j.getSCMs();
			BuildType bt = new BuildType(RTCScm.BUILD_SNAPSHOT_TYPE, null, null, snapshotName, null);
			
			// Set pollingOnly to true by setting the pollingOnlyData to an 
			// object with no snapshot UUID
			bt.setPollingOnlyData(new PollingOnlyData(""));
						
			RTCScm rtcScm = new RTCScm(true, CONFIG_TOOLKIT_NAME, 
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getTimeout(),
					null, null, null, credId, bt, false);
			
			try {
				File f = File.createTempFile("pollingTestPipelineBuildSnapshotPollingOnlyFailure", "log");
				PollingResult pr = rtcScm.compareRemoteRevisionWith(j, launcher, null, 
							new StreamTaskListener(f, Charset.forName("UTF-8")),
							new RTCRevisionState(BigInteger.ZERO));
				fail("Polling should fail with UnsupportedOperation Exception");
			} catch (UnsupportedOperationException exp) {
				Assert.assertTrue("Expected UnsupportedOperationException", 
					exp.getMessage().contains(Messages.Helper_polling_supported_only_for_buildTypes()));
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	@Test
	public void testDoValidateBuildDefinitionWithSCMForPollingOnlyFreestyleFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// Since this is a freestyle job, validation fails with error.
		// TODO complete this
	}
	/**
	 * If polling only is enabled, then validation of build definition of unknown template type   
	 * should succeed.
	 * 
	 * The test is done with toolkit.
	 * @throws Exception
	 */
	@Test 
	public void testDoValidateBuildDefinitionWithSCMForPollingOnlyPipelineWithToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperDoValidateBuildDefinitionWithSCMForPollingOnlyPipeline(true);
	}
	
	/**
	 * If polling only is enabled, then validation of build definition of unknown template type   
	 * should succeed.
	 * 
	 * The test is done without toolkit.
	 */
	@Test
	public void testDoValidateBuildDefinitionWithSCMForPollingOnlyPipelineWithoutToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperDoValidateBuildDefinitionWithSCMForPollingOnlyPipeline(false);
	}
	
	private void helperDoValidateBuildDefinitionWithSCMForPollingOnlyPipeline(boolean useToolkit) throws Exception {
		Map<String, String> setupArtifacts = null;
		try {
			String buildDefinitionId = getBuildDefinitionUniqueName();
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			// Setup a build definition and engine with unknown template
			setupArtifacts = setupUnknownTemplateBuildDefinitionAndEngineWithSCM(buildDefinitionId,
											workspaceName, componentName);
			
			Job<?,?> j = r.createProject(WorkflowJob.class, "Testing");
			DescriptorImpl descriptor = (DescriptorImpl)j.
							getDescriptorByName(RTCScm.class.getName());
			
			// Set polling-only to true - validation should succeed
			boolean pollingOnly = true;
			FormValidation v = descriptor.doValidateBuildDefinitionConfiguration(j, 
					"true", CONFIG_TOOLKIT_NAME, Config.DEFAULT.getServerURI(),
					Integer.toString(Config.DEFAULT.getTimeout()), Config.DEFAULT.getUserId(), 
					Config.DEFAULT.getPassword(), null, 
					null, Boolean.toString(!useToolkit), 
					buildDefinitionId, Boolean.toString(pollingOnly));
			assertTrue(v.getMessage(), v.kind.equals(FormValidation.Kind.OK));

			// Set polling only to false - validation fails because 
			// the build definition is not of type Hudson/Jenkins
			pollingOnly = false;
			v = descriptor.doValidateBuildDefinitionConfiguration(j, 
					"true", CONFIG_TOOLKIT_NAME, Config.DEFAULT.getServerURI(),
					Integer.toString(Config.DEFAULT.getTimeout()), Config.DEFAULT.getUserId(), 
					Config.DEFAULT.getPassword(), null, 
					null, Boolean.toString(!useToolkit), 
					buildDefinitionId, Boolean.toString(pollingOnly));
			assertTrue((v.kind.toString() + ((v.getMessage() == null) ? "null" : v.getMessage())),
								v.kind.equals(FormValidation.Kind.ERROR));
			assertTrue(v.getMessage(), "Build definition is not a Hudson/Jenkins build definition".
								equals(v.getMessage()));
		} finally {
			Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
		}
		
		
	}
	
	/**
	 * Validate that a build definition with engine of unknown template type without SCM fails with 
	 * a message "Jazz SCM not setup" when polling only is enabled.
	 * 
	 * If polling only is off, then the error message will be similar to "Build definition is not of 
	 * Jenkins type".
	 * 
	 * The tests are done with toolkit.
	 * 
	 * @throws Exception
	 */
	@Test 
	public void testDoValidateBuildDefinitionWithoutSCMForPollingOnlyPipelineWithToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// withToolkit
		helperValidationBuildDefinitionWithoutSCMForPollingOnlyPipeline(true);
	}
	
	/**
	 * Validate that a build definition with engine of unknown template type without SCM fails with 
	 * a message "Jazz SCM not setup" when polling only is enabled.
	 * 
	 * If polling only is off, then the error message will be similar to "Build definition is not of 
	 * Jenkins type" 
	 * 
	 * The tests are done without toolkit.
	 * 
	 * @throws Exception
	 */
	@Test 
	public void testDoValidateBuildDefinitionWithoutSCMForPollingOnlyPipelineWithoutToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// withoutToolkit
		helperValidationBuildDefinitionWithoutSCMForPollingOnlyPipeline(false);
		
	}
	
	private void helperValidationBuildDefinitionWithoutSCMForPollingOnlyPipeline(boolean useToolkit)
				throws Exception {
		
		Map<String, String> setupArtifacts = null;
		Job<?,?> j = r.createProject(WorkflowJob.class, "Testing");
		try {
			String buildDefinitionId = getBuildDefinitionUniqueName();
			setupArtifacts = setupUnknownTemplateBuildDefinitionAndEngineWithoutSCM(buildDefinitionId,
													true);

			DescriptorImpl descriptor = (DescriptorImpl)j.getDescriptorByName(RTCScm.class.getName());
			boolean pollingOnly = true;
			FormValidation v = descriptor.doValidateBuildDefinitionConfiguration(j, 
					"true", CONFIG_TOOLKIT_NAME, Config.DEFAULT.getServerURI(),
					Integer.toString(Config.DEFAULT.getTimeout()),
					Config.DEFAULT.getUserId(), 
					Config.DEFAULT.getPassword(), null, 
					null, Boolean.toString(!useToolkit), 
					buildDefinitionId, Boolean.toString(pollingOnly));
			assertTrue(v.getMessage(), v.kind.equals(FormValidation.Kind.ERROR));
			assertTrue(v.getMessage(), v.getMessage().
					contains("Build definition does not have a Jazz Source Control option specified"));
			
			pollingOnly = false;
			v = descriptor.doValidateBuildDefinitionConfiguration(j, 
					"true", CONFIG_TOOLKIT_NAME, Config.DEFAULT.getServerURI(),
					Integer.toString(Config.DEFAULT.getTimeout()), Config.DEFAULT.getUserId(), 
					Config.DEFAULT.getPassword(), null,
					null, Boolean.toString(!useToolkit), 
					buildDefinitionId, Boolean.toString(pollingOnly));
			assertTrue(v.getMessage(), v.kind.equals(FormValidation.Kind.ERROR));
			assertTrue(v.getMessage(), "Build definition is not a Hudson/Jenkins build definition".
					equals(v.getMessage()));
			
		} finally {
			Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
		}
	}
	/**
	 * Setup a build definition and engine of unknown template type. 
	 * Add the build engine to the supported list of the build definition
	 *  
	 * @param buildDefinitionId       The id of the build engine
	 * @param setupBuildEngine	      Whether to setup a build engine or not
	 * @return                        A map containing UUIDs of all the artifacts created
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> setupUnknownTemplateBuildDefinitionAndEngineWithoutSCM(String buildDefinitionId, 
									boolean setupBuildEngine) throws Exception {
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
				.invoke("setupBuildDefinitionWithoutSCMWithQueuedBuild",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildDefinition,
								String.class, // processAreaName
								boolean.class, // setupBuildEngine
								Map.class, // buildProperties
								boolean.class, //  requestBuild
								String.class, // build definition template type
								String.class}, // build engine template type
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), 
						buildDefinitionId, null, 
						setupBuildEngine, null, false, 
						"mybuilddefinition.template", "mybuildengine.template");
	}

	/**
	 * Setup a build definition and engine with SCM contribution and some build results.
	 * The build definition and engine are of unknown template type. 
	 * 
	 * @param buildDefinitionId The id of the build definition
	 * @param workspaceName The name of the build workspace
	 * @param componentName The name of the component in the build workspace
	 * @return  A map containing artifact ids.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> setupUnknownTemplateBuildDefinitionAndEngineWithSCM(String buildDefinitionId, 
								String workspaceName, String componentName) throws Exception {
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
				.invoke("setupBuildResultContributions",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName
								String.class,  // buildDefinitionId
								boolean.class, 
								String.class, // build definition template type 
								String.class}, // build engine template type
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						componentName, buildDefinitionId, true, 
						"mybuilddefinition.template", "mybuildengine.template");
	}
}
