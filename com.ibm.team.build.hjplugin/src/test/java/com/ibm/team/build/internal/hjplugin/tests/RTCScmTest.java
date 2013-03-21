/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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

import junit.framework.Assert;

import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl;

public class RTCScmTest extends HudsonTestCase {

	private static final String CONFIGURE = "configure";
	private static final String CONFIG = "config";
	private static final String BUILD_TOOLKIT = "buildToolkit";
	private static final String OVERRIDE_GLOBAL = "overrideGlobal";
	private static final String SERVER_URI = "serverURI";
	private static final String USER_ID = "userId";
	private static final String PASSWORD = "password";
	private static final String PASSWORD_FILE = "passwordFile";
	private static final String TIMEOUT = "timeout";
	private static final String BUILD_WORKSPACE = "buildWorkspace";

	private static final String TEST_GLOBAL_BUILD_TOOLKIT = "C:\\buildtoolkit";
	private static final String TEST_GLOBAL_SERVER_URI = "https://localhost:9443/ccm";
	private static final String TEST_GLOBAL_USER_ID = "ADMIN";
	private static final String TEST_GLOBAL_PASSWORD = "ADMIN";
	private static final String TEST_GLOBAL_PASSWORD_FILE = "C:/Users/ADMIN/ADMIN-password";
	private static final String TEST_GLOBAL_TIMEOUT = "480";

	private static final String TEST_SERVER_URI = "https://localhost:9443/jazz";
	private static final String TEST_USER_ID = "bill";
	private static final String TEST_PASSWORD = "bill";
	private static final String TEST_PASSWORD_FILE = "C:/Users/bill/bill-password";
	private static final String TEST_TIMEOUT = "480";
	private static final String TEST_BUILD_WORKSPACE = "compile-and-test";

	private RTCScm createEmptyRTCScm() {
		return new RTCScm(false, "", "", 0, "", Secret.fromString(""), "", "");
	}

	private RTCScm createTestOverrideGlobalRTCScm() {
		return new RTCScm(true, "", TEST_SERVER_URI, Integer.parseInt(TEST_TIMEOUT), TEST_USER_ID, Secret.fromString(TEST_PASSWORD), TEST_PASSWORD_FILE,
				TEST_BUILD_WORKSPACE);
	}

	public void testRTCScmConstructor() throws Exception {
		RTCScm scm = createTestOverrideGlobalRTCScm();
		Assert.assertEquals(TEST_SERVER_URI, scm.getServerURI());
		Assert.assertEquals(Integer.parseInt(TEST_TIMEOUT), scm.getTimeout());
		Assert.assertEquals(TEST_USER_ID, scm.getUserId());
		Assert.assertEquals(TEST_PASSWORD, scm.getPassword());
		Assert.assertEquals(TEST_PASSWORD_FILE, scm.getPasswordFile());
		Assert.assertEquals(TEST_BUILD_WORKSPACE, scm.getBuildWorkspace());
	}

	public void testDoCheckBuildToolkit() throws IOException {
		DescriptorImpl descriptor = (DescriptorImpl) hudson.getDescriptor(RTCScm.class);

		// null is not a build toolkit
		assertDoCheckBuildToolkit(descriptor, FormValidation.Kind.ERROR, null);

		File tempFolder = Files.createTempDir();
		File tempBuildToolkitTaskDefsXmlFile = new File(tempFolder, RTCBuildToolInstallation.BUILD_TOOLKIT_TASK_DEFS_XML);
		tempBuildToolkitTaskDefsXmlFile.createNewFile();
		String tempBuildToolkitTaskDefsXmlPath = tempBuildToolkitTaskDefsXmlFile.getAbsolutePath();

		// build toolkit task defs file is not a build toolkit
		assertDoCheckBuildToolkit(descriptor, FormValidation.Kind.ERROR, tempBuildToolkitTaskDefsXmlPath);

		String tempFolderPath = tempFolder.getAbsolutePath();

		// folder containing build toolkit task defs file is a build toolkit ... probably ;-)
		assertDoCheckBuildToolkit(descriptor, FormValidation.Kind.OK, tempFolderPath);

		boolean deleted = tempBuildToolkitTaskDefsXmlFile.delete();

		if (deleted) {

			// folder not containing build toolkit task defs file is not a build toolkit
			assertDoCheckBuildToolkit(descriptor, FormValidation.Kind.ERROR, tempFolderPath);
			
			// missing file is not a build toolkit
			assertDoCheckBuildToolkit(descriptor, FormValidation.Kind.ERROR, tempBuildToolkitTaskDefsXmlPath);
		}

		deleted = tempFolder.delete();

		if (deleted) {
			
			// missing folder is not a build toolkit
			assertDoCheckBuildToolkit(descriptor, FormValidation.Kind.ERROR, tempFolderPath);
		}
	}

	private void assertDoCheckBuildToolkit(DescriptorImpl descriptor, FormValidation.Kind kind, String buildToolkit) {
		FormValidation validation = descriptor.doCheckBuildToolkit(buildToolkit);
		assertEquals("Expected password validation " + kind + ": " + BUILD_TOOLKIT + "=\"" + buildToolkit + "\"", kind, validation.kind);
	}

	public void testDoCheckPassword() throws Exception {
		File testPasswordFileFile = File.createTempFile("ADMIN-password", null);
		String testPasswordFile = testPasswordFileFile.getAbsolutePath();

		FreeStyleProject project = createFreeStyleProject();
		RTCScm rtcScm = createEmptyRTCScm();
		project.setScm(rtcScm);

		DescriptorImpl descriptor = (DescriptorImpl) project.getDescriptorByName(RTCScm.class.getName());

		assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, null, null);
		assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, "", null);
		assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, null, "");
		assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, "", "");
		assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_PASSWORD, null);
		assertDoCheckPassword(descriptor, FormValidation.Kind.OK, TEST_PASSWORD, "");
		assertDoCheckPassword(descriptor, FormValidation.Kind.OK, null, testPasswordFile);
		assertDoCheckPassword(descriptor, FormValidation.Kind.OK, "", testPasswordFile);
		assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, TEST_PASSWORD, testPasswordFile);
		assertDoCheckPassword(descriptor, FormValidation.Kind.ERROR, null, "doesnotexist");

		testPasswordFileFile.delete();
	}

	private void assertDoCheckPassword(DescriptorImpl descriptor, FormValidation.Kind kind, String password, String passwordFile) {
		FormValidation validation = descriptor.doCheckPassword(password, passwordFile);
		assertEquals("Expected password validation " + kind + ": " + PASSWORD + "=\"" + password + "\", " + PASSWORD_FILE + "=\"" + passwordFile + "\"", kind,
				validation.kind);
	}

	public void testDoCheckTimeout() throws Exception {
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

	private void assertDoCheckTimeout(DescriptorImpl descriptor, FormValidation.Kind kind, String timeout) {
		FormValidation validation = descriptor.doCheckTimeout(timeout);
		assertEquals("Expected timeout validation " + kind + ": " + TIMEOUT + "=\"" + timeout + "\"", kind, validation.kind);
	}

	public void testJobConfigRoundtripOverrideGlobal() throws Exception {

		FreeStyleProject project = createFreeStyleProject();
		RTCScm rtcScm = createTestOverrideGlobalRTCScm();
		project.setScm(rtcScm);

		submit(createWebClient().getPage(project, CONFIGURE).getFormByName(CONFIG));

		RTCScm newRtcScm = (RTCScm) project.getScm();

		assertEqualBeans(rtcScm, newRtcScm, OVERRIDE_GLOBAL + "," + SERVER_URI + "," + USER_ID + "," + PASSWORD + "," + PASSWORD_FILE + ","
				+ BUILD_WORKSPACE);
	}

	public void testJobConfigRoundtrip() throws Exception {
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
		descriptor.configure(mockedReq, null);

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
	}

}
