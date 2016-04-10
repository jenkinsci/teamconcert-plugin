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

import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;

import net.sf.json.JSONObject;

import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;
import com.ibm.team.build.internal.hjplugin.RTCRepositoryBrowser;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl;

@SuppressWarnings("nls")
public class RTCScmIT extends HudsonTestCase {

	private static final String CONFIGURE = "configure";
	private static final String CONFIG = "config";
	private static final String BUILD_TOOLKIT = "buildToolkit";
	private static final String OVERRIDE_GLOBAL = "overrideGlobal";
	private static final String SERVER_URI = "serverURI";
	private static final String USER_ID = "userId";
	private static final String PASSWORD = "password";
	private static final String PASSWORD_FILE = "passwordFile";
	private static final String CREDENTIALS_ID = "_.credentialsId";
	private static final String AVOID_USING_TOOLKIT = "avoidUsingToolkit";
	private static final String TIMEOUT = "timeout";
	private static final String BUILD_DEFINITION = "buildDefinition";
	private static final String BUILD_TYPE = "buildType";

	private static final String TEST_GLOBAL_BUILD_TOOLKIT = "C:\\buildtoolkit";
	private static final String TEST_GLOBAL_SERVER_URI = "https://localhost:9443/ccm";
	private static final String TEST_GLOBAL_USER_ID = "ADMIN";
	private static final String TEST_GLOBAL_PASSWORD = "ADMIN";
	private static final String TEST_GLOBAL_PASSWORD_FILE = "C:/Users/ADMIN/ADMIN-password";
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

	private RTCScm createEmptyRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "", "", "", "");
		return new RTCScm(false, "", "", 0, "", Secret.fromString(""), "", "", buildSource, false);
	}

	private RTCScm createTestOverrideGlobalRTCScm(boolean useCreds) {
		BuildType buildSource = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, TEST_BUILD_DEFINITION, TEST_BUILD_WORKSPACE, TEST_BUILD_SNAPSHOT, TEST_BUILD_STREAM);
		return new RTCScm(true, "", TEST_SERVER_URI, Integer.parseInt(TEST_TIMEOUT), TEST_USER_ID, Secret.fromString(TEST_PASSWORD), TEST_PASSWORD_FILE,
				useCreds ? TEST_CRED_ID : null, buildSource, false);
	}

	@Override
	protected void setUp() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			super.setUp();
		}
	}

	@Override
	protected void tearDown() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			super.tearDown();
		}
	}

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

	public void testDoCheckBuildTool() throws IOException {
		if (Config.DEFAULT.isConfigured()) {
			DescriptorImpl descriptor = (DescriptorImpl) hudson.getDescriptor(RTCScm.class);
	
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

	public void testDoCheckCredentials() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
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

	public void testDoCheckUserId() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
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

	public void testDoCheckPassword() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
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

	public void testDoCheckPasswordFile() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File testPasswordFileFile = File.createTempFile("ADMIN-password", null);
			String testPasswordFile = testPasswordFileFile.getAbsolutePath();
	
			FreeStyleProject project = createFreeStyleProject();
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
	

	public void testDoCheckTimeout() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
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

	public void testJobConfigRoundtripOverrideGlobal() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
			RTCScm rtcScm = createTestOverrideGlobalRTCScm(false);
			project.setScm(rtcScm);
	
			submit(createWebClient().getPage(project, CONFIGURE).getFormByName(CONFIG));
	
			RTCScm newRtcScm = (RTCScm) project.getScm();
	
			assertEqualBeans(rtcScm, newRtcScm, OVERRIDE_GLOBAL + "," + SERVER_URI + "," + USER_ID + "," + PASSWORD + "," + PASSWORD_FILE + ","
					+ BUILD_TYPE + "," + BUILD_DEFINITION);
		}
	}

	public void testJobConfigRoundtripWithCredentials() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			DescriptorImpl descriptor = (DescriptorImpl) rtcScm.getDescriptor();
			project.setScm(rtcScm);
	
			StaplerRequest mockedReq = Mockito.mock(StaplerRequest.class);
			Mockito.when(mockedReq.getParameter(BUILD_TOOLKIT)).thenReturn(TEST_GLOBAL_BUILD_TOOLKIT);
			Mockito.when(mockedReq.getParameter(SERVER_URI)).thenReturn(TEST_GLOBAL_SERVER_URI);
			Mockito.when(mockedReq.getParameter(USER_ID)).thenReturn(TEST_GLOBAL_USER_ID);
			Mockito.when(mockedReq.getParameter(TIMEOUT)).thenReturn(TEST_GLOBAL_TIMEOUT);
			Mockito.when(mockedReq.getParameter(PASSWORD)).thenReturn(TEST_GLOBAL_PASSWORD);
			Mockito.when(mockedReq.getParameter(PASSWORD_FILE)).thenReturn(TEST_GLOBAL_PASSWORD_FILE);
			Mockito.when(mockedReq.getParameter(CREDENTIALS_ID)).thenReturn(TEST_GLOBAL_CRED_ID);
			JSONObject mockJSON = new JSONObject();
			mockJSON.element(AVOID_USING_TOOLKIT, new JSONObject());
			mockJSON.element(CREDENTIALS_ID, TEST_GLOBAL_CRED_ID);
			mockJSON.element(SERVER_URI, TEST_GLOBAL_SERVER_URI);
			mockJSON.element(TIMEOUT, TEST_GLOBAL_TIMEOUT);
			descriptor.configure(mockedReq, mockJSON);
	
			descriptor.configure(mockedReq, mockJSON);
	
			WebClient webClient = new WebClient();
	
			// Get the page to configure the project
			HtmlPage page = webClient.getPage(project, CONFIGURE);
	
			// Get the config form
			HtmlForm form = page.getFormByName(CONFIG);
	
			// Get the inputs
			HtmlCheckBoxInput overrideGlobalInput = form.getInputByName(OVERRIDE_GLOBAL);
	
			// Set the input values
			overrideGlobalInput.setChecked(false);
	
			// Submit the config form
			submit(form);
	
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

	public void testJobConfigRoundtripWithoutCredentials() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = createFreeStyleProject();
			RTCScm rtcScm = createEmptyRTCScm();
			DescriptorImpl descriptor = (DescriptorImpl) rtcScm.getDescriptor();
			project.setScm(rtcScm);
	
			StaplerRequest mockedReq = Mockito.mock(StaplerRequest.class);
			Mockito.when(mockedReq.getParameter(BUILD_TOOLKIT)).thenReturn(TEST_GLOBAL_BUILD_TOOLKIT);
			Mockito.when(mockedReq.getParameter(SERVER_URI)).thenReturn(TEST_GLOBAL_SERVER_URI);
			Mockito.when(mockedReq.getParameter(USER_ID)).thenReturn(TEST_GLOBAL_USER_ID);
			Mockito.when(mockedReq.getParameter(TIMEOUT)).thenReturn(TEST_GLOBAL_TIMEOUT);
			Mockito.when(mockedReq.getParameter(PASSWORD)).thenReturn(TEST_GLOBAL_PASSWORD);
			Mockito.when(mockedReq.getParameter(PASSWORD_FILE)).thenReturn(TEST_GLOBAL_PASSWORD_FILE);
			Mockito.when(mockedReq.getParameter(CREDENTIALS_ID)).thenReturn(null);
			JSONObject mockJSON = new JSONObject();
			mockJSON.element(AVOID_USING_TOOLKIT, new JSONObject());
			mockJSON.element(SERVER_URI, TEST_GLOBAL_SERVER_URI);
			mockJSON.element(TIMEOUT, TEST_GLOBAL_TIMEOUT);
			descriptor.configure(mockedReq, mockJSON);
	
			WebClient webClient = new WebClient();
	
			// Get the page to configure the project
			HtmlPage page = webClient.getPage(project, CONFIGURE);
	
			// Get the config form
			HtmlForm form = page.getFormByName(CONFIG);
	
			// Get the inputs
			HtmlCheckBoxInput overrideGlobalInput = form.getInputByName(OVERRIDE_GLOBAL);
	
			// Set the input values
			overrideGlobalInput.setChecked(false);
	
			// Submit the config form
			submit(form);
	
			// check submitted SCM result
			RTCScm newRtcScm = (RTCScm) project.getScm();
			assertEquals(false, newRtcScm.getOverrideGlobal());
			assertEquals(TEST_GLOBAL_SERVER_URI, newRtcScm.getServerURI());
			assertEquals(TEST_GLOBAL_TIMEOUT, String.valueOf(newRtcScm.getTimeout()));
			assertEquals(TEST_GLOBAL_USER_ID, newRtcScm.getUserId());
			assertEquals(TEST_GLOBAL_PASSWORD, newRtcScm.getPassword());
			assertEquals(TEST_GLOBAL_PASSWORD_FILE, newRtcScm.getPasswordFile());
			assertEquals(null, newRtcScm.getCredentialsId());
			assertTrue(newRtcScm.getAvoidUsingToolkit());
		}
	}
}
