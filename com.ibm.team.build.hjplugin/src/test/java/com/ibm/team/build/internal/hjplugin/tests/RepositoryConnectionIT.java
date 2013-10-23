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
import hudson.scm.EditType;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.ChangeDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogComponentEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet.ComponentDescriptor;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.tests.utils.FileUtils;

@SuppressWarnings("nls")
public class RepositoryConnectionIT extends HudsonTestCase {

	private RTCFacadeWrapper testingFacade;
	private File sandboxDir;

	@Override
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {
			super.setUp();
			
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
			super.tearDown();
			FileUtils.delete(sandboxDir);
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
	
    /**
     * Tests that component additions and removals are reported properly
     * @throws Exception
     */
	public void testComponentChanges() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentAddedName = getTestName() + "_added";
			String componentDroppedName = getTestName() + "_dropped";

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupComponentChanges",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentAddedName,
									String.class}, // componentDroppedName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentAddedName, componentDroppedName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						(String) null, listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNull(result.getBaselineSetItemId());
	    		Assert.assertNull(result.getBaselineSetName());
	    		Assert.assertEquals(2, result.getComponentChangeCount());
	    		String componentAdded = "";
	    		String componentDropped = "";
	    		for (RTCChangeLogComponentEntry componentChange : result.getComponentChanges()) {
	    			if (componentChange.getActionType().getName().equals(EditType.ADD.getName())) {
	    				assertEquals(componentAddedName, componentChange.getName());
	    				componentAdded = componentChange.getItemId();
	    			} else {
	    				assertEquals(componentDroppedName, componentChange.getName());
	    				componentDropped = componentChange.getItemId();
	    			}
	    		}
	    		Assert.assertEquals(0, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(0, result.getChangeSetsDiscardedCount());

	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		Assert.assertEquals(setupArtifacts.get("componentAddedItemId"), componentAdded);
	    		Assert.assertEquals(setupArtifacts.get("componentDroppedItemId"), componentDropped);
	    		
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
	 * Test that both change sets accepted & discarded are reported properly
	 * With discarded change sets the path may not always be able to be fully resolved.
	 * @throws Exception
	 */
	public void testAcceptDiscardChanges() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupAcceptDiscardChanges",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				Map<String, String> properties = (Map<String, String>) testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		String snapShotItemId = properties.get("team_scm_snapshotUUID");
	    		Assert.assertEquals(result.getBaselineSetItemId(), snapShotItemId);
	    		Assert.assertEquals("Snapshot", result.getBaselineSetName());
	    		Assert.assertEquals(0, result.getComponentChangeCount());
	    		Assert.assertEquals(6, result.getChangeSetsDiscardedCount());
	    		Assert.assertEquals(2, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		String componentItemId = setupArtifacts.get("component1ItemId");
	    		
	    		// validate accepted change sets
				validateCS1(result.getChangeSetsAccepted(componentItemId).get(0), setupArtifacts, componentName, componentItemId, false);
				validateCS2(result.getChangeSetsAccepted(componentItemId).get(1), setupArtifacts, componentName, componentItemId);
	    		
				// validate discarded change sets
				validateCS3(result.getChangeSetsDiscarded(componentItemId).get(0), setupArtifacts, componentName, componentItemId, true);
				validateCS4(result.getChangeSetsDiscarded(componentItemId).get(1), setupArtifacts, componentItemId, componentName);
				validateEmptyChangeSet(result.getChangeSetsDiscarded(componentItemId).get(2), setupArtifacts, componentItemId, componentName, "cs5");
				validateNoopSuspendChangeSet(result.getChangeSetsDiscarded(componentItemId).get(3), setupArtifacts, componentItemId, componentName, "cs6");
				validateNoopChangeSet(result.getChangeSetsDiscarded(componentItemId).get(4), setupArtifacts, componentName, componentItemId, "cs7", true);
				validateComponentRootChangeSet(result.getChangeSetsDiscarded(componentItemId).get(5), setupArtifacts, componentItemId, componentName, "cs8");

				// There should not be incoming changes any more
	    		changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertFalse(changesIncoming.booleanValue());
	    		
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
	 * Ensures that different types of changes can be accepted and reported properly
	 * Edge cases like the component root folder, empty change sets, change sets with 
	 * the same before/after states (possibly null) are tested as well as the standard
	 * add, mod, move, delete. As well change sets with 1 work item and multiple work items
	 * associated with them are tested.
	 * @throws Exception
	 */
	public void testAcceptChanges() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupAcceptChanges",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}

			try {
				
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		Assert.assertEquals("Snapshot", result.getBaselineSetName());
	    		Assert.assertEquals(0, result.getComponentChangeCount());
	    		Assert.assertEquals(0, result.getChangeSetsDiscardedCount());
	    		Assert.assertEquals(8, result.getChangeSetsAcceptedCount());
	    		String componentItemId = setupArtifacts.get("component1ItemId");
				validateCS1(result.getChangeSetsAccepted(componentItemId).get(0), setupArtifacts, componentName, componentItemId, false);
				validateCS2(result.getChangeSetsAccepted(componentItemId).get(1), setupArtifacts, componentName, componentItemId);
				validateCS3(result.getChangeSetsAccepted(componentItemId).get(2), setupArtifacts, componentName, componentItemId, false);
				validateCS4(result.getChangeSetsAccepted(componentItemId).get(3), setupArtifacts, componentItemId, componentName);
				validateEmptyChangeSet(result.getChangeSetsAccepted(componentItemId).get(4), setupArtifacts, componentItemId, componentName, "cs5");
				// suspended cs resumed for conflict
				validateNoopSuspendChangeSet(result.getChangeSetsAccepted(componentItemId).get(5), setupArtifacts, componentItemId, componentName, "cs6");
				validateNoopChangeSet(result.getChangeSetsAccepted(componentItemId).get(6), setupArtifacts, componentName, componentItemId, "cs7", false);
				validateComponentRootChangeSet(result.getChangeSetsAccepted(componentItemId).get(7), setupArtifacts, componentItemId, componentName, "cs8");

	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());

	    		// ensure that there are no incoming changes are detected
				changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertFalse(changesIncoming.booleanValue());
	    		
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
	 * Verify that an empty change set on its own will not cause problems
	 * @throws Exception
	 */
	public void testEmptyChangeSet() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupEmptyChangeSets",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}

			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		Assert.assertEquals("Snapshot", result.getBaselineSetName());
	    		Assert.assertEquals(0, result.getComponentChangeCount());
	    		Assert.assertEquals(1, result.getChangeSetsDiscardedCount());
	    		Assert.assertEquals(2, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		String componentItemId = setupArtifacts.get("component1ItemId");
    			validateEmptyChangeSet(result.getChangeSetsAccepted(componentItemId).get(0), setupArtifacts, componentItemId, componentName, "cs1");
    			validateEmptyChangeSet(result.getChangeSetsAccepted(componentItemId).get(1), setupArtifacts, componentItemId, componentName, "cs2");
	    		validateEmptyChangeSet(result.getChangeSetsDiscarded(componentItemId).get(0), setupArtifacts, componentItemId, componentName, "cs3");
	    		
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
	 * Verify that a change set with same before/after states (possibly null) on its own
	 * does not cause problems.
	 * @throws Exception
	 */
	public void testNoopChange() throws Exception {
		// add & delete versionable in the cs
		// mod & restore versionable in the cs
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupNoopChanges",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}
			
			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		Assert.assertEquals("Snapshot", result.getBaselineSetName());
	    		Assert.assertEquals(0, result.getComponentChangeCount());
	    		Assert.assertEquals(0, result.getChangeSetsDiscardedCount());
	    		Assert.assertEquals(2, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		String componentItemId = setupArtifacts.get("component1ItemId");
	    		validateNoopSuspendChangeSet(result.getChangeSetsAccepted(componentItemId).get(0), setupArtifacts, componentItemId, componentName, "cs1");
   				validateNoopChangeSet(result.getChangeSetsAccepted(componentItemId).get(1), setupArtifacts, componentName, componentItemId, "cs2", false);
	    		
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
	 * Test the modification of the component root folder "/" is not
	 * a problem on its own.
	 * @throws Exception
	 */
	public void testComponentRootMod() throws Exception {
		// change the properties of the component root folder
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupComponentRootChange",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}

			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		Assert.assertEquals("Snapshot", result.getBaselineSetName());
	    		Assert.assertEquals(0, result.getComponentChangeCount());
	    		Assert.assertEquals(0, result.getChangeSetsDiscardedCount());
	    		Assert.assertEquals(1, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		String componentItemId = setupArtifacts.get("component1ItemId");
	    		validateComponentRootChangeSet(result.getChangeSetsAccepted(componentItemId).get(0), setupArtifacts, componentItemId, componentName, "cs1");
	    		
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
	 * Tests accepts and discards like the other tests except they are spread across
	 * multiple components.
	 * @throws Exception
	 */
	public void testMultipleComponentChanges() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupMultipleComponentChanges",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}

			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot #42", listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		Assert.assertEquals("Snapshot #42", result.getBaselineSetName());
	    		Assert.assertEquals(1, result.getComponentChangeCount());
	    		Assert.assertEquals(3, result.getChangeSetsDiscardedCount());
	    		Assert.assertEquals(6, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(3,  result.getAffectedComponents().size());
	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		String componentItemId1 = setupArtifacts.get("component1ItemId");
	    		String componentItemId2 = setupArtifacts.get("component2ItemId");
	    		String componentItemId3 = setupArtifacts.get("component3ItemId");
	    		String componentItemId4 = setupArtifacts.get("component4ItemId");
	    		
	    		String componentName1 = componentName + "1";
	    		String componentName2 = componentName + "2";
	    		String componentName3 = componentName + "3";
	    		String componentName4 = componentName + "4";
	    		Iterator<ComponentDescriptor> iter = result.getAffectedComponents().iterator();
	    		Assert.assertEquals(componentName1, iter.next().getName());
	    		Assert.assertEquals(componentName2, iter.next().getName());
	    		Assert.assertEquals(componentName3, iter.next().getName());
	    		
				validateCS4(result.getChangeSetsAccepted(componentItemId2).get(0), setupArtifacts, componentItemId2, componentName2);
				
				validateEmptyChangeSet(result.getChangeSetsAccepted(componentItemId3).get(0), setupArtifacts, componentItemId3, componentName3, "cs5");
				validateEmptyChangeSet(result.getChangeSetsAccepted(componentItemId3).get(1), setupArtifacts, componentItemId3, componentName3, "cs6");
				validateNoopSuspendChangeSet(result.getChangeSetsAccepted(componentItemId3).get(2), setupArtifacts, componentItemId3, componentName3, "cs7");
				validateNoopChangeSet(result.getChangeSetsAccepted(componentItemId3).get(3), setupArtifacts, componentName3, componentItemId3, "cs8", false);
				validateComponentRootChangeSet(result.getChangeSetsAccepted(componentItemId3).get(4), setupArtifacts, componentItemId3, componentName3, "cs9");
	    		
				validateCS1(result.getChangeSetsDiscarded(componentItemId1).get(0), setupArtifacts, componentName1, componentItemId1, true);
				validateCS2(result.getChangeSetsDiscarded(componentItemId1).get(1), setupArtifacts, componentName1, componentItemId1);
				
				validateCS3(result.getChangeSetsDiscarded(componentItemId2).get(0), setupArtifacts, componentName2, componentItemId2, true);
	    		
	    		// verify the component added
	    		RTCChangeLogComponentEntry componentEntry = result.getComponentChanges().get(0);
	    		Assert.assertEquals(componentItemId4, componentEntry.getItemId());
	    		Assert.assertEquals(componentName4, componentEntry.getName());
	    		Assert.assertEquals("add", componentEntry.getActionType().getName());

	    		// ensure that there are no incoming changes are detected
				changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertFalse(changesIncoming.booleanValue());
	    		
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
	
	private static final String XML_ENCODED_CHARACTERS = "<'&\">";

	/**
	 * Verify the XML encoding of <code>XML_ENCODED_CHARACTERS</code> in the following,
	 * 	snapshot name
	 * 	TODO component name
	 *  TODO versionable path
	 * 	change set comment
	 * 	TODO work item summary
	 * @throws Exception
	 */
	public void testXMLEncoding() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			// create the build workspace & stream for this test
			// Stream has a component not in the build workspace
			// Build workspace has a component not in the stream
			File passwordFileFile = null;
			if (Config.DEFAULT.getPasswordFile() != null && !Config.DEFAULT.getPasswordFile().isEmpty()
					&& !Config.DEFAULT.getPasswordFile().equalsIgnoreCase("none")) {
				passwordFileFile = new File(Config.DEFAULT.getPasswordFile());
			}
			
			String workspaceName = getTestName() + XML_ENCODED_CHARACTERS + System.currentTimeMillis();
			String componentName = getTestName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupXMLEncodingTestChangeSets",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class}, // componentName
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}

			try {
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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

				String snapshotName = XML_ENCODED_CHARACTERS;
				
				// checkout the changes
				testingFacade.invoke(
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
						null, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						snapshotName, listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		Assert.assertEquals(snapshotName, result.getBaselineSetName());
	    		Assert.assertEquals(0, result.getComponentChangeCount());
	    		Assert.assertEquals(1, result.getChangeSetsAcceptedCount());
	    		Assert.assertEquals(setupArtifacts.get("workspaceItemId"), result.getWorkspaceItemId());
	    		String componentItemId = setupArtifacts.get("component1ItemId");
    			validateXMLEncodingTestChangeSet(result.getChangeSetsAccepted(componentItemId).get(0), setupArtifacts, componentName, componentItemId, XML_ENCODED_CHARACTERS);
	    		
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

	private void validateCS1(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentName,
			String componentItemId, boolean discarded) {
		Assert.assertEquals(setupArtifacts.get("cs1"), csEntry.getChangeSetItemId());
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNull(csEntry.getComment());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(3, csEntry.getAffectedVersionables().size());
		// cs1 modifies a.txt
		//     modifies & renames bRenamed.txt
		//     reparents c.txt
		for (RTCChangeLogChangeSetEntry.ChangeDesc change : csEntry.getAffectedVersionables()) {
			String name = change.getName();
			if (name.equals("/" + componentName + "/f/a.txt")) {
				assertEquals("Modified", change.getModificationKind());
			} else if (!discarded && name.equals("/" + componentName + "/f/bRenamed.txt")) {
				assertEquals("Modified and Moved", change.getModificationKind());
			} else if (discarded && name.equals("/" + componentName + "/f/b.txt")) {
				assertEquals("Modified and Moved", change.getModificationKind());
			} else if (!discarded && name.equals("/" + componentName + "/f2/c.txt")) {
				assertEquals("Modified and Moved", change.getModificationKind());
			} else if (discarded && name.equals("/" + componentName + "/f/c.txt")) {
				assertEquals("Modified and Moved", change.getModificationKind());
			} else {
				Assert.fail("Unexpected versionable " + name);
			}
			validateChange(change, setupArtifacts);
		}
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertTrue(csEntry.getAdditionalWorkItems().isEmpty());
		Assert.assertEquals(csEntry.getAffectedVersionables().size(), csEntry.getAffectedPaths().size());
		Assert.assertFalse(csEntry.isTooManyChanges());
	}

	private void validateCS2(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentName,
			String componentItemId) {
		// cs2 has a comment
		//     deletes f/tree/  f/tree/e.txt 
		Assert.assertEquals(setupArtifacts.get("cs2"), csEntry.getChangeSetItemId());
		Assert.assertEquals("comment of the change set", csEntry.getComment());
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(2, csEntry.getAffectedVersionables().size());
		for (RTCChangeLogChangeSetEntry.ChangeDesc change : csEntry.getAffectedVersionables()) {
			String name = change.getName();
			if (!name.equals("/" + componentName + "/f/tree/") && !name.equals("/" + componentName + "/f/tree/e.txt")) {
				Assert.fail("Unexpected versionable " + name);
			}
			assertEquals("Deleted", change.getModificationKind());
			validateChange(change, setupArtifacts);
		}
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertTrue(csEntry.getAdditionalWorkItems().isEmpty());
		Assert.assertEquals(csEntry.getAffectedVersionables().size(), csEntry.getAffectedPaths().size());
		Assert.assertFalse(csEntry.isTooManyChanges());
	}

	private void validateCS3(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentName,
			String componentItemId, boolean discarded) {
		// cs3 adds newTree/ newTree/newFile.txt
		// cs3 has a work item associated with it but no comment
		// The workitems are dependent on the test repo being setup with some work items
		Assert.assertEquals(setupArtifacts.get("cs3"), csEntry.getChangeSetItemId());
		Assert.assertNull(csEntry.getComment());
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(2, csEntry.getAffectedVersionables().size());
		for (RTCChangeLogChangeSetEntry.ChangeDesc change : csEntry.getAffectedVersionables()) {
			String name = change.getName();
			if (discarded && !name.equals("/<unknown>/newTree/") 
					&& !name.equals("/<unknown>/newFile.txt")) {
				Assert.fail("Unexpected versionable " + name);
			} else if (!discarded && !name.equals("/" + componentName + "/f/newTree/")
					&& !name.equals("/" + componentName + "/f/newTree/newFile.txt")) {
				Assert.fail("Unexpected versionable " + name);
			}
			assertEquals("Added", change.getModificationKind());
			validateChange(change, setupArtifacts);
		}
		WorkItemDesc workItemEntry = csEntry.getWorkItem();
		Assert.assertNotNull(workItemEntry);
		Assert.assertEquals(setupArtifacts.get("cs3wi1"), workItemEntry.getNumber());
		Assert.assertEquals(setupArtifacts.get(workItemEntry.getNumber()), workItemEntry.getSummary());
		Assert.assertTrue(csEntry.getAdditionalWorkItems().isEmpty());
		Assert.assertEquals(csEntry.getAffectedVersionables().size(), csEntry.getAffectedPaths().size());
		Assert.assertFalse(csEntry.isTooManyChanges());
	}

	private void validateCS4(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentItemId, String componentName) {
		// cs4 adds newTree2/ and 256 files below it (making it too large to show)
		// cs4 has a comment and 5 workitems associated with it.
		// The workitems are dependent on the test repo being setup with some work items
		Assert.assertEquals(setupArtifacts.get("cs4"), csEntry.getChangeSetItemId());
		Assert.assertEquals("Share", csEntry.getComment());
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(0, csEntry.getAffectedVersionables().size());
		Assert.assertEquals(1, csEntry.getAffectedPaths().size());
		String msg = csEntry.getAffectedPaths().iterator().next();
		Assert.assertTrue(msg, msg.contains("258"));
		Assert.assertTrue(csEntry.isTooManyChanges());
		WorkItemDesc workItemEntry = csEntry.getWorkItem();
		Assert.assertNotNull(workItemEntry);
		Assert.assertEquals(setupArtifacts.get("cs4wi1"), workItemEntry.getNumber());
		Assert.assertEquals(setupArtifacts.get(workItemEntry.getNumber()), workItemEntry.getSummary());
		Assert.assertEquals(4, csEntry.getAdditionalWorkItems().size());
		int j = 2;
		for (WorkItemDesc workItem : csEntry.getAdditionalWorkItems()) {
			Assert.assertEquals(setupArtifacts.get("cs4wi" + j), workItem.getNumber());
			Assert.assertEquals(setupArtifacts.get(workItem.getNumber()), workItem.getSummary());
			j++;
		}
	}


    private void validateChange(ChangeDesc change,
			Map<String, String> setupArtifacts) {
    	Assert.assertEquals(change.getName(), setupArtifacts.get(change.getName()), change.getItemId());
    	Assert.assertEquals(change.getName(), setupArtifacts.get(change.getItemId()), change.getStateId());
	}

	private void validateEmptyChangeSet(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentItemId, String componentName, String csId) {
		// cs is empty
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertEquals(0,  csEntry.getAdditionalWorkItems().size());
		Assert.assertEquals(setupArtifacts.get(csId), csEntry.getChangeSetItemId());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(0, csEntry.getAffectedVersionables().size());
		Assert.assertEquals(0, csEntry.getAffectedPaths().size());
		Assert.assertEquals("empty change set", csEntry.getComment());
		Assert.assertFalse(csEntry.isTooManyChanges());
	}

	private void validateNoopSuspendChangeSet(
			RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts,
			String componentItemId, String componentName, String csId) {
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertEquals(0,  csEntry.getAdditionalWorkItems().size());
		Assert.assertEquals(setupArtifacts.get(csId), csEntry.getChangeSetItemId());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(3, csEntry.getAffectedVersionables().size());
		Assert.assertEquals(3, csEntry.getAffectedPaths().size());
		Assert.assertEquals("change set to force conflict", csEntry.getComment());
		Assert.assertFalse(csEntry.isTooManyChanges());
	}

	private void validateNoopChangeSet(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentName,
			String componentItemId, String csId, boolean discard) {
		// cs has 3 changes 1 is an mod & restore and is reported
		// and two which are add and then deletes -> not reported since no state
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertEquals(0,  csEntry.getAdditionalWorkItems().size());
		Assert.assertEquals(setupArtifacts.get(csId), csEntry.getChangeSetItemId());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(3, csEntry.getAffectedVersionables().size());
		Assert.assertEquals(3, csEntry.getAffectedPaths().size());
		Assert.assertEquals("Noop change set", csEntry.getComment());
		Assert.assertFalse(csEntry.isTooManyChanges());
		for (RTCChangeLogChangeSetEntry.ChangeDesc change : csEntry.getAffectedVersionables()) {
			String name = change.getName();
			if (name.equals("/" + componentName + "/f/n.txt")) {
				assertEquals("No changes", change.getModificationKind());
				validateChange(change, setupArtifacts);
			} else if (!discard && (name.equals("/" + componentName + "/f/NoopFolder/")
				|| name.equals("/" + componentName + "/f/NoopFolder/Noop.txt"))) {
				// these are the evil twin conflict merges
				assertEquals("No changes", change.getModificationKind());
				Assert.assertNull(change.getStateId());
			} else if (discard && (name.equals("<unknown>/") || name.equals("<unknown>"))) {
					// these are the evil twin conflict merges
					assertEquals("No changes", change.getModificationKind());
					Assert.assertNull(change.getStateId());
			} else {
				Assert.fail("Unexpected versionable " + name);
			}
		}
	}

	private void validateComponentRootChangeSet(
			RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, 
			String componentItemId, String componentName, String csId) {
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertEquals(0,  csEntry.getAdditionalWorkItems().size());
		Assert.assertEquals(setupArtifacts.get(csId), csEntry.getChangeSetItemId());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(1, csEntry.getAffectedVersionables().size());
		Assert.assertEquals(1, csEntry.getAffectedPaths().size());
		Assert.assertEquals("Component Root property changes", csEntry.getComment());
		Assert.assertFalse(csEntry.isTooManyChanges());
		RTCChangeLogChangeSetEntry.ChangeDesc change = csEntry.getAffectedVersionables().get(0);
		Assert.assertEquals("/", change.getName());
		assertEquals("Modified", change.getModificationKind());
		validateChange(change, setupArtifacts);
	}


	public void testBuildResultContributions() throws Exception {
		// test add snapshot contribution (happens during checkout)
		// test add workspace contribution (happens during checkout)
		// test build activity added (happens during checkout)
		// test workitems associated with build result (during checkout)
		
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();
			String buildDefinitionId = getTestName() + System.currentTimeMillis();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupBuildResultContributions",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									File.class, // passwordFile,
									int.class, // timeout,
									String.class, // workspaceName,
									String.class, // componentName
									String.class}, // buildDefinitionId
							Config.DEFAULT.getServerURI(),
							Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), passwordFileFile,
							Config.DEFAULT.getTimeout(), workspaceName,
							componentName, buildDefinitionId);
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}
			
			try {
				
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// ensure that the incoming changes are detected
				Boolean changesIncoming = (Boolean) testingFacade.invoke(
						"incomingChanges",
						new Class[] { String.class, // serverURL
								String.class, // userid
								String.class, // password
								File.class, // password file
								int.class, // timeout
								String.class, // buildDefinition
								String.class, // build workspace name
								Object.class, // listener
								Locale.class}, // clientLocale
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						Config.DEFAULT.getPassword(),
						passwordFileFile,
						Config.DEFAULT.getTimeout(),
						null,
						workspaceName, listener, Locale.getDefault());

				Assert.assertTrue(changesIncoming.booleanValue());
				
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

				// create the build result
				String buildResultItemId = (String) testingFacade.invoke(
						"createBuildResult", new Class[] { //$NON-NLS-1$
								String.class, // serverURI,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildDefinition,
								String.class, // buildLabel,
								Object.class, // listener
								Locale.class // clientLocale
						}, Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
						password, Config.DEFAULT.getTimeout(),
						buildDefinitionId, "TestBuildResultContributions", listener, Locale.getDefault());
				setupArtifacts.put("buildResultItemId", buildResultItemId);
				
				// checkout the changes
				testingFacade.invoke(
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
						buildResultItemId, workspaceName,
						sandboxDir.getCanonicalPath(), changeLog,
						"Snapshot", listener, Locale.getDefault());


	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLogReader);
	    		
	    		// verify the result
	    		Assert.assertNotNull(result.getBaselineSetItemId());
	    		setupArtifacts.put("baselineSetItemId", result.getBaselineSetItemId());

	    		// Verify the build result
				testingFacade.invoke(
						"verifyBuildResultContributions",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								Map.class}, // listener
						Config.DEFAULT.getServerURI(),
						Config.DEFAULT.getUserID(),
						password,
						Config.DEFAULT.getTimeout(),
						setupArtifacts);
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

	private void validateXMLEncodingTestChangeSet(RTCChangeLogChangeSetEntry csEntry,
			Map<String, String> setupArtifacts, String componentName,
			String componentItemId, String comment) {
		Assert.assertEquals(setupArtifacts.get("cs1"), csEntry.getChangeSetItemId());
		Assert.assertEquals(componentItemId, csEntry.getComponentItemId());
		Assert.assertEquals(componentName, csEntry.getComponentName());
		Assert.assertEquals(comment, csEntry.getComment());
		Assert.assertNotNull(csEntry.getTimestamp());
		Assert.assertEquals(1, csEntry.getAffectedVersionables().size());
		// cs1 modifies the file
		for (RTCChangeLogChangeSetEntry.ChangeDesc change : csEntry.getAffectedVersionables()) {
			String name = change.getName();
			assertEquals("/f/a.txt", name);
			validateChange(change, setupArtifacts);
		}
		Assert.assertNull(csEntry.getWorkItem());
		Assert.assertTrue(csEntry.getAdditionalWorkItems().isEmpty());
		Assert.assertEquals(csEntry.getAffectedVersionables().size(), csEntry.getAffectedPaths().size());
		Assert.assertFalse(csEntry.isTooManyChanges());
	}
}
