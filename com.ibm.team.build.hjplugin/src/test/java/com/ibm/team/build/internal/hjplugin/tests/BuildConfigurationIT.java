/*******************************************************************************
 * Copyright © 2013, 2018 IBM Corporation and others.
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
import hudson.model.TaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.SystemUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.LoadOptions;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

@SuppressWarnings({"nls", "static-method", "boxing"})
public class BuildConfigurationIT extends AbstractTestCase {
	private static final String ARTIFACT_WORKSPACE_ITEM_ID = "workspaceItemId";
	private static final String ARTIFACT_STREAM_ITEM_ID = "streamItemId";
	private static final String ARTIFACT_COMPONENT1_ITEM_ID = "component1ItemId";

	private RTCFacadeWrapper testingFacade;

	@Before
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {
			// DO NOT initialize Hudson/Jenkins because its slow and we don't need it for the tests
			setTestingFacade(Utils.getTestingFacade());
	        createSandboxDirectory();
		}
	}

	@After
	public void tearDown() throws Exception {
		// delete the sandbox after Hudson/Jenkins is shutdown
		if (Config.DEFAULT.isConfigured()) {
			// Nothing to do including no need to shutdown Hudson/Jenkins
//			tearDownSandboxDirectory();
		}
	}
	
	@Test
	public void testComponentLoading() throws Exception {
		// Test relative location for fetch destination
		// Test create folders for components
		// Test include/exclude components from the load
		
		// setup build request & verify BuildConfiguration
		// checkout & verify contents on disk
		if (Config.DEFAULT.isConfigured()) {
			
			String fetchLocation = "path\\relative";
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testComponentLoading",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), workspaceName,
							componentName,
							getSandboxDir().getPath(),
							fetchLocation);
			
			try {
				TaskListener listener = getTaskListener();
				
				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						getSandboxDir().getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = getSandboxDir().list();
				Assert.assertEquals(2, children.length); // changelog plus what we loaded
				File actualRoot = new File(getSandboxDir(), "path\\relative");
				Assert.assertTrue(actualRoot.exists());
				children = actualRoot.list();
				assertEquals(2, children.length); // metadata plus component root folder
				File shareRoot = new File(actualRoot, componentName);
				Assert.assertTrue(shareRoot.exists());
				Assert.assertTrue(new File(shareRoot, "f").exists());
				Assert.assertTrue(new File(shareRoot, "f/a.txt").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		Assert.assertFalse(result.isPersonalBuild());

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID),
						fetchLocation, false, true, "",
						true, setupArtifacts.get(ARTIFACT_COMPONENT1_ITEM_ID), result.getBaselineSetItemId(),
						changeCount,
						true, null, null,
						buildProperties);
				
			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	private void validateBuildProperties(String workspaceItemId,
			String fetchDestination,
			boolean deleteBeforeFetch,
			boolean acceptBeforeFetch,
			String componentLoadRules,
			boolean includeComponents,
			String loadComponents,
			String snapshotItemId,
			int changesAccepted,
			boolean createFoldersForComponents,
			String loadPolicy,
			String componentLoadConfig,
			Map<String, String> properties) {

	    final String PROPERTY_WORKSPACE_UUID = "team_scm_workspaceUUID"; //$NON-NLS-1$
	    final String PROPERTY_FETCH_DESTINATION = "team_scm_fetchDestination"; //$NON-NLS-1$
	    final String PROPERTY_DELETE_DESTINATION_BEFORE_FETCH = "team_scm_deleteDestinationBeforeFetch"; //$NON-NLS-1$
	    final String PROPERTY_ACCEPT_BEFORE_FETCH = "team_scm_acceptBeforeFetch"; //$NON-NLS-1$
	    final String PROPERTY_COMPONENT_LOAD_RULES = "team_scm_componentLoadRules"; //$NON-NLS-1$
	    final String PROPERTY_INCLUDE_COMPONENTS = "team_scm_includeComponents"; //$NON-NLS-1$
	    final String PROPERTY_LOAD_COMPONENTS = "team_scm_loadComponents"; //$NON-NLS-1$
	    final String PROPERTY_SNAPSHOT_UUID = "team_scm_snapshotUUID"; //$NON-NLS-1$
	    final String PROPERTY_CHANGES_ACCEPTED = "team_scm_changesAccepted"; //$NON-NLS-1$
	    final String PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS = "team_scm_createFoldersForComponents"; //$NON-NLS-1$
	    final String PROPERTY_LOAD_POLICY = "team_scm_loadPolicy"; //$NON-NLS-1$
	    final String PROPERTY_COMPONENT_LOAD_CONFIG = "team_scm_componentLoadConfig"; //$NON-NLS-1$
	    
	    Assert.assertEquals(workspaceItemId, properties.get(PROPERTY_WORKSPACE_UUID));
	    Assert.assertEquals(fetchDestination, properties.get(PROPERTY_FETCH_DESTINATION));
	    Assert.assertEquals(Boolean.toString(deleteBeforeFetch), properties.get(PROPERTY_DELETE_DESTINATION_BEFORE_FETCH));
	    Assert.assertEquals(Boolean.toString(acceptBeforeFetch), properties.get(PROPERTY_ACCEPT_BEFORE_FETCH));
	    Assert.assertEquals(componentLoadRules, properties.get(PROPERTY_COMPONENT_LOAD_RULES));
    	String property = properties.get(PROPERTY_INCLUDE_COMPONENTS);
    	if (property != null) {
	    	Assert.assertEquals(Boolean.toString(includeComponents), properties.get(PROPERTY_INCLUDE_COMPONENTS));
	    } else {
	    	Assert.assertFalse(includeComponents);
	    }
	    Assert.assertEquals(loadComponents, properties.get(PROPERTY_LOAD_COMPONENTS));
	    Assert.assertEquals(snapshotItemId, properties.get(PROPERTY_SNAPSHOT_UUID));
	    Assert.assertEquals((changesAccepted > 0 ? Integer.toString(changesAccepted) : null), properties.get(PROPERTY_CHANGES_ACCEPTED));
	    Assert.assertEquals(Boolean.toString(createFoldersForComponents), properties.get(PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS));
		Assert.assertEquals(loadPolicy, properties.get(PROPERTY_LOAD_POLICY));
		Assert.assertEquals(componentLoadConfig, properties.get(PROPERTY_COMPONENT_LOAD_CONFIG));
	}

	@Test public void testGoodRelativeFetchLocation() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String fetchLocation = "relative\\is\\ok";
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testGoodFetchLocation",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), workspaceName,
							componentName,
							getSandboxDir().getPath(),
							fetchLocation);
			
			// clean up
			getTestingFacade().invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					setupArtifacts);
		}
	}

	@Test public void testGoodAbsoluteFetchLocationWin() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String fetchLocation = "C:\\absolute\\is\\ok\\too";
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testGoodFetchLocation",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), getRepositoryWorkspaceUniqueName(),
							getComponentUniqueName(),
							getSandboxDir().getPath(),
							fetchLocation);
			
			// clean up
			getTestingFacade().invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), setupArtifacts);
		}
	}

	@Test public void testBadFetchLocationWin() throws Exception {
		if (Config.DEFAULT.isConfigured() && SystemUtils.IS_OS_WINDOWS) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String fetchLocation = "invalid/questionmark?/character/is/not/ok";
			String repositoryWorkspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testBadFetchLocation",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), repositoryWorkspaceName,
							componentName,
							getSandboxDir().getPath(),
							fetchLocation);
			
			// clean up
			getTestingFacade().invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), setupArtifacts);
		}
	}

	@Test public void testPersonalBuild() throws Exception {
		// create a build definition
		// load directory ${propertyA}/here
		// build properties
		// myPropsFile = ${team.scm.fetchDestination}/com.ibm.team.build.releng/continuous-buildsystem.properties
		// propertyA = loadDir
		// propertyB = a place (${propertyA}) to load some stuff 
		// propertyC = original
		// using a load rule

		// create a build engine
		// create a build request for this test with personal build specified
		// (build workspace overridden, build engine is a random one, override load rule, override a build property)
		
		// verify that the buildConfiguration returns the personal build workspace
		// as the workspace to be loaded
		// verify the load rule is changed
		// verify the build property is changed
		
		// checkout based on the request
		// make sure the personal build workspace was loaded and the load rule used
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testPersonalBuild",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), workspaceName,
							componentName,
							getSandboxDir().getPath(),
							"${propertyA}/here");
			
			try {
				TaskListener listener = getTaskListener();
				
				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir/here");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), 
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						getSandboxDir().getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = loadDir.list();
				Assert.assertEquals(5, children.length); // just what the load rule says to load (children of f) + metadata
				Assert.assertTrue(new File(loadDir, "b.txt").exists());
				Assert.assertFalse(new File(loadDir, "i.txt").exists());
				assertTrue(new File(loadDir, "abc").exists());
				assertTrue(new File(loadDir, "def").exists());
				assertTrue(new File(loadDir, "hij").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertTrue(result.isPersonalBuild());
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		Assert.assertEquals(0, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_STREAM_ITEM_ID),
						"loadDir/here", false, true, setupArtifacts.get("LoadRuleProperty"),
						false, "", result.getBaselineSetItemId(),
						changeCount,
						false, null, null,
						buildProperties);
				
				// propertyA = loadDir
				// propertyB = a place (${propertyA}) to load some stuff 
				// propertyC = original
				Assert.assertEquals("loadDir", buildProperties.get("propertyA"));
				Assert.assertEquals("a place (loadDir) to load some stuff", buildProperties.get("propertyB"));
				Assert.assertEquals("overwritten", buildProperties.get("propertyC"));
			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	@Test 
	public void testOldLoadRules() throws Exception {
		// Create build definition with old style load rules
		// load directory missing
		// don't delete directory before loading (dir has other stuff that will not be deleted)
		// don't accept changes before loading

		// create a build engine
		// create a build request
		
		// verify that the buildConfiguration returns the load rules
		// as the workspace to be loaded
		
		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff from sandbox directory not deleted
		// verify no changes accepted
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testOldLoadRules",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class}, // hjPath,
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), workspaceName,
							componentName,
							getSandboxDir().getPath());
			
			try {
				TaskListener listener = getTaskListener();
				
				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				assertTrue(new File(getSandboxDir(), "abc").mkdirs());
				assertTrue(new File(getSandboxDir(), "def").mkdirs());
				assertTrue(new File(getSandboxDir(), "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						getSandboxDir().getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = getSandboxDir().list();
				Assert.assertEquals(6, children.length); // change log + 3 dirs made + metadata + file loaded
				Assert.assertTrue(new File(getSandboxDir(), "a.txt").exists());
				Assert.assertFalse(new File(getSandboxDir(), "h.txt").exists());
				Assert.assertTrue(new File(getSandboxDir(), "abc").exists());
				Assert.assertTrue(new File(getSandboxDir(), "def").exists());
				Assert.assertTrue(new File(getSandboxDir(), "hij").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		// no accept done
	    		Assert.assertEquals(0, changeCount);
	    		Assert.assertNull(result.getBaselineSetItemId());

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID),
						null, false, false, setupArtifacts.get("LoadRuleProperty"),
						false, "", result.getBaselineSetItemId(),
						changeCount,
						false, null, null,
						buildProperties);
				
			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}
	
	@Test 
	public void testOldLoadRules_setAllLoadOptions() throws Exception {
		// Create build definition with old style load rules
		// load directory missing
		// don't delete directory before loading (dir has other stuff that will not be deleted)
		// don't accept changes before loading

		// create a build engine
		// create a build request
		
		// verify that the buildConfiguration returns the load rules
		// as the workspace to be loaded
		
		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff from sandbox directory not deleted
		// verify no changes accepted
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testOldLoadRules_setAllLoadOptions",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class}, // hjPath,
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), workspaceName,
							componentName,
							getSandboxDir().getPath());
			
			try {
				TaskListener listener = getTaskListener();
				
				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				
				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						loadDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = loadDir.list();
				Assert.assertEquals(2, children.length); 
				// Validate contents of component1
				File comp1Root = new File(loadDir, componentName + 1);
				Assert.assertTrue(comp1Root.exists());
				Assert.assertTrue(comp1Root.isDirectory());
				
				children = comp1Root.list();
				Assert.assertEquals(2, children.length); // just what the load rules suggested to load
	
				Assert.assertTrue(new File(comp1Root, "a-comp1.txt").exists());
				Assert.assertTrue(new File(comp1Root, "h-comp1.txt").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		// no accept done
	    		Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID),
						null, true, true, setupArtifacts.get("LoadRuleProperty"),
						false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(),
						changeCount,
						true, null, null,
						buildProperties);
				
			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	@Test public void testNewLoadRules() throws Exception {
		// create a build definition with new format load rules
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading

		// create a build engine
		// create a build request
		
		// verify that the buildConfiguration returns the load rules
		// and other settings.
		
		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("testNewLoadRules",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), workspaceName,
							componentName,
							getSandboxDir().getPath(),
							fetchLocation);
			
			try {
				TaskListener listener = getTaskListener();
				
				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						loadDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = loadDir.list();
				Assert.assertEquals(3, children.length); // just what the load rule says to load (children of f) + metadata
				Assert.assertTrue(new File(loadDir, "a.txt").exists());
				Assert.assertTrue(new File(loadDir, "h.txt").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID),
						fetchLocation, true, true, setupArtifacts.get("LoadRuleProperty"),
						false, "", result.getBaselineSetItemId(),
						changeCount,
						false, null, null,
						buildProperties);
				
			} finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_loadRulesWithNoLoadPolicy() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		
		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		
		// test scenarios
		// 1. do not set loadPolicy and specify load rules
		// 2. set loadPolicy to useComponentLoadConfig and specify load rules
		// 3. do not set loadPolicy and do not specify load rules
		
		// Scenario#1
		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			// configure load rules and verify that all components are loaded and the components for which load rules
			// are specified, are loaded according to the load rules
			// do not specify loadPolicy
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_loadRulesWithNoLoadPolicy",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // configureLoadRules
							boolean.class}, // setLoadPolicy
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, false);

			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(6, children.length); // what the load rule says to load from component 1(children of
															// f_comp1) + all contents of component 2 + metadata
				// Validate contents of component1
				Assert.assertTrue(new File(loadDir, "a-comp1.txt").exists());
				Assert.assertTrue(new File(loadDir, "h-comp1.txt").exists());
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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, null, null,
						buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#2
			// configure load rules and verify that all components are loaded and the components for which load rules
			// are specified, are loaded according to the load rules
			// set loadPolicy to useComponentLoadConfig and configure load rules
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_loadRulesWithNoLoadPolicy",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // configureLoadRules
							boolean.class}, // setLoadPolicy
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, true);

			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#3
			// do not set loadPolicy and do not configure load rules and make sure that all contents from all the
			// components are loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_loadRulesWithNoLoadPolicy",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // configureLoadRules
							boolean.class}, // setLoadPolicy
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false, false);

			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, null, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		
		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted
		
		// Tests following scenarios
		// 1. set loadPolicy to useLoadRules and specify load rules
		// 2. set loadPolicy to useLoadRules and do not specify load rules
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			// Scenario#1
			// configure load rules and verify that only those components for which load rules are specified are loaded,
			// according to the specified load rules
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class }, // configureLoadRules
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(3, children.length); // just what the load rule says to load (children of f_comp1) + metadata
				Assert.assertTrue(new File(loadDir, "a-comp1.txt").exists());
				Assert.assertTrue(new File(loadDir, "h-comp1.txt").exists());

				RTCChangeLogParser parser = new RTCChangeLogParser();
				FileReader changeLogReader = new FileReader(changeLogFile);
				RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

				// verify the result
				int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, "useLoadRules", null, buildProperties);

			} catch (Exception e) {
				// when loadPolicy is set to useLoadRules and load rules are specified, we need a 603 or
				// above build toolkit as the interface to drive the load exclusively with load rules was introduced only in
				// 603. So the load operation would fail with an RTCConfigurationException
				if (Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit")) && "RTCConfigurationException".equals(e.getClass().getSimpleName())) {
					Assert.assertTrue(e.getMessage().startsWith("Please check the version of the build toolkit"));
				} else {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}
			}
			finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#2
			// do not configure load rules and make sure that all contents from all the components are loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class }, // configureLoadRules
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false);

			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // what the load rule says to load from component 1(children of
															// f_comp1) + all contents of component 2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, "useLoadRules", null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_createFoldersForComponents() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted

		// Tests following scenarios
		// 1. do not create folders for components and do not set load policy
		// 2. create folders for components and do not set load policy
		// 3. create folders for components and set load policy to useLoadRules, componentLoadConfig to loadAllComponents
		// 4. create folders for components and set load policy to useComponentLoadConfig, do not set componentLoadConfig
		// 5. create folders for components and set load policy to useComponentLoadConfig, componentLoadConfig to excludeSomeComponents
		// 6. create folders for components and set load policy to useComponentLoadConfig, componentLoadConfig to loadAllComponents
		// 7. do not create folders for components and set load policy to useComponentLoadConfig, componentLoadConfig to loadAllComponents
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			// Scenario#1
			// do not create folders for components and do not set load policy
			// component root folder not created in the sandbox
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false, null, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, null, null,
						buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#2
			// create folders for components and do not set load policy
			// component root folder should be created in the sandbox
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, null, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

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
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(comp1Root, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, true, null, null,
						buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#3
			// create folders for components and set load policy to useLoadRules, componentLoadConfig to loadAllComponents
			// component root folder not created in the sandbox
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_LOAD_RULES, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, true,
						RTCScm.LOAD_POLICY_USE_LOAD_RULES, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}	
			
			// Scenario#4
			// create folders for components and set load policy to useComponentLoadConfig, do not set componentLoadConfig
			// component root folder should be created
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

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
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(comp1Root, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, true,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#5
			// create folders for components and set load policy to useComponentLoadConfig, componentLoadConfig to excludeSomeComponents
			// component root folder should be created
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

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
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(comp1Root, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, true,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#6
			// create folders for components and set load policy to useComponentLoadConfig, componentLoadConfig to loadAllComponents
			// component root folder should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

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
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(comp1Root, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, true,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#7
			// do not create folders for components and set load policy to useComponentLoadConfig, componentLoadConfig to loadAllComponents
			// component root folder should not be created in the sandbox
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldCreateFoldersForComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// use load rules to create folders for components

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted

		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			// Scenario#1
			// do not create folders for components and set loadPolicy to useLoadRules
			// component root folder not created in the sandbox
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class }, // buildPath
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(2, children.length); // comp1 according to load rules + metadata
				// Validate contents of component1
				File comp1Root = new File(loadDir, componentName + 1);
				Assert.assertTrue(comp1Root.exists());
				Assert.assertTrue(comp1Root.isDirectory());
				
				children = comp1Root.list();
				Assert.assertEquals(2, children.length); // just what the load rules suggested to load
	
				Assert.assertTrue(new File(comp1Root, "a-comp1.txt").exists());
				Assert.assertTrue(new File(comp1Root, "h-comp1.txt").exists());

				RTCChangeLogParser parser = new RTCChangeLogParser();
				FileReader changeLogReader = new FileReader(changeLogFile);
				RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

				// verify the result
				int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_LOAD_RULES, null, buildProperties);

			} catch (Exception e) {
				// when loadPolicy is set to useLoadRules and load rules are specified, we need a 603
				// or above build toolkit as the interface to drive the load exclusively with load rules was introduced
				// only in 603. So the load operation would fail with an RTCConfigurationException
				if (Boolean.valueOf(setupArtifacts.get("isPre603BuildToolkit")) && "RTCConfigurationException".equals(e.getClass().getSimpleName())) {
					Assert.assertTrue(e.getMessage().startsWith("Please check the version of the build toolkit"));
				} else {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}	
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_componentsToExclude() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted

		// Tests following scenarios
		// 1. do not exclude components and do not set load policy
		// 2. exclude components and do not set load policy
		// 3. exclude components and set load policy to useLoadRules, componentLoadConfig to excludeSomeComponents
		// 4. exclude components and set load policy to useComponentLoadConfig, do not set componentLoadConfig
		// 5. exclude components and set load policy to useComponentLoadConfig, componentLoadConfig to loadAllComponents
		// 6. exclude components and set load policy to useComponentLoadConfig, componentLoadConfig to excludeSomeComponents
		// 7. do not exclude components and set load policy to useComponentLoadConfig, componentLoadConfig to excludeSomeComponents
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			// Scenario#1
			// do not exclude components and do not set load policy
			// all components should be loaded
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false, null, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, null, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#2
			// exclude components and do not set load policy
			// only component 1 should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, null, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(4, children.length); // all contents of component1 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

				File hComp1 = new File(loadDir, "h-comp1");
				Assert.assertTrue(hComp1.exists());
				Assert.assertTrue(hComp1.isDirectory());
				Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

				RTCChangeLogParser parser = new RTCChangeLogParser();
				FileReader changeLogReader = new FileReader(changeLogFile);
				RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

				// verify the result
				int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(),
						changeCount, false, null, null, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#3
			// exclude components and set load policy to useLoadRules, componentLoadConfig to excludeSomeComponents
			// component1 and component2 should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_LOAD_RULES, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_LOAD_RULES, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}	
			
			// Scenario#4
			// exclude components and set load policy to useComponentLoadConfig, do not set componentLoadConfig
			// all components should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#5
			// exclude components and set load policy to useComponentLoadConfig, set componentLoadConfig to loadAllComponents
			// all components should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#6
			// exclude components and set load policy to useComponentLoadConfig, set componentLoadConfig to excludeSomeComponents
			// only component 1 should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(4, children.length); // all contents of component1 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

				File hComp1 = new File(loadDir, "h-comp1");
				Assert.assertTrue(hComp1.exists());
				Assert.assertTrue(hComp1.isDirectory());
				Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

				RTCChangeLogParser parser = new RTCChangeLogParser();
				FileReader changeLogReader = new FileReader(changeLogFile);
				RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

				// verify the result
				int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(),
						changeCount, false, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
			
			// Scenario#7
			// do not exclude components and set load policy to useComponentLoadConfig, set componentLoadConfig to excludeSomeComponents
			// all components should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_componentsToExclude",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // shouldExcludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}			
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_includeComponents() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted

		// Tests following scenarios
		// 1. set includeComponents property to false and do not set load policy, add load
		// components
		// 2. set includeComponents property to true and do not set load policy, add load
		// components
		// 3. set includeComponents property to true and set load policy to useLoadRules
		// 4. set includeComponents property to true and set load policy to useComponentLoadConfig, componentLoadConfig
		// to loadAllComponents
		// 5. set includeComponents property to true and set load policy to useComponentLoadConfig, componentLoadConfig
		// to excludeSomeComponents
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();

			// Scenario#1
			// set includeComponents property to false and do not set load policy, add load
			// components
			// only component1 should be loaded
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_includeComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // addLoadComponents
							String.class, // valueOfIncludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, "false", null, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(4, children.length); // all contents of component1 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

				File hComp1 = new File(loadDir, "h-comp1");
				Assert.assertTrue(hComp1.exists());
				Assert.assertTrue(hComp1.isDirectory());
				Assert.assertTrue(new File(hComp1, "c-comp1.txt").exists());

				RTCChangeLogParser parser = new RTCChangeLogParser();
				FileReader changeLogReader = new FileReader(changeLogFile);
				RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

				// verify the result
				int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(),
						changeCount, false, null, null, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#2
			// set includeComponents property to true and do not set load policy, add load
			// components
			// only component2 should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_includeComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // addLoadComponents
							String.class, // valueOfIncludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, "true", null, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(4, children.length); // all contents of component2 + metadata
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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), true, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(),
						changeCount, false, null, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#3
			// set includeComponents property to true and set load policy to useLoadRules
			// load all components, as we do not set the load rule file
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_includeComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // addLoadComponents
							String.class, // valueOfIncludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, "false", RTCScm.LOAD_POLICY_USE_LOAD_RULES, null);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_LOAD_RULES, null, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#4
			// set includeComponents property to true and set load policy to useComponentLoadConfig, componentLoadConfig
			// to loadAllComponents, add load components
			// all components should be loaded as componentLoadConfig is set to loadAllComponents
			// includeComponents is considered only when componentLoadConfig is not excludeSomeComponents
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_includeComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // addLoadComponents
							String.class, // valueOfIncludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, "true", RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
					RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				
				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
				// Validate contents of component1
				File fComp1 = new File(loadDir, "f-comp1");
				Assert.assertTrue(fComp1.exists());
				Assert.assertTrue(fComp1.isDirectory());
				Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
				Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

				File gComp1 = new File(loadDir, "g-comp1");
				Assert.assertTrue(gComp1.exists());
				Assert.assertTrue(gComp1.isDirectory());
				Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
				Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), true, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(), changeCount, false,
						RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, buildProperties);
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#5
			// set includeComponents property to true and set load policy to useComponentLoadConfig, componentLoadConfig
			// to excludeSomeComponents, add load components
			// only component2 should be loaded
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();
			fetchLocation = ".";

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_includeComponents",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class, // addLoadComponents
							String.class, // valueOfIncludeComponents
							String.class, // loadPolicy
							String.class }, // componentLoadConfig
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true, "true", RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(4, children.length); // all contents of component2 + metadata
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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), true, setupArtifacts.get("component2ItemId"), result.getBaselineSetItemId(),
						changeCount, false, RTCScm.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, RTCScm.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS,
						buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_multipleLoadRuleFiles() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add multiple load rules files
		
		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted
		
		// Tests following scenarios
		// 1. do not set load policy
		// 2. set load policy to useLoadRules
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			// Scenario#1
			// do not set loadPolicy, files loaded according to load rules
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_multipleLoadRuleFiles",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class }, // setLoadPolicyToUseLoadRules
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(4, children.length); // just what the load rule says to load (children of f_comp1 and f_comp2) + metadata
				Assert.assertTrue(new File(loadDir, "a-comp1.txt").exists());
				Assert.assertTrue(new File(loadDir, "h-comp1.txt").exists());
				Assert.assertTrue(new File(loadDir, "a.txt").exists());

				RTCChangeLogParser parser = new RTCChangeLogParser();
				FileReader changeLogReader = new FileReader(changeLogFile);
				RTCChangeLogSet result = (RTCChangeLogSet)parser.parse(null, null, changeLogReader);

				// verify the result
				int changeCount = result.getComponentChangeCount() + result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, null, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#2
			// set loadPolicy to useLoadRules
			// Fail with exception as multiple load rule files not supported
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_multipleLoadRuleFiles",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class }, // setLoadPolicyToUseLoadRules
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true);

			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				Assert.fail("Exception Expected");

			} catch (Exception e) {
				if ("RTCConfigurationException".equals(e.getClass().getSimpleName())) {
					Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Multiple load rule files, one per component, is not supported"));
				} else {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBuildDefinitionConfig_oldLoadRulesFormat() throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add load rules in old format
		
		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted
		
		// Tests following scenarios
		// 1. do not set loadPolicy
		// 2. set loadPolicy to useLoadRules
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			// Scenario#1
			// do not set loadPolicy, files loaded according to load rules
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";

			Map<String, String> setupArtifacts = (Map<String, String>)getTestingFacade().invoke(
					"testBuildDefinitionConfig_oldLoadRulesFormat",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class }, // setLoadPolicyToUseLoadRules
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, false);
			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Map<String, String> buildProperties = Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				String[] children = loadDir.list();
				Assert.assertEquals(6, children.length); // contents of component 2 + what the load rule says to load for component1 (children of f_comp1) + metadata
				Assert.assertTrue(new File(loadDir, "a-comp1.txt").exists());
				Assert.assertTrue(new File(loadDir, "h-comp1.txt").exists());
				
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
				Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID), fetchLocation, true, true,
						setupArtifacts.get("LoadRuleProperty"), false, "", result.getBaselineSetItemId(), changeCount, false, null, null, buildProperties);

			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}

			// Scenario#2
			// set loadPolicy to useLoadRules to true, should fail as line oriented format is not supported
			workspaceName = getRepositoryWorkspaceUniqueName();
			componentName = getComponentUniqueName();

			setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testBuildDefinitionConfig_oldLoadRulesFormat",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentName,
							String.class, // hjPath,
							String.class, // buildPath
							boolean.class }, // setLoadPolicyToUseLoadRules
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName, componentName,
					getSandboxDir().getPath(), fetchLocation, true);

			try {
				TaskListener listener = getTaskListener();

				File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(getSandboxDir(), "loadDir");
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());

				// checkout the changes
				Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
						loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts.get("buildResultItemId"), null, loadDir.getCanonicalPath(),
						changeLog, "Snapshot", listener, Locale.getDefault());

				Assert.fail("Exception Expected");

			} catch (Exception e) {
				if ("RTCConfigurationException".equals(e.getClass().getSimpleName())) {
					Assert.assertTrue(e.getMessage().startsWith("Load rules are not provided in XML format."));
				} else {
					e.printStackTrace();
					Assert.fail("Exception not expected: " + e.getMessage());
				}
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testRepositoryWorkspaceConfig_loadPolicy() throws Exception {
		// create a repository workspace with a valid load rule file and multiple components

		// verify that the buildConfiguration returns the load rules
		// and other settings.

		// checkout based on the request
		// make sure only the contents identified by the load rule was loaded
		// verify extra stuff deleted from sandbox directory
		// verify changes accepted

		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			Map<String, String> setupArtifacts = null;

			// Set loadPolicy and componentLoadConfig to different values and validate that buildConfiguration instance
			// is
			// initialized as expected
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String fetchLocation = ".";
			try {
				setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testRepositoryWorkspaceConfig_loadPolicy",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName,
								String.class, // hjPath,
								String.class }, // buildPath
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName,
						componentName, getSandboxDir().getPath(), fetchLocation);
				// Scenario#1
				// do not set loadPolicy and componentLoadConfig
				// set createFoldersForComponents to false
				// do not set componentsToExclude
				// do not set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());

					String[] children = loadDir.list();
					Assert.assertEquals(7, children.length); // all contents of component1 and component2 + metadata
					// Validate contents of component1
					File fComp1 = new File(loadDir, "f-comp1");
					Assert.assertTrue(fComp1.exists());
					Assert.assertTrue(fComp1.isDirectory());
					Assert.assertTrue(new File(fComp1, "a-comp1.txt").exists());
					Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

					File gComp1 = new File(loadDir, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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
					Assert.assertEquals(1, changeCount);
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
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
					Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
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
					Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
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
					Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
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
					Assert.assertTrue(new File(fComp1, "h-comp1.txt").exists());

					File gComp1 = new File(comp1Root, "g-comp1");
					Assert.assertTrue(gComp1.exists());
					Assert.assertTrue(gComp1.isDirectory());
					Assert.assertTrue(new File(gComp1, "b-comp1.txt").exists());
					Assert.assertTrue(new File(gComp1, "i-comp1.txt").exists());

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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir1");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, workspaceName, null, null,
							loadDir.getCanonicalPath(), changeLog, "Snapshot", null, loadOptions, listener, Locale.getDefault());
					
					String[] children = loadDir.list();
					Assert.assertEquals(3, children.length); // just what the load rule says to load (children of f_comp1) + metadata
					Assert.assertTrue(new File(loadDir, "a-comp1.txt").exists());
					Assert.assertTrue(new File(loadDir, "h-comp1.txt").exists());

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
				Assert.fail("Exception not expected: " + e.getMessage());
			} finally {
				// clean up
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}
	

	@SuppressWarnings("unchecked")
	@Test
	public void testSnapshotConfig_loadPolicy() throws Exception {
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
				// Scenario#1
				// do not set team.scm.deprecateObsoleteLoadOptions, files loaded according to load rules
				String workspaceName = getRepositoryWorkspaceUniqueName();
				String componentName = getComponentUniqueName();
				String fetchLocation = ".";
				String snapshotName = workspaceName + "_lrSS";

				setupArtifacts = (Map<String, String>)getTestingFacade().invoke("testSnapshotConfig_loadPolicy",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName,
								String.class, // hjPath,
								String.class }, // buildPath
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), workspaceName,
						componentName, getSandboxDir().getPath(), fetchLocation);
				// Scenario#1
				// do not set loadPolicy and componentLoadConfig
				// set createFoldersForComponents to false
				// do not set componentsToExclude
				// do not set loadRules
				try {
					TaskListener listener = getTaskListener();

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
					assertTrue(new File(loadDir, "abc").mkdirs());
					assertTrue(new File(loadDir, "def").mkdirs());
					assertTrue(new File(loadDir, "hij").mkdirs());

					// checkout the changes
					LoadOptions loadOptions = new LoadOptions();
					loadOptions.acceptBeforeLoad = true;
					loadOptions.isDeleteNeeded = true;
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir1");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null,
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

					File changeLogFile = new File(getSandboxDir(), "RTCChangeLogFile");
					FileOutputStream changeLog = new FileOutputStream(changeLogFile);

					// put extraneous stuff in the load directory (which is different from sandbox cause
					// we want to get a the change log.
					File loadDir = new File(getSandboxDir(), "loadDir");
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
					Utils.acceptAndLoad(getTestingFacade(), loginInfo.getServerUri(), loginInfo.getUserId(),
							loginInfo.getPassword(), loginInfo.getTimeout(), null, null, snapshotName, null, 
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
				getTestingFacade().invoke("tearDown", new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class }, // setupArtifacts
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test public void testBuildSnapshotConfiguration() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = null;
			Map<String, String> setupArtifacts = null;
			try {

			loginInfo = Config.DEFAULT.getLoginInfo();
			String workspaceName = getRepositoryWorkspaceUniqueName();
			String componentName = getComponentUniqueName();
			String snapshotName = getSnapshotUniqueName();
			
			setupArtifacts = (Map<String, String>) getTestingFacade()
					.invoke("setupBuildSnapshot",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // snapshotName,
									String.class, // componentName,
									String.class, // workspacePrefix
					      },
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), 
							workspaceName,
							snapshotName,
							componentName,
							"HJP"
							);
			
			getTestingFacade().invoke("testBuildSnapshotConfiguration",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // snapshotName,
									String.class, // workspacePrefix
									String.class, // hjPath
					      },
							loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(), 
							snapshotName,
							"HJP",
							getSandboxDir().getPath());
			}
			finally {
				// clean up
				getTestingFacade().invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), setupArtifacts);
			}
		}
	}

	private RTCFacadeWrapper getTestingFacade() {
		return this.testingFacade;
	}
	
	private void setTestingFacade(RTCFacadeWrapper testingFacade) {
		this.testingFacade = testingFacade;
	}
}
