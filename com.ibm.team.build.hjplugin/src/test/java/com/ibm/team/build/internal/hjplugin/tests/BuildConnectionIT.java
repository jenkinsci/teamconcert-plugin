/*******************************************************************************
 * Copyright © 2013, 2018 IBM Corporation and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ibm.team.build.internal.hjplugin.BuildResultInfo;
import com.ibm.team.build.internal.hjplugin.RTCBuildCause;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildState;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildStatus;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;

import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

@SuppressWarnings({"nls", "boxing"})
public class BuildConnectionIT extends AbstractTestCase {

	public static final String ARTIFACT_BUILD_RESULT_ITEM_ID = "buildResultItemId";

	private RTCFacadeWrapper testingFacade;

	@Before
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {
			// DO NOT initialize Hudson/Jenkins because its slow and we don't need it for the tests
			setTestingFacade(Utils.getTestingFacade());
		}
	}

	@After
	public void tearDown() throws Exception {
		// Nothing to do including no need to shutdown Hudson/Jenkins
	}
	
	/**
	 * Don't link to things not in this plugin
	 * BuildConnection#addSnapshotContribution() is tested by RepositoryConnectionIT#testBuildResultContributions()
	 * BuildConnection#addWorkspaceContribution() is tested by RepositoryConnectionIT#testBuildResultContributions()
	 * BuildConnection#startBuildActivity() is tested by RepositoryConnectionIT#testBuildResultContributions()
	 * BuildConnection#completeBuildActivity() is tested by RepositoryConnectionIT#testBuildResultContributions()
	 * @throws Exception 
	 */

	@Test public void testCreateBuildResult() throws Exception {
		// test creation of build results
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			getTestingFacade().invoke("testCreateBuildResult",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class}, // testName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName());
		}
	}

	@Test public void testCreateBuildResultFail() throws Exception {
		// test creation of build results
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			getTestingFacade().invoke("testCreateBuildResultFail",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class}, // testName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName());
		}
	}

	@Test public void testLinksToJenkins() throws Exception {
		// test Jenkins external links added
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			getTestingFacade().invoke("testExternalLinks",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class}, // testName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName());
		}
	}

	/**
	 * Test terminating (and deleting) build results when in the build is in a variety of states
	 * 
	 * Test  terminate build (status ok)
	 * Test  terminate build (cancelled)
	 * Test  terminate build (failed)
	 * Test  terminate build (unstable)
	 * Test  (set status of build in test to warning) & terminate build (status ok)
	 * Test  (set status of build in test to error) & terminate build (status unstable)
	 * Test  (set status of build in test to error) & terminate build (abandon)
	 * Test  (abandon build in test) & terminate build (status ok)
	 * Test  (abandon build in test) & terminate build (cancelled)
	 * Test  (abandon build in test) & terminate build (failed)
	 * Test  (abandon build in test) & terminate build (unstable)
	 * Test  leave pending & terminate build (status ok)
	 * Test  leave pending & terminate build (cancelled)
	 * @throws Exception
	 */
	@Test public void testBuildTermination() throws Exception {
		// test termination of build 
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			TaskListener listener = new StreamTaskListener(System.out, null);
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade().invoke(
					"testBuildTerminationSetup",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class}, // testName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName());
			
			try {
				// start & terminate build (status ok) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				String buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// start & terminate build (status ok) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);


				// start & terminate build (cancelled) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// start & terminate build (cancelled) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);


				// start & terminate build (failed) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.FAILURE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// start & terminate build (failed) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.FAILURE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// start & terminate build (unstable) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.UNSTABLE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.WARNING.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// start & terminate build (unstable) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.UNSTABLE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.WARNING.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (set status of build in test to warning) & terminate build (status ok) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.WARNING.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.WARNING.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (set status of build in test to warning) & terminate build (status ok) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.WARNING.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.WARNING.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (set status of build in test to error) & terminate build (status unstable) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.UNSTABLE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (set status of build in test to error) & terminate build (status unstable) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.UNSTABLE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (set status of build in test to error) & terminate build (abandon) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (set status of build in test to error) & terminate build (abandon) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (abandon build in test) & terminate build (status ok) using toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (abandon build in test) & terminate build (status ok) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);


				// Test start & (abandon build in test) & terminate build (cancelled) using toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (abandon build in test) & terminate build (cancelled) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.ERROR.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (abandon build in test) & terminate build (failed) using toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (abandon build in test) & terminate build (cancelled) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (abandon build in test) & terminate build (unstable) using toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.UNSTABLE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (abandon build in test) & terminate build (unstable) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.UNSTABLE, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.INCOMPLETE.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & leave pending & terminate build (status ok) using toolkit
				setupBuildTerminationTest(loginInfo, false, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & leave pending & terminate build (status ok) avoiding toolkit
				setupBuildTerminationTest(loginInfo, false, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.SUCCESS, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.COMPLETED.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);


				// Test start & leave pending & terminate build (cancelled) using toolkit
				setupBuildTerminationTest(loginInfo, false, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.CANCELED.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & leave pending & terminate build (cancelled) avoiding toolkit
				setupBuildTerminationTest(loginInfo, false, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.terminateBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID, Result.ABORTED, listener);
				verifyBuildTermination(loginInfo, RTCBuildState.CANCELED.name(), RTCBuildStatus.OK.name(), setupArtifacts);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	/**
	 * Test the deletion of build results with and without a build toolkit
	 * test deleting an in progress build
	 * test deleting an abandoned build
	 * test deleting a pending build
	 * test deleting a build already deleted
	 * @throws Exception
	 */
	@Test public void testBuildDeletion() throws Exception {
		// test deletion of build result
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade().invoke(
					"testBuildTerminationSetup",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class}, // testName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName());
			
			try {
				// start & delete in progress build (status ok) using toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				String buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// start & delete in progress build (status ok) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & (abandon build in test) & delete build using toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);
				// try deleting the already deleted build
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & (abandon build in test) & delete build avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, true, RTCBuildStatus.ERROR.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);
				// try deleting the already deleted build
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				
				// Test start & leave pending & delete build using toolkit
				setupBuildTerminationTest(loginInfo, false, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), false, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// Test start & leave pending & delete build avoiding toolkit
				setupBuildTerminationTest(loginInfo, false, false, RTCBuildStatus.OK.name(), setupArtifacts);
				buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				RTCFacadeFacade.deleteBuild(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), true, buildResultUUID);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	private void setupBuildTerminationTest(
			RTCLoginInfo loginInfo, boolean startBuild, boolean abandon,
			String buildStatus, Map<String, String> setupArtifacts)
			throws Exception {
		getTestingFacade().invoke(
				"testBuildTerminationTestSetup",
			new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				boolean.class, //startBuild,
				boolean.class, // abandon,
				String.class, // buildStatus,
				Map.class}, //  artifactIds
			loginInfo.getServerUri(),
			loginInfo.getUserId(),
			loginInfo.getPassword(),
			loginInfo.getTimeout(),
			startBuild,
			abandon, 
			buildStatus,
			setupArtifacts);
	}

	private void verifyBuildTermination(
			RTCLoginInfo loginInfo, String expectedState,
			String expectedStatus, Map<String, String> setupArtifacts)
			throws Exception {
		getTestingFacade().invoke(
				"verifyBuildTermination",
			new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class, // expectedState,
				String.class, // expectedStatus,
				Map.class}, //  artifactIds
			loginInfo.getServerUri(),
			loginInfo.getUserId(),
			loginInfo.getPassword(),
			loginInfo.getTimeout(),
			expectedState,
			expectedStatus, 
			setupArtifacts);
	}
	
	private void verifyBuildResultDeleted(RTCLoginInfo loginInfo,
			Map<String, String> setupArtifacts) throws Exception {
		getTestingFacade().invoke(
				"verifyBuildResultDeleted",
			new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				Map.class}, //  artifactIds
			loginInfo.getServerUri(),
			loginInfo.getUserId(),
			loginInfo.getPassword(),
			loginInfo.getTimeout(),
			setupArtifacts);
	}

	@Test public void testBuildStart() throws Exception {
		// test termination of build 
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			getTestingFacade().invoke("testBuildStart",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class}, // testName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName());
		}
	}
	
	@Test public void testBuildResultInfo() throws Exception {
		// test that the build info represents the cause of the build
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			// Let the test fill in the build result UUID.
			String buildResultUUID = ""; 
			BuildResultInfo buildResultInfo = new BuildResultInfo(buildResultUUID, false);
			String loggedInContributorName = (String) getTestingFacade().invoke("testBuildResultInfo",
				new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, // testName
					Object.class}, // buildResultInfo
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), getBuildDefinitionUniqueName(),
				buildResultInfo);
			
			assertFalse(buildResultInfo.ownLifeCycle());
			assertTrue(buildResultInfo.isPersonalBuild());
			assertFalse(buildResultInfo.isScheduled());
			assertEquals(loggedInContributorName, buildResultInfo.getRequestor());
			
			RTCBuildCause buildCause = new RTCBuildCause(buildResultInfo);
			assertTrue(buildCause.getShortDescription(), buildCause.getShortDescription().contains("ersonal"));
			assertTrue(buildCause.getShortDescription(), buildCause.getShortDescription().contains(loggedInContributorName));
		}
	}

	private RTCFacadeWrapper getTestingFacade() {
		return this.testingFacade;
	}

	private void setTestingFacade(RTCFacadeWrapper testingFacade) {
		this.testingFacade = testingFacade;
	}
}
