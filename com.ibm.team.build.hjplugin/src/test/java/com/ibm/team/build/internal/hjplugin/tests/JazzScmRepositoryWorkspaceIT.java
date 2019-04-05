/*******************************************************************************
 * Copyright © 2018, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.scm.PollingResult;

/**
 * 
 * Integration tests for RTCScm - repository workspace tests
 * 
 */
@SuppressWarnings({"nls", "boxing"})
public class JazzScmRepositoryWorkspaceIT extends AbstractTestCase {
	/**
	 * Jenkins
	 */
	@Rule public JenkinsRule r = new JenkinsRule();
	private RTCFacadeWrapper testingFacade;

	@Before
	public void setUp() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			setTestingFacade(Utils.getTestingFacade());
	        createSandboxDirectory();
		}
	}
	
	@After
	public void tearDown() throws Exception {
		// delete the sandbox
		if (Config.DEFAULT.isConfigured()) {
			tearDownSandboxDirectory();
		}
	}

	@Test
	public void testBuildToolkitVersionInBuildLogForRepositoryWorkspaceConfiguration() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		if (Utils.is407BuildToolkit() || 
				Utils.is50BuildToolkit()) {
			// Required class IComponentConfiguration is missing
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupAcceptChanges", //$NON-NLS-1$
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName
								String.class, // loadDirectory
								boolean.class, // createWorkItem
								String.class,// workItemSummary
								}, 
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						componentName, this.getSandboxDir().getAbsolutePath(),
						false, null); 
		try {
			// Create a Jenkins free style job with repository workspace			
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName);
			
			// Request a build with com.ibm.team.build.debug
			FreeStyleBuild build = Utils.runBuild(prj, Arrays.asList(new ParametersAction
							(new StringParameterValue("com.ibm.team.build.debug", "true"))));
			RTCBuildToolInstallation install = Utils.getSystemBuildToolkit(getJenkinsRule());
			assertNotNull("Expected system build toolkit to be non empty", install);
			
			// Get the log file out and look for "build toolkit version" text message
			String filePath = Utils.dumpLogFile(build, "tmp", "testBuildToolkitVersionInBuildLog", "test");
			String line = Utils.getMatch(build.getLogFile(), "Version of build toolkit .* on master is .*");
			assertNotNull(String.format("Check the log file at %s", filePath), line);
			
			// Assert that the contents of the line is what we expect.
			assertTrue(String.format("Line is %s",line), line.contains("Version of build toolkit \"" + install.getBuildToolkit() + "\" on "));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	
	/**
	 * Repository Workspace related tests
	 */
	
	/**
	 * Verify that changes are not accepted and snapshot is not created
	 * @throws Exception
	 */
	@Test
	public void testRepositoryWorkspaceWithAcceptBeforeLoadFalse() throws Exception {
		// Call setup_acceptchanges to setup a repository workspace and a flow target repository workspace 
		// with some change sets
		// Create a free style job with repository workspace config
		// Run a build
		// Get the build action properties and check for the following
		
		// 1. team_scm_acceptedChangesCOunt == non negative value > 0
		// 2. Number of acceptedChangesCOunt matches the above value
		// 3. Non empty snapshot UUID value
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = getTestingFacade();
		Map<String, String> setupArtifacts = Utils.setupRepositoryWorkspace(testingFacade,
				defaultC, workspaceName, componentName);
		try {
			// Create a freestyle job
			// Create a Jenkins freestyle job with repository workspace
			RTCScm.BuildType b = new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null); //$NON-NLS-1$
			
			// Set accept before load to false
			b.setAcceptBeforeLoad(false);
			
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName, b);
			// Run the build once and changes will be accepted.
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.verifyRTCScmInBuild(build, false, false);
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertNull(action.getBuildProperties().get("team_scm_changesAccepted"));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * Verify that changes are accepted and snapshot is created
	 * @throws Exception
	 */
	@Test
	public void testRepositoryWorkspaceWithAcceptBeforeLoadTrue() throws Exception {
		// Call setup_acceptchanges to setup a repository workspace and a flow target repository workspace 
		// with some change sets
		// Create a free style job with repository workspace config
		// Run a build
		// Get the build action properties and check for the following
		
		// 1. team_scm_acceptedChangesCOunt == non negative value > 0
		// 2. Number of acceptedChangesCOunt matches the above value
		// 3. Non empty snapshot UUID value
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = getTestingFacade();
		Map<String, String> setupArtifacts = Utils.setupRepositoryWorkspace(testingFacade,
				defaultC, workspaceName, componentName);
		try {
			// Create a freestyle job
			// Create a Jenkins freestyle job with repository workspace
			RTCScm.BuildType b = new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null); //$NON-NLS-1$
			
			// Set accept before load to true
			b.setAcceptBeforeLoad(true);
			
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName, b);
			// Run the build once and changes will be accepted.
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.verifyRTCScmInBuild(build, false, true);
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertNotNull(action.getBuildProperties().get("team_scm_changesAccepted"));
			assertTrue(Integer.valueOf(action.getBuildProperties().get("team_scm_changesAccepted")) >= 1);
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * If a repository workspace has a outgoing change (change set or component add/remove), it is not 
	 * considered when polling happens.
	 * 
	 * @throws Exception
	 */
	public void testRepositoryWorkspacePollingWithAcceptBeforeLoadAndIgnoreOutogingToTrue() throws Exception {
		// Call setup_acceptchanges to setup a repository workspace and a flow target repository workspace 
		// with some change sets
		// Create a free style job with repository workspace config
		// Run a build to accept changes
		// Create change set on the flow target
		// Run polling (changes detected)
		// Run a build
		// Run polling again (no changes)
		// Create an change set on the repository workspace
		// Run polling again (changes detected)
		// Set the property to true
		// Run polling again (no changes detected)
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = getTestingFacade();
		Map<String, String> setupArtifacts = Utils.setupRepositoryWorkspace(testingFacade,
				defaultC, workspaceName, componentName);
		String workspaceUUID = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		String componentUUID = setupArtifacts.get(Utils.ARTIFACT_COMPONENT1_ITEM_ID);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		try {
			
			// Create a Jenkins freestyle job with repository workspace
			RTCScm.BuildType b = new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null); //$NON-NLS-1$			
			// Set accept before load to true
			b.setAcceptBeforeLoad(true);
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName, b);
			
			{
				// First run a build to accept all the changes
				FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyRTCScmInBuild(build, false, true);
				RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
				assertNotNull(action.getBuildProperties().get("team_scm_changesAccepted"));
				assertTrue(Integer.valueOf(action.getBuildProperties().get("team_scm_changesAccepted")) >= 1);
				
				// Setup a change set on the flow target
				// Here the stream is a repository workspace, so we can 
				// create a change set directly
				Utils.createEmptyChangeSets(testingFacade, defaultC, 
					streamUUID, componentUUID, 1);
				
				// Poll to see if changes are detected
				File pollingFile = Utils.getTemporaryFile("testRepositoryWorkspacePollingWithAcceptBeforeLoadAndIgnoreOutogingToTrue-1-", false);
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				Utils.assertPollingMessagesWhenChangesDetected(pollingResult, pollingFile, workspaceName);

				// Run the build once and changes will be accepted.
				build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyRTCScmInBuild(build, false, true);
				action = build.getAction(RTCBuildResultAction.class);
				assertNotNull(action.getBuildProperties().get("team_scm_changesAccepted"));
				assertTrue(Integer.valueOf(action.getBuildProperties().get("team_scm_changesAccepted")) == 1);
			}
			{
				// Poll again to see if changes are not detected
				File pollingFile = Utils.getTemporaryFile("testRepositoryWorkspacePollingWithAcceptBeforeLoadAndIgnoreOutogingToTrue-2-", false);
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, workspaceName);
			
				// Create a empty workspace in the repository workspace 
				Utils.createEmptyChangeSets(testingFacade, defaultC, 
						workspaceUUID, componentUUID, 1);
			
				// Poll to see if changes are detected because there is an outgoing 
				// change on the repository workspace
				pollingFile = Utils.getTemporaryFile("testRepositoryWorkspacePollingWithAcceptBeforeLoadAndIgnoreOutogingToTrue-3-", false);
				pollingResult = Utils.pollProject(prj, pollingFile);
				Utils.assertPollingMessagesWhenChangesDetected(pollingResult, pollingFile, workspaceName);

				// Add a property to the job to ignore outgoing changes in the repository workspace
				prj.addProperty(new ParametersDefinitionProperty(new 
						StringParameterDefinition("com.ibm.team.build.ignoreOutgoingFromBuildWorkspaceWhilePolling", "true")));

				// Poll to confirm that no changes are detected
				pollingFile = Utils.getTemporaryFile("testRepositoryWorkspacePollingWithAcceptBeforeLoadAndIgnoreOutogingToTrue-4-", false);
				pollingResult = Utils.pollProject(prj, pollingFile);
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, workspaceName);
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);

		}
	}
	
	@Test
	public void testRepositoryWorkspacePollingWithAcceptBeforeLoadIsFalse() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		RTCFacadeWrapper testingFacade = getTestingFacade();
		Map<String, String> setupArtifacts = Utils.setupRepositoryWorkspace(testingFacade,
				defaultC, workspaceName, componentName);
		try {
			
			// Create a Jenkins freestyle job with repository workspace and acceptBeforeLoad is true
			{ 
				RTCScm.BuildType b = new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null); //$NON-NLS-1$			
				// Set accept before load to true
				b.setAcceptBeforeLoad(true);
				FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName, b);

				// Run a build to accept all changes
				FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyRTCScmInBuild(build, false, true);
				RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
				assertNotNull(action.getBuildProperties().get("team_scm_changesAccepted"));
				assertTrue(Integer.valueOf(action.getBuildProperties().get("team_scm_changesAccepted")) >= 1);
				
				// Poll to confirm that there are no new changes
				File pollingFile = Utils.getTemporaryFile(true);
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, workspaceName);
			}
			
			// Create a new free style job with the previous repositoryWorkspace and acceptBeforeLoad as false
			{
				RTCScm.BuildType b = new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null); //$NON-NLS-1$			
				// Set accept before load to false
				b.setAcceptBeforeLoad(false);
				FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName, b);

				/*File pollingFile = Utils.getTemporaryFile("pollingBeforeFirstBuild", false);
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);*/
				
				// Run a build to see that no changes were accepted and no snapshot was created
				FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyRTCScmInBuild(build, false, false);
				RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
				assertNull(action.getBuildProperties().get("team_scm_changesAccepted"));
				
				// There are no incoming changes, but since acceptBeforeLoad is false, polling will detect changes
				File pollingFile = Utils.getTemporaryFile("PollingAFterFirstBuild", false);
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				Utils.assertPollingMessagesWhenSpuriousChangesDetected(pollingResult, pollingFile, workspaceName);
				
				// Run a build to see that no changes were accepted and no snapshot was created
				build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyRTCScmInBuild(build, false, false);
				action = build.getAction(RTCBuildResultAction.class);
				assertNull(action.getBuildProperties().get("team_scm_changesAccepted"));
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	private JenkinsRule getJenkinsRule() {
		return this.r;
	}

	private RTCFacadeWrapper getTestingFacade() {
		return this.testingFacade;
	}

	private void setTestingFacade(RTCFacadeWrapper testingFacade) {
		this.testingFacade = testingFacade;
	}
}
