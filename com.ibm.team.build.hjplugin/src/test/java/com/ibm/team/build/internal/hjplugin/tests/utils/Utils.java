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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

import hudson.model.TaskListener;

public class Utils {
	private static final String CALLCONNECTOR_TIMEOUT = "30";

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
							String.class} , // previousBuildUrl
							serverURI,
							userId,
							password,
							timeout,
							null, 
							buildResultUUID, buildWorkspaceName, null,
							buildSnapshotNameOrUUID, buildStreamName,
							hjWorkspacePath, changelog,
							baselineSetName, previousSnapshotUUID, listener, clientLocale, CALLCONNECTOR_TIMEOUT, 
							options.acceptBeforeLoad, null);
		
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
					options.acceptBeforeLoad);
		return buildProperties;
	}
}
