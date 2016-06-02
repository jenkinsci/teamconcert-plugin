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

import java.util.Map;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;

@SuppressWarnings("nls")
public class RTCFacadeIT extends HudsonTestCase {

	RTCFacadeWrapper facade = null;

	@Override
	public void setUp() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			facade = RTCFacadeFactory.getFacade(Config.DEFAULT.getToolkit(), null);
		}
	}

	@Override
	protected void tearDown() throws Exception {
		// Didn't start H/J
	}

	/**
	 * Test "Test Connection" with a valid password
	 */
	public void testTestConnectionWithPassword() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			String errorMessage = RTCFacadeFacade.testConnection(
					Config.DEFAULT.getToolkit(),
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(),
					Config.DEFAULT.getTimeout(),
					false); // using toolkit
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

			errorMessage = RTCFacadeFacade.testConnection(
					Config.DEFAULT.getToolkit(),
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(),
					Config.DEFAULT.getTimeout(),
					true); // avoiding toolkit
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
		}
	}

	/**
	 * Test "Test Connection" with an invalid password
	 */
	public void testTestConnectionWithInvalidPassword() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			String errorMessage = RTCFacadeFacade.testConnection(
					Config.DEFAULT.getToolkit(),
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserIDForAuthenticationFailures(),
					"invalid password",
					Config.DEFAULT.getTimeout(),
					false); // using toolkit
			assertTrue("Successful testConnection with invalid password", errorMessage != null && errorMessage.length() != 0);

			errorMessage = RTCFacadeFacade.testConnection(
					Config.DEFAULT.getToolkit(),
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserIDForAuthenticationFailures(),
					"invalid password",
					Config.DEFAULT.getTimeout(),
					true); // avoiding toolkit
			assertTrue("Successful testConnection with invalid password", errorMessage != null && errorMessage.length() != 0);

		}
	}

	/**
	 * Test "Test Build Workspace" validates correctly a singly occurring workspace (valid)
	 * and multiple workspaces with the same name (invalid)
	 */
	public void testTestBuildWorkspace() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade.invoke(
					"setupTestBuildWorkspace", new Class[] {
							String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // singleWorkspaceName,
							String.class}, // multipleWorkspaceName
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					"Singly Occuring=WS&encoded", 
					"Multiple Occurrence=WS");

			try {
				
				String errorMessage = RTCFacadeFacade.testBuildWorkspace(
					Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					false, // using toolkit
					"Singly Occuring=WS&encoded");
				assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

				errorMessage = RTCFacadeFacade.testBuildWorkspace(
						Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, // avoiding toolkit
						"Singly Occuring=WS&encoded");
				assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

				try {
					errorMessage = RTCFacadeFacade.testBuildWorkspace(
							Config.DEFAULT.getToolkit(),
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(),
							false, // using toolkit
							"Multiple Occurrence=WS");
					assertTrue("There should be more than 1 workspace with the name", errorMessage != null && errorMessage.contains("More than 1"));

					errorMessage = RTCFacadeFacade.testBuildWorkspace(
							Config.DEFAULT.getToolkit(),
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(),
							true, // avoiding toolkit
							"Multiple Occurrence=WS");
					assertTrue("There should be more than 1 workspace with the name", errorMessage != null && errorMessage.contains("More than 1"));
				} catch (Exception e) {
					e.printStackTrace(System.out);
					Assert.fail(e.getMessage());
				}
			} finally {
				testingFacade.invoke(
						"tearDown",
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts);
			}
		}
	}

	/**
	 * Test "Test Build Workspace" identifies the workspace is missing
	 */
	public void testTestMissingBuildWorkspace() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			try {
				String errorMessage = RTCFacadeFacade.testBuildWorkspace(
					Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					false, // using toolkit
					"MissingWorkspace" + System.currentTimeMillis());
				assertTrue(errorMessage, errorMessage != null && errorMessage.contains("Unable to find"));

				errorMessage = RTCFacadeFacade.testBuildWorkspace(
						Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, // avoiding toolkit
						"MissingWorkspace" + System.currentTimeMillis());
				assertTrue(errorMessage, errorMessage != null && errorMessage.contains("Unable to find"));
			} catch (Exception e) {
				e.printStackTrace(System.out);
				Assert.fail(e.getMessage());
			}
		}
	}

	/**
	 * Test "Test Build Definition" identifies the build definition doesn't exist
	 */
	public void testTestMissingBuildDefinition() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			try {
				String errorMessage = RTCFacadeFacade.testBuildDefinition(
					Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					false, // using toolkit
					"MissingBuildDefinition" + System.currentTimeMillis());
				assertTrue(errorMessage, errorMessage != null && errorMessage.contains("Unable to find"));

				errorMessage = RTCFacadeFacade.testBuildWorkspace(
						Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, // avoiding toolkit
						"MissingBuildDefinition" + System.currentTimeMillis());
					assertTrue(errorMessage, errorMessage != null && errorMessage.contains("Unable to find"));
			} catch (Exception e) {
				e.printStackTrace(System.out);
				Assert.fail(e.getMessage());
			}
		}
	}

	/**
	 * Test "Test Build Definition" validates the build definition is setup properly
	 * TODO Add more tests to validate improperly setup build definitions
	 */
	public void testTestBuildDefinition() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			String buildDefinitionName = "BuildDefinitionName" + System.currentTimeMillis();
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade.invoke(
					"setupTestBuildDefinition", new Class[] {
							String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class}, // Build Definition Name,
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					buildDefinitionName);

			try {
				
				String errorMessage = RTCFacadeFacade.testBuildDefinition(
					Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					false, // using toolkit
					buildDefinitionName);
				assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

				errorMessage = RTCFacadeFacade.testBuildDefinition(
						Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, // avoiding toolkit
						buildDefinitionName);
				assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

			} finally {
				testingFacade.invoke(
						"tearDown",
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts);
			}
		}
	}
	
	/**
	 * Execute tests comparing incoming changes, for quite period implementation
	 * @throws Exception
	 */
	public void testCompareIncomingChanges() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			System.out.println("&&&&&& Calling testCompareIncomingChanges&&&&&&&&&&&&&&&&&&");
			RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
			testingFacade.invoke("testCompareIncomingChanges", null, null);
			System.out.println("&&&&&& Called testCompareIncomingChanges&&&&&&&&&&&&&&&&&&");
		}
	}

	/**
	 * Test validation of an existing project area and team area doesn't fail.
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public void testTestProcessArea_basic() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		String projectAreaName = "testTestProcessArea_basic" + System.currentTimeMillis();
		// create a project area with a single team area
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestProcessArea_basic", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, // projectAreaName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);

		try {
			// validate project area
			String errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
			// validate team area
			errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/" + projectAreaName + ".team.area");
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}
	}

	/**
	 * Test validation of an existing project area and team area, with special characters in the name, doesn't fail.
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public void testTestProcessArea_specialChars() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		String projectAreaName = "testTestProcessArea_specialChars`-=[]\\;',.?:\"{}|+_)(*&^%$#@!~)" + System.currentTimeMillis();
		// create a project area with a single team area. include special characters in the name
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestProcessArea_basic", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, // projectAreaName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);

		try {
			// validate project area
			String errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
			// validate team area. also validates that having a trailing "/" is ignored and validation goes through
			errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/" + projectAreaName + ".team.area/");
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}
	}

	/**
	 * Test validation of non-existent project area and team area fails.
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public void testTestProcessArea_nonExistent() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		// create a project area to test that searching for a non-existent team area under an existing team area
		// hierarchy fails
		String projectAreaName = "testTestProcessArea_nonExistent" + System.currentTimeMillis();
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestProcessArea_basic", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, // projectAreaName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);

		try {
			// non-existent project area
			String errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "1");
			assertTrue(errorMessage, ("A project area with name \"" + projectAreaName + "1\" cannot be found.").equals(errorMessage));

			// non-existent team area under an existing team area hierarchy fails. also validates that we parse multiple
			// levels in team area hierarchy
			errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/" + projectAreaName + ".team.area/new.team.area");
			assertTrue(errorMessage,
					("A team area at path \"" + projectAreaName + "/" + projectAreaName + ".team.area/new.team.area\" cannot be found.")
							.equals(errorMessage));

			// non-existent team area under a non-existent project area
			errorMessage = RTCFacadeFacade.testProcessArea(Config.DEFAULT.getToolkit(), loginInfo.getServerUri(), loginInfo.getUserId(),
					loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "1/" + projectAreaName + ".team.area1");
			assertTrue(errorMessage,
					("A team area at path \"" + projectAreaName + "1/" + projectAreaName + ".team.area1\" cannot be found.").equals(errorMessage));

		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}
	}
}
