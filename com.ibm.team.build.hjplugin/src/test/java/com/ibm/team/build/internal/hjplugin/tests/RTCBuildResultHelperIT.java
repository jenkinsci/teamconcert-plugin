/*******************************************************************************
 * Copyright © 2014, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.InvalidCredentialsException;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildResultHelper;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildStatus;

import hudson.model.FreeStyleProject;
import hudson.tools.ToolProperty;
import hudson.util.Secret;

@SuppressWarnings({"nls", "static-method"})
public class RTCBuildResultHelperIT extends AbstractTestCase {

	public static final String ARTIFACT_BUILD_RESULT_ITEM_ID = "buildResultItemId";

	@Rule
	public JenkinsRule j = new  JenkinsRule();
	
	private RTCFacadeWrapper testingFacade;

	@Before
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {
			
			setTestingFacade(Utils.getTestingFacade());
		}
	}

	/**
	 * Test that the Helper class called by the listeners to build deletions 
	 * (deletion of an individual build or a project with builds) will behave
	 * when working with builds that are for workspaces only as well as a build definition.
	 * Detailed testing of the util that finds the RTCScm config is in RTCScmConfigHelperIT
	 * Detailed testing of the actual delete of the build result in RTC is in BuildConnectionIT
	 * @throws Exception If the test fails
	 */
	@SuppressWarnings("unchecked")
	@Test public void testdeleteRTCBuildResults() throws Exception {
		
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
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
				Integer.valueOf(loginInfo.getTimeout()), getBuildDefinitionUniqueName());
			
			try {
				// start & delete in progress build (status ok) avoiding toolkit
				setupBuildTerminationTest(loginInfo, true, false, RTCBuildStatus.OK.name(), setupArtifacts);
				String buildResultUUID = setupArtifacts.get(ARTIFACT_BUILD_RESULT_ITEM_ID);
				FreeStyleProject project = getJenkinsRule().createFreeStyleProject();
				RTCScm scm = getRTCScm();
				project.setScm(scm);
				Set<RTCScm> rtcScmConfigs = Collections.singleton(scm);
				RTCBuildResultAction action = new RTCBuildResultAction(loginInfo.getServerUri(), buildResultUUID, true, null);
				RTCBuildResultHelper.deleteRTCBuildResults(Collections.singletonList(action), project, rtcScmConfigs);
				verifyBuildResultDeleted(loginInfo, setupArtifacts);

				// test an action that does not have a build result uuid - should not fail
				action = new RTCBuildResultAction(loginInfo.getServerUri(), null, true, null);
				RTCBuildResultHelper.deleteRTCBuildResults(Collections.singletonList(action), project, rtcScmConfigs);

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
						Integer.valueOf(loginInfo.getTimeout()), setupArtifacts);
			}
		}
	}

	private RTCScm getRTCScm() throws InvalidCredentialsException {
		RTCBuildToolInstallation tool = new RTCBuildToolInstallation("config_toolkit", Config.DEFAULT.getToolkit(), 
				Collections.<ToolProperty<?>>emptyList());
		tool.getDescriptor().setInstallations(tool);
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		BuildType buildType = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, "SomeBuildDefinition", null, null, "");
		RTCScm scm = new RTCScm(true, "config_toolkit", loginInfo.getServerUri(),
				loginInfo.getTimeout(), loginInfo.getUserId(),
				Secret.fromString(loginInfo.getPassword()), "", "", buildType , false);
		return scm;
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
			Integer.valueOf(loginInfo.getTimeout()),
			Boolean.valueOf(startBuild),
			Boolean.valueOf(abandon), 
			buildStatus,
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
			Integer.valueOf(loginInfo.getTimeout()),
			setupArtifacts);
	}

	public JenkinsRule getJenkinsRule() {
		return this.j;
	}

	public void setJenkinsRUle(JenkinsRule j) {
		this.j = j;
	}

	public RTCFacadeWrapper getTestingFacade() {
		return this.testingFacade;
	}

	public void setTestingFacade(RTCFacadeWrapper testingFacade) {
		this.testingFacade = testingFacade;
	}
}
