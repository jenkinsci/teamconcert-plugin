/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.common.builddefinition.AutoDeliverTriggerPolicy;
import com.ibm.team.build.common.builddefinition.IAutoDeliverConfigurationElement;
import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.BuildStatus;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport;
import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.Constants;
import com.ibm.team.build.internal.hjplugin.rtc.Messages;
import com.ibm.team.build.internal.hjplugin.rtc.RTCBuildUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.hjplugin.rtc.Utils;
import com.ibm.team.build.internal.hjplugin.rtc.tests.utils.Config;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RTCBuildUtilsIT {
	private TestSetupTearDownUtil fTestSetupTearDownUtil;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	
	@Rule
	public TemporaryFolder sandbox = new TemporaryFolder();

	@Rule
	public TemporaryFolder scratchFolder = new TemporaryFolder();

	private synchronized TestSetupTearDownUtil getTestSetupTearDownUtil() {
		if (fTestSetupTearDownUtil == null) {
			 fTestSetupTearDownUtil = new TestSetupTearDownUtil();
		}
		return fTestSetupTearDownUtil;
	}

	@Before
	public void setup() {
		if (Config.DEFAULT.isConfigured()) {
			serverURI = Config.DEFAULT.getServerURI();
			password = Config.DEFAULT.getPassword();
			userId = Config.DEFAULT.getUserID();
			timeout = Config.DEFAULT.getTimeout();
		}
	}
	

	/**
	 * Test whether we can fetch the build definition info from a build result which has 
	 * post build deliver configured.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFetchBuildDefinitionInfoFromBuildResultWithPBDeliverConfigured() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = testClient.setupBuildDefinitionWithPBDeliver(connectionDetails, buildDefinitionId, 
				                        null, getProgressMonitor());
		String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, 
					connection, "my buildLabel", artifactIds).getItemId().getUuidValue();
		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
		    Map<String, Object> buildDefinitionInfo = getInstance().getBuildDefinitionInfoFromBuildResult(buildResultItemId, 
		    			connection.getTeamRepository(), listener, Locale.getDefault(), 
						getProgressMonitor());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			Assert.assertEquals(2, buildDefinitionInfo.keySet().size());
			Map<String, String> pbDeliverInfo = getPostBuildDeliverInfo(buildDefinitionInfo);
			Assert.assertNotNull(pbDeliverInfo);
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_CONFIGURED_KEY));
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_ENABLED_KEY));
			Assert.assertFalse(Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE.equals(pbDeliverInfo.get(
						Constants.PB_TRIGGER_POLICY_KEY)));
			
			@SuppressWarnings("unchecked")
			Map<String, String> genericConfigInfo = (Map<String, String>) buildDefinitionInfo.get(Constants.GENERIC_INFO_ID);
			Assert.assertNotNull(genericConfigInfo);
			Assert.assertEquals(buildDefinitionId, genericConfigInfo.get(Constants.GENERIC_BUILD_DEFINITION_ID_KEY));
		} finally {
			testClient.tearDown(connectionDetails, artifactIds, getProgressMonitor());
		}
	}

	/**
	 * If post build deliver configuration has a null trigger policy, build definition info should have an "unknown" 
	 * value for trigger policy.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFetchBuildDefinitionInfoFromBuildResultWithPBDeliverConfiguredWithNullTriggerPolicy() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = testClient.setupBuildDefinitionWithPBDeliver(connectionDetails,
						buildDefinitionId, null, getProgressMonitor());
		
		// Remove the trigger policy build property before proceeding
		ITeamBuildClient buildClient = (ITeamBuildClient) (connection.getTeamRepository().getClientLibrary(
										ITeamBuildClient.class));
		IBuildDefinition buildDefinition = (IBuildDefinition) buildClient.getBuildDefinition(buildDefinitionId, 
								getProgressMonitor()).getWorkingCopy();
		@SuppressWarnings("unchecked")
		List<IBuildProperty> buildProperties =  buildDefinition.getProperties(); 
		for (int i = 0 ; i < buildDefinition.getProperties().size(); i++) {
			if (buildProperties.get(i).getName().equals(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY)) {
				buildProperties.remove(i);
				break;
			}
		}
		buildClient.save(buildDefinition, getProgressMonitor());
		String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, 
					connection, "my buildLabel", artifactIds).getItemId().getUuidValue();
		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
		    Map<String, Object> buildDefinitionInfo = getInstance().getBuildDefinitionInfoFromBuildResult(
		    		buildResultItemId, connection.getTeamRepository(), 
		    		listener, Locale.getDefault(),	getProgressMonitor());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			Assert.assertEquals(2, buildDefinitionInfo.keySet().size());
			Map<String, String> pbDeliverInfo = getPostBuildDeliverInfo(buildDefinitionInfo);
			Assert.assertNotNull(pbDeliverInfo);
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_CONFIGURED_KEY));
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_ENABLED_KEY));
			Assert.assertTrue(Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE.equals(pbDeliverInfo.get(
									Constants.PB_TRIGGER_POLICY_KEY)));
		} finally {
			testClient.tearDown(connectionDetails, artifactIds, getProgressMonitor());
		}
	}

	/**
	 * If post build deliver configuration has a unknown trigger policy, then the build definition info should have 
	 * an "unknown" value
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFetchBuildDefinitionInfoFromBuildResultWithPBDeliverConfiguredWithUnknownTriggerPolicy()
												throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
								userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> pbDeliverProperties = new HashMap<String, String>();
		// Add an unknown trigger policy
		pbDeliverProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY, "xyz"); 
		Map<String, String> artifactIds = testClient.setupBuildDefinitionWithPBDeliver(connectionDetails, buildDefinitionId, 
				                        pbDeliverProperties, getProgressMonitor());
		String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, connection, 
					"my buildLabel", artifactIds).getItemId().getUuidValue();
		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
		    Map<String, Object> buildDefinitionInfo = getInstance().getBuildDefinitionInfoFromBuildResult(
		    		buildResultItemId, connection.getTeamRepository(), 
		    		listener, Locale.getDefault(),	getProgressMonitor());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			Assert.assertEquals(2, buildDefinitionInfo.keySet().size());
			Map<String, String> pbDeliverInfo = getPostBuildDeliverInfo(buildDefinitionInfo);
			Assert.assertNotNull(pbDeliverInfo);
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_CONFIGURED_KEY));
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_ENABLED_KEY));
			Assert.assertTrue(Constants.PB_TRIGGER_POLICY_UNKNOWN_VALUE.equals(pbDeliverInfo.get(
								Constants.PB_TRIGGER_POLICY_KEY)));
		} finally {
			testClient.tearDown(connectionDetails, artifactIds, getProgressMonitor());
		}
	}
	
	/**
	 * If post build deliver is not configured for a build definition, then build definition info should not fill up the 
	 * values for other post build deliver details.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFetchBuildDefinitionInfoFromBuildResultWithPBDeliverNotConfigured() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = testClient.setupTestBuildDefinition(connectionDetails,
							buildDefinitionId, getProgressMonitor());
		String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, connection, 
				"my buildLabel", artifactIds).getItemId().getUuidValue();
		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
		    Map<String, Object> buildDefinitionInfo = getInstance().getBuildDefinitionInfoFromBuildResult(buildResultItemId, 
		    						connection.getTeamRepository(), 
		    		listener, Locale.getDefault(),	getProgressMonitor());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			Assert.assertEquals(2, buildDefinitionInfo.keySet().size());
			Map<String, String> pbDeliverInfo = getPostBuildDeliverInfo(buildDefinitionInfo);
			Assert.assertNotNull(pbDeliverInfo);
			Assert.assertEquals("false", pbDeliverInfo.get(Constants.PB_CONFIGURED_KEY));
			Assert.assertNull(pbDeliverInfo.get(Constants.PB_ENABLED_KEY));
			Assert.assertNull(pbDeliverInfo.get(Constants.PB_TRIGGER_POLICY_KEY));
		} finally {
			testClient.tearDown(connectionDetails, artifactIds, getProgressMonitor());
		}
	}
	
	/**
	 * If post build deliver is configured but not enabled, then build definition info should say "true" for 
	 * "enabled".
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFetchBuildDefinitionInfoFromBuildResultWithPBDeliverNotEnabled() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> pbDeliverProperties = new HashMap<String, String>();
		pbDeliverProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ENABLED, "false");
		Map<String, String> artifactIds = testClient.setupBuildDefinitionWithPBDeliver(connectionDetails, 
								buildDefinitionId, pbDeliverProperties, getProgressMonitor());
		String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, 
				connection, "my buildLabel", artifactIds).getItemId().getUuidValue();
		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
		    Map<String, Object> buildDefinitionInfo = getInstance().
		    		getBuildDefinitionInfoFromBuildResult(buildResultItemId, connection.getTeamRepository(), 
		    		listener, Locale.getDefault(),	getProgressMonitor());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			Assert.assertEquals(2, buildDefinitionInfo.keySet().size());
			Map<String, String> pbDeliverInfo = getPostBuildDeliverInfo(buildDefinitionInfo);
			Assert.assertNotNull(pbDeliverInfo);
			Assert.assertEquals("true", pbDeliverInfo.get(Constants.PB_CONFIGURED_KEY));
			Assert.assertEquals("false", pbDeliverInfo.get(Constants.PB_ENABLED_KEY));
			Assert.assertEquals(AutoDeliverTriggerPolicy.NO_ERRORS.name(), pbDeliverInfo.get(Constants.PB_TRIGGER_POLICY_KEY));
		} finally {
			testClient.tearDown(connectionDetails, artifactIds, getProgressMonitor());
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildDefinitionParamValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		// Null build definition ID
		try {
			RTCBuildUtils.getInstance().requestBuild(null, null, null,
					connection.getTeamRepository(), getListener(), Locale.getDefault(), 
					getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_build_definition_id_is_null(), 
							exp.getMessage());
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildValidBuildDefinitionWithInActiveEngine() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		try {
			setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(connectionDetails,
					buildDefinitionId, null, 
					true, null,
					false, 
					"mybuilddef.id", "mybuildengine.id", getProgressMonitor());
			// Deactivate the engine
			String buildEngineItemId = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID);
			BuildUtil.deActivateEngine(buildEngineItemId, getProgressMonitor(), connection.getTeamRepository());
			
			// Act
			Map<String, String> result = RTCBuildUtils.getInstance().requestBuild(buildDefinitionId,
					null, null,
					connection.getTeamRepository(), getListener(), Locale.getDefault(), 
					getProgressMonitor());
			
			// Assert
			Assert.assertEquals(null, Utils.fixEmptyAndTrim(result.get(Constants.RTCBuildUtils_BUILD_RESULT_UUID)));
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWaitForBuildResultUUIDValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			buildResultParamValidationHelper(
				new FunctionWithException<String, Object, Exception>(){
					public Object apply(String buildResultUUID) throws Exception {
						RTCBuildUtils.getInstance().waitForBuild(buildResultUUID, 
							new String [] {BuildState.CANCELED.toString() },
							300L, 5L, connection.getTeamRepository(), getListener(), 
							Locale.getDefault(),  getProgressMonitor());
					return null;
				}
			});
		} catch (Exception exp) {
			throw exp;
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWaitForBuildInvalidBuildStatesToWaitFor() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			// Send a null array
			RTCBuildUtils.getInstance().waitForBuild(UUID.generate().getUuidValue(), 
					null, 300L, 5L, connection.getTeamRepository(), getListener(), 
					Locale.getDefault(),  getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_build_states_array_empty(),
					exp.getMessage());
		}

		try {
			// Send an empty array
			RTCBuildUtils.getInstance().waitForBuild(UUID.generate().getUuidValue(), 
					new String [] {}, 300L, 5L, connection.getTeamRepository(), getListener(), 
					Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_build_states_array_empty(),
					exp.getMessage());
		}
		
		// Let the array have one or more invalid values.
		String [] buildStatesToWaitFor = new String [] {BuildState.CANCELED.toString(),
										"xyz"};
		try {
			RTCBuildUtils.getInstance().waitForBuild(UUID.generate().getUuidValue(), 
					buildStatesToWaitFor,
					300L, 5L, connection.getTeamRepository(), getListener(), 
					Locale.getDefault(),  getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(exp.getMessage(),
					Messages.getDefault().RTCBuildUtils_invalid_build_state("xyz"),
					exp.getMessage());
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWaitForBuildInvalidTimeoutParam() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// Provide a negative value less than -1 or 0.
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		for (int i : new int [] {-20, 0}) {
			try {
				RTCBuildUtils.getInstance().waitForBuild(UUID.generate().getUuidValue(), 
						 new String [] {BuildState.CANCELED.toString()},
						i, 5L, connection.getTeamRepository(), getListener(), 
						Locale.getDefault(),  getProgressMonitor());
			} catch (RTCConfigurationException exp) {
				Assert.assertEquals(
						Messages.getDefault().RTCBuildUtils_build_wait_timeout_invalid(Integer.toString(i)),
						exp.getMessage());
			}
		}
	}
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWaitForBuildInvalidWaitBuildIntervalParam() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Provide a negative value less than -1 or 0.
		for (int i : new int [] {-20, 0}) {
			try {
				RTCBuildUtils.getInstance().waitForBuild(UUID.generate().getUuidValue(), 
						 new String [] {BuildState.CANCELED.toString()},
						 -1, i, connection.getTeamRepository(), getListener(), 
						Locale.getDefault(),  getProgressMonitor());
			} catch (RTCConfigurationException exp) {
				Assert.assertEquals(
						Messages.getDefault().RTCBuildUtils_build_wait_interval_invalid(Integer.toString(i)),
						exp.getMessage());
			}
		}
		
		// Provide a value greater than waitBuildTimeout
		int waitBuildTimeout = 30;
		int waitBuildInterval = waitBuildTimeout + 1;
		try {
			RTCBuildUtils.getInstance().waitForBuild(UUID.generate().getUuidValue(), 
					 new String [] {BuildState.CANCELED.toString()},
					 waitBuildTimeout, waitBuildInterval, 
					 connection.getTeamRepository(), getListener(), 
					Locale.getDefault(),  getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_build_wait_interval_cannot_be_greater(
							Integer.toString(waitBuildInterval), Integer.toString(waitBuildTimeout)),
					exp.getMessage());
		}
	}
	
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWaitForBuildTimeoutTests() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		try {

			setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(connectionDetails, 
					buildDefinitionId, null, true, null, true, 
					null, null, getProgressMonitor());
			String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
			for (int timeout : new int [] {29, 1, 45, 90, 60}) {
				testWaitBuildTimeoutHelper(buildResultUUID,
						new String[] { "COMPLETED", "INCOMPLETE"},
						timeout, 1,
						connection.getTeamRepository(),
						getListener(), Locale.getDefault(),
						getProgressMonitor());
			}
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testWaitForBuildIntervalTests() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Check following conditions
		// waitBuildInterval < waitBuildTimeout in exact chunks
		// waitBuildIterval < waitBuildTimeout in almost exact chunks.
		// waitBuildInterval = waitBuildTimeout

		// In both cases, validate that the "sleeping" message is printed 
		// the expected the number of times.
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		try {
			setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(connectionDetails, 
					buildDefinitionId, null, true, null, true, 
					null, null, getProgressMonitor());
			String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
			
			int waitBuildInterval = 5;
			int waitBuildTimeout = 30;
			int expectedNumberOfSleepingMessages = 6;
			testWaitBuildIntervalHelper(buildResultUUID,
						new String[] { "COMPLETED", "INCOMPLETE"},
						waitBuildTimeout, waitBuildInterval, expectedNumberOfSleepingMessages,
						connection.getTeamRepository(),
						getListener(), Locale.getDefault(),
						getProgressMonitor());
			waitBuildInterval = 5;
			waitBuildTimeout = 39;
			expectedNumberOfSleepingMessages = 8;
			testWaitBuildIntervalHelper(buildResultUUID,
					new String[] { "COMPLETED", "INCOMPLETE"},
					waitBuildTimeout, waitBuildInterval, expectedNumberOfSleepingMessages,
					connection.getTeamRepository(),
					getListener(), Locale.getDefault(),
					getProgressMonitor());
			waitBuildInterval = 30;
			waitBuildTimeout = 30;
			expectedNumberOfSleepingMessages = 1;
			testWaitBuildIntervalHelper(buildResultUUID,
					new String[] { "COMPLETED", "INCOMPLETE"},
					waitBuildTimeout, waitBuildInterval, expectedNumberOfSleepingMessages,
					connection.getTeamRepository(),
					getListener(), Locale.getDefault(),
					getProgressMonitor());
			
			waitBuildInterval = 29;
			waitBuildTimeout = 30;
			expectedNumberOfSleepingMessages = 2;
			testWaitBuildIntervalHelper(buildResultUUID,
					new String[] { "COMPLETED", "INCOMPLETE"},
					waitBuildTimeout, waitBuildInterval, expectedNumberOfSleepingMessages,
					connection.getTeamRepository(),
					getListener(), Locale.getDefault(),
					getProgressMonitor());
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	@Test
	public void testWaitForBuildValidBuildStateTests() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		try {

			setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(connectionDetails, 
					buildDefinitionId, null, true, null, true, 
					null, null, getProgressMonitor());
			String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
			testWaitBuildValidStateHelper(buildResultUUID,
					new String[] { "NOT_STARTED"},
					-1, 1,
					connection.getTeamRepository(),
					getListener(), Locale.getDefault(),
					getProgressMonitor());
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	/** 
	 * Test that retrieving snapshot with a null/not a valid 
	 * build result UUID fails.
	 * 
	 */
	@Test
	public void testGetSnapshotDetailsInvalidParameters() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String [] buildResultUUIDs = new String[] {"", null};
		for (String buildResultUUID : buildResultUUIDs) {
			try {
				RTCBuildUtils.getInstance().retrieveSnapshotFromBuild(buildResultUUID, 
						connection.getTeamRepository(), getListener(), Locale.getDefault(), 
						getProgressMonitor());
			} catch(RTCConfigurationException exp) {
				assertEquals(Messages.getDefault().RTCBuildUtils_build_result_id_is_null(), 
						exp.getMessage());
			}
		}
		// Invalid build result UUID
		String buildResultUUID = "TestInvalidBuidlResultUUID";
		try {
			RTCBuildUtils.getInstance().retrieveSnapshotFromBuild(buildResultUUID, 
					connection.getTeamRepository(), getListener(), Locale.getDefault(), 
					getProgressMonitor());
		} catch (IllegalArgumentException exp) {
			assertEquals(Messages.getDefault().RTCBuildUtils_build_result_UUID_invalid(buildResultUUID), 
					exp.getMessage());
		}
		
	}
	
	/**
	 * Provide a build result UUID that doesn't exist
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetSnapshotDetailsBuildResultDoesNotExist() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		// buildResultUUID has a random UUID.
		String buildResultUUID = UUID.generate().getUuidValue();
		try {
			RTCBuildUtils.getInstance().retrieveSnapshotFromBuild(buildResultUUID, 
					connection.getTeamRepository(), getListener(), Locale.getDefault(), 
					getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RTCBuildUtils_build_result_id_not_found(buildResultUUID), 
					exp.getMessage());
		}
	}
	
	/**
	 * The build result has a snapshot contribution but the snapshot is deleted 
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetSnapshotDetailsNonExistentSnapshot() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		
		Map<String, String> setupArtifacts = testClient.setupBuildResultContributions(
							connectionDetails, 
							workspaceName, componentName, 
							buildDefinitionId, getProgressMonitor());
		// Start the build 
		testClient.testBuildTerminationTestSetup(connectionDetails, true, 
						false, BuildStatus.OK.name(), setupArtifacts);
		String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		try {
			// Perform the accept and get the snapshotUUID
			File tempChangeReportFile = File.createTempFile("test", "report");
			tempChangeReportFile.deleteOnExit();
			Map<String, Object> acceptArtifacts = connection.accept(ProcessUtil.getDefaultProjectArea(
					connection.getTeamRepository()).getName(),
					buildResultUUID, null, null, 
					null, null, sandbox.getRoot().getAbsolutePath(), 
					getChangeReport(tempChangeReportFile.getAbsolutePath()), 
					false, null, null, getListener(), getProgressMonitor(), 
					Locale.getDefault(), "900", true, 
					true, new HashMap<>(), null, new HashMap<>());
			
			// Get back the snapshotUUID and assert that it exists
			@SuppressWarnings("unchecked")
			Map<String, String> buildProperties = (Map<String, String>) acceptArtifacts.get("buildProperties");
			String snapshotUUID = buildProperties.get("team_scm_snapshotUUID");
			
			testClient.deleteSnapshotFromWorkspace(connectionDetails,
								workspaceName, snapshotUUID, getProgressMonitor());
			// Now try to retrieve the snapshot details from the build result
			Map<String, String> snapshotProperties = RTCBuildUtils.getInstance().retrieveSnapshotFromBuild(
					buildResultUUID, connection.getTeamRepository(), 
					getListener(), Locale.getDefault(), getProgressMonitor());
			
			// Assert that the properties are empty.
			assertEquals("", snapshotProperties.get(Constants.RTCBuildUtils_SNAPSHOT_NAME_KEY));
			assertEquals("", snapshotProperties.get(Constants.RTCBuildUtils_SNAPSHOT_UUID_KEY));
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	/**
	 * The build result has no snapshot contribution
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetSnapshotDetailsNoSnapshotContribution() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(
				connectionDetails,
				buildDefinitionId, null, 
				true, null,
				true, null, null, getProgressMonitor());
		String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		String buildEngineUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID);
		// Complete the build
		testClient.completeBuild( connectionDetails, buildResultUUID, 
						buildEngineUUID, getProgressMonitor());
		try {
			// Now try to retrieve the snapshot details from the build result
			Map<String, String> snapshotProperties = RTCBuildUtils.getInstance().retrieveSnapshotFromBuild(
					buildResultUUID, connection.getTeamRepository(), 
					getListener(), Locale.getDefault(), getProgressMonitor());
			
			// Assert that the properties are empty.
			assertEquals("", snapshotProperties.get(Constants.RTCBuildUtils_SNAPSHOT_NAME_KEY));
			assertEquals("", snapshotProperties.get(Constants.RTCBuildUtils_SNAPSHOT_UUID_KEY));
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	/**
	 * Null/Empty parameter for buildDefinitionId
	 * @throws Exception
	 */
	@Test
	public void testGetBuildDefinitionInvaildParameter() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			RTCBuildUtils.getInstance().getBuildDefinition(null,
								connection.getTeamRepository(), getListener(), 
								Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RTCBuildUtils_build_definition_id_is_null(),
					exp.getMessage());
		}
	}
	
	/**
	 * Build definition does not exist
	 * @throws Exception
	 */
	@Test
	public void testGetBuildDefinitionDoesNotExist() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		try {
			RTCBuildUtils.getInstance().getBuildDefinition(buildDefinitionId,
								connection.getTeamRepository(), getListener(), 
								Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RTCBuildUtils_build_definition_not_found(buildDefinitionId),
					exp.getMessage());
		}
	}

	/**
	 * Successfully retrieve build definition details.
	 * @throws Exception
	 */
	@Test
	public void testGetBuildDefinitionSucces() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();

		Map<String, String> setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(connectionDetails,
				buildDefinitionId, null, 
				true, null,
				true, null, null, getProgressMonitor());

		try {
			IBuildDefinition definition = RTCBuildUtils.getInstance().
					getBuildDefinition(buildDefinitionId,
					connection.getTeamRepository(), getListener(), 
					Locale.getDefault(), getProgressMonitor());
			assertNotNull(definition);
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	private void testWaitBuildTimeoutHelper(String buildResultUUID,
			 		String [] buildStatesToWaitFor,
			 		int waitBuildTimeoutSeconds,
			 		int waitBuildIntervalSeconds,
			 		ITeamRepository teamRepository, 
			 		ConsoleOutputHelper listener, Locale clientLocale,
			 		IProgressMonitor progress) 
			 		throws RTCConfigurationException, TeamRepositoryException {
		long startTime = System.currentTimeMillis();
		Map<String, String> ret = RTCBuildUtils.getInstance().waitForBuild(buildResultUUID, 
				buildStatesToWaitFor, 
				waitBuildTimeoutSeconds,
				waitBuildIntervalSeconds,
				teamRepository, listener, 
				clientLocale, progress);
		long endTime = System.currentTimeMillis();
		Assert.assertEquals("NOT_STARTED", ret.get(Constants.RTCBuildUtils_BUILD_STATE));
		Assert.assertEquals("OK", ret.get(Constants.RTCBuildUtils_BUILD_STATUS));
		long timeIntervalMillis = endTime - startTime;
		long waitBuildTimeoutMillis = waitBuildTimeoutSeconds * 1000;
		// Check whether timeInterval spent in waiting is around a 
		// 2 second delta from expectedTime 
		Assert.assertTrue("Observed wait time in test method " + Long.toString(timeIntervalMillis) +  
		           ". Build expected " + waitBuildTimeoutMillis * 1000, 
				(((waitBuildTimeoutMillis - 5000) <= timeIntervalMillis) && 
				  (timeIntervalMillis <= (waitBuildTimeoutMillis + 5000))));
		// Assert that the listener contains at least one "Sleeping for message"
		// Count the number of sleeping for messages
		int actualNumberOfSleepingMessages = getSleepingForMessages(listener);
		Assert.assertTrue(actualNumberOfSleepingMessages >= 1);
	
	}

	private void testWaitBuildIntervalHelper(String buildResultUUID,
	 		String [] buildStatesToWaitFor,
	 		int waitBuildTimeoutSeconds,
	 		int waitBuildInterval,
	 		int expectedNumberOfSleepingMessages,
	 		ITeamRepository teamRepository, 
	 		ConsoleOutputHelper listener, Locale clientLocale,
	 		IProgressMonitor progress) 
	 		throws RTCConfigurationException, TeamRepositoryException {
		Map<String, String> ret = RTCBuildUtils.getInstance().waitForBuild(buildResultUUID, 
				buildStatesToWaitFor, 
				waitBuildTimeoutSeconds,
				waitBuildInterval,
				teamRepository, listener, 
				clientLocale, progress);
		Assert.assertEquals("NOT_STARTED", ret.get(Constants.RTCBuildUtils_BUILD_STATE));
		Assert.assertEquals("OK", ret.get(Constants.RTCBuildUtils_BUILD_STATUS));

		// Count the number of sleeping for messages
		int actualNumberOfSleepingMessages = getSleepingForMessages(listener);
		Assert.assertEquals(expectedNumberOfSleepingMessages, actualNumberOfSleepingMessages);
	}
	

	private int getSleepingForMessages(ConsoleOutputHelper listener) {
		int actualNumberOfSleepingMessages = 0;
		for (String infoMessage : listener.getDebugMessages()) {
			if (infoMessage.contains("waitForBuild: Sleeping for")) {
				actualNumberOfSleepingMessages++;
			}
		}
		return actualNumberOfSleepingMessages;
	}
	
	private void testWaitBuildValidStateHelper(String buildResultUUID,
	 		String [] buildStatesToWaitFor,
	 		int waitBuildTimeoutSeconds,
	 		int waitBuildInterval,
	 		ITeamRepository teamRepository, 
	 		ConsoleOutputHelper listener, Locale clientLocale,
	 		IProgressMonitor progress) 
	 		throws RTCConfigurationException, TeamRepositoryException {
			Map<String, String> ret = RTCBuildUtils.getInstance().waitForBuild(buildResultUUID, 
					buildStatesToWaitFor, 
					waitBuildTimeoutSeconds,
					waitBuildInterval,
					teamRepository, listener, 
					clientLocale, progress);
			// Validate that resulting build state is part of buildStatesToWaitFor
			boolean matched = false;
			for (int i = 0; i < buildStatesToWaitFor.length; i++) {
				if (buildStatesToWaitFor[i].equals(ret.get(Constants.RTCBuildUtils_BUILD_STATE))) {
					matched = true;
				}
			}
			Assert.assertTrue(matched);
			Assert.assertEquals("OK", ret.get(Constants.RTCBuildUtils_BUILD_STATUS));
			Assert.assertEquals(false, 
						Boolean.parseBoolean(ret.get(Constants.RTCBuildUtils_TIMEDOUT)));
 
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> getPostBuildDeliverInfo(
					Map<String, Object>	buildDefinitionInfo) {
		return (Map<String, String>) buildDefinitionInfo.get(Constants.PB_INFO_ID);
	}
	
	private RTCBuildUtils getInstance() {
		return RTCBuildUtils.getInstance();
	}
	
	private NullProgressMonitor getProgressMonitor() {
		return new NullProgressMonitor();
	}
	
	private <E extends Exception> 
		void buildResultParamValidationHelper(FunctionWithException<String, Object, E> f) throws Exception {
			// Tests with null, empty UUID
		for (String buildResultUUID : new String[] {null, ""}) {
			
			try {
				f.apply(buildResultUUID);
			} catch (Exception exp) {
				Assert.assertTrue(exp instanceof RTCConfigurationException);
				Assert.assertEquals(
						Messages.getDefault().RTCBuildUtils_build_result_id_is_null(), 
								exp.getMessage());
			}
		}
		
		// Invalid UUID 
		try {
			f.apply("abcd");
		} catch (Exception exp) {
			Assert.assertTrue(exp instanceof IllegalArgumentException);
			Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_build_result_UUID_invalid("abcd"), 
							exp.getMessage());
		}
		
		{
			String nonExistentBuidResultUUID = UUID.generate().getUuidValue();
			// Non existent build result UUID 
			try {
				f.apply(nonExistentBuidResultUUID);
			} catch (Exception exp) {
				Assert.assertTrue(exp instanceof RTCConfigurationException);
				Assert.assertEquals(
						Messages.getDefault().RTCBuildUtils_build_result_id_not_found(
								nonExistentBuidResultUUID), exp.getMessage());
			}
		}
	}

	
	@FunctionalInterface
	interface FunctionWithException<T,R, E extends Exception> {
		R apply(T t) throws E;
	}
	
	private ChangeReport getChangeReport(String filePath) throws IOException {
		OutputStream o = new FileOutputStream(filePath);
		ChangeReport report = new ChangeReport(o);
		return report;
	}

	private ConsoleOutputHelper getListener() {
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		return listener;
	}
}
