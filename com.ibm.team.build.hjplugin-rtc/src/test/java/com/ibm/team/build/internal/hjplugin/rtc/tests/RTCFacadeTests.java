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

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.HashMap;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProcessAreaHandle;
import com.ibm.team.process.common.IProcessDefinition;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.process.common.ITeamAreaHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;

/**
 * Test cases that validate the RTCFacade layer. This class is intended to test only those methods that doesn't need
 * elaborate validation scenarios like testProcessArea(). For methods like load() that need extensive test scenarios it
 * is recommended to create a separate test class.
 * 
 */
public class RTCFacadeTests {

	private RepositoryConnection connection;
	private static final String ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID1 = "paStream1ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_TEAM_AREA_STREAM_ITEM_ID1 = "taStream1ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID2 = "paStream2ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_TEAM_AREA_STREAM_ITEM_ID2 = "taStream2ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_PROJECT_AREA_DUPLICATE_STREAM_ITEM_ID_1 = "paDuplicateStream1ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_PROJECT_AREA_DUPLICATE_STREAM_ITEM_ID_2 = "paDuplicateStream2ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_TEAM_AREA_DUPLICATE_STREAM_ITEM_ID_1 = "taDuplicateStream1ItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_TEAM_AREA_DUPLICATE_STREAM_ITEM_ID_2 = "taDuplicateStream2ItemId"; //$NON-NLS-1$	
	private static final String ARTIFACT_PROJECT_AREA_GLOBAL_DUPLICATE_STREAM_ITEM_ID = "paGlobalDuplicateStreamItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_TEAM_AREA_GLOBAL_DUPLICATE_STREAM_ITEM_ID = "taGlobalDuplicateStreamItemId"; //$NON-NLS-1$
	private static final String ARTIFACT_REPOSITORY_WORKSPACE_ITEM_ID = "repoWsItemId"; //$NON-NLS-1$


	public RTCFacadeTests(RepositoryConnection repositoryConnection) {
		this.connection = repositoryConnection;
	}
	
	/**
	 * Create a project area with a single team area.
	 * 
	 * @param projectAreaName
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestProcessArea_basic(String projectAreaName) throws Exception {
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		String name = projectAreaName;
		// create and return the itemIds so that the teamconcert plugin test, that eventually invokes this method, knows
		// the artifacts to be deleted during tear down.
		IProcessDefinition processDefinition = ProcessUtil.getProcessDefinition(repo, name, false);
		setupArtifacts.put(TestSetupTearDownUtil.ARTIFACT_PROCESS_DEFINITION_ITEM_ID, processDefinition.getItemId().getUuidValue());
		ITeamArea teamArea = (ITeamArea)ProcessUtil.getProcessArea(repo, name, processDefinition, false);
		setupArtifacts.put(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID, teamArea.getProjectArea().getItemId().getUuidValue());
		return setupArtifacts;
	}

	/**
	 * Create a project area with a single team area and archive the project area.
	 * 
	 * @param projectAreaName
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestProcessArea_archiveProjectArea(String projectAreaName) throws Exception {
		Map<String, String> setupArtifacts = setupTestProcessArea_basic(projectAreaName);
		IProjectAreaHandle projectAreaHandle = (IProjectAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(
				UUID.valueOf(setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID)), null);
		IProjectArea projectArea = (IProjectArea)connection.getTeamRepository().itemManager()
				.fetchCompleteItem(projectAreaHandle, IItemManager.DEFAULT, null);
		IProcessItemService processItemService = (IProcessItemService)connection.getTeamRepository().getClientLibrary(IProcessItemService.class);
		processItemService.archiveProcessItem(projectArea, null);
		return setupArtifacts;
	}

	/**
	 * Create a project area with a single team area and archive the team area.
	 * 
	 * @param projectAreaName
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestProcessArea_archiveTeamArea(String projectAreaName) throws Exception {
		Map<String, String> setupArtifacts = setupTestProcessArea_basic(projectAreaName);
		IProjectAreaHandle projectAreaHandle = (IProjectAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(
				UUID.valueOf(setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID)), null);
		IProjectArea projectArea = (IProjectArea)connection.getTeamRepository().itemManager()
				.fetchCompleteItem(projectAreaHandle, IItemManager.DEFAULT, null);
		ITeamAreaHandle teamAreaHandle = (ITeamAreaHandle)projectArea.getTeamAreas().get(0);
		ITeamArea teamArea = (ITeamArea)connection.getTeamRepository().itemManager().fetchCompleteItem(teamAreaHandle, IItemManager.DEFAULT, null);
		IProcessItemService processItemService = (IProcessItemService)connection.getTeamRepository().getClientLibrary(IProcessItemService.class);
		processItemService.archiveProcessItem(teamArea, null);
		return setupArtifacts;
	}
	
	/**
	 * Creates the following artifacts
	 * 		1. project area
	 *  	2. team area under the project area (".team.area" will be appended to the project area name)
	 *  	3. a stream owned by the project area 
	 *  	4. a stream owned by the team area ("teamArea" will be prefixed to the stream name)
	 *      5. duplicate streams i.e. two streams with the same name under the project area ("duplicate" will be prefixed to the stream name)
	 *      6. duplicate streams i.e. two streams with the same name under the team area("teamAreaDuplicate" will be prefixed to the stream name)
	 *      7. two streams with the same name one in the project area and one in the team area ("globalDuplicate" will be prefixed to the stream name)
	 * 
	 * @param projectAreaName
	 * @param streamName
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestBuildStream_complete(String projectAreaName, String streamName) throws Exception {
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);

		Map<String, String> artifactIds = setupTestProcessArea_basic(projectAreaName);
		
		String projectAreaId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID);
		IProcessAreaHandle projectAreaHandle = (IProcessAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(UUID.valueOf(projectAreaId), null);
		IWorkspaceConnection buildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);
		// a stream owned by the project area
		artifactIds.put(ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID1, buildStream.getContextHandle().getItemId().getUuidValue());
		
		// duplicate streams i.e. two streams with the same name under the project area ("duplicate" will be prefixed to the stream name) - 1
		buildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, "duplicate" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_PROJECT_AREA_DUPLICATE_STREAM_ITEM_ID_1, buildStream.getContextHandle().getItemId().getUuidValue());
		
		// duplicate streams i.e. two streams with the same name under the project area ("duplicate" will be prefixed to the stream name) - 2
		buildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, "duplicate" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_PROJECT_AREA_DUPLICATE_STREAM_ITEM_ID_2, buildStream.getContextHandle().getItemId().getUuidValue());
		
		IProjectArea projectArea = (IProjectArea)connection.getTeamRepository().itemManager()
				.fetchCompleteItem(projectAreaHandle, IItemManager.DEFAULT, null);
		ITeamAreaHandle teamAreaHandle = (ITeamAreaHandle)projectArea.getTeamAreas().get(0);
		
		// a stream owned by the team area ("teamArea" will be prefixed to the stream name)
		buildStream = SCMUtil.createStream(workspaceManager, teamAreaHandle, "teamArea" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_TEAM_AREA_STREAM_ITEM_ID1, buildStream.getContextHandle().getItemId().getUuidValue());
		
		// duplicate streams i.e. two streams with the same name under the team area("teamAreaDuplicate" will be prefixed to the stream name) - 1
		buildStream = SCMUtil.createStream(workspaceManager, teamAreaHandle, "teamAreaDuplicate" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_TEAM_AREA_DUPLICATE_STREAM_ITEM_ID_1, buildStream.getContextHandle().getItemId().getUuidValue());
		
		// duplicate streams i.e. two streams with the same name under the team area("teamAreaDuplicate" will be prefixed to the stream name) - 2
		buildStream = SCMUtil.createStream(workspaceManager, teamAreaHandle, "teamAreaDuplicate" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_TEAM_AREA_DUPLICATE_STREAM_ITEM_ID_2, buildStream.getContextHandle().getItemId().getUuidValue());
		
		// two streams with the same name one in the project area and one in the team area ("globalDuplicate" will be prefixed to the stream name)
		buildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, "globalDuplicate" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_PROJECT_AREA_GLOBAL_DUPLICATE_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		buildStream = SCMUtil.createStream(workspaceManager, teamAreaHandle, "globalDuplicate" + streamName); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_TEAM_AREA_GLOBAL_DUPLICATE_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
		

		return artifactIds;
	}
	
	/**
	 * Creates the following artifacts
	 * 
	 * <pre>
	 * 		1. project area with the given name
	 *  	2. team area under the project area (".team.area" will be appended to the project area name)
	 *  	3. Two streams owned by the project area, with names pa_<streamName>1 and pa_<streamName>2
	 *  	4. Five snapshots owned by pa_<streamName>1 
	 *  		- two snapshots with the same name, pa_<streamName>1_<snapshotName>, duplicate at all levels
	 *  		- one snapshot with an unique name at the stream level, <projectAreaName>_<snapshotName>, unique at stream level and duplicate at project and repository levels
	 *  		- one snapshot with an unique name at the project area level, "universal_"<snapshotName>, unique at project level and duplicate at repository level
	 *  		- one snapshot with an unique name at the repository level, "universal_"<projectAreaName>_<snapshotName>, unique at repository level
	 *  	5. Three snapshots owned by pa_<streamName>2
	 *  		- two snapshots with the same name, pa_<streamName>2_<snapshotName>, duplicate at all levels
	 *  		- one snapshot with an unique name at the stream level, <projectAreaName>_<snapshotName>, unique at stream level and duplicate at project and repository levels
	 *  	6. Two streams owned by the team area, with names ta_<streamName>1 and ta_<streamName>2
	 *  	7. Five snapshots owned by ta_<streamName>1 
	 *  		- two snapshots with the same name, ta_<streamName>1_<snapshotName>, duplicate at all levels
	 *  		- one snapshot with an unique name at the stream level, <teamAreaName>_<snapshotName>, unique at stream level and duplicate at team and repository levels
	 *  		- one snapshot with an unique name at the team area level, "universal_"<snapshotName>, unique at team level and duplicate at repository level
	 *  		- one snapshot with an unique name at the repository level, "universal_"<teamAreaName>_<snapshotName>, unique at repository level
	 *  	8. Three snapshots owned by ta_<streamName>2
	 *  		- two snapshots with the same name, ta_<streamName>2_<snapshotName>, duplicate at all levels
	 *  		- one snapshot with an unique name at the stream level, <teamAreaName>_<snapshotName>, unique at stream level and duplicate at team and repository level 
	 *  	9. Create a repository workspace, with name <workspaceName>
	 *  	10. Three snapshots owned by the repository workspace <workspaceName>
	 *  		- two snapshots with the same name, <workspaceName>_<snapshotName>, duplicate at all levels
	 *  		- one snapshot with an unique name at the workspace level, "universal_"<snapshotName>, unique at workspace level and duplicate at repository level
	 * </pre>
	 * 
	 * 
	 * @param workspaceName
	 * @param projectAreaName
	 * @param streamName
	 * @param snapshotName
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestBuildSnapshot_complete(String workspaceName, String projectAreaName, String streamName, String snapshotName)
			throws Exception {
		ITeamRepository repo = connection.getTeamRepository();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		String componentName = "setupTestBuildSnapshot_complete_comp" + System.currentTimeMillis(); //$NON-NLS-1$

		Map<String, String> artifactIds = setupTestProcessArea_basic(projectAreaName);
		IProcessAreaHandle projectAreaHandle = (IProcessAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(
				UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID)), null);
		IProjectArea projectArea = (IProjectArea)connection.getTeamRepository().itemManager()
				.fetchCompleteItem(projectAreaHandle, IItemManager.DEFAULT, null);
		ITeamAreaHandle teamAreaHandle = (ITeamAreaHandle)projectArea.getTeamAreas().get(0);

		// Two streams owned by the project area, with names pa_<streamName>1 and pa_<streamName>2
		// create pa_<streamName>1
		IWorkspaceConnection stream = SCMUtil.createStream(workspaceManager, projectAreaHandle, "pa_" + streamName + 1); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID1, stream.getContextHandle().getItemId().getUuidValue());

		SCMUtil.addComponent(workspaceManager, stream, componentName);

		// Five snapshots owned by pa_<streamName>1
		// two snapshots with the same name, pa_<streamName>1_<snapshotName>, duplicate at all levels
		SCMUtil.createSnapshot(stream, "pa_" + streamName + "1_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		SCMUtil.createSnapshot(stream, "pa_" + streamName + "1_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		// one snapshot with an unique name at the stream level, <projectAreaName>_<snapshotName>, unique at stream
		// level and duplicate at project and repository levels
		SCMUtil.createSnapshot(stream, projectAreaName + "_" + snapshotName); //$NON-NLS-1$
		// one snapshot with an unique name at the project area level, "universal_"<snapshotName>, unique at project
		// level and duplicate at repository level
		SCMUtil.createSnapshot(stream, "universal_" + snapshotName); //$NON-NLS-1$
		// one snapshot with an unique name at the repository level, "universal_"<projectAreaName>_<snapshotName>,
		// unique at repository level
		SCMUtil.createSnapshot(stream, "universal_" + projectAreaName + "_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$

		// create pa_<streamName>2
		stream = SCMUtil.createStream(workspaceManager, projectAreaHandle, "pa_" + streamName + 2); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID2, stream.getContextHandle().getItemId().getUuidValue());

		SCMUtil.addComponent(workspaceManager, stream, componentName);

		// Five snapshots owned by pa_<streamName>2
		// two snapshots with the same name, pa_<streamName>2_<snapshotName>, duplicate at all levels
		SCMUtil.createSnapshot(stream, "pa_" + streamName + "2_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		SCMUtil.createSnapshot(stream, "pa_" + streamName + "2_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		// one snapshot with an unique name at the stream level, <projectAreaName>_<snapshotName>, unique at stream
		// level and duplicate at project and repository levels
		SCMUtil.createSnapshot(stream, projectAreaName + "_" + snapshotName); //$NON-NLS-1$

		// Two streams owned by the team area, with names ta_<streamName>1 and ta_<streamName>2
		// create pa_<streamName>1
		String teamAreaName = projectAreaName + ".team.area"; //$NON-NLS-1$
		stream = SCMUtil.createStream(workspaceManager, teamAreaHandle, "ta_" + streamName + 1); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_TEAM_AREA_STREAM_ITEM_ID1, stream.getContextHandle().getItemId().getUuidValue());

		SCMUtil.addComponent(workspaceManager, stream, componentName);

		// Five snapshots owned by ta_<streamName>1
		// two snapshots with the same name, ta_<streamName>1_<snapshotName>, duplicate at all levels
		SCMUtil.createSnapshot(stream, "ta_" + streamName + "1_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		SCMUtil.createSnapshot(stream, "ta_" + streamName + "1_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		// one snapshot with an unique name at the stream level, <teamAreaName>_<snapshotName>, unique at stream level
		// and duplicate at team and repository levels
		SCMUtil.createSnapshot(stream, teamAreaName + "_" + snapshotName); //$NON-NLS-1$
		// one snapshot with an unique name at the team area level, "universal_"<snapshotName>, unique at team level and
		// duplicate at repository level
		SCMUtil.createSnapshot(stream, "universal_" + snapshotName); //$NON-NLS-1$
		// one snapshot with an unique name at the repository level, "universal_"<teamAreaName>_<snapshotName>, unique
		// at repository level
		SCMUtil.createSnapshot(stream, "universal_" + teamAreaName + "_" + snapshotName); //$NON-NLS-1$  //$NON-NLS-2$

		// create ta_<streamName>2
		stream = SCMUtil.createStream(workspaceManager, teamAreaHandle, "ta_" + streamName + 2); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_TEAM_AREA_STREAM_ITEM_ID2, stream.getContextHandle().getItemId().getUuidValue());

		SCMUtil.addComponent(workspaceManager, stream, componentName);

		// Five snapshots owned by pa_<streamName>2
		// two snapshots with the same name, ta_<streamName>2_<snapshotName>, duplicate at all levels
		SCMUtil.createSnapshot(stream, "ta_" + streamName + "2_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		SCMUtil.createSnapshot(stream, "ta_" + streamName + "2_" + snapshotName); //$NON-NLS-1$ //$NON-NLS-2$
		// one snapshot with an unique name at the stream level, <teamAreaName><snapshotName>, unique at stream level
		// and duplicate at team and repository levels
		SCMUtil.createSnapshot(stream, teamAreaName + "_" + snapshotName); //$NON-NLS-1$
		
		// Create a repository workspace, with name <workspaceName>
		IWorkspaceConnection wsConnection = SCMUtil
				.createWorkspace(workspaceManager, workspaceName, "setupTestBuildSnapshot_complete test workspace"); //$NON-NLS-1$
		artifactIds.put(ARTIFACT_REPOSITORY_WORKSPACE_ITEM_ID, wsConnection.getContextHandle().getItemId().getUuidValue());
		
		SCMUtil.addComponent(workspaceManager, wsConnection, componentName);
		
		// Three snapshots owned by the repository workspace <workspaceName>
		// two snapshots with the same name, <snapshotName>, duplicate at all levels
		SCMUtil.createSnapshot(wsConnection, snapshotName);
		SCMUtil.createSnapshot(wsConnection, snapshotName);
		// one snapshot with an unique name at the workspace level, "universal_"<snapshotName>, unique at workspace
		// level and duplicate at repository level
		SCMUtil.createSnapshot(wsConnection, "universal_" + snapshotName); //$NON-NLS-1$
		
		return artifactIds;
	}

	public static void tearDownTestBuildStream_complete(ITeamRepository repo, Map<String, String> artifactIds) throws Exception {
		// delete what we created
		// some of the artifacts are deleted in TestSetupTearDownUtils
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID1));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_TEAM_AREA_STREAM_ITEM_ID1));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_PROJECT_AREA_DUPLICATE_STREAM_ITEM_ID_1));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_PROJECT_AREA_DUPLICATE_STREAM_ITEM_ID_2));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_TEAM_AREA_DUPLICATE_STREAM_ITEM_ID_1));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_TEAM_AREA_DUPLICATE_STREAM_ITEM_ID_2));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_PROJECT_AREA_GLOBAL_DUPLICATE_STREAM_ITEM_ID));
		SCMUtil.deleteWorkspace(repo, artifactIds.get(ARTIFACT_TEAM_AREA_GLOBAL_DUPLICATE_STREAM_ITEM_ID));
	}
	
	public static void tearDownTestBuildSnapshot_complete(ITeamRepository repo, Map<String, String> artifactIds) throws Exception {
		// delete what we created
		// some of the artifacts are deleted in TestSetupTearDownUtils
		SCMUtil.deleteWorkspaceAndAssociatedSnapshots(repo, artifactIds.get(ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID1));
		SCMUtil.deleteWorkspaceAndAssociatedSnapshots(repo, artifactIds.get(ARTIFACT_PROJECT_AREA_STREAM_ITEM_ID2));
		SCMUtil.deleteWorkspaceAndAssociatedSnapshots(repo, artifactIds.get(ARTIFACT_TEAM_AREA_STREAM_ITEM_ID1));
		SCMUtil.deleteWorkspaceAndAssociatedSnapshots(repo, artifactIds.get(ARTIFACT_TEAM_AREA_STREAM_ITEM_ID2));
		SCMUtil.deleteWorkspaceAndAssociatedSnapshots(repo, artifactIds.get(ARTIFACT_REPOSITORY_WORKSPACE_ITEM_ID));
	}
}
