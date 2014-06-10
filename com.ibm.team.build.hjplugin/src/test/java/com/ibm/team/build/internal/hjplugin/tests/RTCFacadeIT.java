/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
}
