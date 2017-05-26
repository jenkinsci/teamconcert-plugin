/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
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
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCPostBuildDeliverPublisher;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;
import com.ibm.team.build.internal.hjplugin.tests.Config;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.tasks.Publisher;
import hudson.util.Secret;
import hudson.util.StreamTaskListener;

public class Utils {
	private static final String Delimiter = "\n|" + System.getProperty("line.separator");

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

	/**
	 * Perform accept operation on a given buildResultUUID, repository workspace, snapshot or stream
	 * 
	 * Note: Only one of 
	 * <ul>
	 * <li><code>buildResultUUID</code></li>
	 * <li><code>buildWorkspaceName</code></li>
	 * <li><code>buildSnapshotNameOrUUID</code></li>
	 * <li><code>buildStreamName</code></li>
	 * </ul>
	 * should be non null.
	 * 
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param serverURI - URI of the RTC server
	 * @param userId 
	 * @param password  
	 * @param timeout - Specify the amount of time in seconds to wait for the connection
	 * @param buildResultUUID - UUID of the build result. The build result should exist 
	 * @param buildWorkspaceName - Name of the repository workspace
	 * @param buildSnapshotNameOrUUID - UUID or name of the snapshot 
	 * @param buildStreamName - Name of the stream.
	 * @param hjWorkspacePath - Path to the Jenkins workspace 
	 * @param changelog - Output stream for writing changelog
	 * @param baselineSetName - The name to be given for the snapshot that will be created for 
	 * 				if buildResultUUID, snapshotNameOrUUID, stream is provided 
	 * @param previousSnapshotUUID - The UUID of the previous snapshot that will be used for comparison 
	 * @param options - Options for performing accept/load.
	 * @param listener - The listener to write logging information into
	 * @param clientLocale - The locale of the requesting client.
	 * @return - A map of string, Object pairs that contains the result of the accept operation.
	 * @throws Exception - If there is an error performing accept.
	 */
	public static Map<String, Object> accept(RTCFacadeWrapper testingFacade, String serverURI, 
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
		return acceptMap;	
	}
	
	/**
	 * Perform load operation on a given buildResultUUID, repository workspace, snapshot or stream
	 * 
	 * Note: Only one of 
	 * <ul>
	 * <li><code>buildResultUUID</code></li>
	 * <li><code>buildWorkspaceName</code></li>
	 * <li><code>buildSnapshotNameOrUUID</code></li>
	 * <li><code>buildStreamName</code></li>
	 * </ul>
	 * should be non null. This should be called after performing a {@link #accept} operation
	 * 
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param serverURI - URI of the RTC server
	 * @param userId 
	 * @param password  
	 * @param timeout - Specify the amount of time in seconds to wait for the connection
	 * @param buildResultUUID - UUID of the build result. The build result should exist 
	 * @param buildWorkspaceName - Name of the repository workspace
	 * @param buildSnapshotNameOrUUID - UUID or name of the snapshot 
	 * @param buildStreamName - Name of the stream.
	 * @param hjWorkspacePath - Path to the Jenkins workspace 
	 * @param baselineSetName - The name to be given for the snapshot that will be created for 
	 * 				if buildResultUUID, snapshotNameOrUUID, stream is provided 
	 * @param options - Options for performing accept/load.
	 * @param listener - The listener to write logging information into
	 * @param clientLocale - The locale of the requesting client.
	 * @param acceptMap - The map obtained from the call to accept.
	 * @return - A map of string, Object pairs that contains the result of the accept operation.
	 * @throws Exception - If there is an error performing accept.
	 */
	public static void load(RTCFacadeWrapper testingFacade, String serverURI, String userId, String password,
			int timeout, String buildResultUUID, String buildWorkspaceName, String buildSnapshotNameOrUUID,
			String buildStreamName, String hjWorkspacePath, String baselineSetName, LoadOptions options,
			TaskListener listener, Locale clientLocale, Map<String, Object> acceptMap) throws Exception {
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
	}

	/**
	 * Perform accept and load operation on a given buildResultUUID or repository workspace
	 * 
	 * Note: Only one of 
	 * <ul>
	 * <li><code>buildResultUUID</code></li>
	 * <li><code>buildWorkspaceName</code></li>
	 * </ul>
	 * should be non null. 
	 * 
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param serverURI - URI of the RTC server
	 * @param userId 
	 * @param password  
	 * @param timeout - Specify the amount of time in seconds to wait for the connection
	 * @param buildResultUUID - UUID of the build result. The build result should exist 
	 * @param buildWorkspaceName - Name of the repository workspace
	 * @param hjWorkspacePath - Path to the Jenkins workspace 
	 * @param changelog - Output stream for writing changelog
	 * @param baselineSetName - The name to be given for the snapshot that will be created for 
	 * 				if buildResultUUID, snapshotNameOrUUID, stream is provided 
	 * @param listener - The listener to write logging information into
	 * @param clientLocale - The locale of the requesting client.
	 * @return - A map of string, Object pairs that contains the result of the accept operation.
	 * @throws Exception - If there is an error performing accept.
	 */
	public static Map<String,String> acceptAndLoad(RTCFacadeWrapper testingFacade, String serverURI,
				String userId, String password, int timeout, String buildResultUUID,
				String buildWorkspaceName, String hjWorkspacePath, OutputStream changeLog, 
				String baselineSetName, TaskListener listener, Locale clientLocale) throws Exception {
		return acceptAndLoad(testingFacade, serverURI, userId, password, timeout, 
				buildResultUUID, buildWorkspaceName, null, null, hjWorkspacePath,
				changeLog, baselineSetName, null, LoadOptions.getDefault(), listener, clientLocale);
	}
	
	/**
	 * Call accept and load of RTC Facade one after the other and return the build properties from accept
	 * 
	 * Note: Only one of 
	 * <ul>
	 * <li><code>buildResultUUID</code></li>
	 * <li><code>buildWorkspaceName</code></li>
	 * <li><code>buildSnapshotNameOrUUID</code></li>
	 * <li><code>buildStreamName</code></li>
	 * </ul>
	 * should be non null. This should be called after performing a {@link #accept} operation
	 * 
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param serverURI - URI of the RTC server
	 * @param userId 
	 * @param password  
	 * @param timeout - Specify the amount of time in seconds to wait for the connection
	 * @param buildResultUUID - UUID of the build result. The build result should exist 
	 * @param buildWorkspaceName - Name of the repository workspace
	 * @param buildSnapshotNameOrUUID - UUID or name of the snapshot 
	 * @param buildStreamName - Name of the stream.
	 * @param hjWorkspacePath - Path to the Jenkins workspace
	 * @param changelog - Output stream for writing changelog
	 * @param baselineSetName - The name to be given for the snapshot that will be created for 
	 * 				if buildResultUUID, snapshotNameOrUUID, stream is provided 
	 * @param options - Options for performing accept/load.
	 * @param listener - The listener to write logging information into
	 * @param clientLocale - The locale of the requesting client.
	 * @param acceptMap - The map obtained from the call to accept.
	 * @return - A map of string, Object pairs that contains the result of the accept operation.
	 * @throws Exception - If there is an error performing accept.
	 */
	public static Map<String, String> acceptAndLoad(RTCFacadeWrapper testingFacade, String serverURI, 
			String userId, String password, int timeout, String buildResultUUID, String buildWorkspaceName,
			String buildSnapshotNameOrUUID, String buildStreamName, String hjWorkspacePath, OutputStream changelog,
			String baselineSetName, String previousSnapshotUUID,
			LoadOptions options, TaskListener listener, Locale clientLocale) throws Exception {
		Map<String, Object> acceptMap = accept(testingFacade, serverURI, userId, password, 
							timeout, buildResultUUID, buildWorkspaceName, buildSnapshotNameOrUUID, 
							buildStreamName, hjWorkspacePath, changelog, baselineSetName,
							previousSnapshotUUID, options, listener, clientLocale);
		// Retrieve connectorId and parentActivityId
		@SuppressWarnings("unchecked")
		Map<String, String> buildProperties = (Map <String, String>) acceptMap.get("buildProperties");
		load(testingFacade, serverURI, userId, password, timeout, buildResultUUID, buildWorkspaceName,
				buildSnapshotNameOrUUID, buildStreamName, hjWorkspacePath, baselineSetName, options, listener,
				clientLocale, acceptMap);
		return buildProperties;
	}
	
	/**
	 * Create and add a component to the stream identified by its UUID
	 * 
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param c - configuration options for this test run
	 * @param streamUUID - UUId of the stream
	 * @param componentToAddName - Name of the component that should be added to the stream
	 * @return - A map that contains the UUID of the component identified by "componentAddedItemId"
	 * 			  Artifact ids are declared in TestSetupTearDownUtil in -rtc plugin.
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
	
	/**
	 * Set up a stream and a repository workspace flowing to that stream.
	 * 
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param c - configuration options for this test run
	 * @param streamName - The name of the stream
	 * @return - a map of artifact ids and their UUIDs.
	 * 			Artifact ids are declared in TestSetupTearDownUtil in -rtc plugin.
	 * @throws Exception
	 */
	 public static Map<String,String> setUpBuildStream(RTCFacadeWrapper testingFacade, 
									Config c,
									String streamName) throws Exception {
		// Setup a build stream with a component
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>)testingFacade.invoke("setupTestBuildStream_basic", new Class[] { String.class, // serverURL,
				String.class, // userId,
				String.class, // password,
				int.class, // timeout,
				String.class }, //streamName
				c.getServerURI(), c.getUserID(), c.getPassword(), c.getTimeout(), streamName);
		
		return setupArtifacts;
	}
	
	/**
	 * Sets up a build definition with the given buildDefinitionId. Also create a build repository workspace with a
	 * component and adds the repository workspace to the JazzSCM configuration element of the build definition.
	 * It also creates a repository workspace to which the build workspace flows and creates a few change sets 
	 * on the build repository workspace.
	 *  
	 * @param testingFacade - The {@link RTCTestingFacade} for the build toolkit
	 * @param c - configuration options for this test run
	 * @param buildDefinitionId - The name of the build definition id.
	 * @param workspaceName - The name of the repository workspace to be setup as the build workspace 
	 * @param componentName - The name of the component to be added to the build workspace and the flow target
	 * @return - A map of artifact ids and their UUIDs.
	 * 			  Artifact ids are declared in TestSetupTearDownUtil in -rtc plugin.
	 * @throws Exception
	 */
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
	
	/**
	 * Sets up a build definition with post build deliver deliver configuration.
	 * Sets up a build definition with the given buildDefinitionId. Also create a build repository workspace with a
	 * component and adds the repository workspace to the JazzSCM configuration element of the build definition.
	 * It also creates a repository workspace to which the build workspace flows and creates a few change sets 
	 * on the build repository workspace.
	 * Finally it creates another repository workspace which is set up as the target of post build deliver.
	 * The post build deliver configuration element is then added to the buiuld definition.
	 * 
	 * @param loginInfo - The login information for the RTC server
	 * @param componentName - The name of the component to be added to the build workspace and the flow target
	 * @param workspaceName - The name of the repository workspace to be setup as the build workspace 
	 * @param buildDefinitionId - The name of the build definition id.
	 * @param configOrGenericProperties - A map configuration or build property names and values for the post build deliver
	 * 									 configuration element  
	 * @return - A map of artifact ids and their UUIDs.
	 * 			 Artifact ids are declared in TestSetupTearDownUtil in -rtc plugin.
	 * @throws Exception
	 */
	public static Map<String, String> setupBuildDefinitionWithPBDeliver(RTCLoginInfo loginInfo, String componentName,
			String workspaceName, String buildDefinitionId, Map<String, String> configOrGenericProperties)
			throws Exception {
		return setupBuildDefinitionWithPBDeliver(loginInfo, componentName, workspaceName, buildDefinitionId, false, configOrGenericProperties);
	}
	
	
	/**
	 * 
	 * Sets up a build definition with post build deliver deliver configuration.
	 * <p><b>Creates a build result if required</b></p> 
	 * Sets up a build definition with the given buildDefinitionId. Also create a build repository workspace with a
	 * component and adds the repository workspace to the JazzSCM configuration element of the build definition.
	 * It also creates a repository workspace to which the build workspace flows and creates a few change sets 
	 * on the build repository workspace.
	 * Finally it creates another repository workspace which is set up as the target of post build deliver.
	 * The post build deliver configuration element is then added to the buiuld definition.
	 * 
	 * @param loginInfo - The login information for the RTC server
	 * @param componentName - The name of the component to be added to the build workspace and the flow target
	 * @param workspaceName - The name of the repository workspace to be setup as the build workspace 
	 * @param buildDefinitionId - The name of the build definition id.
	 * @param createBuildResult - Whether to create a build result or not.
	 * @param configOrGenericProperties - A map configuration or build property names and values for the post build deliver
	 * 									 configuration element  
	 * @return - A map of artifact ids and their UUIDs.
	 * 			 Artifact ids are declared in TestSetupTearDownUtil in -rtc plugin.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> setupBuildDefinitionWithPBDeliver(RTCLoginInfo loginInfo, String componentName,
			String workspaceName, String buildDefinitionId, boolean createBuildResult, Map<String, 
			String> configOrGenericProperties)
			throws Exception {
		return (Map<String, String>) getTestingFacade()
			.invoke("setupBuildDefinitionWithJazzScmAndPBDeliver",
					new Class[] { String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // workspaceName,
							String.class, // componentToAddName,
							String.class, // buildDefinitionId
							boolean.class, // createBuiildResult
							Map.class}, // configOrGenericPropreties
					loginInfo.getServerUri(),
					loginInfo.getUserId(),
					loginInfo.getPassword(),
					loginInfo.getTimeout(), workspaceName,
					componentName, buildDefinitionId, createBuildResult, configOrGenericProperties);
	}
	
	/**
	 * Creates a repository workspace and a component and adds the component to the given repository workspace.
	 * Creates a snapshot with the given name on the repository workspace  
	 * 
	 * @param loginInfo - The login information for RTC server
	 * @param workspaceName - The name of the repository workspace
	 * @param snapshotName - The name of the snapshot 
	 * @param componentName - The name of the component
	 * @param testingFacade - The testing facade for the build toolkit
	 * @return A map of artifact ids and their UUIDs.
	 * 			Artifact ids are declared in TestSetupTearDownUtil in -rtc plugin.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> setupBuildSnapshot(RTCLoginInfo loginInfo, String workspaceName, String snapshotName,
			String componentName, RTCFacadeWrapper testingFacade) throws Exception {
		return (Map<String, String>) testingFacade
				.invoke("setupBuildSnapshot",
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // snapshotName
								String.class, // componentName
								String.class}, // workspacePrefix
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(), workspaceName,
						snapshotName, componentName, "HJP");
	}
	
	/**
	 * Sets up a freestyle job with the given stream name. The stream should exist
	 * 
	 * @param r - The Jenkins instance
	 * @param c - The configuration of the current test run 
	 * @param streamName - The name of the stream
	 * @param loadDirectory - The directory in which the stream's contents have to be loaded
	 * @return - A FreeStyleProject created with the given RTCScm.
	 * @throws Exception 
	 */
	public static FreeStyleProject setupFreeStyleJobForStream(JenkinsRule r, Config c, 
		String streamName, String loadDirectory) throws Exception {
		RTCScm rtcScm = constructRTCScmForStream(r, c, streamName, loadDirectory);
		FreeStyleProject prj = createFreeStyleJobWithRTCScm(r, rtcScm);
		return prj;
	}

	/** Sets up a freestyle job with the given stream name. The stream should exist
	 * 
	 * @param r - The Jenkins instance
	 * @param c - The configuration of the current test run 
	 * @param streamName - The name of the stream
	 * @return - A FreeStyleProject created with the given RTCScm.
	 * @throws Exception 
	 */
	public static FreeStyleProject setupFreeStyleJobForStream(JenkinsRule r, Config c, String streamName) throws Exception {
		return setupFreeStyleJobForStream(r, c, streamName, null);
	}
	
	/**
	 * Runs a build for the given freestyle project with the given ParametersActions
	 * 
	 * @param prj - The freestyle project
	 * @param actions - The list of ParametersActions 
	 * @return - A FreeStyleProject created with the given RTCScm.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static FreeStyleBuild runBuild(FreeStyleProject prj, List<ParametersAction> actions) throws InterruptedException, ExecutionException  {
		QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0, (Cause) null, actions == null ? new ArrayList<ParametersAction>() : actions);
		while(!future.isDone());
		return future.get();
	}

	/** 
	 * Returns the number of times a pattern was found in the file.
	 * 
	 * @param file - The file to search
	 * @param pattern - The pattern to search for 
	 * @return - The number of times the match was found.
	 * @throws FileNotFoundException
	 */
	public static int getMatchCount(File file, String pattern) throws FileNotFoundException {
        Scanner scanner = null;
        if (!pattern.endsWith(".*")) {
        	pattern = pattern + ".*";
        }
    	int matchCount = 0;
        try {
        	scanner = new Scanner(file, "UTF-8");
        	scanner.useDelimiter(Delimiter);
            while(scanner.hasNext()) {
                    String token = scanner.next();
                    if (token.matches(pattern)) {
                    	matchCount++;
                    }
            }
        } finally {
        	if (scanner != null) {
        		scanner.close();
        	}
        }
        return matchCount;
	}
	
	/**
	 * Returns the string that matched the given pattern in the file.
	 * 
	 * @param file - The file to search
	 * @param pattern - The pattern to search for 
	 * @return - The string that was matched.
	 * @throws FileNotFoundException
	 */
	public static String getMatch(File file, String pattern) throws FileNotFoundException {
        Scanner scanner = null;
        String match = null;
        if (!pattern.endsWith(".*")) {
        	pattern = pattern + ".*";
        }
        try {
        	scanner = new Scanner(file, "UTF-8");
        	scanner.useDelimiter(Delimiter);
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
	
	/**
	 * Verify that RTCScm ran successfully in the given build.
	 * 
	 * @param build - The build to check
	 * @param isBuildDefinitionBuild - Whether it is a build definition configuration
	 * @throws Exception
	 */
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
	
	/**
	 * Poll a given project for changes.
	 * 
	 * @param prj The freestyle project to poll
	 * @param pollingFile  The file in which the result of polling will be put into
	 * @return - The result of polling.
	 * @throws Exception
	 */
	public static PollingResult pollProject(FreeStyleProject prj, File pollingFile) throws Exception {
		return prj.poll(new StreamTaskListener(pollingFile, Charset.forName("UTF-8")));
	}
	
	/**
	 * Assert that polling file has the item name.
	 * Check that the polling result is NONE. 
	 *  
	 * @param pollingResult The result of polling  
	 * @param pollingFile The file that contains polling messages
	 * @param itemName - The name of the item that we expect to be present in the polling file. 
	 * @throws Exception
	 */
	public static void assertPollingMessagesWhenNoChanges(PollingResult pollingResult, 
				File pollingFile, String itemName) throws Exception {
		assertEquals(Change.NONE, pollingResult.change);
		assertCheckForIncomingChangesMessage(pollingFile, itemName);
		assertNoChangesMessage(pollingFile);
	}
	
	/**
	 * Assert that polling file has the item name.
	 * Check that the polling result is SIGNIFICANT. 
	 *  
	 * @param pollingResult The result of polling  
	 * @param pollingFile The file that contains polling messages
	 * @param itemName - The name of the item that we expect to be present in the polling file. 
	 * @throws Exception
	 */
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

	/**
	 * Construct RTCScm for the given build definition. The build definition should exist.
	 * 
	 * @param r The Jenkins instance.
	 * @param buildDefinitionId The id of the build definition.
	 * @return A RTCScm  with the given build definition id.
	 */
	public static RTCScm constructRTCScmForBuildDefinition(JenkinsRule r, String buildDefinitionId) {
		Config defaultC = Config.DEFAULT;
		setSystemBuildToolkit(r, defaultC);
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()), 
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildDefinition", buildDefinitionId, null, null, null), false);
		return rtcScm;
	}
	
	/**
	 * Setup freestyle job for the given build definition. The build definition should exist.
	 * 
	 * @param r The Jenkins instance.
	 * @param buildDefinitionId The id of the build definition
	 * @return A freestyle project that has a RTCScm instance configured with the build definition.
	 * @throws Exception
	 */
	public static FreeStyleProject setupFreeStyleJobForBuildDefinition(JenkinsRule r, String buildDefinitionId) throws Exception{
		RTCScm rtcScm = constructRTCScmForBuildDefinition(r, buildDefinitionId);
		FreeStyleProject prj = createFreeStyleJobWithRTCScm(r, rtcScm);		
		return prj;
	}

	/**
	 * Sets up a freestyle project with build definition id that has post build deliver configured.
	 * The build definition should exist.
	 * Additionally adds the list of Publishers to the freestyle project. Can be <code>null<code.
	 * After adding the given list, {@link RTCPostBuildDeliverPublisher} is added.
	 * 
	 * @param r The Jenkins instance
	 * @param buildDefinitionId The id of the build definition
	 * @param list The list of Publishers to add
	 * @return A freestyle project that has a RTCScm instance configured with the build definition.
	 * @throws Exception
	 */
	public static FreeStyleProject setupFreeStyleJobWithPBDeliver(JenkinsRule r, String buildDefinitionId, List<? extends Publisher> list) throws Exception {
		RTCScm rtcScm = constructRTCScmForBuildDefinition(r, buildDefinitionId);
	    FreeStyleProject prj = createFreeStyleJobWithRTCScm(r, rtcScm);
	    if (list != null) {
	    	prj.getPublishersList().addAll(list);
	    }
		prj.getPublishersList().add(new RTCPostBuildDeliverPublisher(true));
		return prj;
	}

	/**
	 * Sets up a freestyle project with build definition id that has post build deliver configured.
	 * The build definition should exist.
	 * {@link RTCPostBuildDeliverPublisher} is added as a publisher to the freestyle project
	 *  
	 * @param r The Jenkins instance.
	 * @param buildDefinitionId The id of the build definition
	 * @return A freestyle project that has a RTCScm instance configured with the build definition.
	 * @throws Exception
	 */
	public static FreeStyleProject setupFreeStyleJobWithPBDeliver(JenkinsRule r, String buildDefinitionId) throws Exception {
		return setupFreeStyleJobWithPBDeliver(r, buildDefinitionId, null);
	}

	/**
	 * Construct RTCScm for the given repository workspace. The repository workspace should exist.
	 * 
	 * @param r The Jenkins instance.
	 * @param workspaceName The name of the repository workspace.
	 * @return A freestyle project that has a RTCScm instance configured with the repository workspace.
	 */
	public static FreeStyleProject setupFreeStyleJobForWorkspace(JenkinsRule r, String workspaceName) throws Exception {
		Config defaultC = Config.DEFAULT;
		setSystemBuildToolkit(r, defaultC);
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null), false);
		
		// Setup
		FreeStyleProject prj = createFreeStyleJobWithRTCScm(r, rtcScm);
		
		return prj;
	}
	
	/**
	 * Setup a freestyle project with snapshot configuration.The snapshot should exist.
	 * Load directory should exist.
	 * 
	 * @param r The Jenkins instance
	 * @param snapshotUUIDOrName The UUID or name of the snapshot 
	 * @param loadDirectory The directory in which the snapshot has to be loaded
	 * @return A freestyle project that has a RTCScm instance configured with the snapshot
	 * @throws Exception
	 */
	public static FreeStyleProject setupFreeStyleJobForSnapshot(JenkinsRule r, String snapshotUUIDOrName, String loadDirectory) throws Exception {
		Config defaultC = Config.DEFAULT;

		setSystemBuildToolkit(r, defaultC);
		RTCScm.BuildType buildType = new RTCScm.BuildType("buildSnapshot", null, null, snapshotUUIDOrName, null);
		if (loadDirectory != null) {
			buildType.setLoadDirectory(loadDirectory);
		}
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, buildType, false);
	
		FreeStyleProject prj = createFreeStyleJobWithRTCScm(r, rtcScm);
		
		return prj;
	}
	
	/**
	 * Setup a freestyle project with snapshot configuration. The snapshot should exist.
	 * 
	 * @param r The Jenkins instance.
	 * @param snapshotUUIDOrName The UUID or name of the snapshot.
	 * @return A freestyle project that has a RTCScm instance configured with the snapshot
	 * @throws Exception
	 */
	public static FreeStyleProject setupFreeStyleJobForSnapshot(JenkinsRule r, String snapshotUUIDOrName) throws Exception {
		return setupFreeStyleJobForSnapshot(r, snapshotUUIDOrName, null);
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
	 * Find temporary repository workspaces in the rtc repository
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
	
	/**
	 * Get the testing facade based on the configuration of the test run.
	 * 
	 * @return The testing facade
	 * @throws Exception
	 */
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
	
	/**
	 * Puts the build result UUID from the given build into the artifact ids.
	 * The build result UUID can be identified with {@link Utils.ARTIFACT_BUILDRESULT_ITEM_3_ID}
	 * 
	 * @param build The build from which the build result uuid has to be extracted
	 * @param setupArtifacts The artifact map.
	 */
	public static void putBuildResultUUIDIntoArtifactIds(Run<?, ?>build, Map<String, String> setupArtifacts) {

		List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
		if (actions.size() > 0) {
			RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
			setupArtifacts.put(Utils.ARTIFACT_BUILDRESULT_ITEM_3_ID, action.getBuildResultUUID());
		}
	}
	
	/**
	 * Get the build resultUUId from the given build. It is assumed that the build is based on a build definition
	 * configuration
	 * 
	 * @param build The build from which the data has to be fetched
	 * @return A buildResultUUId if it is a build definition based build and RTCScm has run successfully.
	 * 			<code>null</code> otherwise.
	 */
	public static String getBuildResultUUID(Run<?, ?>build) {

		List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
		if (actions.size() > 0) {
			RTCBuildResultAction action = build.getActions(RTCBuildResultAction.class).get(0);
			return action.getBuildResultUUID();
		}
		return null;
	}
	
	/**
	 * Returns a list of one ParametersAction that represents an empty buildResultUUID.
	 *  
	 * @return A list of ParametersAction that contains one item representing an empty buildResultUUID.
	 */
	public static List<ParametersAction> getPactionsWithEmptyBuildResultUUID() {
		List<ParametersAction> pActions = new ArrayList<ParametersAction> ();
		pActions.add(new ParametersAction(new StringParameterValue("buildResultUUID", "")));
		return pActions;
	}
	
	/**
	 * Runs a tearDown operation on the given artifacts 
	 *
	 * @param testingFacade The testing facade to work with
	 * @param c - The configuration of the test run.
	 * @param setupArtifacts - A map of artifact identifiers and UUIDs
	 * @throws Exception
	 */
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
	
	/**
	 * Returns a temporary file that will delete itself with JVM exists.
	 *  
	 * @return A File object that represents the temporary file.
	 * @throws IOException
	 */
	public static File getTemporaryFile() throws IOException  {
		File f = File.createTempFile("tmp", "log");
		f.deleteOnExit();
		return f;
	}
	

	/**
	 * Constructs RTCScm for the given stream name. The stream should exist.
	 * 
	 * @param r The Jenkins instance
	 * @param c The configuration of the test run
	 * @param streamName The name of the build stream
	 * @param loadDirectory The directory in which the stream has to be loaded.
	 * @return A RTCScm instance
	 */
	private static RTCScm constructRTCScmForStream(JenkinsRule r, Config c, String streamName, String loadDirectory) {
		Config defaultC = c;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm.BuildType buildType = new RTCScm.BuildType("buildStream", null, null, null, streamName);
		if (loadDirectory != null) {
			buildType.setLoadDirectory(loadDirectory);
		}
		RTCScm rtcScm = new RTCScm(true, BUILDTOOLKITNAME, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, buildType, false);
		return rtcScm;
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
	
	private static FreeStyleProject createFreeStyleJobWithRTCScm(JenkinsRule r, RTCScm rtcScm) throws IOException {
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		return prj;
	}
	
	private static void setSystemBuildToolkit(JenkinsRule r, Config defaultC) {
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(BUILDTOOLKITNAME, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
	}
}
