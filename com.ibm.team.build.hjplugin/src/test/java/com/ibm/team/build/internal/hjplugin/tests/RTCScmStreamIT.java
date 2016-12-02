/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogComponentEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet.ComponentDescriptor;
import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

/**
 * Tests for Build from Stream 
 * 
 * @author lvaikunt
 *
 */
public class RTCScmStreamIT extends AbstractTestCase {
	private static final String BUILDTOOLKITNAME = "rtc-build-toolkit";

	@Rule public JenkinsRule r = new JenkinsRule();
	
	@Before
	public void setup() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
	}
	
	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
	}

	/**
	 * Verify that build completes when snapshot from a previous build has been deleted.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void buildPassIfPrevSnapshotIsNotFoundInStreamBuild() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts =  Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);

		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			// Test that previousBuildUrl is none because there is nothing to compare with
			verifyStreamBuild(build, streamUUID, "");
			
			// Get the snapshot UUID from it so that we can delete it before the next build runs
			String snapshotUUID = getSnapshotUUIDFromBuild(build);
			assertNotNull(snapshotUUID);
			assertTrue(snapshotUUID.length() > 0);
			deleteSnapshot(testingFacade, loginInfo, streamName, snapshotUUID);
			
			// Run another build but ensure that it runs successfully
			FreeStyleBuild build1 =  Utils.runBuild(prj, null);
			// Test that previousBuildUrl is none because our previous snapshot is delete so
			// we didn't compare with anything
			 verifyStreamBuild(build1, streamUUID, "");
			
			// Perform another build
			FreeStyleBuild build2 =  Utils.runBuild(prj, null);
			// Ensure that previousBuildUrl is there since we did compare with a previous snapshot
			 verifyStreamBuild(build2, streamUUID, build1.getUrl());
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Verify that stream name and UUID and previous build url after found in
	 * changelog
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void streamNameItemIdPairWithPreviousBuildUrlFoundInChangeLogSet() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();

			// Verify name and itemId pair inside changelogset
			assertEquals(streamName, changelog.getStreamName());
			assertEquals(streamUUID, changelog.getStreamItemId());
			// Since this is the first build, prevBuildUrl is null
			assertEquals("", changelog.getPreviousBuildUrl());
			
			// Run a second build and ensure that previousBuildUrl is non empty
			FreeStyleBuild build1 = Utils.runBuild(prj, null);
			changelog = (RTCChangeLogSet) build1.getChangeSet();
			
			// Verify name and itemId pair inside changelogset
			assertEquals(streamName, changelog.getStreamName());
			assertEquals(streamUUID, changelog.getStreamItemId());
			assertEquals(build.getUrl(), changelog.getPreviousBuildUrl());
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test 
	public void streamNameItemIdPairWithPreviousBuildUrlFoundInChangeLogFile() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamItemId = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			File changelogFile = new File(build.getRootDir(), "changelog.xml");
			// Verify name and itemId pair inside changelog file
			assertNotNull("Expecting streamName tag", Utils.getMatch(changelogFile, ".*streamName=\"" + streamName +"\".*"));
			assertNotNull("Expecting streamItemId tag", Utils.getMatch(changelogFile, ".*streamItemId=\"" + streamItemId +"\".*"));
			assertNotNull("Expecting previousBuildUrl tag", Utils.getMatch(changelogFile, ".*previousBuildUrl=\"\".*"));
			
			// Run a second build and ensure that previousBuildUrl is non empty
			FreeStyleBuild build1 = Utils.runBuild(prj, null);
			changelogFile = new File(build1.getRootDir(), "changelog.xml");
			
			// Verify name and itemId pair inside changelog file
			assertNotNull("Expecting streamName tag", Utils.getMatch(changelogFile, ".*streamName=\"" + streamName +"\".*"));
			assertNotNull("Expecting streamItemId tag", Utils.getMatch(changelogFile, ".*streamItemId=\"" + streamItemId +"\".*"));
			// Since this is the second build, there should a non empty previous build url.
			assertNotNull("Expecting previousBuildUrl tag", Utils.getMatch(changelogFile, ".*previousBuildUrl=\"" + build.getUrl() + "\".*"));
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Stream, normal checkout + polling  through build toolkit
	 * 1) Positive case when name is provided
	 * 2) Negative case when name is empty
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test 
	public void streamCheckoutAndPollingWithBuildtoolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		
		try {
			{ // positive case - when stream name is not null
				FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
				
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				verifyStreamBuild(build, streamUUID, "");
				
				// Run polling and check whether message appears
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				// Verify polling messages
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, streamName);
			}
			{ // negative case - when stream name is null
				FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, null);
				
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				// Verify that build failed and there is a checkout failure message
				assertEquals(build.getResult(), Result.FAILURE);
				Utils.getMatch(build.getLogFile(), "ERROR: RTC : checkout failure: A stream name is not provided");

				
				// Run polling and check whether message appears
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				// Verify polling messages
				// Ensure that there are no changes and polling happened successfully
				assertEquals(pollingResult.change, Change.NONE);
				Utils.getMatch(pollingFile, "RTC : checking for changes failure: A stream name is not provided");
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Test that after adding a component to the stream, polling detects
	 * new changes.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test 
	public void streamCheckoutAndPollingAfterNewChangesWithBuildtoolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		
		try {
				FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
				
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				verifyStreamBuild(build, streamUUID, "");
				
				// Run polling and check whether message appears
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				// Verify polling messages for "No Changes"
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, streamName);

				// Add a component to the stream
				String componentToAddName = getComponentUniqueName();
				Utils.addComponentToBuildStream(testingFacade, defaultC, streamUUID, componentToAddName);
				
				// Run polling to check whether "Changes Found" message appears
				pollingFile = Utils.getTemporaryFile();
				pollingResult = Utils.pollProject(prj, pollingFile);
				// Verify polling messages for "No Changes"
				Utils.assertPollingMessagesWhenChangesDetected(pollingResult, pollingFile, streamName);
				
				// Run a build and check whether the change log set contains the new component's name
				build = Utils.runBuild(prj, null);
				verifyStreamBuild(build, streamUUID, build.getPreviousBuild().getUrl());

				RTCChangeLogSet changeLogSet = (RTCChangeLogSet) build.getChangeSets().get(0);
				assertEquals(1, changeLogSet.getComponentChangeCount());
				
				for (RTCChangeLogComponentEntry componentEntry : changeLogSet.getComponentChanges()) {
					assertEquals(componentToAddName, componentEntry.getName());
				}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Use this test as a placeholder for all simple validations related to stream configuration.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testStreamConfiguration_misc() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		try {
			// Create a basic project configuration
			// Individual validation steps can then customize the RTCScm instance and update it in the project instance
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			// validate support for custom snapshot name
			validateCustomSnapshotName_stream(prj);

		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Ensure that temporary workspace created during a stream build is deleted 
	 * at the end of a successful build
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testTemporaryWorkspaceDeletedForStreamSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			verifyStreamBuild(build, streamUUID, "");
			
			// Check whether the temporary workspace is uuid and name is not null
			List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, actions.size());
			
			Map<String, String> buildProperties = actions.get(0).getBuildProperties();
			String tempWorkspaceName = buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_NAME);
			String tempWorkspaceUUID = buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_UUID);
			
			assertNotNull(tempWorkspaceName);
			assertNotNull(tempWorkspaceUUID);
			
			// Make a testing facade call to ensure that the temporary workspace does not exist.
			String [] workspaceItemIds = Utils.findTemporaryWorkspaces();
			assertEquals(0, workspaceItemIds.length);			
		}  finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Test whether temporary workspace is deleted when load fails.
	 * In this case, the temporary workspace is deleted immediately after]
	 * load finishes. Therefore two the properties "rtcTempRepoWorkspaceName"
	 *  and "rtcTempRepoWorkspaceUUID" are null
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testTemporaryWorkspaceDeletedForStreamLoadFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		try {

			String loadDirectory = Utils.getInvalidLoadPath();
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName, loadDirectory);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			assertTrue(build.getLog(100).toString(), build.getResult().isWorseOrEqualTo(Result.FAILURE));
			
			// Check whether the temporary workspace is uuid and name is not null
			List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, actions.size());
			
			// Since load failed, temporary workspace should have been deleted before RTCScm returns 
			Map<String, String> buildProperties = actions.get(0).getBuildProperties();
			String tempWorkspaceName = buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_NAME);
			String tempWorkspaceUUID = buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_UUID);
			
			assertTrue(tempWorkspaceName == null);
			assertTrue(tempWorkspaceUUID == null);
			
			// Make a testing facade call to ensure that the temporary workspace does not exist.
			String [] workspaceItemIds = Utils.findTemporaryWorkspaces();
			assertEquals(0, workspaceItemIds.length);			
		}  finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	
	/**
	 * Check for following
	 * team_scm_snapshotUUID, 
	 * team.scm.snapshotOwner,
	 * team.scm.streamChangesData,
	 * team.scm.acceptPhaseOver,
	 * repositoryAddress,
	 * rtcTempRepoWorkspaceName
	 * rtcTempRepoWorkspaceUUID
	 * team.scm.changesAccepted is undefined if there are no changes
	 * @throws Exception
	 */
	@Test
	public void testBuildPropertiesInStreamBuild() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			Utils.verifyRTCScmInBuild(build, false);
			
			List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
			Map<String, String> buildProperties = actions.get(0).getBuildProperties();
			
			assertNotNull(buildProperties.get(Utils.TEAM_SCM_SNAPSHOTUUID));
			assertNotNull(buildProperties.get(Utils.TEAM_SCM_SNAPSHOT_OWNER));
			assertNotNull(buildProperties.get(Utils.TEAM_SCM_STREAM_CHANGES_DATA));
			assertNotNull(buildProperties.get(Utils.TEAM_SCM_ACCEPT_PHASE_OVER));
			assertEquals(defaultC.getLoginInfo().getServerUri(), 
								buildProperties.get(Utils.REPOSITORY_ADDRESS));
			assertNotNull(buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_NAME));
			assertNotNull(buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_UUID));
			
			// Should be null if there are no changes
			assertNull("Not excepting any changes", buildProperties.get(Utils.TEAM_SCM_CHANGES_ACCEPTED));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Check whether team_scm_changesAccepted is non zero if there are component additions in a 
	 * stream
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNonZeroChangesAcceptedBuildPropertyInStreamBuild() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			FreeStyleBuild build = Utils.runBuild(prj, null);		
			Utils.verifyRTCScmInBuild(build, false);
			
			// Verify that after adding a component to the stream, 
			// we see a non zero team_scm_changesAccepted
			String componentToAddName = getComponentUniqueName();
			Map<String, String> newSetupArtifacts = Utils.addComponentToBuildStream(testingFacade, 
					defaultC,
					streamUUID, componentToAddName);
			String componentAddedUUID = newSetupArtifacts.get(Utils.ARTIFACT_COMPONENT_ADDED_ITEM_ID);
			build = Utils.runBuild(prj, null);
			Utils.verifyRTCScmInBuild(build, false);
			
			// Ensure that in build's changelog set we find the component UUID
			RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();
			assertEquals(1, changelog.getComponentChangeCount());
			
			List<RTCChangeLogComponentEntry> componentChanges = changelog.getComponentChanges();
			assertEquals(1, componentChanges.size());
			
			// assert that the component change has the right UUID
			assertEquals(componentAddedUUID, componentChanges.get(0).getItemId());
			
			List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, actions.size());
			Map<String, String> buildProperties = actions.get(0).getBuildProperties();
			
			// Assert that team_scm_changesAccepted is 1
			assertEquals(String.valueOf(1), buildProperties.get(Utils.TEAM_SCM_CHANGES_ACCEPTED));
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * Validate the snapshot name generated in the builds - with and without providing custom snapshot name
	 * 
	 * @param prj
	 * @param setupArtifacts
	 * @param buildDefinitionId
	 * @throws Exception
	 */
	private void validateCustomSnapshotName_stream(FreeStyleProject prj) throws Exception {
		// Run a build, without providing custom snapshot name
		FreeStyleBuild build = Utils.runBuild(prj, null);

		// Verify that by default the snapshot name is <Job Name>_#<Build_Number>
		RTCChangeLogSet changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());

		// update the job configuration with custom snapshot name
		// set overrideDefaultSnapshotName to false. Should use default snapshot name even if a custom snapshot name is
		// provided
		RTCScm rtcScm = (RTCScm)prj.getScm();
		BuildType buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(false);
		buildType.setCustomizedSnapshotName("jenkins_${JOB_NAME}_#${BUILD_NUMBER}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		// Verify that snapshot name is set to the default value
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());

		// update the job configuration with custom snapshot name and set overrideDefaultSnapshotName to true
		rtcScm = (RTCScm)prj.getScm();
		buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(true);
		buildType.setCustomizedSnapshotName("jenkins_${JOB_NAME}_#${BUILD_NUMBER}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		// Verify that the template specified in the custom snapshot name is resolved and set as the name of the
		// generated snapshot
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals("jenkins_" + prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());
		
		// update the job configuration with custom snapshot name that resolves to an empty string and set overrideDefaultSnapshotName to true
		rtcScm = (RTCScm)prj.getScm();
		buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(true);
		buildType.setCustomizedSnapshotName("${emptyParam}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
		
		prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {new StringParameterDefinition("emptyParam", " ")})));
		
		build = Utils.runBuild(prj, Collections.singletonList(new ParametersAction(new StringParameterValue("emptyParam", " "))));

		// Verify that the snapshot name is set to a default value
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());
		// Verify that the console output has the log message
		assertNotNull(Utils
				.getMatch(build.getLogFile(), java.util.regex.Pattern.quote(Messages.RTCScm_empty_resolved_snapshot_name("${emptyParam}"))));
	}
	
	private static void verifyStreamBuild(FreeStyleBuild build, String streamUUID, String url) throws IOException {
		assertNotNull(build);
		assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));

		// Verify whether RTCScm ran successfully
		List<RTCBuildResultAction> rtcActions = build.getActions(RTCBuildResultAction.class);
		assertEquals(1, rtcActions.size());
		RTCBuildResultAction action = rtcActions.get(0);
		assertNotNull(action);
		
		// Verify that we have the stream UUID as the snapshot owner field
		assertEquals(streamUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOT_OWNER));
		
		// Verify that we have stored stream's state inside the build result action
		assertTrue(action.getBuildProperties().get(Utils.TEAM_SCM_STREAM_CHANGES_DATA).length() > 0);
		
		// Verify previous build Url
		RTCChangeLogSet changeLogSet = (RTCChangeLogSet) build.getChangeSet();
		assertEquals("Expected a proper previousBuildUrl", url, changeLogSet.getPreviousBuildUrl());
		
	}
	
	/**
	 * Delete the snapshot identified by a UUID
	 *  
	 * @param testingFacade The facade for the build toolkit
	 * @param loginInfo The login details
	 * @param streamName The stream that owns the snapshot
	 * @param snapshotUUID The uuid of the snapshot to be deleted
	 * @throws Exception
	 */
	private void deleteSnapshot(RTCFacadeWrapper testingFacade, RTCLoginInfo loginInfo, String streamName, String snapshotUUID) throws Exception {
		testingFacade.invoke("deleteSnapshot",
				new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class, // streamName
						String.class // snapshotUUID
							},
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(),
				streamName,
				snapshotUUID);
		
	}
	
	/**
	 * Return the snapshot uuid from the build
	 *  
	 * @param build - The Jenkins Freestyle build
	 * @return A string that represents the snapshot UUID
	 */
	private String getSnapshotUUIDFromBuild(Run<?,?> build) {
		return build.getActions(RTCBuildResultAction.class).get(0).getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID);
	}
}
