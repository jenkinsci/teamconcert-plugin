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

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

import junit.framework.Assert;

import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.tests.utils.FileUtils;

public class BuildConfigurationIT extends HudsonTestCase {
	private static final String ARTIFACT_WORKSPACE_ITEM_ID = "workspaceItemId";
	private static final String ARTIFACT_STREAM_ITEM_ID = "streamItemId";
	private static final String ARTIFACT_COMPONENT1_ITEM_ID = "component1ItemId";

	private RTCFacadeWrapper testingFacade;
	private File sandboxDir;

	@Override
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {
			// DO NOT initialize Hudson/Jenkins because its slow and we don't need it for the tests
			
			testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
			
	        File tempDir = new File(System.getProperty("java.io.tmpdir"));
	        File buildTestDir = new File(tempDir, "HJPluginTests");
	        sandboxDir = new File(buildTestDir, getTestName());
	        sandboxDir.mkdirs();
	        sandboxDir.deleteOnExit();
	        Assert.assertTrue(sandboxDir.exists());
		}
	}

	@Override
	public void tearDown() throws Exception {
		// delete the sandbox after Hudson/Jenkins is shutdown
		if (Config.DEFAULT.isConfigured()) {
			// Nothing to do including no need to shutdown Hudson/Jenkins
			FileUtils.delete(sandboxDir);
		}
	}
	
	public void testComponentLoading() throws Exception {
		// Test relative location for fetch destination
		// Test create folders for components
		// Test include/exclude components from the load
		
		// setup build request & verify BuildConfiguration
		// checkout & verify contents on disk
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String testName = getTestName() + System.currentTimeMillis();
			String fetchLocation = "path\\relative";

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("testComponentLoading",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), testName,
							getTestName(),
							sandboxDir.getPath(),
							fetchLocation);
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				String password = (String) testingFacade.invoke(
						"determinePassword", 
						new Class[] {
								String.class, // password
								File.class, // password file
								Locale.class // clientLocale
						},
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Locale.getDefault());

				// checkout the changes
				Map<String, String> buildProperties = (Map<String, String>) testingFacade.invoke(
						"checkout",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildResultUUID,
								String.class, // workspaceName,
								String.class, // hjWorkspacePath,
								OutputStream.class, // changeLog,
								String.class, // baselineSetName,
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						password,
						Config.DEFAULT.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = sandboxDir.list();
				Assert.assertEquals(2, children.length); // changelog plus what we loaded
				File actualRoot = new File(sandboxDir, "path\\relative");
				Assert.assertTrue(actualRoot.exists());
				children = actualRoot.list();
				assertEquals(2, children.length); // metadata plus component root folder
				File shareRoot = new File(actualRoot, getTestName());
				Assert.assertTrue(shareRoot.exists());
				Assert.assertTrue(new File(shareRoot, "f").exists());
				Assert.assertTrue(new File(shareRoot, "f/a.txt").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID),
						fetchLocation, false, true, "",
						true, setupArtifacts.get(ARTIFACT_COMPONENT1_ITEM_ID), result.getBaselineSetItemId(),
						changeCount,
						true,
						buildProperties);
				
			} finally {
				// clean up
				testingFacade.invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								File.class, // passwordFile,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(), passwordFileFile,
						Config.DEFAULT.getTimeout(), setupArtifacts);
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
			Map<String, String> properties) {

	    final String PROPERTY_WORKSPACE_UUID = "team_scm_workspaceUUID"; //$NON-NLS-1$
	    final String PROPERTY_FETCH_DESTINATION = "team_scm_fetchDestination"; //$NON-NLS-1$
	    final String PROPERTY_DELETE_DESTINATION_BEFORE_FETCH = "team_scm_deleteDestinationBeforeFetch"; //$NON-NLS-1$
	    final String PROPERTY_ACCEPT_BEFORE_FETCH = "team_scm_acceptBeforeFetch"; //$NON-NLS-1$
	    final String PROPERTY_BUILD_ONLY_IF_CHANGES = "team_scm_buildOnlyIfChanges"; //$NON-NLS-1$
	    final String PROPERTY_COMPONENT_LOAD_RULES = "team_scm_componentLoadRules"; //$NON-NLS-1$
	    final String PROPERTY_INCLUDE_COMPONENTS = "team_scm_includeComponents"; //$NON-NLS-1$
	    final String PROPERTY_LOAD_COMPONENTS = "team_scm_loadComponents"; //$NON-NLS-1$
	    final String PROPERTY_SNAPSHOT_UUID = "team_scm_snapshotUUID"; //$NON-NLS-1$
	    final String PROPERTY_CHANGES_ACCEPTED = "team_scm_changesAccepted"; //$NON-NLS-1$
	    final String PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS = "team_scm_createFoldersForComponents"; //$NON-NLS-1$
	    
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
		
	}

	public void testBadFetchLocation() throws Exception {
		// load directory bad 
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String testName = getTestName() + System.currentTimeMillis();
			String fetchLocation = "C:\\This\\Should\\Be\\Relative";

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("testBadFetchLocation",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), testName,
							getTestName(),
							sandboxDir.getPath(),
							fetchLocation);
			
			// clean up
			testingFacade.invoke(
					"tearDown",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							File.class, // passwordFile,
							int.class, // timeout,
							Map.class}, // setupArtifacts
					Config.DEFAULT.getServerURI(),
					Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(), passwordFileFile,
					Config.DEFAULT.getTimeout(), setupArtifacts);
		}
	}

	public void testPersonalBuild() throws Exception {
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
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String testName = getTestName() + System.currentTimeMillis();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("testPersonalBuild",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), testName,
							getTestName(),
							sandboxDir.getPath(),
							"${propertyA}/here");
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				String password = (String) testingFacade.invoke(
						"determinePassword", 
						new Class[] {
								String.class, // password
								File.class, // password file
								Locale.class // clientLocale
						},
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Locale.getDefault());

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(sandboxDir, "loadDir/here");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = (Map<String, String>) testingFacade.invoke(
						"checkout",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildResultUUID,
								String.class, // workspaceName,
								String.class, // hjWorkspacePath,
								OutputStream.class, // changeLog,
								String.class, // baselineSetName,
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						password,
						Config.DEFAULT.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						sandboxDir.getCanonicalPath(), changeLog,
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
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		Assert.assertEquals(0, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_STREAM_ITEM_ID),
						"loadDir/here", false, true, setupArtifacts.get("LoadRuleProperty"),
						false, "", result.getBaselineSetItemId(),
						changeCount,
						false,
						buildProperties);
				
				// propertyA = loadDir
				// propertyB = a place (${propertyA}) to load some stuff 
				// propertyC = original
				Assert.assertEquals("loadDir", buildProperties.get("propertyA"));
				Assert.assertEquals("a place (loadDir) to load some stuff", buildProperties.get("propertyB"));
				Assert.assertEquals("overwritten", buildProperties.get("propertyC"));
			} finally {
				// clean up
				testingFacade.invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								File.class, // passwordFile,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(), passwordFileFile,
						Config.DEFAULT.getTimeout(), setupArtifacts);
			}
		}
	}

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
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String testName = getTestName() + System.currentTimeMillis();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("testOldLoadRules",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class}, // hjPath,
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), testName,
							getTestName(),
							sandboxDir.getPath());
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				String password = (String) testingFacade.invoke(
						"determinePassword", 
						new Class[] {
								String.class, // password
								File.class, // password file
								Locale.class // clientLocale
						},
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Locale.getDefault());

				assertTrue(new File(sandboxDir, "abc").mkdirs());
				assertTrue(new File(sandboxDir, "def").mkdirs());
				assertTrue(new File(sandboxDir, "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = (Map<String, String>) testingFacade.invoke(
						"checkout",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildResultUUID,
								String.class, // workspaceName,
								String.class, // hjWorkspacePath,
								OutputStream.class, // changeLog,
								String.class, // baselineSetName,
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						password,
						Config.DEFAULT.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = sandboxDir.list();
				Assert.assertEquals(6, children.length); // change log + 3 dirs made + metadata + file loaded
				Assert.assertTrue(new File(sandboxDir, "a.txt").exists());
				Assert.assertFalse(new File(sandboxDir, "h.txt").exists());
				Assert.assertTrue(new File(sandboxDir, "abc").exists());
				Assert.assertTrue(new File(sandboxDir, "def").exists());
				Assert.assertTrue(new File(sandboxDir, "hij").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
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
						false,
						buildProperties);
				
			} finally {
				// clean up
				testingFacade.invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								File.class, // passwordFile,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(), passwordFileFile,
						Config.DEFAULT.getTimeout(), setupArtifacts);
			}
		}
	}

	public void testNewLoadRules() throws Exception {
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
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String testName = getTestName() + System.currentTimeMillis();
			String fetchLocation = ".";

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("testNewLoadRules",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName,
									String.class, // hjPath,
									String.class}, // buildPath
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), testName,
							getTestName(),
							sandboxDir.getPath(),
							fetchLocation);
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				String password = (String) testingFacade.invoke(
						"determinePassword", 
						new Class[] {
								String.class, // password
								File.class, // password file
								Locale.class // clientLocale
						},
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Locale.getDefault());

				// put extraneous stuff in the load directory (which is different from sandbox cause
				// we want to get a the change log.
				File loadDir = new File(sandboxDir, "loadDir");
				assertTrue(loadDir.mkdirs());
				assertTrue(new File(loadDir, "abc").mkdirs());
				assertTrue(new File(loadDir, "def").mkdirs());
				assertTrue(new File(loadDir, "hij").mkdirs());
				
				// checkout the changes
				Map<String, String> buildProperties = (Map<String, String>) testingFacade.invoke(
						"checkout",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildResultUUID,
								String.class, // workspaceName,
								String.class, // hjWorkspacePath,
								OutputStream.class, // changeLog,
								String.class, // baselineSetName,
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						password,
						Config.DEFAULT.getTimeout(),
						setupArtifacts.get("buildResultItemId"), null,
						loadDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());
	    		
				String[] children = loadDir.list();
				Assert.assertEquals(3, children.length); // just what the load rule says to load (children of f) + metadata
				Assert.assertTrue(new File(loadDir, "a.txt").exists());
				Assert.assertTrue(new File(loadDir, "h.txt").exists());
				
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		int changeCount = result.getComponentChangeCount() +
	    				result.getChangeSetsAcceptedCount() + result.getChangeSetsDiscardedCount();
	    		Assert.assertEquals(1, changeCount);

				validateBuildProperties(setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID),
						fetchLocation, true, true, setupArtifacts.get("LoadRuleProperty"),
						false, "", result.getBaselineSetItemId(),
						changeCount,
						false,
						buildProperties);
				
			} finally {
				// clean up
				testingFacade.invoke(
						"tearDown",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								File.class, // passwordFile,
								int.class, // timeout,
								Map.class}, // setupArtifacts
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(), passwordFileFile,
						Config.DEFAULT.getTimeout(), setupArtifacts);
			}
		}
	}

    /**
     * generate the name of the project based on the test case
     * 
     * @return Name of the project
     */
    protected String getTestName() {
        String name = this.getClass().getName();
        int posn = name.lastIndexOf('.');
        if (posn != -1 && posn < name.length()-1) {
            name = name.substring(posn + 1);
        }
        return name + "_" + this.getName();
    }
}
