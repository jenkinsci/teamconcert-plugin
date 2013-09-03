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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.hjplugin.rtc.BuildClient;
import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.IBuildResultInfo;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceConnection.IConfigurationOp;
import com.ibm.team.scm.client.IWorkspaceConnection.IMarkAsMergedOp;
import com.ibm.team.scm.client.IWorkspaceConnection.IRevertOp;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.AcceptFlags;
import com.ibm.team.scm.common.IChange;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.dto.IItemConflictReport;
import com.ibm.team.scm.common.dto.IUpdateReport;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;

@SuppressWarnings("nls")
public class TestSetupTearDownUtil extends BuildClient {
	
	public static final String ARTIFACT_WORKSPACE_ITEM_ID = "workspaceItemId";
	public static final String ARTIFACT_STREAM_ITEM_ID = "streamItemId";
	public static final String ARTIFACT_COMPONENT1_ITEM_ID = "component1ItemId";
	public static final String ARTIFACT_BASELINE_SET_ITEM_ID = "baselineSetItemId";
	public static final String ARTIFACT_BUILD_DEFINITION_ITEM_ID = "buildDefinitionItemId";
	public static final String ARTIFACT_BUILD_ENGINE_ITEM_ID = "buildEngineItemId";
	public static final String ARTIFACT_BUILD_RESULT_ITEM_ID = "buildResultItemId";
	
	public TestSetupTearDownUtil() {
	}
	
	public Map<String, String> setupComponentChanges(ConnectionDetails connectionDetails, String workspaceName,
			String componentAddedName, String componentDroppedName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		Map<String, IItemHandle> cAdded = SCMUtil.addComponent(workspaceManager, buildStream, componentAddedName, new String[] {componentAddedName + "/" });
		Map<String, IItemHandle> cDropped = SCMUtil.addComponent(workspaceManager, buildWorkspace, componentDroppedName, new String[] {componentDroppedName + "/" });
		
		Map<String, String> result = new HashMap<String, String>();
		result.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		result.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		result.put("componentAddedItemId", ((IComponent) cAdded.get(componentAddedName)).getItemId().getUuidValue());
		result.put("componentDroppedItemId", ((IComponent) cDropped.get(componentDroppedName)).getItemId().getUuidValue());
		return result;
	}
	
	public Map<String, String> setupAcceptChanges(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, IProgressMonitor progress) throws Exception {
				
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				c1 + "/f/c.txt",
				c1 + "/f/d.txt",
				c1 + "/f/n.txt",
				c1 + "/f/tree/",
				c1 + "/f/tree/e.txt",
				c1 + "/f2/",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/a.txt", pathToHandle.get(c1 + "/f/a.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/c.txt", pathToHandle.get(c1 + "/f/c.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/e.txt", pathToHandle.get(c1 + "/f/tree/e.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/", pathToHandle.get(c1 + "/f/tree/").getItemId().getUuidValue());
		
		createChangeSet1(repo, buildStream, component, c1, pathToHandle, artifactIds, false);
		createChangeSet2(repo, buildStream, component, c1, pathToHandle, artifactIds);
		createChangeSet3(repo, buildStream, component, c1, pathToHandle, artifactIds, false);
		createChangeSet4(repo, buildStream, component, pathToHandle, artifactIds);
		createEmptyChangeSets(repo, buildStream, component, artifactIds, 5, 1);
		createNoopChangeSets(repo, buildStream, component, c1, pathToHandle, artifactIds, 6);
		createComponentRootChangeSet(repo, buildStream, component, pathToHandle, artifactIds, 8);
		
		return artifactIds;
	}

	private void createChangeSet1(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component, String root,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts,
			boolean forDiscard)
			throws TeamRepositoryException {
		// cs1 modifies a.txt
		//     modifies & renames bRenamed.txt
		//     reparents c.txt
		IChangeSetHandle cs1 = workspace.createChangeSet(component, null);
		SCMUtil.modifyFiles(workspace, component, cs1, pathToHandle, new String[] {
				root + "/f/a.txt",
				root + "/f/b.txt",
		});
		SCMUtil.moveVersionable(workspace, component, cs1, pathToHandle, 
				root + "/f/b.txt",
				root + "/f/bRenamed.txt"
		);
		SCMUtil.moveVersionable(workspace, component, cs1, pathToHandle, 
				root + "/f/c.txt",
				root + "/f2/c.txt"
		);
		workspace.closeChangeSets(Collections.singletonList(cs1), null);
		
		IChangeSet changeSet = (IChangeSet) repo.itemManager().fetchCompleteItem(cs1, IItemManager.DEFAULT, null);
		for (IChange change : (List<IChange>) changeSet.changes()) {
			IVersionableHandle state = change.afterState();
			if (state == null) {
				state = change.beforeState();
			}
			artifacts.put(state.getItemId().getUuidValue(), state.getStateId().getUuidValue());
		}
		artifacts.put("cs1", cs1.getItemId().getUuidValue());
		if (forDiscard) {
			// it will be known by the name currently and that won't be bRename.txt (or f2/c.txt) if discarded
			artifacts.put(root + "/f/b.txt", pathToHandle.get(root + "/f/bRenamed.txt").getItemId().getUuidValue());
			artifacts.put(root + "/f/c.txt", pathToHandle.get(root + "/f2/c.txt").getItemId().getUuidValue());
		} else {
			artifacts.put(root + "/f/bRenamed.txt", pathToHandle.get(root + "/f/bRenamed.txt").getItemId().getUuidValue());
			artifacts.put(root + "/f2/c.txt", pathToHandle.get(root + "/f2/c.txt").getItemId().getUuidValue());
		}
	}

	private void createChangeSet2(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component, String root,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts)
			throws TeamRepositoryException {
		
		// cs2 has a comment
		//     deletes f/tree/  f/tree/e.txt 
		IChangeSetHandle cs2 = workspace.createChangeSet(component, "comment of the change set", false, null);
		SCMUtil.deleteVersionables(workspace, component, cs2, pathToHandle, new String[] {
				root + "/f/tree/",
				root + "/f/tree/e.txt",
		});
		workspace.closeChangeSets(Collections.singletonList(cs2), null);

		IChangeSet changeSet = (IChangeSet) repo.itemManager().fetchCompleteItem(cs2, IItemManager.DEFAULT, null);
		for (IChange change : (List<IChange>) changeSet.changes()) {
			IVersionableHandle state = change.afterState();
			if (state == null) {
				state = change.beforeState();
			}
			artifacts.put(state.getItemId().getUuidValue(), state.getStateId().getUuidValue());
		}
		artifacts.put("cs2", cs2.getItemId().getUuidValue());
	}

	private void createChangeSet3(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component, String root,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts, boolean forDiscard)
			throws TeamRepositoryException {
		
		// cs3 adds newTree/ newTree/newFile.txt
		// cs3 has a work item associated with it but no comment
		// The workitems are dependent on the test repo being setup with some work items
		IChangeSetHandle cs3 = workspace.createChangeSet(component, "", false, null);
		SCMUtil.addVersionables(workspace, component, cs3, pathToHandle, new String[] {
				root + "/f/newTree/",
				root + "/f/newTree/newFile.txt"
		});
		List<IWorkItemHandle> workItems = WorkItemUtil.findSomeWorkItems(repo, 1);
		if (!workItems.isEmpty()) {
			SCMUtil.createWorkItemChangeSetLink(repo, new IWorkItemHandle[] { workItems.get(0) }, cs3);
			IWorkItem fullWorkItem = (IWorkItem) repo.itemManager().fetchCompleteItem(workItems.get(0), IItemManager.DEFAULT, null);
			artifacts.put(Integer.toString(fullWorkItem.getId()), fullWorkItem.getHTMLSummary().toString());
			artifacts.put("cs3wi1", Integer.toString(fullWorkItem.getId()));
			artifacts.put("cs3wi1itemId", fullWorkItem.getItemId().getUuidValue());
		}
		workspace.closeChangeSets(Collections.singletonList(cs3), null);

		IChangeSet changeSet = (IChangeSet) repo.itemManager().fetchCompleteItem(cs3, IItemManager.DEFAULT, null);
		for (IChange change : (List<IChange>) changeSet.changes()) {
			IVersionableHandle state = change.afterState();
			if (state == null) {
				state = change.beforeState();
			}
			artifacts.put(state.getItemId().getUuidValue(), state.getStateId().getUuidValue());
		}
		artifacts.put("cs3", cs3.getItemId().getUuidValue());
		if (forDiscard) {
			// paths will be unknown
			artifacts.put("/<unknown>/newTree/", pathToHandle.get(root + "/f/newTree/").getItemId().getUuidValue());
			artifacts.put("/<unknown>/newFile.txt", pathToHandle.get(root + "/f/newTree/newFile.txt").getItemId().getUuidValue());
		} else {
			artifacts.put(root + "/f/newTree/", pathToHandle.get(root + "/f/newTree/").getItemId().getUuidValue());
			artifacts.put(root + "/f/newTree/newFile.txt", pathToHandle.get(root + "/f/newTree/newFile.txt").getItemId().getUuidValue());
		}
	}

	private void createChangeSet4(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts)
			throws TeamRepositoryException {
		List<IWorkItemHandle> workItems;
		// cs4 adds newTree2/ and 256 files below it (making it too large to show)
		// cs4 has a comment and 5 workitems associated with it.
		// The workitems are dependent on the test repo being setup with some work items
		IChangeSetHandle cs4 = workspace.createChangeSet(component, "Share", false, null);
		String[] toAdd = new String[258];
		toAdd[0] = "/newTree2/";
		for (int i = 0; i < 257; i++) {
			toAdd[i+1] = "/newTree2/newF" + i + ".txt";
		}
		SCMUtil.addVersionables(workspace, component, cs4, pathToHandle, toAdd);
		workItems = WorkItemUtil.findSomeWorkItems(repo, 5);
		if (!workItems.isEmpty()) {
			SCMUtil.createWorkItemChangeSetLink(repo, workItems.toArray(new IWorkItemHandle[workItems.size()]), cs4);
			List<IWorkItem> fullWorkItems = repo.itemManager().fetchCompleteItems(workItems, IItemManager.DEFAULT, null);
			int i = 1;
			for (IWorkItem wi : fullWorkItems) {
				artifacts.put(Integer.toString(wi.getId()), wi.getHTMLSummary().toString());
				artifacts.put("cs4wi" + i, Integer.toString(wi.getId()));
				artifacts.put("cs4wi" + i + "itemId", wi.getItemId().getUuidValue());
				i++;
			}
		}
		workspace.closeChangeSets(Collections.singletonList(cs4), null);
		artifacts.put("cs4", cs4.getItemId().getUuidValue());
	}

	public void tearDown(ConnectionDetails connectionDetails,
			Map<String, String> setupArtifacts, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		// Can't delete components just the workspaces
		SCMUtil.deleteWorkspace(repo, setupArtifacts.get(ARTIFACT_WORKSPACE_ITEM_ID));
		SCMUtil.deleteWorkspace(repo, setupArtifacts.get(ARTIFACT_STREAM_ITEM_ID));
		SCMUtil.deleteWorkspace(repo, setupArtifacts.get("singleWorkspaceItemId"));
		SCMUtil.deleteWorkspace(repo, setupArtifacts.get("multipleWorkspaceItemId1"));
		SCMUtil.deleteWorkspace(repo, setupArtifacts.get("multipleWorkspaceItemId2"));
		
		// Delete the build defn related artifacts
		BuildUtil.deleteBuildArtifacts(repo, setupArtifacts);
	}

	public Map<String, String> setupTestBuildWorkspace(ConnectionDetails connectionDetails, String singleWorkspaceName,
			String multipleWorkspaceName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, singleWorkspaceName);
		IWorkspaceConnection buildWorkspace2 = SCMUtil.createWorkspace(workspaceManager, multipleWorkspaceName);
		IWorkspaceConnection buildWorkspace3 = SCMUtil.createWorkspace(workspaceManager, multipleWorkspaceName);
		
		Map<String, String> result = new HashMap<String, String>();
		result.put("singleWorkspaceItemId", buildWorkspace.getContextHandle().getItemId().getUuidValue());
		result.put("multipleWorkspaceItemId1", buildWorkspace2.getContextHandle().getItemId().getUuidValue());
		result.put("multipleWorkspaceItemId2", buildWorkspace3.getContextHandle().getItemId().getUuidValue());
		repo.logout();
		return result;
	}

	public Map<String, String> setupAcceptDiscardChanges(
			ConnectionDetails connectionDetails, String workspaceName,
			String componentName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				c1 + "/f/c.txt",
				c1 + "/f/d.txt",
				c1 + "/f/n.txt",
				c1 + "/f/tree/",
				c1 + "/f/tree/e.txt",
				c1 + "/f2/",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/a.txt", pathToHandle.get(c1 + "/f/a.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/c.txt", pathToHandle.get(c1 + "/f/c.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/e.txt", pathToHandle.get(c1 + "/f/tree/e.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/", pathToHandle.get(c1 + "/f/tree/").getItemId().getUuidValue());
		
		// cs1 & cs2 are accepted
		createChangeSet1(repo, buildStream, component, c1, pathToHandle, artifactIds, false);
		createChangeSet2(repo, buildStream, component, c1, pathToHandle, artifactIds);

		// since CS3 & CS4 & CS5 & CS7 & CS8 are in buildWorkspace and not the build stream it will be the discarded cs
		// This is to ensure no deviation in processing for discarded change sets, but also because some of the paths can't be resolved.
		// Any adds discarded are going to have difficulties
		createChangeSet3(repo, buildWorkspace, component, c1, pathToHandle, artifactIds, true);
		createChangeSet4(repo, buildWorkspace, component, pathToHandle, artifactIds);
		createEmptyChangeSets(repo, buildWorkspace, component, artifactIds, 5, 1);
		createNoopChangeSets(repo, buildWorkspace, component, c1, pathToHandle, artifactIds, 6);
		createComponentRootChangeSet(repo, buildWorkspace, component, pathToHandle, artifactIds, 8);

		return artifactIds;
	}

	public Map<String, String> setupEmptyChangeSets(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		
		createEmptyChangeSets(repo, buildStream, component, artifactIds, 1, 2);
		
		createEmptyChangeSets(repo, buildWorkspace, component, artifactIds, 3, 1);

		return artifactIds;
		
	}
	
	private void createEmptyChangeSets(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component,
			Map<String, String> artifactIds, int csStartNumber, int count) throws TeamRepositoryException {
		for (int i = 0; i < count; i++) {
			IChangeSetHandle cs = workspace.createChangeSet(component,"empty change set", false, null);
			workspace.closeChangeSets(Collections.singletonList(cs), null);
			artifactIds.put("cs" + (csStartNumber + i), cs.getItemId().getUuidValue());
		}
	}
	
	public Map<String, String> setupNoopChanges(ConnectionDetails connectionDetails,
			String workspaceName, String componentName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				c1 + "/f/c.txt",
				c1 + "/f/d.txt",
				c1 + "/f/n.txt",
				c1 + "/f/tree/",
				c1 + "/f/tree/e.txt",
				c1 + "/f2/",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/n.txt", pathToHandle.get(c1 + "/f/n.txt").getItemId().getUuidValue());

		createNoopChangeSets(repo, buildStream, component, c1, pathToHandle, artifactIds, 1);

		return artifactIds;
	}
	
	private void createNoopChangeSets(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component, String c1,
			Map<String, IItemHandle> pathToHandle,
			Map<String, String> artifactIds, int changeSetNumber) throws TeamRepositoryException {
		// Two types of noop changes created
		// 1) file is added & deleted within the cs -> no state
		// 2) file is changed & restored to original state (before == after)
		// we will introduce a merge state so that the change will remain (otherwise the server just removes it from the change set)
		IChangeSetHandle csSuspend = workspace.createChangeSet(component, "change set to force conflict", false, null);
		SCMUtil.modifyFiles(workspace, component, csSuspend, pathToHandle, new String[] {
				c1 + "/f/n.txt"
		});
		SCMUtil.addVersionables(workspace, component, csSuspend, pathToHandle, new String[] {
				c1 + "/f/NoopFolder/",
				c1 + "/f/NoopFolder/Noop.txt",
		});
		workspace.closeChangeSets(Collections.singletonList(csSuspend), null);
		workspace.suspend(Collections.singletonList(csSuspend), null);

		IChangeSetHandle cs = workspace.createChangeSet(component, "Noop change set", false, null);
		IFileItem current = (IFileItem) workspace.configuration(component).fetchCompleteItem((IFileItemHandle) pathToHandle.get(c1 + "/f/n.txt"), null);
		SCMUtil.modifyFiles(workspace, component, cs, pathToHandle, new String[] {
				c1 + "/f/n.txt"
		});
		SCMUtil.addVersionables(workspace, component, cs, pathToHandle, new String[] {
				c1 + "/f/NoopFolder/",
				c1 + "/f/NoopFolder/Noop.txt",
		});
		
		// resume the suspended change set to get conflicts
		workspace.resume(AcceptFlags.DEFAULT, Collections.singletonList(csSuspend), null);
		// resolve conflicts with resolve with mine
		IUpdateReport conflictReport = workspace.conflictReport();
		List<IConfigurationOp> ops = new ArrayList<IWorkspaceConnection.IConfigurationOp>();
		for (IItemConflictReport conflict : conflictReport.conflicts()) {
			IMarkAsMergedOp resolveOp = workspace.configurationOpFactory().markAsMerged(conflict.item(), 
					conflict.getSelectedContributorState(), conflict.getProposedContributorState());
			ops.add(resolveOp);
		}
		workspace.commit(cs, ops, null);
		
		// restore original state
		IRevertOp configOp = workspace.configurationOpFactory().revert(current);
		workspace.commit(cs, Collections.singletonList(configOp), null);
		
		// delete same versionable added
		artifactIds.put(c1 + "/f/n.txt", pathToHandle.get(c1 + "/f/n.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/NoopFolder/", pathToHandle.get(c1 + "/f/NoopFolder/").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/NoopFolder/Noop.txt", pathToHandle.get(c1 + "/f/NoopFolder/Noop.txt").getItemId().getUuidValue());
		SCMUtil.deleteVersionables(workspace, component, cs, pathToHandle, new String[] {
				c1 + "/f/NoopFolder/Noop.txt",
				c1 + "/f/NoopFolder/",
		});
		workspace.closeChangeSets(Collections.singletonList(cs), null);
		
		IChangeSet changeSet = (IChangeSet) repo.itemManager().fetchCompleteItem(cs, IItemManager.DEFAULT, null);
		for (IChange change : (List<IChange>) changeSet.changes()) {
			IVersionableHandle state = change.afterState();
			if (state == null) {
				state = change.beforeState();
			}
			if (state != null) {
				artifactIds.put(state.getItemId().getUuidValue(), state.getStateId().getUuidValue());
			}
		}
		artifactIds.put("cs" + changeSetNumber, csSuspend.getItemId().getUuidValue());
		artifactIds.put("cs" + (changeSetNumber + 1), cs.getItemId().getUuidValue());
	}

	public Map<String, String> setupComponentRootChange(
			ConnectionDetails connectionDetails, String workspaceName,
			String componentName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				c1 + "/",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());

		createComponentRootChangeSet(repo, buildStream, component, pathToHandle, artifactIds, 1);

		return artifactIds;
	}

	private void createComponentRootChangeSet(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component,
			Map<String, IItemHandle> pathToHandle,
			Map<String, String> artifactIds, int changeSetNumber) throws Exception {
		// TODO Auto-generated method stub
		artifactIds.put("/", component.getRootFolder().getItemId().getUuidValue());
		IChangeSetHandle cs = workspace.createChangeSet(component, "Component Root property changes", false, null);
		artifactIds.put("cs" + changeSetNumber, cs.getItemId().getUuidValue());
		SCMUtil.makePropertyChanges(workspace, component, cs, component.getRootFolder());
		workspace.closeChangeSets(Collections.singletonList(cs), null);
		IChangeSet changeSet = (IChangeSet) repo.itemManager().fetchCompleteItem(cs, IItemManager.DEFAULT, null);
		for (IChange change : (List<IChange>) changeSet.changes()) {
			IVersionableHandle state = change.afterState();
			if (state == null) {
				state = change.beforeState();
			}
			artifactIds.put(state.getItemId().getUuidValue(), state.getStateId().getUuidValue());
		}

	}

	public Map<String, String> setupMultipleComponentChanges(ConnectionDetails connectionDetails,
			String workspaceName, String componentPrefix, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String componentName1 = componentPrefix + "1";
		String componentName2 = componentPrefix + "2";
		String componentName3 = componentPrefix + "3";
		String componentName4 = componentPrefix + "4";
		
		String c1 = "/" + componentName1;
		String c2 = "/" + componentName2;
		String c3 = "/" + componentName3;
		String c4 = "/" + componentName4;
		
		Map<String, IItemHandle> pathToHandle1 = SCMUtil.addComponent(workspaceManager, buildStream, componentName1, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				c1 + "/f/c.txt",
				c1 + "/f/d.txt",
				c1 + "/f/tree/",
				c1 + "/f/tree/e.txt",
				c1 + "/f2/",
		});

		Map<String, IItemHandle> pathToHandle2 = SCMUtil.addComponent(workspaceManager, buildStream, componentName2, new String[] {
				c2 + "/",
				c2 + "/f/",
		});

		Map<String, IItemHandle> pathToHandle3 = SCMUtil.addComponent(workspaceManager, buildStream, componentName3, new String[] {
				c3 + "/",
				c3 + "/f/",
				c3 + "/f/n.txt",
		});

		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);

		Map<String, IItemHandle> pathToHandle4 = SCMUtil.addComponent(workspaceManager, buildStream, componentName4, new String[] {
				c4 + "/",
		});

		IComponent component1 = (IComponent) pathToHandle1.get(componentName1);
		IComponent component2 = (IComponent) pathToHandle2.get(componentName2);
		IComponent component3 = (IComponent) pathToHandle3.get(componentName3);
		IComponent component4 = (IComponent) pathToHandle4.get(componentName4);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component1.getItemId().getUuidValue());
		artifactIds.put("component2ItemId", component2.getItemId().getUuidValue());
		artifactIds.put("component3ItemId", component3.getItemId().getUuidValue());
		artifactIds.put("component4ItemId", component4.getItemId().getUuidValue());
		
		artifactIds.put(c1 + "/f/a.txt", pathToHandle1.get(c1 + "/f/a.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/c.txt", pathToHandle1.get(c1 + "/f/c.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/e.txt", pathToHandle1.get(c1 + "/f/tree/e.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/", pathToHandle1.get(c1 + "/f/tree/").getItemId().getUuidValue());

		// accepted change set
		createChangeSet4(repo, buildStream, component2, pathToHandle2, artifactIds);
		createEmptyChangeSets(repo, buildStream, component3, artifactIds, 5, 2);
		createNoopChangeSets(repo, buildStream, component3, c3, pathToHandle3, artifactIds, 7);
		createComponentRootChangeSet(repo, buildStream, component3, pathToHandle3, artifactIds, 9);

		// discarded change sets
		createChangeSet1(repo, buildWorkspace, component1, c1, pathToHandle1, artifactIds, true);
		createChangeSet2(repo, buildWorkspace, component1, c1, pathToHandle1, artifactIds);
		createChangeSet3(repo, buildWorkspace, component2, c2, pathToHandle2, artifactIds, true);

		return artifactIds;
	}
	
	public Map<String, String> setupBuildResultContributions(ConnectionDetails connectionDetails,
									String workspaceName,
									String componentName,
									String buildDefinitionId,
									IProgressMonitor progress) throws Exception {

		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				c1 + "/f/c.txt",
				c1 + "/f/d.txt",
				c1 + "/f/n.txt",
				c1 + "/f/tree/",
				c1 + "/f/tree/e.txt",
				c1 + "/f2/",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);
		
		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/a.txt", pathToHandle.get(c1 + "/f/a.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/c.txt", pathToHandle.get(c1 + "/f/c.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/e.txt", pathToHandle.get(c1 + "/f/tree/e.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/", pathToHandle.get(c1 + "/f/tree/").getItemId().getUuidValue());
		
		createChangeSet3(repo, buildStream, component, c1, pathToHandle, artifactIds, false);
		createChangeSet4(repo, buildStream, component, pathToHandle, artifactIds);
		
		BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
				IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
				IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
				IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
		return artifactIds;
	}

	public void verifyBuildResultContributions(
			ConnectionDetails connectionDetails, Map<String, String> artifacts) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.verifyBuildResultContributions(artifacts);
	}

	public void testCreateBuildResult(ConnectionDetails connectionDetails,
			String testName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testCreateBuildResult(testName);
	}

	public void testCreateBuildResultFail(ConnectionDetails connectionDetails,
			String testName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testCreateBuildResultFail(testName);
	}

	public void testExternalLinks(ConnectionDetails connectionDetails,
			String testName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testExternalLinks(testName);
	}
	
	private static final String XML_ENCODED_CHARACTERS = "<'&\">";

	public Map<String, String> setupXMLEncodingTestChangeSets(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, IProgressMonitor progress) throws Exception {
				
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
				"/f/",
				"/f/a.txt"
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put("workspaceItemId", buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put("streamItemId", buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put("/f/a.txt", pathToHandle.get("/f/a.txt").getItemId().getUuidValue());
		
		createXMLEncodingTestChangeSet(repo, buildStream, component, pathToHandle, artifactIds);
		
		return artifactIds;
	}

	private void createXMLEncodingTestChangeSet(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts)
			throws TeamRepositoryException {
		// cs1 modifies the file
		IChangeSetHandle cs1 = workspace.createChangeSet(component, XML_ENCODED_CHARACTERS, false, null);
		SCMUtil.modifyFiles(workspace, component, cs1, pathToHandle, new String[] {
				"/f/a.txt"
		});
		workspace.closeChangeSets(Collections.singletonList(cs1), null);
		
		IChangeSet changeSet = (IChangeSet) repo.itemManager().fetchCompleteItem(cs1, IItemManager.DEFAULT, null);
		for (IChange change : (List<IChange>) changeSet.changes()) {
			IVersionableHandle state = change.afterState();
			if (state == null) {
				state = change.beforeState();
			}
			artifacts.put(state.getItemId().getUuidValue(), state.getStateId().getUuidValue());
		}
		artifacts.put("cs1", cs1.getItemId().getUuidValue());
	}
	
	public void testBuildTermination(ConnectionDetails connectionDetails,
			String testName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testBuildTermination(testName);
	}
	
	public String testBuildResultInfo(ConnectionDetails connectionDetails,
			String testName, IBuildResultInfo buildResultInfo) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		return buildConnectionTests.testBuildResultInfo(testName, buildResultInfo);
	}
	public Map<String, String> testComponentLoading(ConnectionDetails connectionDetails,
			String workspaceName, String componentName, String hjPath, String buildPath,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupComponentLoading(workspaceName,
				componentName, hjPath, buildPath);
		try {
			buildConfigurationTests.testComponentLoading(workspaceName, componentName, hjPath, buildPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		return artifactIds;
	}

	public Map<String, String> testNewLoadRules(ConnectionDetails connectionDetails,
			String workspaceName, String testName, String hjPath, String buildPath, 
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupNewLoadRules(workspaceName,
				testName, hjPath, buildPath);
		try {
			buildConfigurationTests.testNewLoadRules(workspaceName, testName, hjPath, buildPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		return artifactIds;
	}

	public Map<String, String> testOldLoadRules(ConnectionDetails connectionDetails,
			String workspaceName, String testName, String hjPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupOldLoadRules(workspaceName,
				testName, hjPath);
		try {
			buildConfigurationTests.testOldLoadRules(workspaceName, testName, hjPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		return artifactIds;
	}

	public Map<String, String> testPersonalBuild(
			ConnectionDetails connectionDetails, String workspaceName,
			String testName, String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupPersonalBuild(workspaceName,
				testName, hjPath, buildPath);
		try {
			buildConfigurationTests.testPersonalBuild(workspaceName, testName, hjPath, buildPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		return artifactIds;
	}

	public Map<String, String> testGoodFetchLocation(
			ConnectionDetails connectionDetails, String workspaceName,
			String testName, String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupBadFetchLocation(workspaceName,
				testName, hjPath, buildPath);
		try {
			buildConfigurationTests.testGoodFetchLocation(workspaceName, testName, hjPath, buildPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		return artifactIds;
	}

	public Map<String, String> testBadFetchLocation(
			ConnectionDetails connectionDetails, String workspaceName,
			String testName, String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupBadFetchLocation(workspaceName,
				testName, hjPath, buildPath);
		try {
			buildConfigurationTests.testBadFetchLocation(workspaceName, testName, hjPath, buildPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		return artifactIds;
	}
}
