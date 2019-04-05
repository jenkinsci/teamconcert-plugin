/*******************************************************************************
 * Copyright © 2013, 2019 IBM Corporation and others.
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
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.kohsuke.stapler.StaplerRequest;
import org.mockito.Mockito;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;

import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import net.sf.json.JSONObject;

@SuppressWarnings({"nls", "boxing"})
public class MayFailIT extends AbstractTestCase {
	private static final String CONFIGURE = "configure";
	private static final String CONFIG = "config";
	private static final String OVERRIDE_GLOBAL = "overrideGlobal";
	private static final String SERVER_URI = "serverURI";
	private static final String AVOID_USING_TOOLKIT = "avoidUsingToolkit";
	private static final String BUILD_DEFINITION = "buildDefinition";
	private static final String BUILD_TYPE = "buildType";

	private static final String TEST_GLOBAL_BUILD_TOOLKIT = "C:\\buildtoolkit";
	private static final String TEST_GLOBAL_SERVER_URI = "https://localhost:9443/ccm";
	private static final String TEST_GLOBAL_USER_ID = "ADMIN";
	private static final String TEST_GLOBAL_PASSWORD = "ADMIN";
	private static final String TEST_GLOBAL_PASSWORD_FILE = "C:/Users/ADMIN/ADMIN-password";
	@SuppressWarnings("unused")
	private static final String TEST_GLOBAL_CRED_ID = "1234";
	private static final String TEST_GLOBAL_TIMEOUT = "480";
	
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
	
	@Rule
	public JenkinsRule j = new  JenkinsRule();
	
	@Test public void testJobConfigRoundtripOverrideGlobal() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = getJenkinsRule().createFreeStyleProject();
			RTCScm rtcScm = createTestOverrideGlobalRTCScm(false);
			project.setScm(rtcScm);
	
			getJenkinsRule().submit(getJenkinsRule().createWebClient().getPage(project, CONFIGURE).getFormByName(CONFIG));
	
			RTCScm newRtcScm = (RTCScm) project.getScm();
	
			getJenkinsRule().assertEqualBeans(rtcScm, newRtcScm, OVERRIDE_GLOBAL + "," + SERVER_URI + "," + USER_ID + "," + PASSWORD + "," + PASSWORD_FILE + ","
					+ BUILD_TYPE + "," + BUILD_DEFINITION);
		}
	}

	@Test public void testJobConfigRoundtripWithoutCredentials() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = getJenkinsRule().createFreeStyleProject();
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
	
			WebClient webClient = getJenkinsRule().createWebClient();
	
			// Get the page to configure the project
			HtmlPage page = webClient.getPage(project, CONFIGURE);
	
			// Get the config form
			HtmlForm form = page.getFormByName(CONFIG);
	
			// Get the inputs
			HtmlCheckBoxInput overrideGlobalInput = form.getInputByName(OVERRIDE_GLOBAL);
	
			// Set the input values
			overrideGlobalInput.setChecked(false);
	
			// Submit the config form
			getJenkinsRule().submit(form);
	
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
	
	private RTCScm createTestOverrideGlobalRTCScm(boolean useCreds) {
		BuildType buildSource = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, TEST_BUILD_DEFINITION, TEST_BUILD_WORKSPACE, TEST_BUILD_SNAPSHOT, TEST_BUILD_STREAM);
		return new RTCScm(true, "", TEST_SERVER_URI, Integer.parseInt(TEST_TIMEOUT), TEST_USER_ID, Secret.fromString(TEST_PASSWORD), TEST_PASSWORD_FILE,
				useCreds ? TEST_CRED_ID : null, buildSource, false);
	}
	
	private RTCScm createEmptyRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "", "", "", "");
		return new RTCScm(false, "", "", 0, "", Secret.fromString(""), "", "", buildSource, false);
	}

	public JenkinsRule getJenkinsRule() {
		return this.j;
	}

	public void setJenkinsRule(JenkinsRule j) {
		this.j = j;
	}

}
