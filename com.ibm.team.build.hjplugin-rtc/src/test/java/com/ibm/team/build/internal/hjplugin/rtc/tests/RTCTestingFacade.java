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

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.io.File;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.RTCFacade;


public class RTCTestingFacade extends RTCFacade {
	private TestSetupTearDownUtil fTestSetupTearDownUtil;
	
	private synchronized TestSetupTearDownUtil getTestSetupTearDownUtil() {
		if (fTestSetupTearDownUtil == null) {
			 fTestSetupTearDownUtil = new TestSetupTearDownUtil();
		}
		return fTestSetupTearDownUtil;
	}

	public Map<String, String> setupComponentChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentAddedName, String componentDroppedName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupComponentChanges(connectionDetails, workspaceName, componentAddedName,
				componentDroppedName);
		return setup;
	}

	public void tearDownComponentChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownComponentChanges(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupAcceptChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupAcceptChanges(connectionDetails, workspaceName, componentName);
		return setup;
	}
	
	public void tearDownAcceptChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownAcceptChanges(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupAcceptDiscardChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupAcceptDiscardChanges(connectionDetails, workspaceName, componentName);
		return setup;
	}
	
	public void tearDownAcceptDiscardChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownAcceptDiscardChanges(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupTestBuildWorkspace(String serverURL, String userId, String password, File passwordFile, int timeout,
			String singleWorkspaceName, String multipleWorkspaceName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupTestBuildWorkspace(connectionDetails, singleWorkspaceName, 
				multipleWorkspaceName);
		return setup;
	}
	
	public void tearDownTestBuildWorkspace(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownTestBuildWorkspace(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupEmptyChangeSets(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupEmptyChangeSets(connectionDetails, workspaceName,
				componentName);
		return setup;
	}
	
	public void tearDownEmptyChangeSets(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownEmptyChangeSets(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupNoopChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupNoopChanges(connectionDetails, workspaceName,
				componentName);
		return setup;
	}
	
	public void tearDownNoopChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownNoopChanges(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupComponentRootChange(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupComponentRootChange(connectionDetails, workspaceName, componentName);
		return setup;
	}
	
	public void tearDownComponentRootChange(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownComponentRootChange(connectionDetails, setupArtifacts);
	}
	
	public Map<String, String> setupMultipleComponentChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupMultipleComponentChanges(connectionDetails, workspaceName,
				componentName);
		return setup;
	}
	
	public void tearDownMultipleComponentChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDownMultipleComponentChanges(connectionDetails, setupArtifacts);
	}
}