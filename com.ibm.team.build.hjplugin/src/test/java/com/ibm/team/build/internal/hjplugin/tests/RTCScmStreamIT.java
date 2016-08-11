/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;

public class RTCScmStreamIT {
	private static final String BUILDTOOLKITNAME = "rtc-build-toolkit";

	@Rule public JenkinsRule r = new JenkinsRule();
	
	@WithTimeout(600)
	@Test
	public void buildPassIfPrevSnapshotIsNotFoundInStreamBuild() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String streamName = getTestName() + System.currentTimeMillis();
		Map<String, String> setupArtifacts =  Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);

		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(r, defaultC, BUILDTOOLKITNAME, streamName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			// Test that previousBuildUrl is none because there is nothing to compare with
			RTCScmIT.verifyStreamBuild(build, streamUUID, "");
			
			// Get the snapshot UUID from it so that we can delete it before the next build runs
			String snapshotUUID = getSnapshotUUIDFromBuild(build);
			assertNotNull(snapshotUUID);
			assertTrue(snapshotUUID.length() > 0);
			deleteSnapshot(testingFacade, loginInfo, streamName, snapshotUUID);
			
			// Run another build but ensure that it runs successfully
			FreeStyleBuild build1 =  Utils.runBuild(prj, null);
			// Test that previousBuildUrl is none because our previous snapshot is delete so
			// we didn't compare with anything
			 RTCScmIT.verifyStreamBuild(build1, streamUUID, "");
			
			// Perform another build
			FreeStyleBuild build2 =  Utils.runBuild(prj, null);
			// Ensure that previousBuildUrl is there since we did compare with a previous snapshot
			 RTCScmIT.verifyStreamBuild(build2, streamUUID, build1.getUrl());
			
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
	public void streamNameItemIdPairWithPreviousBuildUrlFoundInChangeLogSet() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String streamName = getTestName() + System.currentTimeMillis();
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
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String streamName = getTestName() + System.currentTimeMillis();
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
	
	private void deleteSnapshot(RTCFacadeWrapper testingFacade, RTCLoginInfo loginInfo, String streamName, String snapshotUUID) throws Exception {
		testingFacade.invoke("deleteSnapshot",
				new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class, // workspaceName
						String.class // snapshotUUID
							},
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(),
				streamName,
				snapshotUUID);
		
	}
	
	private String getSnapshotUUIDFromBuild(Run<?,?> build) {
		return build.getActions(RTCBuildResultAction.class).get(0).getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID);
	}
	
	private String getTestName() {
		return this.getClass().getName();
	}
}
