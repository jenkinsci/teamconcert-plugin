/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
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

import com.ibm.team.build.internal.hjplugin.BuildResultInfo;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultSetupTask;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.FileUtils;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;

public class RTCBuildTaskIT extends AbstractTestCase {
	
	@Rule public JenkinsRule r = new JenkinsRule();

	private static final String FAKE_UUID = "_kkmC4NWiEdylmcAI5HeTUQ"; //$NON-NLS-1$
	private RTCFacadeWrapper testingFacade;
	private File sandboxDir;

	@Before
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {

			testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
			
	        File tempDir = new File(System.getProperty("java.io.tmpdir"));
	        File buildTestDir = new File(tempDir, "HJPluginTests");
	        sandboxDir = new File(buildTestDir, getUniqueName());
	        sandboxDir.mkdirs();
	        sandboxDir.deleteOnExit();
	        Assert.assertTrue(sandboxDir.exists());
		}
	}

	@After
	public void tearDown() throws Exception {
		// delete the sandbox after Hudson/Jenkins is shutdown
		if (Config.DEFAULT.isConfigured()) {
			FileUtils.delete(sandboxDir);
		}
	}

    /**
     * Tests that BuildResultSetupTask knows when to setup locally
     * @throws Exception
     */
	@Test public void testLocalInvocation() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		
			TaskListener listener = new StreamTaskListener(System.out, null);
    		
			RTCBuildResultSetupTask task = new RTCBuildResultSetupTask(
					"Testing " + getUniqueName(), Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					Config.DEFAULT.getUserID(),
					Secret.fromString(Config.DEFAULT.getPassword()).getEncryptedValue(),
					loginInfo.getTimeout(),
					false, null,
					null, getUniqueName(), listener, true, false,
					Locale.getDefault());
			
			// using workspace and no build defn means task can run locally (no RTC calls)
			assertNotNull(task.localInvocation());
			
			task = new RTCBuildResultSetupTask(
					"Testing " + getUniqueName(), Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					Secret.fromString(loginInfo.getPassword()).getEncryptedValue(),
					loginInfo.getTimeout(),
					false, null,
					FAKE_UUID, getUniqueName(), listener, true, false,
					Locale.getDefault());
			
			// we have a build result so we have to get info from it - run on slave
			assertNull(task.localInvocation());
			
			
			task = new RTCBuildResultSetupTask(
					"Testing " + getUniqueName(), Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					Secret.fromString(loginInfo.getPassword()).getEncryptedValue(),
					loginInfo.getTimeout(),
					true, getUniqueName(),
					null, getUniqueName(), listener, true, false,
					Locale.getDefault());
			
			// we have a build definition so need to create a result - run on slave
			assertNull(task.localInvocation());
		}
	}

	/**
	 * There are existing detailed tests for RepositoryConnection api called to figure out
	 * changes and add build result contributions. This test is to ensure that the tasks call
	 * those API to start a build initiated but only queued by RTC
	 * @throws Exception
	 */
	@Test public void testBuildResultContributionsStartingBuild() throws Exception {
		// test add snapshot contribution (happens during checkout)
		// test add workspace contribution (happens during checkout)
		// test build activity added (happens during checkout)
		// test workitems associated with build result (during checkout)
		
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String baselineSetName = "Snapshot_" + getUniqueName();
			String workspaceName = getUniqueName();
			String componentName = getUniqueName();
			String buildDefinitionId = getUniqueName();

			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
					.invoke("setupBuildResultContributionsInQueuedBuild",
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
			
			String buildResultUUID = setupArtifacts.get("buildResultItemId");
			assertNotNull(buildResultUUID); // make sure we are testing what we think we are
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}
			
			try {
				
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// We have no build result, but we do have a build definition
				// The task should create a build result and we own its lifecycle
				RTCBuildResultSetupTask task = new RTCBuildResultSetupTask(
						"Testing " + getUniqueName(), Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, buildDefinitionId,
						buildResultUUID, // no build result
						getUniqueName(), listener, true, false,
						Locale.getDefault());
				assertNull(task.localInvocation());
				
				BuildResultInfo buildResultInfo = task.invoke(sandboxDir, null);
				assertTrue(buildResultInfo.ownLifeCycle());
				assertEquals(buildResultUUID, buildResultInfo.getBuildResultUUID());

				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				RemoteOutputStream remoteChangeLog = new RemoteOutputStream(changeLog);
				
				Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), 
						loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), buildResultInfo.getBuildResultUUID(),
						null, sandboxDir.getAbsolutePath(), remoteChangeLog, baselineSetName, listener, Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
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
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts);
			} finally {
				// clean up
				testingFacade.invoke(
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

	/**
	 * There are existing detailed tests for RepositoryConnection api called to figure out
	 * changes and add build result contributions. This test is to ensure that the tasks call
	 * those API to setup the build result & do the checkout
	 * @throws Exception
	 */
	@Test public void testBuildResultContributions() throws Exception {
		// test add snapshot contribution (happens during checkout)
		// test add workspace contribution (happens during checkout)
		// test build activity added (happens during checkout)
		// test workitems associated with build result (during checkout)
		
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String baselineSetName = getUniqueName("Snapshot");
			String workspaceName = getUniqueName("Workspace");
			String componentName = getUniqueName("Component");
			String buildDefinitionId = getUniqueName("BuildDefinition");

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
			
			if (Config.DEFAULT.isSetUpOnly()) {
				return;
			}
			
			try {
				
				TaskListener listener = new StreamTaskListener(System.out, null);
				
				// We have no build result, but we do have a build definition
				// The task should create a build result and we own its lifecycle
				RTCBuildResultSetupTask task = new RTCBuildResultSetupTask(
						"Testing " + getUniqueName(), Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						Secret.fromString(loginInfo.getPassword()).getEncryptedValue(),
						loginInfo.getTimeout(),
						true, buildDefinitionId,
						null, // no build result
						getUniqueName(), listener, true, false,
						Locale.getDefault());
				assertNull(task.localInvocation());
				
				BuildResultInfo buildResultInfo = task.invoke(sandboxDir, null);
				assertTrue(buildResultInfo.ownLifeCycle());
				assertNotNull(buildResultInfo.getBuildResultUUID());

				// record build result so it gets cleaned up on test end
				setupArtifacts.put("buildResultItemId", buildResultInfo.getBuildResultUUID());

				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				RemoteOutputStream remoteChangeLog = new RemoteOutputStream(changeLog);
				
				Utils.acceptAndLoad(testingFacade, loginInfo.getServerUri(), loginInfo.getUserId(), 
						loginInfo.getPassword(), loginInfo.getTimeout(), buildResultInfo.getBuildResultUUID(),
						null, sandboxDir.getAbsolutePath(), remoteChangeLog, baselineSetName, listener,
						Locale.getDefault());

	    		// parse the change report and ensure the expected components are reported.
	    		RTCChangeLogParser parser = new RTCChangeLogParser();
	    		FileReader changeLogReader = new FileReader(changeLogFile);
	    		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changeLogReader);
	    		
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
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						setupArtifacts);
			} finally {
				// clean up
				testingFacade.invoke(
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

}
