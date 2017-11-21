/*******************************************************************************
 * Copyright (c) 2013, 2017 IBM Corporation and others.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import hudson.util.IOUtils;
import hudson.util.Secret;
import net.sf.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.InvalidCredentialsException;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCRepositoryBrowser;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

public class RTCScmIT extends AbstractTestCase {
	private static final String CONFIGURE = "configure";
	private static final String CONFIG = "config";
	private static final String BUILD_TOOLKIT = "buildToolkit";

	private static final String SERVER_URI = "serverURI";
	private static final String OVERRIDE_GLOBAL = "overrideGlobal";
	private static final String USER_ID = "userId";
	private static final String PASSWORD = "password";
	private static final String PASSWORD_FILE = "passwordFile";
	private static final String CREDENTIALS_ID = "_.credentialsId";
	private static final String CREDENTIALS_ID_NON_FORM = "credentialsId";
	private static final String TIMEOUT = "timeout";
	private static final String AVOID_USING_TOOLKIT = "avoidUsingToolkit";

	private static final String TEST_GLOBAL_SERVER_URI = "https://localhost:9443/ccm";
	private static final String TEST_GLOBAL_CRED_ID = "1234";
	private static final String TEST_GLOBAL_TIMEOUT = "480";


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

	private String buildTool;
	private String serverURI;
	private String timeout;
	private String userId;
	private String password;
	private int timeoutInt;
	
	@Rule public JenkinsRule r = new JenkinsRule();

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
				useCreds ? TEST_CRED_ID : "", buildSource, false);
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
			assertEquals("", scm.getCredentialsId());
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
			File testPasswordFileFile = Utils.getTemporaryFile();
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

	@Test 
	public void testJobConfigRoundtripWithCredentials() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			org.jvnet.hudson.test.JenkinsRule.WebClient webClient = r.createWebClient();

			FreeStyleProject project = r.createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			DescriptorImpl descriptor = (DescriptorImpl) rtcScm.getDescriptor();
			project.setScm(rtcScm);
	
			StaplerRequest mockedReq = Mockito.mock(StaplerRequest.class);
			JSONObject mockJSON = new JSONObject();
			mockJSON.element(AVOID_USING_TOOLKIT, new JSONObject());
			mockJSON.element(CREDENTIALS_ID_NON_FORM, TEST_GLOBAL_CRED_ID);
			mockJSON.element(SERVER_URI, TEST_GLOBAL_SERVER_URI);
			mockJSON.element(TIMEOUT, TEST_GLOBAL_TIMEOUT);
			descriptor.configure(mockedReq, mockJSON);
	
	
			// Get the page to configure the project
			HtmlPage page = webClient.getPage(project, CONFIGURE);
	
			// Get the config form
			HtmlForm form = page.getFormByName(CONFIG);
	
			// Get the inputs
			HtmlCheckBoxInput overrideGlobalInput = form.getInputByName(OVERRIDE_GLOBAL);
	
			// Set the input values
			overrideGlobalInput.setChecked(false);
	
			// Submit the config form
			r.submit(form);
	
			// check submitted SCM result
			RTCScm newRtcScm = (RTCScm) project.getScm();
			assertEquals(false, newRtcScm.getOverrideGlobal());
			assertEquals(TEST_GLOBAL_SERVER_URI, newRtcScm.getServerURI());
			assertEquals(TEST_GLOBAL_TIMEOUT, String.valueOf(newRtcScm.getTimeout()));
			assertEquals(null, newRtcScm.getUserId());
			assertEquals(null, newRtcScm.getPassword());
			assertEquals(null, newRtcScm.getPasswordFile());
			assertEquals(TEST_GLOBAL_CRED_ID, newRtcScm.getCredentialsId());
			assertTrue(newRtcScm.getAvoidUsingToolkit());
		}
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
				null, null, "false", null, null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_workspace_empty(), result.renderHtml());

		// buildWorkspace - empty string
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_workspace_empty(), result.renderHtml());

		// buildWorkspace - blank string
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "   ", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_workspace_empty(), result.renderHtml());

		// invalid connect info
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null,
				"false", "test", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());

		// valid connect info - using build toolkit and invalid workspace
		result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "test", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals("Unable to find a workspace with name &quot;test&quot;", result.renderHtml());
		
		// create workspaces
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
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
					"true", "Multiple Occurrence=WS", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("More than 1 repository workspace has the name &quot;Multiple Occurrence=WS&quot;", result.renderHtml());
			
			// valid connect info - using build toolkit and warning workspace (parameterized values) 
			// only warning should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "${rtcRepositoryWorkspace}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_repository_workspace_not_validated(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and warning workspace (parameterized values) 
			// both the warnings should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", "", serverURI, timeout, userId, password, null, null,
					"true", "${rtcRepositoryWorkspace}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job() + "<br/>" + Messages.RTCScm_repository_workspace_not_validated(),
					result.renderHtml());

			// valid connect info - using build toolkit and valid workspace
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "Singly Occuring=WS", null, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, valid workspace
			// warning connect info and valid workspace - only warning should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "Singly Occuring=WS", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job(), result.renderHtml());
			
			// valid connect info - using build toolkit 
			// valid workspace
			// error path to load rule file - invalid path to load rule file 
			// load policy is not set to useLoadRules
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "Singly Occuring=WS", "useComponentLoadConfig", "/comp1/f/ws.loadrule");
			assertEquals(FormValidation.Kind.OK, result.kind);
			
			// valid connect info - using build toolkit 
			// valid workspace
			// error path to load rule file - invalid path to load rule file 
			// error should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "Singly Occuring=WS", "useLoadRules", "/comp1/f/ws.loadrule");
			assertEquals("Path to the load rule file &quot;/comp1/f/ws.loadrule&quot; does not include the component name. "
					+ "Please specify the path in the following format: &lt;component name>/&lt;remote path to the load rule file>.",
					result.renderHtml());
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null
			// valid workspace
			// error path to load rule file - build toolkit required for validation 
			// error should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", "", serverURI, timeout, userId, password, null, null,
					"true", "Singly Occuring=WS", "useLoadRules", "/comp1/f/ws.loadrule");
			assertEquals("A build toolkit is required to validate the path to the load rule file.", result.renderHtml());
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null
			// warning workspace (parameterized values) 
			// warning path to load rule file (parameterized workspace value)
			// all the warnings should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", "", serverURI, timeout, userId, password, null, null,
					"true", "${rtcRepositoryWorkspace}", "useLoadRules", "Singly Occuring=WS-comp1/Singly Occuring=WS-comp1/f/ws.loadrule");
			assertEquals("A build toolkit is required to perform builds.<br/>Cannot validate the parameterized "
					+ "value for repository workspace.<br/>Cannot validate the path to the load rule file"
					+ " with the parameterized value for repository workspace.", result.renderHtml());
			assertEquals(FormValidation.Kind.WARNING, result.kind);

			// warning connect info - avoidUsingBuildToolkit
			// valid workspace
			// warning path to load rule file (parameterized path to load rule file)
			// all the warnings should be displayed
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null, "true",
					"Singly Occuring=WS", "useLoadRules", "${pathToLoadRuleFile}");
			assertEquals("Cannot validate the parameterized value for the path to the load rule file.", result.renderHtml());
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			
			// valid connect info - using build toolkit 
			// valid workspace
			// valid path to load rule file
			// validation success
			result = descriptor.doValidateBuildWorkspaceConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "Singly Occuring=WS", "useLoadRules", "Singly Occuring=WS-comp1/Singly Occuring=WS-comp1/f/ws.loadrule");
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			assertEquals(FormValidation.Kind.OK, result.kind);
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
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String buildDefinitionName = getBuildDefinitionUniqueName();
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
				null, null, "false", null, null, null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_stream_empty(), result.renderHtml());

		// buildStream - empty string
		result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", null, "", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_stream_empty(), result.renderHtml());

		// buildStream - blank string
		result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", null, "   ", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_stream_empty(), result.renderHtml());
		
		// invalid connect info
		result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null,
				"false", null, "testStream", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());
		
		// create project area and build stream
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String projectAreaName = getUniqueName("testDoValidateBuildStreamConfiguration");
		String streamName = getUniqueName("testDoValidateBuildStreamConfigurationStream");
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
					"false", "testProject", streamName, null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());
			
			// valid connect info - avoidUsingBuildToolkiit and build toolkit is provided and non-null invalid process area, valid stream
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", "testProject", streamName, null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, non-null invalid process area, valid stream
			// warning connect info and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "testProject", streamName, null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// valid connect info - using build toolkit and non-null invalid process area, warning stream (parameterized value)
			// only error message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "testProject", "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());

			// valid connect info - avoidUsingBuildToolkiit and build toolkit is provided and non-null invalid process area, warning stream (parameterized value)
			// only error message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", "testProject", "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;testProject&quot; cannot be found.", result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, non-null invalid process area, warning stream (parameterized value)
			// warning connect info, warning stream and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", "testProject", "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());

			// valid connect info - using build tool kit, valid process area, invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", projectAreaName, "testStream", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the project area &quot;" + projectAreaName + "&quot;.", result.renderHtml());
			
			// valid connect info - avoidUsingBuildToolkit and buildTool is provided, valid process area, invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "testStream", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the project area &quot;" + projectAreaName + "&quot;.", result.renderHtml());
			
			// valid connect info - using build tool kit, valid process area(null), invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, "testStream", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the repository.", result.renderHtml());
			
			// valid connect info - avoidUsingBuildToolkit and buildTool is provided, valid process area(empty), invalid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", "   ", "testStream", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);			
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the repository.", result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null, valid process area, invalid stream value
			// warning connect info and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "testStream", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// warning connect info avoidUsingBuildToolkit and buildTool is invalid, valid(null) process area, invalid stream value
			// warning connect info and error stream - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", "test", serverURI, timeout, userId, password, null, null,
					"true", null, "testStream", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;testStream&quot; cannot be found in the repository.", result.renderHtml());
			
			// valid connectinfo - using build toolkit, valid process area, warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", projectAreaName, "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());
				
			// valid connectinfo - avoidUsingBuildToolkit and buildTool is provided, valid process area, warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());			

			// valid connectinfo - using build toolkit, valid process area (null), warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());

			// valid connectinfo - avoidUsingBuildToolkit and a buildTool is provided, valid process area (null), warning stream value (parameterized value)
			// only warning message should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", null, "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_stream_not_validated(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool, valid process area, warning stream value (parameterized value)
			// warning connect info, warning stream value and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool, valid (null) process area, warning stream value (parameterized value)
			// warning connect info, warning stream value and validation success - both the warning messages should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", null, "${rtcStream}", null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job() + "<br/>" + Messages.RTCScm_stream_not_validated(), result.renderHtml());
			
			// valid connectinfo - using build toolkit, valid process area, valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", projectAreaName, streamName, null, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo - avoidUsingBuildToolkit and buildTool is provided, valid process area, valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, streamName, null, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo - using build toolkit, valid process area (null), valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, streamName, null, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo - avoidUsingBuildToolkit and a buildTool is provided, valid process area (null), valid stream value
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"true", null, streamName, null, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool, valid process area, valid stream value
			// warning connect info and error project area - only error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", projectAreaName, streamName, null, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_process_area(), result.renderHtml());
			
			// warning connect info - avoidUsingBuildToolkit and buildTool, valid (null) process area, valid stream value
			// warning connect info and validation success - only warning should be displayed 
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null,
					"true", null, streamName, null, null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_tool_needed_for_job(), result.renderHtml());
			
			// valid connectinfo - avoidUsingBuildToolkit and a buildTool is provided
			// valid process area (null)
			// valid stream value
			// error path to load rule file - invalid path
			// load policy not set to load rules
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, streamName, "useComponentLoadConfig", "/comp1/f/ws.loadrule");
			assertEquals(FormValidation.Kind.OK, result.kind);
			
			// valid connectinfo - avoidUsingBuildToolkit and a buildTool is provided
			// valid process area (null)
			// valid stream value
			// error path to load rule file - invalid path
			// error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, streamName, "useLoadRules", "/comp1/f/ws.loadrule");
			assertEquals("Path to the load rule file &quot;/comp1/f/ws.loadrule&quot; does not include the component name. "
					+ "Please specify the path in the following format: &lt;component name>/&lt;remote path to the load rule file>.",
					result.renderHtml());
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null
			// valid process area(null)
			// valida stream
			// error path to load rule file - build toolkit required for validation 
			// error should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", "", serverURI, timeout, userId, password, null, null,
					"true", null, streamName, "useLoadRules", "/comp1/f/ws.loadrule");
			assertEquals("A build toolkit is required to validate the path to the load rule file.", result.renderHtml());
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			
			// warning connect info - avoidUsingBuildToolkit and buildTool is null
			// valid process area(null)
			// warning stream (parameterized values) 
			// warning path to load rule file (parameterized stream value)
			// all the warnings should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", "", serverURI, timeout, userId, password, null, null,
					"true", null, "${rtcStream}", "useLoadRules", "Singly Occuring=WS-comp1/Singly Occuring=WS-comp1/f/ws.loadrule");
			assertEquals("A build toolkit is required to perform builds.<br/>Cannot validate the parameterized "
					+ "value for stream.<br/>Cannot validate the path to the load rule file"
					+ " with the parameterized value for stream.", result.renderHtml());
			assertEquals(FormValidation.Kind.WARNING, result.kind);

			// warning connect info - avoidUsingBuildToolkit
			// valid process area(null)
			// valid stream
			// warning path to load rule file (parameterized path to load rule file)
			// all the warnings should be displayed
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null, "true",
					null, streamName, "useLoadRules", "${pathToLoadRuleFile}");
			assertEquals("Cannot validate the parameterized value for the path to the load rule file.", result.renderHtml());
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			
			// valid connect info - using build toolkit 
			// valid process area(null)
			// valid stream
			// valid path to load rule file
			// validation success
			String componentName = setupArtifacts.get(Utils.ARTIFACT_COMPONENT_NAME);
			result = descriptor.doValidateBuildStreamConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, streamName, "useLoadRules", componentName + "/" + componentName + "/f/ws.loadrule");
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			assertEquals(FormValidation.Kind.OK, result.kind);

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
				null, null, "false", "", "", "", "", null, null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_snapshot_empty(), result.renderHtml());

		// buildSnapshot - empty string
		result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "", "", "", "", "", null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_snapshot_empty(), result.renderHtml());

		// buildSnapshot - blank string
		result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
				"false", "", "", "", "", "   ", null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCScm_build_snapshot_empty(), result.renderHtml());

		// invalid connect info
		result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, null, password, null, null, "false",
				"", "", "", "", "testStream", null);
		assertEquals(FormValidation.Kind.ERROR, result.kind);
		assertEquals(Messages.RTCLoginInfo_missing_userid(), result.renderHtml());

		// create project area, build stream, and a snapshot
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String projectAreaName = getUniqueName("testDoValidateBuildSnapshotConfiguration");
		String streamName = getUniqueName("testDoValidateBuildSnapshotConfigurationStream");
		String workspaceName = getUniqueName("testDoValidateBuildSnapshotConfigurationWorkspace");
		String snapshotName = getUniqueName("testDoValidateBuildSnapshotConfigurationSnapshot");
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
					"", "", "", "", snapshotName, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_snapshot(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool is null, warning snapshot value (job
			// property)
			// warning connect info and cannot validate snapshot error - only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null, "true",
					"", "", "", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_snapshot(), result.renderHtml());

			// warning connect info - avoidUsingBuildToolkit and buildTool is null, invalid snapshot value
			// warning connect info and error snapshot - only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", null, serverURI, timeout, userId, password, null, null, "true",
					"", "", "", "", "testSnapshot", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals(Messages.RTCScm_build_toolkit_required_to_validate_snapshot(), result.renderHtml());

			// valid connect info, invalid snapshot value
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "", "", "", "", "testSnapshot", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A snapshot with name &quot;testSnapshot&quot; cannot be found in the repository.", result.renderHtml());

			// valid connectinfo, valid snapshot value
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "", "", "", "", snapshotName, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to none
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "none", "", "", "", snapshotName, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to workspace
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName, wsSnapshotName, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to stream without specifying project
			// area, but having a workspace name
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName, "ignoreWorkspaceName", snapshotName, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());

			// valid connectinfo, valid snapshot value, with snapshotOwnerType set to stream with specifying project
			// area
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName, "", snapshotName, null);
			assertEquals(FormValidation.Kind.OK, result.kind);
			assertEquals(Messages.RTCScm_validation_success(), result.renderHtml());
			
			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to workspace
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName + "invalid", wsSnapshotName, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("Unable to find a workspace with name &quot;" + workspaceName + "invalid&quot;", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream without specifying
			// project area, but having a workspace name
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName + "invalid", "ignoreWorkspaceName", snapshotName, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the repository.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName + "invalid", "", snapshotName, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the project area &quot;" + projectAreaName
					+ "&quot;.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName + "invalid", streamName + "invalid", "", snapshotName, null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;" + projectAreaName + "invalid&quot; cannot be found.", result.renderHtml());
			
			// valid connectinfo, warning snapshot value(job property)
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", null, "", "", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to none
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "none", "", "", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to workspace
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName, "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());
			
			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to workspace no workspaceName
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());


			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to stream without
			// specifying project area, but having a workspace name
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName, "ignoreWorkspaceName", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());
			

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to stream without
			// specifying project area and stream name
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", "", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), valid snapshotOwnerType set to stream with specifying project
			// area
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName, "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_build_snapshot_not_validated(), result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), invalid snapshotOwnerType set to workspace
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "workspace", "", "", workspaceName + "invalid", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("Unable to find a workspace with name &quot;" + workspaceName + "invalid&quot;", result.renderHtml());

			// valid connectinfo, warning snapshot value(job property), invalid snapshotOwnerType set to stream without
			// specifying project area, but having a workspace name
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", "", streamName + "invalid", "ignoreWorkspaceName", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the repository.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName + "invalid", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A stream with name &quot;" + streamName + "invalid&quot; cannot be found in the project area &quot;" + projectAreaName
					+ "&quot;.", result.renderHtml());

			// valid connectinfo, valid snapshot value, invalid snapshotOwnerType set to stream with specifying project
			// area
			// only error should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName + "invalid", streamName + "invalid", "", "${rtcBuildSnapshot}", null);
			assertEquals(FormValidation.Kind.ERROR, result.kind);
			assertEquals("A project area with name &quot;" + projectAreaName + "invalid&quot; cannot be found.", result.renderHtml());
			
			// valid connectinfo, valid snapshot value, valid snapshotOwnerType set to stream with specifying project
			// area
			// warning load rules - can't be validated 
			// only warning should be displayed
			result = descriptor.doValidateBuildSnapshotConfiguration(project, "true", buildTool, serverURI, timeout, userId, password, null, null,
					"false", "stream", projectAreaName, streamName, "", snapshotName, "comp1/f/ws.loadrule");
			assertEquals(FormValidation.Kind.WARNING, result.kind);
			assertEquals(Messages.RTCScm_path_to_load_rule_not_validated_snapshot(), result.renderHtml());

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
	 * Verify that build definition item id and name is found in the changelogset 
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
		String buildDefinitionItemId = setupArtifacts.get(Utils.ARTIFACT_BUILDDEFINITION_ITEM_ID);

		try {
			// Setup a build with build definition configuration
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, buildDefinitionId);

			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			
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
	 * Verify that Repository workspace item id and name are found in 
	 * Changelogset
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test 
	public void repositoryWorkspaceNameItemIdPairFoundInChangeLogSet() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String workspaceUUID = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
			
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
	 * Verify that build definition name nad Itemid is found in the 
	 * change log file
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
		String buildDefinitionItemId = setupArtifacts.get(Utils.ARTIFACT_BUILDDEFINITION_ITEM_ID);

		try {
			// Setup a build with build definition configuration
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, buildDefinitionId);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
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
	 * Verify that Repository workspace name and item id is found in the 
	 * changelog file
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
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getSnapshotUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC,
							streamName);
		String workspaceItemId = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
			
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
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = Utils.setupBuildDefinition(testingFacade, defaultC, 
					buildDefinitionId, workspaceName, componentName);		
		try {
			// positive case when there is a valid build definition Id
			{
				FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, buildDefinitionId);
				// Run a build
				FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				
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
				FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, null);
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
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC,
							streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			{ // positive case - when workspaceName is not null
				FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
				
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
				FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, null);
				
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
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();

		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(loginInfo, workspaceName, snapshotName, componentName, testingFacade);
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		try {
			{ // valid snapshot UUID - checkout and polling
				FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, snapshotUUID);
				FreeStyleBuild build = Utils.runBuild(prj, null);
	
				// Verify the build status
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
				FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, null);
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

	/**
	 * Use this test as a placeholder for all simple validations related to build definition configuration.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testBuildDefinitionConfiguration_misc() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = Utils.setupBuildDefinition(testingFacade, defaultC, buildDefinitionId, workspaceName, componentName);
		try {
			// Create a basic project configuration
			// Individual validation steps can then customize the RTCScm instance and update it in the project instance
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, buildDefinitionId);
			// validate support for custom snapshot name
			validateCustomSnapshotName_buildDef(prj, setupArtifacts, buildDefinitionId);

		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}	

	/**
	 * Validate invocation of dynamic load rules for build definition configuration.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testBuildDefinitionConfiguration_dynamicLoadRules() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = Utils.setupBuildDefinition_toTestLoadPolicy(testingFacade, defaultC, buildDefinitionId, workspaceName, componentName);
		try {
			// Create a basic project configuration
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, buildDefinitionId);
			
			// set useDynamicLoadRules to true and validate that the workspace is loaded according to the dynamic load
			// rules
			RTCScm rtcScm = (RTCScm)prj.getScm();
			File tempDir = new File(System.getProperty("java.io.tmpdir"));
			File buildTestDir = new File(tempDir, "HJPluginTests");
			File loadDir = new File(buildTestDir, getFileUniqueName());
			loadDir.mkdirs();
			loadDir.deleteOnExit();
			Assert.assertTrue(loadDir.exists());
			BuildType buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(true);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID, action.getBuildResultUUID());

			String[] children = null;
			if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
				boolean isPre603BuildToolkit = Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit"));
				String buildToolkitVersionString = "FATAL: RTC : checkout failure: Please check the version of the build toolkit. Build toolkit version 6.0.3 or above is required to load components using load rules.";

				if (!isPre603BuildToolkit || (isPre603BuildToolkit && Utils.getMatch(build.getLogFile(), buildToolkitVersionString) == null)) {
					Assert.fail("Failure not expected");
				}
			} else {
				children = loadDir.list();
				Assert.assertEquals(2, children.length);
				Assert.assertTrue(new File(loadDir, "f/a.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/b.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/c.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/d.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/n.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/tree").exists());
				Assert.assertTrue(new File(loadDir, "f/tree/e.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/newTree").exists());
				Assert.assertTrue(new File(loadDir, "f/newTree/newFile.txt").exists());
				Assert.assertFalse(new File(loadDir, "f2").exists());
			}
			
			ParametersDefinitionProperty useExtensionProperty = new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] { new StringParameterDefinition(
					RTCJobProperties.USE_DYNAMIC_LOAD_RULE, " ") }));
			
			// set useDynamicLoadRules to false, set useExtension parameter to true and validate that the workspace is
			// loaded according to dynamic load rules
			// set useOldLoadRuleGenerator property to true so that the deprecated method getComponentLoadRules()
			// returns load rules
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(false);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
			
			prj.addProperty(useExtensionProperty);			
			List<ParametersAction> pAction = new ArrayList<ParametersAction>();
			pAction.addAll(Utils.getPactionsWithEmptyBuildResultUUID());
			pAction.add(new ParametersAction(new StringParameterValue(RTCJobProperties.USE_DYNAMIC_LOAD_RULE, "true")));
			pAction.add(new ParametersAction(new StringParameterValue("useOldDynamicLoadRuleGenerator", "true")));
			build = Utils.runBuild(prj, pAction);
			action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID + "1", action.getBuildResultUUID());

			if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
				Assert.fail("Failure not expected");
			} else {
				children = loadDir.list();
				Assert.assertEquals(3, children.length);
				Assert.assertTrue(new File(loadDir, "f/a.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/b.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/c.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/d.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/n.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/tree").exists());
				Assert.assertTrue(new File(loadDir, "f/tree/e.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/newTree").exists());
				Assert.assertTrue(new File(loadDir, "f/newTree/newFile.txt").exists());
				Assert.assertFalse(new File(loadDir, "f2").exists());
				Assert.assertTrue(new File(loadDir, componentName + "c2").exists());
				Assert.assertTrue(new File(loadDir, componentName + "c2/f").exists());
				Assert.assertTrue(new File(loadDir, componentName + "c2/f/a.txt").exists());
				Assert.assertTrue(new File(loadDir, componentName + "c2/f/b.txt").exists());
				Assert.assertTrue(new File(loadDir, componentName + "c2/f/c.txt").exists());
			}
			prj.removeProperty(useExtensionProperty);
			
			// set useDynamicLoadRules to true, set useExtension parameter to false and validate that the workspace is
			// loaded according to dynamic load rules
			// set useOldLoadRuleGenerator property to true so that the deprecated method getComponentLoadRules()
			// returns load rules
			// set excludeComponents property so that the deprecated method getExcludeComponents() returns components to
			// exclude
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(true);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			prj.addProperty(useExtensionProperty);

			pAction = new ArrayList<ParametersAction>();
			pAction.addAll(Utils.getPactionsWithEmptyBuildResultUUID());
			pAction.add(new ParametersAction(new StringParameterValue(RTCJobProperties.USE_DYNAMIC_LOAD_RULE, "false")));
			pAction.add(new ParametersAction(new StringParameterValue("useOldDynamicLoadRuleGenerator", "true")));
			pAction.add(new ParametersAction(new StringParameterValue("excludeComponents", "true")));
			build = Utils.runBuild(prj, pAction);
			action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID + "2", action.getBuildResultUUID());

			if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
				Assert.fail("Failure not expected");
			} else {
				children = loadDir.list();
				Assert.assertEquals(2, children.length);
				Assert.assertTrue(new File(loadDir, "f/a.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/b.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/c.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/d.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/n.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/tree").exists());
				Assert.assertTrue(new File(loadDir, "f/tree/e.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/newTree").exists());
				Assert.assertTrue(new File(loadDir, "f/newTree/newFile.txt").exists());
				Assert.assertFalse(new File(loadDir, "f2").exists());
			}
			prj.removeProperty(useExtensionProperty);
			
			// set useDynamicLoadRules to false and validate that the whole workspace is loaded
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(false);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID + "3", action.getBuildResultUUID());

			children = loadDir.list();
			Assert.assertEquals(4, children.length); 
			Assert.assertTrue(new File(loadDir, componentName).exists());
			Assert.assertTrue(new File(loadDir, "newTree2").exists());
			Assert.assertTrue(new File(loadDir, componentName + "c2").exists());
			File newTree2Dir = new File(loadDir, "newTree2");
			children = newTree2Dir.list();
			Assert.assertEquals(257, children.length);
			File rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/d.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/n.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/tree").exists());
			Assert.assertTrue(new File(rootDir, "f/tree/e.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/newTree").exists());
			Assert.assertTrue(new File(rootDir, "f/newTree/newFile.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
			rootDir = new File(loadDir, componentName + "c2");
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			
			// do not set useDynamicLoadRules and validate that the whole workspace is loaded
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID + "4", action.getBuildResultUUID());

			children = loadDir.list();
			Assert.assertEquals(4, children.length);
			Assert.assertTrue(new File(loadDir, componentName).exists());
			Assert.assertTrue(new File(loadDir, componentName + "c2").exists());
			Assert.assertTrue(new File(loadDir, "newTree2").exists());
			newTree2Dir = new File(loadDir, "newTree2");
			children = newTree2Dir.list();
			Assert.assertEquals(257, children.length);
			rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/d.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/n.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/tree").exists());
			Assert.assertTrue(new File(rootDir, "f/tree/e.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/newTree").exists());
			Assert.assertTrue(new File(rootDir, "f/newTree/newFile.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
			rootDir = new File(loadDir, componentName + "c2");
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			
			// set useDynamicLoadRules to false, loadPolicy to useDynamicLoadrules and validate that the whole workspace
			// is loaded
			// loadPolicy field is not applicable for build definition configuration
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(false);
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID + "5", action.getBuildResultUUID());

			children = loadDir.list();
			Assert.assertEquals(4, children.length); 
			Assert.assertTrue(new File(loadDir, componentName).exists());
			Assert.assertTrue(new File(loadDir, componentName + "c2").exists());
			Assert.assertTrue(new File(loadDir, "newTree2").exists());
			newTree2Dir = new File(loadDir, "newTree2");
			children = newTree2Dir.list();
			Assert.assertEquals(257, children.length);
			rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/d.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/n.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/tree").exists());
			Assert.assertTrue(new File(rootDir, "f/tree/e.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/newTree").exists());
			Assert.assertTrue(new File(rootDir, "f/newTree/newFile.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
			rootDir = new File(loadDir, componentName + "c2");
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Validate invocation of dynamic load rules for repository workspace configuration.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testRepositoryWorkspaceConfiguration_dynamicLoadRules() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream_toTestLoadPolicy(testingFacade, defaultC, streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		String componentName = setupArtifacts.get(Utils.ARTIFACT_COMPONENT_NAME);

		try {
			// Create a basic project configuration
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
			
			// set loadPolicy to useDynamicLoadRules and validate that the workspace is loaded according to the dynamic
			// load rules
			RTCScm rtcScm = (RTCScm)prj.getScm();
			File tempDir = new File(System.getProperty("java.io.tmpdir"));
			File buildTestDir = new File(tempDir, "HJPluginTests");
			File loadDir = new File(buildTestDir, getFileUniqueName());
			loadDir.mkdirs();
			loadDir.deleteOnExit();
			Assert.assertTrue(loadDir.exists());
			BuildType buildType = rtcScm.getBuildType();
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES + " "); // validate that white space is trimmed
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			buildType.setAcceptBeforeLoad(true);
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
			
			String[] children = null;
			FreeStyleBuild build = Utils.runBuild(prj, null);
			if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
				boolean isPre603BuildToolkit = Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit"));
				String buildToolkitVersionString = "FATAL: RTC : checkout failure: Please check the version of the build toolkit. Build toolkit version 6.0.3 or above is required to load components using load rules.";

				if (!isPre603BuildToolkit || (isPre603BuildToolkit && Utils.getMatch(build.getLogFile(), buildToolkitVersionString) == null)) {
					Assert.fail("Failure not expected");
				}
			} else {
				children = loadDir.list();
				Assert.assertEquals(2, children.length);
				Assert.assertTrue(new File(loadDir, "f/a.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/b.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/c.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/d.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/n.txt").exists());
				Assert.assertTrue(new File(loadDir, "f/ws.loadrule").exists());
				Assert.assertTrue(new File(loadDir, "f/tree").exists());
				Assert.assertTrue(new File(loadDir, "f/tree/e.txt").exists());
				Assert.assertFalse(new File(loadDir, "f2").exists());
			}
			
			// set useDynamicLoadRules to true, loadPolicy to useComponentLoadConfig and validate that the whole workspace
			// is loaded
			// useDynamicLoadRules is not applicable for non-buildDefinition configuration
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(true);
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			Utils.runBuild(prj, null);

			children = loadDir.list();
			Assert.assertEquals(2, children.length); 
			Assert.assertTrue(new File(loadDir, componentName).exists());
			File rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/d.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/n.txt").exists());			
			Assert.assertTrue(new File(rootDir, "f/ws.loadrule").exists());
			Assert.assertTrue(new File(rootDir, "f/tree").exists());
			Assert.assertTrue(new File(rootDir, "f/tree/e.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
			
			// set loadPolicy to useLoadRules and validate that the whole workspace
			// is loaded, as no load rule file is specified
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(false);
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_LOAD_RULES);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			Utils.runBuild(prj, null);

			children = loadDir.list();
			Assert.assertEquals(2, children.length); 
			Assert.assertTrue(new File(loadDir, componentName).exists());
			rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/d.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/n.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/ws.loadrule").exists());
			Assert.assertTrue(new File(rootDir, "f/tree").exists());
			Assert.assertTrue(new File(rootDir, "f/tree/e.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Validate that empty values are trimmed for loadPolicy and componentLoadConfig
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testEmptyLoadPolicyAndComponentLoadConfig() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream_toTestLoadPolicy(testingFacade, defaultC, streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		String componentName = setupArtifacts.get(Utils.ARTIFACT_COMPONENT_NAME);

		try {
			// Create a basic project configuration
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
			
			// set loadPolicy to useDynamicLoadRules and validate that the workspace is loaded according to the dynamic
			// load rules
			RTCScm rtcScm = (RTCScm)prj.getScm();
			File tempDir = new File(System.getProperty("java.io.tmpdir"));
			File buildTestDir = new File(tempDir, "HJPluginTests");
			File loadDir = new File(buildTestDir, getFileUniqueName());
			loadDir.mkdirs();
			loadDir.deleteOnExit();
			Assert.assertTrue(loadDir.exists());
			BuildType buildType = rtcScm.getBuildType();
			buildType.setLoadPolicy("   "); // validate that empty values are considered as null
			buildType.setComponentLoadConfig("   "); // validate that empty values are considered as null 
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			buildType.setAcceptBeforeLoad(true);
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
			
			String[] children = null;
			Utils.runBuild(prj, null);
			children = loadDir.list();
			Assert.assertEquals(2, children.length); 
			Assert.assertTrue(new File(loadDir, componentName).exists());
			File rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/b.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/c.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/d.txt").exists());
			Assert.assertTrue(new File(rootDir, "f/n.txt").exists());			
			Assert.assertTrue(new File(rootDir, "f/ws.loadrule").exists());
			Assert.assertTrue(new File(rootDir, "f/tree").exists());
			Assert.assertTrue(new File(rootDir, "f/tree/e.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Validate invocation of dynamic load rules for snapshot configuration.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testSnapshotConfiguration_dynamicLoadRules() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(loginInfo, workspaceName, snapshotName, componentName, testingFacade);
		try {
			String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
			// setup a free style project and start a build
			// verify that the temporary workspace created during the build is deleted
			FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, snapshotUUID);
			// set loadPolicy to useDynamicLoadRules and validate that the workspace is loaded according to the dynamic
			// load rules
			RTCScm rtcScm = (RTCScm)prj.getScm();
			File tempDir = new File(System.getProperty("java.io.tmpdir"));
			File buildTestDir = new File(tempDir, "HJPluginTests");
			File loadDir = new File(buildTestDir, getFileUniqueName());
			loadDir.mkdirs();
			loadDir.deleteOnExit();
			Assert.assertTrue(loadDir.exists());
			BuildType buildType = rtcScm.getBuildType();
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			buildType.setClearLoadDirectory(true);
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
			
			String[] children = null;
			FreeStyleBuild build = Utils.runBuild(prj, null);
			if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
				boolean isPre603BuildToolkit = Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit"));
				String buildToolkitVersionString = "FATAL: RTC : checkout failure: Please check the version of the build toolkit. Build toolkit version 6.0.3 or above is required to load components using load rules.";

				if (!isPre603BuildToolkit || (isPre603BuildToolkit && Utils.getMatch(build.getLogFile(), buildToolkitVersionString) == null)) {
					Assert.fail("Failure not expected");
				}
			} else {
				children = loadDir.list();
				Assert.assertEquals(2, children.length);
				Assert.assertTrue(new File(loadDir, "f/a.txt").exists());
				Assert.assertFalse(new File(loadDir, "f2").exists());
			}
			
			// set useDynamicLoadRules to true, loadPolicy to useComponentLoadConfig and validate that the whole workspace
			// is loaded
			// useDynamicLoadRules is not applicable for non-buildDefinition configuration
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(true);
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			Utils.runBuild(prj, null);

			children = loadDir.list();
			Assert.assertEquals(2, children.length);
			Assert.assertTrue(new File(loadDir, componentName).exists());
			File rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
			
			// set loadPolicy to useLoadRules and validate that the whole workspace
			// is loaded, as no load rule file is specified
			rtcScm = (RTCScm)prj.getScm();
			buildType = rtcScm.getBuildType();
			buildType.setUseDynamicLoadRules(false);
			buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_LOAD_RULES);
			buildType.setLoadDirectory(loadDir.getAbsolutePath());
			prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

			Utils.runBuild(prj, null);

			children = loadDir.list();
			Assert.assertEquals(2, children.length);
			Assert.assertTrue(new File(loadDir, componentName).exists());
			rootDir = new File(loadDir, componentName);
			Assert.assertTrue(new File(rootDir, "f").exists());
			Assert.assertTrue(new File(rootDir, "f/a.txt").exists());
			Assert.assertTrue(new File(rootDir, "f2").exists());
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * Validate that the build fails with an appropriate error message when invalid values are provided for loadPolicy
	 * and componentLoadConfig attributes.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testInvalidLoadPolicyAndComponentLoadConfigValues() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream_toTestLoadPolicy(testingFacade, defaultC, streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);

		// Create a basic project configuration
		FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);

		// set loadPolicy to useDynamicLoadRules and validate that the workspace is loaded according to the dynamic
		// load rules
		RTCScm rtcScm = (RTCScm)prj.getScm();
		BuildType buildType = rtcScm.getBuildType();
		buildType.setLoadPolicy("useLoadRules1");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		FreeStyleBuild build = Utils.runBuild(prj, null);
		
		String invalidLoadPolicyString = "ERROR: RTC : checkout failure: The value for loadPolicy attribute must be one of \"" + RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG
				+ "\", \"" + RTCScm.LOAD_POLICY_USE_LOAD_RULES + "\", or \"" + RTCScm.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES
				+ "\". The specified value is \"useLoadRules1\".";

		if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
			assertNotNull(Utils.getMatch(build.getLogFile(),invalidLoadPolicyString));
		} else {
			Assert.fail("Failure expected");
		}

		buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG);
		buildType.setComponentLoadConfig("loadAllComponents1");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
			assertNotNull(Utils.getMatch(build.getLogFile(), "ERROR: " + Messages.RTCScm_checkout_failure4(Messages.RTCScm_invalid_value_for_componentLoadConfig(
					RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, "loadAllComponents1"))));
		} else {
			Assert.fail("Failure expected");
		}
		
		buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_LOAD_RULES);
		buildType.setComponentLoadConfig("loadAllComponents1");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
			Assert.fail("Failure not expected");
		}
		
		buildType.setLoadPolicy(RTCScm.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES);
		buildType.setComponentLoadConfig("loadAllComponents1");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
			boolean isPre603BuildToolkit = Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit"));
			String buildToolkitVersionString = "FATAL: RTC : checkout failure: Please check the version of the build toolkit. Build toolkit version 6.0.3 or above is required to load components using load rules.";

			if (!isPre603BuildToolkit || (isPre603BuildToolkit && Utils.getMatch(build.getLogFile(), buildToolkitVersionString) == null)) {
				Assert.fail("Failure not expected");
			}
		}
		
		buildType.setLoadPolicy("   ");
		buildType.setComponentLoadConfig("   ");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		if (build.getResult().isWorseOrEqualTo(Result.FAILURE)) {
			Assert.fail("Failure not expected");
		}
	}

	/**
	 * Use this test as a placeholder for all simple validations related to repository workspace configuration.
	 * 
	 * @throws Exception
	 */
	@WithTimeout(1200)
	@Test
	public void testRepositoryWorkspaceConfiguration_misc() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);

		try {
			// Create a basic project configuration
			// Individual validation steps can then customize the RTCScm instance and update it in the project instance
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
			// validate support for custom snapshot name
			validateCustomSnapshotName_repositoryWS(prj);

		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	
	/**
	 * Test whether temporary workspace created during build from snapshot 
	 * is deleted after a successful build.
	 */
	@WithTimeout(600)
	@Test
	public void testTemporaryWorkspaceDeletedForSnapshotSuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(loginInfo, workspaceName, snapshotName, componentName, testingFacade);
		try {
			String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
			// setup a free style project and start a build
			// verify that the temporary workspace created during the build is deleted
			FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, snapshotUUID);
			FreeStyleBuild build = Utils.runBuild(prj, null);
			
			// Ensure build is successful
			assertTrue(build.getLog().toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Ensure non null snapshot UUID 
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertEquals(snapshotUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID));
			
			// Make a testing facade call to ensure that the temporary workspace does not exist.
			String [] workspaceItemIds = Utils.findTemporaryWorkspaces();
			assertEquals(0, workspaceItemIds.length);	
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * Test whether temporary workspace created during build from snapshot is deleted
	 * even if load fails 
	 *  
	 * @throws Exception
	 */
	@WithTimeout(600)
	@Test
	public void testTemporaryWorkspaceDeletedForSnapshotLoadFailure() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(loginInfo, workspaceName, snapshotName, componentName, testingFacade);
		try {
			String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
			// setup a free style project with an invalid load path and start a build
			// verify that the temporary workspace created during the build is deleted
			String loadDirectory = Utils.getInvalidLoadPath();
			FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, snapshotUUID, loadDirectory);
			FreeStyleBuild build = Utils.runBuild(prj, null);
			
			// Ensure build is successful
			assertTrue(build.getLog().toString(), build.getResult().isWorseOrEqualTo(Result.FAILURE));
			
			// Ensure non null snapshot UUID 
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			assertEquals(snapshotUUID, action.getBuildProperties().get(Utils.TEAM_SCM_SNAPSHOTUUID));
			
			// Make a testing facade call to ensure that the temporary workspace does not exist.
			String [] workspaceItemIds = Utils.findTemporaryWorkspaces();
			assertEquals(0, workspaceItemIds.length);	
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Check for 
	 * team_scm_snapshotUUID
	 * repositoryAddress
	 * team_scm_changesAccepted should be null forever
	 * @throws Exception
	 */
	@Test
	public void testBuildPropertiesInSnapshotBuild() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String snapshotName = getSnapshotUniqueName();
		String componentName = getComponentUniqueName();
		
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(loginInfo, workspaceName, snapshotName, componentName, testingFacade);
		String snapshotUUID = setupArtifacts.get(Utils.ARTIFACT_BASELINESET_ITEM_ID);
		try {
		  
			FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(r, snapshotUUID);
			FreeStyleBuild build = Utils.runBuild(prj, null);

			// Verify the build status
			assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
			
			// Verify the build properties from RTCBuildResultAction
			RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
			Map<String, String> buildProperties = action.getBuildProperties();
			
			// Check for known properties
			assertEquals(snapshotUUID, buildProperties.get(Utils.TEAM_SCM_SNAPSHOTUUID));
			assertEquals(loginInfo.getServerUri(), buildProperties.get(Utils.REPOSITORY_ADDRESS));	
			
			// Doesn't make sense for a snapshot build
			assertNull(buildProperties.get(Utils.TEAM_SCM_ACCEPT_PHASE_OVER));
			assertNull(buildProperties.get(Utils.TEAM_SCM_CHANGES_ACCEPTED));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * Check for 
	 * team_scm_snapshotUUID, 
	 * repositoryAddress, 
	 * team_scm_acceptPhaseOver,
	 * team_scm_changes Accepted may be non zero from the very first build
	 * team_scm_workspaceUUID - The UUID of the repository workspace.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testBuildPropertiesInWorkspaceBuild() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC,
							streamName);
		String workspaceName = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_NAME);
		
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(r, workspaceName);
			
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			
			// Get Build Properties out of RTCBuildResultAction
			List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
			assertTrue(actions.size() == 1);
			
			Map<String, String> buildProperties = actions.get(0).getBuildProperties();
			assertEquals(defaultC.getLoginInfo().getServerUri(), 
						buildProperties.get(Utils.REPOSITORY_ADDRESS));
			assertNotNull(buildProperties.get(Utils.TEAM_SCM_SNAPSHOTUUID));
			assertNotNull(buildProperties.get(Utils.TEAM_SCM_ACCEPT_PHASE_OVER));
			
			// Check whether the workspace UUID is available in the build properties
			// Get the workspace item id from setupArtifacts and compare
			String workspaceItemIdFromArtifacts = hudson.Util.fixEmptyAndTrim(setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID));
			String workspaceItemIdFromBuildAction = hudson.Util.fixEmptyAndTrim(buildProperties.get(Utils.TEAM_SCM_WORKSPACE_UUID));
			// Ensure that it is not null or empty and a valid UUID
			assertTrue(workspaceItemIdFromBuildAction != null && workspaceItemIdFromArtifacts != null);
			assertEquals("WorksapceItemId From Artifacts" + workspaceItemIdFromArtifacts
					+ "WorkspaceItemId From BA " + workspaceItemIdFromBuildAction,
					workspaceItemIdFromArtifacts, workspaceItemIdFromBuildAction);
			
			// Changes would have been dropped since the stream doesn't have some 
			// change sets and hence we see that the accepted changesets number is 
			// greater than zero.
			assertTrue(Integer.parseInt(buildProperties.get(Utils.TEAM_SCM_CHANGES_ACCEPTED))>0);
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	/**
	 * Test {@link RTCBuildResultAction}'s properties exposed through API for a build
	 * 
	 */
	@Test
	public void testRTCBuildResultActionPropertiesInBuildAPI() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = Utils.setupBuildDefinition(testingFacade, defaultC, 
					buildDefinitionId, workspaceName, componentName);
		
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForBuildDefinition(r, buildDefinitionId);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.dumpLogFile(build, "log" , "testRTCBuildResultActionPropertiesInBuildAPI", "file");		

			// Verify
			Utils.verifyRTCScmInBuild(build, true);
			RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID, action.getBuildResultUUID());
			
			// Construct the build API URL. Use HTTPURLConnection to get the XML data
			URL url = new URL(r.getURL() + "/" + build.getUrl() + "api/xml");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.connect();
			
			assertEquals(HttpURLConnection.HTTP_OK, conn.getResponseCode());
			
			InputStream is = conn.getInputStream();
			String output = IOUtils.toString(is);
			assertTrue(output.contains("<buildResultUUID>"+action.getBuildResultUUID()+"</buildResultUUID>"));
			assertTrue(output.contains("<displayName>"+action.getDisplayName()+"</displayName>"));
			assertTrue(output.contains("<serverURI>"+action.getServerURI()+"</serverURI>"));
			assertTrue(output.contains("<urlName>"+action.getUrlName()+"</urlName>"));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	/**
	 * Validate the snapshot name generated in the builds - with and without providing custom snapshot name
	 * 
	 * @param prj
	 * @param setupArtifacts
	 * @param buildDefinitionId
	 * @throws Exception
	 */
	private void validateCustomSnapshotName_repositoryWS(FreeStyleProject prj) throws Exception {
		// Run a build, without providing custom snapshot name
		FreeStyleBuild build = Utils.runBuild(prj, null);

		// Verify that by default the snapshot name is set to <Job Name>_#<Build Number>
		RTCChangeLogSet changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());
		

		// update the job configuration with custom snapshot name
		// but do not set overrideDefaultSnapshotName to true. Should use default snapshot name even if a custom snapshot name is provided 
		RTCScm rtcScm = (RTCScm)prj.getScm();
		BuildType buildType = rtcScm.getBuildType();
		buildType.setCustomizedSnapshotName("jenkins_${JOB_NAME}_#${BUILD_NUMBER}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		// Verify that the snapshot name is set to the default value
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());

		// update the job configuration with custom snapshot name and set overrideDefaultSnapshotName to true
		rtcScm = (RTCScm)prj.getScm();
		buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(true);
		buildType.setCustomizedSnapshotName("jenkins_${JOB_NAME}_#${BUILD_NUMBER}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, null);

		// Verify that the template specified in the custom snapshot name is resolved and set as the name of the
		// generated snapshot
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals("jenkins_" + prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());
		
		// update the job configuration with custom snapshot name that resolves to an empty string and set overrideDefaultSnapshotName to true
		rtcScm = (RTCScm)prj.getScm();
		buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(true);
		buildType.setCustomizedSnapshotName("${emptyParam}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
		
		prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {new StringParameterDefinition("emptyParam", " ")})));
		
		build = Utils.runBuild(prj, Collections.singletonList(new ParametersAction(new StringParameterValue("emptyParam", " "))));

		// Verify that the snapshot name is set to a default value
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());
		// Verify that the console output has the log message
		assertNotNull(Utils
				.getMatch(build.getLogFile(), java.util.regex.Pattern.quote(Messages.RTCScm_empty_resolved_snapshot_name("${emptyParam}"))));
		
	}
	

	/**
	 * Validate the snapshot name generated in the builds - with and without providing custom snapshot name
	 * 
	 * @param prj
	 * @param setupArtifacts
	 * @param buildDefinitionId
	 * @throws Exception
	 */
	private void validateCustomSnapshotName_buildDef(FreeStyleProject prj, Map<String, String> setupArtifacts, String buildDefinitionId)
			throws Exception {
		// Run a build, without providing custom snapshot name
		
		// This Jenkins instance sees all parameters of the build as environment variables.
		// So buildResultUUID will be set if a personal build was triggered from RTC
		// The  build running inside Jenkins instance will see the buildResultUUID parameter
		// and try to get IBuildResult in the context of the test RTC server but this buildResultUUID 
		// is from our production server. To avoid this issue, we explicitly set buildResultUUID to be empty
		// so that the test build will create a new build result in the RTC test server.
		FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
		setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_ID, action.getBuildResultUUID());

		// Verify
		// By default snapshot name should be <Build Definition Id>_<Build Number>
		RTCChangeLogSet changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(buildDefinitionId + "_#" + build.getNumber(), changelog.getBaselineSetName());

		// update the job configuration with custom snapshot name
		// but do not set overrideDefaultSnapshotName to true. Should use default snapshot name even if a custom snapshot name is provided 
		RTCScm rtcScm = (RTCScm)prj.getScm();
		BuildType buildType = rtcScm.getBuildType();
		buildType.setCustomizedSnapshotName("jenkins_${JOB_NAME}_#${BUILD_NUMBER}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		action = build.getActions(RTCBuildResultAction.class).get(0);
		setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID, action.getBuildResultUUID());
		
		// Verify that the snapshot name is set to the default value
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(buildDefinitionId + "_#" + build.getNumber(), changelog.getBaselineSetName());
		

		// update the job configuration with custom snapshot name and set overrideDefaultSnapshotName to true
		rtcScm = (RTCScm)prj.getScm();
		buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(true);
		buildType.setCustomizedSnapshotName("jenkins_${JOB_NAME}_#${BUILD_NUMBER}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));

		build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
		action = build.getActions(RTCBuildResultAction.class).get(0);
		setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_2_ID, action.getBuildResultUUID());

		// Verify that the template specified in the custom snapshot name is resolved and set as the name of the
		// generated snapshot
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals("jenkins_" + prj.getName() + "_#" + build.getNumber(), changelog.getBaselineSetName());

		// update the job configuration with custom snapshot name that resolves to an empty string and set overrideDefaultSnapshotName to true
		rtcScm = (RTCScm)prj.getScm();
		buildType = rtcScm.getBuildType();
		buildType.setOverrideDefaultSnapshotName(true);
		buildType.setCustomizedSnapshotName("${emptyParam}");
		prj.setScm(Utils.updateAndGetRTCScm(rtcScm, buildType));
		
		prj.addProperty(new ParametersDefinitionProperty(Arrays.asList(new ParameterDefinition[] {new StringParameterDefinition("emptyParam", " ")})));
		
		List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
		pActions.add(new ParametersAction(new StringParameterValue("emptyParam", " ")));
		build = Utils.runBuild(prj, pActions);

		action = build.getActions(RTCBuildResultAction.class).get(0);
		setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_3_ID, action.getBuildResultUUID());

		// Verify that the snapshot name is set to a default value
		changelog = (RTCChangeLogSet)build.getChangeSet();
		assertEquals(buildDefinitionId + "_#" + build.getNumber(), changelog.getBaselineSetName());
		// Verify that the console output has the log message		
		assertNotNull(Utils.getMatch(build.getLogFile(),
				java.util.regex.Pattern.quote(Messages.RTCScm_empty_resolved_snapshot_name("${emptyParam}"))));
	}
}
