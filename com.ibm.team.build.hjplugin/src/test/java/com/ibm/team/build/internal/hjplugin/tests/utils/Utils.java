/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.tests.Config;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;

public class Utils {
	private static final String BUILDTOOLKITNAME = "rtc-build-toolkit";

	private static final String CALLCONNECTOR_TIMEOUT = "30";
	public static final String ARTIFACT_WORKSPACE_NAME = "workspaceName";
	public static final String ARTIFACT_WORKSPACE_ITEM_ID = "workspaceItemId";
	public static final String ARTIFACT_STREAM_NAME = "streamName";
	public static final String ARTIFACT_STREAM_ITEM_ID = "streamItemId";
	public static final String ARTIFACT_COMPONENT_ADDED_ITEM_ID = "componentAddedItemId";

	public static final String ARTIFACT_BUILDDEFINITION_ITEM_ID = "buildDefinitionItemId";
	public static final String ARTIFACT_BUILDRESULT_ITEM_ID = "buildResultItemId";
	public static final String ARTIFACT_BUILDRESULT_ITEM_1_ID = "buildResultItemId1";
	public static final String ARTIFACT_BUILDRESULT_ITEM_2_ID = "buildResultItemId2";
	public static final String ARTIFACT_BUILDRESULT_ITEM_3_ID = "buildResultItemId3";
	public static final String ARTIFACT_BASELINESET_ITEM_ID = "baselineSetItemId";
	public static final String TEMPORARY_WORKSPACE_PREFIX = "HJP_";

	public static final String TEAM_SCM_ACCEPT_PHASE_OVER = "team_scm_acceptPhaseOver";
	public static final String TEAM_SCM_CHANGES_ACCEPTED = "team_scm_changesAccepted";
	public static final String TEAM_SCM_SNAPSHOTUUID = "team_scm_snapshotUUID";
	public static final String TEAM_SCM_STREAM_CHANGES_DATA = "team_scm_streamChangesData";
	public static final String TEAM_SCM_SNAPSHOT_OWNER = "team_scm_snapshotOwner";

	public static final Object REPOSITORY_ADDRESS = "repositoryAddress";
	
	private static RTCFacadeWrapper testingFacade = null;

	public static Map<String,String> acceptAndLoad(RTCFacadeWrapper testingFacade, String serverURI,
			String userId, String password, int timeout, String buildResultUUID,
				String buildWorkspaceName, String hjWorkspacePath, OutputStream changeLog, 
				String baselineSetName, TaskListener listener, Locale clientLocale) throws Exception {
		return acceptAndLoad(testingFacade, serverURI, userId, password, timeout, buildResultUUID, buildWorkspaceName,
				 null, null, hjWorkspacePath, changeLog, baselineSetName, null, LoadOptions.getDefault(), listener, clientLocale);
	}
	/**
	 * Call accept and load of RTC Facade one after the other.
	 * Return the build properties from accept
	 *  
	 * @param testingFacade The facade for the build toolkit
	 * @param serverURI The RTC server to interact with
	 * @param userId  
	 * @param password
	 * @param timeout
	 * @param buildResultUUID
	 * @param buildWorkspaceName
	 * @param buildSnapshotNameOrUUID
	 * @param buildStreamName
	 * @param hjWorkspacePath
	 * @param changelog
	 * @param baselineSetName
	 * @param previousSnapshotUUID
	 * @param options
	 * @param listener
	 * @param clientLocale
	 * @return
	 * @throws Exception
	 */
	public static Map<String, String> acceptAndLoad(RTCFacadeWrapper testingFacade, String serverURI, 
			String userId, String password, int timeout, String buildResultUUID, String buildWorkspaceName,
			String buildSnapshotNameOrUUID, String buildStreamName, String hjWorkspacePath, OutputStream changelog,
			String baselineSetName, String previousSnapshotUUID,
			LoadOptions options, TaskListener listener, Locale clientLocale) throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, Object> acceptMap = (Map<String, Object>) testingFacade.invoke(
							"accept",
							new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, //process
							String.class, // buildResultUUID,
							String.class, // workspaceName,
							Map.class, //buildSnapshotContextMap
							String.class, // buildsnapshot,
							String.class, // buildStream,
							String.class, // hjWorkspacePath,
							OutputStream.class, // changeLog,
							boolean.class, // isCustomSnapshotName
							String.class, // snapshotName,
							String.class, // previousSnapshotUUID
							Object.class, // listener
							Locale.class, // locale
							String.class, // callConnectorTimeout
							boolean.class,// acceptBeforeLoad
							String.class,// previousBuildUrl
							String.class} , // workspaceComment
							serverURI,
							userId,
							password,
							timeout,
							null, 
							buildResultUUID, buildWorkspaceName, null,
							buildSnapshotNameOrUUID, buildStreamName,
							hjWorkspacePath, changelog, false,
							baselineSetName, previousSnapshotUUID, listener, clientLocale, CALLCONNECTOR_TIMEOUT, 
							options.acceptBeforeLoad, null, null);
		
		// Retrieve connectorId and parentActivityId
		@SuppressWarnings("unchecked")
		Map<String, String> buildProperties = (Map <String, String>) acceptMap.get("buildProperties");
		String callConnectorId = (String) acceptMap.get("connectorId");
		String parentActivityId = (String) acceptMap.get("parentActivityId");
		
		// load the changes
		testingFacade.invoke(
					"load",
					new Class[] { String.class, //serverURI 
					String.class, // userId 
					String.class, // password
					int.class, // timeout
					String.class, // processArea
					String.class, //buildResultUUID
					String.class, //buildWorkspace
					Map.class, //buildSnapshotContextMap
					String.class, //buildSnapshot
					String.class, //buildStream
					Map.class, // buildStreamData,
					String.class, // hjWorkspacePath
					boolean.class, // isCustomSnapshotName
					String.class, // snapshotName
					Object.class, //listener
					Locale.class, //clientLocale
					String.class, // parentActivityId
					String.class, //connectorId
					Object.class, //extProvider
					PrintStream.class, // logger
					boolean.class, // isDeleteNeeded
					boolean.class, //createFoldersForComponents
					String.class, // componentsToBeExcluded
					String.class, //loadRules
					boolean.class, // acceptBeforeLoad
					String.class, // workspaceComment
					boolean.class},
					serverURI, 
					userId, 
					password,
					timeout,
					null, 
					buildResultUUID, 
					buildWorkspaceName,
					null,
					buildSnapshotNameOrUUID,
					buildStreamName,
					acceptMap.get("buildStreamData"),
					hjWorkspacePath,
					false,
					baselineSetName, 
					listener,
					clientLocale, 
					parentActivityId, 
					callConnectorId, 
					null, 
					listener.getLogger(),
					options.isDeleteNeeded, 
					options.createFoldersForComponents, 
					options.componentsToBeExcluded, 
					options.loadRules,
					options.acceptBeforeLoad,
					null,
					true);
		return buildProperties;
	}
	
	/**
	 * Create and add a component to the stream identified by its UUID
	 * 
	 * @param testingFacade
	 * @param c
	 * @param streamUUID
	 * @param componentToAddName
	 * @return
	 * @throws Exception
	 */
	public static Map<String, String> addComponentToBuildStream(RTCFacadeWrapper testingFacade, 
						Config c, String streamUUID, String componentToAddName) throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade.invoke("addComponentToStream",
				   new Class[] { String.class,  // serverURI
						   String.class, // userId 
						   String.class, //password
						   int.class, //timeout
						   String.class, //streamUUID
						   String.class }, //componentToAddName
				   c.getServerURI(), c.getUserID(), c.getPassword(), c.getTimeout(),
				   streamUUID, componentToAddName);
		return setupArtifacts;
	}
	
	
	@SuppressWarnings("unchecked")
	public static Map<String,String> setUpBuildStream(RTCFacadeWrapper testingFacade, 
									Config c,
									String streamName) throws Exception {
		// Setup a build stream with a component
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildStream_basic", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, //streamName
				c.getServerURI(), c.getUserID(), c.getPassword(), c.getTimeout(), streamName);
		
		return setupArtifacts;
	}
	
	public static Map<String, String> setupBuildDefinition(RTCFacadeWrapper testingFacade, Config c,
				String buildDefinitionId, String workspaceName, String componentName) throws Exception {
		RTCLoginInfo loginInfo = c.getLoginInfo();
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
		return setupArtifacts;
	}
	
	public static FreeStyleProject setupFreeStyleJobForStream(JenkinsRule r, Config c, String buildtoolkitName, 
							String streamName, String loadDirectory) throws Exception {
		Config defaultC = c;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(buildtoolkitName, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm.BuildType buildType = new RTCScm.BuildType("buildStream", null, null, null, streamName);
		if (loadDirectory != null) {
			buildType.setLoadDirectory(loadDirectory);
		}
		RTCScm rtcScm = new RTCScm(true, buildtoolkitName, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, buildType, false);
		
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}
	
	public static FreeStyleProject setupFreeStyleJobForStream(JenkinsRule r, Config c, String buildtoolkitName, String streamName) throws Exception {
		return setupFreeStyleJobForStream(r, c, buildtoolkitName, streamName, null);
	}
	
	public static FreeStyleBuild runBuild(FreeStyleProject prj, List<ParametersAction> actions) throws InterruptedException, ExecutionException  {
		QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null, actions == null ? new ArrayList<ParametersAction>() : actions);
		while(!future.isDone());
		return future.get();
	}
	
	
	public static void tearDown(RTCFacadeWrapper testingFacade, Config c, Map<String, String> setupArtifacts) throws Exception {
		testingFacade.invoke(
				"tearDown",
				new Class[] { String.class, // serverURL,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						Map.class}, // setupArtifacts
				c.getServerURI(),
				c.getUserID(),
				c.getPassword(),
				c.getTimeout(), setupArtifacts);
	}
	
	public static String getMatch(File file, String pattern) throws FileNotFoundException {
        Scanner scanner = null;
        String match = null;
        try {
        	scanner = new Scanner(file, "UTF-8");
        	scanner.useDelimiter(System.getProperty("line.separator"));
            while(scanner.hasNext()) {
                    String token = scanner.next();
                    if (token.matches(pattern)) {
                    	match = token;
                            break;
                    }
            }
        } finally {
        	if (scanner != null) {
        		scanner.close();
        	}
        }
        return match;
	}
	
	public  static void verifyRTCScmInBuild(Run<?,?> build, boolean isBuildDefinitionBuild) throws Exception {
		// Verify the build status
		assertNotNull(build);
		assertTrue(build.getLog(100).toString(), build.getResult().isBetterOrEqualTo(Result.SUCCESS));
		
		// Verify whether RTCScm ran successfully
		List<RTCBuildResultAction> rtcActions = build.getActions(RTCBuildResultAction.class);
		assertEquals(1, rtcActions.size());
		RTCBuildResultAction action = rtcActions.get(0);
		
		// Verify build result getting created
		if (isBuildDefinitionBuild) {
			assertNotNull(action.getBuildResultUUID());
		}

		// Verify snapshot getting created
		String baselineSetItemId = action.getBuildProperties().get("team_scm_snapshotUUID");
		assertNotNull(baselineSetItemId);
	}
	
	public static PollingResult pollProject(FreeStyleProject prj, File pollingFile) throws Exception {
		return prj.poll(new StreamTaskListener(pollingFile, Charset.forName("UTF-8")));
	}
	
	public static File getTemporaryFile() throws IOException  {
		File f = File.createTempFile("tmp", "log");
		f.deleteOnExit();
		return f;
	}
	
	public static void assertPollingMessagesWhenNoChanges(PollingResult pollingResult, 
				File pollingFile, String item) throws Exception {
		assertEquals(Change.NONE, pollingResult.change);
		assertCheckForIncomingChangesMessage(pollingFile, item);
		assertNoChangesMessage(pollingFile);
	}
	
	public static void assertPollingMessagesWhenChangesDetected(PollingResult pollingResult, 
			File pollingFile, String item) throws Exception {
		assertEquals(Change.SIGNIFICANT, pollingResult.change);
		assertCheckForIncomingChangesMessage(pollingFile, item);
		assertChangesMessage(pollingFile);		
	}

	/**
	 * Update the given RTCScm instance with the provided build type and return the updated instance.
	 */
	public static RTCScm updateAndGetRTCScm(RTCScm rtcScm, BuildType buildType) {
		RTCScm updatedRTCScm = new RTCScm(rtcScm.getOverrideGlobal(), rtcScm.getBuildTool(), rtcScm.getServerURI(), rtcScm.getTimeout(),
				rtcScm.getUserId(), Secret.fromString(rtcScm.getPassword()), rtcScm.getPasswordFile(), rtcScm.getCredentialsId(), buildType,
				rtcScm.getAvoidUsingToolkit());
		return updatedRTCScm;
	}
	

	public static FreeStyleProject setupFreeStyleJobForWorkspace(JenkinsRule r, String workspaceName) throws Exception {
		Config defaultC = Config.DEFAULT;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null), false);
		
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}
	
	public static FreeStyleProject setupFreeStyleJobForBuildDefinition(JenkinsRule r, String buildDefinitionId) throws Exception{
		Config defaultC = Config.DEFAULT;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()), 
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", buildDefinitionId, null, null, null), false);

		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}
	
	public static FreeStyleProject setupFreeStyleJobForSnapshot(JenkinsRule r, String snapshotUUID, 
			String loadDirectory) throws Exception {
		Config defaultC = Config.DEFAULT;

		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm.BuildType buildType = new RTCScm.BuildType("buildSnapshot", null, null, snapshotUUID, null);
		if (loadDirectory != null) {
			buildType.setLoadDirectory(loadDirectory);
		}
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, buildType, false);
	
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
	}
	
	public static FreeStyleProject setupFreeStyleJobForSnapshot(JenkinsRule r, String snapshotUUID) throws Exception {
		return setupFreeStyleJobForSnapshot(r, snapshotUUID, null);
	}

	/**
	 * Delete any temporary repository workspaces created during tests
	 * Temporary workspaces start with the prefix HJP 
	 */
	public static void deleteTemporaryWorkspaces() throws Exception {
		// Ensure that temporary workspaces are deleted before our test begins
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		testingFacade.invoke("tearDownRepositoryWorkspaces", new Class [] {
				String.class, // Server URI
				String.class, // userId
				String.class, // password
				int.class, // timeout
				String.class}, // repositoryWorkspacePrefix
				loginInfo.getServerUri(), 
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(),
				TEMPORARY_WORKSPACE_PREFIX);
	}
	
	/**
	 * Find temporary Repository workspaces
	 * 
	 */
	public static String [] findTemporaryWorkspaces() throws Exception {
		// Ensure that temporary workspaces are deleted before our test begins
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		return (String []) testingFacade.invoke("findRepositoryWorkspaces", new Class [] {
				String.class, // Server URI
				String.class, // userId
				String.class, // password
				int.class, // timeout
				String.class}, // repositoryWorkspacePrefix
				loginInfo.getServerUri(), 
				loginInfo.getUserId(),
				loginInfo.getPassword(),
				loginInfo.getTimeout(),
				TEMPORARY_WORKSPACE_PREFIX);
	}
	
	/** 
	 * Return a directory path that is invalid.
	 * A file path is a invalid directory path.
	 * 
	 * @return
	 * @throws IOException
	 */
	public static String getInvalidLoadPath() throws IOException {
		File f = getTemporaryFile();
		return f.getAbsolutePath();
	}
	
	public synchronized static RTCFacadeWrapper getTestingFacade() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return null;
		}
		if (testingFacade == null) {
			Config defaultC = Config.DEFAULT;
			testingFacade = RTCFacadeFactory.newTestingFacade(defaultC.getToolkit());
		}
		return testingFacade;
	}
	
	
	private static void assertCheckForIncomingChangesMessage(File pollingFile, String item) throws Exception {
		assertNotNull("Expected message about checking incoming changes", getMatch(pollingFile, "Checking incoming changes for \"" + item + "\""));
	}
	
	private static void assertNoChangesMessage(File pollingFile) throws Exception {
		assertNotNull("Expecting No changes", getMatch(pollingFile, "RTC : No changes detected"));
	}
	
	private static void assertChangesMessage(File pollingFile) throws Exception {
		assertNotNull("Expecting No changes", getMatch(pollingFile, "RTC : Changes detected"));
	}
}
