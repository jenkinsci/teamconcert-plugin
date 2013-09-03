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
import java.util.Locale;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.IBuildResultInfo;
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
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupComponentChanges(connectionDetails, workspaceName, componentAddedName,
				componentDroppedName, getProgressMonitor());
		return setup;
	}

	public void tearDown(String serverURL, String userId, String password, File passwordFile, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
	}
	
	public Map<String, String> setupAcceptChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupAcceptChanges(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupAcceptDiscardChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupAcceptDiscardChanges(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupTestBuildWorkspace(String serverURL, String userId, String password, File passwordFile, int timeout,
			String singleWorkspaceName, String multipleWorkspaceName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupTestBuildWorkspace(connectionDetails, singleWorkspaceName, 
				multipleWorkspaceName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupEmptyChangeSets(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupEmptyChangeSets(connectionDetails, workspaceName,
				componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupNoopChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupNoopChanges(connectionDetails, workspaceName,
				componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupComponentRootChange(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupComponentRootChange(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupMultipleComponentChanges(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupMultipleComponentChanges(connectionDetails, workspaceName,
				componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupBuildResultContributions(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName, String buildDefinitionId) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupBuildResultContributions(connectionDetails, workspaceName,
				componentName, buildDefinitionId, getProgressMonitor());
		return setup;
	}
	
	public void verifyBuildResultContributions(String serverURL, String userId, String passwordToUse, int timeout,
			Map<String, String> artifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.verifyBuildResultContributions(connectionDetails, artifacts);
	}

	public void testCreateBuildResult(String serverURL,	String userId, String password,	File passwordFile,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.testCreateBuildResult(connectionDetails, testName);
	}

	public void testCreateBuildResultFail(String serverURL,	String userId, String password,	File passwordFile,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.testCreateBuildResultFail(connectionDetails, testName);
	}

	public void testExternalLinks(String serverURL, String userId, String password,	File passwordFile,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.testExternalLinks(connectionDetails, testName);
	}
	
	public Map<String, String> setupXMLEncodingTestChangeSets(String serverURL, String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		Map<String, String> setup = testClient.setupXMLEncodingTestChangeSets(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}

	public void testBuildTermination(String serverURL, String userId, String password, File passwordFile,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.testBuildTermination(connectionDetails, testName);
	}

	public String testBuildResultInfo(String serverURL, String userId, String password, File passwordFile,
			int timeout, String testName, Object buildResultInfoObject) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		IBuildResultInfo buildResultInfo = getBuildResultInfo(buildResultInfoObject);
		return testClient.testBuildResultInfo(connectionDetails, testName, buildResultInfo);
	}
	
	public Map<String, String> testComponentLoading(String serverURL,
			String userId, String password, File passwordFile, int timeout,
			String workspaceName, String componentName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		return testClient.testComponentLoading(connectionDetails, workspaceName, componentName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testNewLoadRules(String serverURL,
			String userId, String password, File passwordFile, int timeout,
			String workspaceName, String testName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		return testClient.testNewLoadRules(connectionDetails, workspaceName, testName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testOldLoadRules(String serverURL,
			String userId, String password, File passwordFile, int timeout,
			String workspaceName, String testName, String hjPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		return testClient.testOldLoadRules(connectionDetails, workspaceName, testName, hjPath, getProgressMonitor());
	}
	
	public Map<String, String> testPersonalBuild(String serverURL,
			String userId, String password, File passwordFile, int timeout,
			String workspaceName, String testName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		return testClient.testPersonalBuild(connectionDetails, workspaceName, testName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testGoodFetchLocation(String serverURL,
			String userId, String password, File passwordFile, int timeout,
			String workspaceName, String testName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		return testClient.testGoodFetchLocation(connectionDetails, workspaceName, testName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testBadFetchLocation(String serverURL,
			String userId, String password, File passwordFile, int timeout,
			String workspaceName, String testName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		String passwordToUse = testClient.determinePassword(password, passwordFile, Locale.getDefault());
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		return testClient.testBadFetchLocation(connectionDetails, workspaceName, testName, hjPath, buildPath, getProgressMonitor());
	}
}