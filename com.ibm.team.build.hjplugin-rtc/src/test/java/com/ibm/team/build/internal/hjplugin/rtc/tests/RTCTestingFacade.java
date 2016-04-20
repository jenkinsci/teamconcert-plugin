/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

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

	public Map<String, String> setupComponentChanges(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentAddedName, String componentDroppedName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupComponentChanges(connectionDetails, workspaceName, componentAddedName,
				componentDroppedName, getProgressMonitor());
		return setup;
	}

	public void tearDown(String serverURL, String userId, String password, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
	}
	
	public Map<String, String> setupAcceptChanges(String serverURL, String userId, String password, int timeout,
			String name,  String componentName, boolean createBuildDefinition) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupAcceptChanges(connectionDetails, name, componentName, createBuildDefinition, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupAcceptDiscardChanges(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupAcceptDiscardChanges(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupTestBuildWorkspace(String serverURL, String userId, String password, int timeout,
			String singleWorkspaceName, String multipleWorkspaceName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupTestBuildWorkspace(connectionDetails, singleWorkspaceName, 
				multipleWorkspaceName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupTestBuildDefinition(String serverURL, String userId, String password, int timeout,
			String buildDefinitionName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupTestBuildDefinition(connectionDetails, buildDefinitionName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupEmptyChangeSets(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupEmptyChangeSets(connectionDetails, workspaceName,
				componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupNoopChanges(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupNoopChanges(connectionDetails, workspaceName,
				componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupComponentRootChange(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupComponentRootChange(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupMultipleComponentChanges(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupMultipleComponentChanges(connectionDetails, workspaceName,
				componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupBuildResultContributions(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String buildDefinitionId) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupBuildResultContributions(connectionDetails, workspaceName,
				componentName, buildDefinitionId, getProgressMonitor());
		return setup;
	}

	public Map<String, String> setupBuildResultContributionsInQueuedBuild(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String buildDefinitionId) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupBuildResultContributionsInQueuedBuild(connectionDetails, workspaceName,
				componentName, buildDefinitionId, getProgressMonitor());
		return setup;
	}

	public void verifyBuildResultContributions(String serverURL, String userId, String passwordToUse, int timeout,
			Map<String, String> artifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, passwordToUse, timeout);
		testClient.verifyBuildResultContributions(connectionDetails, artifacts);
	}

	public void testCreateBuildResult(String serverURL,	String userId, String password,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.testCreateBuildResult(connectionDetails, testName);
	}

	public void testCreateBuildResultFail(String serverURL,	String userId, String password,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.testCreateBuildResultFail(connectionDetails, testName);
	}

	public void testExternalLinks(String serverURL, String userId, String password,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.testExternalLinks(connectionDetails, testName);
	}
	
	public Map<String, String> setupXMLEncodingTestChangeSets(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupXMLEncodingTestChangeSets(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupTestBuildSnapshotConfiguration(String serverURL, String userId, String password, int timeout,
				String workspaceName, String snapshotName, String componentName, String workspacePrefix,
				String hJPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupSnapshot(connectionDetails, workspaceName, componentName, snapshotName, getProgressMonitor());
	}

	public Map<String, String> testBuildTerminationSetup(String serverURL, String userId, String password,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildTerminationSetup(connectionDetails, testName);
	}

	public void testBuildTerminationTestSetup(String serverURL, String userId, String password,
			int timeout, boolean startBuild, boolean abandon, String buildStatus, Map<String, String> artifactIds) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.testBuildTerminationTestSetup(connectionDetails, startBuild, abandon, buildStatus, artifactIds);
	}

	public void verifyBuildTermination(String serverURL, String userId, String password,
			int timeout, String expectedState, String expectedStatus, Map<String, String> artifactIds) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.verifyBuildTermination(connectionDetails, expectedState, expectedStatus, artifactIds);
	}

	public void verifyBuildResultDeleted(String serverURL, String userId, String password,
			int timeout, Map<String, String> artifactIds) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.verifyBuildResultDeleted(connectionDetails, artifactIds);
	}

	public void testBuildStart(String serverURL, String userId, String password,
			int timeout, String testName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.testBuildStart(connectionDetails, testName);
	}

	public String testBuildResultInfo(String serverURL, String userId, String password,
			int timeout, String testName, Object buildResultInfoObject) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		IBuildResultInfo buildResultInfo = getBuildResultInfo(buildResultInfoObject);
		return testClient.testBuildResultInfo(connectionDetails, testName, buildResultInfo);
	}
	
	public Map<String, String> testComponentLoading(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testComponentLoading(connectionDetails, workspaceName, componentName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testNewLoadRules(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testNewLoadRules(connectionDetails, workspaceName, componentName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testOldLoadRules(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testOldLoadRules(connectionDetails, workspaceName, componentName, hjPath, getProgressMonitor());
	}
	
	public Map<String, String> testPersonalBuild(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testPersonalBuild(connectionDetails, workspaceName, componentName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testGoodFetchLocation(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String testName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testGoodFetchLocation(connectionDetails, workspaceName, testName, hjPath, buildPath, getProgressMonitor());
	}
	
	public Map<String, String> testBadFetchLocation(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String testName, String hjPath,
			String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBadFetchLocation(connectionDetails, workspaceName, testName, hjPath, buildPath, getProgressMonitor());
	}
	
	public void testCompareIncomingChanges() throws Exception {
		RTCAcceptReportUtilityTests testsClassObject = new RTCAcceptReportUtilityTests();
		testsClassObject.testMatchingAcceptReports();
		testsClassObject.testAcceptReportsWithDifferentAcceptedChangesets();
		testsClassObject.testAcceptReportsWithDifferentDiscardedChangesets();
		testsClassObject.testAcceptReportsWithDifferentAcceptedComponents();
		testsClassObject.testAcceptReportsWithDifferentRemovedComponents();
		testsClassObject.testAcceptReportsWithOppositeChangesets();
		testsClassObject.testNonMatchingAcceptReports();
	}
		
	public Map<String, String> setupBuildSnapshot(String serverURL, String userId, String password,
					int timeout, String workspaceName, String snapshotName, String componentName, String workspacePrefix ) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupBuildSnapshot(connectionDetails, workspaceName, snapshotName, componentName,
				workspacePrefix, getProgressMonitor());
	}
	
	public void testBuildSnapshotConfiguration(String serverURL, String userId, String password,
			int timeout, String snapshotName,String workspacePrefix, String hjPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.testBuildSnapshotConfiguration(connectionDetails, snapshotName,
				workspacePrefix, hjPath);
}
	
	public void testBuildStreamConfiguration(String serverURL, String userId, String password,
			int timeout, String workspaceName, String streamName, String componentName, String workspacePrefix, String hjPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
	}
}