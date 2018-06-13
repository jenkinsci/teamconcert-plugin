/*******************************************************************************
 * Copyright (c) 2013, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.BuildClient;
import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.IBuildResultInfo;
import com.ibm.team.build.internal.hjplugin.rtc.RTCFacade;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.scm.common.IWorkspaceHandle;


public class RTCTestingFacade extends RTCFacade {
	private TestSetupTearDownUtil fTestSetupTearDownUtil;
	
	private synchronized TestSetupTearDownUtil getTestSetupTearDownUtil() {
		if (fTestSetupTearDownUtil == null) {
			 fTestSetupTearDownUtil = new TestSetupTearDownUtil();
		}
		return fTestSetupTearDownUtil;
	}
	
	/**
	 * Finds Repository Workspaces matching the given prefix
	 *
	 * @param serverURL
	 * @param userId
	 * @param password
	 * @param timeout
	 * @param repositoryWorkspacePrefix
	 * @return an array of workspace itemids. Never <code>null</code>
	 * @throws Exception
	 */
	public String[] findRepositoryWorkspaces(String serverURL, String userId, String password, int timeout,
			String repositoryWorkspacePrefix) throws Exception {
		BuildClient testClient = new BuildClient();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(null);
		List<IWorkspaceHandle> workspaceHs = SCMUtil.findRepositoryWorkspaces(
								connection.getTeamRepository(), repositoryWorkspacePrefix);
		String [] workspaceItemIds = new String[workspaceHs.size()];
		for (int i = 0 ; i < workspaceHs.size(); i++) {
			workspaceItemIds[i] = workspaceHs.get(i).getItemId().getUuidValue();
		}
		return workspaceItemIds;
	}
	
	/**
	 * Remove repository workspaces starting with a specific prefix
	 * @throws Exception 
	 */
	public void tearDownRepositoryWorkspaces(String serverURL, String userId, String password, int timeout,
				String repositoryWorkspacePrefix) throws Exception {
		BuildClient testClient = new BuildClient();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(null);
		SCMUtil.deleteRepositoryWorkspaces(connection.getTeamRepository(), 
					repositoryWorkspacePrefix);
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

	public void tearDownTestBuildStream_complete(String serverURL, String userId, String password, int timeout,
			Map<String, String> setupArtifacts) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.tearDownTestBuildStream_complete(connectionDetails, setupArtifacts, getProgressMonitor());
	}
	

    public void tearDownTestBuildSnapshot_complete(String serverURL, String userId, String password, int timeout,
            Map<String, String> setupArtifacts) throws Exception {
        TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
        ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
        testClient.tearDownTestBuildSnapshot_complete(connectionDetails, setupArtifacts, getProgressMonitor());
    }
	
    public Map<String, String> addComponentToStream(String serverURL, String userId, String password, int timeout,
    		String streamUUID, String componentToBeAddedName) throws Exception {
    	TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
    	ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
    	return testClient.addComponentToStream(connectionDetails, 
    			streamUUID, componentToBeAddedName, getProgressMonitor());
    }
	public Map<String, String> setupAcceptChanges(String serverURL, String userId, String password, int timeout,
			String name,  String componentName, boolean createBuildDefinition) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupAcceptChanges(connectionDetails, name, componentName, createBuildDefinition, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupAcceptChanges(String serverURL, String userId, String password, int timeout,
			String name,  String componentName, String buildDefinitionId,  boolean createBuildDefinition, boolean createBuildResult) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupAcceptChanges(connectionDetails, name, componentName, buildDefinitionId,
						null, createBuildDefinition, createBuildResult, false, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupAcceptDiscardChanges(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupAcceptDiscardChanges(connectionDetails, workspaceName, componentName, getProgressMonitor());
		return setup;
	}

	/**
	 * 
	 * Sets up a target workspace and some change sets in it. One of the these change sets 
	 * will have a new work item with the summary passed in to this method. 
	 * It sets up a build workspace that flows to the target workspace.
	 * 
	 * @param serverURL - The URL of RTC server
	 * @param userId - The user id 
	 * @param password - The password
	 * @param timeout - The timeout when connecting to the server
	 * @param workspaceName  - The name of the build workspace to create
	 * @param componentName - THe name for the component to be added to build and target worskpace 
	 * @param loadDirectory - The directory in which the workspace will be loaded 
	 * @param createWorkItem - <code> true</code> if work item has to be created for one of the change sets
	 * @param workItemSummary - The summary to be used for the work item.
	 * @return a map that has the predefined name for the artifact and its UUID 
	 * @throws Exception if anything goes wrong in setup.
	 */
	public Map<String,String> setupAcceptChanges(String serverURL, String userId, String password, int timeout, 
			String workspaceName,
			String componentName, String loadDirectory, boolean createWorkItem, String workItemSummary) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupAcceptChanges(connectionDetails, workspaceName, componentName,
				loadDirectory, createWorkItem, workItemSummary, getProgressMonitor());
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
	
	public Map<String, String> setupTestBuildStream_basic(String serverURL, String userId, String password, int timeout,
			String projectAreaName, String buildStreamName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupTestBuildStream_basic(connectionDetails, projectAreaName, buildStreamName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupTestBuildStream_basic(String serverURL, String userId, String password, int timeout, String buildStreamName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupTestBuildStream_basic(connectionDetails, buildStreamName, getProgressMonitor());
		return setup;
	}
	
	public Map<String, String> setupTestBuildStream_toTestLoadPolicy(String serverURL, String userId, String password, int timeout, String buildStreamName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupTestBuildStream_toTestLoadPolicy(connectionDetails, buildStreamName, getProgressMonitor());
		return setup;
	}

	public Map<String, String> setupTestBuildSnapshot_basic(String serverURL, String userId, String password, int timeout, String projectAreaName,
			String streamName, String workspaceName, String snapshotName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupTestBuildSnapshot_basic(connectionDetails, projectAreaName, streamName, workspaceName,
				snapshotName, getProgressMonitor());
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
	
	public Map<String, String> setupBuildResultContributions_toTestLoadPolicy(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String buildDefinitionId) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil(); 
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		Map<String, String> setup = testClient.setupBuildResultContributions_toTestLoadPolicy(connectionDetails, workspaceName,
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
	
	public Map<String, String> testOldLoadRules_setAllLoadOptions(String serverURL,
			String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testOldLoadRules_setAllLoadOptions(connectionDetails, workspaceName, componentName, hjPath, getProgressMonitor());
	}

	public Map<String, String> testBuildDefinitionConfig_loadRulesWithNoLoadPolicy(String serverURL, String userId, String password,
			int timeout, String workspaceName, String componentName, String hjPath, String buildPath, boolean configureLoadRules,
			boolean setLoadPolicy) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_loadRulesWithNoLoadPolicy(connectionDetails, workspaceName, componentName, hjPath,
				buildPath, configureLoadRules, setLoadPolicy, getProgressMonitor());
	}

	public Map<String, String> testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules(String serverURL, String userId, String password,
			int timeout, String workspaceName, String componentName, String hjPath, String buildPath, boolean configureLoadRules) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules(connectionDetails, workspaceName, componentName, hjPath,
				buildPath, configureLoadRules, getProgressMonitor());
	}
	
	public Map<String, String> testBuildDefinitionConfig_createFoldersForComponents(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath, boolean shouldCreateFoldersForComponents,
			String loadPolicy, String componentLoadConfig) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_createFoldersForComponents(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				shouldCreateFoldersForComponents, loadPolicy, componentLoadConfig, getProgressMonitor());
	}
	
	public Map<String, String> testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				getProgressMonitor());
	}

	public Map<String, String> testBuildDefinitionConfig_componentsToExclude(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath, boolean shouldExcludeComponents, String loadPolicy,
			String componentLoadConfig) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_componentsToExclude(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				shouldExcludeComponents, loadPolicy, componentLoadConfig, getProgressMonitor());
	}

	public Map<String, String> testBuildDefinitionConfig_includeComponents(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath, boolean addLoadComponents, String valueOfIncludeComponents,
			String loadPolicy, String componentLoadConfig) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_includeComponents(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				addLoadComponents, valueOfIncludeComponents, loadPolicy, componentLoadConfig, getProgressMonitor());
	}

	public Map<String, String> testBuildDefinitionConfig_multipleLoadRuleFiles(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath, boolean setLoadPolicyToUseLoadRules) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_multipleLoadRuleFiles(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				 setLoadPolicyToUseLoadRules, getProgressMonitor());
	}
	
	public Map<String, String> testBuildDefinitionConfig_oldLoadRulesFormat(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath, boolean setLoadPolicyToUseLoadRules) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testBuildDefinitionConfig_oldLoadRulesFormat(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				setLoadPolicyToUseLoadRules, getProgressMonitor());
	}
	
	public Map<String, String> testRepositoryWorkspaceConfig_loadPolicy(String serverURL, String userId, String password, int timeout,
			String workspaceName, String componentName, String hjPath, String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testRepositoryWorkspaceConfig_loadPolicy(connectionDetails, workspaceName, componentName, hjPath, buildPath,
				getProgressMonitor());
	} 

	public Map<String, String> testStreamConfig_loadPolicy(String serverURL, String userId, String password, int timeout, String workspaceName,
			String componentName, String hjPath, String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testStreamConfig_loadPolicy(connectionDetails, workspaceName, componentName, hjPath, buildPath, getProgressMonitor());
	}

	public Map<String, String> testSnapshotConfig_loadPolicy(String serverURL, String userId, String password, int timeout, String workspaceName,
			String componentName, String hjPath, String buildPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.testSnapshotConfig_loadPolicy(connectionDetails, workspaceName, componentName, hjPath, buildPath, getProgressMonitor());
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
	
	/*public void testBuildStreamConfiguration(String serverURL, String userId, String password,
			int timeout, String workspaceName, String streamName, String componentName, String workspacePrefix, String hjPath) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
	}*/

	public Map<String, String> setupTestProcessArea_basic(String serverURL, String userId, String password, int timeout, String projectAreaName)
			throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupTestProcessArea_basic(connectionDetails, projectAreaName);
	}

	public Map<String, String> setupTestProcessArea_archiveProjectArea(String serverURL, String userId, String password, int timeout,
			String projectAreaName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupTestProcessArea_archiveProjectArea(connectionDetails, projectAreaName);
	}

	public Map<String, String> setupTestProcessArea_archiveTeamArea(String serverURL, String userId, String password, int timeout,
			String projectAreaName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupTestProcessArea_archiveTeamArea(connectionDetails, projectAreaName);
	}

	public Map<String, String> setupTestBuildStream_complete(String serverURL, String userId, String password, int timeout, String projectAreaName,
			String streamName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupTestBuildStream_complete(connectionDetails, projectAreaName, streamName);
	}

	public Map<String, String> setupTestBuildSnapshot_complete(String serverURL, String userId, String password, int timeout, String workspaceName,
			String projectAreaName, String streamName, String snapshotName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		return testClient.setupTestBuildSnapshot_complete(connectionDetails, workspaceName, projectAreaName, streamName, snapshotName);
	}

	public void deleteSnapshot(String serverURL, String userId, String password, int timeout, 
							String streamName, String snapshotUUID) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		testClient.deleteSnapshot(connectionDetails, streamName, snapshotUUID, getProgressMonitor());
	}
	
	public Map<String, String> setupBuildDefinitionWithJazzScmAndPBDeliver(String serverURL, String userId, String password, int timeout,
						String workspaceName, String componentName, String buildDefinitionId, 
						String loadDirectory, boolean createBuildResult, boolean isPersonalBuild, 
						Map<String, String> configOrGenericProperties) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		if (configOrGenericProperties == null) {
			configOrGenericProperties = new HashMap<String, String>();
		}
		return testClient.setupBuildDefinitionWithJazzScmAndPBDeliver(connectionDetails, workspaceName, componentName, buildDefinitionId, 
							loadDirectory, createBuildResult, isPersonalBuild, configOrGenericProperties, getProgressMonitor());
	}
	
	public Map<String, String> setupBuildDefinitionWithoutJazzScmWithPBDeliver(String serverURL, String userId, String password, int timeout,
						String workspaceName, String componentName, String buildDefinitionId, boolean createBuildResult,
						Map<String, String> configOrGenericProperties) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURL, userId, password, timeout);
		if (configOrGenericProperties == null) {
			configOrGenericProperties = new HashMap<String, String>();
		}
		return testClient.setupBuildDefinitionWithoutJazzScmWithPBDeliver(connectionDetails, workspaceName, componentName, buildDefinitionId, 
									createBuildResult, configOrGenericProperties, getProgressMonitor());
	}
}