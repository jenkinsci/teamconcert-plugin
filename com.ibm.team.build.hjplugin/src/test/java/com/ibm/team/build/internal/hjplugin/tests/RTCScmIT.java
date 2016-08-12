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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.InvalidCredentialsException;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCRepositoryBrowser;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import hudson.util.Secret;

@SuppressWarnings("nls")
public class RTCScmIT {

	private static final String BUILD_TOOLKIT = "buildToolkit";
	
	private static final String USER_ID = "userId";
	private static final String PASSWORD = "password";
	private static final String PASSWORD_FILE = "passwordFile";
	private static final String CREDENTIALS_ID = "_.credentialsId";
	private static final String TIMEOUT = "timeout";

	private static final String TEST_SERVER_URI = "https://localhost:9443/jazz";
	private static final String TEST_TIMEOUT = "480";
	private static final String TEST_USER_ID = "bill";
	private static final String TEST_PASSWORD = "bill";
	private static final String TEST_PASSWORD_FILE = "C:/Users/bill/bill-password";
	private static final String TEST_CRED_ID = "5678";
	private static final String TEST_BUILD_WORKSPACE = "compile-and-test";
	private static final String TEST_BUILD_DEFINITION = "_Sf_R8EhyEeKuMu7IPRTOeQ";
	private static final String TEST_BUILD_SNAPSHOT = "_vf_F8EyGeBuAp7IPRTOeQ";
	private static final String TEST_BUILD_STREAM = "compile-and-test-stream";
	
	private static final String CONFIG_TOOLKIT_NAME = "config_toolkit";
	private static final String BUILDTOOLKITNAME = "rtc-build-toolkit";

	private String buildTool;
	private String serverURI;
	private String timeout;
	private String userId;
	private String password;
	private int timeoutInt;

	private RTCScm createEmptyRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "", "", "", "");
		return new RTCScm(false, "", "", 0, "", Secret.fromString(""), "", "", buildSource, false);
	}

	private RTCScm getRTCScm(BuildType buildType) throws InvalidCredentialsException {
		RTCBuildToolInstallation tool = new RTCBuildToolInstallation(CONFIG_TOOLKIT_NAME, Config.DEFAULT.getToolkit(), Collections.<ToolProperty<?>> emptyList());
		tool.getDescriptor().setInstallations(tool);
		RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		RTCScm scm = new RTCScm(true, "config_toolkit", loginInfo.getServerUri(), loginInfo.getTimeout(), loginInfo.getUserId(),
				Secret.fromString(loginInfo.getPassword()), "", "", buildType, false);
		return scm;
	}

	private RTCScm createTestOverrideGlobalRTCScm(boolean useCreds) {
		BuildType buildSource = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, TEST_BUILD_DEFINITION, TEST_BUILD_WORKSPACE, TEST_BUILD_SNAPSHOT, TEST_BUILD_STREAM);
		return new RTCScm(true, "", TEST_SERVER_URI, Integer.parseInt(TEST_TIMEOUT), TEST_USER_ID, Secret.fromString(TEST_PASSWORD), TEST_PASSWORD_FILE,
				useCreds ? TEST_CRED_ID : null, buildSource, false);
	}

	@Before
	public void setUp() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			Config config = Config.DEFAULT;
			buildTool = CONFIG_TOOLKIT_NAME;
			serverURI = config.getServerURI();
			timeoutInt = config.getTimeout();
			timeout = String.valueOf(config.getTimeout());
			userId = config.getUserID();
			password = config.getPassword();
		}
	}

	@Rule public JenkinsRule r = new JenkinsRule();

	@Test
	public void testRTCScmConstructor() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCScm scm = createTestOverrideGlobalRTCScm(true);
			assertEquals(TEST_SERVER_URI, scm.getServerURI());
			assertEquals(Integer.parseInt(TEST_TIMEOUT), scm.getTimeout());
			assertEquals(TEST_CRED_ID, scm.getCredentialsId());
			assertEquals(null, scm.getUserId());
			assertEquals(null, scm.getPassword());
			assertEquals(null, scm.getPasswordFile());
			assertEquals(RTCScm.BUILD_DEFINITION_TYPE, scm.getBuildType().value);
			assertEquals(RTCScm.BUILD_DEFINITION_TYPE, scm.getBuildTypeStr());
			assertEquals(TEST_BUILD_DEFINITION, scm.getBuildDefinition());
			assertEquals(TEST_BUILD_WORKSPACE, scm.getBuildWorkspace());
			assertEquals(TEST_BUILD_SNAPSHOT, scm.getBuildSnapshot());
			
			RTCChangeLogChangeSetEntry.WorkItemDesc workItem = new WorkItemDesc();
			workItem.setNumber("2");
			RTCRepositoryBrowser browser = (RTCRepositoryBrowser) scm.getEffectiveBrowser();
			assertEquals(TEST_SERVER_URI + "/resource/itemName/com.ibm.team.workitem.WorkItem/2" , browser.getWorkItemLink(workItem).toString());

			scm = createTestOverrideGlobalRTCScm(false);
			assertEquals(TEST_SERVER_URI, scm.getServerURI());
			assertEquals(Integer.parseInt(TEST_TIMEOUT), scm.getTimeout());
			assertEquals(null, scm.getCredentialsId());
			assertEquals(TEST_USER_ID, scm.getUserId());
			assertEquals(TEST_PASSWORD, scm.getPassword());
			assertEquals(TEST_PASSWORD_FILE, scm.getPasswordFile());
			assertEquals(RTCScm.BUILD_DEFINITION_TYPE, scm.getBuildType().value);
			assertEquals(RTCScm.BUILD_DEFINITION_TYPE, scm.getBuildTypeStr());
			assertEquals(TEST_BUILD_DEFINITION, scm.getBuildDefinition());
			assertEquals(TEST_BUILD_WORKSPACE, scm.getBuildWorkspace());
			assertEquals(TEST_BUILD_SNAPSHOT, scm.getBuildSnapshot());

			browser = (RTCRepositoryBrowser) scm.getEffectiveBrowser();
			assertEquals(TEST_SERVER_URI + "/resource/itemName/com.ibm.team.workitem.WorkItem/2" , browser.getWorkItemLink(workItem).toString());

		}
	}

	@Test public void testDoCheckBuildTool() throws IOException {
		if (Config.DEFAULT.isConfigured()) {
			DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().getDescriptor(RTCScm.class);
	
			// null is not a build toolkit
			assertDoCheckBuildTool(descriptor, FormValidation.Kind.ERROR, null);
			assertDoCheckBuildTool(descriptor, FormValidation.Kind.ERROR, "");

			// null is not a build toolkit path
			assertDoCheckBuildToolKitPath(FormValidation.Kind.ERROR, null);
			assertDoCheckBuildToolKitPath(FormValidation.Kind.ERROR, "");

			File tempFolder = Files.createTempDir();
			File tempBuildToolkitTaskDefsXmlFile = new File(tempFolder, RTCBuildToolInstallation.BUILD_TOOLKIT_TASK_DEFS_XML);
			tempBuildToolkitTaskDefsXmlFile.createNewFile();
			String tempBuildToolkitTaskDefsXmlPath = tempBuildToolkitTaskDefsXmlFile.getAbsolutePath();
	
			// build toolkit task defs file is not a build toolkit
			assertDoCheckBuildToolKitPath(FormValidation.Kind.ERROR, tempBuildToolkitTaskDefsXmlPath);
	
			String tempFolderPath = tempFolder.getAbsolutePath();
	
			// folder containing build toolkit task defs file is a build toolkit ... probably ;-)
			assertDoCheckBuildToolKitPath(FormValidation.Kind.OK, tempFolderPath);
	
			boolean deleted = tempBuildToolkitTaskDefsXmlFile.delete();
	
			if (deleted) {
	
				// folder not containing build toolkit task defs file is not a build toolkit
				assertDoCheckBuildToolKitPath(FormValidation.Kind.ERROR, tempFolderPath);
				
				// missing file is not a build toolkit
				assertDoCheckBuildToolKitPath(FormValidation.Kind.ERROR, tempBuildToolkitTaskDefsXmlPath);
			}
	
			deleted = tempFolder.delete();
	
			if (deleted) {
				
				// missing folder is not a build toolkit
				assertDoCheckBuildToolKitPath(FormValidation.Kind.ERROR, tempFolderPath);
			}
		}
	}

	private void assertDoCheckBuildToolKitPath(FormValidation.Kind kind,
			String buildToolkitPath) {
		FormValidation validation = RTCBuildToolInstallation.validateBuildToolkit(false, buildToolkitPath);
		assertEquals("Expected toolkit path validation " + kind + ": " + BUILD_TOOLKIT + "=\"" + buildToolkitPath + "\"", kind, validation.kind);
	}

	private void assertDoCheckBuildTool(DescriptorImpl descriptor, FormValidation.Kind kind, String buildToolkit) {
		FormValidation validation = descriptor.doCheckBuildTool(buildToolkit);
		assertEquals("Expected toolkit validation " + kind + ": " + BUILD_TOOLKIT + "=\"" + buildToolkit + "\"", kind, validation.kind);
	}

	@Test public void testDoCheckCredentials() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = r.createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			project.setScm(rtcScm);
	
			DescriptorImpl descriptor = (DescriptorImpl) project.getDescriptorByName(RTCScm.class.getName());

			assertDoCheckCredentials(descriptor, FormValidation.Kind.ERROR, null, null, null, null);
			assertDoCheckCredentials(descriptor, FormValidation.Kind.ERROR, null, "", null, null);
			assertDoCheckCredentials(descriptor, FormValidation.Kind.ERROR, null, null, "", null);
			assertDoCheckCredentials(descriptor, FormValidation.Kind.ERROR, null, null, null, "");
			assertDoCheckCredentials(descriptor, FormValidation.Kind.ERROR, "", "", "", "");
			assertDoCheckCredentials(descriptor, FormValidation.Kind.OK, null, null, null, TEST_CRED_ID);
			assertDoCheckCredentials(descriptor, FormValidation.Kind.OK, "", "", "", TEST_CRED_ID);
		}
	}

	@Test public void testDoCheckUserId() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = r.createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			project.setScm(rtcScm);
	
			DescriptorImpl descriptor = (DescriptorImpl) project.getDescriptorByName(RTCScm.class.getName());

			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, null, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, "", null, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, "", null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, "", "", "");
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, null, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, "", null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, TEST_PASSWORD_FILE, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, "", TEST_PASSWORD_FILE, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, TEST_PASSWORD_FILE, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, null, null, null, TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, "", "", "", TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, null, TEST_PASSWORD, null, TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, "", TEST_PASSWORD, null, TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, "", null, TEST_PASSWORD_FILE, TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.OK, null, null, TEST_PASSWORD_FILE, TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.ERROR, null, TEST_PASSWORD, null, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.ERROR, null, TEST_PASSWORD, "", null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.ERROR, null, null, TEST_PASSWORD_FILE, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.ERROR, null, "", TEST_PASSWORD_FILE, null);
			assertDoCheckUserId(descriptor, FormValidation.Kind.WARNING, TEST_USER_ID, null, null, TEST_CRED_ID);
			assertDoCheckUserId(descriptor, FormValidation.Kind.WARNING, TEST_USER_ID, TEST_PASSWORD, TEST_PASSWORD_FILE, TEST_CRED_ID);
		}
	}

	@Test public void testDoCheckPassword() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = r.createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			project.setScm(rtcScm);
	
			DescriptorImpl descriptor = (DescriptorImpl) project.getDescriptorByName(RTCScm.class.getName());

			assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, null, null, null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, "", null, null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, null, "", null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, "", "", "");
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, null, null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, "", null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.WARNING, "", TEST_PASSWORD, null, TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.WARNING, null, TEST_PASSWORD, "", TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, "", null, TEST_PASSWORD_FILE, TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, null, "", TEST_PASSWORD_FILE, TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, TEST_PASSWORD_FILE, null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, "", TEST_PASSWORD_FILE, null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, null, null, null, TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, "", "", "", TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.WARNING, TEST_USER_ID, TEST_PASSWORD, TEST_PASSWORD_FILE, null);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, null, TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, TEST_PASSWORD_FILE, TEST_CRED_ID);
			assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, null, TEST_CRED_ID);

		}
	}

	@Test public void testDoCheckPasswordFile() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File testPasswordFileFile = File.createTempFile("ADMIN-password", null);
			String testPasswordFile = testPasswordFileFile.getAbsolutePath();
	
			FreeStyleProject project = r.createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			project.setScm(rtcScm);
	
			DescriptorImpl descriptor = (DescriptorImpl) project.getDescriptorByName(RTCScm.class.getName());

			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, null, null, null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, "", null, null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, null, "", null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, "", "", "");
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, null, null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, "", null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, testPasswordFile, null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, "", testPasswordFile, null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, null, null, null, TEST_CRED_ID);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, "", "", "", TEST_CRED_ID);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.WARNING, TEST_USER_ID, TEST_PASSWORD, testPasswordFile, null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, null, "doesnotexist", null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.ERROR, TEST_USER_ID, TEST_PASSWORD, "doesnotexist", null);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, null, TEST_CRED_ID);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, TEST_PASSWORD, testPasswordFile, TEST_CRED_ID);
			assertDoCheckPasswordFile(descriptor, FormValidation.Kind.OK, TEST_USER_ID, null, testPasswordFile, TEST_CRED_ID);

			testPasswordFileFile.delete();
		}
	}
	
	private void assertDoCheckCredentials(DescriptorImpl descriptor, FormValidation.Kind kind, String userId, String password, String passwordFile, String credId) {
		FormValidation validation = descriptor.doCheckCredentialsId(credId, userId, password, passwordFile);
		assertEquals("Expected credentials validation " + kind + ": "
				+ CREDENTIALS_ID + "=\"" + credId + "\", " + USER_ID + "=\""
				+ userId + "\", " + PASSWORD + "=\"" + password + "\", "
				+ PASSWORD_FILE + "=\"" + passwordFile + "\"", kind,
				validation.kind);
	}
	
	private void assertDoCheckUserId(DescriptorImpl descriptor, FormValidation.Kind kind, String userId, String password, String passwordFile, String credId) {
		FormValidation validation = descriptor.doCheckUserId(credId, userId, password, passwordFile);
		assertEquals("Expected credentials validation " + kind + ": "
				+ CREDENTIALS_ID + "=\"" + credId + "\", " + USER_ID + "=\""
				+ userId + "\", " + PASSWORD + "=\"" + password + "\", "
				+ PASSWORD_FILE + "=\"" + passwordFile + "\"", kind,
				validation.kind);
	}
	
	private void assertDoCheckPassword(DescriptorImpl descriptor, FormValidation.Kind kind, String userId, String password, String passwordFile, String credId) {
		FormValidation validation = descriptor.doCheckPassword(credId, userId, password, passwordFile);
		assertEquals("Expected password validation " + kind + ": "
				+ CREDENTIALS_ID + "=\"" + credId + "\", " + USER_ID + "=\""
				+ userId + "\", " + PASSWORD + "=\"" + password + "\", "
				+ PASSWORD_FILE + "=\"" + passwordFile + "\"", kind,
				validation.kind);
	}
	
	private void assertDoCheckPasswordFile(DescriptorImpl descriptor, FormValidation.Kind kind, String userId, String password, String passwordFile, String credId) {
		FormValidation validation = descriptor.doCheckPasswordFile(credId, userId, password, passwordFile);
		assertEquals("Expected password validation " + kind + ": "
				+ CREDENTIALS_ID + "=\"" + credId + "\", " + USER_ID + "=\""
				+ userId + "\", " + PASSWORD + "=\"" + password + "\", "
				+ PASSWORD_FILE + "=\"" + passwordFile + "\"", kind,
				validation.kind);
	}
	

	@Test public void testDoCheckTimeout() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = r.createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			project.setScm(rtcScm);
	
			DescriptorImpl descriptor = (DescriptorImpl) project.getDescriptorByName(RTCScm.class.getName());
	
			assertDoCheckTimeout(descriptor, FormValidation.Kind.ERROR, null);
			assertDoCheckTimeout(descriptor, FormValidation.Kind.ERROR, "");
			assertDoCheckTimeout(descriptor, FormValidation.Kind.ERROR, "-1");
			assertDoCheckTimeout(descriptor, FormValidation.Kind.ERROR, "0");
			assertDoCheckTimeout(descriptor, FormValidation.Kind.OK, "1000");
			assertDoCheckTimeout(descriptor, FormValidation.Kind.OK, TEST_TIMEOUT);
		}
	}

	private void assertDoCheckTimeout(DescriptorImpl descriptor, FormValidation.Kind kind, String timeout) {
		FormValidation validation = descriptor.doCheckTimeout(timeout);
		assertEquals("Expected timeout validation " + kind + ": " + TIMEOUT + "=\"" + timeout + "\"", kind, validation.kind);
	}

	@SuppressWarnings("unchecked")
	@Test public void testDoValidateBuildWorkspaceConfiguration() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		FreeStyleProject project = r.createFreeStyleProject();
		// that build type that we set here is just for creation, it is not significant for the following validation
		// we manually pass the values to the validation method
		BuildType buildType = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "SomeBuildWorkspace", null, null, "");
		RTCScm rtcScm = getRTCScm(buildType);
		project.setScm(rtcScm);

		DescriptorImpl descriptor = (DescriptorImpl)project.getDescriptorByName(RTCScm.class.getName());
		// buildWorkspace - null
		FormValidation result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password,
				null, null, "false", null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_workspace_empty(), result.renderHtml());

		// buildWorkspace - empty string
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_workspace_empty(), result.renderHtml());

		// buildWorkspace - blank string
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "   ");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_workspace_empty(), result.renderHtml());

		// invalid connect info
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null,
				"false", "test");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());

		// valid connect info - using build toolkit and invalid workspace
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "test");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals("Unable to find a workspace with name &quot;test&quot;", result.renderHtml());
		
		// create workspaces
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildWorkspace", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class, // singleWorkspaceName,
				String.class }, // multipleWorkspaceName
				serverURI, userId, password, timeoutInt, "Singly Occuring=WS", "Multiple Occurrence=WS");
		try {
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, invalid workspace - multiple workspace
			// warning connect info and error workspace - only error should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "Multiple Occurrence=WS");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("More than 1 repository workspace has the name &quot;Multiple Occurrence=WS&quot;", result.renderHtml());
			
			// valid connect info - using build toolkit and warning workspace (parameterized values) 
			// only warning should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "${rtcRepositoryWorkspace}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_repository_workspace_not_validated(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and warning workspace (parameterized values) 
			// both the warnings should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", "", serverURI, timeout, userId, password, null, null,
					"true", "${rtcRepositoryWorkspace}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job() + "<br/>" + Messages.RTCScm_repository_workspace_not_validated(),
					result.renderHtml());

			// valid connect info - using build toolkit and valid workspace
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "Singly Occuring=WS");
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, valid workspace
			// warning connect info and valid workspace - only warning should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "Singly Occuring=WS");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job(), result.renderHtml());
			
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}

	@Test public void testDoValidateBuildDefinitionConfiguration() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		FreeStyleProject project = r.createFreeStyleProject();
		// that build type that we set here is just for creation, it is not significant for the following validation
		// we manually pass the values to the validation method
		BuildType buildType = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, "SomeBuildDefinition", null, null, "");
		RTCScm rtcScm = getRTCScm(buildType);
		project.setScm(rtcScm);

		DescriptorImpl descriptor = (DescriptorImpl)project.getDescriptorByName(RTCScm.class.getName());
		// buildDefinition - null
		FormValidation result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, userId, password,
				null, null, "false", null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_definition_empty(), result.renderHtml());

		// buildDefinition - empty string
		result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_definition_empty(), result.renderHtml());

		// buildDefinition - blank string
		result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "   ");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_definition_empty(), result.renderHtml());

		// invalid connect info
		result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null,
				"false", "test");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());

		// valid connect info - using build toolkit and invalid build definition
		result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "test");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals("Unable to find a build definition with ID: &quot;test&quot;", result.renderHtml());
		
		// warning connect info - avoidUsingBuildToolkit and buildTool is null, invalid build definition
		// warning connect info and error build definition - only error should be displayed
		result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
				"true", "test");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals("Unable to find a build definition with name &quot;test&quot;", result.renderHtml());
		
		// create buildDefinition
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		String buildDefinitionName = "BuildDefinitionName" + System.currentTimeMillis();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade.invoke(
				"setupTestBuildDefinition", new Class[] {
						String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class}, // Build Definition Name,
						serverURI, userId, password, timeoutInt, buildDefinitionName);
		try {
			// valid connect info - using build toolkit and warning build definition (parameterized value)
			// only warning should be displayed
			result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "${rtcBuildDefinition}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_definition_not_validated(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool is null, warning build definition (parameterized value)
			// both the warning messages should be displayed
			result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job() + "<br/>" + Messages.RTCScm_build_definition_not_validated(), result.renderHtml());
			// valid connect info - using build toolkit and valid build definition
			result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", buildDefinitionName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, valid build definition
			// only warning message should be displayed
			result = descriptor.doValidateBuildDefinitionConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", buildDefinitionName);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job(), result.renderHtml());

		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test public void testDoValidateBuildStreamConfiguration() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		FreeStyleProject project = r.createFreeStyleProject();
		// that build type that we set here is just for creation, it is not significant for the following validation
		// we manually pass the values to the validation method
		BuildType buildType = new BuildType(RTCScm.BUILD_STREAM_TYPE, "SomeBuildStream", null, null, "");
		RTCScm rtcScm = getRTCScm(buildType);
		project.setScm(rtcScm);

		DescriptorImpl descriptor = (DescriptorImpl)project.getDescriptorByName(RTCScm.class.getName());
		// buildStream - null
		FormValidation result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password,
				null, null, "false", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_stream_empty(), result.renderHtml());

		// buildStream - empty string
		result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", null, "");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_stream_empty(), result.renderHtml());

		// buildStream - blank string
		result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", null, "   ");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_stream_empty(), result.renderHtml());
		
		// invalid connect info
		result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null,
				"false", null, "testStream");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());
		
		// create project area and build stream
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		String projectAreaName = "testDoValidateBuildStreamConfiguration" + System.currentTimeMillis();
		String streamName = "testDoValidateBuildStreamConfigurationStream" + System.currentTimeMillis();
		// create a project area with a single team area
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildStream_basic", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class,  // projectAreaName
				String.class }, //streamName
				serverURI, userId, password, timeoutInt, projectAreaName, streamName);

		try {
			// valid connect info - using build toolkit and non-null invalid process area, valid stream
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "testProject", streamName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());
			
			// valid connect info - avoidUsingBuildToolkiit and build toolkit is provided and non-null invalid process area, valid stream
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", "testProject", streamName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, non-null invalid process area, valid stream
			// warning connect info and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "testProject", streamName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// valid connect info - using build toolkit and non-null invalid process area, warning stream (parameterized value)
			// only error message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "testProject", "${rtcStream}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());

			// valid connect info - avoidUsingBuildToolkiit and build toolkit is provided and non-null invalid process area, warning stream (parameterized value)
			// only error message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", "testProject", "${rtcStream}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, non-null invalid process area, warning stream (parameterized value)
			// warning connect info, warning stream and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "testProject", "${rtcStream}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());

			// valid connect info - using build tool kit, valid process area, invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", projectAreaName, "testStream");
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the project area &quot;" + projectAreaName + "&quot;.", result.renderHtml());
			
			// valid connect info - avoidUsingBuildToolkit and buildTool is provided, valid process area, invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "testStream");
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the project area &quot;" + projectAreaName + "&quot;.", result.renderHtml());
			
			// valid connect info - using build tool kit, valid process area(null), invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, "testStream");
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the repository.", result.renderHtml());
			
			// valid connect info - avoidUsingBuildToolkit and buildTool is provided, valid process area(empty), invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", "   ", "testStream");
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the repository.", result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, valid process area, invalid stream value
			// warning connect info and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "testStream");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// warning connect info avoidUsingBuildToolkit and buildTool is invalid, valid(null) process area, invalid stream value
			// warning connect info and error stream - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", "test", serverURI, timeout, userId, password, null, null,
					"true", null, "testStream");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the repository.", result.renderHtml());
			
			// valid connectinfo - using build toolkit, valid process area, warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", projectAreaName, "${rtcStream}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());
				
			// valid connectinfo - avoidUsingBuildToolkit and buildTool is provided, valid process area, warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "${rtcStream}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());			

			// valid connectinfo - using build toolkit, valid process area (null), warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, "${rtcStream}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());

			// valid connectinfo - avoidUsingBuildToolkit and a buildTool is provided, valid process area (null), warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", null, "${rtcStream}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool, valid process area, warning stream value (parameterized value)
			// warning connect info, warning stream value and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "${rtcStream}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool, valid (null) process area, warning stream value (parameterized value)
			// warning connect info, warning stream value and validation success - both the warning messages should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", null, "${rtcStream}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job() + "<br/>" + Messages.RTCScm_stream_not_validated(), result.renderHtml());
			
			// valid connectinfo - using build toolkit, valid process area, valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", projectAreaName, streamName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo - avoidUsingBuildToolkit and buildTool is provided, valid process area, valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, streamName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo - using build toolkit, valid process area (null), valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, streamName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo - avoidUsingBuildToolkit and a buildTool is provided, valid process area (null), valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", null, streamName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool, valid process area, valid stream value
			// warning connect info and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, streamName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool, valid (null) process area, valid stream value
			// warning connect info and validation success - only warning should be displayed 
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", null, streamName);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job(), result.renderHtml());

		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}		
	}

	@SuppressWarnings("unchecked")
	@Test public void testDoValidateBuildSnapshotConfiguration() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		FreeStyleProject project = r.createFreeStyleProject();
		// that build type that we set here is just for creation, it is not significant for the following validation
		// we manually pass the values to the validation method
		BuildType buildType = new BuildType(RTCScm.BUILD_SNAPSHOT_TYPE, "SomeBuildSnapshot", null, null, "");
		RTCScm rtcScm = getRTCScm(buildType);
		project.setScm(rtcScm);

		DescriptorImpl descriptor = (DescriptorImpl)project.getDescriptorByName(RTCScm.class.getName());
		// buildSnapshot - null
		FormValidation result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password,
				null, null, "false", "", "", "", "", null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_snapshot_empty(), result.renderHtml());

		// buildSnapshot - empty string
		result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "", "", "", "", "");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_snapshot_empty(), result.renderHtml());

		// buildSnapshot - blank string
		result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "", "", "", "", "   ");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_snapshot_empty(), result.renderHtml());

		// invalid connect info
		result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null, "false",
				"", "", "", "", "testStream");
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());

		// create project area, build stream, and a snapshot
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		String projectAreaName = "testDoValidateBuildSnapshotConfiguration" + System.currentTimeMillis();
		String streamName = "testDoValidateBuildSnapshotConfigurationStream" + System.currentTimeMillis();
		String workspaceName = "testDoValidateBuildSnapshotConfigurationWorkspace" + System.currentTimeMillis();
		String snapshotName = "testDoValidateBuildSnapshotConfigurationSnapshot" + System.currentTimeMillis();
		String wsSnapshotName = "ws" + snapshotName;
		// create a project area with a single team area
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildSnapshot_basic", new Class[] {
				String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class, // projectAreaName
				String.class, // streamName
				String.class,
				String.class }, // snapshotName
				serverURI, userId, password, timeoutInt, projectAreaName, streamName, workspaceName, snapshotName);
		try {

			// warning connect info - avoidUsingBuildToolkit and buildTool is null, valid snapshot value
			// warning connect info and cannot validate snapshot error - only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null, "true",
					"", "", "", "", snapshotName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_snapshot(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool is null, warning snapshot value (job
			// property)
			// warning connect info and cannot validate snapshot error - only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null, "true",
					"", "", "", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_snapshot(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool is null, invalid snapshot value
			// warning connect info and error snapshot - only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null, "true",
					"", "", "", "", "testSnapshot");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_snapshot(), result.renderHtml());

			// valid connect info, invalid snapshot value
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "", "", "", "", "testSnapshot");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A snapshot with name &quot;testSnapshot&quot; cannot be found in the repository.", result.renderHtml());

			// valid connectinfo, valid snapshot value
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "", "", "", "", snapshotName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to none
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "none", "", "", "", snapshotName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to workspace
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName, wsSnapshotName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to stream without specifying project
			// area, but having a workspace name
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName, "ignoreWorkspaceName", snapshotName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to stream with specifying project
			// area
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName, "", snapshotName);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to workspace
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName + "invalid", wsSnapshotName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("Unable to find a workspace with name &quot;" + workspaceName + "invalid&quot;", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream without specifying
			// project area, but having a workspace name
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName + "invalid", "ignoreWorkspaceName", snapshotName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the repository.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName + "invalid", "", snapshotName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the project area &quot;" + projectAreaName
					+ "&quot;.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName + "invalid", streamName + "invalid", "", snapshotName);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;" + projectAreaName + "invalid&quot; cannot be found.", result.renderHtml());
			
			// valid connectinfo, warning snapshot value(job property)
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, "", "", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to none
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "none", "", "", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to workspace
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName, "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());
			
			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to workspace no workspaceName
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());


			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to stream without
			// specifying project area, but having a workspace name
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName, "ignoreWorkspaceName", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());
			

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to stream without
			// specifying project area and stream name
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", "", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, valid snapshot value, valid snapshotOwnerType set to stream with specifying project
			// area
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName, "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), invalid snapshotOwnerType set to workspace
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName + "invalid", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("Unable to find a workspace with name &quot;" + workspaceName + "invalid&quot;", result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), invalid snapshotOwnerType set to stream without
			// specifying project area, but having a workspace name
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName + "invalid", "ignoreWorkspaceName", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the repository.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName + "invalid", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the project area &quot;" + projectAreaName
					+ "&quot;.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName + "invalid", streamName + "invalid", "", "${rtcBuildSnapshot}");
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;" + projectAreaName + "invalid&quot; cannot be found.", result.renderHtml());

		} finally {
			testingFacade.invoke("tearDown", new Class[] { String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Map.class }, // setupArtifacts
					serverURI, userId, password, timeoutInt, setupArtifacts);

		}
	}

	
	
	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test 
	public void buildDefinitionNameItemIdPairFoundInChangeLogSet() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();
		String buildDefinitionId = getTestName() + System.currentTimeMillis();
		
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
		String buildDefinitionItemId = setupArtifacts.get(Utils.ARTIFACT_BUILDDEFINITION_ITEM_ID);

		try {
			// Setup a build with build definition configuration
			FreeStyleProject prj = setupFreeStyleJobForBuildDefinition(buildDefinitionId);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			
			// Get the buildresultUUID from actions and insert it into setupArtifacts
			List<RTCBuildResultAction> rtcActions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, rtcActions.size());
			RTCBuildResultAction action = rtcActions.get(0);
			
			// Verify build result getting created
			assertNotNull(action.getBuildResultUUID());
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID, action.getBuildResultUUID());

			RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();
			
			assertEquals(buildDefinitionItemId, changelog.getBuildDefinitionItemId());
			assertEquals(buildDefinitionId, changelog.getBuildDefinitionName());
			
			// No previous build url is written for build definition builds
			assertEquals("", changelog.getPreviousBuildUrl());
	
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test 
	public void repositoryWorkspaceNameItemIdPairFoundInChangeLogSet() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String streamName = getTestName() + System.currentTimeMillis();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String workspaceUUID = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			FreeStyleProject prj = setupFreeStyleJobForWorkspace(workspaceName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();

			// Verify name and itemId pair inside changelogset
			assertEquals(workspaceName, changelog.getWorkspaceName());
			assertEquals(workspaceUUID, changelog.getWorkspaceItemId());
			// Since this is the first build, prevBuildUrl is null
			assertEquals("", changelog.getPreviousBuildUrl());		
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	
	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test 
	public void buildDefinitionNameItemIdPairFoundInChangeLogFile() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();
		String buildDefinitionId = getTestName() + System.currentTimeMillis();
		
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
		String buildDefinitionItemId = setupArtifacts.get(Utils.ARTIFACT_BUILDDEFINITION_ITEM_ID);

		try {
			// Setup a build with build definition configuration
			FreeStyleProject prj = setupFreeStyleJobForBuildDefinition(buildDefinitionId);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			File changelogFile = new File(build.getRootDir(), "changelog.xml");

			// Get the buildresultUUID from actions and insert it into setupArtifacts
			List<RTCBuildResultAction> rtcActions = build.getActions(RTCBuildResultAction.class);
			assertEquals(1, rtcActions.size());
			RTCBuildResultAction action = rtcActions.get(0);
			
			// Verify build result getting created
			assertNotNull(action.getBuildResultUUID());
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID, action.getBuildResultUUID());

			// Verify name and itemId pair inside changelog file
			assertNotNull("Expecting buildDefinition Id tag", Utils.getMatch(changelogFile, ".*buildDefinitionName=\"" + buildDefinitionId +"\".*"));
			assertNotNull("Expecting buildDefinition ItemId tag", Utils.getMatch(changelogFile, ".*buildDefinitionItemId=\"" + buildDefinitionItemId +"\".*"));
			assertNotNull("Expecting previousBuildUrl tag", Utils.getMatch(changelogFile, ".*previousBuildUrl=\"\".*"));
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test 
	public void repositoryWorkspaceNameItemIdPairFoundInChangeLogFile() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String streamName = getTestName() + System.currentTimeMillis();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC,
							streamName);
		String workspaceItemId = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			FreeStyleProject prj = setupFreeStyleJobForWorkspace(workspaceName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			File changelogFile = new File(build.getRootDir(), "changelog.xml");
			
			// Verify name and itemId pair inside changelog file
			assertNotNull("Expecting workspaceName tag", Utils.getMatch(changelogFile, ".*workspaceName=\"" + workspaceName +"\".*"));
			assertNotNull("Expecting workspaceItemId tag", Utils.getMatch(changelogFile, ".*workspaceItemId=\"" + workspaceItemId +"\".*"));
			assertNotNull("Expecting previousBuildUrl tag", Utils.getMatch(changelogFile, ".*previousBuildUrl=\"\".*"));
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}
	
	
	//public void streamNameItemIdPairWithPreviousBuildUrlFoundInBuildResultPage() throws Exception	
	
	//public void buildDefinitionNameItemIdPairFoundInBuildResultPage() throws Exception
	
	//public void repositoryWorkspaceNameItemIdPairFoundInBuildResultPage() throws Exception
	
	
	/**
	 * Build definition, normal checkout + polling through Build toolkit
	 * 1) Positive case when name is provided
	 * 2) Negative case when name is empty
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test 
	public void buildDefinitionCheckoutAndPollingWithBuildtoolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String workspaceName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();
		String buildDefinitionId = getTestName() + System.currentTimeMillis();
		Map<String, String> setupArtifacts = Utils.setupBuildDefinition(testingFacade, defaultC, 
					buildDefinitionId, workspaceName, componentName);		
		try {
			// positive case when there is a valid build definition Id
			{
				FreeStyleProject prj = setupFreeStyleJobForBuildDefinition(buildDefinitionId);
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				
				// Verify
				Utils.verifyRTCScmInBuild(build, true);
				RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
				setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID, action.getBuildResultUUID());

				// Run polling and check the log file
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				// Ensure that there are no changes and polling happened successfully
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, buildDefinitionId);
			}
			// negative case when build definition id is empty
			{
				FreeStyleProject prj = setupFreeStyleJobForBuildDefinition(null);
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				
				// Verify that build failed and there is a check ut failure message
				assertEquals(Result.FAILURE, build.getResult());
				Utils.getMatch(build.getLogFile(), "RTC : checkout failure: The parameter \"buildDefinitionId\" must not be null");
				
				// Run polling and check the log file
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				// Ensure that there are no changes and polling happened successfully
				assertEquals(Change.NONE, pollingResult.change);
				Utils.getMatch(pollingFile, "RTC : checking for changes failure: More than one repository workspace has the name \"\"");
			}
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}

	/**
	 * Repository Workspace, normal checkout + polling through build toolkit
	 * 1) Positive case when name is provided
	 * 2) Negative case when name is empty
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test 
	public void repositoryWorkspaceCheckoutAndPollingWithBuildtoolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		String streamName = getTestName() + System.currentTimeMillis();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC,
							streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			{ // positive case - when workspaceName is not null
				FreeStyleProject prj = setupFreeStyleJobForWorkspace(workspaceName);
				
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				
				// Verify
				Utils.verifyRTCScmInBuild(build, false);
				
				// Start polling and check whether there are no changes and polling ran successfully
				// Run polling and check the log file
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				// Verify
				Utils.assertPollingMessagesWhenNoChanges(pollingResult, pollingFile, workspaceName);
			}
			{ // negative case - when workspaceName is null
				FreeStyleProject prj = setupFreeStyleJobForWorkspace(null);
				
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, null);
				
				// Verify that build failed and there is a checkout failure message
				assertEquals(Result.FAILURE, build.getResult());
				Utils.getMatch(build.getLogFile(), "RTC : checkout failure: More than one repository workspace has the name \"null\"");
				
				// Run polling and check the log file
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				// Ensure that there are no changes and polling happened successfully
				assertEquals(Change.NONE, pollingResult.change);
				Utils.getMatch(pollingFile, "RTC : checking for changes failure: More than one repository workspace has the name \"null\"");
			}
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}

	/**
	 * Snapshot, normal checkout + polling through build toolkit
	 * 1) Positive case when name is provided
	 * 2) Negative case when name is empty
	 * Note that in both cases, polling should say that it is not a supported configuration.
	 * Checkout should succeed for the positive case and fail in the negative case.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void snapshotCheckoutAndPollingWithBuildtoolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getTestName() + System.currentTimeMillis();
		String snapshotName = getTestName() + System.currentTimeMillis();
		String componentName = getTestName() + System.currentTimeMillis();

		
		RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
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
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINE_ITEM_ID);
		try {
			{ // valid snapshot UUID - checkout and polling
				FreeStyleProject prj = setupFreeStyleJobForSnapshot(snapshotUUID);
				FreeStyleBuild build = Utils.runBuild(prj, null);
	
				// Verify the build status
				assertNotNull(build);
				assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
				
				// Get the build snapshot UUID from build result action 
				RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
				assertEquals(snapshotUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID));
				
				// polling when snapshotUUID is a valid one
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				assertEquals(Change.NONE, pollingResult.change);
				assertNotNull("Expecting not supported message", Utils.getMatch(pollingFile, "Polling is not supported for this configuration. Polling is supported only for build definition, repository workspace and stream configurations."));
			}
			{ // null snapshot UUID  - checkout and polling
				FreeStyleProject prj = setupFreeStyleJobForSnapshot(null);
				FreeStyleBuild build = Utils.runBuild(prj, null);
	
				// Verify the build status
				assertNotNull(build);
				assertEquals(Result.FAILURE, build.getResult());
				
				// polling when snapshotUUID is a valid one
				File pollingFile = Utils.getTemporaryFile();
				PollingResult pollingResult = Utils.pollProject(prj, pollingFile);
				
				assertEquals(Change.NONE, pollingResult.change);
				assertNotNull("Expecting not supported message", Utils.getMatch(pollingFile, "Polling is not supported for this configuration. Polling is supported only for build definition, repository workspace and stream configurations."));
			}
		}
		finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	private FreeStyleProject setupFreeStyleJobForWorkspace(String workspaceName) throws Exception {
		Config defaultC = Config.DEFAULT;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null), false);
		
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}
	
	private FreeStyleProject setupFreeStyleJobForBuildDefinition(String buildDefinitionId) throws Exception{
		Config defaultC = Config.DEFAULT;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()), 
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", buildDefinitionId, null, null, null), false);

		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}
	
	private FreeStyleProject setupFreeStyleJobForSnapshot(String snapshotUUID) throws Exception {
		Config defaultC = Config.DEFAULT;

		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildSnapshot", null, null, snapshotUUID, null), false);
	
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}

	private String getTestName() {
		return this.getClass().getName();
	}
}
