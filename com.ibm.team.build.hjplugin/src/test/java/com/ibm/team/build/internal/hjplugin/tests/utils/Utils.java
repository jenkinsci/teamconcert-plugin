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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.tests.Config;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.Secret;

public class Utils {
	private static final String CALLCONNECTOR_TIMEOUT = "30";
	public static final String ARTIFACT_WORKSPACE_NAME = "workspaceName";
	public static final String ARTIFACT_WORKSPACE_ITEM_ID = "workspaceItemId";
	public static final String ARTIFACT_STREAM_NAME = "streamName";
	public static final String ARTIFACT_STREAM_ITEM_ID = "streamItemId";
	public static final String TEAM_SCM_SNAPSHOTUUID = "team_scm_snapshotUUID";
	public static final String TEAM_SCM_STREAM_CHANGES_DATA = "team_scm_streamChangesData";
	public static final String TEAM_SCM_SNAPSHOT_OWNER = "team_scm_snapshotOwner";
	public static final String ARTIFACT_BUILDDEFINITION_ITEM_ID = "buildDefinitionItemId";
	public static final String ARTIFACT_BUILDRESULT_ITEM_ID = "buildResultItemId";
	public static final String ARTIFACT_BASELINE_ITEM_ID = "baselineSetItemId";

	public static Map<String,String> acceptAndLoad(RTCFacadeWrapper testingFacade, String serverURI,
			String userId, String password, int timeout, String buildResultUUID,
				String buildWorkspaceName, String hjWorkspacePath, OutputStream changeLog, 
				String baselineSetName, TaskListener listener, Locale clientLocale) throws Exception {
		return acceptAndLoad(testingFacade, serverURI, userId, password, timeout, buildResultUUID, buildWorkspaceName,
				 null, null, hjWorkspacePath, changeLog, baselineSetName, null, LoadOptions.getDefault(), listener, clientLocale);
	}
	
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
							String.class, // baselineSetName,
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
							hjWorkspacePath, changelog,
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
					String.class, //baselineSetName
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
					},
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
					null);
		return buildProperties;
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
	
	public static FreeStyleProject setupFreeStyleJobForStream(JenkinsRule r, Config c, String buildtoolkitName, String streamName) throws Exception {
		Config defaultC = c;
		// Set the toolkit
		RTCBuildToolInstallation install = new RTCBuildToolInstallation(buildtoolkitName, defaultC.getToolkit(), null);
		r.jenkins.getDescriptorByType(RTCBuildToolInstallation.DescriptorImpl.class).setInstallations(install);
		RTCScm rtcScm = new RTCScm(true, buildtoolkitName, defaultC.getServerURI(), defaultC.getTimeout(), defaultC.getUserID(), Secret.fromString(defaultC.getPassword()),
				defaultC.getPasswordFile(), null, new RTCScm.BuildType("buildStream", null, null, null, streamName), false);
		
		// Setup
		FreeStyleProject prj = r.createFreeStyleProject();
		prj.setScm(rtcScm);
		
		return prj;
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
}
