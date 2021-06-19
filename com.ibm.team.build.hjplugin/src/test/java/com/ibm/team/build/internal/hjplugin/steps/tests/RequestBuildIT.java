/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.steps.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.steps.RequestBuildStepExecution;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

import hudson.Util;
import hudson.model.Action;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;

/**
 * Integration tests for {@link RequestBuildStepExecution}
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RequestBuildIT extends AbstractRTCBuildStepTest {

	String requestBuildFragment = "task: [buildDefinitionId: \"Test1\",  name: 'requestBuild']";
	String prefix = "requestBuild";

	
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
	
	/**
	 * Validation tests
	 */
	
	@Test
	public void testRequestBuildNoServerURI() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestNoServerURI(rule, prefix, requestBuildFragment);
	}

	@Test
	public void testRequestBuildMissingCreds() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingCreds(rule, prefix, requestBuildFragment);
	}
	
	@Test
	public void testRequestBuildMissingBuildToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingBuildToolkit(rule, prefix, requestBuildFragment);
	}
	
	@Test
	public void testRequestBuildMissingBuildDefinitionID() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		WorkflowJob j = setupWorkflowJob(rule);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s', task: [buildDefinitionId: '%s',  name: 'requestBuild'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
				""); // Missing build result UUID
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun r = requestJenkinsBuild(j);
		
		String log = getLog(r);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildDefinitionId()));
		Utils.dumpLogFile(r, "requestBuild", "missingBuildDefinitionID", ".log");
	}
	
	@WithTimeout(600)
	@Test
	public void testRequestBuildAInPipeline() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String buildDefinitionId = getBuildDefinitionUniqueName();
		
		Map<String, String> setupArtifacts = new HashMap<>();
		try {
			setupArtifacts = setupBuildDefinitionAndEngine(buildDefinitionId, true, null);
			
			WorkflowJob j = rule.createProject(WorkflowJob.class);
			Credentials c = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,"testMeCreds",
									"test", Config.DEFAULT.getUserId(),
									Config.DEFAULT.getPassword());
			SystemCredentialsProvider.getInstance().getCredentials().add(c);
			j.addProperty(new ParametersDefinitionProperty(
					new StringParameterDefinition("com.ibm.team.build.debug", "true")));
			
			List<ParametersAction> pActions = new ArrayList<ParametersAction> ();
			pActions.add(new ParametersAction(new StringParameterValue("com.ibm.team.build.debug", "true")));
			
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildDefinitionId: \"%s\",  name: 'requestBuild'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, "testMeCreds", 
					 Config.DEFAULT.getServerURI(),	buildDefinitionId);
			CpsFlowDefinition d = new CpsFlowDefinition("node { echo \"hello world\" \n " + 
						rtcBuildStep + "\n }", true);
			j.setDefinition(d);
			
			QueueTaskFuture<WorkflowRun> future = j.scheduleBuild2(0, pActions.toArray(new Action[0]));
			while(!future.isDone()) {
				// Intentionally empty
			}
			WorkflowRun r = future.get();
			String log = getLog(r);
			Assert.assertTrue(log, log.contains("hello world"));
			Assert.assertEquals(Result.SUCCESS, r.getResult());
			Assert.assertTrue(log, log.contains(
					String.format("Requesting build for build definition %s", buildDefinitionId)));
			Assert.assertTrue(log, log.contains("Request Build task complete. Return values"));
			Utils.dumpLogFile(r, "listLogs", "validateBuildResultUUID", ".log");
		} finally {
			Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
		}
	}
	
	/**
	 * Test if requestBuild succeeds if provided a valid build definition ID
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildAPISuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String buildDefinitionId = getBuildDefinitionUniqueName();
		
		Map<String, String> setupArtifacts = new HashMap<>();
		try {
			setupArtifacts = setupBuildDefinitionAndEngine(buildDefinitionId, true, null);
			Map<String, String> result  = requestBuild(buildDefinitionId);
			// Validate the build result UUID
			assertNotNull(Util.fixEmptyAndTrim(result.get("buildResultUUID")));
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID, result.get("buildResultUUID"));
		} finally {
			Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
		}
	}
	
	/**
	 * If build definition does not exist, then requestBuild fails with an exception 
	 *  
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildAPINonExistentBuildDefFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String buildDefinitionId = getBuildDefinitionUniqueName();
		try {
			Map<String, String> result  = requestBuild(buildDefinitionId);
			fail(String.format("Expected an exception but instead got result %s", result.toString()));
		} catch (Exception exp) {
			String exceptionMessage = "Build Definition \"%s\" could not be found.";
			assertTrue(exp.getMessage(), exp.getMessage().contains(
						String.format(exceptionMessage, buildDefinitionId)));
		} finally {
			// Nothing to remove
		}
	}
	
	/**
	 * If build definition has no supporting engines, then requestBuild does fail 
	 * but returns an empty buildResultUUID.
	 *  
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildAPINoSupportingEnginesSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = new HashMap<>(); 
		try {
			setupArtifacts = setupBuildDefinitionAndEngine(buildDefinitionId, false, null);

			// Act
			Map<String, String> result  = requestBuild(buildDefinitionId);
			
			// Assert
			// Result should not have any build result UUID
			assertEquals("", result.get("buildResultUUID"));
		} finally {
			Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
		}
	}

	/**
	 * If all supporting  engines are inactive, then request build does not fail but 
	 * returns an empty buildResultUUID.
	 *  
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildAPIAllEnginesInactiveEngineFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String buildDefinitionId = getBuildDefinitionUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		Map<String, String> setupArtifacts = new HashMap<>();
		try {
			// Setup
			setupArtifacts = setupBuildDefinitionAndEngine(buildDefinitionId, true, null);
			String buildEngineUUID = setupArtifacts.get(Utils.ARTIFACT_BUILD_ENGINE_ITEM_ID);
			// Deactivate the build engine
			deactivateEngine(buildEngineUUID);
			
			// Act 
			Map<String, String> result = requestBuild(buildDefinitionId);
			
			// Assert
			// Result should not have any build result UUID - empty build result UUID
			assertEquals("", result.get("buildResultUUID"));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	/**
	 * TODO Disabling for now until there is some way to test this.
	@Test
	public void testRequestBuildNoReadPermissionOnBuildDefinition() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	}**/

	/**
	 * If the user does not have permissions to request build, then request build fails 
	 * with an exception
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildNoPermsSupport() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		String pAName = getProjectAreaUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();		
		String processAreaXMLFile = "ProcessSpecNoRequestBuildPerms.xml";
		
		List<Map<String, String>> setupArtifactsList = new ArrayList<>();
		try {
			// Setup process area
			Map<String, String> setupArtifacts = setupProcessArea(pAName, processAreaXMLFile);

			// Setup build definition in process area
			Map<String, String> setupArtifacts1 = setupBuildDefinitionInPA(pAName, buildDefinitionId);
			
			// We are adding it in reverse oder for artifact cleanup
			setupArtifactsList.add(setupArtifacts1);
			setupArtifactsList.add(setupArtifacts);			
			try {
				// Act
				Map<String, String> result = requestBuild(buildDefinitionId);
				
				// Assert - 1
				fail(String.format("Expected an exception but instead got result %s", result.toString()));
			} catch (Exception exp) {
				// Assert - 2
				assertTrue(exp.getMessage(), exp.getMessage().contains(
						"To complete the 'Request Build' task, you need these permissions: "
						+ "'You don't have permission to perform the following actions:"));
				assertTrue(exp.getMessage(), exp.getMessage().contains("Request Build (request)"));
			}
		} finally {
			for (Map<String, String> setupArtifacts : setupArtifactsList) {
				Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
			}
		}
	}	
	
	/**
	 * Test if delete, add and override properties work as expected when requesting a build.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testRequestBuildDeleteAddOverrideProperties() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String buildDefinitionId = getBuildDefinitionUniqueName();

		Map<String, String> setupArtifacts = new HashMap<>();
		try {
			// add properties to the build definition
			Map<String, String> buildProperties = new HashMap<String, String>();
			buildProperties.put("propertyToDelete1", "valueOfPropertyToDelete1");
			buildProperties.put("propertyToDelete2", "valueOfPropertyToDelete2");
			buildProperties.put("propertyToOverride1", "valueOfPropertyToOverride1");
			buildProperties.put("propertyToOverride2", "valueOfPropertyToOverride2");
			//setup the build definition and engine
			setupArtifacts = setupBuildDefinitionAndEngine(buildDefinitionId, true, buildProperties);
			
			// create the properties to delete, add or override when requesting the build
			String[] propertiesToDelete = new String[] { "propertyToDelete1" };
			Map<String, String> propertiesToAddOrOverride = new HashMap<String, String>();
			propertiesToAddOrOverride.put("propertyToOverride1", "modifiedValueOfPropertyToOverride1");
			propertiesToAddOrOverride.put("propertyToAdd1", "valueOfPropertyToAdd1");
			Map<String, String> result = requestBuild(buildDefinitionId, propertiesToDelete, propertiesToAddOrOverride);
			// Validate the build result UUID
			assertNotNull(Util.fixEmptyAndTrim(result.get("buildResultUUID")));
			String buildResultUUID = Util.fixEmptyAndTrim(result.get("buildResultUUID"));
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID, buildResultUUID);
			
			// complete the build so that we can check if the property changes provided when requesting the build were
			// applied
			completeBuild(buildResultUUID, setupArtifacts.get(Utils.ARTIFACT_BUILD_ENGINE_ITEM_ID));
			
			// get the properties from the build definition associated with the build result
			buildProperties = getBuildProperties(buildResultUUID);
			assertTrue(buildProperties.size() == 4);
			assertEquals(null, buildProperties.get("propertyToDelete1"));
			assertEquals("valueOfPropertyToDelete2", buildProperties.get("propertyToDelete2"));
			assertEquals("modifiedValueOfPropertyToOverride1", buildProperties.get("propertyToOverride1"));
			assertEquals("valueOfPropertyToOverride2", buildProperties.get("propertyToOverride2"));
			assertEquals("valueOfPropertyToAdd1", buildProperties.get("propertyToAdd1"));
		} finally {
			Utils.tearDown(Utils.getTestingFacade(), Config.DEFAULT, setupArtifacts);
		}
	}

	/**
	 * Setup build definition in given project area. It is assumed that the 
	 * process area already exists
	 *  
	 * @param projectAreaName     - the name of the project area
	 * @param buildDefinitionId   - the id of the build definition
	 * @return                    - A map containing UUIDs of all the artifacts created
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> setupBuildDefinitionInPA(String projectAreaName,
											String buildDefinitionId) throws Exception {
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
				.invoke("setupTestBuildDefinition",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildDefinitionId
								String.class}, // projectAreaName 
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						buildDefinitionId, projectAreaName);
	}

	/**
	 * Setup a project area with the given name and process xml file.
	 * 
	 * @param pAName               - The name of the project area       
	 * @param processAreaXMLFile   - The process XML
	 * @return                     - A map containing UUIDs of all the artifacts created
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> setupProcessArea(String pAName, String processAreaXMLFile) throws Exception {
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
		.invoke("setupTestProcessArea_basic",
				new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class, // projectAreaName,
						String.class}, // processXMLName
				loginInfo.getServerUri(),
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(), pAName,
				processAreaXMLFile);
	}
	
	/**
	 * 
	 * @param buildEngineItemId The item id of the build engine to deactivate
	 *  
	 */
	private void deactivateEngine(String buildEngineItemId) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		testingFacade.invoke("deactivateEngine", 
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class}, // buildEngineItemId
					defaultC.getServerURI(), loginInfo.getUserId(), loginInfo.getPassword(), 
					defaultC.getTimeout(), buildEngineItemId);
		
	}
	
	/**
	 * Request a build for the given build definition
	 * 
	 * @param buildDefinitionId The id of the build definition
	 * @return A map containing UUIDs of all the artifacts created
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> requestBuild(String buildDefinitionId) throws Exception {
		return requestBuild(buildDefinitionId, null, null);
	}
	
	/**
	 * Request a build for the given build definition 
	 * 
	 * @param buildDefinitionId         The id of the build definition
	 * @param propertiesToDelete		The list of properties to ignore when requesting the build
	 * @param propertiesToAddOrOverride The list of properties to add or override when requesting the build
	 * @return                          A map containing UUIDs of all the artifacts created
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> requestBuild(String buildDefinitionId, String[] propertiesToDelete, Map<String, String> propertiesToAddOrOverride)
			throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
					.invoke("requestBuild", 
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildDefinition
							String[].class, // propertiesToDelete
							Map.class, // propertiesToAddOrOverride 
							Object.class, // listener
							Locale.class }, // clientLocale
					defaultC.getServerURI(), loginInfo.getUserId(), loginInfo.getPassword(), 
					defaultC.getTimeout(), buildDefinitionId, propertiesToDelete, propertiesToAddOrOverride,
					new TaskListenerWrapper(getTaskListener()),
					Locale.getDefault());
	}

	/**
	 * Setup a build definition and engine. Add the build engine to the supported list of   
	 * the build definition
	 *  
	 * @param buildDefinitionId       The id of the build engine
	 * @param setupBuildEngine	      Whether to setup a build engine or not
	 * @return                        A map containing UUIDs of all the artifacts created
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> setupBuildDefinitionAndEngine(String buildDefinitionId, 
									boolean setupBuildEngine, Map<String, String> buildProperties) throws Exception {
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		return (Map<String, String>) testingFacade
				.invoke("setupBuildDefinitionWithoutSCMWithQueuedBuild",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildDefinition,
								String.class, // processAreaName
								boolean.class, // setupBuildEngine
								Map.class, // buildProperties
								boolean.class, //  requestBuild
								String.class,
								String.class},
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), 
						buildDefinitionId, null, 
						setupBuildEngine, buildProperties, false, null, null);
	}

	/**
	 * Complete the build result corresponding to the provided build result item id
	 * 
	 * @param buildResultUUID
	 */
	private void completeBuild(String buildResultUUID, String buildEngineUUID) throws Exception {
		try {
			Config defaultC = Config.DEFAULT;
			RTCLoginInfo loginInfo = defaultC.getLoginInfo();
			RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
			testingFacade
					.invoke("completeBuild", 
							new Class[] { String.class, // serverURI
									String.class, // userId
									String.class, // password
									int.class, // timeout
									String.class, // buildResultUUID
									String.class}, // buildEngineUUID
							defaultC.getServerURI(), loginInfo.getUserId(), 
							loginInfo.getPassword(), defaultC.getTimeout(), 
							buildResultUUID, buildEngineUUID);
		} catch (Exception e) {
			File f = File.createTempFile("complete", "Build");
			writeExpDetails(f, e);
			throw e;
		}
		
	}
	
	@SuppressWarnings("unchecked")
	private Map<String, String> getBuildProperties(String buildResultUUID) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();

		return (Map<String, String>)testingFacade.invoke("getBuildProperties", new Class[] { String.class, // serverURI
				String.class, // userId
				String.class, // password
				int.class, // timeout
				String.class }, // buildResultUUID
				defaultC.getServerURI(), loginInfo.getUserId(), loginInfo.getPassword(), 
				defaultC.getTimeout(), buildResultUUID);
	}
}