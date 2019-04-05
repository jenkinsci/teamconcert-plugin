/*******************************************************************************
 * Copyright © 2013, 2019 IBM Corporation and others.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.AssertionFailedException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.common.builddefinition.IAutoDeliverConfigurationElement;
import com.ibm.team.build.common.builddefinition.UDeployConfigurationElement.TriggerPolicy;
import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.hjplugin.rtc.BuildClient;
import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.IBuildResultInfo;
import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;
import com.ibm.team.build.internal.hjplugin.rtc.RTCSnapshotUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RTCVersionCheckException;
import com.ibm.team.build.internal.hjplugin.rtc.RTCWorkspaceUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.hjplugin.rtc.VersionCheckerUtil;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.registry.IEndPointDescriptor;
import com.ibm.team.links.common.registry.ILinkTypeRegistry;
import com.ibm.team.process.common.IProcessAreaHandle;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IFlowNodeConnection.IComponentAdditionOp;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceConnection.IConfigurationOp;
import com.ibm.team.scm.client.IWorkspaceConnection.IMarkAsMergedOp;
import com.ibm.team.scm.client.IWorkspaceConnection.IRevertOp;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.AcceptFlags;
import com.ibm.team.scm.common.BaselineSetFlags;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IChange;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.WorkspaceComparisonFlags;
import com.ibm.team.scm.common.dto.IChangeHistorySyncReport;
import com.ibm.team.scm.common.dto.IItemConflictReport;
import com.ibm.team.scm.common.dto.IUpdateReport;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.IWorkItemReferences;
import com.ibm.team.workitem.common.model.WorkItemLinkTypes;

@SuppressWarnings({"nls", "restriction"})
public class TestSetupTearDownUtil extends BuildClient {
	
	private static final String CS3WI1 = "cs3wi1";
	public static final String ARTIFACT_MULTIPLE_WORKSPACE_ITEM_ID_2 = "multipleWorkspaceItemId2";
	public static final String ARTIFACT_MULTIPLE_WORKSPACE_ITEM_ID_1 = "multipleWorkspaceItemId1";
	public static final String ARTIFACT_SINGLE_WORKSPACE_ITEM_ID = "singleWorkspaceItemId";
	public static final String ARTIFACT_WORKSPACE_NAME = "workspaceName";
	public static final String ARTIFACT_WORKSPACE_ITEM_ID = "workspaceItemId";
	// This could be a repository workspace in setupAcceptChanges or 
	// a stream in setupTestBuildStream_basic
	public static final String ARTIFACT_STREAM_ITEM_ID = "streamItemId";
	public static final String ARTIFACT_STREAM_NAME = "streamName";
	public static final String ARTIFACT_PB_STREAM_ITEM_ID = "pbStreamItemId";
	public static final String ARTIFACT_PB_STREAM_NAME = "pbStreamName";
	public static final String ARTIFACT_COMPONENT1_ITEM_ID = "component1ItemId";
	public static final String ARTIFACT_COMPONENT2_ITEM_ID = "component2ItemId";
	public static final String ARTIFACT_COMPONENT_ADDED_ITEM_ID = "componentAddedItemId";
	public static final String ARTIFACT_BASELINE_SET_ITEM_ID = "baselineSetItemId";
	public static final String ARTIFACT_BUILD_DEFINITION_ITEM_ID = "buildDefinitionItemId";
	public static final String ARTIFACT_BUILD_DEFINITION_ID = "buildDefinitionId";
	public static final String ARTIFACT_BUILD_ENGINE_ITEM_ID = "buildEngineItemId";
	public static final String ARTIFACT_BUILD_RESULT_ITEM_ID = "buildResultItemId";
	public static final String ARTIFACT_PROJECT_AREA_ITEM_ID = "projectAreaItemId";
	public static final String ARTIFACT_PROCESS_DEFINITION_ITEM_ID = "processDefinitionItemId";
	public static final String ARTIFACT_LOAD_RULE_FILE_ITEM_ID = "loadRuleFileItemId";
	public static final String COMPONENT_NAME = "componentName";
	public static final String ARTIFACT_TEST_FOLDER_ITEM_ID = "testFolderItemId";
	public static final String ARTIFACT_LOAD_RULE_FILE_PATH = "loadRuleFilePath";
	public static final String ARTIFACT_LOAD_RULE_STREAM_ITEM_ID = "lrStreamItemId";
	public static final String ARTIFACT_LOAD_RULE_STREAM_NAME = "lrStreamName";
	public static final String ARTIFACT_LOAD_RULE_STREAM_WS_ITEM_ID = "lrStreamWSItemId";
	public static final String ARTIFACT_LOAD_RULE_STREAM_SS_ITEM_ID = "lrStreamSSItemId";
	public static final String ARTIFACT_LOAD_RULE_SS_ITEM_ID = "lrSnapshotItemId";
	public static final String ARTIFACT_LOAD_RULE_SS_WS_ITEM_ID = "lrSSWSId";
	public static final String ARTIFACT_WORKITEM_ID = "workItemId";
	
	@SuppressWarnings("deprecation")
	private static final IEndPointDescriptor RELATED_ARTIFACT = 
						ILinkTypeRegistry.INSTANCE.getLinkType(WorkItemLinkTypes.RELATED_ARTIFACT).
									getTargetEndPointDescriptor();

	public TestSetupTearDownUtil() {
		
	}
	
	/**
	 * Creates and adds a new component to the stream given its UUID
	 * @param connectionDetails
	 * @param streamUUID
	 * @param componentName
	 * @param progress
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> addComponentToStream(ConnectionDetails connectionDetails, String streamUUID,
										String componentName, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		@SuppressWarnings("unused")
		IProcessAreaHandle projectAreaHandle = ProcessUtil.getDefaultProjectArea(repo);

		IWorkspaceHandle streamHandle = (IWorkspaceHandle) IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(streamUUID), null);
		IWorkspaceConnection buildStreamConnection = workspaceManager.getWorkspaceConnection(streamHandle, null);

		// Create a component and add it to the stream
		IComponent component = workspaceManager.createComponent(componentName, workspaceManager.teamRepository().loggedInContributor(), null);
		
		// Add the component to the stream
		IComponentAdditionOp componentOp = buildStreamConnection.componentOpFactory().addComponent(component, false);
		buildStreamConnection.applyComponentOperations(Collections.singletonList(componentOp), null);

		// capture interesting uuids
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID, component.getItemId().getUuidValue());
		return artifactIds;
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

	public Map<String, String> setupAcceptChanges(ConnectionDetails connectionDetails, String name,
			String componentName, boolean createBuildDefinition, IProgressMonitor progress) throws Exception {
		return setupAcceptChanges(connectionDetails, name, componentName, name, null, createBuildDefinition, true, false, progress);
	}
	
	public Map<String, String> setupAcceptChanges(ConnectionDetails connectionDetails, String name,
			String componentName, String buildDefinitionId, String loadDirectory, boolean createBuildDefinition,
			boolean createBuildResult, boolean isPersonalBuild, IProgressMonitor progress) throws Exception {
		return setupAcceptChanges(connectionDetails, name, componentName, buildDefinitionId, loadDirectory,
				createBuildDefinition, createBuildResult, isPersonalBuild, false, null, progress);
	}
	
	public Map<String, String> setupAcceptChanges(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String loadDirectory, boolean createWorkItem, String workItemSummary, 
			IProgressMonitor progress) throws Exception {
		return setupAcceptChanges(connectionDetails, workspaceName, componentName, null, loadDirectory,
				false, false, false, createWorkItem, workItemSummary, progress);
	}
	
	public Map<String, String> setupAcceptChanges(ConnectionDetails connectionDetails, String name,
			String componentName, String buildDefinitionId, String loadDirectory, boolean createBuildDefinition,
			boolean createBuildResult, boolean isPersonalBuild, boolean createWorkItem, 
			String workItemSummary, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		String streamName = name + "_stream";
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, streamName);
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
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, name);
		
		if (createBuildDefinition) {
			String destinationDirectory = loadDirectory;
			if (destinationDirectory == null) {
				destinationDirectory = ".";
			}
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, destinationDirectory,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
			if (createBuildResult) {
				String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, connection, "my label", artifactIds);
				
				if (isPersonalBuild) {
					IBuildResult buildResult = (IBuildResult) repo.itemManager().
							fetchCompleteItem(IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null), 
							IItemManager.REFRESH, null).getWorkingCopy();
					buildResult.setPersonalBuild(true);
					BuildUtil.save(repo, buildResult);
				}
			}
		}
		IWorkItem workItem = null;
		if (createWorkItem) {
			IProjectArea projectArea = ProcessUtil.getDefaultProjectArea(repo);
			workItem = WorkItemUtil.createWorkItem(repo, projectArea, workItemSummary);
			artifactIds.put(ARTIFACT_WORKITEM_ID, Integer.toString(workItem.getId()));
		}
		
		IComponent component = (IComponent) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(ARTIFACT_STREAM_NAME, streamName);
		artifactIds.put(ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/a.txt", pathToHandle.get(c1 + "/f/a.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/c.txt", pathToHandle.get(c1 + "/f/c.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/e.txt", pathToHandle.get(c1 + "/f/tree/e.txt").getItemId().getUuidValue());
		artifactIds.put(c1 + "/f/tree/", pathToHandle.get(c1 + "/f/tree/").getItemId().getUuidValue());
		
		createChangeSet1(repo, buildStream, component, c1, pathToHandle, artifactIds, false);
		createChangeSet2(repo, buildStream, component, c1, pathToHandle, artifactIds);
		createChangeSet3(repo, buildStream, component, c1, pathToHandle,artifactIds, workItem, false);
		createChangeSet4(repo, buildStream, component, pathToHandle, artifactIds);
		createEmptyChangeSets(repo, buildStream, component, artifactIds, 5, 1);
		createNoopChangeSets(repo, buildStream, component, c1, pathToHandle, artifactIds, 6);
		createComponentRootChangeSet(repo, buildStream, component, pathToHandle, artifactIds, 8);
		
		return artifactIds;
	}

	public Map<String, String> createEmptyChangeSets(ConnectionDetails connectionDetails, String workspaceUUID, 
			String componentUUID, int count, IProgressMonitor progress) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(monitor.newChild(5));
		ITeamRepository repo = connection.getTeamRepository(); 
		Map<String, String> artifactIds = new HashMap<>();
		IWorkspaceHandle workspaceH = (IWorkspaceHandle) IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(workspaceUUID), null);
		IComponentHandle componentH = (IComponentHandle) IComponent.ITEM_TYPE.createItemHandle(UUID.valueOf(componentUUID), null);
		IComponent component = (IComponent) repo.itemManager().fetchCompleteItem(componentH, 
							IItemManager.REFRESH, monitor.newChild(35));
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		IWorkspaceConnection workspaceC = workspaceManager.getWorkspaceConnection(workspaceH, monitor.newChild(55));
		createEmptyChangeSets(repo, workspaceC, component, artifactIds, 1, 1);
		return artifactIds;
	}

	@SuppressWarnings("unchecked")
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

	/**
	 * Deletes versionables in the filesystem tree /f/tree/e.txt
	 * 
	 * @param repo
	 * @param workspace
	 * @param component
	 * @param root
	 * @param pathToHandle
	 * @param artifacts
	 * @throws TeamRepositoryException
	 */
	@SuppressWarnings("unchecked")
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
		List<IWorkItemHandle> workItems = WorkItemUtil.findSomeWorkItems(repo, 1);
		createChangeSet3(repo, workspace, component, root, pathToHandle, artifacts, 
				workItems, forDiscard);		
	}
	
	/**
	 * Creates a change set, creates a work item with the specified summary and 
	 * links the work item to the change set.
	 * 
	 * @param repo - The repository connection 
	 * @param workspace - The repository workspace in which change sets are being created 
	 * @param component - The component in which change set is created
	 * @param root - The path to the root folder in the workspace 
	 * @param pathToHandle - A map with the name of the file and its item handle
	 * @param artifacts - The list of artifacts created up to this point
	 * @param workItem - The work item to be associated with the change set
	 * @param forDiscard  - Whether the change set is setup for discarding in the build workspace 
	 * @throws TeamRepositoryException - if anything goes wrong
	 */
	private void createChangeSet3(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component, String root,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts,
			IWorkItemHandle workItem,
			boolean forDiscard)
			throws TeamRepositoryException {
		if (workItem == null) {
			createChangeSet3(repo, workspace, component, root, pathToHandle, artifacts, 
					forDiscard);
		} else {
			createChangeSet3(repo, workspace, component, root, pathToHandle, artifacts, 
				Arrays.asList(new IWorkItemHandle[] {workItem}), forDiscard);	
		}
	}
	
	
	@SuppressWarnings("unchecked")
	private void createChangeSet3(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component, String root,
			Map<String, IItemHandle> pathToHandle, Map<String, String> artifacts,
			List<IWorkItemHandle> workItems, boolean forDiscard) throws TeamRepositoryException {
		
		// cs3 adds newTree/ newTree/newFile.txt
		// cs3 has a work item associated with it but no comment
		// The workitems are dependent on the test repo being setup with some work items
		IChangeSetHandle cs3 = workspace.createChangeSet(component, "", false, null);
		SCMUtil.addVersionables(workspace, component, cs3, pathToHandle, new String[] {
				root + "/f/newTree/",
				root + "/f/newTree/newFile.txt"
		}, "createChangeSet3");
		if (!workItems.isEmpty()) {
			SCMUtil.createWorkItemChangeSetLink(repo, new IWorkItemHandle[] { workItems.get(0) }, cs3);
			IWorkItem fullWorkItem = (IWorkItem) repo.itemManager().fetchCompleteItem(workItems.get(0), IItemManager.DEFAULT, null);
			artifacts.put(Integer.toString(fullWorkItem.getId()), fullWorkItem.getHTMLSummary().toString());
			artifacts.put(CS3WI1, Integer.toString(fullWorkItem.getId()));
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
		SCMUtil.addVersionables(workspace, component, cs4, pathToHandle, toAdd , "createChangeSet4");
		workItems = WorkItemUtil.findSomeWorkItems(repo, 5);
		if (!workItems.isEmpty()) {
			SCMUtil.createWorkItemChangeSetLink(repo, workItems.toArray(new IWorkItemHandle[workItems.size()]), cs4);
			@SuppressWarnings("unchecked")
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

		// Delete SCM repository workspaces
		SCMUtil.deleteSCMArtifacts(repo, setupArtifacts);
		
		// Delete the build defn related artifacts
		BuildUtil.deleteBuildArtifacts(repo, setupArtifacts);
		
		// Delete project area and process definition, if any
		ProcessUtil.deleteProcessArtifacts(repo, setupArtifacts);
		
		// Delete work item artifacts
		WorkItemUtil.deleteWorkItems(repo, setupArtifacts);
	}
	
	public void tearDownTestBuildStream_complete(ConnectionDetails connectionDetails,
			Map<String, String> setupArtifacts, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		RTCFacadeTests.tearDownTestBuildStream_complete(repo, setupArtifacts);
		tearDown(connectionDetails, setupArtifacts, progress);
	}
	
	public void tearDownTestBuildSnapshot_complete(ConnectionDetails connectionDetails,
			Map<String, String> setupArtifacts, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		RTCFacadeTests.tearDownTestBuildSnapshot_complete(repo, setupArtifacts);
		tearDown(connectionDetails, setupArtifacts, progress);
	}

	public Map<String, String> setupTestBuildWorkspace(ConnectionDetails connectionDetails, String singleWorkspaceName,
			String multipleWorkspaceName, IProgressMonitor progress) throws Exception {
		
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, singleWorkspaceName);
		String componentName = singleWorkspaceName + "-comp1";
		String c1 = "/" + componentName;
		SCMUtil.addComponent(workspaceManager, buildWorkspace, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f/b.txt",
				c1 + "/f/c.txt",
				c1 + "/f/d.txt",
				c1 + "/f/ws.loadrule",
				c1 + "/f/tree/",
				c1 + "/f/tree/e.txt",
				c1 + "/f2/",
				});
		IWorkspaceConnection buildWorkspace2 = SCMUtil.createWorkspace(workspaceManager, multipleWorkspaceName);
		IWorkspaceConnection buildWorkspace3 = SCMUtil.createWorkspace(workspaceManager, multipleWorkspaceName);
		
		Map<String, String> result = new HashMap<String, String>();
		result.put(ARTIFACT_SINGLE_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		result.put(ARTIFACT_MULTIPLE_WORKSPACE_ITEM_ID_1, buildWorkspace2.getContextHandle().getItemId().getUuidValue());
		result.put(ARTIFACT_MULTIPLE_WORKSPACE_ITEM_ID_2, buildWorkspace3.getContextHandle().getItemId().getUuidValue());
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
	
	@SuppressWarnings("unchecked")
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
		}, "createNoopChangeSets");
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
		}, "createNoopChangeSets");
		
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

	@SuppressWarnings("unchecked")
	private void createComponentRootChangeSet(ITeamRepository repo,
			IWorkspaceConnection workspace, IComponent component,
			Map<String, IItemHandle> pathToHandle,
			Map<String, String> artifactIds, int changeSetNumber) throws Exception {
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
	
	public Map<String, String> setupBuildResultContributionsInQueuedBuild(ConnectionDetails connectionDetails,
									String workspaceName,
									String componentName,
									String buildDefinitionId,
									IProgressMonitor progress) throws Exception {
		Map<String, String> artifactIds = setupBuildResultContributions(connectionDetails, workspaceName, componentName, buildDefinitionId, progress);
		// create a build result, but not started (mimic requested in RTC but queued only property is true for build engine)
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		BuildConnectionTests.requestBuild(repo, BuildState.NOT_STARTED, buildDefinitionId, artifactIds);
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
		
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}
	
	public Map<String, String> setupBuildResultContributions_toTestLoadPolicy(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String buildDefinitionId, IProgressMonitor progress) throws Exception {

		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);

		Map<String, String> artifactIds = new HashMap<String, String>();

		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] { c1 + "/",
				c1 + "/f/", c1 + "/f/a.txt", c1 + "/f/b.txt", c1 + "/f/c.txt", c1 + "/f/d.txt", c1 + "/f/n.txt", c1 + "/f/tree/",
				c1 + "/f/tree/e.txt", c1 + "/f2/", });
		
		String c2 = c1 + "c2";
		SCMUtil.addComponent(workspaceManager, buildStream, componentName + "c2", new String[] { c2 + "/",
				c2 + "/f/", c2 + "/f/a.txt", c2 + "/f/b.txt", c2 + "/f/c.txt"});

		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
		IComponent component = (IComponent)pathToHandle.get(componentName);

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

		BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds, IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
				buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
				IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");

		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
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
	
	public void testMetronomeLogsInBuildResult(ConnectionDetails connectionDetails, 
						String buildResultUUID)  throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testMetronomeLogsInBuildResult(buildResultUUID);
	}
	
	public void testNoMetronomeLogsInBuildResult(ConnectionDetails connectionDetails, 
			String buildResultUUID)  throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testNoMetronomeLogsInBuildResult(buildResultUUID);
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
	
	public Map<String, String> setupSnapshot(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String snapshotName,  IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, workspaceName);
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildWorkspace, componentName, new String[] {
				c1 + "/",
				c1 + "/f/",
				c1 + "/f/a.txt",
				c1 + "/f2"
				});
		
		IComponentHandle component = (IComponentHandle) pathToHandle.get(componentName);

		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		
		IBaselineSetHandle buildSnapshot = SCMUtil.createSnapshot(buildWorkspace, snapshotName);
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BASELINE_SET_ITEM_ID, buildSnapshot.getItemId().getUuidValue());
		
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));

		return artifactIds;

	}


	@SuppressWarnings("unchecked")
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
	
	public void testBuildStart(ConnectionDetails connectionDetails,
			String testName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testBuildStart(testName);
	}
	
	public Map<String, String> testBuildTerminationSetup(ConnectionDetails connectionDetails,
			String testName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		return buildConnectionTests.testBuildTerminationSetup(testName);
	}
	
	public void testBuildTerminationTestSetup(ConnectionDetails connectionDetails,
			boolean startBuild, boolean abandon, String buildStatus,
			Map<String, String> artifactIds) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.testBuildTerminationTestSetup(startBuild, abandon, buildStatus, artifactIds);
	}
	
	public void verifyBuildTermination(ConnectionDetails connectionDetails,
			String expectedState, String expectedStatus,
			Map<String, String> artifactIds) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.verifyBuildTermination(expectedState, expectedStatus, artifactIds);
	}
	
	public void verifyBuildResultDeleted(ConnectionDetails connectionDetails,
			Map<String, String> artifactIds) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConnectionTests buildConnectionTests = new BuildConnectionTests(connection);
		buildConnectionTests.verifyBuildResultDeleted(artifactIds);
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
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupComponentLoading(workspaceName,
				buildDefinitionId,
				componentName, hjPath, buildPath);
		try {
			buildConfigurationTests.testComponentLoading(workspaceName, buildDefinitionId, componentName, hjPath, buildPath, artifactIds);
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
			String workspaceName, String componentName, String hjPath, String buildPath, 
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupNewLoadRules(workspaceName, 
				componentName, buildDefinitionId, hjPath, buildPath);
		try {
			buildConfigurationTests.testNewLoadRules(workspaceName, componentName, buildDefinitionId, hjPath, buildPath, artifactIds);
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
			String workspaceName, String componentName, String hjPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupOldLoadRules(workspaceName,
				componentName, hjPath);
		try {
			buildConfigurationTests.testOldLoadRules(workspaceName, componentName, hjPath, artifactIds);
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

	public Map<String, String> testOldLoadRules_setAllLoadOptions(ConnectionDetails connectionDetails, String workspaceName, String componentName,
			String hjPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupOldLoadRules_setAllLoadOptions(workspaceName, componentName, hjPath);
		try {
			buildConfigurationTests.testOldLoadRules_setAllLoadOptions(workspaceName, componentName, hjPath, artifactIds);
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

	public Map<String, String> testBuildDefinitionConfig_loadRulesWithNoLoadPolicy(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean configureLoadRules, boolean setLoadPolicy,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_loadRulesWithNoLoadPolicy(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, configureLoadRules, setLoadPolicy);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_loadRulesWithNoLoadPolicy(workspaceName, buildDefinitionId, hjPath, buildPath,
					artifactIds, configureLoadRules, setLoadPolicy);
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

	public Map<String, String> testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean configureLoadRules, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_loadRulesWithLoadPolicySetToLoadRules(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, configureLoadRules);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules(workspaceName, buildDefinitionId, hjPath, buildPath,
					artifactIds, configureLoadRules);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testBuildDefinitionConfig_createFoldersForComponents(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean shouldCreateFoldersForComponents, String loadPolicy,
			String componentLoadConfig, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_toTestCreateFolderForComponents(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, shouldCreateFoldersForComponents, loadPolicy, componentLoadConfig);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_createFoldersForComponents(workspaceName, buildDefinitionId, hjPath, buildPath,
					artifactIds, shouldCreateFoldersForComponents, loadPolicy, componentLoadConfig);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules(ConnectionDetails connectionDetails,
			String workspaceName, String componentName, String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_toTestCreateFoldersForComponents_usingLoadRules(workspaceName,
				componentName, buildDefinitionId, hjPath, buildPath);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules(workspaceName, buildDefinitionId, hjPath,
					buildPath, artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testBuildDefinitionConfig_componentsToExclude(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean shouldExcludeComponents, String loadpolicy, String componentLoadConfig,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_toTestComponentsToExclude(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, shouldExcludeComponents, loadpolicy, componentLoadConfig);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_componentsToExclude(workspaceName, buildDefinitionId, hjPath, buildPath, artifactIds,
					shouldExcludeComponents, loadpolicy, componentLoadConfig);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testBuildDefinitionConfig_includeComponents(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean addLoadComponents, String valueOfIncludeComponents,
			String loadPolicy, String componentLoadConfig, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_toTestIncludeComponents(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, addLoadComponents, valueOfIncludeComponents, loadPolicy, componentLoadConfig);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_includeComponents(workspaceName, buildDefinitionId, hjPath, buildPath, artifactIds,
					addLoadComponents, valueOfIncludeComponents, loadPolicy, componentLoadConfig);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}	

	public Map<String, String> testBuildDefinitionConfig_multipleLoadRuleFiles(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean setLoadPolicyToUseLoadRules, IProgressMonitor progress)
			throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_toTestMultipleLoadRuleFiles(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, setLoadPolicyToUseLoadRules);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_multipleLoadRuleFiles(workspaceName, buildDefinitionId, hjPath, buildPath, artifactIds,
					setLoadPolicyToUseLoadRules);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testBuildDefinitionConfig_oldLoadRulesFormat(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, boolean setLoadPolicyToUseLoadRules, IProgressMonitor progress)
			throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		Map<String, String> artifactIds = buildConfigurationTests.setupBuildDefinition_toTestOldLoadRulesFormat(workspaceName, componentName,
				buildDefinitionId, hjPath, buildPath, setLoadPolicyToUseLoadRules);
		try {
			buildConfigurationTests.testBuildDefinitionConfig_oldLoadRulesFormat(workspaceName, buildDefinitionId, hjPath, buildPath, artifactIds,
					setLoadPolicyToUseLoadRules);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testRepositoryWorkspaceConfig_loadPolicy(ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);

		Map<String, String> artifactIds = buildConfigurationTests.setupRepositoryWorkspaceConfig_toTestLoadPolicy(workspaceName, componentName);
		try {
			buildConfigurationTests.testRepositoryWorkspaceConfig_toTestLoadPolicy(workspaceName, componentName, hjPath, buildPath,
					componentName + "1/" + artifactIds.get(ARTIFACT_LOAD_RULE_FILE_PATH), artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}
	
	public Map<String, String> testStreamConfig_loadPolicy(ConnectionDetails connectionDetails, String workspaceName, String componentName,
			String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);

		Map<String, String> artifactIds = buildConfigurationTests.setupStreamConfig_toTestLoadPolicy(workspaceName, componentName);
		try {
			buildConfigurationTests.testStreamConfig_loadPolicy(workspaceName, componentName, hjPath, buildPath,
					componentName + "1/" + artifactIds.get(ARTIFACT_LOAD_RULE_FILE_PATH), artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}
	
	public Map<String, String> testSnapshotConfig_loadPolicy(ConnectionDetails connectionDetails, String workspaceName, String componentName,
			String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);

		Map<String, String> artifactIds = buildConfigurationTests.setupSnapshotConfig_toTestLoadPolicy(workspaceName, componentName);
		try {
			buildConfigurationTests.testSnapshotConfig_loadPolicy(workspaceName, componentName, hjPath, buildPath,
					componentName + "1/" + artifactIds.get(ARTIFACT_LOAD_RULE_FILE_PATH), artifactIds);
		} catch (Exception e) {
			try {
				tearDown(connectionDetails, artifactIds, progress);
			} catch (Exception e2) {
				// don't let cleanup exception bury the details of the original failure
			}
			throw e;
		}
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> testPersonalBuild(
			ConnectionDetails connectionDetails, String workspaceName,
			String componentName, String hjPath, String buildPath, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		Map<String, String> artifactIds = buildConfigurationTests.setupPersonalBuild(workspaceName,
				componentName, hjPath, buildPath);
		try {
			buildConfigurationTests.testPersonalBuild(workspaceName, componentName, hjPath, buildPath, artifactIds);
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
	
	public Map<String, String> setupTestBuildDefinition(
			ConnectionDetails connectionDetails, String uniqueName,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, uniqueName + "_stream");
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, uniqueName, new String[] {
				"/" + uniqueName + "/",
				});
		
		IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, uniqueName);
		IComponent component = (IComponent) pathToHandle.get(uniqueName);
		
		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		
		BuildUtil.createBuildDefinition(repo, uniqueName, true, artifactIds,
				IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
				IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, ".",
				IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true");
		return artifactIds;
	}
	
	public Map<String, String> setupTestBuildStream_basic(ConnectionDetails connectionDetails, String projectAreaName, String streamName,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);

		Map<String, String> artifactIds = setupTestProcessArea_basic(connectionDetails, projectAreaName);
		String projectAreaId = artifactIds.get(ARTIFACT_PROJECT_AREA_ITEM_ID);
		IProcessAreaHandle projectAreaHandle = (IProcessAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(UUID.valueOf(projectAreaId), null);

		IWorkspaceConnection buildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);

		String componentName = TestUtils.getComponentUniqueName();
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = setupWorkspaceWithComponent(repo, buildStream, 
				workspaceName, componentName, 
				new String[] {
					c1 + "/",
					c1 + "/f/",
					c1 + "/f/a.txt",
					c1 + "/f/b.txt",
					c1 + "/f/c.txt",
					c1 + "/f/d.txt",
					c1 + "/f/n.txt",
					c1 + "/f/ws.loadrule",
					c1 + "/f/tree/",
					c1 + "/f/tree/e.txt",
					c1 + "/f2/",
					});
		
		IComponent component = (IComponent) pathToHandle.get(componentName);
		IWorkspace buildWorkspace = (IWorkspace) pathToHandle.get(workspaceName);
		IWorkspaceConnection buildWorkspaceConnection = workspaceManager.getWorkspaceConnection(buildWorkspace, null);
		
		// Add the component to the stream
		IComponentAdditionOp componentOp = buildStream.componentOpFactory().addComponent(component, buildWorkspaceConnection, false);
		buildStream.applyComponentOperations(Collections.singletonList(componentOp), null);
		
		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getItemId().getUuidValue());
		artifactIds.put(COMPONENT_NAME, componentName);
		
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));

		return artifactIds;
	}

	public Map<String, String> setupTestBuildSnapshot_basic(ConnectionDetails connectionDetails, String projectAreaName, String streamName,
			String workspaceName, String snapshotName, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);

		Map<String, String> artifactIds = setupTestProcessArea_basic(connectionDetails, projectAreaName);
		String projectAreaId = artifactIds.get(ARTIFACT_PROJECT_AREA_ITEM_ID);
		IProcessAreaHandle projectAreaHandle = (IProcessAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(UUID.valueOf(projectAreaId), null);

		IWorkspaceConnection owningStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);
		
		String componentName = owningStream + "Default Component";
		
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, owningStream, componentName, null);
		IComponentHandle component = (IComponentHandle) pathToHandle.get(componentName);
		IBaselineSetHandle streamSnapshot = SCMUtil.createSnapshot(owningStream, snapshotName);
		
		IWorkspaceConnection owningWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, owningStream, workspaceName);
		SCMUtil.createSnapshot(owningWorkspace, "ws" + snapshotName);

		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());		
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BASELINE_SET_ITEM_ID, streamSnapshot.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, owningStream.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, owningWorkspace.getContextHandle().getItemId().getUuidValue());

		return artifactIds;
	}
	
	public Map<String, String> setupBuildSnapshot(
			ConnectionDetails connectionDetails, String workspaceName, String snapshotName, String componentName,
			String workspacePrefix, IProgressMonitor progress) throws Exception {
		Map<String, String> artifactIds = setupSnapshot(connectionDetails, workspaceName, componentName, snapshotName, progress);
		return artifactIds;
	}

	/** 
	 * Setups a stream and a repository workspace flowing to that stream.
	 * In the repository workspace, a folder with some files are shared.
	 * 4 change sets are created. One change set has a work item associated with it.
	 * 
	 * @param connectionDetails
	 * @param streamName
	 * @param progress
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestBuildStream_acceptChanges(ConnectionDetails connectionDetails, 
			String streamName, String workItemSummary,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();

		Map<String, String> artifactIds = new HashMap<String, String>();
		IProcessAreaHandle projectAreaHandle = ProcessUtil.getDefaultProjectArea(repo);

		IWorkspaceConnection buildStreamConnection = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = setupWorkspaceWithComponent(repo, buildStreamConnection, 
				workspaceName, componentName, 
				new String[] {
					c1 + "/",
					c1 + "/f/",
					c1 + "/f/a.txt",
					c1 + "/f/b.txt",
					c1 + "/f/c.txt",
					c1 + "/f/d.txt",
					c1 + "/f/n.txt",
					c1 + "/f/ws.loadrule",
					c1 + "/f/tree/",
					c1 + "/f/tree/e.txt",
					c1 + "/f2/",
					});
		
		IComponent component = (IComponent) pathToHandle.get(componentName);
		IWorkspace buildWorkspace = (IWorkspace) pathToHandle.get(workspaceName);
		IWorkspaceConnection buildWorkspaceConnection = workspaceManager.getWorkspaceConnection(buildWorkspace, null);

		// Add the component to the stream
		IComponentAdditionOp componentOp = buildStreamConnection.componentOpFactory().addComponent(component, false);
		buildStreamConnection.applyComponentOperations(Collections.singletonList(componentOp), null);
		
		IWorkItem workItem = null;
		String buildToolkitVersion = null;
		try {
			buildToolkitVersion = VersionCheckerUtil.getBuildToolkitVersion(Locale.getDefault()); 
		} catch (RTCVersionCheckException exp) {
			if (exp.getMessage().contains("Could not find class \"com.ibm.team.rtc.common.configuration.IComponentConfiguration\" in com.ibm.team.rtc.commons jar")) {
				buildToolkitVersion = "4.0.7";
			}
		}
		if (canWorkItemBeCreated(buildToolkitVersion)) {
			IProjectArea projectArea = ProcessUtil.getDefaultProjectArea(repo);
			workItem = WorkItemUtil.createWorkItem(repo, projectArea, workItemSummary);
			artifactIds.put(ARTIFACT_WORKITEM_ID, Integer.toString(workItem.getId()));
		}

		// Create change sets in the workspace
		createChangeSet3(repo, buildWorkspaceConnection, component, c1, pathToHandle,artifactIds, workItem, false);
		
		// Create a snapshot on the workspace
		buildWorkspaceConnection.createBaselineSet(null, "test snapshot",
				 "test snapshot for workspace",
				 BaselineSetFlags.CREATE_NEW_BASELINES, new NullProgressMonitor());

		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStreamConnection.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_NAME, streamName);
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_NAME, buildWorkspace.getName());
		artifactIds.put(COMPONENT_NAME, componentName);
		
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}
	
	public Map<String, String> setupTestBuildStream_toTestLoadPolicy(ConnectionDetails connectionDetails, String streamName,
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		String workspaceName = TestUtils.getRepositoryWorkspaceUniqueName();
		String componentName = TestUtils.getComponentUniqueName();

		Map<String, String> artifactIds = new HashMap<String, String>();
		IProcessAreaHandle projectAreaHandle = ProcessUtil.getDefaultProjectArea(repo);

		IWorkspaceConnection buildStreamConnection = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);
		String c1 = "/" + componentName;
		Map<String, IItemHandle> pathToHandle = setupWorkspaceWithComponent(repo, buildStreamConnection, 
				workspaceName, componentName, 
				new String[] {
					c1 + "/",
					c1 + "/f/",
					c1 + "/f/a.txt",
					c1 + "/f/b.txt",
					c1 + "/f/c.txt",
					c1 + "/f/d.txt",
					c1 + "/f/n.txt",
					c1 + "/f/ws.loadrule",
					c1 + "/f/tree/",
					c1 + "/f/tree/e.txt",
					c1 + "/f2/",
					});
		
		IComponent component = (IComponent) pathToHandle.get(componentName);
		IWorkspace buildWorkspace = (IWorkspace) pathToHandle.get(workspaceName);
		IWorkspaceConnection buildWorkspaceConnection = workspaceManager.getWorkspaceConnection(buildWorkspace, null);

		// Add the component to the stream
		IComponentAdditionOp componentOp = buildStreamConnection.componentOpFactory().addComponent(component, buildWorkspaceConnection, false);
		buildStreamConnection.applyComponentOperations(Collections.singletonList(componentOp), null);
		
		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStreamConnection.getContextHandle().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_NAME, streamName);
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_NAME, buildWorkspace.getName());
		artifactIds.put(COMPONENT_NAME, componentName);
		
		artifactIds.put("isPre603BuildToolkit", Boolean.toString(VersionCheckerUtil.isPre603BuildToolkit()));
		return artifactIds;
	}

	public Map<String, String> setupTestBuildSnapshotUsingStream(ConnectionDetails connectionDetails, String projectAreaName, String streamName, 
			String snapshotName, IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);

		Map<String, String> artifactIds = setupTestProcessArea_basic(connectionDetails, projectAreaName);
		String projectAreaId = artifactIds.get(ARTIFACT_PROJECT_AREA_ITEM_ID);
		IProcessAreaHandle projectAreaHandle = (IProcessAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(UUID.valueOf(projectAreaId), null);

		IWorkspaceConnection buildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);
		
		String componentName = buildStream + "Default Component";
		
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, null);
		IComponentHandle component = (IComponentHandle) pathToHandle.get(componentName);
		IBaselineSetHandle buildSnapshot = SCMUtil.createSnapshot(buildStream, snapshotName);

		// capture interesting uuids to verify against
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());		
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BASELINE_SET_ITEM_ID, buildSnapshot.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());

		return artifactIds;
	}

	public void testBuildSnapshotConfiguration(ConnectionDetails connectionDetails, String snapshotName, String workspacePrefix, String hjPath) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		BuildConfigurationTests buildConfigurationTests = new BuildConfigurationTests(connection);
		buildConfigurationTests.testLoadSnapshotConfiguration(snapshotName, workspacePrefix, hjPath);
	}

	public Map<String, String> setupTestProcessArea_basic(ConnectionDetails connectionDetails, String projectAreaName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		RTCFacadeTests rtcFacadeTests = new RTCFacadeTests(connection);
		return rtcFacadeTests.setupTestProcessArea_basic(projectAreaName);
	}
	
	public Map<String, String> setupTestProcessArea_archiveProjectArea(ConnectionDetails connectionDetails, String projectAreaName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		RTCFacadeTests rtcFacadeTests = new RTCFacadeTests(connection);
		return rtcFacadeTests.setupTestProcessArea_archiveProjectArea(projectAreaName);
	}
	
	public Map<String, String> setupTestProcessArea_archiveTeamArea(ConnectionDetails connectionDetails, String projectAreaName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		RTCFacadeTests rtcFacadeTests = new RTCFacadeTests(connection);
		return rtcFacadeTests.setupTestProcessArea_archiveTeamArea(projectAreaName);
	}

	public Map<String, String> setupTestBuildStream_complete(ConnectionDetails connectionDetails, String projectAreaName, String streamName)
			throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		RTCFacadeTests rtcFacadeTests = new RTCFacadeTests(connection);
		return rtcFacadeTests.setupTestBuildStream_complete(projectAreaName, streamName);
	}

	public Map<String, String> setupTestBuildSnapshot_complete(ConnectionDetails connectionDetails, String workspaceName, String projectAreaName,
			String streamName, String snapshotName) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		RTCFacadeTests rtcFacadeTests = new RTCFacadeTests(connection);
		return rtcFacadeTests.setupTestBuildSnapshot_complete(workspaceName, projectAreaName, streamName, snapshotName);
	}
	
	public static IConsoleOutput getListener(final Exception[] failure) {
		IConsoleOutput listener = new IConsoleOutput() {
			
			@Override
			public void log(String message, Exception e) {
				failure[0] = e;
			}
			
			@Override
			public void log(String message) {
				// not good
				throw new AssertionFailedException(message);
			}
		};
		return listener;
	}

	public void deleteSnapshot(ConnectionDetails connectionDetails, String streamName, String snapshotUUID,
								IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repository = connection.getTeamRepository();
		IWorkspaceHandle workspaceHandle = RTCWorkspaceUtils.getInstance().getStream(null, streamName, repository, progress, Locale.getDefault());
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repository);
		IWorkspaceConnection workspaceConnection = workspaceManager.getWorkspaceConnection(workspaceHandle, progress);
		IBaselineSetHandle baseline = RTCSnapshotUtils.getSnapshot(repository, null, snapshotUUID, progress, Locale.getDefault());
		workspaceConnection.removeBaselineSet(baseline, progress);
	}
	
	public Map<String, String> setupBuildDefinitionWithoutJazzScmWithPBDeliver(ConnectionDetails connectionDetails,
			String workspaceName, String componentName, String buildDefinitionId,
			boolean createBuildResult, Map<String, String> configOrGenericProperties, 
			IProgressMonitor progress) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		
		Map<String, String> artifactIds = setupAcceptChanges(connectionDetails, workspaceName, componentName, 
								buildDefinitionId, null, true, false, false, progress);
		
		// Remove the Jazz SCM configuration element but leave the post build deliver configuration element
		BuildUtil.removeConfigurationElement(repo, buildDefinitionId, IJazzScmConfigurationElement.ELEMENT_ID, progress);
		
		// Setting up the PB deliver configuration element.
		String deliverTargetName = TestUtils.getRepositoryWorkspaceUniqueName();
		// Create another repository workspace that will act as the flow target. Use the stream created in setupAcceptChanges as the base for this new workspace
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(connection.getTeamRepository());
		IWorkspaceConnection stream = workspaceManager.getWorkspaceConnection((IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(
										UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID)),null),null);
		IWorkspaceConnection deliverTarget = SCMUtil.createWorkspace(workspaceManager, deliverTargetName, ""
												+ "My deliver target workspace", stream);
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_PB_STREAM_ITEM_ID, deliverTarget.getResolvedWorkspace().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_PB_STREAM_NAME, deliverTarget.getName());
		
		// Compose the map of properties that has to be provided for PB deliver configuration
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_ADD_NEW_COMPONENTS_TO_TARGET, BuildUtil.getValueOrDefault(configOrGenericProperties, 
					IAutoDeliverConfigurationElement.PROPERTY_ADD_NEW_COMPONENTS_TO_TARGET, "true"));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ALL_COMPONENTS, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ALL_COMPONENTS, "true"));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_REMOVE_COMPONENTS_IN_TARGET, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_REMOVE_COMPONENTS_IN_TARGET, "true"));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY, TriggerPolicy.NO_WARNINGS.name()));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TARGET_UUID, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TARGET_UUID, deliverTarget.getResolvedWorkspace().getItemId().getUuidValue()));
		setupPBDeliverConfigurationElement(connectionDetails, buildDefinitionId, artifactIds, configOrGenericProperties, progress);
		
		// Create the build result now, so that it includes PB deliver configuration element
		if (createBuildResult) {
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my label", artifactIds);
		}
		return artifactIds;
	}

	public Map<String, String> setupBuildDefinitionWithJazzScmAndPBDeliver(ConnectionDetails connectionDetails,
			String workspaceName, String componentName, String buildDefinitionId,
			String loadDirectory, boolean createBuildResult, 
			boolean isPersonalBuild, Map<String, String> configOrGenericProperties, IProgressMonitor progress) throws Exception {
		// Purposefully sending false, false for createBuildResult and isPersonalBuild because
		// we want to create a build result after adding PB deliver configuration element to the build
		// definition
		Map<String, String> artifactIds = setupAcceptChanges(connectionDetails, workspaceName, componentName, 
								buildDefinitionId, loadDirectory, true, false, false, progress);
		
		// Setting up the PB deliver configuration element.
		String deliverTargetName = TestUtils.getRepositoryWorkspaceUniqueName();
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(progress);
		ITeamRepository repo = connection.getTeamRepository(); 
		
		// Create another repository workspace that will act as the flow target. Use the stream created in setupAcceptChanges as the base for this new workspace
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(connection.getTeamRepository());
		IWorkspaceConnection stream = workspaceManager.getWorkspaceConnection((IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(
										UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID)),null),null);
		IWorkspaceConnection deliverTarget = SCMUtil.createWorkspace(workspaceManager, deliverTargetName, ""
												+ "My deliver target workspace", stream);
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_PB_STREAM_ITEM_ID, deliverTarget.getResolvedWorkspace().getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_PB_STREAM_NAME, deliverTarget.getName());
		
		// Compose the map of properties that has to be provided for PB deliver configuration
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_ADD_NEW_COMPONENTS_TO_TARGET, BuildUtil.getValueOrDefault(configOrGenericProperties, 
					IAutoDeliverConfigurationElement.PROPERTY_ADD_NEW_COMPONENTS_TO_TARGET, "true"));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ALL_COMPONENTS, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ALL_COMPONENTS, "true"));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_REMOVE_COMPONENTS_IN_TARGET, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_REMOVE_COMPONENTS_IN_TARGET, "true"));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY, TriggerPolicy.NO_WARNINGS.name()));
		configOrGenericProperties.put(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TARGET_UUID, BuildUtil.getValueOrDefault(configOrGenericProperties, 
				IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TARGET_UUID, deliverTarget.getResolvedWorkspace().getItemId().getUuidValue()));
		setupPBDeliverConfigurationElement(connectionDetails, buildDefinitionId, artifactIds, configOrGenericProperties, progress);
		
		// Create the build result now, so that it includes PB deliver configuration element
		if (createBuildResult) {
			String buildResultItemId = BuildUtil.createBuildResult(buildDefinitionId, connection, "my label", artifactIds);
			if (isPersonalBuild) {
				IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchCompleteItem(IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null), 
						IItemManager.REFRESH, null).getWorkingCopy();
				buildResult.setPersonalBuild(true);
				BuildUtil.save(repo, buildResult);
			}
		}
		return artifactIds;
	}
	
	public Map<String, String> setupBuildDefinitionWithPBDeliver(ConnectionDetails connectionDetails, String buildDefinitionId, 
												Map<String, String> configOrGenericProperties, IProgressMonitor progress) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
			connection.ensureLoggedIn(monitor.newChild(2));
			Map<String, String> artifactIds = setupTestBuildDefinition(connectionDetails, buildDefinitionId, monitor.newChild(10));
			setupPBDeliverConfigurationElement(connectionDetails, buildDefinitionId, artifactIds, configOrGenericProperties,
												monitor.newChild(70));
			return artifactIds;
		} finally {
			monitor.done();
		}
	}
	
	private void setupPBDeliverConfigurationElement(ConnectionDetails connectionDetails, String buildDefinitionId, 
							   Map<String,String> artifactIds, 
							   Map<String,String> configOrGenericProperties, IProgressMonitor progress) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
			connection.ensureLoggedIn(monitor.newChild(30));
			ITeamRepository repository = connection.getTeamRepository();
			BuildUtil.setupPBDeliverConfigurationElement(repository, buildDefinitionId, 
													artifactIds,
								 					configOrGenericProperties, monitor.newChild(70));
		} finally {
			monitor.done();
		}
	}
	
	private Map<String, IItemHandle> setupWorkspaceWithComponent(ITeamRepository repo, IWorkspaceConnection target, 
				String workspaceName, String componentName, 
			String [] filePaths) throws TeamRepositoryException {
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		IWorkspaceConnection buildWorkspace = null;
		if (target == null) {
			buildWorkspace = SCMUtil.createWorkspace(workspaceManager, workspaceName); 
		} else {
			buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager,target, workspaceName);
		}
		Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildWorkspace, componentName, filePaths);
		pathToHandle.put(workspaceName, buildWorkspace.getResolvedWorkspace());
		return pathToHandle;
	}
	
	@SuppressWarnings("unused")
	private Map<String, IItemHandle> setupBuildWorkspaceWithComponent(ITeamRepository repo, IWorkspaceConnection target, 
			String workspaceName, String componentName, 
			String [] filePaths) throws TeamRepositoryException {
		return setupWorkspaceWithComponent(repo, target, workspaceName, componentName, filePaths);
	}
	
	@SuppressWarnings("unused")
	private Map<String, IItemHandle> setupWorkspaceWithComponent(ITeamRepository repo, String workspaceName, String componentName, 
			String [] filePaths) throws TeamRepositoryException {
		return setupWorkspaceWithComponent(repo, null, workspaceName, componentName, filePaths);
	}

	public void testWorkItemHasRelatedArtifactLink(ConnectionDetails connectionDetails,
					String workItemid, String url) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(new NullProgressMonitor());
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkItem workItem = WorkItemUtil.getWorkItem(repo, workItemid);
		IWorkItemReferences references = WorkItemUtil.getWorkItemReferences(repo, workItem);
		List<IReference> rArtifacts = references.getReferences(RELATED_ARTIFACT);
		boolean matchFound = false;
		if (rArtifacts == null || (rArtifacts != null && rArtifacts.size() == 0)) {
			throw new TeamRepositoryException("Related artifact links not found");
		}
		if (rArtifacts.size() > 1 ) {
			throw new TeamRepositoryException("More than one related artifact link found");
		}
		IReference iReference = rArtifacts.get(0);
		if(iReference.createURI().toURL().toString().equals(url)) {
			matchFound = true;
		}
		if (!matchFound) {
			throw new TeamRepositoryException("URL not found." + 
							String.format("given URL is %s, other URL is %s", url, iReference.createURI().toURL().toString()));
		}
	}

	public void testWorkItemHasNoRelatedArtifactLink(ConnectionDetails connectionDetails, String workItemId) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(new NullProgressMonitor());
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkItem workItem = WorkItemUtil.getWorkItem(repo, workItemId);
		IWorkItemReferences references = WorkItemUtil.getWorkItemReferences(repo, workItem);
		List<IReference> rArtifacts = references.getReferences(RELATED_ARTIFACT);
		if (rArtifacts == null || (rArtifacts != null && rArtifacts.size() == 0)) {
			return;
		}
		if (rArtifacts.size() > 1 ) {
			throw new TeamRepositoryException("More than one related artifact link found when none is expected");
		}
	}

	public int deliverChangesFromWorkspaceToStream(ConnectionDetails connectionDetails,
						String streamUUID, String workspaceUUID) throws Exception {
		RepositoryConnection connection = super.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(new NullProgressMonitor());
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspace workspace = (IWorkspace) repo.itemManager().fetchCompleteItem(
					IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(workspaceUUID), null),
					IItemManager.REFRESH, new NullProgressMonitor());
		repo.itemManager();
		IWorkspace stream = (IWorkspace) repo.itemManager().fetchCompleteItem(
				IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(streamUUID), null),
				IItemManager.REFRESH, new NullProgressMonitor());

		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		IWorkspaceConnection workspaceConn = workspaceManager.getWorkspaceConnection(workspace, new NullProgressMonitor());
		IWorkspaceConnection streamConn =  workspaceManager.getWorkspaceConnection(stream, new NullProgressMonitor());
		//Deliver the changes
		IChangeHistorySyncReport report = workspaceConn.compareTo(streamConn, 
					WorkspaceComparisonFlags.CHANGE_SET_COMPARISON_ONLY,  
					Collections.EMPTY_LIST, null);
		workspaceConn.deliver(streamConn, 
				report, report.outgoingBaselines(), 
				report.outgoingChangeSets(), null);
		return report.outgoingChangeSets().size();
	}
	
	private boolean canWorkItemBeCreated(String buildToolkitVersion) {
		String [] unsupportedBuildToolkitVersions = {"6.0", "5.0.2", "5.0", "4.0.7", "5.0.1"};
		for (String unsupportedBuildToolkitVersion : unsupportedBuildToolkitVersions) {
			if (buildToolkitVersion.equals(unsupportedBuildToolkitVersion)) {
				return false;
			}
		}
		return true;
	}
}
