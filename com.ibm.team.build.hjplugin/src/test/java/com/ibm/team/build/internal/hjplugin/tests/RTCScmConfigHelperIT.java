/*******************************************************************************
 * Copyright © 2014, 2018 IBM Corporation and others.
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

import java.util.HashSet;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.MockScm;
import com.ibm.team.build.internal.hjplugin.util.RTCScmConfigHelper;

import hudson.model.FreeStyleProject;
import hudson.util.Secret;

@SuppressWarnings("nls")
public class RTCScmConfigHelperIT extends AbstractTestCase {

	private static final String DEFN_SERVER_URI = "https://localHost:4321/jazz";
	private static final String SNAPSHOT_NAME = "AnotherSnapshot";
	
	@Rule
	public JenkinsRule j = new  JenkinsRule();

	/**
	 * Tests that we can get the RTCScm configuration from the project
	 * Tests we behave ok if the SCM is not set too
	 * Tests we behave ok if its not an RTCScm
	 * @throws Exception
	 */
	@Test
	public void testGetCurrentConfigs() throws Exception {
		
		if (Config.DEFAULT.isConfigured()) {
			FreeStyleProject project = this.j.createFreeStyleProject();
			
			// no scm
			Set<RTCScm> currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected no RTCScm instances", 0, currentConfigs.size());
			
			// workspace configured SCM
			RTCScm rtcScm = createWorkspaceRTCScm();
			project.setScm(rtcScm);
			
			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected Workspace RTCScm instance only", 1, currentConfigs.size());
			assertEquals("Expected Workspace RTCScm instance", rtcScm, currentConfigs.iterator().next());

			// workspace with load directory configured SCM
			rtcScm = createWorkspaceWithLoadDirectoryRTCScm();
			project.setScm(rtcScm);

			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected Workspace with Load directory RTCScm instance only", 1, currentConfigs.size());
			assertEquals("Expected Workspace with Load directory RTCScm instance", rtcScm, currentConfigs.iterator().next());

			// workspace with delete directory configured SCM
			rtcScm = createWorkspaceWithDeleteDirectoryRTCScm();
			project.setScm(rtcScm);

			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected Workspace with delete directory RTCScm instance only", 1, currentConfigs.size());
			assertEquals("Expected Workspace with delete directory RTCScm instance", rtcScm, currentConfigs.iterator().next());

			// snapshot configured SCM
			rtcScm = createSnapshotRTCScm();
			project.setScm(rtcScm);
			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected Snapshot RTCScm instance only", 1, currentConfigs.size());
			assertEquals("Expected Snapshot RTCScm instance", rtcScm, currentConfigs.iterator().next());
			
			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected Workspace RTCScm instance only", 1, currentConfigs.size());
			assertEquals("Expected Workspace RTCScm instance", rtcScm, currentConfigs.iterator().next());
			
			// non RTC Scm
			project = this.j.createFreeStyleProject();
			project.setScm(new MockScm("mocking"));

			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("MockScm should not of been returned", 0, currentConfigs.size());

			// build definition configured SCM
			rtcScm = createBuildDefnRTCScm();
			project.setScm(rtcScm);
			
			currentConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
			assertEquals("Expected Build Definition RTCScm instance only", 1, currentConfigs.size());
			assertEquals("Expected Build Definition RTCScm instance", rtcScm, currentConfigs.iterator().next());
		}
	}

	@Test public void testFindRTCScm() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Set<RTCScm> multipleConfigs = new HashSet<RTCScm>();
		RTCScm defnRTCScm = createBuildDefnRTCScm();
		RTCScm wsRTCScm = createWorkspaceRTCScm();
		RTCScm uninitializedRTCScm =createUninitializedRTCScm();
		
		RTCBuildResultAction buildResultActionWithDefnURI = new RTCBuildResultAction(DEFN_SERVER_URI, "_yamC4NWiEdylmcAI5HeTUQ", true, null);
		RTCBuildResultAction unexpectedBuildAction = new RTCBuildResultAction(null, "_yamC4NWiEdylmcAI5HeTUQ", true, null);
		RTCBuildResultAction buildResultActionWithRandomURI = new RTCBuildResultAction("https://somewhere.com:7777", "_yamC4NWiEdylmcAI5HeTUQ", false, null);
		
		// test only 1 config
		multipleConfigs.add(defnRTCScm);
		RTCScm rtcScmFound = RTCScmConfigHelper.findRTCScm(multipleConfigs, buildResultActionWithDefnURI );
		assertNotNull(rtcScmFound);
		assertEquals(defnRTCScm, rtcScmFound);
		
		// test with 2 configs the matching server uri is found
		multipleConfigs.clear();
		multipleConfigs.add(defnRTCScm);
		multipleConfigs.add(wsRTCScm);
		rtcScmFound = RTCScmConfigHelper.findRTCScm(multipleConfigs, buildResultActionWithDefnURI );
		assertNotNull(rtcScmFound);
		assertEquals(defnRTCScm, rtcScmFound);

		// test with 2 configs but action doesn't match
		multipleConfigs.clear();
		multipleConfigs.add(defnRTCScm);
		multipleConfigs.add(wsRTCScm);
		rtcScmFound = RTCScmConfigHelper.findRTCScm(multipleConfigs, buildResultActionWithRandomURI );
		assertNull(rtcScmFound);

		// test with 2 configs but action is malformed
		multipleConfigs.clear();
		multipleConfigs.add(defnRTCScm);
		multipleConfigs.add(wsRTCScm);
		rtcScmFound = RTCScmConfigHelper.findRTCScm(multipleConfigs, unexpectedBuildAction );
		assertNull(rtcScmFound);
		
		// test with 2 configs but action doesn't match and an RTCScm not setup
		multipleConfigs.clear();
		multipleConfigs.add(defnRTCScm);
		multipleConfigs.add(wsRTCScm);
		multipleConfigs.add(uninitializedRTCScm);
		rtcScmFound = RTCScmConfigHelper.findRTCScm(multipleConfigs, buildResultActionWithRandomURI );
		assertNull(rtcScmFound);

	}

	@SuppressWarnings("static-method")
	private RTCScm createUninitializedRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, "", "", "", "");
		return new RTCScm(true, "", null, 0, "ADMIN", Secret.fromString(""), "", "", buildSource, false);
	}

	@SuppressWarnings("static-method")
	private RTCScm createWorkspaceRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "", "AnotherWorkspace", "", "");
		return new RTCScm(true, "", "https://localHost:1234", 0, "ADMIN", Secret.fromString(""), "", "", buildSource, false);
	}
	
	@SuppressWarnings("static-method")
	private RTCScm createWorkspaceWithLoadDirectoryRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "", "AnotherWorkspace", "", "");
		buildSource.setLoadDirectory("testLoadDirectory");
		buildSource.setClearLoadDirectory(false);
		return new RTCScm(true, "", "https://localHost:1234", 0, "ADMIN", Secret.fromString(""), "", "", buildSource, false);
	}
	
	@SuppressWarnings("static-method")
	private RTCScm createWorkspaceWithDeleteDirectoryRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_WORKSPACE_TYPE, "", "AnotherWorkspace", "", "");
		buildSource.setLoadDirectory("");
		buildSource.setClearLoadDirectory(true);
		return new RTCScm(true, "", "https://localHost:1234", 0, "ADMIN", Secret.fromString(""), "", "", buildSource, false);
	}
	
	@SuppressWarnings("static-method")	
	private RTCScm createSnapshotRTCScm() {
		BuildType buildType = new BuildType(RTCScm.BUILD_SNAPSHOT_TYPE, "", "", SNAPSHOT_NAME, "");
		return new RTCScm(true, "", "https://localHost:1234", 0, "ADMIN", Secret.fromString(""), "", "", buildType, false);
	}

	@SuppressWarnings("static-method")
	private RTCScm createBuildDefnRTCScm() {
		BuildType buildSource = new BuildType(RTCScm.BUILD_DEFINITION_TYPE, "AnotherBuildDefinition", "", "", "");
		return new RTCScm(true, "", DEFN_SERVER_URI, 0, "ADMIN", Secret.fromString(""), "", "", buildSource, false);
	}
}
