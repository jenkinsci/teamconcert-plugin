/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.steps.tests;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.steps.ListFilesStepExecution;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

/**
 * Integration tests for {@link ListFilesStepExecution}
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ListFilesIT extends AbstractRTCBuildStepTest {
	private String listFilesFragment = "task: [buildResultUUID: \"Test1\",  name: 'listLogs']";
	private String prefix = "listFiles";

	@Before
	public void setup() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		createSandboxDirectory();
		installBuildToolkitIntoJenkins();
	}
	
	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
	}
	
	@Rule
	public JenkinsRule rule = new JenkinsRule();

	@Test
	public void testListFilesNoServerURI() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestNoServerURI(rule, prefix, listFilesFragment);
	}

	@Test
	public void testListFilesMissingCreds() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingCreds(rule, prefix, listFilesFragment);
	}
	
	@Test
	public void testListFilesMissingBuildToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingBuildToolkit(rule, prefix, listFilesFragment);
	}
	
	@Test
	public void testListFilesMissingBuildResultUUID() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		WorkflowJob j = setupWorkflowJob(rule);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s', task: [buildResultUUID: '%s',  name: 'listLogs'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
				""); // Missing build result UUID
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildResultUUID()));
		Utils.dumpLogFile(run, "listFiles", "missingBuildResultUUID", ".log");
	}
	
	@Test
	public void testListFilesInvalidMaxResultsParm() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		// 0, negative value for maxResults
		WorkflowJob j = setupWorkflowJob(rule);
		
		for (String maxResult : new String[] {"0", "-12"}) {
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildResultUUID: '%s', maxResults: %s, name: 'listLogs'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
					"${test}", maxResult);
			
			setupFlowDefinition(j, rtcBuildStep);
			WorkflowRun r = requestJenkinsBuild(j);
			String log = getLog(r);
			Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_maxResults_invalid_value(maxResult).replace("\"", "&quot;")));
			Utils.dumpLogFile(r, "listFiles", "maxResultsValidation", ".log");
		}
		
		// Greater than 2048 for maxResults
		j = setupWorkflowJob(rule);
		String maxResult = Integer.toString(Helper.MAX_RESULTS_UPPER_LIMIT + 1);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s', task: [buildResultUUID: '%s', maxResults: %s, name: 'listLogs'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
				"${test}", maxResult); // Missing build result UUID
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun r = requestJenkinsBuild(j);
		
		String log = getLog(r);
		Assert.assertTrue(log, log.contains(
					Messages.RTCBuildStep_maxResults_invalid_value_greater_than_2048().replace("\"", "&quot;")));
		Utils.dumpLogFile(r, "listFiles", "maxResultsValidation", ".log");
		
	}
	
	@Test
	public void testListFilesValidateFileNamePatternParm() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		// 0, negative value for maxResults
		WorkflowJob j = setupWorkflowJob(rule);
		String fileNamePattern="[a-aA-Z\\\\.][][][]";
		// Need to remove \\ since we need only one \\ to match in the pipeline log 
		// message
		String fileNamePatternForValidation="[a-aA-Z\\.][][][]";
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s', task: [buildResultUUID: '%s', fileNameOrPattern: \"%s\",name: 'listLogs'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
				"${test}", fileNamePattern);
			
		setupFlowDefinition(j, rtcBuildStep);
		WorkflowRun r = requestJenkinsBuild(j);
		String log = getLog(r);
		Assert.assertTrue(log, log.contains(
				Messages.RTCBuildStep_file_name_pattern_invalid(fileNamePatternForValidation).
										replace("\"", "&quot;")));
		Utils.dumpLogFile(r, "listFiles", "validateFileNamePattern", ".log");
	}
	
	@Test
	public void testListFilesAPISuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String buildDefinitionId = getBuildDefinitionUniqueName(prefix);	
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = null;
		
		try {
			setupArtifacts = (Map<String, String>) testingFacade
					.invoke("createBuildResultWithLogsAndArtifacts",
							new Class[] { String.class, // serverURL,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // buildDefinitionId
									String.class},// scratchFolder
	  						loginInfo.getServerUri(),
							loginInfo.getUserId(),
							loginInfo.getPassword(),
							loginInfo.getTimeout(),
							buildDefinitionId, 
							getSandboxDir().getAbsolutePath());
			String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
			@SuppressWarnings("unchecked")
			Map<String, Object> ret = (Map<String, Object>) testingFacade.invoke("listFiles", 
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // buildresultUUID
							String.class, // fileNameOrPattern
							String.class, // componentName
							String.class, // contributionType
							int.class, // maxResults
							Object.class, // listener
							Locale.class}, // locale
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					buildResultUUID,
					null, null, "log", 512,
					new TaskListenerWrapper(getTaskListener()),
					Locale.getDefault());
			@SuppressWarnings("unchecked")
			List<List<String>> fileInfos = (List<List<String>>) ret.get("fileInfos");
			Assert.assertEquals(20, fileInfos.size());
		} finally {
			if (setupArtifacts == null) {
				return;
			}
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
}

