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

import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.localizer.LocaleProvider;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildSnapshotContext;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;

@SuppressWarnings("nls")
public class RTCFacadeIT extends HudsonTestCase {

	RTCFacadeWrapper facade = null;
	
	private String serverURI;
	private int timeout;
	private String userId;
	private String password;

	@Override
	public void setUp() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			facade = RTCFacadeFactory.getFacade(Config.DEFAULT.getToolkit(), null);
			Config config = Config.DEFAULT;
			serverURI = config.getServerURI();
			timeout = config.getTimeout();
			userId = config.getUserID();
			password = config.getPassword();
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
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName,
					LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
			// validate team area
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/"
							+ projectAreaName + ".team.area", LocaleProvider.getLocale());
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
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName,
					LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
			// validate team area. also validates that having a trailing "/" is ignored and validation goes through
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/"
							+ projectAreaName + ".team.area/", LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
			// validate team area. also validates that having a trailing "/" is ignored and validation goes through
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/"
							+ projectAreaName + ".team.area///", LocaleProvider.getLocale());
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
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "1",
					LocaleProvider.getLocale());
			assertEquals("A project area with name \"" + projectAreaName + "1\" cannot be found.", errorMessage);
			
			// non-existent project area with a trailing "/"
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "1/",
					LocaleProvider.getLocale());
			assertEquals("A project area with name \"" + projectAreaName + "1/\" cannot be found.", errorMessage);

			// non-existent project area with multiple trailing "/"
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "1///",
					LocaleProvider.getLocale());
			assertEquals("A project area with name \"" + projectAreaName + "1///\" cannot be found.", errorMessage);

			// non-existent team area under an existing team area hierarchy fails. also validates that we parse multiple
			// levels in team area hierarchy
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/"
							+ projectAreaName + ".team.area/new.team.area", LocaleProvider.getLocale());
			assertEquals("A team area at path \"" + projectAreaName + "/" + projectAreaName + ".team.area/new.team.area\" cannot be found.",
					errorMessage);

			// non-existent team area under a non-existent project area
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "1/"
							+ projectAreaName + ".team.area1", LocaleProvider.getLocale());
			assertEquals("A team area at path \"" + projectAreaName + "1/" + projectAreaName + ".team.area1\" cannot be found.", errorMessage);

		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}
	}

	@SuppressWarnings("unchecked")
	public void testTestProcessArea_archiveProjectArea() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		// create a project area to test that searching for an archived project area/ team area fails
		String projectAreaName = "testTestProcessArea_archiveProjectArea" + System.currentTimeMillis();
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestProcessArea_archiveProjectArea", new Class[] {
				String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, // projectAreaName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);

		try {
			// Archived project area
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName,
					LocaleProvider.getLocale());
			assertEquals("The project area \"" + projectAreaName + "\" is archived.", errorMessage);

			// Teamarea under archived project area
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/"
							+ projectAreaName + ".team.area", LocaleProvider.getLocale());
			assertEquals("The team area \"" + projectAreaName + "/" + projectAreaName + ".team.area\" is archived.", errorMessage);
		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}
	}

	@SuppressWarnings("unchecked")
	public void testTestProcessArea_archiveTeamArea() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		// create a project area and archive just the team area. Test that searching by project area succeeds and
		// searching by team area fails
		String projectAreaName = "testTestProcessArea_archiveTeamArea" + System.currentTimeMillis();
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestProcessArea_archiveTeamArea", new Class[] {
				String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, // projectAreaName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName);

		try {
			// Archived project area
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // processArea
							Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName,
					LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

			// Teamarea under archived project area
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					Locale.class }, // clientLocale
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName + "/"
							+ projectAreaName + ".team.area", LocaleProvider.getLocale());
			assertEquals("The team area \"" + projectAreaName + "/" + projectAreaName + ".team.area\" is archived.", errorMessage);
		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void testTestBuildStream_complete() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		// create a project area and archive just the team area. Test that searching by project area succeeds and
		// searching by team area fails
		String projectAreaName = "testTestBuildStream_complete_pa" + System.currentTimeMillis();
		String teamAreaPath = projectAreaName + "/" + projectAreaName + ".team.area";
		String streamName = "testTestBuildStream_complete_stream" + System.currentTimeMillis();
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildStream_complete", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class, // projectAreaName
				String.class }, // streamName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), projectAreaName, streamName);
		try {
			// non-existent or archived project area, non-null stream name
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, projectAreaName + "1", streamName, LocaleProvider.getLocale());
			assertEquals("A project area with name \"" + projectAreaName + "1\" cannot be found.", errorMessage);

			// non-existent or archived team area, non-null stream name
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, teamAreaPath + "1", streamName, LocaleProvider.getLocale());
			assertEquals("A team area at path \"" + teamAreaPath + "1\" cannot be found.", errorMessage);

			// null process area, non-existent stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, null, streamName + "1", LocaleProvider.getLocale());
			assertEquals("A stream with name \"" + streamName + "1\" cannot be found in the repository.", errorMessage);
			
			// non-null valid project area, non-existent stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, projectAreaName, streamName + "1", LocaleProvider.getLocale());
			assertEquals("A stream with name \"" + streamName + "1\" cannot be found in the project area \"" + projectAreaName + "\".", errorMessage);

			// non-null valid team area, non-existent stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, teamAreaPath, streamName + "1", LocaleProvider.getLocale());
			assertEquals("A stream with name \"" + streamName + "1\" cannot be found in the team area \"" + teamAreaPath + "\".", errorMessage);

			// null process area, duplicate stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, null, "teamAreaDuplicate" + streamName, LocaleProvider.getLocale());
			assertEquals("More than one stream with name \"teamAreaDuplicate" + streamName + "\" found in the repository.", errorMessage);

			// non-null valid project area, duplicate stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, projectAreaName, "duplicate" + streamName, LocaleProvider.getLocale());
			assertEquals("More than one stream with name \"duplicate" + streamName + "\" found in the project area \"" + projectAreaName + "\".",
					errorMessage);

			// non-null valid team area, duplicate stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, teamAreaPath, "teamAreaDuplicate" + streamName, LocaleProvider.getLocale());
			assertEquals("More than one stream with name \"teamAreaDuplicate" + streamName + "\" found in the team area \"" + teamAreaPath + "\".",
					errorMessage);

			// null process area, valid stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, null, streamName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);

			// non-null valid project area, valid stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, projectAreaName, streamName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);

			// non-null valid team area, valid stream
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, teamAreaPath, "teamArea" + streamName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// duplicate stream, which gets resolved at project area and team area level but errors out without context
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, null, "globalDuplicate" + streamName, LocaleProvider.getLocale());
			assertEquals("More than one stream with name \"globalDuplicate" + streamName + "\" found in the repository.", errorMessage);
			
			
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, projectAreaName, "globalDuplicate" + streamName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, teamAreaPath, "globalDuplicate" + streamName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
		} finally {
			testingFacade.invoke("tearDownTestBuildStream_complete", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
		}

	}

	@SuppressWarnings("unchecked")
	public void testTestBuildSnapshot_complete() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		// create a project area and archive just the team area. Test that searching by project area succeeds and
		// searching by team area fails
		String projectAreaName = "testTestBuildSnapshot_complete_pa_" + System.currentTimeMillis();
		String teamAreaName = projectAreaName + ".team.area";
		String teamAreaPath = projectAreaName + "/" + teamAreaName;
		String workspaceName = "testTestBuildSnapshot_complete_ws_" + System.currentTimeMillis();
		String streamName = "testTestBuildSnapshot_complete_st_" + System.currentTimeMillis();
		String snapshotName = "testTestBuildSnapshot_complete_ss_" + System.currentTimeMillis();
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildSnapshot_complete",
				new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class, // workspaceName
						String.class, // projectAreaName
						String.class, // streamName
						String.class }, // snapshotName
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, projectAreaName,
				streamName, snapshotName);
		try {
			// pass null value for BuildSnapshotContext, duplicate snapshot name at the repository level // null value
			// is considered as np snapshot owner and repository level search is performed
			String configuredSnapshotName = "universal_" + snapshotName;
			String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, null, configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass blank values for all fields, duplicate snapshot name at the repository level // repository level search
			BuildSnapshotContext buildSnapshotContext = new BuildSnapshotContext("", "", "", "");
			configuredSnapshotName = "universal_" + snapshotName;
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass null for all fields, duplicate snapshot name at the repository level // repository level search
			buildSnapshotContext = new BuildSnapshotContext(null, null, null, null);
			configuredSnapshotName = "universal_" + snapshotName;
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);

			// pass just none, unique snapshot name // repository level search
			configuredSnapshotName = "universal_" + projectAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("none", "", "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass just none, duplicate snapshot name at the repository level // repository level search
			configuredSnapshotName = "universal_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("none", "", "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass just none, non-existent snapshot name // repository level search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("none", "", "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" cannot be found in the repository.", errorMessage);
			
			// pass none with workspace value, duplicate snapshot name at the repository level // repository level search, workspace value should be ignored
			configuredSnapshotName = projectAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("none", "", "", workspaceName);
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass none with stream value, duplicate snapshot name at the repository level // repository level search, stream name should be ignored
			configuredSnapshotName = teamAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("none", "", "pa_" + streamName + 1, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass workspace with workspace null value, duplicate snapshot name at the repository level // repository level search, since workspace name is not specified
			configuredSnapshotName = teamAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("workspace", "", "", null);
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass workspace with workspace empty value, duplicate snapshot name at the repository level // repository level search, since workspace name is not specified
			configuredSnapshotName = "universal_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("workspace", "", "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \""+ configuredSnapshotName + "\" found in the repository.", errorMessage);
			
			// pass workspace with invalid workspace name // workspace search should fail
			configuredSnapshotName = teamAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("workspace", "", "", "invalid_" + workspaceName);
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("Unable to find a workspace with name \"" + "invalid_" + workspaceName + "\"", errorMessage);
			
			// pass workspace with valid workspace name, unique snapshot name// workspace level search
			configuredSnapshotName = "universal_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("workspace", "", "", workspaceName);
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass workspace with valid workspace name, duplicate snapshot name at the workspace level// workspace level search
			configuredSnapshotName = snapshotName;
			// add additional blank space to validate that the values are trimmed 
			buildSnapshotContext = new BuildSnapshotContext("workspace", "", "", " " + workspaceName + " ");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \"" + configuredSnapshotName + "\" is associated with the workspace \"" + workspaceName + "\".", errorMessage);
			
			// pass workspace with valid workspace name, non-existent snapshot name at the workspace level but snapshot exists at the repository level// workspace level search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("workspace", "", "", workspaceName);
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" is not associated with the workspace \"" + workspaceName + "\".",
					errorMessage);
			
			// pass stream with invalid project or team area name// process area search should fail
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", "invalid_" + projectAreaName, "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A project area with name \"invalid_" + projectAreaName + "\" cannot be found.", errorMessage);
			
			// pass stream with valid project area name and null stream value, unique snapshot name(have a snapshot with the same name at the team level)// project area level search
			configuredSnapshotName = "universal_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName, null, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass stream with valid team area name and empty stream value, unique snapshot name (have a snapshot with the same name at the project level)// team area level search
			configuredSnapshotName = "universal_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", teamAreaPath, "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass stream with valid project area name and blank stream value, duplicate snapshot name at the project level// project area level search
			configuredSnapshotName = projectAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName, "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \"" + configuredSnapshotName + "\" found in the project area \"" + projectAreaName
					+ "\".", errorMessage);
			
			// pass stream with valid team area name and blank stream value, duplicate snapshot name at the team level// team area level search
			configuredSnapshotName = teamAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", teamAreaPath, "", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \"" + configuredSnapshotName + "\" found in the team area \"" + teamAreaPath + "\".",
					errorMessage);
			
			// pass stream with valid project area name and null stream, non-existent snapshot name at the project level but associated with workspace// project area level search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName, null, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" cannot be found in the project area \"" + projectAreaName + "\".",
					errorMessage);
			
			// pass stream with valid team area name and empty, non-existent snapshot name at the team level// team area level search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", teamAreaPath, null, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" cannot be found in the team area \"" + teamAreaPath + "\".",
					errorMessage);
			// ---
			// pass stream with null process area, invalid stream // stream search should fail
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", null, "invalid_" + streamName, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A stream with name \"invalid_" + streamName + "\" cannot be found in the repository.", errorMessage);

			// pass stream with blank process, area valid stream, unique snapshot name// stream across repository level search
			configuredSnapshotName = projectAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", "",  "pa_"+ streamName + 1, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass stream with empty process area, valid stream, duplicate snapshot name at the stream level// streams across repository level
			configuredSnapshotName = "pa_" + streamName + "2_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", "",  "pa_"+ streamName + 2, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \"" + configuredSnapshotName + "\" is associated with the stream \"pa_" + streamName
					+ "2\".", errorMessage);
			
			// pass stream with empty process area, valid stream, non-existent snapshot name at the stream level but associated with workspace// streams across repository level search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", "",  "ta_"+ streamName + 1, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" is not associated with the stream \"ta_" + streamName + "1\".",
					errorMessage);
			
			// pass stream with valid process area, invalid stream// stream search should fail
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName,  "invalid_"+ streamName, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A stream with name \"invalid_" + streamName + "\" cannot be found in the project area \"" + projectAreaName + "\".",
					errorMessage);

			// pass stream with valid project area, valid stream, unique snapshot name in the stream in the project area// stream in project area search
			configuredSnapshotName = projectAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName,  "pa_"+ streamName + 1, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass stream with valid team area, valid stream, unique snapshot name in the stream in the team area// stream in team area search
			configuredSnapshotName = teamAreaName + "_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", teamAreaPath,  "ta_"+ streamName + 2, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertTrue(errorMessage, errorMessage == null);
			
			// pass stream with valid project area, valid stream, duplicate snapshot name at the stream level in the project area// stream in project area search
			configuredSnapshotName = "pa_" + streamName + "2_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName,  "pa_"+ streamName + 2, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \"" + configuredSnapshotName + "\" is associated with the stream \"pa_" + streamName
					+ "2\" in the project area \"" + projectAreaName + "\".", errorMessage);
			
			// pass stream with valid team area, valid stream, duplicate snapshot name at the stream level in the team area// stream in team area search
			configuredSnapshotName = "ta_" + streamName + "2_" + snapshotName;
			// add additional spaces to validate that the values are trimmed
			buildSnapshotContext = new BuildSnapshotContext(" stream ", " " + teamAreaPath + " ",  " ta_" + streamName + "2 ", "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("More than one snapshot with name \"" + configuredSnapshotName + "\" is associated with the stream \"ta_" + streamName
					+ "2\" in the team area \"" + teamAreaPath + "\".", errorMessage);
			
			// pass stream with valid project area, valid stream, non-existent snapshot name at the stream level in the project area// stream in project area search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", projectAreaName,  "pa_"+ streamName + 2, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" is not associated with the stream \"pa_" + streamName
					+ "2\" in the project area \"" + projectAreaName + "\".", errorMessage);
			
			// pass stream with valid team area, valid stream, non-existent snapshot name at the stream level in the team area// stream in team area search
			configuredSnapshotName = "nonExistent_" + snapshotName;
			buildSnapshotContext = new BuildSnapshotContext("stream", teamAreaPath,  "ta_"+ streamName + 1, "");
			errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT, new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class, // buildSnapshotContext
					String.class, // buildStream
					Locale.class },// clientLocale
					serverURI, userId, password, timeout, buildSnapshotContext.getContextMap(), configuredSnapshotName, LocaleProvider.getLocale());
			assertEquals("A snapshot with name \"" + configuredSnapshotName + "\" is not associated with the stream \"ta_" + streamName
					+ "1\" in the team area \"" + teamAreaPath + "\".", errorMessage);

			
		} finally {
			testingFacade.invoke("tearDownTestBuildSnapshot_complete", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);

		}
	}	
}
