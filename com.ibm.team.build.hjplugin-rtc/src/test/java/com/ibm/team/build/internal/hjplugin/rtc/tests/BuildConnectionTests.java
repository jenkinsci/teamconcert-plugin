/*******************************************************************************
 * Copyright (c) 2013, 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.client.ITeamBuildRequestClient;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.ScmConstants;
import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.BuildStatus;
import com.ibm.team.build.common.model.IBuildActivity;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildDefinitionHandle;
import com.ibm.team.build.common.model.IBuildEngine;
import com.ibm.team.build.common.model.IBuildEngineHandle;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestParams;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultContribution;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.client.workitem.WorkItemHelper;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.common.links.BuildLinkTypes;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConfiguration;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.build.internal.hjplugin.rtc.IBuildResultInfo;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.links.client.ILinkManager;
import com.ibm.team.links.common.IItemReference;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.ILinkCollection;
import com.ibm.team.links.common.ILinkQueryPage;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.factory.IReferenceFactory;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;

@SuppressWarnings({ "nls", "restriction" })
public class BuildConnectionTests {
	
	private final static List<String> TERMINATE_PROPERTIES = Arrays.asList(new String[] {
			IBuildResult.PROPERTY_BUILD_STATE,
			IBuildResult.PROPERTY_BUILD_STATUS,
			IBuildResult.PROPERTY_LABEL});

	private RepositoryConnection connection;

	private class BuildResultInfoDelegate implements IBuildResultInfo {
		private String buildResultUUID;
		private boolean ownLifeCycle;
		private boolean isPersonalBuild;
		private boolean isScheduled;
		private String requestor;

		public BuildResultInfoDelegate(String buildResultUUID) {
			this.buildResultUUID = buildResultUUID;
		}

		@Override
		public String getBuildResultUUID() {
			return buildResultUUID;
		}

		public boolean ownLifeCycle() {
			return ownLifeCycle;
		}

		@Override
		public void setOwnLifeCycle(boolean ownLifeCycle) {
			this.ownLifeCycle = ownLifeCycle;
		}

		public boolean isPersonalBuild() {
			return isPersonalBuild;
		}

		@Override
		public void setPersonalBuild(boolean isPersonalBuild) {
			this.isPersonalBuild = isPersonalBuild;
		}

		public String getRequestor() {
			return requestor;
		}

		@Override
		public void setRequestor(String requestor) {
			this.requestor = requestor;
		}

		public boolean isScheduled() {
			return isScheduled;
		}

		@Override
		public void setScheduled(boolean isScheduled) {
			this.isScheduled = isScheduled;
		}
	}
	
	public BuildConnectionTests(RepositoryConnection repositoryConnection) {
		this.connection = repositoryConnection;
	}

	public void verifyBuildResultContributions(Map<String, String> artifacts) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		ITeamBuildClient buildClient = (ITeamBuildClient) repo.getClientLibrary(ITeamBuildClient.class);
		
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(artifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID)), null);
		IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchCompleteItem(buildResultHandle, IItemManager.REFRESH, null);
		
		AssertUtil.assertEquals(BuildState.IN_PROGRESS, buildResult.getState());
		AssertUtil.assertTrue("Label should be set", buildResult.getLabel() != null && !buildResult.getLabel().isEmpty());
		AssertUtil.assertEquals(artifacts.get(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID), buildResult.getBuildDefinition().getItemId().getUuidValue());

        verifyBuildWorkspaceContribution(repo, buildClient, buildResult,
				artifacts);
        
        verifySnapshotContribution(repo, buildClient, buildResult, artifacts);
        verifyBuildActivity(repo, buildClient, buildResult);
        verifyWorkItemPublished(repo, buildClient, buildResult, 
        		artifacts.get("cs3wi1itemId"),
        		artifacts.get("cs4wi1itemId"),
        		artifacts.get("cs4wi2itemId"),
        		artifacts.get("cs4wi3itemId"),
        		artifacts.get("cs4wi4itemId"),
        		artifacts.get("cs4wi5itemId"));

	}

	private void verifyWorkItemPublished(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult,
			String... workItemIds) throws Exception {

        Set<String> workItems = new HashSet<String>();
        for (String id : workItemIds) {
        	workItems.add(id);
        }
        
        IWorkItemHandle[] fixedWorkItems = WorkItemHelper.getFixedInBuild(repo, buildResult, null);
        AssertUtil.assertEquals(workItems.size(), fixedWorkItems.length);

        for (IWorkItemHandle workItemHandle : fixedWorkItems) {
        	if (!workItems.contains(workItemHandle.getItemId().getUuidValue())) {
            	IWorkItem workItem = (IWorkItem) repo.itemManager().fetchCompleteItem(workItemHandle, IItemManager.REFRESH, null);
            	AssertUtil.fail("Unexpected work item " + workItem.getId() + " marked as fixed by build");
        	}
        	verifyWorkItemLinkToBuildResult(repo, workItemHandle, buildResult);
        }
	}

    private void verifyWorkItemLinkToBuildResult(ITeamRepository repo, IWorkItemHandle workItemHandle, IBuildResult buildResult)
            throws TeamRepositoryException {

        ILinkManager linkManager = (ILinkManager) repo.getClientLibrary(ILinkManager.class);

        ILinkQueryPage results = linkManager.findLinksByTarget(BuildLinkTypes.INCLUDED_WORK_ITEMS,
                IReferenceFactory.INSTANCE.createReferenceToItem(workItemHandle), null);

        ILinkCollection linkCollection = results.getAllLinksFromHereOn();

        boolean found = false;
        for (Iterator<ILink> i = linkCollection.iterator(); i.hasNext(); ) {
        	ILink link = i.next();
            IReference reference = link.getSourceRef();
            AssertUtil.assertTrue("reference " + reference.getComment() + " is not an item reference", reference.isItemReference());

            IItemHandle referencedItem = ((IItemReference) reference).getReferencedItem();
            AssertUtil.assertTrue("referencedItem is a " + referencedItem.getClass().getName() + " not an IBuildResultHandle", referencedItem instanceof IBuildResultHandle);

            if (buildResult.getItemId().getUuidValue().equals(referencedItem.getItemId().getUuidValue())) {
            	found = true;
            	break;
            }
        }
        if (!found) {
        	IWorkItem workItem = (IWorkItem) repo.itemManager().fetchCompleteItem(workItemHandle, IItemManager.REFRESH, null);
        	AssertUtil.fail("Work item " + workItem.getId() + " is missing link to build result");
        }
    }

	private void verifyBuildActivity(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult) throws Exception {
		IBuildActivity[] activities = buildClient.getBuildActivities(buildResult, null);
		AssertUtil.assertEquals(1, activities.length);
		IBuildActivity activity = activities[0];
		AssertUtil.assertEquals("Jazz Source Control setup", activity.getLabel());
		AssertUtil.assertTrue("activity is not complete", activity.isComplete());
		AssertUtil.assertEquals(2, activity.getChildActivities().length);
		AssertUtil.assertEquals("Accepting changes", activity.getChildActivities()[0].getLabel());
		AssertUtil.assertEquals("Fetching files", activity.getChildActivities()[1].getLabel());
	}

	private void verifySnapshotContribution(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult,
			Map<String, String> artifacts) throws TeamRepositoryException {
		// Verify the snapshot contribution was created
		IBaselineSetHandle baselineSetHandle = (IBaselineSetHandle) IBaselineSet.ITEM_TYPE.createItemHandle(UUID.valueOf(artifacts.get(TestSetupTearDownUtil.ARTIFACT_BASELINE_SET_ITEM_ID)), null);
        IBaselineSet baselineSet = (IBaselineSet) repo.itemManager().fetchCompleteItem(baselineSetHandle, IItemManager.REFRESH, null);
        IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(buildResult,
                ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_SNAPSHOT, null);

        AssertUtil.assertEquals(1, contributions.length);

        AssertUtil.assertEquals(NLS.bind("snapshot {0}", baselineSet.getName()), //$NON-NLS-1$
                contributions[0].getLabel());
        AssertUtil.assertFalse("Snapshot contribution impacts primary result", (contributions[0].isImpactsPrimaryResult()));
	}

	private void verifyBuildWorkspaceContribution(ITeamRepository repo,
			ITeamBuildClient buildClient, IBuildResult buildResult,
			Map<String, String> artifacts) throws TeamRepositoryException {
		// Verify the build result workspace contribution was created.
		IWorkspaceHandle workspaceHandle = (IWorkspaceHandle) IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(artifacts.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID)), null);
        IWorkspace workspace = (IWorkspace) repo.itemManager().fetchCompleteItem(workspaceHandle, IItemManager.REFRESH, null);
        IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(buildResult,
                ScmConstants.EXTENDED_DATA_TYPE_ID_BUILD_WORKSPACE, null);
        AssertUtil.assertEquals(1, contributions.length);

        AssertUtil.assertEquals(workspace.getName(), contributions[0].getLabel());
        AssertUtil.assertFalse("Workspace contribution impacts primary result", (contributions[0].isImpactsPrimaryResult()));

        AssertUtil.assertEquals(workspace.getItemId(),
                contributions[0].getExtendedContribution().getItemId());
	}

	public void testCreateBuildResult(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
			
			// create 2 workspaces, a build one and a personal build one
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			IWorkspaceConnection personalWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "2");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, personalWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// test the straight forward build result creation
			String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}

			BuildConfiguration buildConfiguration = new BuildConfiguration(repo, "");
			buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}

			AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
			BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
			AssertUtil.assertEquals(buildWorkspace.getContextHandle().getItemId(), workspaceDescriptor.getWorkspaceHandle().getItemId());
			AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
			
			artifactIds.remove(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
			BuildUtil.deleteBuildResult(repo, buildResultItemId);

			// test the creation of a personal build
			buildResultItemId = connection.createBuildResult(testName, personalWorkspace.getResolvedWorkspace().getName(), "my personal buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			
			buildConfiguration = new BuildConfiguration(repo, "");
			buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			
			AssertUtil.assertTrue("Should be a personal build", buildConfiguration.isPersonalBuild());
			workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
			AssertUtil.assertEquals(personalWorkspace.getContextHandle().getItemId(), workspaceDescriptor.getWorkspaceHandle().getItemId());
			AssertUtil.assertEquals("my personal buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	public void testCreateBuildResultFail(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			final Exception[] failure = new Exception[] {null};
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
			
			// create a build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			// no build engine for the build definition
			BuildUtil.createBuildDefinition(repo, testName, false, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// build result creation should fail
			try {
				String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
				if (failure[0] != null) {
					throw failure[0];
				}
				AssertUtil.fail("Without a build engine, the result should not be able to be created");
			} catch (Exception e) {
				// expected
				AssertUtil.assertTrue("Unexpected exception encountered " + e.getMessage(), e instanceof RTCConfigurationException);
			}
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}
	
	public void testMetronomeLogsInBuildResult(String buildResultUUID) throws Exception {
		helperTestLogContributionsInBuildResult(buildResultUUID, 2);
	}
	
	public void testNoMetronomeLogsInBuildResult(String buildResultUUID) throws Exception {
		helperTestLogContributionsInBuildResult(buildResultUUID, 0);
	}
	
	private void helperTestLogContributionsInBuildResult(String buildResultUUID, int count) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		ITeamBuildClient buildClient = (ITeamBuildClient) repo.getClientLibrary(ITeamBuildClient.class);
		IBuildResultHandle resultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(
							UUID.valueOf(buildResultUUID), null);
		IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(
				resultHandle, IBuildResultContribution.LOG_EXTENDED_CONTRIBUTION_ID, 
				new NullProgressMonitor());
		Assert.isTrue(contributions.length == count);
	}

	public void testExternalLinks(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
			
			// create build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, 
					buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, 
					buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			
			// create a build result
			String buildResultItemId = connection.createBuildResult(testName, null, 
						"external links test 1", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}

			// Add external links
			connection.getBuildConnection().createBuildLinks(buildResultItemId, 
					"http://localHost:8080", "myJob", "myJob/2",
					listener, null);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			
			// verify the links are on the build result
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			ITeamBuildClient buildClient = (ITeamBuildClient) repo.getClientLibrary(ITeamBuildClient.class);
			IBuildResultContribution[] contributions = buildClient.getBuildResultContributions(buildResultHandle, IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, null);
			AssertUtil.assertEquals(2, contributions.length);
			for (IBuildResultContribution contribution : contributions) {
				AssertUtil.assertEquals(IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, 
						contribution.getExtendedContributionTypeId());
				if (contribution.getLabel().equals("Hudson/Jenkins Job")) {
					AssertUtil.assertEquals("http://localHost:8080/myJob", 
							contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else if (contribution.getLabel().equals("Hudson/Jenkins Build")) {
					AssertUtil.assertEquals("http://localHost:8080/myJob/2", 
							contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else {
					AssertUtil.fail("Unexpected contribution " + contribution.getLabel());
				}
			}
			
			// create another build result
			BuildUtil.deleteBuildResult(repo, buildResultItemId);
			buildResultItemId = connection.createBuildResult(testName, null, "external links test 2", 
								listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}

			// test creating links when we could not get the hudson url
			connection.getBuildConnection().createBuildLinks(buildResultItemId, null, "anotherJob", "anotherJob/44", listener, null);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}

			// verify the links are on the build result
			buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
			contributions = buildClient.getBuildResultContributions(buildResultHandle, IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, null);
			AssertUtil.assertEquals(2, contributions.length);
			for (IBuildResultContribution contribution : contributions) {
				AssertUtil.assertEquals(IBuildResultContribution.LINK_EXTENDED_CONTRIBUTION_ID, contribution.getExtendedContributionTypeId());
				if (contribution.getLabel().equals("Hudson/Jenkins Job")) {
					AssertUtil.assertEquals("http://junit.ottawa.ibm.com:8081/anotherJob", contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else if (contribution.getLabel().equals("Hudson/Jenkins Build")) {
					AssertUtil.assertEquals("http://junit.ottawa.ibm.com:8081/anotherJob/44", contribution.getExtendedContributionProperty(IBuildResultContribution.PROPERTY_NAME_URL));
				} else {
					AssertUtil.fail("Unexpected contribution " + contribution.getLabel());
				}
			}
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	public void testBuildStart(String testName) throws Exception {
		// Test create (pending) & start (true)
		// Test create (in progress) & start (false)
		// Test create (complete) & start (false)
		// Test create (cancelled = pending & cancelled) & start (operation cancelled)
		// Test create (incomplete = inprogress & cancelled) & start (operation cancelled)
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			// create a build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");

			// Test create (pending) & start (true)
			requestBuild(repo, null, testName, artifactIds);
			startAndVerify(testName, repo, true, BuildState.IN_PROGRESS, artifactIds);

			// Test create (in progress) & start (false)
			requestBuild(repo, BuildState.IN_PROGRESS, testName, artifactIds);
			startAndVerify(testName, repo, false, BuildState.IN_PROGRESS, artifactIds);

			// Test create (complete) & start (false)
			requestBuild(repo, BuildState.COMPLETED, testName, artifactIds);
			startAndVerify(testName, repo, false, BuildState.COMPLETED, artifactIds);

			// Test create (cancelled = pending & canceled) & start (operation canceled)
			requestBuild(repo, BuildState.CANCELED, testName, artifactIds);
			try {
				startAndVerify(testName, repo, false, BuildState.CANCELED, artifactIds);
				AssertUtil.fail("Expected start to result in build being canceled");
			} catch (OperationCanceledException e) {
				// good 
			}
			
			// Test create (incomplete = inprogress & cancelled) & start (operation cancelled)
			requestBuild(repo, BuildState.INCOMPLETE, testName, artifactIds);
			try {
				startAndVerify(testName, repo, false, BuildState.INCOMPLETE, artifactIds);
				AssertUtil.fail("Expected start to result in build being canceled");
			} catch (OperationCanceledException e) {
				// good
			}

		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}
	}

	public Map<String, String> testBuildTerminationSetup(String testName) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			// create a build workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");

			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ID, testName);
			
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}
	
	/**
	 * Sets up a build result in the requested state & status
	 * The result can then later be terminated and then verified via {@link #verifyBuildTermination(String, String, Map)}
	 * @param startBuild Whether to start the build or leave it pending
	 * @param abandon Whether the build should be abandoned after being started
	 * @param buildStatus The status to put on the started build
	 * @param artifactIds Ids of the artifacts in play
	 * @throws Exception thrown if anything goes wrong with the setup
	 */
	public void testBuildTerminationTestSetup(boolean startBuild,
			boolean abandon, String buildStatus, Map<String, String> artifactIds)
			throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		String buildDefinitionId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ID);
		if (startBuild) {
			String buildResultItemId = startBuild(repo, buildDefinitionId, artifactIds);
			BuildStatus status = BuildStatus.valueOf(buildStatus);
			if (abandon || status != BuildStatus.OK) {
				setBuildStatus(repo, buildResultItemId, abandon, status);
			}
		} else {
			requestBuild(repo, BuildState.NOT_STARTED, buildDefinitionId, artifactIds);
		}
	}

	/**
	 * Verify that after termination that the build result is in the expected state
	 * with the expected status.
	 * @param expectedState The expected build state
	 * @param expectedStatus The expected build status
	 * @param artifactIds The artifacts now in play
	 * @throws TeamRepositoryException Thrown if anything goes wrong
	 */
	public void verifyBuildTermination(String expectedState, String expectedStatus,
			Map<String, String> artifactIds)
			throws TeamRepositoryException {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();

		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle;
		IBuildResult buildResult;
		buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		AssertUtil.assertEquals(expectedState, buildResult.getState().name());
		AssertUtil.assertEquals(expectedStatus, buildResult.getStatus().name());
	}

	/**
	 * Verify that the build result really has been deleted
	 * @param artifactIds The artifacts now in play
	 * @throws TeamRepositoryException Thrown if anything goes wrong
	 */
	public void verifyBuildResultDeleted(Map<String, String> artifactIds)
			throws TeamRepositoryException {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();

		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle;
		buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		try {
			repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
			AssertUtil.fail("Build result found for " + buildResultItemId);
		} catch (ItemNotFoundException e) {
			// good
		}
	}

	private static void setBuildStatus(ITeamRepository repo, String buildResultItemId,
			boolean abandon, BuildStatus status) throws Exception {
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		buildResult = (IBuildResult) buildResult.getWorkingCopy();
		buildResult.setStatus(status);
		ClientFactory.getTeamBuildClient(repo).save(buildResult, new NullProgressMonitor());
		if (abandon) {
			ClientFactory.getTeamBuildRequestClient(repo).makeBuildIncomplete(buildResultHandle, new String[] {
					IBuildResult.PROPERTY_BUILD_STATE,
					IBuildResult.PROPERTY_BUILD_STATUS}, null);
		}
	}

	private String startBuild(ITeamRepository repo, String testName,
			Map<String, String> artifactIds) throws Exception,
			TeamRepositoryException {
		ConsoleOutputHelper listener = new ConsoleOutputHelper();

		String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
		if (listener.hasFailure()) {
			throw listener.getFailure();
		}

		// make sure the build is in progress
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		AssertUtil.assertEquals(BuildState.IN_PROGRESS, buildResult.getState());
		return buildResultItemId;
	}

	public static void requestBuild(ITeamRepository repo, BuildState initialState, String testName,
			Map<String, String> artifactIds) throws Exception, TeamRepositoryException {
		
		String engineItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID);
		String defnItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID);
		IBuildEngineHandle buildEngineHandle = (IBuildEngineHandle) IBuildEngine.ITEM_TYPE.createItemHandle(UUID.valueOf(engineItemId), null);
		IBuildDefinitionHandle buildDefinitionHandle = (IBuildDefinitionHandle) IBuildDefinition.ITEM_TYPE.createItemHandle(UUID.valueOf(defnItemId), null);
		
        IBuildRequestParams params = BuildItemFactory.createBuildRequestParams();
        params.setBuildDefinition(buildDefinitionHandle);
        params.setAllowDuplicateRequests(true);
        params.setPersonalBuild(false);
        params.getPotentialHandlers().add(buildEngineHandle);
        params.setStartBuild(false);
        ITeamBuildRequestClient service = (ITeamBuildRequestClient) repo.getClientLibrary(ITeamBuildRequestClient.class);
        IBuildRequest buildRequest = service.requestBuild(params, null);
        IBuildResultHandle buildResultHandle = buildRequest.getBuildResult();
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultHandle.getItemId().getUuidValue());
		
		service.claimRequest(buildRequest, buildEngineHandle, IBuildRequest.PROPERTIES_REQUIRED, null);
		
		if (initialState == BuildState.IN_PROGRESS) {
    		service.startBuild(buildRequest, new String[0], null);
		} else if (initialState == BuildState.CANCELED) {
			service.cancelPendingRequest(buildRequest, new String[0], null);
		} else if (initialState == BuildState.INCOMPLETE) {
    		service.startBuild(buildRequest, new String[0], null);
			setBuildStatus(repo, buildResultHandle.getItemId().getUuidValue(), true, BuildStatus.OK);
		} else if (initialState == BuildState.COMPLETED) {
    		service.startBuild(buildRequest, new String[0], null);
			setBuildStatus(repo, buildResultHandle.getItemId().getUuidValue(), false, BuildStatus.OK);
    		service.makeBuildComplete(buildResultHandle, false, new String[0], null);
		} // else initialState == BuildState.NOT_STARTED
	}

	private void startAndVerify(String label,
			ITeamRepository repo,
			boolean expectedResult,
			BuildState expectedState, Map<String, String> artifactIds)
					throws Exception, TeamRepositoryException {
		final Exception[] failure = new Exception[] {null};
		ConsoleOutputHelper listener = new ConsoleOutputHelper();

		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		OperationCanceledException cancelException = null;
		try {
			BuildResultInfoDelegate buildResultInfoDelegate = new BuildResultInfoDelegate(buildResultItemId);
			connection.startBuild(buildResultInfoDelegate, label, listener, null, Locale.getDefault());
			AssertUtil.assertEquals(expectedResult, buildResultInfoDelegate.ownLifeCycle());
			
			if (failure[0] != null) {
				throw failure[0];
			}
		} catch (OperationCanceledException e) {
			cancelException  = e;
		}
		
		// verify
		IBuildResultHandle buildResultHandle;
		IBuildResult buildResult;
		buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);
		buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(buildResultHandle, 
				IItemManager.REFRESH, TERMINATE_PROPERTIES, null);
		AssertUtil.assertEquals(expectedState, buildResult.getState());
		if (expectedResult) {
			// label will only be set if we started build
			AssertUtil.assertEquals(label, buildResult.getLabel());
		}
		
		// delete the build result artifact
		BuildUtil.deleteBuildResult(repo, buildResultItemId);
		artifactIds.remove(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		
		if (cancelException != null) {
			throw cancelException;
		}
	}
	
	public String testBuildResultInfo(String testName, final IBuildResultInfo buildResultInfo) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			ConsoleOutputHelper listener = new ConsoleOutputHelper();
			
			// create 2 workspaces, a build one and a personal build one
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "1");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			IWorkspaceConnection personalWorkspace = SCMUtil.createWorkspace(workspaceManager, testName + "2");
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, personalWorkspace.getResolvedWorkspace().getItemId().getUuidValue());
			
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");

			// test a personal build
			final String buildResultItemId = connection.createBuildResult(testName, personalWorkspace.getResolvedWorkspace().getName(), "my personal buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (listener.hasFailure()) {
				throw listener.getFailure();
			}
			
			BuildResultInfoDelegate buildResultInfoDelegate = new BuildResultInfoDelegate(buildResultItemId);
			BuildConnection buildConnection = new BuildConnection(repo);
			buildConnection.startBuild(buildResultInfoDelegate, "testGettingInfo", listener, new NullProgressMonitor(), Locale.getDefault());
			buildResultInfo.setOwnLifeCycle(buildResultInfoDelegate.ownLifeCycle());
			buildResultInfo.setPersonalBuild(buildResultInfoDelegate.isPersonalBuild());
			buildResultInfo.setRequestor(buildResultInfoDelegate.getRequestor());
			buildResultInfo.setScheduled(buildResultInfoDelegate.isScheduled());
			
			return repo.loggedInContributor().getName();
		} finally {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
		}

	}
}
