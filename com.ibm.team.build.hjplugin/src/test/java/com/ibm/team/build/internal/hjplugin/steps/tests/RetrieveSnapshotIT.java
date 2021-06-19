package com.ibm.team.build.internal.hjplugin.steps.tests;
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

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Map;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.steps.RetrieveSnapshotStepExecution;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

/**
 * Integration tests for {@link RetrieveSnapshotStepExecution}
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RetrieveSnapshotIT extends AbstractRTCBuildStepTest {

	private static final String retrieveSnapshotTaskFragment = 
			"task : [buildResultUUID: \"Test1\", name: 'retrieveSnapshot']";
	
	private static final String prefix ="retrieveSnapshot";

	@Rule
	public JenkinsRule rule = new JenkinsRule();

	@Rule
	public TemporaryFolder scratchFolder = new TemporaryFolder();
	
	@Before
	public void setup() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		createSandboxDirectory();
		installBuildToolkitIntoJenkins();
	}

	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	}
	
	@Test
	public void testRetrieveSnapshotNoServerURI() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestNoServerURI(rule, prefix, retrieveSnapshotTaskFragment);
	}

	@Test
	public void testRetrieveSnapshotMissingCreds() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingCreds(rule, prefix, retrieveSnapshotTaskFragment);
	}

	@Test
	public void testRetrieveSnapshotMissingBuildToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingBuildToolkit(rule, prefix, retrieveSnapshotTaskFragment);
	}

	/**
	 * Validate different cases of buildResultUUUID parameter
	 * 1. Empty build result UUID
	 * 2. Missing build result UUID
	 * 3. Invalid build result UUID (format incorrect)
	 * @throws Exception
	 */
	@Test
	public void testRetrieveSnapshotBuildResultUUIDValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		// Missing build result UUID
		{
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildResultUUID: '%s', name: 'retrieveSnapshot'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
					""); // Missing build result UUID
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "testRetrieveSnapshotBuildResultUUIDValidation", "MissingBuildResultUUID", ".log");

			String log = getLog(run);
			Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildResultUUID()));
		}
		// No build result UUID
		{
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
					+ " '%s', task: [name: 'retrieveSnapshot'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI());
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "testRetrieveSnapshotBuildResultUUIDValidation", "NoBuildResultUUID", ".log");

			String log = getLog(run);
			Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildResultUUID()));
		}
		// Build result UUID is invalid
		{
			String invalidBuildResultUUID = "test";
			String exceptionMessage = 
					String.format("The value \"%s\" provided for build result UUID is invalid.",
							invalidBuildResultUUID);
			
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildResultUUID: '%s', name: 'retrieveSnapshot'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
					invalidBuildResultUUID); // invalid build result UUID
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "testRetrieveSnapshotBuildResultUUIDValidation", "invalidBuildResultUUID", ".log");	
	
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(exceptionMessage));
		}
	}
	
	/**
	 * Build result is non existent.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRetrieveSnapshotDetailsBuildResultUUIDNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		Map<String, String> setupArtifacts = 
					setupBuildDefinitionWithoutSCMAndResult("testRetrieveSnapshotDetailsBuildResultUUIDNotFound");
		// Build result UUID does not exist
		try {
			String invalidBuildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDDEFINITION_ITEM_ID);
			String exceptionMessage = 
					String.format("Build Result with id \"%s\" could not be found. It may have been deleted.",
							invalidBuildResultUUID);
			
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildResultUUID: '%s', name: 'retrieveSnapshot'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
					invalidBuildResultUUID); // invalid build result UUID
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "testRetrieveSnapshotDetailsBuildResultUUIDNotFound", "InvalidBuildResultUUID", ".log");	
	
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(exceptionMessage));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}

	/**
	 * Build result has a snapshot contribution and the snapshot exists.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRetrieveSnapshotDetailsSnapshotFoundInBuildResult() throws Exception {
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
			// Setup
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(rule, buildDefinitionId);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.dumpLogFile(build, "test", "RetrieveSnapshotDetailsSnapshotFoundInBuildResult", "Freestyle");

			// Get the build result UUID from buidl
			String buildResultUUID = Utils.getBuildResultUUID(build);
			
			// Use the changelog parser to get the build snapshot UUID
			File changelogFile = new File(build.getRootDir(), "changelog.xml"); //$NON-NLS-1$
			RTCChangeLogParser parser = new RTCChangeLogParser();
			RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changelogFile);
			String snapshotUUID = result.getBaselineSetItemId();
			String snapshotName = result.getBaselineSetName();
			
			// Make sure that the items that we are going to compare with are not empty
			assertNotNull(buildResultUUID);
			assertNotNull(snapshotUUID);
			assertNotNull(snapshotName);

			// Since setting up the freestyle job overwrote the buildtoolkit configuration, 
			// rerun the method to install a toolkit with a name for the pipeline job
			installBuildToolkitIntoJenkins();
			
			// Act
			// Run a workflow build and check whether the log has the snapshotUUID that we expect
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("def ret = rtcBuild buildTool: '%s', credentialsId: '%s',"
					+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'retrieveSnapshot' ]"
					+ ", timeout: 480 \n echo \"${ret.snapshotUUID}\" \n echo \"${ret.snapshotName}\"",
					CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), buildResultUUID);
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "test", "RetrieveSnapshotDetailsSnapshotFoundInBuildResult", "Pipeline");
			
			// Validate that the log has the snapshot UUID we retrieved from the changelog
			String log = getLog(run);
			Assert.assertTrue(String.format("Expecting snapshot UUID %s", snapshotUUID), 
					log.contains(snapshotUUID));
			Assert.assertTrue(String.format("Expecting snapshot name %s", snapshotName), 
					log.contains(snapshotName));
			
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	/**
	 * The build result does not have a snapshot contribution.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRetrieveSnapshotDetailsSnapshotNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		Map<String, String> setupArtifacts = 
					setupBuildDefinitionWithoutSCMAndResult("testRetrieveSnapshotDetailsSnapshotNotFound");
		try {
			// Get the build result UUID and complete it.
			String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
			completeBuild(buildResultUUID);
			
			// Setup a workflow run that outputs a message "No snapshot found"
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("def ret = rtcBuild buildTool: '%s', credentialsId: '%s',"
					+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'retrieveSnapshot' ]"
					+ ", timeout: 480 \n if (ret.snapshotUUID.isEmpty()) { echo \"Snapshot UUID was not found\" }",
					CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), buildResultUUID);
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "test", "RetrieveSnapshotDetailsSnapshotNotFound", "Pipeline");
			
			// Validate that the log has the message "Snapshot UUID was not found"
			String log = getLog(run);
			Assert.assertTrue("Expecting snapshot UUID was not found message", 
					log.contains("Snapshot UUID was not found"));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	/**
	 * The build result has a snapshot contribution but the contribution has been deleted
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRetrieveSnapshotDetailsSnapshotDeleted() throws Exception {
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
			// Setup
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(rule, buildDefinitionId);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.dumpLogFile(build, "test", "testRetrieveSnapshotDetailsSnapshotDeleted", "Freestyle");

			// Get the build result UUID from buidl
			String buildResultUUID = Utils.getBuildResultUUID(build);
			
			// Use the changelog parser to get the build snapshot UUID
			File changelogFile = new File(build.getRootDir(), "changelog.xml"); //$NON-NLS-1$
			RTCChangeLogParser parser = new RTCChangeLogParser();
			RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changelogFile);
			String snapshotUUID = result.getBaselineSetItemId();
			String snapshotName = result.getBaselineSetName();
			
			// Make sure that the items that we are going to compare with are not empty
			assertNotNull(buildResultUUID);
			assertNotNull(snapshotUUID);
			assertNotNull(snapshotName);

			// Delete the snapshot
			testingFacade.invoke("deleteSnapshotFromWorkspace",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class}, // snapshotUUID
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), workspaceName,
					snapshotUUID);

			// Since setting up the freestyle job overwrote the buildtoolkit configuration, 
			// rerun the method to install a toolkit with a name for the pipeline job
			installBuildToolkitIntoJenkins();

			// Act
			// Run a workflow build and check whether the log has a message 
			// "Snapshot UUID was not found"
			String credId = "myCreds" + System.currentTimeMillis();
			setupValidCredentials(credId);
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("def ret = rtcBuild buildTool: '%s', credentialsId: '%s',"
					+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'retrieveSnapshot' ]"
					+ ", timeout: 480 \n if (ret.snapshotUUID.isEmpty()) { echo \"Snapshot UUID was not found\" }",
					CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), buildResultUUID);
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			Utils.dumpLogFile(run, "test", "testRetrieveSnapshotDetailsSnapshotDeleted", "Pipeline");
			
			// Validate that the log has the message "Snapshot UUID was not found"
			String log = getLog(run);
			Assert.assertTrue("Expecting snapshot UUID was not found message", 
					log.contains("Snapshot UUID was not found"));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
}
