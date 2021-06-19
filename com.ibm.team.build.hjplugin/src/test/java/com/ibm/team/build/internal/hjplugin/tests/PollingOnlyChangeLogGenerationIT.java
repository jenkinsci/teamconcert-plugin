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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.steps.tests.AbstractRTCBuildStepTest;
import com.ibm.team.build.internal.hjplugin.tests.utils.LoadOptions;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;

public class PollingOnlyChangeLogGenerationIT extends  AbstractRTCBuildStepTest {
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

	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildDefinition() throws Exception {
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

		String workspaceItemId = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		try {
			WorkflowJob j = setupWorkflowJob(r);
			WorkflowRun run1= helperSetupBuildForBuildDefinitionWithValidation(testingFacade, j, 
						buildDefinitionId, workspaceName, workspaceItemId, "testPollingOnlyChangeLogGenerationBuildDefinition",
						"1", null);
			assertTrue(run1.getResult().equals(Result.SUCCESS));
			WorkflowRun run2 = helperSetupBuildForBuildDefinitionWithValidation(testingFacade, j, 
						buildDefinitionId, workspaceName, workspaceItemId, "testPollingOnlyChangeLogGenerationBuildDefinition",
						"2", run1);
			assertTrue(run2.getResult().equals(Result.SUCCESS));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildWorkspace() throws Exception {
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

		String workspaceItemId = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		try {
			WorkflowJob j = setupWorkflowJob(r);
			
			WorkflowRun run1= helperSetupBuildForBuildWorkspaceWithValidation(testingFacade, j, 
							workspaceName, workspaceItemId, "testPollingOnlyChangeLogGenerationBuildWorkspace", 
							"1", null);
			assertTrue(run1.getResult().equals(Result.SUCCESS));

			WorkflowRun run2 = helperSetupBuildForBuildWorkspaceWithValidation(testingFacade, j, 
							workspaceName, workspaceItemId, "testPollingOnlyChangeLogGenerationBuildWorkspace",
							"2", run1);
			assertTrue(run2.getResult().equals(Result.SUCCESS));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	@Test
	public void testPollingOnlyChangeLogGenerationBuildDefinitionWithChanges() throws Exception {
		// TODO not complete
	}
	
	@Test
	public void testPollingOnlyChangeLogGenerationBuildWorkspaceWithChanges() throws Exception {
		// TODO not complete
	}

	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildDefinitionPreviousSnapshotNotFound() throws Exception {
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

		String workspaceItemId = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		try {
			WorkflowJob j = setupWorkflowJob(r);
			WorkflowRun run1= helperSetupBuildForBuildDefinitionWithValidation(testingFacade, j, 
						buildDefinitionId, workspaceName, workspaceItemId,
						"testPollingOnlyChangeLogGenerationBuildDefinitionPreviousSnapshotNotFound", "1", null);
			assertTrue(run1.getResult().equals(Result.SUCCESS));
			
			// Get the snapshot from run1 and delete the snapshot
			RTCBuildResultAction action = getBuildResultAction(run1);
			String snapshotUUID = action.getBuildProperties().get("team_scm_snapshotUUID");
			
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
			
			// Since there is no previous snapshot, the changelog for the run2 will not have 
			// previous snapshot UUID
			WorkflowRun run2 = helperSetupBuildForBuildDefinitionWithValidation(testingFacade, j, 
						buildDefinitionId, workspaceName, workspaceItemId, 
						"testPollingOnlyChangeLogGenerationBuildDefinitionPreviousSnapshotNotFound", "2", null);
			assertTrue(run2.getResult().equals(Result.SUCCESS));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildWorkspacePreviousSnapshotNotFound() throws Exception {
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

		String workspaceItemId = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		try {
			WorkflowJob j = setupWorkflowJob(r);
			WorkflowRun run1= helperSetupBuildForBuildWorkspaceWithValidation(testingFacade, j, 
						workspaceName, workspaceItemId,
						"testPollingOnlyChangeLogGenerationBuildWorkspacePreviousSnapshotNotFound", "1", null);
			assertTrue(run1.getResult().equals(Result.SUCCESS));
			
			// Get the snapshot from run1 and delete the snapshot
			RTCBuildResultAction action = getBuildResultAction(run1);
			String snapshotUUID = action.getBuildProperties().get("team_scm_snapshotUUID");
			
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
			
			// Since there is no previous snapshot, the changelog for the run2 will not have 
			// previous snapshot UUID
			WorkflowRun run2 = helperSetupBuildForBuildWorkspaceWithValidation(testingFacade, j, 
						workspaceName, workspaceItemId, 
						"testPollingOnlyChangeLogGenerationBuildWorkspacePreviousSnapshotNotFound", "2", null);
			assertTrue(run2.getResult().equals(Result.SUCCESS));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildWorkspaceNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String snapshotName = getSnapshotUniqueName();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		WorkflowJob j = setupWorkflowJob(r);
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupTestBuildSnapshotConfiguration",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName,
								String.class, // componentName
								String.class, // workspacePrefix
								String.class}, // hjPath
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, "testWksp", 
						getSandboxDir().getAbsolutePath());
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		String invalidWorkspaceName = getRepositoryWorkspaceUniqueName();
		String checkoutStep = String.format(
				"node {"
				+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
				+ "buildTool: '%s', buildType: [buildWorkspace: '%s', "
				+ "pollingOnlyData: [snapshotUUID: '%s'], value: 'buildWorkspace'], "
				+ "credentialsId: '%s', "
				+ "overrideGlobal: true, "
				+ "serverURI: '%s', timeout: 480])"
				+ "}",
				CONFIG_TOOLKIT_NAME, invalidWorkspaceName, 
				snapshotUUID, 
				credId, 
				Config.DEFAULT.getServerURI()); 
		j.setDefinition(new CpsFlowDefinition(checkoutStep));
		WorkflowRun run = requestJenkinsBuild(j);
		Utils.dumpLogFile(run, "testPollingOnlyChangeLogGenerationBuildWorkspaceNotFound",
					"1", ".log");
		
		// Assert that the build failed.
		assertTrue(run.getResult().equals(Result.FAILURE));
	}
	
	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildDefinitionNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String snapshotName = getSnapshotUniqueName();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		WorkflowJob j = setupWorkflowJob(r);
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupTestBuildSnapshotConfiguration",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName,
								String.class, // componentName
								String.class, // workspacePrefix
								String.class}, // hjPath
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, "testWksp", 
						getSandboxDir().getAbsolutePath());
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		String invalidBuildDefinitionId = getBuildDefinitionUniqueName();
		String checkoutStep = String.format(
				"node {"
				+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
				+ "buildTool: '%s', buildType: [buildDefinition: '%s', "
				+ "pollingOnlyData: [snapshotUUID: '%s'], value: 'buildDefinition'], "
				+ "credentialsId: '%s', "
				+ "overrideGlobal: true, "
				+ "serverURI: '%s', timeout: 480])"
				+ "}",
				CONFIG_TOOLKIT_NAME, invalidBuildDefinitionId, 
				snapshotUUID, 
				credId, 
				Config.DEFAULT.getServerURI()); 
		j.setDefinition(new CpsFlowDefinition(checkoutStep));
		WorkflowRun run = requestJenkinsBuild(j);
		Utils.dumpLogFile(run, "testPollingOnlyChangeLogGenerationBuildDefinitionNotFound",
					"1", ".log");
		
		// Assert that the build failed.
		assertTrue(run.getResult().equals(Result.FAILURE));
	}
	
	@WithTimeout(600)
	@Test
	public void testPollingOnlyChangeLogGenerationBuildWorkspaceDeleted() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String snapshotName = getSnapshotUniqueName();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		WorkflowJob j = setupWorkflowJob(r);
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		String workspacePrefix = "testWksp";
		
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupTestBuildSnapshotConfiguration",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName,
								String.class, // componentName
								String.class, // workspacePrefix
								String.class}, // hjPath
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, workspacePrefix, 
						getSandboxDir().getAbsolutePath());
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		// Delete the workspace
		testingFacade
				.invoke("tearDownRepositoryWorkspaces",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class}, // workspacePrefix
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), 
						workspaceName);
		String checkoutStep = String.format(
				"node {"
				+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
				+ "buildTool: '%s', buildType: [buildWorkspace: '%s', "
				+ "pollingOnlyData: [snapshotUUID: '%s'], value: 'buildWorkspace'], "
				+ "credentialsId: '%s', "
				+ "overrideGlobal: true, "
				+ "serverURI: '%s', timeout: 480])"
				+ "}",
				CONFIG_TOOLKIT_NAME, workspaceName, 
				snapshotUUID, 
				credId, 
				Config.DEFAULT.getServerURI()); 
		j.setDefinition(new CpsFlowDefinition(checkoutStep));
		WorkflowRun run = requestJenkinsBuild(j);
		Utils.dumpLogFile(run, "testPollingOnlyChangeLogGenerationBuildWorkspaceDeleted",
					"1", ".log");
		
		// Assert that the build failed.
		assertTrue(run.getResult().equals(Result.FAILURE));
	}
	
	@WithTimeout(600)
	@SuppressWarnings({ "unchecked"})
	@Test
	public void testPollingOnlyChangelogGenerationTwoWorkspacesUsedInSubsequentBuilds() throws Exception {
		// Run the first build with one workspace
		// Run the second build with another workspace
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		
		// Setup the workflow job
		WorkflowJob j = setupWorkflowJob(r);
		
		Map<String, String> setupArtifacts1 = new HashMap<String, String>();
		Map<String, String> setupArtifacts2 = new HashMap<String, String>();

		try {
			{
			String workspaceName1 = getRepositoryWorkspaceUniqueName();
			{
				String componentName = getComponentUniqueName();
				String buildDefinitionId = getBuildDefinitionUniqueName();
				
				// First set of artifacts
				setupArtifacts1 = (Map<String, String>) testingFacade
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
								loginInfo.getTimeout(), workspaceName1,
								componentName, buildDefinitionId);
	
			}
			String workspaceItemId1 = setupArtifacts1.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
	
			{
				// Run the first build and check if it is success.
				// Inside the helper method, we check if the baselineSet details are added 
				// and there is no pervious baselineSet details
				WorkflowRun run1= helperSetupBuildForBuildWorkspaceWithValidation(testingFacade, 
							j, workspaceName1, workspaceItemId1, 
							"testPollingOnlyChangelogGenerationTwoWorkspacesUsedInSubsequentBuilds", "1", null); 
		
				assertTrue(run1.getResult().equals(Result.SUCCESS));
			}
			}
			{
			// Setup the second build with a different workspace that exists
			String workspaceName2 = getRepositoryWorkspaceUniqueName();
			{
				String componentName = getComponentUniqueName();
				String buildDefinitionId = getBuildDefinitionUniqueName();
	
				// First set of artifacts
				setupArtifacts2 = (Map<String, String>) testingFacade
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
								loginInfo.getTimeout(), workspaceName2,
								componentName, buildDefinitionId);
			}
	
			String workspaceItemId2 = setupArtifacts2.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
			// Since the owning workspace has changed, the previous build's snapshot will not be considered 
			// for changelog generation.
			// The value of previousBaselineSetName and ItemId should be null
			{
				WorkflowRun run2= helperSetupBuildForBuildWorkspaceWithValidation(testingFacade, 
							j, workspaceName2, workspaceItemId2, 
							"testPollingOnlyChangelogGenerationTwoWorkspacesUsedInSubsequentBuilds", 
							"2", null); 
		
				assertTrue(run2.getResult().equals(Result.SUCCESS));
			}
			}
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts1);
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts2);
		}
	}
	
	// @Test
	public void testPollingOnlyChangeLogGenerationMultipleCheckouts() throws Exception {
		// TODO not complete
	}

	private RTCBuildResultAction getBuildResultAction(WorkflowRun run) {
		return run.getAction(RTCBuildResultAction.class);
	}
	
	private RTCChangeLogSet getRTCChangeLogSetFromBuild(WorkflowRun run) {
		RTCChangeLogSet rtcChangeLogSet = null;
		List<ChangeLogSet<?>> changesets = run.getChangeSets();
		for (ChangeLogSet<?> changeSet : changesets) {
			if (changeSet instanceof RTCChangeLogSet) {
				rtcChangeLogSet = (RTCChangeLogSet) changeSet;
				break;
			}
		}
		return rtcChangeLogSet;
	}
	
	private WorkflowRun helperSetupBuildForBuildDefinitionWithValidation(RTCFacadeWrapper testingFacade, WorkflowJob job, 
			String buildDefinitionId, String workspaceName, String workspaceItemId, String prefix, String suffix, 
			WorkflowRun previousRun) throws Exception {
		return helperSetupBuildWithValidation(testingFacade, job, buildDefinitionId, workspaceName, 
				workspaceItemId, prefix,"helperSetupBuildForBuildDefinitionWithValidation" + suffix, previousRun);
	}
	
	private WorkflowRun helperSetupBuildForBuildWorkspaceWithValidation(RTCFacadeWrapper testingFacade, WorkflowJob job, 
			String workspaceName, String workspaceItemId, String prefix, String suffix, 
			WorkflowRun previousRun) throws Exception {
		return helperSetupBuildWithValidation(testingFacade, job, null, workspaceName, workspaceItemId,
				prefix , "helperSetupBuildForBuildWorkspaceWithValidation" + suffix, previousRun);
	}
	
	private WorkflowRun helperSetupBuildWithValidation(RTCFacadeWrapper testingFacade, WorkflowJob job, 
			String buildDefinitionId, String workspaceName, String workspaceItemId,
			String  prefix,	String suffix, WorkflowRun previousRun) throws Exception {
		
		File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
		FileOutputStream changeLog = new FileOutputStream(changeLogFile);
		TaskListener listener = getTaskListener();
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		String snapshotName = getSnapshotUniqueName();
		// Setup Take a snapshot on the workspace and get back the data
		Map<String, Object> result = Utils.accept(testingFacade, 
						loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), 
						null, workspaceName, 
						null, null, 
						getSandboxDir().getAbsolutePath(), 
						changeLog, snapshotName, null, 
						LoadOptions.getDefault(), 
						listener, Locale.getDefault());
		// Get the snapshot UUID from the result object
		// polling only set to true.
		@SuppressWarnings("unchecked")
		Map<String, String> properties = (Map<String, String>) result.get("buildProperties");
		String snapshotUUIDFromAccept = properties.get("team_scm_snapshotUUID");
		Assert.assertNotNull(snapshotUUIDFromAccept);
		
		String checkoutStep = null; 
		if (buildDefinitionId != null) {
			checkoutStep = String.format(
					"node {"
					+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
					+ "buildTool: '%s', buildType: [buildDefinition: '%s', "
					+ "pollingOnlyData: [snapshotUUID: '%s'], value: 'buildDefinition'], "
					+ "credentialsId: '%s', "
					+ "overrideGlobal: true, "
					+ "serverURI: '%s', timeout: 480])"
					+ "}",
					CONFIG_TOOLKIT_NAME, buildDefinitionId, 
					properties.get("team_scm_snapshotUUID"), 
					credId, 
					Config.DEFAULT.getServerURI()); 
		} else {
			checkoutStep = String.format(
					"node {"
					+ "checkout([$class: 'RTCScm', avoidUsingToolkit: false, "
					+ "buildTool: '%s', buildType: [buildWorkspace: '%s', "
					+ "pollingOnlyData: [snapshotUUID: '%s'], value: 'buildWorkspace'], "
					+ "credentialsId: '%s', "
					+ "overrideGlobal: true, "
					+ "serverURI: '%s', timeout: 480])"
					+ "}",
					CONFIG_TOOLKIT_NAME, workspaceName, 
					properties.get("team_scm_snapshotUUID"), 
					credId, 
					Config.DEFAULT.getServerURI()); 
		}
		setupFlowDefinition(job, checkoutStep);
		
		// Act - run the first build and validate snapshot details from 
		// change log set
		WorkflowRun run = requestJenkinsBuild(job);
		Utils.dumpLogFile(run, prefix, suffix, ".log");
		assertTrue(run.getResult().equals(Result.SUCCESS));

		// Check that the log file has a message that it is skipping accept and load
		assertTrue("Expected accept/load skipped message",
				run.getLog().contains("Polling-only is selected. "
						+ "Accept and load of the build workspace will be skipped."));

		// Assert
		RTCChangeLogSet rtcChangeLogSet = getRTCChangeLogSetFromBuild(run);
		Assert.assertNotNull(rtcChangeLogSet);

		// Validate that the snapshot UUID is same as what we passed in
		Assert.assertEquals(snapshotUUIDFromAccept, rtcChangeLogSet.getBaselineSetItemId());
		// Validate the baseline set name
		Assert.assertEquals(snapshotName, rtcChangeLogSet.getBaselineSetName());
		
		// Validate previous baseline set ID and name are null
		if (previousRun == null) {
			Assert.assertNull(rtcChangeLogSet.getPreviousBaselineSetItemId());
			Assert.assertNull(rtcChangeLogSet.getPreviousBaselineSetName());
		} else {
			// Get the changeLogSet from previous build 
			RTCChangeLogSet rtcChangeLogSetPrev = getRTCChangeLogSetFromBuild(run.getPreviousBuild());
			
			// Validate previous baseline set ID and name are null/empty
			Assert.assertEquals(rtcChangeLogSetPrev.getBaselineSetItemId(),
					 rtcChangeLogSet.getPreviousBaselineSetItemId());
			Assert.assertEquals(
					rtcChangeLogSetPrev.getBaselineSetName(),
					rtcChangeLogSet.getPreviousBaselineSetName());
		}
		
		// Validate that the workspace owner item id is set correctly
		Assert.assertEquals(workspaceItemId, rtcChangeLogSet.getWorkspaceItemId());
		Assert.assertEquals(workspaceName, rtcChangeLogSet.getWorkspaceName());
		
		// Validate that the build result action has the right properties
		RTCBuildResultAction action = getBuildResultAction(run);
		Assert.assertEquals(snapshotUUIDFromAccept, 
					action.getBuildProperties().get("team_scm_snapshotUUID"));
		Assert.assertEquals(workspaceItemId, 
				action.getBuildProperties().get("team_scm_snapshotOwner"));
		
		return run;
	}
}
