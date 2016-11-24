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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;
import org.mockito.Mockito;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.Helper;

import hudson.FilePath;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;


/**
 * Integration Tests for {@link Helper} class
 *
 */
public class HelperIT extends AbstractTestCase {
	
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
	 * Check whether RTCScm can accept build definition value from the a build parameter when running the build
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionWithParameterWithNoDefaultValueAndOverrideSuccess() throws Exception {
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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, Config.DEFAULT.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm1 = new RTCScm(true, BUILDTOOLKITNAME, Config.DEFAULT.getServerURI(), Config.DEFAULT.getTimeout(), Config.DEFAULT.getUserID(), 
					Secret.fromString(Config.DEFAULT.getPassword()), Config.DEFAULT.getPasswordFile(), null, 
					new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			String[] result = verifyBuildDefinitionWithParameter(r, rtcScm1, buildDefinitionId,
								null, componentName,
								new ParametersAction(new StringParameterValue("myBuildDefinition", buildDefinitionId)),
								new ParametersAction(new StringParameterValue("buildResultUUID", "")));
			setupArtifacts.put("buildResultItemId1", result[0]);
		} finally {
			testingFacade.invoke(
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

	/**
	 * With no default value and no override, build fails
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionWithParameterWithNoDefaultValueAndNoOverrideFail() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.setScm(rtcScm);
			
			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null, 
									Arrays.asList(new ParametersAction[] {new ParametersAction(new StringParameterValue("buildResultUUID", ""))}));
			while(!future.isDone());
			FreeStyleBuild build = future.get();
			
			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.FAILURE));
			assertNotNull("Expected checkout failure message", getMatch(build.getLogFile(), ".*RTC : checkout failure:.*"));
		} finally {
			testingFacade.invoke(
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
	
	/**
	 * If you don't provide a value for the parameter while running the build, it takes the default value 
	 * of the parameter and succeeds
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionParameterWithDefaultValueAndNoOverrideSuccess() throws Exception {
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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm1 = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			String[] result = verifyBuildDefinitionWithParameter(r, rtcScm1, buildDefinitionId,
												new ParameterDefinition[] {new StringParameterDefinition("myBuildDefinition", buildDefinitionId), 
														new StringParameterDefinition("buildResultUUID", "")},
												componentName,null);
			setupArtifacts.put("buildResultItemId1", result[0]);
		} finally {
			testingFacade.invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // passwortestBuildDefinitionParameterWithDefaultValueAndNoOverrideSuccessd,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), setupArtifacts);
		} 
	}

	
	/**
	 * If you override with a proper value, build succeeds even if the default value is invalid
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionParameterWithInvalidDefaultValueAndOverrideSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		String invalidBuildDefinitionId = "InvalidBuildDefinitionId";

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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm1 = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			String[] result = verifyBuildDefinitionWithParameter(r, rtcScm1, buildDefinitionId,
												new ParameterDefinition[] {new StringParameterDefinition("myBuildDefinition", invalidBuildDefinitionId)},
												componentName, new ParametersAction(new StringParameterValue("myBuildDefinition", buildDefinitionId)),
												new ParametersAction(new StringParameterValue("buildResultUUID", "")));

			setupArtifacts.put("buildResultItemId1", result[0]);
		} finally {
			testingFacade.invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // passwortestBuildDefinitionParameterWithDefaultValueAndNoOverrideSuccessd,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), setupArtifacts);
			
		} 
	}
	
	/**
	 * If you don't provide a value for the parameter while running the build, it take the default value. 
	 * If that default value is invalid, then build fails
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionParameterWithInvalidDefaultValueAndNoOverrideFail() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		String invalidBuildDefinitionId = "InvalidBuildDefinitionId";

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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {
					new StringParameterDefinition("myBuildDefinition", invalidBuildDefinitionId),
					new StringParameterDefinition("buildResultUUID", "")})));
			prj.setScm(rtcScm);
			
			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null, Arrays.asList(new ParametersAction[] {}));
			
			while(!future.isDone());
			FreeStyleBuild build = future.get();
			
			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.FAILURE));
			
			// If there is any error during polling, it can be seen in the log file
			assertNotNull("Expected checkout failure message", getMatch(build.getLogFile(), ".*RTC : checkout failure:.*"));
		} finally {
			testingFacade.invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // passwortestBuildDefinitionParameterWithDefaultValueAndNoOverrideSuccessd,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), setupArtifacts);
		} 
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionPollingWithParameterWithDefaultValueSuccess() throws Exception {
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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			// Setup with rtcBuildDefinition parameter with a value
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(
						new ParameterDefinition[] {new StringParameterDefinition("myBuildDefinition", buildDefinitionId),
										new StringParameterDefinition("buildResultUUID", "")})));
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause)null);
			while(!future.isDone());
			FreeStyleBuild build = future.get();

			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Verify whether RTCScm ran successfully
			List<RTCBuildResultAction> rtcActions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, rtcActions.size());
			RTCBuildResultAction action = rtcActions.get(0);
			
			// Verify that RTCScm ran with the default build definition id
			RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();
			assertEquals(buildDefinitionId, changelog.getBuildDefinitionName());
			
			// Verify build result getting created
			assertNotNull(action.getBuildResultUUID());
			setupArtifacts.put("buildResultItemId", action.getBuildResultUUID());

			File pollingFile = Utils.getTemporaryFile();
			PollingResult pollResult = prj.poll(new StreamTaskListener(pollingFile, Charset.forName("ASCII")));

			// If there is any error during polling, it can be seen in the log file
			assertNotNull("Expected message about checking incoming changes", getMatch(pollingFile, "Checking incoming changes for \"" + buildDefinitionId + "\""));
			assertNotNull("Expecting No changes", getMatch(pollingFile, "RTC : No changes detected"));
			assertEquals("Expecting No changes", Change.NONE, pollResult.change);
		} finally {
			testingFacade.invoke(
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

	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test public void testBuildDefinitionPollingWithParameterAndNoDefaultValueFail() throws Exception {
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
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${myBuildDefinition}", null, null, null), false);

			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null, 
						new ParametersAction(new StringParameterValue("myBuildDefinition", buildDefinitionId)),
						new ParametersAction(new StringParameterValue("buildResultUUID", "")));
			while(!future.isDone());
			FreeStyleBuild build = future.get();

			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Verify whether RTCScm ran successfully
			List<RTCBuildResultAction> rtcActions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, rtcActions.size());
			RTCBuildResultAction action = rtcActions.get(0);
			
			// Verify build result getting created
			assertNotNull(action.getBuildResultUUID());
			setupArtifacts.put("buildResultItemId", action.getBuildResultUUID());
			
			File pollingFile = Utils.getTemporaryFile();
			PollingResult pollResult = prj.poll(new StreamTaskListener(pollingFile, Charset.forName("UTF-8")));
			
			// If there is any error during polling, it can be seen in the log file
			assertNotNull(getMatch(pollingFile, "FATAL: RTC : checking for changes failure: Unable to find a build definition with ID:.*myBuildDefinition.*"));
			assertEquals(Change.NONE, pollResult.change);
		} finally {
			testingFacade.invoke(
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

	@WithTimeout(600)
	@Test public void testBuildSnapshot_PredefinedParameterNonNull_BuildSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();

		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupBuildSnapshot",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName
								String.class, // componentName
								String.class}, // workspacePrefix
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, "HJP");
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		try {
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildSnapshot", "", null, null, null), false);
		
			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {new StringParameterDefinition(RTCJobProperties.RTC_BUILD_SNAPSHOT, "")})));
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null,  
						new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, snapshotName)),
						new ParametersAction(new StringParameterValue("buildResultUUID", "")));
			while(!future.isDone());
			FreeStyleBuild build = future.get();

			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Get the build snapshot UUID from build result action 
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertEquals(snapshotUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID));
			
		} finally {
			testingFacade.invoke(
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


	@WithTimeout(600)
	@Test public void testBuildSnapshot_PredefinedParameterNonNullConfigValueAsParamInvalid_BuildSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();

		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupBuildSnapshot",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName
								String.class, // componentName
								String.class}, // workspacePrefix
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, "HJP");
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		try {
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildSnapshot", null, null, "${myBuildSnapshot}", null), false);
		
			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {new StringParameterDefinition(RTCJobProperties.RTC_BUILD_SNAPSHOT, snapshotName),
																new StringParameterDefinition("myBuildSnapshot", "Dummy Snapshot")
																})));
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null,  
								new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, snapshotName)),
								new ParametersAction(new StringParameterValue("myBuildSnapshot", "Dummy Snapshot")),
								new ParametersAction(new StringParameterValue("buildResultUUID", "")));

			while(!future.isDone());
			FreeStyleBuild build = future.get();

			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Get the build snapshot UUID from build result action 
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertEquals(snapshotUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID));
		} finally {
			testingFacade.invoke(
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
		
	@WithTimeout(600)
	@Test public void testBuildSnapshot_ConfigValueAsParamNonNull_BuildSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();

		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupBuildSnapshot",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName
								String.class, // componentName
								String.class}, // workspacePrefix
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, "HJP");
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		try {
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildSnapshot", null, null, "${myBuildSnapshot}", null), false);
		
			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {new StringParameterDefinition("myBuildSnapshot", "Dummy Snapshot")})));
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null, 
						new ParametersAction(new StringParameterValue("myBuildSnapshot", snapshotName)),
						new ParametersAction(new StringParameterValue("buildResultUUID", "")));
			while(!future.isDone());
			FreeStyleBuild build = future.get();

			// Verify the build status
			assertNotNull(build);
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Get the build snapshot UUID from build result action 
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertEquals(snapshotUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID));
		} finally {
			testingFacade.invoke(
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
	
	/**
	 * Test whether the getTemorary workspace comment method uses build number, job name and server name
	 */
	@Test public void testGetTemporaryWorkspaceComment() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject("myfakeJob");
		
		// Test
		QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0);

		while(!future.isDone());
		FreeStyleBuild build = future.get();
	
		// Test
		String workspaceComment = Helper.getTemporaryWorkspaceComment(build);
		
		// Behavior assertion
		assertEquals(Messages.RTCScm_temporary_workspace_comment(
				1, 
				"myfakeJob", 
				r.getURL()), workspaceComment);
			
	}

	private String[] verifyBuildDefinitionWithParameter(JenkinsRule r, RTCScm rtcScm, String buildDefinitionId,
				ParameterDefinition[] parameterDefinitions, String componentName, ParametersAction... pActions) throws Exception {
		String [] result = new String[2];
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		if (parameterDefinitions != null) {
			prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(parameterDefinitions)));
		}
		prj.setScm(rtcScm);
		
		if (pActions ==null) {
			pActions = new ParametersAction[0];
		}
		// Test
		QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null,pActions);
		while(!future.isDone());
		FreeStyleBuild build = future.get();
		
		// Verify whether RTCScm ran successfully
		Utils.verifyRTCScmInBuild(build, true);

		RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
		result[0] = action.getBuildResultUUID();
		result[1] =  action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID);
		
		// Verify that build ran with the same definition Id
		RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();
		assertEquals(buildDefinitionId, changelog.getBuildDefinitionName());

		// Verify the file contents
		FilePath w = build.getWorkspace();
		File dir = new File(w.getRemote());
		String[] children = dir.list(); 
		assertEquals(dir.getAbsolutePath(), 3, children.length);

		// Existence tests
		assertTrue(new File(dir, ".jazz5").exists());
		assertTrue(new File(dir, componentName).exists());
		assertTrue(new File(dir, "newTree2").exists());
		assertTrue(new File(dir, componentName + "/f/newTree").exists());
		return result;
	}
	
	private String getMatch(File file, String pattern) throws FileNotFoundException {
        Scanner scanner = null;
        String match = null;
        try {
        	scanner = new Scanner(file, "UTF-8");
        	scanner.useDelimiter(System.getProperty("line.separator"));
            while(scanner.hasNext()) {
                    String token = scanner.next();
                    if (token.matches(pattern)) {
                    	match = token;
                            break;
                    }
            }
        } finally {
        	if (scanner != null) {
        		scanner.close();
        	}
        }
        return match;
	}
}
