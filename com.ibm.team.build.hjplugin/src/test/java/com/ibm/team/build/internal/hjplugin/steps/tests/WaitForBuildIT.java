/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.steps.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.steps.WaitForBuildStepExecution;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildState;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildStatus;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

/**
 * Integration tests for {@link WaitForBuildStepExecution}
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WaitForBuildIT extends AbstractRTCBuildStepTest {

	private String waitForBuildFragment = 
				"task: [buildResultUUID: \"Test1\",  name: 'waitForBuild']";
	private String prefix = "waitForBuild";

	
	@Rule
	public JenkinsRule rule = new JenkinsRule();

	@Before
	public void setup() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
		createSandboxDirectory();
		installBuildToolkitIntoJenkins();
	}
	
	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
	}
	

	@Test
	public void testWaitForBuildNoServerURI() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestNoServerURI(rule, prefix, waitForBuildFragment);
	}

	@Test
	public void testWaitForBuildMissingCreds() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingCreds(rule, prefix, waitForBuildFragment);
	}
	
	@Test
	public void testWaitForBuildMissingBuildToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingBuildToolkit(rule, prefix, waitForBuildFragment);
	}
	
	@Test
	public void testWaitForBuildBuildResultUUIDValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		
		// Empty build result UUID
		WorkflowJob j = setupWorkflowJob(rule);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
				+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), "");
		setupFlowDefinition(j, rtcBuildStep);
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.
				RTCBuildStep_missing_buildResultUUID()));
		Utils.dumpLogFile(run, "waitForBuild", "emptyBuildResultUUID", ".log");
		
		// No build result UUID
		rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
				+ " serverURI: '%s', task: [name: 'waitForBuild'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI());
		setupFlowDefinition(j, rtcBuildStep);
		
		run = requestJenkinsBuild(j);
		log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.
				RTCBuildStep_missing_buildResultUUID()));
		Utils.dumpLogFile(run, "waitForBuild", "missingBuildResultUUID", ".log");
	}
	
	@Test
	public void testWaitForBuildBuildStatesValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		WorkflowJob j = setupWorkflowJob(rule);

		String buildResultUUID = UUID.randomUUID().toString();
		// Empty build states
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
				+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild', buildStates: '' "
				+ "], timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), buildResultUUID);
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_build_states()));
		Utils.dumpLogFile(run, "waitForBuild", "emptyBuildStates", ".log");
		
		// One or more invalid states
		rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
				+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild', "
				+ "buildStates: 'IN_PROGRESS,XYZ,COMPLETED,ABC' "
				+ "], timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), buildResultUUID);
		setupFlowDefinition(j, rtcBuildStep);
		
		run = requestJenkinsBuild(j);
		
		log = getLog(run);
		Assert.assertTrue(log, log.contains(
				Messages.RTCBuildStep_invalid_build_states_2(
						String.join(",", new String[] {"XYZ", "ABC"}))));
		Utils.dumpLogFile(run, "waitForBuild", "invalidBuildStates", ".log");
	}

	@Test
	public void testWaitForBuildTimeoutValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		WorkflowJob j = setupWorkflowJob(rule);

		String buildResultUUID = UUID.randomUUID().toString();

		// timeout of 0
		{
			String invalidWaitBuildTimeout = "0";
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
					+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild', waitBuildTimeout : %s"
					+ "], timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
					buildResultUUID, invalidWaitBuildTimeout);
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(
					Messages.RTCBuildStep_invalid_waitBuildTimeout(invalidWaitBuildTimeout)));
			Utils.dumpLogFile(run, "waitForBuild", "invalidWaitBuildTimeout"+ invalidWaitBuildTimeout, ".log");
		}
		
		// timeout of -99
		{
			String invalidWaitBuildTimeout2 = "-99";
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
					+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild', waitBuildTimeout : %s"
					+ "], timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(), 
					buildResultUUID, invalidWaitBuildTimeout2);
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(
					Messages.RTCBuildStep_invalid_waitBuildTimeout(invalidWaitBuildTimeout2)));
			Utils.dumpLogFile(run, "waitForBuild", "invalidWaitBuildTimeout" + invalidWaitBuildTimeout2, ".log");
		}
	}
	
	@Test
	public void testWaitForBuildIntervalValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);
		WorkflowJob j = setupWorkflowJob(rule);

		String buildResultUUID = UUID.randomUUID().toString();

		// interval of 0, -23
		{
			int [] intervals = { 0 , -23};
			for (int i = 0 ; i < intervals.length; i++ ) {
			
				String invalidWaitBuildInterval = Integer.toString(intervals[i]);
				String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
						+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild', waitBuildInterval : %s"
						+ "], timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
						buildResultUUID, invalidWaitBuildInterval);
				setupFlowDefinition(j, rtcBuildStep);
				
				WorkflowRun run = requestJenkinsBuild(j);
				String log = getLog(run);
				Assert.assertTrue(log, log.contains(
						Messages.RTCBuildStep_invalid_waitBuildInterval(invalidWaitBuildInterval)));
				Utils.dumpLogFile(run, "waitForBuild", "invalidWaitBuildInterval" + invalidWaitBuildInterval
								+ i, ".log");
			}
		}
	
		// Interval greater than timeout
		{
			String invalidWaitBuildInterval = "50";
			String waitBuildTimeout = "30";
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s',"
					+ " serverURI: '%s', task: [buildResultUUID: '%s',  name: 'waitForBuild', "
					+ "waitBuildTimeout : %s, waitBuildInterval : %s"
					+ "], timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
					buildResultUUID, waitBuildTimeout, invalidWaitBuildInterval);
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(
					Messages.RTCBuildStep_invalid_waitBuildIntervalGreater(invalidWaitBuildInterval, waitBuildTimeout)));
			Utils.dumpLogFile(run, "waitForBuild", "invalidWaitBuildIntervalGreater", ".log");
		}
		
	}

	
	/**
	 * Test if waitForBuild succeeds based on timeout of 2 minutes
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testWaitForBuildAPITimeoutSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		// Setup
		Map<String, String> setupArtifacts = setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildTimeoutSuccess");
		String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
		try {
			// Test
			Map<String, String> result  = waitForBuild( 
					buildResultUUID, Helper.DEFAULT_BUILD_STATES, 120);

			// Assert
			// Validate that the task indeed timedout
			assertNotNull(result.get("timedout"));
			assertEquals(true, Boolean.parseBoolean(result.get("timedout")));
		} finally {
			tearDown(setupArtifacts);
		}
	}
	
	/**
	 * Test whether waitForBuild waits until the build state changes and doesn't 
	 * timeout. It should wait till the build is either abandoned or completed.
	 *  
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testWaitForBuildAPIStateChangeSuccessAbandoned() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Map<String, String> setupArtifacts = null;
		try {
			// Setup
			setupArtifacts = setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildStateChangeSuccessAbandoned");
			String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
			// Abandon the build result
			abandonBuild(buildResultUUID);

			// Test
			Map<String, String> result  = waitForBuild(buildResultUUID, 
							Helper.DEFAULT_BUILD_STATES, Helper.DEFAULT_WAIT_BUILD_TIMEOUT);

			// Assert 
			validateWaitForBuildResponse(result, false, RTCBuildState.INCOMPLETE, RTCBuildStatus.OK);
			
		} finally {
			tearDown(setupArtifacts);
		}
		
		try {
			setupArtifacts = setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildStateChangeSuccessCompleted");
			String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);

			// Complete the build result
			completeBuild(buildResultUUID);
		
			// Test
			Map<String, String> result  = waitForBuild(buildResultUUID, 
							Helper.DEFAULT_BUILD_STATES, Helper.DEFAULT_WAIT_BUILD_TIMEOUT);

			// Assert 
			assertNotNull(result.get("timedout"));
			assertEquals(false, Boolean.parseBoolean(result.get("timedout")));
			assertEquals(RTCBuildState.COMPLETED.toString(), result.get("buildState"));
			assertEquals(RTCBuildStatus.OK.toString(), result.get("buildStatus"));
		} finally {
			tearDown(setupArtifacts);
		}
	}

	/**
	 * 
	 * Test waitForBuild throws an exception if there is an invalid build result UUID.
	 * There is no such item with that UUID.
	 *  
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testWaitForBuildAPIInvalidBuildResultUUIDFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Map<String, String> setupArtifacts = 
							setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildInvalidBuildResultUUIDFailure");
		String invalidBuildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDDEFINITION_ITEM_ID);
		String exceptionMessage = 
				String.format("Build Result with id \"%s\" could not be found. It may have been deleted.",
						invalidBuildResultUUID);
		try {
			waitForBuild( 
					invalidBuildResultUUID,
					Helper.DEFAULT_BUILD_STATES, Helper.DEFAULT_WAIT_BUILD_TIMEOUT);
			fail(String.format("Expected an exception to be thrown for invalid build result UUID %s", 
					invalidBuildResultUUID));
		} catch (Exception exp) {
			assertTrue(exp.getMessage(), exp.getMessage().contains(exceptionMessage));
			File f = File.createTempFile("exp", "testWaitForBuildInvalidBuildResultUUIDFailure");
			writeExpDetails(f, exp);
		} finally {
			tearDown(setupArtifacts);
		}
	}
	
	/**
	 * If build definition has no supporting engines, then request build does fail 
	 * but returns an empty buildResultUUID.
	 *  
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testWaitForBuildAPIInvalidBuildStateFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Map<String, String> setupArtifacts = setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildInvalidBuildStateFailure");
		String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
		String invalidBuildState = "abcd";
		String exceptionMessage = String.format("Invalid build state \"%s\" was provided.", invalidBuildState); 
		try {
			waitForBuild(buildResultUUID,
							new String[] {invalidBuildState, RTCBuildState.COMPLETED.toString()}, 
							Helper.DEFAULT_WAIT_BUILD_TIMEOUT);
			fail(String.format("Expected an exception to be thrown for invalid build state %s", 
							invalidBuildState));
		} catch (Exception exp) {
			assertTrue(exp.getMessage(), exp.getMessage().contains(exceptionMessage));
			File f = File.createTempFile("exp", "testWaitForBuildInvalidBuildStateFailure");
			writeExpDetails(f, exp);
		} finally {
			tearDown(setupArtifacts);
		}
	}
	
	/**
	 * waitForBuild fails if the timeout value is negative or zero but not -1.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testWaitForBuildAPIInvalidWaitBuildTimeoutFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Map<String, String> setupArtifacts = setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildInvalidWaitBuildTimeoutFailure");
		String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
		try {
			String timeoutInvalidMessage = "The timeout value \"%d\" provided for waiting on a build is invalid.";
			int [] invalidTimeoutValues = new int [] {-99, -23, 0};
			for (int invalidTimeoutValue : invalidTimeoutValues) {
				try {
					waitForBuild(buildResultUUID, Helper.DEFAULT_BUILD_STATES, invalidTimeoutValue);
					fail(String.format("Expected an exception to be thrown for timeout value %d",invalidTimeoutValue));
				} catch (Exception exp) {
					assertTrue(exp.getMessage(), exp.getMessage().contains(
							String.format(timeoutInvalidMessage, invalidTimeoutValue)));
				}
			}
		} finally {
			tearDown(setupArtifacts);
		}
	}
	
	/**
	 * Let the build wait for ever (3 minutes) and cancel the build after that in a 
	 * separate thread. Timedout value should be <code>false</code>. 
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testWaitForBuildAPIInfiniteWaitSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		long maxWaitTimeoutSeconds = 180;
		Map<String, String> setupArtifacts = setupBuildDefinitionWithoutSCMAndResult("testWaitForBuildInfiniteWaitSuccess");
		final String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
		try {
			Thread t = new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						Thread.sleep(maxWaitTimeoutSeconds *1000);
						// Cancel the build request
						cancelBuild(buildResultUUID);
					} catch (Exception exp) {
						// Can't do much about the exception
					}
				}
			});
			t.start();
			long startTime = System.currentTimeMillis();
			Map<String, String> result  = waitForBuild(buildResultUUID,
								new String[] {RTCBuildState.CANCELED.toString()},
								Helper.DEFAULT_WAIT_BUILD_TIMEOUT);
			// Assert 
			validateWaitForBuildResponse(result, false, RTCBuildState.CANCELED, 
								RTCBuildStatus.OK);
			long endTime = System.currentTimeMillis();
			assertTrue("Waited for more than 3 minutes which is unexpected", 
						(endTime - startTime) >= (1000 * maxWaitTimeoutSeconds));
		} finally {
			tearDown(setupArtifacts);
		}
	}

	/**
	 * Wait for the given build result UUID for the given build states and wait timeout
	 * 
	 * @param buildResultUUID      The build result UUID to wait on
	 * @param buildStates          The build states to wait on    
	 * @param waitBuildTimeout     The wait timeout
	 * @return                    
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> waitForBuild(
			final String buildResultUUID, String[] buildStates, long waitBuildTimeout) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();

		return (Map<String, String>) testingFacade
				.invoke("waitForBuild", 
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								String.class, // buildResultUUID
								Object.class, // build states string array
								long.class, // wait build timeout
								long.class, // wait build interval 
								boolean.class, // debug flag
								Object.class, // listener
								Locale.class }, // clientLocale
						defaultC.getServerURI(), loginInfo.getUserId(), 
						loginInfo.getPassword(), defaultC.getTimeout(), 
						buildResultUUID, buildStates, waitBuildTimeout,
						Helper.DEFAULT_WAIT_BUILD_INTERVAL,
						false,
						new TaskListenerWrapper(getTaskListener()),
						Locale.getDefault());
	}

	/**
	 * Utility method to tearDown the artifacts setup
	 * 
	 * @param setupArtifacts
	 * @throws Exception
	 */
	private void tearDown(Map<String, String> setupArtifacts) throws Exception {
		
		if (setupArtifacts == null) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		Utils.tearDown(testingFacade, defaultC, setupArtifacts);
	}
	
	/**
	 * Utility method to validate the response from waitForBuild 
	 * 
	 * @param result          The build result UUID to validate
	 * @param timeout         Whether waitForBuild timed out or not
	 * @param expectedBuildState      The expected state of the build.
	 * @param expectedBuildStatus     The expected status of the build.
	 */
	private void validateWaitForBuildResponse(Map<String, String> result, boolean timeout, 
			RTCBuildState expectedBuildState, RTCBuildStatus expectedBuildStatus) {
		assertNotNull(result.get("timedout"));
		assertEquals(timeout, Boolean.parseBoolean(result.get("timedout")));
		assertEquals(expectedBuildState.toString(), result.get("buildState"));
		assertEquals(expectedBuildStatus.toString(), result.get("buildStatus"));
	}
}
