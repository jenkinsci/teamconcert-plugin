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

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.Charset;
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
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.steps.DownloadFileStepExecution;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

/**
 * Integration tests for {@link DownloadFileStepExecution}
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DownloadFileIT extends AbstractRTCBuildStepTest {
	private static final String downloadTaskFragment =  
			"task: [buildResultUUID: \"Test1\", fileName : \"test\" , name: 'downloadLog']";
	private static final String prefix ="downloadFile";
	
	@Rule
	public JenkinsRule rule = new JenkinsRule();

	@Rule
	public TemporaryFolder scratchFolder = new TemporaryFolder();
	
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
	
	@Test
	public void testDownloadFileNoServerURI() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestNoServerURI(rule, prefix, downloadTaskFragment);
	}

	@Test
	public void testDownloadFileMissingCreds() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingCreds(rule, prefix, downloadTaskFragment);
	}

	@Test
	public void testDownloadFileMissingBuildToolkit() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperMissingBuildToolkit(rule, prefix, downloadTaskFragment);
	}

	@Test
	public void testDownloadFileMissingBuildResultUUID() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		WorkflowJob j = setupWorkflowJob(rule);
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s', task: [buildResultUUID: '%s', fileName : \"test\",  name: 'downloadLog'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
				""); // Missing build result UUID
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.RTCBuildStep_missing_buildResultUUID()));
		Utils.dumpLogFile(run, "downloadFile", "missingBuildResultUUID", ".log");
	}
	
	@Test
	public void testDownloadFileDestinationFileNameValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		// Invalid path
		WorkflowJob j = setupWorkflowJob(rule);
		String invalidDestinationFilename = "abcd/efgh";
		String rtcBuildStep = String.format("rtcBuild buildTool: '%s', credentialsId: '%s', serverURI:"
				+ " '%s', task: [buildResultUUID: 'Test1', fileName : \"test\", "
				+ "destinationFileName:'%s', name: 'downloadLog'],"
				+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI(),
				invalidDestinationFilename); 
		setupFlowDefinition(j, rtcBuildStep);
		
		WorkflowRun run = requestJenkinsBuild(j);
		
		String log = getLog(run);
		Assert.assertTrue(log, log.contains(Messages.
							RTCBuildStep_destination_file_name_ispath(invalidDestinationFilename).replace("\"", "&quot;")));
		Utils.dumpLogFile(run, "downloadFile", "invalidDestinationFileName", ".log");
	}
	
	@Test
	public void testDownloadFileFileNameContentIDValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String credId = "myCreds" + System.currentTimeMillis();
		setupValidCredentials(credId);

		// Both contentId and file name are not provided
		{
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', "
					+ "credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildResultUUID: 'Test1', fileName : \"\", name: 'downloadLog'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI()); 
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(Messages.
								RTCBuildStep_contentId_destination_path_none_provided()));
			Utils.dumpLogFile(run, "downloadFile", "contentIDFileNameNotProvided", ".log");
		}
		
		{
			WorkflowJob j = setupWorkflowJob(rule);
			String rtcBuildStep = String.format("rtcBuild buildTool: '%s', "
					+ "credentialsId: '%s', serverURI:"
					+ " '%s', task: [buildResultUUID: 'Test1', fileName : \"Test\", "
					+ "contentId : \"XYZ\" , name: 'downloadLog'],"
					+ " timeout: 480", CONFIG_TOOLKIT_NAME, credId, Config.DEFAULT.getServerURI()); 
			setupFlowDefinition(j, rtcBuildStep);
			
			WorkflowRun run = requestJenkinsBuild(j);
			
			String log = getLog(run);
			Assert.assertTrue(log, log.contains(Messages.
								RTCBuildStep_contentId_destination_path_both_provided()));
			Utils.dumpLogFile(run, "downloadFile", "contentIDFileNameProvided", ".log");
		}
	}
	
	@Test
	public void testDownloadFileAPISuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String buildDefinitionId = getBuildDefinitionUniqueName(prefix);	
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
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
							scratchFolder.getRoot().getCanonicalPath());
			String buildResultUUID = setupArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_ID);
			String fileNameToVerify = "log-10-90.txt";
			File fileToVerify= new File(scratchFolder.getRoot(), "log-10-90.txt");
			String filePathToVerify = fileToVerify.getCanonicalPath();
					
			@SuppressWarnings("unchecked")
			Map<String, String> ret = (Map<String, String>) testingFacade.invoke("downloadFile", 
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // buildresultUUID
							String.class, // fileName
							String.class, // componentName
							String.class, // contentId
							String.class, // contributionType
							String.class, // destinationFolder
							String.class, // destinationFileName
							Object.class, // listener
							Locale.class}, // locale
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(),
					buildResultUUID,
					"log-10-90.txt", null, null, "log", 
					scratchFolder.getRoot().getAbsolutePath(), null,
					new TaskListenerWrapper(getTaskListener()),
					Locale.getDefault());
			Assert.assertEquals(fileNameToVerify, ret.get("fileName"));
			Assert.assertEquals(filePathToVerify, ret.get("filePath"));
			// Get the contents of the file and verify it
			StringWriter sw = new StringWriter();
			Files.copy(fileToVerify, Charset.forName("utf-8"), sw);
			Assert.assertEquals("log-10-90 log", sw.toString());
		} finally {
			if (setupArtifacts == null) {
				return;
			}
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
}