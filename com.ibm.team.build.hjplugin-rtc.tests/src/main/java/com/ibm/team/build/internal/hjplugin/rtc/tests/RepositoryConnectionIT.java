/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2008, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.ibm.team.build.internal.hjplugin.rtc.Constants;

import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.client.ITeamBuildRequestClient;
import com.ibm.team.build.common.WorkItemConstants;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestHandle;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultContribution;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.common.links.BuildLinkTypes;
import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport;
import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;
import com.ibm.team.build.internal.hjplugin.rtc.Messages;
import com.ibm.team.build.internal.hjplugin.rtc.RTCBuildUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.hjplugin.rtc.VersionCheckerUtil;
import com.ibm.team.build.internal.hjplugin.rtc.tests.utils.Config;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.registry.IEndPointDescriptor;
import com.ibm.team.links.common.registry.ILinkTypeRegistry;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemReferences;

/**
 * Tests for {@link RepositoryConnection}
 * 
 * Tests will only work in toolkits greater than 7.0 or above.
 * When an old toolkit is used, even if the property is set to false, links will be 
 * created 
 */
public class RepositoryConnectionIT {
	
	private TestSetupTearDownUtil fTestSetupTearDownUtil;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;

	@Rule
	public TemporaryFolder sandbox = new TemporaryFolder();
	
	@Rule
	public TemporaryFolder scratchFolder = new TemporaryFolder();
	
	private synchronized TestSetupTearDownUtil getTestSetupTearDownUtil() {
		if (fTestSetupTearDownUtil == null) {
			 fTestSetupTearDownUtil = new TestSetupTearDownUtil();
		}
		return fTestSetupTearDownUtil;
	}
	
	@Before
	public void setup() {
		if (Config.DEFAULT.isConfigured()) {
			serverURI = Config.DEFAULT.getServerURI();
			password = Config.DEFAULT.getPassword();
			userId = Config.DEFAULT.getUserID();
			timeout = Config.DEFAULT.getTimeout();
		}
	}

	/**
	 * Build will create links for toolkit v 7.0 above or below if the property does not exist
	 * This tests the scenario when an existing build definition is used with the new toolkit
     * or used with an old toolkit (against a 7.0 or above server)
	 * 
	 * @throws Exception
	 */
	@Test
	public void testAcceptCreatesIncludedInBuildLinksForOldBuildDefinitionPropertyDoesNotExist() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String artifactName = "testAcceptCreatesIncludedInBuildLinksForOldBuildDefinitionPropertyDoesNotExist" + " " + System.currentTimeMillis();
		Map<String, String> buildProperties = new HashMap<>();
		helperTestAcceptCreatesIncludedInLinks(artifactName, buildProperties, new IResultValidator() {
			@Override
			public void validate(ITeamRepository repository, String buildResultItemId, 
							String workItemId) throws TeamRepositoryException {
				// Assert that the request does not contain the property 
				// team.scm.createWorkItemIncludeLinks
				assertNoBuildResultProperty(repository, buildResultItemId, 
						Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS);
			}
		}, new IResultValidator() {
			
			@Override
			public void validate(ITeamRepository repository, String buildResultItemId, 
									String workItemId) throws TeamRepositoryException {
				IEndPointDescriptor endPointDesc =  getIncludedInBuildsDescriptor();
				int expectedLinksLength = 1;
				// Assert that there is one included in build link
				assertLinksInWorkItem(repository, workItemId, endPointDesc, 
								expectedLinksLength);
			}		
		}, new IResultValidator() {
			@Override
			public void validate(ITeamRepository repository, String buildResultItemId, 
								String workItemId) throws TeamRepositoryException {
				String contributionTypeId = WorkItemConstants.EXTENDED_DATA_TYPE_ID;
				int expectedContributionLength = 1;
				// Assert that there is one work item contribution
				assertBuildResultContributions(repository, buildResultItemId, contributionTypeId,
						expectedContributionLength);
			}
		});
	}
	
	/**
	 * Build will create links for toolkit v 7.0 above or below if the property is set to true
	 * This tests the scenario when a new build definition is used with the new toolkit
     * or used with an old toolkit (against a 7.0 or above server)
     * 
	 * @throws Exception
	 */
	@Test
	public void testAcceptCreatesIncludedInBuildLinksForNewBuildDefintion_PropertySetToTrue() 
													throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String artifactName = 
				"testAcceptCreatesIncludedInBuildLinksForNewBuildDefintion_PropertySetToTrue" + " " + System.currentTimeMillis();
		Map<String, String> buildProperties = new HashMap<>();
		buildProperties.put(Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "true");
		helperTestAcceptCreatesIncludedInLinks(artifactName, buildProperties,
				
			// Assert that the build request contains the property with the value 
			// true
			new IResultValidator() {
				
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId,
								String workItemId) throws TeamRepositoryException {
					assertBuildResultPropertyValue(repository, 
							buildResultItemId, 
							Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "true");
					
				}
			}, 
			// Assert that the work item has an included in build result link 
			new IResultValidator() {
	
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
						String workItemId) throws TeamRepositoryException {
					assertLinksInWorkItem(repository, workItemId, getIncludedInBuildsDescriptor(),
							1);
				}
				
			},
			// Assert that the build result has one work item contribution
			new IResultValidator() {
				
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
								String workItemId) throws TeamRepositoryException {
					assertBuildResultContributions(repository, buildResultItemId, 
							WorkItemConstants.EXTENDED_DATA_TYPE_ID, 1);
				}
			});
	}
	
	/**
	 * Build will create links for toolkit v 7.0 above or below if the property's value is empty
     * 
	 * @throws Exception
	 */
	@Test
	public void testAcceptCreatesIncludedInBuildLinksForNewBuildDefintion_PropertySetToEmpty() 
													throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		String artifactName = 
				"testAcceptCreatesIncludedInBuildLinksForNewBuildDefintion_PropertySetToEmpty" + " " + System.currentTimeMillis();
		Map<String, String> buildProperties = new HashMap<>();
		buildProperties.put(Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "");
		helperTestAcceptCreatesIncludedInLinks(artifactName, buildProperties, 
			new IResultValidator() {
				
				// Assert that the request has the property but the value is empty
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
						String workItemId) throws TeamRepositoryException {
					assertEmptyBuildResultProperty(repository, buildResultItemId,
							Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS);
				}
			}, 
			new IResultValidator()  {
				
				// Assert that the work item has included in build result link
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
						String workItemId) throws TeamRepositoryException {
					assertLinksInWorkItem(repository, workItemId, 
							getIncludedInBuildsDescriptor(), 1);
					
				}
			},
			new IResultValidator() {
				
				// Assert that the build result has one work item contribution
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
						String workItemId) throws TeamRepositoryException {

					assertBuildResultContributions(repository, buildResultItemId, 
							WorkItemConstants.EXTENDED_DATA_TYPE_ID, 1);
				}
			});
	}
	
	/**
	 * Build will NOT create links for toolkit v 7.0 above if the property's value is false.
	 * 
	 * For toolkit versions 6.0.6.1 or below, the test is a no-op.
	 * 
	 * See {@link #testAcceptCreatesIncludedInBuildLinksForNewBuildDefintionPropertySetToFalse6061()} 
	 * for the same type of test in 6061 and below toolkits. 
     * 
	 * @throws Exception
	 */
	@Test
	public void testAcceptDoesNotCreatesIncludedInBuildLinksForNewBuildDefintionPropertySetToFalse70()
													throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// If the toolkit is 6.0.6.1 or below, then return 
		if (VersionCheckerUtil.isPre70BuildToolkit()) {
			return;
		}
		
		// Run test only for 7.0 toolkit and above
		String artifactName = 
				"testAcceptNotCreatesIncludedInBuildLinksForNewBuildDefintionPropertySetToFalse70" + " " + System.currentTimeMillis();
		Map<String, String> buildProperties = new HashMap<>();
		buildProperties.put(Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "false");
		helperTestAcceptCreatesIncludedInLinks(artifactName, buildProperties, 
			new IResultValidator() {

				// Assert that the value for the property in the build request is false
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
						String workItemId) throws TeamRepositoryException {
					assertBuildResultPropertyValue(repository, buildResultItemId,
							Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "false");
				}
			}, 
			new IResultValidator() {
				
				// Assert that there is NO included in build link in the work item
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
							String workItemId) throws TeamRepositoryException {
					assertLinksInWorkItem(repository, workItemId, 
							getIncludedInBuildsDescriptor(), 0);
				}
			}, 
			new IResultValidator() {
				
				// Assert that the build result has one work item contribution
				@Override
				public void validate(ITeamRepository repository, String buildResultItemId, 
						String workItemId) throws TeamRepositoryException {
					assertBuildResultContributions(repository, buildResultItemId,
							WorkItemConstants.EXTENDED_DATA_TYPE_ID, 1);
				}

			});
	}
	
	/**
	 * Build will create links for toolkit v 6.0.6.1 or 
	 * below even if the property's value is false.
	 * 
	 * For toolkit versions 7.0 or below, the test is a no-op.
	 * 
	 * See {@link #testAcceptNotCreatesIncludedInBuildLinksForNewBuildDefintionPropertySetToFalse70()} 
	 * for the same type of test in 7.0 and above toolkits. 
     * 
	 * @throws Exception
	 */
	@Test
	public void testAcceptCreatesIncludedInBuildLinksForNewBuildDefintionPropertySetToFalse6061() 
													throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// If the toolkit is 7.0 or above, then return 
		if (VersionCheckerUtil.isPre70BuildToolkit() == false) {
			return;
		}
		
		// Run test only for 6.0.6.1 and lower
		String artifactName = 
				"testAcceptCreatesIncludedInBuildLinksForNewBuildDefintionPropertySetToFalse6061" + " " + System.currentTimeMillis();
		Map<String, String> buildProperties = new HashMap<>();
		buildProperties.put(Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "false");
		helperTestAcceptCreatesIncludedInLinks(artifactName, buildProperties,
				new IResultValidator() {
					
					// Assert that the property's value in the build request is false
					@Override
					public void validate(ITeamRepository repository, String buildResultItemId, 
							String workItemId) throws TeamRepositoryException {
						assertBuildResultPropertyValue(repository, buildResultItemId, 
								Constants.PROPERTY_INCLUDE_LINKS_IN_WORKITEMS, "false");
						
					}
				}, new IResultValidator() {
					
					// Assert that the work item has ONE included in build result link
					@Override
					public void validate(ITeamRepository repository, String buildResultItemId,
							  String workItemId) throws TeamRepositoryException {
						assertLinksInWorkItem(repository, workItemId, 
								getIncludedInBuildsDescriptor(), 1);
						
					}
				}, new IResultValidator() {
					
					// Assert that the build result has one work item contribution
					@Override
					public void validate(ITeamRepository repository, String buildResultItemId, 
							String workItemId) throws TeamRepositoryException {
						assertBuildResultContributions(repository, buildResultItemId, 
								WorkItemConstants.EXTENDED_DATA_TYPE_ID, 1);
						
					}
				});
	}

	@Test
	public void testGetWorkspaceUUIDInvalidParameters() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// both null
		try {
			connection.getWorkspaceUUID(null, null, getListener(), Locale.getDefault(), getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RepositoryConnection_getWorkspaceUUID_invalid_params_1(),
					exp.getMessage());
		}
		
		// both non-null
		try {
			connection.getWorkspaceUUID(TestUtils.getBuildDefinitionUniqueName(), 
						TestUtils.getRepositoryWorkspaceUniqueName(), 
						getListener(), Locale.getDefault(), getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RepositoryConnection_getWorkspaceUUID_invalid_params_2(),
					exp.getMessage());
		}
	}

	@Test
	public void testGetWorkspaceUUIDBuildDefinitionNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		try {
			connection.getWorkspaceUUID(buildDefinitionId, 
					null, getListener(), Locale.getDefault(), getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RTCBuildUtils_build_definition_not_found(buildDefinitionId),
					exp.getMessage());
		}
	}
	
	@Test
	public void testGetWorkspaceUUIDBuildWorkspaceNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		try {
			connection.getWorkspaceUUID(null, workspaceName, 
						getListener(), Locale.getDefault(), getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RepositoryConnection_workspace_not_found(workspaceName),
					exp.getMessage());
		}
	}
	
	@SuppressWarnings("restriction")
	@Test
	public void testGetWorkspaceUUIDBuildDefinitionNoWorkspaceProperty() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		
		Map<String, String> setupArtifacts = testClient.setupBuildResultContributions(
							connectionDetails, 
							workspaceName, componentName, 
							buildDefinitionId, getProgressMonitor());
		
		// Retrieve the build definition and remove the workspace property.
		IBuildDefinition buildDefinition = (IBuildDefinition) RTCBuildUtils.getInstance().getBuildDefinition(buildDefinitionId,
						connection.getTeamRepository(), getListener(),
						Locale.getDefault(), getProgressMonitor()).getWorkingCopy();
		buildDefinition.getProperties().remove(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
		ITeamBuildClient client = (ITeamBuildClient) connection.getTeamRepository().getClientLibrary(ITeamBuildClient.class);
		client.save(buildDefinition, getProgressMonitor());
		
		try {
			connection.getWorkspaceUUID(buildDefinitionId, null, 
						getListener(), Locale.getDefault(), getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().
					RepositoryConnection_build_definition_no_repository_wksp_property(buildDefinitionId),
					exp.getMessage());
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	@Test
	public void testGetWorkspaceUUIDBuildDefinitionEmptyWorkspaceProperty() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		
		Map<String, String> setupArtifacts = testClient.setupBuildResultContributions(
							connectionDetails, 
							workspaceName, componentName, 
							buildDefinitionId, getProgressMonitor());
		
		// Retrieve the build definition and save empty value for the workspace property.
		IBuildDefinition buildDefinition = (IBuildDefinition) RTCBuildUtils.getInstance().getBuildDefinition(buildDefinitionId,
						connection.getTeamRepository(), getListener(),
						Locale.getDefault(), getProgressMonitor()).getWorkingCopy();
		@SuppressWarnings("restriction")
		IBuildProperty prop = buildDefinition.getProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
		prop.setValue("");
		ITeamBuildClient client = (ITeamBuildClient) connection.getTeamRepository().getClientLibrary(ITeamBuildClient.class);
		client.save(buildDefinition, getProgressMonitor());
		
		try {
			connection.getWorkspaceUUID(buildDefinitionId, null, 
						getListener(), Locale.getDefault(), getProgressMonitor());
		} catch(RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().
					RepositoryConnection_build_definition_repository_wksp_property_empty(buildDefinitionId),
					exp.getMessage());
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	@Test
	public void testGetWorkspaceUUIDBuildDefinitionJazzSCMNotFound() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> setupArtifacts = testClient.setupBuildDefinitionWithoutSCMWithQueuedBuild(
				connectionDetails,
				buildDefinitionId, null, 
				true, null,
				true, null, null, getProgressMonitor());
		try {
			connection.getWorkspaceUUID(buildDefinitionId, null, 
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().
					BuildConnection_build_definition_missing_jazz_scm_config_1(buildDefinitionId),
					exp.getMessage());
		} finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}

	@Test
	public void testGenerateChangeLogNullParameters() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String workspaceUUID = UUID.generate().getUuidValue();
		try {
			connection.generateChangelog(null, workspaceUUID, 
					null, getChangelog(),
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RepositoryConnection_invalid_param_snapshotUUID(),
					exp.getMessage());
		}
		
		String snapshotUUID = UUID.generate().getUuidValue();
		try {
			connection.generateChangelog(snapshotUUID, null, 
					null, getChangelog(),
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			assertEquals(Messages.getDefault().RepositoryConnection_invalid_param_workspaceUUID(),
					exp.getMessage());
		}
		
	}
	
	@Test
	public void testGenerateChangeLogInvalidParameters() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		String snapshotUUID = UUID.generate().getUuidValue();
		String workspaceUUID = UUID.generate().getUuidValue();

		//  Send a invalid workpaceUUID
		String invalidWorkspaceUUID = TestUtils.getRepositoryWorkspaceUniqueName();
		try {
			connection.generateChangelog(snapshotUUID, invalidWorkspaceUUID, 
					null, getChangelog(),
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (IllegalArgumentException exp) {
			assertEquals(Messages.getDefault().
					RepositoryConnection_invalid_param_workspaceUUID_1(invalidWorkspaceUUID),
					exp.getMessage());
		}
		
		
		//  Send a invalid snapshotUUID
		String invalidSnapshotUUID = TestUtils.getSnapshotUniqueName();
		try {
			connection.generateChangelog(invalidSnapshotUUID, workspaceUUID, 
					null, getChangelog(),
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (IllegalArgumentException exp) {
			assertEquals(Messages.getDefault().
					RepositoryConnection_invalid_param_snapshotUUID_1(invalidSnapshotUUID),
					exp.getMessage());
		}
	}
	
	@Test
	public void testGenerateChangeLogNewSnapshotDoesNotExist() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		connection.ensureLoggedIn(getProgressMonitor());
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();
		String snapshotName = TestUtils.getSnapshotUniqueName();
		Map<String, String> setupArtifacts = testClient.setupSnapshot(connectionDetails, workspaceName, 
				componentName, snapshotName, getProgressMonitor());
		String workspaceUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID);
		String invalidSnapshotUUID = UUID.generate().getUuidValue();
		try {
			connection.generateChangelog(invalidSnapshotUUID, workspaceUUID, 
					null, getChangelog(),
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) { 
			assertEquals(Messages.getDefault().
					RTCSnapshotUtils_snapshot_UUID_not_found(invalidSnapshotUUID),
					exp.getMessage());
		}
		finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}
	}
	
	@Test
	public void testGenerateChangeLogWorkspaceDoesNotExist() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		connection.ensureLoggedIn(getProgressMonitor());
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();
		String snapshotName = TestUtils.getSnapshotUniqueName();
		Map<String, String> setupArtifacts = testClient.setupSnapshot(connectionDetails, workspaceName, 
				componentName, snapshotName, getProgressMonitor());
		String snapshotUUID = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_BASELINE_SET_ITEM_ID);
		String invalidWorkspaceUUID = UUID.generate().getUuidValue();
		try {
			connection.generateChangelog(snapshotUUID, invalidWorkspaceUUID, 
					null, getChangelog(),
					getListener(), Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) { 
			assertTrue(exp.getMessage(),
					exp.getMessage().contains(
					String.format("Repository workspace with UUID \"%s\" could not be found. "
							+ "It may have been deleted.",
					invalidWorkspaceUUID)));
		}
		finally {
			testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
		}	
	}
	
	@Test
	public void testGenerateChangeLogOldSnapshotDoesNotExist() throws Exception {
		// TODO not implemented
	}
	
	private static IBuildRequest getBuildRequest(ITeamRepository repository, IBuildResult result)
			throws TeamRepositoryException {
		return (IBuildRequest) repository.itemManager().fetchCompleteItem(
									(IBuildRequestHandle) result.getBuildRequests().get(0), 
									IItemManager.REFRESH, new NullProgressMonitor());
	}

	private static IBuildResult getBuildResult(ITeamRepository repository, String buildResultUUID)
			throws TeamRepositoryException {
		IBuildResultHandle resultHandle = getBuildResultHandle(buildResultUUID);
		return (IBuildResult) repository.itemManager().fetchCompleteItem(resultHandle, 
							IItemManager.REFRESH, new NullProgressMonitor());
	}

	private static IBuildResultHandle getBuildResultHandle(String buildResultUUID) {
		return (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(
															UUID.valueOf(buildResultUUID),null);
	}

	private static IWorkItemClient getWorkItemClient(ITeamRepository repository) {
		return (IWorkItemClient) repository.getClientLibrary(IWorkItemClient.class);
	}
	
	private static ITeamBuildClient getTeamBuildClient(ITeamRepository repository) {
		return (ITeamBuildClient)(repository.getClientLibrary(ITeamBuildClient.class));
	}

	private void helperTestAcceptCreatesIncludedInLinks(String artifactName, 
							Map<String, String> buildProperties,
							IResultValidator buildRequestPropertyValidator,
							IResultValidator workItemLinkValidator,
							IResultValidator buildresultContributonValidator)
			throws Exception, IOException, TeamRepositoryException, FileNotFoundException {
		Map<String, String> setupArtifacts = null;
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, 
												password, timeout);
		try {
			setupArtifacts = testClient.setupAcceptChanges(connectionDetails, artifactName,
						artifactName, artifactName, sandbox.getRoot().getAbsolutePath(),
						true, true, false, buildProperties, true,
						"Testing " + artifactName, new NullProgressMonitor());
			String buildResultUUID = setupArtifacts.get(
						TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
			String workItemId = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_WORKITEM_ID);
			RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
			ITeamRepository repository = testClient.getRepositoryConnection(connectionDetails).
												getTeamRepository();
			File tempFile = File.createTempFile("changelog_", "txt");
			File listenerFile = File.createTempFile("listenerFile_", "txt");
			String projectAreaName = ProcessUtil.getDefaultProjectArea(repository).getName();
			String sandboxPath = sandbox.getRoot().getAbsolutePath();
			OutputStream o = new FileOutputStream(tempFile);
			PrintStream listenerStream = new PrintStream(listenerFile);
			ChangeReport report = new ChangeReport(o);
			IConsoleOutput listener = new IConsoleOutput() {
				
				@Override
				public void log(String message, Exception e) {
					listenerStream.println(message);
					e.printStackTrace(listenerStream);
				}
				
				@Override
				public void log(String message) {
					listenerStream.println(message);
				}

				@Override
				public void debug(String message) {
					// TODO Auto-generated method stub
					
				}
			};
			AssertUtil.assertNotNull(listener);
			try {
				connection.accept(projectAreaName, 
						buildResultUUID, null, 
						null, null, null, 
						sandboxPath, 
						report, false,
						artifactName, null, 
						listener, new NullProgressMonitor(), 
						Locale.getDefault(), Integer.toString(300), 
						true, false, new HashMap<String, String>(), 
						artifactName, new HashMap<String, Object>());

				buildRequestPropertyValidator.validate(repository, buildResultUUID, workItemId);
				workItemLinkValidator.validate(repository, buildResultUUID, workItemId);
				buildresultContributonValidator.validate(repository, buildResultUUID, workItemId);
			} finally {
				o.close();
				listenerStream.close();
			}
		} finally {
			if (setupArtifacts != null) {
				testClient.tearDown(connectionDetails, setupArtifacts, new NullProgressMonitor());
			}
		}
	}

	interface IResultValidator {
		void validate(ITeamRepository repository, String buildResultItemId, String workItemId) 
															throws TeamRepositoryException;
	}
	
	private static void assertNoBuildResultProperty(ITeamRepository repository, 
					String buildResultItemId, String propertyName) throws TeamRepositoryException {
		IBuildResult result = getBuildResult(repository, buildResultItemId);
		IBuildRequest request = getBuildRequest(repository, result);
		AssertUtil.assertFalse("Did not expect property " + propertyName, 
						request.getBuildDefinitionProperties().containsKey(propertyName));
	}
	
	private static void assertEmptyBuildResultProperty(ITeamRepository repository, 
			String buildResultItemId, String propertyName) throws TeamRepositoryException {
		IBuildResult result = getBuildResult(repository, buildResultItemId);
		IBuildRequest request = getBuildRequest(repository, result);
		AssertUtil.assertTrue("Expected property " + propertyName, 
				request.getBuildDefinitionProperties().containsKey(propertyName));
		AssertUtil.assertTrue("Expected empty value for property " + propertyName, 
				"".equals((String)request.getBuildDefinitionProperties().get(propertyName)));
	}
	
	private static void assertBuildResultPropertyValue(ITeamRepository repository, 
				String buildResultItemId, String propertyName,
				String expectedValue) throws TeamRepositoryException {
		IBuildResult result = getBuildResult(repository, buildResultItemId);
		IBuildRequest request = getBuildRequest(repository, result);
		AssertUtil.assertTrue("Expected property " + propertyName, 
				request.getBuildDefinitionProperties().containsKey(propertyName));
		AssertUtil.assertTrue("Expected  value for property " + propertyName 
				+ " as " + expectedValue, 
				expectedValue.equals((String)request.getBuildDefinitionProperties().get(propertyName)));
	}
	
	private static void assertBuildResultContributions(ITeamRepository repository, String buildResultItemId,
			String contributionTypeId, int expectedContributionLength) throws TeamRepositoryException {
		IBuildResult result = getBuildResult(repository, buildResultItemId);
		// Get the build result contribution for work items and ensure that there is 
		// exactly one such contribution.
        IBuildResultContribution [] contributions = getTeamBuildClient(repository).
        			getBuildResultContributions(
        		result, 
        		contributionTypeId,
                new NullProgressMonitor());
        AssertUtil.assertTrue("Expected " + expectedContributionLength + 
        		"build result contributions for type " + contributionTypeId, 
        		expectedContributionLength == contributions.length);
	}
	
	private static void assertLinksInWorkItem(ITeamRepository repository, String workItemId,
			IEndPointDescriptor endPointDesc, int expectedLinksLength) throws TeamRepositoryException {
		IWorkItemClient wcl = getWorkItemClient(repository);
		IWorkItem wi = wcl.findWorkItemById(Integer.parseInt(workItemId), 
							IWorkItem.FULL_PROFILE, new NullProgressMonitor());
		IWorkItemReferences wiReferences = wcl.resolveWorkItemReferences(wi, new NullProgressMonitor());
		// Ensure that there are required number of links.
		List<IReference> includedArtifacts = wiReferences.getReferences(endPointDesc);
		AssertUtil.assertTrue("Expected " + expectedLinksLength + 
						" links in the work item  " + workItemId , 
						includedArtifacts.size() == expectedLinksLength);
	}
	
	@SuppressWarnings("deprecation")
	private static IEndPointDescriptor getIncludedInBuildsDescriptor() {
		return ILinkTypeRegistry.INSTANCE.getLinkType(BuildLinkTypes.INCLUDED_WORK_ITEMS).
		getSourceEndPointDescriptor();
	}
	
	private NullProgressMonitor getProgressMonitor() {
		return new NullProgressMonitor();
	}
	
	private ConsoleOutputHelper getListener() {
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		return listener;
	}
	
	private OutputStream getChangelog() throws IOException {
		File file = scratchFolder.newFile();
		OutputStream o = new FileOutputStream(file);
		return o;
	}

}
