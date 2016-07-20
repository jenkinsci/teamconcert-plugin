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
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.util.Helper;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;
/**
 * Tests for {@link Helper} class
 *
 */
public class HelperIT {
	
	private static final String BUILDTOOLKITNAME = "rtc-build-toolkit";

	/**
	 * Check whether Helper can correctly parse predefined parameter from {@link EnvVars} in {@link Run}
	 * 
	 * @throws Exception
	 */
	 @Test public void testParseConfigurationValueForPredefinedParameterFromEnvironment() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run mockR = createMockRun(listener);
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		// The user has provided value for predefined parameter.
		// For every test value, setup the environment to return a value for the predefined parameter
		// The output from our test method should match the expected value from the map
		for (String s : keySet) {
			EnvVars v = new EnvVars();
			v.put(RTCJobProperties.RTC_BUILD_DEFINITION, s);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			// test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, null, true, listener);
			// verify
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If predefined parameter is empty, return the configuration value as is
		mockR = createMockRun(listener);
		EnvVars v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_DEFINITION, "  ");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, "dummy value", true, listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
		
		// If predefined parameter is not defined, return the configuration value as is
		mockR = createMockRun(listener);
		v = new EnvVars();
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, "dummy value", true, listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}
	
	/**
	 * Check whether Helper can correctly parse predefined parameter from {@link ParametersAction} inside {@link Run}
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueForPredefinedParameterFromParametersAction() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?,?> mockR = createMockRun(listener);
		// Return empty environment. this will force our code to look from {@link ParametersAction}
		EnvVars v = new EnvVars();
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		for (String s: keySet) {
			// Add a parameters action for the predefined parameter
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, s)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			// test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, null, true, listener);
			// verify
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If predefined parameter is empty, return the configuration value as is
		mockR = createMockRun(listener);
		ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
		actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, "    ")));
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, "dummy value", true, listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
		
		// If predefined parameter is not defined, return the configuration value as is
		mockR = createMockRun(listener);
		actions = new ArrayList<ParametersAction>();
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, "dummy value", true, listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}
	
	/**
	 * Checks whether Helper parses user defined parameter from {@link EnvVars} in {@link Run}
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueForUserDefinedParameterFromEnvironment() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?,?> mockR = createMockRun(listener);
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		// The configuration value is a user defined parameter. 
		// For every test value, setup the environment to return a value for the user defined key in environment
		// the output from our test method should match the expected value.
		String userDefinedParameterAsConfigValue = "${testParameter}";
		String userDefinedParameter = "testParameter";
		for (String s : keySet) {
			EnvVars v = new EnvVars();
			v.put(userDefinedParameter, s);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			// test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, userDefinedParameterAsConfigValue, true, listener);
			// verify
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If predefined parameter and user defined parameters are defined, then predefined parameter should take precedence
		mockR = createMockRun(listener);
		EnvVars v = new EnvVars();
		// Define predefined parameter
		v.put(RTCJobProperties.RTC_BUILD_DEFINITION, "test value");
		// Add user defined parameter
		v.put(userDefinedParameter, "dummy value");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, userDefinedParameterAsConfigValue, true, listener);
		// verify that getActions was never called because predefined parameter was resolved from the environment
		Mockito.verify(mockR, Mockito.times(0)).getActions(ParametersAction.class);
		assertEquals("test value", actualValue);
		
		// If predefined parameter is empty, then user defined parameter is used
		mockR = createMockRun(listener);
		v = new EnvVars();
		// Define predefined parameter
		v.put(RTCJobProperties.RTC_BUILD_DEFINITION, "   ");
		// Add user defined parameter
		v.put(userDefinedParameter, "dummy value");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, userDefinedParameterAsConfigValue, true, listener);
		// verify that getActions was called only once for RTC_Build_Definition but not for userDefiniedParameter
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}

	/**
	 * Check whether Helper parses user defined parameters from {@link ParametersAction} inside {@link Run}
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueForUserDefinedParameterFromParametersAction() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?,?> mockR = createMockRun(listener);
		// Return empty environment. this will force our code to look from {@link ParametersAction}
		EnvVars v = new EnvVars();
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		String userDefinedParameterAsConfigValue = "${testParameter}";
		String userDefinedParameter = "testParameter";
		for (String s: keySet) {
			// Add a parameters action for the userdefined parameter
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(userDefinedParameter, s)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			// test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, userDefinedParameterAsConfigValue, true, listener);
			// verify
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If both predefined and user defined parameters are present, predefined parameter takes precedence
		mockR = createMockRun(listener);
		ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
		// Add a parameters action for the predefined parameter
		actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, "test value")));
		// Add a parameters action for user defined parameter
		actions.add(new ParametersAction(new StringParameterValue(userDefinedParameter, "dummy value")));
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, userDefinedParameterAsConfigValue, true, listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("test value", actualValue);
		
		// If predefined parameter is empty, then user defined parameter is used
		mockR = createMockRun(listener);
		actions = new ArrayList<ParametersAction>();
		// Add a parameters action for the predefined parameter
		actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, "   ")));
		// Add a parameters action for user defined parameter
		actions.add(new ParametersAction(new StringParameterValue(userDefinedParameter, "dummy value")));
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, userDefinedParameterAsConfigValue, true, listener);
		// verify that getActions was called twice, once for RTC_BUILD_DEFINITION and another for user defined parameter
		Mockito.verify(mockR, Mockito.times(2)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}
	

	/**
	 * Check whether Helper doesn't parse a regular value
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueRegular() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?,?> mockR = createMockRun(listener);		
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		// The configuration value is not a property. The values should be returned trimmed even if it is not a property
		for (String s : keySet) {
			EnvVars v = new EnvVars();
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, s, true, listener);
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		// If a predefined parameter is defined, then that should take precedence over the configuration value
		mockR = createMockRun(listener);
		EnvVars v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_DEFINITION, "test value");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, "dummy value", true, listener);
		Mockito.verify(mockR, Mockito.times(0)).getActions(ParametersAction.class);
		assertEquals("test value", actualValue);
		
		// If a predefined parameter is empty, then configuration value is used
		mockR = createMockRun(listener);
		v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_DEFINITION, "   ");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_DEFINITION, "dummy value", true, listener);
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueTestResolveConfigurationValueAsParm() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		String streamConfigValue = "stream Config Value";
		
		// Test
		// If a predefined parameter is defined and a configuration value is also present, the predefined parameter is resolved
		Run<?,?> mockRun = createMockRun(listener);
		EnvVars v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_STREAM,  "test stream");
		Mockito.doReturn(v).when(mockRun).getEnvironment(listener);
		assertEquals("test stream", Helper.parseConfigurationValue(mockRun, RTCJobProperties.RTC_BUILD_STREAM, streamConfigValue, false, listener));
		
		// If a predefined parameter is not defined and configuration value is a regular value, it is returned as such
		// Whitespace is trimmed.
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		for (String s : keySet) {
			mockRun = createMockRun(listener);
			Mockito.doReturn(new EnvVars()).when(mockRun).getEnvironment(listener);
			String actualValue = Helper.parseConfigurationValue(mockRun, RTCJobProperties.RTC_BUILD_STREAM, s, false, listener);
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If a predefined parameter is not defined and configuration value is a parameter, it is not resolved but trimmed for 
		// whitespace
		Map<String, String> actualToExpectedValues = new HashMap<String, String>();
		actualToExpectedValues.put(" ${streamConfig} ", "${streamConfig}");
		actualToExpectedValues.put(" ${stream Config}    ",  "${stream Config}");
		actualToExpectedValues.put("      ",  null);
		for (String actualValue : actualToExpectedValues.keySet()) {
			mockRun = createMockRun(listener);
			Mockito.doReturn(new EnvVars()).when(mockRun).getEnvironment(listener);
			assertEquals(Helper.parseConfigurationValue(mockRun, RTCJobProperties.RTC_BUILD_STREAM, actualValue, false, listener), 
								actualToExpectedValues.get(actualValue));
		}
	}

	@Rule public JenkinsRule r = new JenkinsRule();
	/**
	 * Check whether RTCScm can accept build definition value from the "rtcBuildDefinition" predefined parameter
	 * @throws Exception
	 */
	@Test public void testBuildDefinitionWithPredefinedParameter() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();
		String buildDefinitionId = getTestName() + System.currentTimeMillis();
		//String baselineSetName = "Snapshot_" + getTestName() + "_" + System.currentTimeMillis();
		
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
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
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "", null, null, null), false);
			String[] result = verifyBuildDefinitionWithParameter(r, rtcScm1, new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, buildDefinitionId)), componentName);
			setupArtifacts.put("buildResultItemId1", result[0]);
			
			/*RTCScm rtcScm2 = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "${definitionId}", null, null, null), false);
			result = verifyBuildDefinitionWithParameter(r, rtcScm2, new ParametersAction(new StringParameterValue("definitionId", buildDefinitionId)), componentName);
			setupArtifacts.put("buildResultItemId2", result[0]);*/
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
	@Test public void testBuildDefinitionPollingWithPredefinedParameterAndNonExistentBDFail() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();
		String buildDefinitionId = getTestName() + System.currentTimeMillis();
		String buildDefinitionIdNotPresent = "testBuildDefinitionNotPresent";
		//String baselineSetName = "Snapshot_" + getTestName() + "_" + System.currentTimeMillis();
		
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
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
		Scanner scanner = null;
		try {
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", buildDefinitionIdNotPresent, null, null, null), false);
			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null,  new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, buildDefinitionId)));
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
			
			File pollingFile = File.createTempFile("tmp", ".log");
			PollingResult pollResult = prj.poll(new StreamTaskListener(pollingFile, Charset.forName("ASCII")));
			
			// If there is any error during polling, it can be seen in the log file
			assertNotNull(getMatch(pollingFile, "FATAL: RTC : checking for changes failure: Unable to find a build definition with ID: \"" + buildDefinitionIdNotPresent + "\"$"));
			assertEquals(PollingResult.NO_CHANGES, pollResult);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
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
	@Test public void buildDefinitionPollingWithPredefinedParameterAndEmptyBDFail() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();
		String buildDefinitionId = getTestName() + System.currentTimeMillis();
		//String baselineSetName = "Snapshot_" + getTestName() + "_" + System.currentTimeMillis();
		
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
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
		Scanner scanner = null;
		try {
			// Set the toolkit
			RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
			r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
			RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
					defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", "", null, null, null), false);
			// Setup
			FreeStyleProject prj = r.createFreeStyleProject();
			prj.setScm(rtcScm);

			// Test
			QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null,  new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_DEFINITION, buildDefinitionId)));
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

			File pollingFile = File.createTempFile("tmp", ".log");
			PollingResult pollResult = prj.poll(new StreamTaskListener(pollingFile, Charset.forName("ASCII")));

			// If there is any error during polling, it can be seen in the log file
			assertNotNull(getMatch(pollingFile, "FATAL: RTC : checking for changes failure: More than one repository workspace has the name \"\"$"));
			assertEquals(PollingResult.NO_CHANGES, pollResult);
		} finally {
			if (scanner != null) {
				scanner.close();
			}
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

	private String[] verifyBuildDefinitionWithParameter(JenkinsRule r, RTCScm rtcScm, ParametersAction pAction, String componentName) throws Exception {
		String [] result = new String[2];
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		// Test
		QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null,pAction);
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
		result[0] = action.getBuildResultUUID();

		// Verify snapshot getting created
		String baselineSetItemId = action.getBuildProperties().get("team_scm_snapshotUUID");
		assertNotNull(baselineSetItemId);
		result[1] = baselineSetItemId;

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
	
	private Map<String,String> getTestValues() {
		// A proper value for the parameter
		// An empty value for the parameter
		// A null value for the parameter
		// Value with only whitespace
		// Value with whitespace around edges
		HashMap<String, String> testToExpected = new HashMap<String, String>();
		testToExpected.put("ABCD", "ABCD");
		testToExpected.put("", null);
		testToExpected.put("     ", null);
		testToExpected.put(" ABCD   ", "ABCD");
		return testToExpected;
	}
	
	private Run<?,?> createMockRun(TaskListener listener) throws IOException, InterruptedException {
		Run<?,?> mockR = Mockito.mock(Run.class);
		EnvVars v = new EnvVars();
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		return mockR;
	}
	
	private String getTestName() {
		return this.getClass().getName();
	}
	
	private String getMatch(File file, String pattern) throws FileNotFoundException {
        Scanner scanner = null;
        String match = null;
        try {
        	scanner = new Scanner(file, "ASCII");
        	scanner.useDelimiter("\\n");
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
