/*******************************************************************************
 * Copyright © 2016, 2018 IBM Corporation and others.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.mockito.Mockito;

import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.util.Helper;

import hudson.EnvVars;
import hudson.model.BooleanParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;

/**
 * Tests for {@link Helper} class
 *
 */
@SuppressWarnings({"nls", "static-method"})
public class HelperTest extends AbstractTestCase {
	
	
	private Run<?,?> createMockRun(TaskListener listener) throws IOException, InterruptedException {
		Run<?,?> mockR = Mockito.mock(Run.class);
		EnvVars v = new EnvVars();
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		return mockR;
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

	/**
	 * Precedence to predefined parameter
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_NonNullExistsAndPlainConfigurationValue_ReturnsParameterValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpected = new HashMap<String, String>();
		testToExpected.put("ABCD", "ABCD");
		testToExpected.put(" ABCD   ", "ABCD");
		Set<String> testValues = testToExpected.keySet();
		String configurationValue = "testBuildSnapshot";
		for (String testValue : testValues) {
			EnvVars v = new EnvVars();
			v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, testValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, configurationValue, listener);
			assertEquals(testToExpected.get(testValue), actualValue);
		}
	}
	
	/**
	 * Precedence to predefined parameter
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_NonNullExistsAndConfigurationValueAsParam_ReturnsParameterValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpected = new HashMap<String, String>();
		testToExpected.put("ABCD", "ABCD");
		testToExpected.put(" ABCD   ", "ABCD");
		Set<String> testValues = testToExpected.keySet();
		String configurationValue = "${mySnapshotParameter}";
		for (String testValue : testValues) {
			EnvVars v = new EnvVars();
			v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, testValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, configurationValue, listener);
			assertEquals(testToExpected.get(testValue), actualValue);
		}
	}
	
	/**
	 * If predefined parameter's value is full of whitespace, then return the configuration value trimmed
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_NonNullExistsOnlyWS_ReturnsConfigurationValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedConfigurationValues = new HashMap<String, String>();
		testToExpectedConfigurationValues.put("ABCD", "ABCD");
		testToExpectedConfigurationValues.put(" ABCD   ", "ABCD");
		testToExpectedConfigurationValues.put("   ", null);

		Set<String> testConfigurationValues = testToExpectedConfigurationValues.keySet();
		String predefinedParameterValue =  "                   ";
		for (String testConfigurationValue : testConfigurationValues) {
			EnvVars v = new EnvVars();
			v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, predefinedParameterValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			// Test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, testConfigurationValue, listener);
			
		    // Verify
			assertEquals(testToExpectedConfigurationValues.get(testConfigurationValue), actualValue);
		}
	}

	/**
	 * If predefined parameter's value is full of whitespace, and configuration value is a parameter, then resolve configuration value trimmed
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_NonNullExistsOnlyWS_ReturnsConfigurationValueAsResolvedTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);

		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String predefinedParametername = RTCJobProperties.RTC_BUILD_SNAPSHOT;
		String predefinedParameterValue =  "                   ";
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";
		for (String testParameterValue : testParameterValues) {
			EnvVars v = new EnvVars();
			v.put(predefinedParametername, predefinedParameterValue);
			v.put(userDefinedParameterName, testParameterValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterNameForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}

	/**
	 * If predefined parameter is not defined, then return configuration value trimmed
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_NonNullNotExists_ReturnsConfigurationValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedConfigurationValues = new HashMap<String, String>();
		testToExpectedConfigurationValues.put("ABCD", "ABCD");
		testToExpectedConfigurationValues.put(" ABCD   ", "ABCD");
		testToExpectedConfigurationValues.put("    ", null);
		Set<String> testConfigurationValues = testToExpectedConfigurationValues.keySet();
		for (String testConfigurationValue : testConfigurationValues) {
			EnvVars v = new EnvVars();
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			// Test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, testConfigurationValue, listener);
			
		    // Verify
			assertEquals(testToExpectedConfigurationValues.get(testConfigurationValue), actualValue);
		}	
	}
	
	/**
	 * If predefined parameter is not defined, and configuration value is a parameter, then resolve that parameter and return the value trimmed
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_NonNullNotExists_ReturnsConfigurationValueResolved() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";

		for (String testParameterValue : testParameterValues) {
			EnvVars v = new EnvVars();
			v.put(userDefinedParameterName, testParameterValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterNameForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}
	
	/**
	 * If predefined parameter is null, then configuration value is returned trimmed
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_Null_ReturnsConfigurationValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedConfigurationValues = new HashMap<String, String>();
		testToExpectedConfigurationValues.put("ABCD", "ABCD");
		testToExpectedConfigurationValues.put(" ABCD   ", "ABCD");
		testToExpectedConfigurationValues.put("    ", null);
		Set<String> testConfigurationValues = testToExpectedConfigurationValues.keySet();
		for (String testConfigurationValue : testConfigurationValues) {
			EnvVars v = new EnvVars();
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			// Test
			String actualValue = Helper.parseConfigurationValue(mockR, null, testConfigurationValue, listener);
			
		    // Verify
			assertEquals(testToExpectedConfigurationValues.get(testConfigurationValue), actualValue);
		}
	}
	
	/**
	 * If predefined parameter is null, and configuration value is parameter, then it is resolved
	 * and value is returned trimmed
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterFreeStyle_Null_ReturnsConfigurationValueResolved() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";

		for (String testParameterValue : testParameterValues) {
			EnvVars v = new EnvVars();
			v.put(userDefinedParameterName, testParameterValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			String actualValue = Helper.parseConfigurationValue(mockR, null, userDefinedParameterNameForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}
	
	/**
	 * If predefined parameter is defined, then return value trimmed. This takes precedence over configuration value
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_NonNullExistsWithPlainConfigurationValue_ReturnsParameterValue() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpected = new HashMap<String, String>();
		testToExpected.put("ABCD", "ABCD");
		testToExpected.put(" ABCD   ", "ABCD");
		Set<String> testValues = testToExpected.keySet();
		String configurationValue = "testBuildSnapshot";
		for (String testValue : testValues) {
			// Add a parameters action for the predefined parameter
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, testValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, configurationValue, listener);
			assertEquals(testToExpected.get(testValue), actualValue);
		}
	}

	/**
	 * If predefined parameter is defined, then return value trimmed. This takes precedence over configuration value as parameter
	 * @throws Exception
	 */
	@Test public void parseConfigurationValuePredefinedParameterWorkflow_NonNullExistsWithConfigurationValueAsParam_ReturnsParameterValue() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpected = new HashMap<String, String>();
		testToExpected.put("ABCD", "ABCD");
		testToExpected.put(" ABCD   ", "ABCD");
		Set<String> testValues = testToExpected.keySet();
		String configurationValue = "${mySnapshotParameter}";
		for (String testValue : testValues) {
			// Add a parameters action for the predefined parameter
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, testValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, configurationValue, listener);
			assertEquals(testToExpected.get(testValue), actualValue);
		}	
	}
	
	 /**
	  * If predefined parameter is defined but full of whitespace, then configuration value is returned trimmed
	  * 
	  * @throws Exception
	  */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_NonNullExistsOnlyWS_ReturnsConfigurationValueAsIs() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedConfigurationValues = new HashMap<String, String>();
		testToExpectedConfigurationValues.put("ABCD", "ABCD");
		testToExpectedConfigurationValues.put(" ABCD   ", "ABCD");
		testToExpectedConfigurationValues.put("   ", null);

		Set<String> testConfigurationValues = testToExpectedConfigurationValues.keySet();
		String predefinedParameterValue =  "                   ";
		for (String testConfigurationValue : testConfigurationValues) {
			// Add a parameters action for the predefined parameter
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, predefinedParameterValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			// Test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, testConfigurationValue, listener);
			
		    // Verify
			assertEquals(testToExpectedConfigurationValues.get(testConfigurationValue), actualValue);
		}
	}
	
	/**
	  * If predefined parameter is defined but full of whitespace and configuration value is a parameter,
	  * then it is resolved and value is trimmed before retuning.
	  * 
	  * @throws Exception
	  */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_NonNullExistsOnlyWS_ReturnsConfigurationValueAsResolved() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);

		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String predefinedParametername = RTCJobProperties.RTC_BUILD_SNAPSHOT;
		String predefinedParameterValue =  "                   ";
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";
		for (String testParameterValue : testParameterValues) {
			// Add a parameters action for the predefined parameter
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(predefinedParametername, predefinedParameterValue)));
			actions.add(new ParametersAction(new StringParameterValue(userDefinedParameterName, testParameterValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			String actualValue = Helper.parseConfigurationValue(mockR, predefinedParametername, userDefinedParameterNameForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}

	/**
	 * If predefined parameter does not exist, then configuration value is returned trimmed.
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_NonNullNotExists_ReturnsConfigurationValueAsIs() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedConfigurationValues = new HashMap<String, String>();
		testToExpectedConfigurationValues.put("ABCD", "ABCD");
		testToExpectedConfigurationValues.put(" ABCD   ", "ABCD");
		testToExpectedConfigurationValues.put("    ", null);
		Set<String> testConfigurationValues = testToExpectedConfigurationValues.keySet();
		for (String testConfigurationValue : testConfigurationValues) {
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			// Test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, testConfigurationValue, listener);
			
		    // Verify
			assertEquals(testToExpectedConfigurationValues.get(testConfigurationValue), actualValue);
		}	
	}

	/**
	 * If predefined parameter does not exist, and configuration value is a parameter then it is resolved and 
	 * value is trimmed.
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_NonNullNotExists_ReturnsConfigurationValueResolved() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String predefinedParametername = RTCJobProperties.RTC_BUILD_SNAPSHOT;
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";

		for (String testParameterValue : testParameterValues) {
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(userDefinedParameterName, testParameterValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			String actualValue = Helper.parseConfigurationValue(mockR, predefinedParametername, userDefinedParameterNameForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}
	
	/**
	 * If predefined parameter is null, then configuration value is returned as is
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_Null_ReturnsConfigurationValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedConfigurationValues = new HashMap<String, String>();
		testToExpectedConfigurationValues.put("ABCD", "ABCD");
		testToExpectedConfigurationValues.put(" ABCD   ", "ABCD");
		testToExpectedConfigurationValues.put("    ", null);
		Set<String> testConfigurationValues = testToExpectedConfigurationValues.keySet();
		for (String testConfigurationValue : testConfigurationValues) {
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			// Test
			String actualValue = Helper.parseConfigurationValue(mockR, null, testConfigurationValue, listener);
			
		    // Verify
			assertEquals(testToExpectedConfigurationValues.get(testConfigurationValue), actualValue);
		}
	}
	
	/**
	 * If predefined parameter is null, and configuration value is a parameter, then it is resolved
	 * before returning
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValuePredefinedParameterWorkflow_Null_ReturnsConfigurationValueResolvedTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";

		for (String testParameterValue : testParameterValues) {
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(userDefinedParameterName, testParameterValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			String actualValue = Helper.parseConfigurationValue(mockR, null, userDefinedParameterNameForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}
	
	/**
	 * If configuration value is a parameter and is surrounded by whitespace and non null, then it is resolved to value and whitespace
	 * trimmed before returning.
	 * @throws Exception
	 */
	@Test
	public void parseConfigurationValueConfigurationValueIsParamFreeStyle_ParamHasWSAndExistsNonNull_ReturnsValueResolvedWithWSTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValueWithWS = "   ${myBuildSnapshot}   ";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";

		for (String testParameterValue : testParameterValues) {
			EnvVars v = new EnvVars();
			v.put(userDefinedParameterName, testParameterValue);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			
			String actualValue = Helper.parseConfigurationValue(mockR, null, userDefinedParameterNameForConfigValueWithWS, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}

	@Test
	public void parseConfigurationValueConfigurationValueIsParamWorkflow_ParamHasWSAndExistsNonNull_ReturnsValueResolvedWithWSTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		String userDefinedParameterName = "myBuildSnapshot";
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";
		String userDefinedParameterNameForConfigValueWithWs = "    ${myBuildSnapshot}   ";

		for (String testParameterValue : testParameterValues) {
			ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
			actions.add(new ParametersAction(new StringParameterValue(userDefinedParameterName, testParameterValue)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			
			String actualValue = Helper.parseConfigurationValue(mockR, null, userDefinedParameterNameForConfigValueWithWs, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterNameForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
		
	}

	@Test
	public void parseConfigurationValueConfigurationValueIsParam_NotExists_ReturnsConfigurationValueAsParamWithWSTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		String userDefinedParameterNameForConfigValue = "${myBuildSnapshot}";
		String userDefinedParameterNameForConfigValueWithWS = "   ${myBuildSnapshot}   ";
			
		String actualValue = Helper.parseConfigurationValue(mockR, null, userDefinedParameterNameForConfigValueWithWS, listener);
		assertEquals(userDefinedParameterNameForConfigValue, actualValue);
	}
	
	@Test
	public void parseConfigurationValueConfigurationValue_Null_ReturnsNull() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		String actualValue = Helper.parseConfigurationValue(mockR, null, null, listener);
		assertEquals(null, actualValue);
	}
	
	@Test
	public void parseConfigurationValueConfigurationValue_OnlyWS_ReturnsNull() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		String actualValue = Helper.parseConfigurationValue(mockR, null, "              ", listener);
		assertEquals(null, actualValue);	
	}
	
	@Test
	public void parseConfigurationValueConfigurationValue_WSAtEnds_ReturnsConfigValueTrimmed() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		String actualValue = Helper.parseConfigurationValue(mockR, null, " abcdefgh  ", listener);
		assertEquals("abcdefgh", actualValue);	
	}
	
	@Test
	public void parseConfigurationValueForJobConfigurationValue_Null_ReturnsNull() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);

		String actualValue = Helper.parseConfigurationValue(mockJob, null, listener);
		assertEquals(null, actualValue);
	}
	
	@Test
	public void parseConfigurationValueForJobConfigurationValue_OnlyWS_ReturnsNull() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);

		String actualValue = Helper.parseConfigurationValue(mockJob, "    ", listener);
		assertEquals(null, actualValue);
	}
	
	@Test
	public void parseConfigurationValueForJobConfigurationValue_NonNullWSAtEnds_ReturnsValueTrimmed() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);

		String actualValue = Helper.parseConfigurationValue(mockJob, "  abcdefgh  ", listener);
		assertEquals("abcdefgh", actualValue);
	}
	
	@Test
	public void parseConfigurationValueForJobConfigurationValueIsParam_NonNull_ReturnsValueTrimmed() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);
		
		String userDefinedParameter = "testBuildDefinition";
		String userDefinedParameterForConfigValue = "${testBuildDefinition}";
		
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		
		for (String testParameterValue : testParameterValues) {
			ParametersDefinitionProperty property = new ParametersDefinitionProperty(
				Arrays.asList(new ParameterDefinition[] { new StringParameterDefinition(userDefinedParameter, testParameterValue) }));
			Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
			
			String actualValue = Helper.parseConfigurationValue(mockJob, userDefinedParameterForConfigValue, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}
	
	@Test
	public void parseConfigurationValueForJobConfigurationValueIsParam_HasWSAtEndsAndNonNull_ReturnsValueTrimmed() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);
		
		String userDefinedParameter = "testBuildDefinition";
		String userDefinedParameterForConfigValueWithWS = "  ${testBuildDefinition}   ";
		String userDefinedParameterForConfigValue = "${testBuildDefinition}";
		
		HashMap<String, String> testToExpectedParameterValues = new HashMap<String, String>();
		testToExpectedParameterValues.put("ABCD", "ABCD");
		testToExpectedParameterValues.put(" ABCD   ", "ABCD");
		testToExpectedParameterValues.put("   ", null);
		
		Set<String> testParameterValues = testToExpectedParameterValues.keySet();
		
		for (String testParameterValue : testParameterValues) {
			ParametersDefinitionProperty property = new ParametersDefinitionProperty(
				Arrays.asList(new ParameterDefinition[] { new StringParameterDefinition(userDefinedParameter, testParameterValue) }));
			Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
			
			String actualValue = Helper.parseConfigurationValue(mockJob, userDefinedParameterForConfigValueWithWS, listener);
			if (testToExpectedParameterValues.get(testParameterValue) == null) {
				assertEquals(userDefinedParameterForConfigValue, actualValue);
			} else {
				assertEquals(testToExpectedParameterValues.get(testParameterValue), actualValue);
			}
		}
	}

	@Test
	public void parseConfigurationValueForJobConfigurationValueIsParam_Null_ReturnsParameterName() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);
		String userDefinedParameterForConfigValue = "${testBuildDefinition}";
		String actualValue = Helper.parseConfigurationValue(mockJob, userDefinedParameterForConfigValue, listener);
		assertEquals(userDefinedParameterForConfigValue, actualValue);
	}
	
	@Test
	public void parseConfigurationValueForJobConfigurationValueIsParam_HasWsAtEndsAndNull_ReturnsParameterName() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);
		String userDefinedParameterForConfigValueWithWS = "  ${testBuildDefinition}   ";
		String userDefinedParameterForConfigValue = "${testBuildDefinition}";
		String actualValue = Helper.parseConfigurationValue(mockJob, userDefinedParameterForConfigValueWithWS, listener);
		assertEquals(userDefinedParameterForConfigValue, actualValue);
	}


	/**
	 * Check whether Helper can correctly parse predefined parameter from {@link EnvVars} in {@link Run}
	 * 
	 * 	 * @throws Exception
	 */	 
	 @Test public void testParseConfigurationValueForPredefinedParameterFromEnvironment() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockR = createMockRun(listener);
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		// The user has provided value for predefined parameter.
		// For every test value, setup the environment to return a value for the predefined parameter
		// The output from our test method should match the expected value from the map
		for (String s : keySet) {
			EnvVars v = new EnvVars();
			v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, s);
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			// test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, null, listener);
			// verify
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If predefined parameter is empty, return the configuration value as is
		mockR = createMockRun(listener);
		EnvVars v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, "  ");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, "dummy value", listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
		
		// If predefined parameter is not defined, return the configuration value as is
		mockR = createMockRun(listener);
		v = new EnvVars();
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, "dummy value", listener);
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
			actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, s)));
			Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
			// test
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, null, listener);
			// verify
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If predefined parameter is empty, return the configuration value as is
		mockR = createMockRun(listener);
		ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
		actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, "    ")));
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, "dummy value", listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
		
		// If predefined parameter is not defined, return the configuration value as is
		mockR = createMockRun(listener);
		actions = new ArrayList<ParametersAction>();
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, "dummy value", listener);
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
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterAsConfigValue, listener);
			// verify
			// if you are expecting null, then it is actually the userDefinedParameterAsConfigValue
			if (testValueToExpectedValueMap.get(s) == null) {
				assertEquals(userDefinedParameterAsConfigValue, actualValue);
			} else {
				assertEquals(testValueToExpectedValueMap.get(s), actualValue);
			}
		}
		
		// If predefined parameter and user defined parameters are defined, then predefined parameter should take precedence
		mockR = createMockRun(listener);
		EnvVars v = new EnvVars();
		// Define predefined parameter
		v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, "test value");
		// Add user defined parameter
		v.put(userDefinedParameter, "dummy value");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterAsConfigValue, listener);
		// verify that getActions was never called because predefined parameter was resolved from the environment
		Mockito.verify(mockR, Mockito.times(0)).getActions(ParametersAction.class);
		assertEquals("test value", actualValue);
		
		// If predefined parameter is empty, then user defined parameter is used
		mockR = createMockRun(listener);
		v = new EnvVars();
		// Define predefined parameter
		v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, "   ");
		// Add user defined parameter
		v.put(userDefinedParameter, "dummy value");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterAsConfigValue, listener);
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
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterAsConfigValue, listener);
			// verify
			// if you are expecting null, then it is actually the userDefinedParameterAsConfigValue
			if (testValueToExpectedValueMap.get(s) == null) {
				assertEquals(userDefinedParameterAsConfigValue, actualValue);
			} else {
				assertEquals(testValueToExpectedValueMap.get(s), actualValue);
			}
		}
		
		// If both predefined and user defined parameters are present, predefined parameter takes precedence
		mockR = createMockRun(listener);
		ArrayList<ParametersAction> actions = new ArrayList<ParametersAction>();
		// Add a parameters action for the predefined parameter
		actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, "test value")));
		// Add a parameters action for user defined parameter
		actions.add(new ParametersAction(new StringParameterValue(userDefinedParameter, "dummy value")));
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterAsConfigValue, listener);
		// verify
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("test value", actualValue);
		
		// If predefined parameter is empty, then user defined parameter is used
		mockR = createMockRun(listener);
		actions = new ArrayList<ParametersAction>();
		// Add a parameters action for the predefined parameter
		actions.add(new ParametersAction(new StringParameterValue(RTCJobProperties.RTC_BUILD_SNAPSHOT, "   ")));
		// Add a parameters action for user defined parameter
		actions.add(new ParametersAction(new StringParameterValue(userDefinedParameter, "dummy value")));
		Mockito.doReturn(actions).when(mockR).getActions(ParametersAction.class);
		// test
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, userDefinedParameterAsConfigValue, listener);
		// verify that getActions was called twice, once for RTC_BUILD_DEFINITION and another for user defined parameter
		Mockito.verify(mockR, Mockito.times(2)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}
	

	/**
	 * Check whether Helper returns a regular value after trimming for whitespace
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueRegular() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?,?> mockR = createMockRun(listener);		
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();

		// The configuration value is not a property. The values should be returned trimmed
		for (String s : keySet) {
			EnvVars v = new EnvVars();
			Mockito.doReturn(v).when(mockR).getEnvironment(listener);
			String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, s, listener);
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}

		// If a predefined parameter is defined, then that should take precedence over the configuration value
		mockR = createMockRun(listener);
		EnvVars v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, "test value");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		String actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, "dummy value", listener);
		Mockito.verify(mockR, Mockito.times(0)).getActions(ParametersAction.class);
		assertEquals("test value", actualValue);
		
		// If a predefined parameter is empty, then configuration value is used
		mockR = createMockRun(listener);
		v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT, "   ");
		Mockito.doReturn(v).when(mockR).getEnvironment(listener);
		actualValue = Helper.parseConfigurationValue(mockR, RTCJobProperties.RTC_BUILD_SNAPSHOT, "dummy value", listener);
		Mockito.verify(mockR, Mockito.times(1)).getActions(ParametersAction.class);
		assertEquals("dummy value", actualValue);
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueWithConfigurationValueAsParm() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		String streamConfigValue = "stream Config Value";
		
		// Test
		// If a predefined parameter is defined and a configuration value is also present, the predefined parameter is resolved
		Run<?,?> mockRun = createMockRun(listener);
		EnvVars v = new EnvVars();
		v.put(RTCJobProperties.RTC_BUILD_SNAPSHOT,  "test stream");
		Mockito.doReturn(v).when(mockRun).getEnvironment(listener);
		assertEquals("test stream", Helper.parseConfigurationValue(mockRun, RTCJobProperties.RTC_BUILD_SNAPSHOT, streamConfigValue, listener));
		
		// If a predefined parameter is not defined and configuration value is a regular value, it is returned as such
		// Whitespace is trimmed.
		Map<String, String> testValueToExpectedValueMap = getTestValues();
		Set<String> keySet = testValueToExpectedValueMap.keySet();
		for (String s : keySet) {
			mockRun = createMockRun(listener);
			Mockito.doReturn(new EnvVars()).when(mockRun).getEnvironment(listener);
			String actualValue = Helper.parseConfigurationValue(mockRun, RTCJobProperties.RTC_BUILD_SNAPSHOT, s, listener);
			assertEquals(testValueToExpectedValueMap.get(s), actualValue);
		}
		
		// If a predefined parameter is not defined and configuration value is a parameter which is not defined, then it is returned
		// as such but trimmed for whitespace
		Map<String, String> testToExpectedValuesMap = new HashMap<String, String>();
		testToExpectedValuesMap.put(" ${streamConfig} ", "${streamConfig}");
		testToExpectedValuesMap.put(" ${stream Config}    ",  "${stream Config}");
		testToExpectedValuesMap.put("      ",  null);
		for (String testValue : testToExpectedValuesMap.keySet()) {
			mockRun = createMockRun(listener);
			Mockito.doReturn(new EnvVars()).when(mockRun).getEnvironment(listener);
			assertEquals(testToExpectedValuesMap.get(testValue), Helper.parseConfigurationValue(mockRun, RTCJobProperties.RTC_BUILD_SNAPSHOT, testValue, listener));
		}
	}

	/**
	 * 
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueForJobWithParameterAndDefaultValue() throws Exception {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);

		/*
		 * Put a parameterDefinitionproperty inside the job, our method should be able to find the given
		 * parameter inside it and return its default value
		 */
		// Setup
		String buildDefinitionParam = "testBuildDefinition";
		String buildDefinitionParamForValue = "${testBuildDefinition}";
		String buildDefinitionDefaultValue = "testBuildDefinitionDefaultValue";
		ParametersDefinitionProperty property = new ParametersDefinitionProperty(
							Arrays.asList(new ParameterDefinition[] { new StringParameterDefinition(buildDefinitionParam, buildDefinitionDefaultValue) }));
		Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
		
		// Test
		String actualValue = Helper.parseConfigurationValue(mockJob, buildDefinitionParamForValue, listener);
		
		// Verify
		assertEquals(buildDefinitionDefaultValue, actualValue);
		
		/*
		 * Put a non string value inside the parameter and our method should fail to find it
		 * and return the configuration value 
		 */

		// Setup
		property = new ParametersDefinitionProperty(Arrays.asList(
							new ParameterDefinition[] {new BooleanParameterDefinition(buildDefinitionParam, false, "Testing build definition with boolean value")}));
		Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
		
		// Test
		actualValue = Helper.parseConfigurationValue(mockJob, buildDefinitionParamForValue, listener);
		
		// Verify
		assertEquals(buildDefinitionParamForValue, actualValue);
		
		/*
		 * Put a subclass of StringParameterValue into the parameter and our method should not be able find the value
		 */
		// Setup
		class MyOwnParameterDefinition extends StringParameterDefinition {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public MyOwnParameterDefinition(String name, String defaultValue) {
				super(name, defaultValue);
			}
			
		}
		
		buildDefinitionDefaultValue = "testBuildDefinitionDefaultValue";
		property = new ParametersDefinitionProperty(Arrays.asList(
						   new ParameterDefinition[] {new MyOwnParameterDefinition(buildDefinitionParam, buildDefinitionDefaultValue)}));
		Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
		
		// Test
		actualValue = Helper.parseConfigurationValue(mockJob, buildDefinitionParamForValue, listener);
		
		// Verify
		assertEquals(buildDefinitionParamForValue, actualValue);
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueForJobWithParameterAndNoDefaultValue() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);
		String buildDefinitionParam = "testBuildDefinition";
		String buildDefinitionParamForValue = "${testBuildDefinition}";
		ParametersDefinitionProperty property = new ParametersDefinitionProperty(Arrays.asList(
							new ParameterDefinition[] {new StringParameterDefinition(buildDefinitionParam, null)}));
		Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
		
		// Test
		String actualValue = Helper.parseConfigurationValue(mockJob, buildDefinitionParamForValue, listener);
		
		// Verify
		assertEquals(buildDefinitionParamForValue, actualValue);
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test public void testParseConfigurationValueForJobWithNoParameter() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Job<?,?> mockJob = Mockito.mock(Job.class);
		String buildDefinitionParameterForValue = "${buildDefinitionParameter}";
		ParametersDefinitionProperty property = new ParametersDefinitionProperty(Arrays.asList(
							new ParameterDefinition[] {new StringParameterDefinition("dummyParameter", "dummyValue")}));
		Mockito.doReturn(property).when(mockJob).getProperty(ParametersDefinitionProperty.class);
		
		// Test
		String actualValue = Helper.parseConfigurationValue(mockJob, buildDefinitionParameterForValue, listener);
		
		// Verify
		assertEquals(buildDefinitionParameterForValue, actualValue);
	}
	
	@Test public void testResolveCustomSnapshotNameWithStaticText() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockRun = createMockRun(listener);
		String staticTextForCustomSnapshotName = "static-text-for-custom-snapshot-name with spaces";
		
		// Test
		String actualSnapshotName = Helper.resolveCustomSnapshotName(mockRun, staticTextForCustomSnapshotName, listener);
		
		// Verify
		assertEquals(staticTextForCustomSnapshotName, actualSnapshotName);
	}
	
	@Test
	public void testResolveCustomSnapshotNameWithEnvVariables() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockRun = createMockRun(listener);

		Map<String, String> envVarsMap = new HashMap<String, String>();
		envVarsMap.put("JOB_NAME", "Test Job");
		envVarsMap.put("BUILD_NUMBER", "1");
		EnvVars v = new EnvVars(envVarsMap);
		Mockito.doReturn(v).when(mockRun).getEnvironment(listener);

		String customSnapshotNameWithEnvVars = "${JOB_NAME}_#${BUILD_NUMBER}";

		// Test
		String actualSnapshotName = Helper.resolveCustomSnapshotName(mockRun, customSnapshotNameWithEnvVars, listener);

		// Verify
		String expectedSnapshotName = "Test Job_#1";
		assertEquals(expectedSnapshotName, actualSnapshotName);
	}

	@Test
	public void testResolveCustomSnapshotNameWithUndefinedEnvVariables() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		Run<?, ?> mockRun = createMockRun(listener);

		Map<String, String> envVarsMap = new HashMap<String, String>();
		envVarsMap.put("JOB_NAME", "Test Job");
		envVarsMap.put("BUILD_NUMBER", "1");
		EnvVars v = new EnvVars(envVarsMap);
		Mockito.doReturn(v).when(mockRun).getEnvironment(listener);

		String customSnapshotNameWithEnvVars = "${JOB_NAME1}_#${BUILD_NUMBER}";

		// Test
		String actualSnapshotName = Helper.resolveCustomSnapshotName(mockRun, customSnapshotNameWithEnvVars, listener);

		// Verify
		// Verify that the name of the unresolved environment variable is retained
		String expectedSnapshotName = "${JOB_NAME1}_#1";
		assertEquals(expectedSnapshotName, actualSnapshotName);
	}

	@Test
	public void testResolveCustomSnapshotNameWithBuildParameters() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		FreeStyleBuild freeStyleBuild = Mockito.mock(FreeStyleBuild.class);

		Map<String, String> envVarsMap = new HashMap<String, String>();
		envVarsMap.put("JOB_NAME", "Test Job");
		envVarsMap.put("BUILD_NUMBER", "1");
		EnvVars v = new EnvVars(envVarsMap);
		Mockito.doReturn(v).when(freeStyleBuild).getEnvironment(listener);

		Map<String, String> buildVariables = new HashMap<String, String>();
		buildVariables.put("Branch", "CSN_Enhancement");
		buildVariables.put("Release", "1202");
		Mockito.doReturn(buildVariables).when(freeStyleBuild).getBuildVariables();

		String customSnapshotNameWithBuildParams = "1_${JOB_NAME}_${Branch}_${Release}_$$";

		// Test
		String actualSnapshotName = Helper.resolveCustomSnapshotName(freeStyleBuild, customSnapshotNameWithBuildParams, listener);

		// Verify
		String expectedSnapshotName = "1_Test Job_CSN_Enhancement_1202_$$";
		assertEquals(expectedSnapshotName, actualSnapshotName);
	}

	@Test
	public void testResolveCustomSnapshotNameWithUndefinedBuildParameters() throws Exception {
		// Setup
		TaskListener listener = Mockito.mock(TaskListener.class);
		FreeStyleBuild freeStyleBuild = Mockito.mock(FreeStyleBuild.class);

		Map<String, String> envVarsMap = new HashMap<String, String>();
		envVarsMap.put("JOB_NAME", "Test Job");
		envVarsMap.put("BUILD_NUMBER", "1");
		EnvVars v = new EnvVars(envVarsMap);
		Mockito.doReturn(v).when(freeStyleBuild).getEnvironment(listener);

		Map<String, String> buildVariables = new HashMap<String, String>();
		buildVariables.put("Branch", "CSN_Enhancement");
		buildVariables.put("Release", "1202");
		Mockito.doReturn(buildVariables).when(freeStyleBuild).getBuildVariables();

		String customSnapshotNameWithBuildParams = "1_${Branch1}_${Release}";

		// Test
		String actualSnapshotName = Helper.resolveCustomSnapshotName(freeStyleBuild, customSnapshotNameWithBuildParams, listener);

		// Verify
		// Verify that the name of the unresolved build parameter is retained
		String expectedSnapshotName = "1_${Branch1}_1202";
		assertEquals(expectedSnapshotName, actualSnapshotName);
	}
}