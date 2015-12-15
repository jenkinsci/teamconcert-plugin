/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
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
import hudson.remoting.RemoteOutputStream;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.Locale;
import java.util.Map;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.BuildResultInfo;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultSetupTask;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCCheckoutTask;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.tests.utils.FileUtils;

public class RTCBuildTaskIT extends HudsonTestCase {

	private static final String FAKE_UUID = "_kkmC4NWiEdylmcAI5HeTUQ"; //$NON-NLS-1$
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
     * Tests that BuildResultSetupTask knows when to setup locally
     * @throws Exception
     */
	public void testLocalInvocation() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
		
			TaskListener listener = new StreamTaskListener(System.out, null);
    		
			RTCBuildResultSetupTask task = new RTCBuildResultSetupTask(
					"Testing " + getTestName(), Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					false, null,
					null, getTestName(), listener, true, false,
					Locale.getDefault());
			
			// using workspace and no build defn means task can run locally (no RTC calls)
			assertNotNull(task.localInvocation());
			
			task = new RTCBuildResultSetupTask(
					"Testing " + getTestName(), Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					false, null,
					FAKE_UUID, getTestName(), listener, true, false,
					Locale.getDefault());
			
			// we have a build result so we have to get info from it - run on slave
			assertNull(task.localInvocation());
			
			
			task = new RTCBuildResultSetupTask(
					"Testing " + getTestName(), Config.DEFAULT.getToolkit(),
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					true, getTestName(),
					null, getTestName(), listener, true, false,
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
	public void testBuildResultContributionsStartingBuild() throws Exception {
		// test add snapshot contribution (happens during checkout)
		// test add workspace contribution (happens during checkout)
		// test build activity added (happens during checkout)
		// test workitems associated with build result (during checkout)
		
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();
			String buildDefinitionId = getTestName() + System.currentTimeMillis();

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
						"Testing " + getTestName(), Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, buildDefinitionId,
						buildResultUUID, // no build result
						getTestName(), listener, true, false,
						Locale.getDefault());
				assertNull(task.localInvocation());
				
				BuildResultInfo buildResultInfo = task.invoke(sandboxDir, null);
				assertTrue(buildResultInfo.ownLifeCycle());
				assertEquals(buildResultUUID, buildResultInfo.getBuildResultUUID());

				File changeLogFile = new File(sandboxDir, "RTCChangeLogFile");
				FileOutputStream changeLog = new FileOutputStream(changeLogFile);
				RemoteOutputStream remoteChangeLog = new RemoteOutputStream(changeLog);
				
				// now do the checkout
				RTCCheckoutTask checkoutTask = new RTCCheckoutTask("Testing " + getTestName(),
						Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						buildResultInfo.getBuildResultUUID(),
						null, getTestName(), listener,
						remoteChangeLog, true, false, Locale.getDefault());
				
				checkoutTask.invoke(sandboxDir, null);

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
	public void testBuildResultContributions() throws Exception {
		// test add snapshot contribution (happens during checkout)
		// test add workspace contribution (happens during checkout)
		// test build activity added (happens during checkout)
		// test workitems associated with build result (during checkout)
		
		if (Config.DEFAULT.isConfigured()) {
			RTCLoginInfo loginInfo = Config.DEFAULT.getLoginInfo();
			
			String workspaceName = getTestName() + System.currentTimeMillis();
			String componentName = getTestName();
			String buildDefinitionId = getTestName() + System.currentTimeMillis();

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
						"Testing " + getTestName(), Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						true, buildDefinitionId,
						null, // no build result
						getTestName(), listener, true, false,
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
				
				// now do the checkout
				RTCCheckoutTask checkoutTask = new RTCCheckoutTask("Testing " + getTestName(),
						Config.DEFAULT.getToolkit(),
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						buildResultInfo.getBuildResultUUID(),
						null, getTestName(), listener,
						remoteChangeLog, true, false, Locale.getDefault());
				
				checkoutTask.invoke(sandboxDir, null);

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
