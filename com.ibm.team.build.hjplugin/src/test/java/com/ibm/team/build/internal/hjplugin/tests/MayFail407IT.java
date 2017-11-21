/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests;


import static org.junit.Assert.assertTrue;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.model.TaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.LoadOptions;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

/**
 * Add those tests that may/will fail in 407 toolkit to this class.
 * 
 */
public class MayFail407IT extends AbstractTestCase {

	private RTCFacadeWrapper testingFacade;

	@Rule
	public JenkinsRule r = new JenkinsRule();

	@Before
	public void setUp() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}

		testingFacade = Utils.getTestingFacade();
		createSandboxDirectory();

		Utils.deleteTemporaryWorkspaces();
	}

	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
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
	
	@SuppressWarnings("unchecked")
	@Test
	public void testStreamConfig_loadPolicy() throws Exception {
		// create a repository workspace with a valid load rule file and multiple components

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted

		
		// Set loadPolicy and componentLoadConfig to different values and validate that buildConfiguration instance is
		// initialized as expected
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			Map<String, String> setupArtifacts = null;

			try {
				String workspaceName = getRepositoryWorkspaceUniqueName();
				String streamName = workspaceName + "_lrStream";
				String componentName = getComponentUniqueName();
				String fetchLocation = ".";

				setupArtifacts = (Map<String, String>)testingFacade.invoke("testStreamConfig_loadPolicy",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName,
								String.class, // hjPath,
								String.class }, // buildPath
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName,
						componentName, sandboxDir.getPath(), fetchLocation);
				// Scenario#1
				// do not set loadPolicy and componentLoadConfig
				// set createFoldersForComponents to false
				// do not set componentsToExclude
				// do not set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());

					String[] children = loadDir.list();
					Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
					// Validate contents of component1
					File fComp1 = new File(loadDir, "f-comp1");
					Assert.assertTrue(fComp1.exists());
					Assert.assertTrue(fComp1.isDirectory());
					Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());

					File gComp1 = new File(loadDir, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());

					File hComp1 = new File(loadDir, "h-comp1");
					Assert.assertTrue(hComp1.exists());
					Assert.assertTrue(hComp1.isDirectory());
					Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

					// Validate contents of component2
					File fComp2 = new File(loadDir, "f-comp2");
					Assert.assertTrue(fComp2.exists());
					Assert.assertTrue(fComp2.isDirectory());
					Assert.assertTrue(new File(fComp2, "a.txt").exists());

					File gComp2 = new File(loadDir, "g-comp2");
					Assert.assertTrue(gComp2.exists());
					Assert.assertTrue(gComp2.isDirectory());
					Assert.assertTrue(new File(gComp2, "b.txt").exists());

					File hComp2 = new File(loadDir, "h-comp2");
					Assert.assertTrue(hComp2.exists());
					Assert.assertTrue(hComp2.isDirectory());
					Assert.assertTrue(new File(hComp2, "c.txt").exists());

					RTCChangeLogParser parser = new RTCChangeLogParser();
					FileReader changeLogReader = new FileReader(changeLogFile);
					RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

					// verify the result
					int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
					Assert.assertEquals(0, changeCount);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}
				
				// Scenario#2
				// do not set loadPolicy and componentLoadConfig
				// set createFoldersForComponents to true
				// set componentsToExclude
				// set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					loadOptions.createFoldersForComponents = true;
					loadOptions.componentsToExclude = componentName + 2;
					loadOptions.pathToLoadRuleFile = componentName + "1/h-comp1/new.loadRule";
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
					
					String[] children = loadDir.list();
					Assert.assertEquals(3, children.length); // all contents of component1 and component2 + metadata
					// Validate contents of component1
					File comp1Root = new File(loadDir, componentName + 1);
					Assert.assertTrue(comp1Root.exists());
					Assert.assertTrue(comp1Root.isDirectory());
					File fComp1 = new File(comp1Root, "f-comp1");
					Assert.assertTrue(fComp1.exists());
					Assert.assertTrue(fComp1.isDirectory());
					Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					
					File hComp1 = new File(comp1Root, "h-comp1");
					Assert.assertTrue(hComp1.exists());
					Assert.assertTrue(hComp1.isDirectory());
					Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

					// Validate contents of component2
					File comp2Root = new File(loadDir, componentName + 2);
					Assert.assertTrue(comp2Root.exists());
					Assert.assertTrue(comp2Root.isDirectory());
					File fComp2 = new File(comp2Root, "f-comp2");
					Assert.assertTrue(fComp2.exists());
					Assert.assertTrue(fComp2.isDirectory());
					Assert.assertTrue(new File(fComp2, "a.txt").exists());

					File gComp2 = new File(comp2Root, "g-comp2");
					Assert.assertTrue(gComp2.exists());
					Assert.assertTrue(gComp2.isDirectory());
					Assert.assertTrue(new File(gComp2, "b.txt").exists());

					File hComp2 = new File(comp2Root, "h-comp2");
					Assert.assertTrue(hComp2.exists());
					Assert.assertTrue(hComp2.isDirectory());
					Assert.assertTrue(new File(hComp2, "c.txt").exists());

					RTCChangeLogParser parser = new RTCChangeLogParser();
					FileReader changeLogReader = new FileReader(changeLogFile);
					RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

					// verify the result
					int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
					Assert.assertEquals(0, changeCount);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}
				
				// Scenario#3
				// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig
				// set createFoldersForComponents to true
				// set componentsToExclude
				// set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					loadOptions.createFoldersForComponents = true;
					loadOptions.componentsToExclude = componentName + 2;
					loadOptions.pathToLoadRuleFile = componentName + "1/h-comp1/new.loadRule";
					loadOptions.loadPolicy = RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG;
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
					
					String[] children = loadDir.list();
					Assert.assertEquals(3, children.length); // all contents of component1 and component2 + metadata
					// Validate contents of component1
					File comp1Root = new File(loadDir, componentName + 1);
					Assert.assertTrue(comp1Root.exists());
					Assert.assertTrue(comp1Root.isDirectory());
					File fComp1 = new File(comp1Root, "f-comp1");
					Assert.assertTrue(fComp1.exists());
					Assert.assertTrue(fComp1.isDirectory());
					Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());

					File hComp1 = new File(comp1Root, "h-comp1");
					Assert.assertTrue(hComp1.exists());
					Assert.assertTrue(hComp1.isDirectory());
					Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

					// Validate contents of component2
					File comp2Root = new File(loadDir, componentName + 2);
					Assert.assertTrue(comp2Root.exists());
					Assert.assertTrue(comp2Root.isDirectory());
					File fComp2 = new File(comp2Root, "f-comp2");
					Assert.assertTrue(fComp2.exists());
					Assert.assertTrue(fComp2.isDirectory());
					Assert.assertTrue(new File(fComp2, "a.txt").exists());

					File gComp2 = new File(comp2Root, "g-comp2");
					Assert.assertTrue(gComp2.exists());
					Assert.assertTrue(gComp2.isDirectory());
					Assert.assertTrue(new File(gComp2, "b.txt").exists());

					File hComp2 = new File(comp2Root, "h-comp2");
					Assert.assertTrue(hComp2.exists());
					Assert.assertTrue(hComp2.isDirectory());
					Assert.assertTrue(new File(hComp2, "c.txt").exists());

					RTCChangeLogParser parser = new RTCChangeLogParser();
					FileReader changeLogReader = new FileReader(changeLogFile);
					RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

					// verify the result
					int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
					Assert.assertEquals(0, changeCount);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}	
				
				// Scenario#4
				// set loadPolicy to useComponentLoadConfig and componentLoadConfig to loadAllComponents
				// set createFoldersForComponents to true
				// set componentsToExclude
				// set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					loadOptions.createFoldersForComponents = true;
					loadOptions.componentsToExclude = componentName + 2;
					loadOptions.pathToLoadRuleFile = componentName + "1/h-comp1/new.loadRule";
					loadOptions.loadPolicy = RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG;
					loadOptions.componentLoadConfig = RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS;
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
					
					String[] children = loadDir.list();
					Assert.assertEquals(3, children.length); // all contents of component1 and component2 + metadata
					// Validate contents of component1
					File comp1Root = new File(loadDir, componentName + 1);
					Assert.assertTrue(comp1Root.exists());
					Assert.assertTrue(comp1Root.isDirectory());
					File fComp1 = new File(comp1Root, "f-comp1");
					Assert.assertTrue(fComp1.exists());
					Assert.assertTrue(fComp1.isDirectory());
					Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());

					File hComp1 = new File(comp1Root, "h-comp1");
					Assert.assertTrue(hComp1.exists());
					Assert.assertTrue(hComp1.isDirectory());
					Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

					// Validate contents of component2
					File comp2Root = new File(loadDir, componentName + 2);
					Assert.assertTrue(comp2Root.exists());
					Assert.assertTrue(comp2Root.isDirectory());
					File fComp2 = new File(comp2Root, "f-comp2");
					Assert.assertTrue(fComp2.exists());
					Assert.assertTrue(fComp2.isDirectory());
					Assert.assertTrue(new File(fComp2, "a.txt").exists());

					File gComp2 = new File(comp2Root, "g-comp2");
					Assert.assertTrue(gComp2.exists());
					Assert.assertTrue(gComp2.isDirectory());
					Assert.assertTrue(new File(gComp2, "b.txt").exists());

					File hComp2 = new File(comp2Root, "h-comp2");
					Assert.assertTrue(hComp2.exists());
					Assert.assertTrue(hComp2.isDirectory());
					Assert.assertTrue(new File(hComp2, "c.txt").exists());

					RTCChangeLogParser parser = new RTCChangeLogParser();
					FileReader changeLogReader = new FileReader(changeLogFile);
					RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

					// verify the result
					int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
					Assert.assertEquals(0, changeCount);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}	
				
				// Scenario#5
				// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
				// set createFoldersForComponents to true
				// set componentsToExclude
				// set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					loadOptions.createFoldersForComponents = true;
					loadOptions.componentsToExclude = componentName + 2;
					loadOptions.pathToLoadRuleFile = componentName + "1/h-comp1/new.loadRule";
					loadOptions.loadPolicy = RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG;
					loadOptions.componentLoadConfig = RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS;
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
					
					String[] children = loadDir.list();
					Assert.assertEquals(2, children.length); // all contents of component1 + metadata
					// Validate contents of component1
					File comp1Root = new File(loadDir, componentName + 1);
					Assert.assertTrue(comp1Root.exists());
					Assert.assertTrue(comp1Root.isDirectory());
					File fComp1 = new File(comp1Root, "f-comp1");
					Assert.assertTrue(fComp1.exists());
					Assert.assertTrue(fComp1.isDirectory());
					Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
					
					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					
					File hComp1 = new File(comp1Root, "h-comp1");
					Assert.assertTrue(hComp1.exists());
					Assert.assertTrue(hComp1.isDirectory());
					Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

					RTCChangeLogParser parser = new RTCChangeLogParser();
					FileReader changeLogReader = new FileReader(changeLogFile);
					RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

					// verify the result
					int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
					Assert.assertEquals(0, changeCount);
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}	
				
				// Scenario#5
				// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
				// set createFoldersForComponents to true
				// set componentsToExclude to duplicateComponentName
				// set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir1");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					loadOptions.createFoldersForComponents = true;
					loadOptions.componentsToExclude = componentName + 3;
					loadOptions.pathToLoadRuleFile = componentName + "1/h-comp1/new.loadRule";
					loadOptions.loadPolicy = RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG;
					loadOptions.componentLoadConfig = RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS;
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
				} catch (Exception e) {
					if ("RTCConfigurationException".equals(e.getClass().getSimpleName())) {
						Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("More than one component with name"));
					} else {
						e.printStackTrace();
						Assert.fail("Exception not expected: " + e.getMessage());
					}
				}
				
				// Scenario#7
				// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
				// set createFoldersForComponents to true
				// set componentsToExclude
				// set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(sandboxDir, "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					loadOptions.createFoldersForComponents = true;
					loadOptions.componentsToExclude = componentName + 2;
					loadOptions.pathToLoadRuleFile = componentName + "1/h-comp1/new.loadRule";
					loadOptions.loadPolicy = RTCScm.LOAD_POLICY_USE_LOAD_RULES;
					loadOptions.componentLoadConfig = RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS;
					Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, null, streamName,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
					
					String[] children = loadDir.list();
					Assert.assertEquals(2, children.length); // just what the load rule says to load (children of f_comp1) + metadata
					Assert.assertTrue(new File(loadDir, "a-comp1.txt").exists());
					
					RTCChangeLogParser parser = new RTCChangeLogParser();
					FileReader changeLogReader = new FileReader(changeLogFile);
					RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

					// verify the result
					int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
					Assert.assertEquals(0, changeCount);
				} catch (Exception e) {
					// above build toolkit as the interface to drive the load exclusively with load rules was introduced
					// only in 603. So the load operation would fail with an RTCConfigurationException
					if (Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit"))
							&& "RTCConfigurationException".equals(e.getClass().getSimpleName())) {
						Assert.assertTrue(e.getMessage().startsWith("Please check the version of the build toolkit"));
					} else {
						e.printStackTrace();
						Assert.fail("Exception not expected: " + e.getMessage());
					}
				}	
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// clean up
				testingFacade.invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

}
