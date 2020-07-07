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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildEngineHandle;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildRequest;
import com.ibm.team.build.common.model.IBuildRequestParams;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConfiguration;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.build.internal.hjplugin.rtc.BuildSnapshotDescriptor;
import com.ibm.team.build.internal.hjplugin.rtc.BuildStreamDescriptor;
import com.ibm.team.build.internal.hjplugin.rtc.Constants;
import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;
import com.ibm.team.build.internal.hjplugin.rtc.Messages;
import com.ibm.team.build.internal.hjplugin.rtc.NonValidatingLoadRuleFactory;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RTCSnapshotUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RTCWorkspaceUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.build.internal.scm.ComponentLoadRules;
import com.ibm.team.build.internal.scm.LoadComponents;
import com.ibm.team.build.internal.scm.RepositoryManager;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.process.common.IProcessAreaHandle;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IFlowNodeConnection.IComponentAdditionOp;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.BaselineSetFlags;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;

@SuppressWarnings({ "nls", "restriction" })
public class BuildConfigurationTests {
	private RepositoryConnection connection;

	public BuildConfigurationTests(RepositoryConnection repositoryConnection) {
		this.connection = repositoryConnection; 
	}

	public Map<String, String> setupComponentLoading(String workspaceName, String buildDefinitionId,
			String testName, String hjPath, String buildPath) throws Exception {
		
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		try {
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, workspaceName);
			Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildWorkspace, testName, new String[] {
					"/",
					"/f/",
					"/f/a.txt",
					});
			pathToHandle.putAll(SCMUtil.addComponent(workspaceManager, buildWorkspace, testName + "2", new String[] {
					"/",
					"/g/",
					"/g/b.txt",
					}));
			
			pathToHandle.putAll(SCMUtil.addComponent(workspaceManager, buildWorkspace, testName + "3", new String[] {
					"/",
					"/h/",
					"/h/c.txt",
					}));
			
			
			IComponentHandle component = (IComponentHandle) pathToHandle.get(testName);
	
			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "true",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, "",
					IJazzScmConfigurationElement.PROPERTY_INCLUDE_COMPONENTS, "true",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.singletonList(component)).getBuildProperty() );

			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
		
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testComponentLoading(String workspaceName, String buildDefinitionId,
			String testName, String hjPath, String buildPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertTrue("Should be a list of components to include", buildConfiguration.includeComponents());
		AssertUtil.assertTrue("Should be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(0, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(1, buildConfiguration.getComponents().size());
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID), buildConfiguration.getComponents().iterator().next().getItemId().getUuidValue());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse("Deletion should not be needed", buildConfiguration.isDeleteNeeded());
	}

	public Map<String, String> setupNewLoadRules(String workspaceName,
			String componentName, String buildDefinitionId, String hjPath, String buildPath) throws Exception {
		// create a build definition with new format load rules
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading

		// create a build engine
		// create a build request
		
		// verify that the buildConfiguration returns the load rules
		// and other settings.
		

		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		try {
			IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
			Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
					"/",
					"/f/",
					"/f/a.txt",
					"/g/",
					"/g/b.txt",
					"/h/",
					"/h/c.txt",
					});
			IComponent component = (IComponent) pathToHandle.get(componentName);
			IChangeSetHandle cs = buildStream.createChangeSet(component, null);
			SCMUtil.addVersionables(buildStream, component, cs, pathToHandle,
					new String[] {"/h/new.loadRule"}, 
					new String[] {getNewLoadRule(componentName, "f")},
					"setupNewLoadRules"); // load rule to load f directory
			buildStream.closeChangeSets(Collections.singletonList(cs), null);
			IFileItemHandle loadRuleFile = (IFileItemHandle) pathToHandle.get("/h/new.loadRule");
			Map<IComponentHandle, IFileItemHandle> loadRuleFiles = Collections.singletonMap((IComponentHandle) component, loadRuleFile);
			ComponentLoadRules loadRule = new ComponentLoadRules(loadRuleFiles);
			artifactIds.put("LoadRuleProperty", loadRule.getBuildPropertySetting());

			IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
			
			// cs1 adds h.txt & i.txt
			IChangeSetHandle cs1 = buildStream.createChangeSet(component, null);
			SCMUtil.addVersionables(buildStream, component, cs1, pathToHandle, new String[] {
					"/f/h.txt",
					"/g/i.txt"
			}, "SetupNewLoadRules");
			buildStream.closeChangeSets(Collections.singletonList(cs1), null);

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRule.getBuildPropertySetting(),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.<IComponentHandle>emptyList()).getBuildProperty() );

			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
		
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}
	
	private String getNewLoadRule(String componentName, String folderName) {
		return getNewLoadRule(componentName, folderName, false);
	}

	private String getNewLoadRule(String componentName, String folderName, boolean createFoldersForComponents) {
		String loadRule = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<!--Built from the sandbox \"C:\\BuildPlugin3\" and the workspace \"JenkinsInitiation Development Workspace\"-->\n"
				+ "<!--Generated: 2013-04-02 14.49.08-->\n"
				+ "<scm:sourceControlLoadRule version=\"1\" xmlns:scm=\"http://com.ibm.team.scm\">\n"
				+ "    <parentLoadRule>\n"
				+ "        <component name=\"" + componentName + "\" />\n" 
				+ "        <parentFolder repositoryPath=\"/" + folderName + "\" />\n" 
				+ (createFoldersForComponents? "<sandboxRelativePath includeComponentName=\"true\"/>":"")
				+ "    </parentLoadRule>\n"
				+ "</scm:sourceControlLoadRule>\n";
		return loadRule;
	}

	public void testNewLoadRules(String workspaceName, String testName, String buildDefinitionId, 
			String hjPath, String buildPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(1, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
	}

	public Map<String, String> setupOldLoadRules(String workspaceName,
			String componentName, String hjPath) throws Exception {
		// Create build definition with old style load rules
		// load directory missing
		// don't delete directory before loading (dir has other stuff that will(not) be deleted)
		// don't accept changes before loading

		// create a build engine
		// create a build request
		
		// verify that the buildConfiguration returns the load rules
		// and other settings.

		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		try {
			IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
			Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, componentName, new String[] {
					"/",
					"/f/",
					"/f/a.txt",
					"/g/",
					"/g/b.txt",
					"/h/",
					"/h/c.txt",
					});
			IComponent component = (IComponent) pathToHandle.get(componentName);
			IChangeSetHandle cs = buildStream.createChangeSet(component, null);
			SCMUtil.addVersionables(buildStream, component, cs, pathToHandle,
					new String[] {"/h/new.loadRule"}, 
					new String[] {getOldLoadRule(componentName, "f")},
					"setupOldLoadRules"); // load rule to load f directory
			buildStream.closeChangeSets(Collections.singletonList(cs), null);
			IFileItemHandle loadRuleFile = (IFileItemHandle) pathToHandle.get("/h/new.loadRule");
			Map<IComponentHandle, IFileItemHandle> loadRuleFiles = Collections.singletonMap((IComponentHandle) component, loadRuleFile);
			ComponentLoadRules loadRule = new ComponentLoadRules(loadRuleFiles);
			artifactIds.put("LoadRuleProperty", loadRule.getBuildPropertySetting());

			IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
			
			// cs1 adds h.txt & i.txt
			IChangeSetHandle cs1 = buildStream.createChangeSet(component, null);
			SCMUtil.addVersionables(buildStream, component, cs1, pathToHandle, new String[] {
					"/f/h.txt",
					"/g/i.txt"
			},"setupOldLoadRules");
			buildStream.closeChangeSets(Collections.singletonList(cs1), null);

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, componentName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRule.getBuildPropertySetting(),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.<IComponentHandle>emptyList()).getBuildProperty() );
			
			Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
			
			// create the build result
			String buildResultItemId = connection.createBuildResult(componentName, null, "my buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (failure[0] != null) {
				throw failure[0];
			}
		
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}
	
	public Map<String, String> setupBuildDefinition_loadRulesWithNoLoadPolicy(String workspaceName, String componentName,
			String buildDefinitionId, String hjPath, String buildPath, boolean configureLoadRules, boolean setLoadPolicy)
			throws Exception {
		// create a build definition with new format load rules
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// do not set loadPolicy or set it to useComponentLoadConfig

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds,
					configureLoadRules);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty());
			if (configureLoadRules) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES,
						artifactIds.get("LoadRuleProperty"));
			}
			if (setLoadPolicy) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY,
						Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG);
			}
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);

			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_loadRulesWithNoLoadPolicy(String workspaceName, String buildDefinitionId, String hjPath,
			String buildPath, Map<String, String> artifactIds, boolean configureLoadRules, boolean setLoadPolicy)
			throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		// when loadPolicy is set to useComponentLoadConfig loadRules are not set
		AssertUtil.assertEquals(
				configureLoadRules && !setLoadPolicy? 1 : 0,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue(" isLoadPolicySetToUseComponentLoadConfig is not as expected",
				setLoadPolicy ? buildConfiguration.isLoadPolicySetToUseComponentLoadConfig()== true : buildConfiguration.isLoadPolicySet() == false);
		AssertUtil.assertTrue(" isLoadPolicySet is not as expected",
				setLoadPolicy ? buildConfiguration.isLoadPolicySet() == true : buildConfiguration.isLoadPolicySet() == false);
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents is not as expected",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}

	public Map<String, String> setupBuildDefinition_loadRulesWithLoadPolicySetToLoadRules(String workspaceName, String componentName,
			String buildDefinitionId, String hjPath, String buildPath, boolean configureLoadRules) throws Exception {
		// create a build definition with new format load rules
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// set loadPolicy to useLoadRules

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, configureLoadRules);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty(), Constants.PROPERTY_LOAD_POLICY,
					Constants.LOAD_POLICY_USE_LOAD_RULES);
			if (configureLoadRules) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES,
						artifactIds.get("LoadRuleProperty"));
			}

			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);

			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_loadRulesWithLoadPolicySetToLoadRules(String workspaceName, String buildDefinitionId, String hjPath,
			String buildPath, Map<String, String> artifactIds, boolean configureLoadRules) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		@SuppressWarnings("rawtypes")
		Collection loadRules = buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", configureLoadRules ? loadRules.size() == 1 : loadRules.size() == 0);
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySet should be true", buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}

	public Map<String, String> setupBuildDefinition_toTestCreateFolderForComponents(String workspaceName, String componentName,
			String buildDefinitionId, String hjPath, String buildPath, boolean shouldCreateFoldersForComponents,
			String loadPolicy, String componentLoadConfig) throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add and set team.scm.loadPolicy and team.scm.componentLoadConfig properties
		// select create folders for components option depending on the value of setCreateFoldersForComponents

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, false);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, shouldCreateFoldersForComponents ? "true" : "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty());
			if (loadPolicy != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY, loadPolicy);
			}
			if (componentLoadConfig != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_COMPONENT_LOAD_CONFIG, componentLoadConfig);
			}
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_createFoldersForComponents(String workspaceName, String buildDefinitionId, String hjPath, String buildPath,
			Map<String, String> artifactIds, boolean shouldCreateFoldersForComponents, String loadPolicy, String componentLoadConfig) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil
				.assertTrue(
					"Create folders for components is not as expected",
						shouldCreateFoldersForComponents
									&& (loadPolicy == null || loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)) ? buildConfiguration
									.createFoldersForComponents() == true : buildConfiguration.createFoldersForComponents() == false);
		AssertUtil.assertEquals(
				0,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil
				.assertTrue(
						"isLoadPolicySetToUseLoadRules is not as expected ",
						loadPolicy != null && Constants.LOAD_POLICY_USE_LOAD_RULES.equals(loadPolicy) ? buildConfiguration
								.isLoadPolicySetToUseLoadRules() == true : buildConfiguration.isLoadPolicySetToUseLoadRules() == false);
		AssertUtil.assertTrue(
				"isLoadPolicySetToUseComponentLoadConfig is not as expected ",
				loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy) ? buildConfiguration
						.isLoadPolicySetToUseComponentLoadConfig() == true : buildConfiguration.isLoadPolicySetToUseComponentLoadConfig() == false);
		AssertUtil.assertTrue("isLoadPolicySet is not as expected",
				loadPolicy != null ? buildConfiguration.isLoadPolicySet() == true : buildConfiguration.isLoadPolicySet() == false);
		AssertUtil
				.assertTrue(
						"isComponentLoadConfigSetToExcludeSomeComponents is not as expected",
						componentLoadConfig != null && componentLoadConfig.equals(Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS) ? buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == true : buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == false);
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}

	public Map<String, String> setupBuildDefinition_toTestCreateFoldersForComponents_usingLoadRules(String workspaceName, String componentName,
			String buildDefinitionId, String hjPath, String buildPath) throws Exception {
		// create a build definition with new format load rules that creates root folders with the component name
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// set lodaPolicy to useLoadRules
		// create folders for components using load rules rather than the option in the Jazz SCM configuration

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, true,
					false, false, true, false, false, false);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty(),
					Constants.PROPERTY_LOAD_POLICY, Constants.LOAD_POLICY_USE_LOAD_RULES);

			BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES,
					artifactIds.get("LoadRuleProperty"));

			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);

			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_createFoldersForComponents_usingLoadRules(String workspaceName, String buildDefinitionId, String hjPath,
			String buildPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Create folders for components should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(
				1,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules is not as expected ", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig is not as expected ", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isLoadPolicySet is not as expected", buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents is not as expected",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}

	public Map<String, String> setupBuildDefinition_toTestComponentsToExclude(String workspaceName, String componentName, String buildDefinitionId,
			String hjPath, String buildPath, boolean shouldExcludeComponents, String loadPolicy, String componentLoadConfig) throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add and set team.scm.loadPolicy and team.scm.componentLoadConfig properties
		// exclude components depending on the value of shouldExcludeComponents

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds,
					false);
			// create the build definition
			BuildUtil.createBuildDefinition(
					repo,
					buildDefinitionId,
					true,
					artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION,
					buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH,
					"true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH,
					"true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS,
					"false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(shouldExcludeComponents ? Collections.singletonList((IComponentHandle)IComponent.ITEM_TYPE.createItemHandle(
							UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID)), null)) : Collections
							.<IComponentHandle> emptyList()).getBuildProperty());
			if (loadPolicy != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY, loadPolicy);
			}
			if (componentLoadConfig != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_COMPONENT_LOAD_CONFIG, componentLoadConfig);
			}
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_componentsToExclude(String workspaceName, String buildDefinitionId, String hjPath, String buildPath,
			Map<String, String> artifactIds, boolean shouldExcludeComponents, String loadPolicy, String componentLoadConfig) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Create folders for components should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(
				0,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		Collection<IComponentHandle> componentsToExclude = buildConfiguration.getComponents();
		AssertUtil
				.assertTrue(
						"List of components to exclude is not as expected",
						shouldExcludeComponents
								&& (loadPolicy == null || (Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy) && Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS
										.equals(componentLoadConfig))) ? componentsToExclude.size() == 1 : componentsToExclude.size() == 0);
		// only component 2 is excluded
		if (shouldExcludeComponents
				&& (loadPolicy == null || Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)
						&& Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS.equals(componentLoadConfig))) {
			AssertUtil.assertTrue(
					"components to exclude list is not a expected", UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID)).equals(
									componentsToExclude.iterator().next().getItemId()));
		}
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil
				.assertTrue(
						"isLoadPolicySetToUseLoadRules is not as expected ",
						loadPolicy != null && Constants.LOAD_POLICY_USE_LOAD_RULES.equals(loadPolicy) ? buildConfiguration
								.isLoadPolicySetToUseLoadRules() == true : buildConfiguration.isLoadPolicySetToUseLoadRules() == false);
		AssertUtil.assertTrue(
				"isLoadPolicySetToUseComponentLoadConfig is not as expected ",
				loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy) ? buildConfiguration
						.isLoadPolicySetToUseComponentLoadConfig() == true : buildConfiguration.isLoadPolicySetToUseComponentLoadConfig() == false);
		AssertUtil.assertTrue("isLoadPolicySet is not as expected",
				loadPolicy != null ? buildConfiguration.isLoadPolicySet() == true : buildConfiguration.isLoadPolicySet() == false);
		AssertUtil
				.assertTrue(
						"isComponentLoadConfigSetToExcludeSomeComponents is not as expected",
						componentLoadConfig != null && componentLoadConfig.equals(Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS) ? buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == true : buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == false);
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}

	public Map<String, String> setupBuildDefinition_toTestIncludeComponents(String workspaceName, String componentName, String buildDefinitionId,
			String hjPath, String buildPath, boolean addLoadComponents, String valueOfIncludeComponents, String loadPolicy, String componentLoadConfig)
			throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add and set loadPolicy property
		// setup a list of components to include/exclude depending on the value of addLoadComponents
		// add and set include components property to valueOfIncludeComponents

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, false);
			// create the build definition
			BuildUtil.createBuildDefinition(
					repo,
					buildDefinitionId,
					true,
					artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION,
					buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH,
					"true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH,
					"true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS,
					"false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(addLoadComponents ? Collections.singletonList((IComponentHandle)IComponent.ITEM_TYPE
							.createItemHandle(UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID)), null)) : Collections
							.<IComponentHandle> emptyList()).getBuildProperty()); 
			if (loadPolicy != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY, loadPolicy);
			}
			
			if (componentLoadConfig != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_COMPONENT_LOAD_CONFIG, componentLoadConfig);
			}
			BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, IJazzScmConfigurationElement.PROPERTY_INCLUDE_COMPONENTS,
					valueOfIncludeComponents);			
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_includeComponents(String workspaceName, String buildDefinitionId, String hjPath, String buildPath,
			Map<String, String> artifactIds, boolean addLoadComponents, String valueOfIncludeComponents, String loadPolicy, String componentLoadConfig)
			throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertTrue(
				"includeComponents is not as expected", Boolean.valueOf(valueOfIncludeComponents)
								&& (loadPolicy == null || Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)
										&& Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS.equals(componentLoadConfig)) ? buildConfiguration
								.includeComponents() == true : buildConfiguration.includeComponents() == false);
		AssertUtil.assertFalse("Create folders for components should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(
				0,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		Collection<IComponentHandle> loadComponents = buildConfiguration.getComponents();
		AssertUtil.assertTrue("Load components list is not as expected", addLoadComponents
						&& (loadPolicy == null || Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)
								&& Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS.equals(componentLoadConfig)) ? loadComponents.size() == 1
						: loadComponents.size() == 0);
		if (addLoadComponents
				&& (loadPolicy == null || Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)
				&& Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS.equals(componentLoadConfig))) {
			AssertUtil.assertTrue(
					"Load components list is not a expected", UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID)).equals(
									loadComponents.iterator().next().getItemId()));
		}

		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil
				.assertTrue(
						"isLoadPolicySetToUseLoadRules is not as expected ",
						loadPolicy != null && Constants.LOAD_POLICY_USE_LOAD_RULES.equals(loadPolicy) ? buildConfiguration
								.isLoadPolicySetToUseLoadRules() == true : buildConfiguration.isLoadPolicySetToUseLoadRules() == false);
		AssertUtil.assertTrue(
				"isLoadPolicySetToUseComponentLoadConfig is not as expected ",
				loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy) ? buildConfiguration
						.isLoadPolicySetToUseComponentLoadConfig() == true : buildConfiguration.isLoadPolicySetToUseComponentLoadConfig() == false);
		AssertUtil.assertTrue("isLoadPolicySet is not as expected",
				loadPolicy != null ? buildConfiguration.isLoadPolicySet() == true : buildConfiguration.isLoadPolicySet() == false);
		AssertUtil
				.assertTrue(
						"isComponentLoadConfigSetToExcludeSomeComponents is not as expected",
						componentLoadConfig != null && componentLoadConfig.equals(Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS) ? buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == true : buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == false);
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}
	
	public Map<String, String> setupBuildDefinition_toTestMultipleLoadRuleFiles(String workspaceName, String componentName, String buildDefinitionId,
			String hjPath, String buildPath, boolean setLoadPolicyToUseLoadRules) throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add and set loadPolicy property
		// add multiple load rules files

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds,
					true, true, false, false, false, false, false);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty(),
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, artifactIds.get("LoadRuleProperty"));
			if (setLoadPolicyToUseLoadRules) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY, Constants.LOAD_POLICY_USE_LOAD_RULES);
			}
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	@SuppressWarnings("rawtypes")
	public void testBuildDefinitionConfig_multipleLoadRuleFiles(String workspaceName, String buildDefinitionId, String hjPath, String buildPath,
			Map<String, String> artifactIds, boolean setLoadPolicyToUseLoadRules) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Create folders for components should be false", buildConfiguration.createFoldersForComponents());

		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertTrue(
				"isLoadPolicySetToUseLoadRules should be true", setLoadPolicyToUseLoadRules ? buildConfiguration.isLoadPolicySetToUseLoadRules() == true : buildConfiguration
								.isLoadPolicySetToUseLoadRules() == false);
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isLoadPolicySet is not as expected", setLoadPolicyToUseLoadRules ? buildConfiguration.isLoadPolicySet() == true
						: buildConfiguration.isLoadPolicySet() == false);
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		try {
			Collection loadRules = buildConfiguration.getComponentLoadRules(
					workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, ps, null, Locale.getDefault());
			if (setLoadPolicyToUseLoadRules) {
				AssertUtil.fail("Exception expected");
			} else {
				AssertUtil.assertTrue("There should be 2 load rule files.", loadRules.size() == 2);
				AssertUtil.assertEquals(Messages.get(Locale.getDefault()).BuildConfiguration_multiple_load_rule_files_deprecated(), baos.toString()
						.trim());
			}
		} catch (RTCConfigurationException e) {
			if (setLoadPolicyToUseLoadRules) {
				AssertUtil
						.assertEquals(Messages.get(Locale.getDefault()).BuildConfiguration_multiple_load_rule_files_not_supported(), e.getMessage());
			} else {
				AssertUtil.fail("Exception not expected: " + e.getMessage());
			}
		} finally {
			try {
				ps.close();
			} catch (Exception e) {
				// ignore
			}
		}
	}
	
	public Map<String, String> setupBuildDefinition_toTestOldLoadRulesFormat(String workspaceName, String componentName, String buildDefinitionId,
			String hjPath, String buildPath, boolean setLoadPolicyToUseLoadRules) throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add and set loadPolicy property
		// add load rules in old format

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds,
					true, false, true, false, false, false, false);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty(),
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES,
					artifactIds.get("LoadRuleProperty"));
			if (setLoadPolicyToUseLoadRules) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY, Constants.LOAD_POLICY_USE_LOAD_RULES);
			}
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	@SuppressWarnings("rawtypes")
	public void testBuildDefinitionConfig_oldLoadRulesFormat(String workspaceName, String buildDefinitionId, String hjPath, String buildPath,
			Map<String, String> artifactIds, boolean setLoadPolicyToUseLoadRules) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Create folders for components should be false", buildConfiguration.createFoldersForComponents());

		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertTrue(
				"isLoadPolicySetToUseLoadRules should be true", setLoadPolicyToUseLoadRules ? buildConfiguration.isLoadPolicySetToUseLoadRules() == true : buildConfiguration
								.isLoadPolicySetToUseLoadRules() == false);
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isLoadPolicySet should be true", setLoadPolicyToUseLoadRules ? buildConfiguration.isLoadPolicySet() == true
						: buildConfiguration.isLoadPolicySet() == false);
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		try {
			Collection loadRules = buildConfiguration.getComponentLoadRules(
					workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, ps, null, Locale.getDefault());
			if (setLoadPolicyToUseLoadRules) {
				AssertUtil.fail("Exception expected");
			} else {
				AssertUtil.assertTrue("There should be 1 load rule file.", loadRules.size() == 1);
				AssertUtil.assertEquals(Messages.get(Locale.getDefault()).NonValidatingLoadRuleFactory_old_load_rules_format_deprecated(), baos
						.toString().trim());
			}
		} catch (RTCConfigurationException e) {
			if (setLoadPolicyToUseLoadRules) {
				AssertUtil
						.assertEquals(Messages.get(Locale.getDefault()).NonValidatingLoadRuleFactory_load_rules_not_in_XML_format(), e.getMessage());

			} else {
				AssertUtil.fail("Exception not expected: " + e.getMessage());
			}
		} finally {
			try {
				ps.close();
			} catch (Exception e) {
				// ignore
			}
		}
		baos = new ByteArrayOutputStream();
		ps = new PrintStream(baos);
		try {
			IWorkspaceConnection buildWorkspace = SCMPlatform.getWorkspaceManager(repo).getWorkspaceConnection(
					(IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(
							UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID)), null), null);
			NonValidatingLoadRuleFactory.checkForObsoleteLoadRuleFormat(
					buildWorkspace,
					(IComponentHandle)IComponent.ITEM_TYPE.createItemHandle(
							UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID)), null),
					(IFileItemHandle)IFileItem.ITEM_TYPE.createItemHandle(
							UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_FILE_ITEM_ID)), null),
					setLoadPolicyToUseLoadRules, ps, null, Locale.getDefault());
			if (setLoadPolicyToUseLoadRules) {
				AssertUtil.fail("Exception expected");
			} else {
				AssertUtil.assertEquals(Messages.get(Locale.getDefault()).NonValidatingLoadRuleFactory_old_load_rules_format_deprecated(), baos
						.toString().trim());
			}
		} catch (RTCConfigurationException e) {
			if (setLoadPolicyToUseLoadRules) {
				AssertUtil
						.assertEquals(Messages.get(Locale.getDefault()).NonValidatingLoadRuleFactory_load_rules_not_in_XML_format(), e.getMessage());

			} else {
				AssertUtil.fail("Exception not expected: " + e.getMessage());
			}
		} finally {
			try {
				ps.close();
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private IWorkspaceConnection setupWorkspace_toTestLoadPolicy(String workspaceName, String componentName, ITeamRepository repo,
			Map<String, String> artifactIds, boolean configureLoadRules) throws Exception {
		return setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, configureLoadRules, false, false,
				false, false, false, false);
	}

	private IWorkspaceConnection setupWorkspace_toTestLoadPolicy(String workspaceName, String componentName, ITeamRepository repo,
			Map<String, String> artifactIds, boolean configureLoadRules, boolean addDuplicateComponents) throws Exception {
		return setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, configureLoadRules, false, false,
				false, addDuplicateComponents, false, false);
	}

	private IWorkspaceConnection setupWorkspace_toTestLoadPolicy(String workspaceName, String componentName, ITeamRepository repo,
			Map<String, String> artifactIds, boolean configureLoadRules, boolean addDuplicateComponents, boolean createStream) throws Exception {
		return setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, configureLoadRules, false, false,
				false, addDuplicateComponents, createStream, false);
	}

	private IWorkspaceConnection setupWorkspace_toTestLoadPolicy(String workspaceName, String componentName, ITeamRepository repo,
			Map<String, String> artifactIds, boolean configureLoadRules, boolean addDuplicateComponents, boolean createStream, boolean createSnapshot)
			throws Exception {
		return setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, configureLoadRules, false, false,
				false, addDuplicateComponents, createStream, createSnapshot);
	}
	
	@SuppressWarnings("unchecked")
	private IWorkspaceConnection setupWorkspace_toTestLoadPolicy(String workspaceName, String componentName, ITeamRepository repo,
			Map<String, String> artifactIds, boolean configureLoadRules, boolean configureMultipleLoadRuleFiles, boolean configureOldRules,
			boolean createFoldersForComponents, boolean addDuplicateComponents, boolean createStream, boolean createSnapshot) throws Exception {
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);

		try {
			IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
			String component1_Name = componentName + 1;
			String component2_Name = componentName + 2;
			// add multiple components
			// comp1
			Map<String, IItemHandle> pathToHandle1 = SCMUtil.addComponent(workspaceManager, buildStream, component1_Name, new String[] { "/",
					"/f-comp1/", "/f-comp1/a-comp1.txt", "/g-comp1/", "/g-comp1/b-comp1.txt", "/h-comp1/", "/h-comp1/c-comp1.txt", });
			IComponent component1 = (IComponent)pathToHandle1.get(component1_Name);
			// comp2
			Map<String, IItemHandle> pathToHandle2 = SCMUtil.addComponent(workspaceManager, buildStream, component2_Name, new String[] { "/", "/f-comp2/", "/f-comp2/a.txt", "/g-comp2/",
					"/g-comp2/b.txt", "/h-comp2/", "/h-comp2/c.txt", });
			IComponent component2 = (IComponent)pathToHandle2.get(component2_Name);

			if (addDuplicateComponents) {
				String component3_Name = componentName + 3;
				IComponent duplicateComponent = workspaceManager.createComponent(component3_Name, workspaceManager.teamRepository()
						.loggedInContributor(), null);
				IComponentAdditionOp componentOp = buildStream.componentOpFactory().addComponent(duplicateComponent, false);
				buildStream.applyComponentOperations(Collections.singletonList(componentOp), null);
				duplicateComponent = workspaceManager.createComponent(component3_Name, workspaceManager.teamRepository()
						.loggedInContributor(), null);
				componentOp = buildStream.componentOpFactory().addComponent(duplicateComponent, false);
				buildStream.applyComponentOperations(Collections.singletonList(componentOp), null);
			}

			IChangeSetHandle cs = buildStream.createChangeSet(component1, null);
			// load rule to load f directory
			SCMUtil.addVersionables(
					buildStream,
					component1,
					cs,
					pathToHandle1,
					new String[] { "/h-comp1/new.loadRule" },
					new String[] { configureOldRules ? getOldLoadRule(component1_Name, "f-comp1") : getNewLoadRule(component1_Name, "f-comp1",
							createFoldersForComponents) }, "setupWorkspace_toTestLoadPolicy");
			buildStream.closeChangeSets(Collections.singletonList(cs), null);
			if (configureLoadRules) {
				IFileItemHandle loadRuleFile = (IFileItemHandle)pathToHandle1.get("/h-comp1/new.loadRule");
				Map<IComponentHandle, IFileItemHandle> loadRuleFiles = new HashMap<IComponentHandle, IFileItemHandle>();
				loadRuleFiles.put((IComponentHandle)component1, loadRuleFile);
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_FILE_ITEM_ID, loadRuleFile.getItemId().getUuidValue());
				if (configureMultipleLoadRuleFiles) {
					cs = buildStream.createChangeSet(component2, null);
					SCMUtil.addVersionables(
							buildStream,
							component2,
							cs,
							pathToHandle2,
							new String[] { "/h-comp2/new.loadRule" },
							new String[] { configureOldRules ? getOldLoadRule(component2_Name, "f-comp2") : getNewLoadRule(component2_Name,
									"f-comp2", createFoldersForComponents) }, "setupWorkspace_toTestLoadPolicy");
					buildStream.closeChangeSets(Collections.singletonList(cs), null);
					loadRuleFile = (IFileItemHandle)pathToHandle2.get("/h-comp2/new.loadRule");
					loadRuleFiles.put((IComponentHandle)component2, loadRuleFile);
				}
				ComponentLoadRules loadRule = new ComponentLoadRules(loadRuleFiles);
				artifactIds.put("LoadRuleProperty", loadRule.getBuildPropertySetting());
			}

			IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);

			// cs1 adds h-comp1.txt & i-comp1.txt
			IChangeSetHandle cs1 = buildStream.createChangeSet(component1, null);
			SCMUtil.addVersionables(buildStream, component1, cs1, pathToHandle1, new String[] { "/f-comp1/h-comp1.txt", "/g-comp1/i-comp1.txt" },
					"setupWorkspace_toTestLoadPolicy");
			buildStream.closeChangeSets(Collections.singletonList(cs1), null);
			
			if (createStream) {
				String streamName = workspaceName + "_lrStream";
				RTCFacadeTests rtcFacadeTests = new RTCFacadeTests(connection);
				Map<String, String> paArtifactIds = rtcFacadeTests.setupTestProcessArea_basic(workspaceName + "_PA");
				artifactIds.putAll(paArtifactIds);
				String projectAreaId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID);
				IProcessAreaHandle projectAreaHandle = (IProcessAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(UUID.valueOf(projectAreaId), null);
				IWorkspaceConnection lrBuildStream = SCMUtil.createStream(workspaceManager, projectAreaHandle, streamName);
				List<IComponentHandle> components = buildWorkspace.getComponents();
				List<IComponentAdditionOp> componentAddOps = new ArrayList<IComponentAdditionOp>();
				for (IComponentHandle compHandle : components) {
					componentAddOps.add(lrBuildStream.componentOpFactory().addComponent(compHandle, buildWorkspace, false));
				}
				lrBuildStream.applyComponentOperations(componentAddOps, null);
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_ITEM_ID, lrBuildStream.getContextHandle().getItemId().getUuidValue());
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_NAME, lrBuildStream.getName());
			}

			if (createSnapshot) {
				// Create a baseline set for the workspace
				String snapshotName = workspaceName + "_lrSS";
				IBaselineSetHandle baselineSet = buildWorkspace.createBaselineSet(null, snapshotName, null, BaselineSetFlags.DEFAULT, null);
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_SS_ITEM_ID, baselineSet.getItemId().getUuidValue());
			}

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component1.getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID, component2.getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_TEST_FOLDER_ITEM_ID, pathToHandle1.get("/h-comp1/").getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_FILE_PATH, "/h-comp1/new.loadRule");
			return buildWorkspace;
		} catch (Exception e) {
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_ITEM_ID));
			throw e;
		}
	}

	private String getOldLoadRule(String componentName, String folderName) {
		String loadRule = "# Two directives are supported: \n" +
					"# folderName=\n" + 
					"# RootFolderName=\n" +
					"\n" + 
					"RootFolderName=/" + folderName;
		
		return loadRule;
	}

	public void testOldLoadRules(String workspaceName, String testName,
			String hjPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertFalse("Should not be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(1, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse("Deletion is not needed", buildConfiguration.isDeleteNeeded());
	}
	
	
	public Map<String, String> setupOldLoadRules_setAllLoadOptions(String workspaceName,
			String componentName, String hjPath) throws Exception {
		// Create build definition with old style load rules
		// load directory missing
		// don't delete directory before loading (dir has other stuff that will(not) be deleted)
		// don't accept changes before loading

		// create a build engine
		// create a build request
		
		// verify that the buildConfiguration returns the load rules
		// and other settings.

		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, true, false, true,
					false, false, false, false);
			
			// create the build definition
			BuildUtil.createBuildDefinition(
					repo,
					componentName,
					true,
					artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH,
					"true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH,
					"true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS,
					"true",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES,
					artifactIds.get("LoadRuleProperty"),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.singletonList((IComponentHandle)IComponent.ITEM_TYPE.createItemHandle(
							UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID)), null))).getBuildProperty() );

			Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
			
			// create the build result
			String buildResultItemId = connection.createBuildResult(componentName, null, "my buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (failure[0] != null) {
				throw failure[0];
			}
		
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testOldLoadRules_setAllLoadOptions(String workspaceName, String testName,
			String hjPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should not be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertTrue("Should be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(1, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(1, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue("Deletion is needed", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertFalse("isLoadPolicySet should be false", buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false", buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
	}

	public Map<String, String> setupPersonalBuild(String workspaceName,
			String testName, String hjPath, String buildPath) throws Exception {
		// create a build definition
		// load directory ${propertyA}/here
		// build properties
		// myPropsFile = ${team.scm.fetchDestination}/com.ibm.team.build.releng/continuous-buildsystem.properties
		// propertyA = loadDir
		// propertyB = a place (${propertyA}) to load some stuff 
		// propertyC = original
		// using a load rule

		// create a build engine
		// create a build request for this test with personal build specified
		// (build workspace overridden, build engine is a random one, override load rule, override a build property)
		
		// verify that the buildConfiguration returns the personal build workspace
		// as the workspace to be loaded
		// verify the load rule is changed
		// verify the build property is changed
		// verify property substitution done

		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		try {
			IWorkspaceConnection buildStream = SCMUtil.createWorkspace(workspaceManager, workspaceName + "_stream");
			Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildStream, testName, new String[] {
					"/",
					"/f/",
					"/f/a.txt",
					"/g/",
					"/g/b.txt",
					"/h/",
					"/h/c.txt",
					});
			IComponent component = (IComponent) pathToHandle.get(testName);
			IChangeSetHandle cs = buildStream.createChangeSet(component, null);
			SCMUtil.addVersionables(buildStream, component, cs, pathToHandle,
					new String[] {"/h/new.loadRule", "/h/old.loadRule"}, 
					new String[] {getNewLoadRule(testName, "f"), getOldLoadRule(testName, "g")},
					"setupPersonalBuild"); 
			buildStream.closeChangeSets(Collections.singletonList(cs), null);
			IFileItemHandle loadRuleFile = (IFileItemHandle) pathToHandle.get("/h/new.loadRule");
			IFileItemHandle oldLoadRuleFile = (IFileItemHandle) pathToHandle.get("/h/old.loadRule");
			ComponentLoadRules loadRule = new ComponentLoadRules(Collections.singletonMap((IComponentHandle) component, loadRuleFile));
			ComponentLoadRules oldLoadRule = new ComponentLoadRules(Collections.singletonMap((IComponentHandle) component, oldLoadRuleFile));
			artifactIds.put("LoadRuleProperty", oldLoadRule.getBuildPropertySetting());

			IWorkspaceConnection buildWorkspace = SCMUtil.createBuildWorkspace(workspaceManager, buildStream, workspaceName);
			
			// cs1 adds h.txt & i.txt
			IChangeSetHandle cs1 = buildWorkspace.createChangeSet(component, null);
			SCMUtil.addVersionables(buildWorkspace, component, cs1, pathToHandle, new String[] {
					"/f/h.txt",
					"/g/i.txt"
			},"setupPersonalBuild");
			buildWorkspace.closeChangeSets(Collections.singletonList(cs1), null);

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRule.getBuildPropertySetting(),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.<IComponentHandle>emptyList()).getBuildProperty(),
					"myPropsFile", "${team.scm.fetchDestination}/com.ibm.team.build.releng/continuous-buildsystem.properties",
					"propertyA", "loadDir",
					"propertyB", "a place (${propertyA}) to load some stuff", 
					"propertyC", "original");
			
			Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
			
			// create the build result for a personal build
			IBuildResultHandle buildResultHandle = createPersonalBuildResult(testName, buildStream.getResolvedWorkspace(),
					"my buildLabel", oldLoadRule.getBuildPropertySetting(), listener );
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultHandle.getItemId().getUuidValue());
			if (failure[0] != null) {
				throw failure[0];
			}
		
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}
	/**
	 * Create a build result to report back the build progress in RTC
	 * @param buildDefinitionId The id of the build definition. There must be a build definition and it
	 * will need to have an active build engine associated with it.
	 * @param personalBuildWorkspace Override the build workspace in the build definition with a personal workspace
	 * @param buildLabel The label to assign to the build
	 * @param listener A log to report progress and failures to.
	 * @return The build result to update with progress of the Jenkins build. May be <code>null</code>
	 * if there is no build engine associated with the build definition
	 * @throws TeamRepositoryException Thrown if problems are encountered
	 * @throws RTCConfigurationException Thrown if the build definition is not valid
	 */
	private IBuildResultHandle createPersonalBuildResult(String buildDefinitionId, IWorkspaceHandle personalBuildWorkspace, String buildLabel,
			String loadRuleProperty, IConsoleOutput listener) throws TeamRepositoryException, RTCConfigurationException {
		ITeamRepository repo = connection.getTeamRepository();
		BuildConnection buildConnection = new BuildConnection(repo);
		IBuildDefinition buildDefinition = buildConnection.getBuildDefinition(buildDefinitionId, null);
		if (buildDefinition == null) {
			throw new RTCConfigurationException(Messages.getDefault().BuildConnection_build_definition_not_found(buildDefinitionId));
		}
		List<IBuildProperty> modifiedProperties = new ArrayList<IBuildProperty>();
		IBuildProperty originalProperty = buildDefinition.getProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
		if (originalProperty != null && !originalProperty.getValue().equals(personalBuildWorkspace.getItemId().getUuidValue())) {
			modifiedProperties.add(BuildItemFactory.createBuildProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, personalBuildWorkspace.getItemId().getUuidValue()));
		} else {
			AssertUtil.fail("Should of been able to override build workspace");
		}
		originalProperty = buildDefinition.getProperty(IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES);
		if (originalProperty != null && !originalProperty.getValue().equals(loadRuleProperty)) {
			modifiedProperties.add(BuildItemFactory.createBuildProperty(IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRuleProperty));
		} else {
			AssertUtil.fail("Shoud of been able to override load rule");
		}
		
		originalProperty = buildDefinition.getProperty(IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH);
		if (originalProperty != null && !originalProperty.getValue().equals("false")) {
			modifiedProperties.add(BuildItemFactory.createBuildProperty(IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "false"));
		} else {
			AssertUtil.fail("Shoud of been able to override delete destination before fetch");
		}
		originalProperty = buildDefinition.getProperty("propertyC");
		if (originalProperty != null && !originalProperty.getValue().equals("overwritten")) {
			modifiedProperties.add(BuildItemFactory.createBuildProperty("propertyC", "overwritten"));
		} else {
			AssertUtil.fail("Shoud of been able to override propertyC");
		}

		IBuildEngineHandle buildEngine = buildConnection.getBuildEngine(buildDefinition, null);
		if (buildEngine == null) {
			throw new RTCConfigurationException(Messages.getDefault().BuildConnection_no_build_engine_for_defn(buildDefinitionId));
		}

        IBuildRequestParams params = BuildItemFactory.createBuildRequestParams();
        params.setBuildDefinition(buildDefinition);
        params.getNewOrModifiedBuildProperties().addAll(modifiedProperties);
        params.setAllowDuplicateRequests(true);
        params.setPersonalBuild(true);
        params.getPotentialHandlers().add(buildEngine);
        params.setStartBuild(true);
        IBuildRequest buildRequest = ClientFactory.getTeamBuildRequestClient(repo).requestBuild(params, new NullProgressMonitor());
        IBuildResultHandle buildResultHandle = buildRequest.getBuildResult();

        if (buildLabel != null) {
            IBuildResult buildResult = (IBuildResult) repo.itemManager().fetchPartialItem(
                    buildResultHandle, IItemManager.REFRESH,
                    Arrays.asList(IBuildResult.PROPERTIES_VIEW_ITEM), new NullProgressMonitor());

            buildResult = (IBuildResult) buildResult.getWorkingCopy();
            buildResult.setLabel(buildLabel);
            ClientFactory.getTeamBuildClient(repo).save(buildResult, new NullProgressMonitor());
        }

		return buildResultHandle;
	}

	public void testPersonalBuild(String workspaceName, String testName,
			String hjPath, String buildPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		final Exception[] failure = new Exception[] {null};
		final boolean[] propertiesLogged = new boolean[] {false};
		IConsoleOutput listener = new IConsoleOutput() {
			
			@Override
			public void log(String message, Exception e) {
				failure[0] = e;
			}
			
			@Override
			public void log(String message) {
				// ok, just logging property substitutions to the log
				propertiesLogged[0] = true;
			}
		};
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}
		if (!propertiesLogged[0]) {
			AssertUtil.fail("Property substitutions not logged");
		}

		AssertUtil.assertTrue("Should be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertEquals(
				1,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, "loadDir/here");
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse("Deletion is needed", buildConfiguration.isDeleteNeeded());
		// myPropsFile = ${team.scm.fetchDestination}/com.ibm.team.build.releng/continuous-buildsystem.properties
		// propertyA = loadDir
		// propertyB = a place (${propertyA}) to load some stuff 
		// propertyC = original
		Map<String, String> buildProperties = buildConfiguration.getBuildProperties();
		AssertUtil.assertEquals("loadDir", buildProperties.get("propertyA"));
		AssertUtil.assertEquals(buildProperties.get("team.scm.fetchDestination") + "/com.ibm.team.build.releng/continuous-buildsystem.properties", buildProperties.get("myPropsFile"));
		AssertUtil.assertEquals("a place (loadDir) to load some stuff", buildProperties.get("propertyB"));
		AssertUtil.assertEquals("overwritten", buildProperties.get("propertyC"));
	}

	public Map<String, String> setupBadFetchLocation(String workspaceName,
			String testName, String hjPath, String buildPath) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		
		try {
			IWorkspaceConnection buildWorkspace = SCMUtil.createWorkspace(workspaceManager, workspaceName);
			Map<String, IItemHandle> pathToHandle = SCMUtil.addComponent(workspaceManager, buildWorkspace, testName, new String[] {
					"/",
					"/f/",
					"/f/a.txt",
					});
			
			IComponentHandle component = (IComponentHandle) pathToHandle.get(testName);
	
			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					null,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "true",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, "",
					IJazzScmConfigurationElement.PROPERTY_INCLUDE_COMPONENTS, "true",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.singletonList(component)).getBuildProperty() );
			
			Exception[] failure = new Exception[] {null};
			IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
			
			// create the build result
			String buildResultItemId = connection.createBuildResult(testName, null, "my buildLabel", listener, null, Locale.getDefault());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultItemId);
			if (failure[0] != null) {
				throw failure[0];
			}
		
			return artifactIds;
		} catch (Exception e) {
			
			// cleanup artifacts created
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testGoodFetchLocation(String workspaceName,
			String testName, String hjPath, String buildPath,
			Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			AssertUtil.fail("The relative fetch location should have been good: " + buildPath);
		}
	}

	public void testBadFetchLocation(String workspaceName,
			String testName, String hjPath, String buildPath,
			Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		try {
			buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
			if (failure[0] != null) {
				throw failure[0];
			}
			AssertUtil.fail("The relative fetch location should have been bad: " + buildPath);
		} catch (RTCConfigurationException e) {
			// good, the fetch location was bad
		}
	}
	
	public void testLoadSnapshotConfiguration(String snapshotName, String workspacePrefix, String hjPath) throws Exception {
		connection.ensureLoggedIn(null);
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		
		ITeamRepository repo = connection.getTeamRepository();
		RepositoryManager manager = connection.getRepositoryManager();
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		IBaselineSet bs = RTCSnapshotUtils.getSnapshot(repo, null, snapshotName, null, Locale.getDefault());
		buildConfiguration.initialize(bs, repo.loggedInContributor(), workspacePrefix, null, null, null, false, null, null, listener, Locale.getDefault(),
				null);
		if (listener.hasFailure()) {
			throw listener.getFailure();
		}
		// Things that have be right in BuildConfiguration
		AssertUtil.assertEquals(buildConfiguration.getBuildSnapshotDescriptor().getSnapshotUUID(), bs.getItemId().getUuidValue());
		AssertUtil.assertTrue("WorkspaceDescriptor cannot be null for snapshot load", buildConfiguration.getBuildWorkspaceDescriptor() != null);
		AssertUtil.assertFalse("isPersonalBuild cannot be true for a snapshot load", buildConfiguration.isPersonalBuild());
		AssertUtil.assertFalse("acceptBeforeFetch cannot be true for a snapshot load", buildConfiguration.acceptBeforeFetch());

		IWorkspaceHandle workspaceHandle =  buildConfiguration.getBuildWorkspaceDescriptor().getWorkspaceHandle();
		String workspaceName = buildConfiguration.getBuildWorkspaceDescriptor().getConnection(manager, false, null).getName();		
		// verify the following
		AssertUtil.assertFalse("createFolders for components cannot be true for testLoadSnapshotConfiguration", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertFalse("includeComponents cannot be true for testLoadSnapshotConfiguration", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("isDeleteNeeded cannot be true for  testLoadSnapshotConfiguration", buildConfiguration.isDeleteNeeded());
		AssertUtil.assertEquals(buildConfiguration.getComponents(), Collections.emptyList());
		AssertUtil.assertEquals(buildConfiguration.getFetchDestinationFile().getCanonicalPath(), hjPath);
		AssertUtil.assertEquals(buildConfiguration.getSnapshotName(), null);
		
		// There should not be any build properties (for now)
		AssertUtil.assertTrue("buildProperties has to be zero size", buildConfiguration.getBuildProperties().keySet().size() == 0);

		// Call tearDown and ensure that the workspace is deleted
		buildConfiguration.tearDown(manager, false, null, listener, Locale.getDefault());
		try {
			workspaceManager.getWorkspaceConnection(workspaceHandle, null);
			AssertUtil.fail("tearDown failed to delete workspace " + workspaceName);
		}	catch (ItemNotFoundException exp) {
			// this is what we want
		}
	}
	
	public Map<String, String> setupRepositoryWorkspaceConfig_toTestLoadPolicy(String workspaceName, String componentName) throws Exception{
		// create a repository workspace
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, true, true);
			// create a component but don't add it to workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IComponent component = workspaceManager.createComponent(componentName, workspaceManager.teamRepository().loggedInContributor(), null);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID, component.getItemId().getUuidValue());
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testRepositoryWorkspaceConfig_toTestLoadPolicy(String workspaceName, String componentName, String hjPath, String buildPath,
			String pathToLoadRuleFile, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();

		String componentId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID);
		String loadRuleItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_FILE_ITEM_ID);
		String addedComponentId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID);
		
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		IWorkspaceHandle workspaceHandle = RTCWorkspaceUtils.getInstance().getWorkspace(workspaceName, repo, new NullProgressMonitor(),
				Locale.getDefault());		
		
		// Validate load rules
		// valid pathToLoadRuleFile
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, pathToLoadRuleFile, listener,
				Locale.getDefault(), new NullProgressMonitor());

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		@SuppressWarnings("rawtypes")
		Collection loadRules = buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 1);
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySet should be true", buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());

		// validate RTCWorkspaceUtils.getComponentLoadRuleString() indirectly through buildConfiguration.initialize
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #1 - no load rules
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, null,
				listener, Locale.getDefault(), new NullProgressMonitor());
		loadRules = buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
				System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 0);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #2 - missing separator
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					"testLoadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_invalid_format("testLoadRule"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #3 - missing componentName
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					"/testLoadRule/ws.loadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_no_component_name("/testLoadRule/ws.loadRule"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #4 - missing file path
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					"testLoadRule/", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_no_file_path("testLoadRule/"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #5 - duplicateComponentName
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentName + "3/ws.loadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_multiple_components_with_name_in_ws(componentName + 3, workspaceName),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #6 - non-existent component in the repository
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentName + "12/ws.loadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RepositoryConnection_component_with_name_not_found(componentName + "12"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #7 - non-existent component in the workspace
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentName + "/ws.loadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_component_with_name_not_found_ws(componentName, workspaceName),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #8 - non-existent file
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentName + "1/ws.loadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_file_not_found_ws("ws.loadRule", componentName + 1,
							workspaceName), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #9 - folder
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentName + "1/h-comp1", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_not_a_file_ws("h-comp1", componentName + 1, workspaceName),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #10 - valid ids
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		loadRules = buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
				System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 1);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #11 - non-existent-componentId in repo
		String tempComponentId = UUID.generate().getUuidValue();
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					tempComponentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RepositoryConnection_component_with_id_not_found(tempComponentId),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #12 - non-existent-componentId in ws
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					addedComponentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_component_with_id_not_found_ws(addedComponentId, workspaceName),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #13 - non-existent-fileId in repo
		String tempFileId = UUID.generate().getUuidValue();
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentId + "/" + tempFileId, listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_file_with_id_not_found_ws(tempFileId, componentName + 1,
							workspaceName), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #14 - folder id
		String folderItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_TEST_FOLDER_ITEM_ID);
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null,
					componentId + "/" + folderItemId, listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_with_id_not_a_file_ws(folderItemId, componentName + 1,
							workspaceName), e.getMessage());
		}
		
		// validate that isLoadPolicySet, isLoadPolicySetToUseLoadRules, isComponentLoadConfigSetToExcludeComponents,
		// createFoldersForComponents, components, and componentLoadRules are set to appropriate values depending on the
		// value of loadPolicy and componentLoadConfig
		
		// #15 set createFoldersForComponents to false, do not set componentsToExclude and componentLoadRules, do not
		// set loadPolicy and componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, null, null, false, null, null, listener, Locale.getDefault(),
				new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #16 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules, 
		// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig 
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #17 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules, 
		// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig 
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #18 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules, 
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to loadAllComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #19 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to loadAllComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #20 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #21 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #22 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES,
				null, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #23 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES,
				null, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #24 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #25 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_LOAD_RULES,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #26 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// do not set loadPolicy and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, null,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #27 set createFoldersForComponents to false, set componentsToExclude to multiple component names and set
		// componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// add same component name, should not be added twice	
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, componentName + "1, " + componentName + "1",
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #28 set createFoldersForComponents to false, set componentsToExclude to multiple component ids and set
		// componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID)
						+ "," + artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 2);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #29 set createFoldersForComponents to false, set componentsToExclude to multiple component ids and set
		// componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// add same component id twice, should be added only once
		buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID)
						+ "," + artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #30 add duplicate component name, which will fail
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		
		try {
			buildConfiguration.initialize(workspaceHandle, workspaceName, null, true, Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
					Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, componentName + "1, " + componentName + "3", componentId + "/"
							+ loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_multiple_components_with_name_in_ws(componentName + 3, workspaceName),
					e.getMessage());
		}
	}

	public Map<String, String> setupStreamConfig_toTestLoadPolicy(String workspaceName, String componentName) throws Exception {
		// create a repository workspace
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, true, true, true);
			// Create a component but don't add it to workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IComponent component = workspaceManager.createComponent(componentName, workspaceManager.teamRepository().loggedInContributor(), null);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID, component.getItemId().getUuidValue());
			IWorkspaceConnection streamConnection = workspaceManager.getWorkspaceConnection(
					(IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(
							UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_ITEM_ID)), null), null);
			// Create a workspace for the stream
			IWorkspaceConnection workspaceConnection = SCMUtil
					.createBuildWorkspace(workspaceManager, streamConnection, workspaceName + "_lrStreamWS");

			// Create a baseline set for the workspace
			String snapshotName = workspaceName + "_lrStreamSS";
			IBaselineSetHandle baselineSet = workspaceConnection.createBaselineSet(null, snapshotName, null, BaselineSetFlags.DEFAULT, null);

			// Change the owner of the baselineset to the stream
			streamConnection.addBaselineSet(baselineSet, null);

			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_WS_ITEM_ID, workspaceConnection.getContextHandle().getItemId()
					.getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_SS_ITEM_ID, baselineSet.getItemId().getUuidValue());

			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}
	
	public void testStreamConfig_loadPolicy(String workspaceName, String componentName, String hjPath, String buildPath, String pathToLoadRuleFile,
			Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();

		String componentId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID);
		String loadRuleItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_FILE_ITEM_ID);
		String addedComponentId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID);

		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		IWorkspaceHandle streamHandle = (IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(
				UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_ITEM_ID)), null);
		IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshotByUUID(repo,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_SS_ITEM_ID), new NullProgressMonitor(), Locale.getDefault());
		IWorkspace workspace = RTCWorkspaceUtils.getInstance().getWorkspace(
				UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_WS_ITEM_ID)), repo, new NullProgressMonitor(),
				Locale.getDefault());
		IContributor contributor = repo.loggedInContributor();
		// valid pathToLoadRuleFile with null loadPolicy
		buildConfiguration
				.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
						Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, pathToLoadRuleFile, listener, Locale.getDefault(),
						new NullProgressMonitor());

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_WS_ITEM_ID), workspaceDescriptor.getWorkspaceHandle()
				.getItemId().getUuidValue());
		AssertUtil.assertFalse("Should not be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		@SuppressWarnings("rawtypes")
		Collection loadRules = buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 1);
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySet should be true", buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		
		BuildStreamDescriptor buildStreamDescriptor = buildConfiguration.getBuildStreamDescriptor();
		AssertUtil.assertEquals(workspaceName + "_lrStream", buildStreamDescriptor.getName());
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_STREAM_SS_ITEM_ID), buildStreamDescriptor.getSnapshotUUID());

		// validate RTCWorkspaceUtils.getComponentLoadRuleString() indirectly through buildConfiguration.initialize
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #1 - no load rules
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, null, listener, Locale.getDefault(), new NullProgressMonitor());
		loadRules = buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
				System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 0);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #2 - missing separator
		try {
			buildConfiguration
					.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
							Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, "testLoadRule", listener, Locale.getDefault(),
							new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_invalid_format("testLoadRule"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #3 - missing componentName
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, "/testLoadRule/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_no_component_name("/testLoadRule/ws.loadRule"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #4 - missing file path
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, "testLoadRule/", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_no_file_path("testLoadRule/"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #5 - duplicateComponentName
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "3/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_multiple_components_with_name_in_stream(componentName + 3,
							workspaceName + "_lrStream"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #6 - non-existent component in the repository
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "12/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RepositoryConnection_component_with_name_not_found(componentName + "12"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #7 - non-existent component in the workspace
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_component_with_name_not_found_stream(componentName,
							workspaceName + "_lrStream"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #8 - non-existent file
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "1/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_file_not_found_stream("ws.loadRule", componentName + 1,
							workspaceName + "_lrStream"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #9 - folder
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "1/h-comp1", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_not_a_file_stream("h-comp1", componentName + 1,
							workspaceName + "_lrStream"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #10 - valid ids
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
				new NullProgressMonitor());
		loadRules = buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
				System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 1);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #11 - non-existent-componentId in repo
		String tempComponentId = UUID.generate().getUuidValue();
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, tempComponentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RepositoryConnection_component_with_id_not_found(tempComponentId),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #12 - non-existent-componentId in ws
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, addedComponentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_component_with_id_not_found_stream(addedComponentId,
							workspaceName + "_lrStream"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #13 - non-existent-fileId in repo
		String tempFileId = UUID.generate().getUuidValue();
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentId + "/" + tempFileId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_file_with_id_not_found_stream(tempFileId, componentName + 1,
							workspaceName + "_lrStream"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #14 - folder id
		String folderItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_TEST_FOLDER_ITEM_ID);
		try {
			buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentId + "/" + folderItemId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_with_id_not_a_file_stream(folderItemId, componentName + 1,
							workspaceName + "_lrStream"), e.getMessage());
		}
		
		
		// validate that isLoadPolicySet, isLoadPolicySetToUseLoadRules, isComponentLoadConfigSetToExcludeComponents,
		// createFoldersForComponents, components, and componentLoadRules are set to appropriate values depending on the
		// value of loadPolicy and componentLoadConfig

		// #15 set createFoldersForComponents to false, do not set componentsToExclude and componentLoadRules, do not
		// set loadPolicy and componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor, null, null, false, null,
				null, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #16 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #17 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #18 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to loadAllComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #19 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to loadAllComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #20 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #21 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #22 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId
						+ "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #23 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId
						+ "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #24 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_LOAD_RULES, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());

		// #25 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_LOAD_RULES, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());
		
		// #26 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// do not set loadPolicy and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				null, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());		

		// #27 set createFoldersForComponents to false, set componentsToExclude to multiple component names and set
		// componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// set non-existent component names to make sure that we ignore them
		buildConfiguration.initialize(streamHandle, workspaceName + "_lrStream", workspace, baselineSet, true, contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false, componentName
						+ "1, " + componentName + "2," + componentName, componentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
				new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 2);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());		

		// #28 set createFoldersForComponents to false, set componentsToExclude to multiple component Ids and set
		// componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		
		// include non-existent component ids to make sure that we ignore them
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(
				streamHandle,
				workspaceName + "_lrStream",
				workspace,
				baselineSet,
				true,
				contributor,
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS,
				false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID) + ","
						+ artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID) + "," + UUID.generate().getUuidValue(), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 2);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertFalse("isSnapshotLoad should be false", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertTrue("isStreamLoad should be true", buildConfiguration.isStreamLoad());
	}

	public Map<String, String> setupSnapshotConfig_toTestLoadPolicy(String workspaceName, String componentName) throws Exception {
		// create a repository workspace
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, true, true, true, true);
			// Create a component but don't add it to workspace
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IComponent component = workspaceManager.createComponent(componentName, workspaceManager.teamRepository().loggedInContributor(), null);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID, component.getItemId().getUuidValue());
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}
	
	public void testSnapshotConfig_loadPolicy(String workspaceName, String componentName, String hjPath, String buildPath,
			String pathToLoadRuleFile, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();

		String componentId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID);
		String loadRuleItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_FILE_ITEM_ID);
		String addedComponentId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT_ADDED_ITEM_ID);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		IBaselineSet baselineSet = RTCSnapshotUtils.getSnapshotByUUID(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_SS_ITEM_ID), new NullProgressMonitor(), Locale.getDefault());
		IContributor contributor = repo.loggedInContributor();
		// valid pathToLoadRuleFile
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		buildConfiguration
				.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration", Constants.LOAD_POLICY_USE_LOAD_RULES,
						null, false, null, pathToLoadRuleFile, listener, Locale.getDefault(), new NullProgressMonitor());

		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse("Should NOT be a personal build", buildConfiguration.isPersonalBuild());
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_SS_WS_ITEM_ID,
				workspaceDescriptor.getWorkspace(connection.getRepositoryManager(), null).getItemId().getUuidValue());
		AssertUtil.assertTrue("Not the expected workspace descriptor", workspaceDescriptor.getWorkspace(connection.getRepositoryManager(), null).getName().startsWith("HJP"));
		AssertUtil.assertFalse("Should not be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil.assertFalse("Should not be creating a folder for the component", buildConfiguration.createFoldersForComponents());
		@SuppressWarnings("rawtypes")
		Collection loadRules = buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null, System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 1);
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isLoadPolicySet should be true", buildConfiguration.isLoadPolicySet());
		BuildSnapshotDescriptor buildSnapshotDescriptor = buildConfiguration.getBuildSnapshotDescriptor();
		AssertUtil.assertEquals(workspaceName + "_lrSS", buildSnapshotDescriptor.getSnapshotName());
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_LOAD_RULE_SS_ITEM_ID), buildSnapshotDescriptor.getSnapshotUUID());

		// validate RTCWorkspaceUtils.getComponentLoadRuleString() indirectly through buildConfiguration.initialize
		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #1 - no load rules
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, null, listener, Locale.getDefault(), new NullProgressMonitor());
		loadRules = buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
				System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 0);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #2 - missing separator
		try {
			buildConfiguration
					.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration", Constants.LOAD_POLICY_USE_LOAD_RULES,
							null, false, null, "testLoadRule", listener, Locale.getDefault(), new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_invalid_format("testLoadRule"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #3 - missing componentName
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, "/testLoadRule/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_no_component_name("/testLoadRule/ws.loadRule"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #4 - missing file path
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, "testLoadRule/", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RTCWorkspaceUtils_path_to_load_rule_file_no_file_path("testLoadRule/"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #5 - duplicateComponentName
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "3/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_multiple_components_with_name_in_snapshot(componentName + 3,
							workspaceName + "_lrSS"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #6 - non-existent component in the repository
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "12/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RepositoryConnection_component_with_name_not_found(componentName + "12"),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #7 - non-existent component in the workspace
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_component_with_name_not_found_snapshot(componentName,
							workspaceName + "_lrSS"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #8 - non-existent file
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "1/ws.loadRule", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_file_not_found_snapshot("ws.loadRule", componentName + 1,
							workspaceName + "_lrSS"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #9 - folder
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentName + "1/h-comp1", listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_not_a_file_snapshot("h-comp1", componentName + 1,
							workspaceName + "_lrSS"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #10 - valid ids
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
				new NullProgressMonitor());
		loadRules = buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
				System.out, null, Locale.getDefault());
		AssertUtil.assertTrue("Load rules not as expected", loadRules.size() == 1);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #11 - non-existent-componentId in repo
		String tempComponentId = UUID.generate().getUuidValue();
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, tempComponentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(Messages.get(Locale.getDefault()).RepositoryConnection_component_with_id_not_found(tempComponentId),
					e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #12 - non-existent-componentId in ws
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, addedComponentId + "/" + loadRuleItemId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_component_with_id_not_found_snapshot(addedComponentId,
							workspaceName + "_lrSS"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #13 - non-existent-fileId in repo
		String tempFileId = UUID.generate().getUuidValue();
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentId + "/" + tempFileId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_file_with_id_not_found_snapshot(tempFileId, componentName + 1,
							workspaceName + "_lrSS"), e.getMessage());
		}

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// #14 - folder id
		String folderItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_TEST_FOLDER_ITEM_ID);
		try {
			buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
					Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, null, componentId + "/" + folderItemId, listener, Locale.getDefault(),
					new NullProgressMonitor());
			AssertUtil.fail("Exception expected");
		} catch (RTCConfigurationException e) {
			AssertUtil.assertEquals(
					Messages.get(Locale.getDefault()).RepositoryConnection_load_rule_with_id_not_a_file_snapshot(folderItemId, componentName + 1,
							workspaceName + "_lrSS"), e.getMessage());
		}

		// validate that isLoadPolicySet, isLoadPolicySetToUseLoadRules, isComponentLoadConfigSetToExcludeComponents,
		// createFoldersForComponents, components, and componentLoadRules are set to appropriate values depending on the
		// value of loadPolicy and componentLoadConfig

		// #15 set createFoldersForComponents to false, do not set componentsToExclude and componentLoadRules, do not
		// set loadPolicy and componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration", null, null, false, null, null,
				listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #16 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #17 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, null, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID),
				componentId + "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #18 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to loadAllComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #19 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to loadAllComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be false", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #20 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertTrue("createFoldersForComponents should be true", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #21 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #22 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, true, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId
						+ "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #23 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and do not set componentLoadConfig
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_LOAD_RULES, null, false, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId
						+ "/" + loadRuleItemId, listener, Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertFalse("isComponentLoadConfigSetToExcludeSomeComponents should be false",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #24 set createFoldersForComponents to true, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_LOAD_RULES, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, true,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #25 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// set loadPolicy to useLoadRules and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_LOAD_RULES, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should be empty", buildConfiguration.getComponents().size() == 0);
		AssertUtil.assertTrue(
				"loadRules should not be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 1);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertTrue("isLoadPolicySetToUseLoadRules should be true", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertFalse("isLoadPolicySetToUseComponentLoadConfig should be false",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
		
		// #26 set createFoldersForComponents to false, set componentsToExclude and componentLoadRules,
		// do not set loadPolicy and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				null, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 1);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true",
				buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #27 set createFoldersForComponents to false, set multiple component names for componentsToExclude and
		// set componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// add a space before the componentName to make sure that we trim the value
		buildConfiguration.initialize(baselineSet, contributor, "HJP", "Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, false,
				componentName + "1, " + componentName + "2", componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 2);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());

		// #28 set createFoldersForComponents to false, set multiple component ids for componentsToExclude and
		// set componentLoadRules,
		// set loadPolicy to useComponentLoadConfig and set componentLoadConfig to excludeSomeComponents
		buildConfiguration = new BuildConfiguration(repo, hjPath);

		buildConfiguration = new BuildConfiguration(repo, hjPath);
		// add a space before the componentName to make sure that we trim the value
		buildConfiguration.initialize(
				baselineSet,
				contributor,
				"HJP",
				"Test load rules for snapshot configuration",
				Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
				Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS,
				false,
				artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID) + ","
						+ artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT2_ITEM_ID), componentId + "/" + loadRuleItemId, listener,
				Locale.getDefault(), new NullProgressMonitor());
		AssertUtil.assertFalse("createFoldersForComponents should be fale", buildConfiguration.createFoldersForComponents());
		AssertUtil.assertTrue("componentsToExclude should not be empty", buildConfiguration.getComponents().size() == 2);
		AssertUtil.assertTrue(
				"loadRules should be empty", buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
								System.out, null, Locale.getDefault()).size() == 0);
		AssertUtil.assertTrue("for non-buildDefinition configuration isLoadPolicySet should be always be true irrespective of whether loadPolicy is specified",
				buildConfiguration.isLoadPolicySet());
		AssertUtil.assertFalse("isLoadPolicySetToUseLoadRules should be false", buildConfiguration.isLoadPolicySetToUseLoadRules());
		AssertUtil.assertTrue("isLoadPolicySetToUseComponentLoadConfig should be true", buildConfiguration.isLoadPolicySetToUseComponentLoadConfig());
		AssertUtil.assertTrue("isComponentLoadConfigSetToExcludeSomeComponents should be true",
				buildConfiguration.isComponentLoadConfigSetToExcludeSomeComponents());
		AssertUtil.assertFalse("isBuildDefinitionConfiguration should be false", buildConfiguration.isBuildDefinitionConfiguration());
		AssertUtil.assertTrue("isSnapshotLoad should be true", buildConfiguration.isSnapshotLoad());
		AssertUtil.assertFalse("isStreamLoad should be false", buildConfiguration.isStreamLoad());
	}

	public Map<String, String> setupBuildDefinition_toTestIncrementalUpdate(String workspaceName, String componentName, String buildDefinitionId,
			String hjPath, String buildPath, boolean shouldCreateFoldersForComponents, String loadPolicy, String componentLoadConfig,
			boolean isPersonalBuild, String loadMethod) throws Exception {
		// create a build definition
		// load directory "."
		// delete directory before loading (dir has other stuff that will be deleted)
		// accept changes before loading
		// add and set team.scm.loadPolicy and team.scm.componentLoadConfig properties
		// select create folders for components option depending on the value of shouldCreateFoldersForComponents
		// select doIncrementalUpdate option depending on the value of shouldDoIncrementalUpdate

		// create a build engine
		// create a build request

		// verify that the buildConfiguration returns the load rules
		// and other settings.
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Map<String, String> artifactIds = new HashMap<String, String>();

		try {
			IWorkspaceConnection buildWorkspace = setupWorkspace_toTestLoadPolicy(workspaceName, componentName, repo, artifactIds, false);
			// create the build definition
			BuildUtil.createBuildDefinition(repo, buildDefinitionId, true, artifactIds, null, IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID,
					buildWorkspace.getContextHandle().getItemId().getUuidValue(), IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, shouldCreateFoldersForComponents ? "true" : "false",
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS,
					new LoadComponents(Collections.<IComponentHandle> emptyList()).getBuildProperty(), Constants.PROPERTY_LOAD_METHOD,
					loadMethod);
			if (loadPolicy != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_LOAD_POLICY, loadPolicy);
			}
			if (componentLoadConfig != null) {
				BuildUtil.addPropertyToBuildDefiniion(repo, buildDefinitionId, Constants.PROPERTY_COMPONENT_LOAD_CONFIG, componentLoadConfig);
			}

			if (isPersonalBuild) {
				Exception[] failure = new Exception[] { null };
				IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
				IBuildResultHandle buildResultHandle = createPersonalBuildResult(
						buildDefinitionId,
						(IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(
								UUID.valueOf(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID)), null), "my buildLabel", listener);
				artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultHandle.getItemId().getUuidValue());
				if (failure[0] != null) {
					throw failure[0];
				}
			} else {
				BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel", artifactIds);
			}
			return artifactIds;
		} catch (Exception e) {
			// cleanup artifacts created
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	public void testBuildDefinitionConfig_doIncrementalUpdate(String workspaceName, String buildDefinitionId, String hjPath, String buildPath,
			Map<String, String> artifactIds, boolean shouldCreateFoldersForComponents, String loadPolicy, String componentLoadConfig,
			boolean isPersonalBuild, String loadMethod) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);

		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, false, "builddef_my buildLabel", false, listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertTrue("Personal build property is not as expected", buildConfiguration.isPersonalBuild() == isPersonalBuild);
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(
				(isPersonalBuild ? artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID) : artifactIds
						.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID)), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue("Should be accepting before fetching", buildConfiguration.acceptBeforeFetch());
		AssertUtil.assertFalse("Should be a list of components to exclude", buildConfiguration.includeComponents());
		AssertUtil
				.assertTrue(
						"Create folders for components is not as expected",
						shouldCreateFoldersForComponents
								&& (loadPolicy == null || loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)) ? buildConfiguration
								.createFoldersForComponents() == true : buildConfiguration.createFoldersForComponents() == false);
		AssertUtil.assertEquals(
				0,
				buildConfiguration.getComponentLoadRules(workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null,
						System.out, null, Locale.getDefault()).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(buildDefinitionId + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse("Deletion is not needed", buildConfiguration.isDeleteNeeded());
		AssertUtil
				.assertTrue(
						"isLoadPolicySetToUseLoadRules is not as expected ",
						loadPolicy != null && Constants.LOAD_POLICY_USE_LOAD_RULES.equals(loadPolicy) ? buildConfiguration
								.isLoadPolicySetToUseLoadRules() == true : buildConfiguration.isLoadPolicySetToUseLoadRules() == false);
		AssertUtil.assertTrue(
				"isLoadPolicySetToUseComponentLoadConfig is not as expected ",
				loadPolicy != null && Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy) ? buildConfiguration
						.isLoadPolicySetToUseComponentLoadConfig() == true : buildConfiguration.isLoadPolicySetToUseComponentLoadConfig() == false);
		AssertUtil.assertTrue("isLoadPolicySet is not as expected",
				loadPolicy != null ? buildConfiguration.isLoadPolicySet() == true : buildConfiguration.isLoadPolicySet() == false);
		AssertUtil
				.assertTrue(
						"isComponentLoadConfigSetToExcludeSomeComponents is not as expected",
						componentLoadConfig != null && componentLoadConfig.equals(Constants.COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS) ? buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == true : buildConfiguration
								.isComponentLoadConfigSetToExcludeSomeComponents() == false);
		AssertUtil.assertEquals(loadMethod, buildConfiguration.getLoadMethod());
		AssertUtil.assertTrue("isBuildDefinitionConfiguration should be true", buildConfiguration.isBuildDefinitionConfiguration());
	}

	public Map<String, String> setUpBuildDefinition_incrementalChanges(String buildDefinitionId, String workspaceItemId, String componentItemId,
			boolean isPersonalBuild, String folderName, String fileName, IProgressMonitor monitor) throws Exception {
		IWorkspaceHandle wsHandle = (IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(workspaceItemId), null);
		IWorkspaceConnection wsConnection = SCMPlatform.getWorkspaceManager(connection.getTeamRepository()).getWorkspaceConnection(wsHandle, monitor);
		IComponentHandle compHandle = (IComponentHandle)IComponent.ITEM_TYPE.createItemHandle(UUID.valueOf(componentItemId), null);
		IChangeSetHandle cs = wsConnection.createChangeSet(compHandle, null);
		IComponent component = (IComponent)connection.getTeamRepository().itemManager().fetchCompleteItem(compHandle, IItemManager.DEFAULT, null);
		// add new files and folders, that are expected to be loaded by IncrementalUpdateOperation in subsequent builds.
		SCMUtil.addVersionables(wsConnection, component, cs, new HashMap<String, IItemHandle>(), new String[] { "/" + folderName + "/",
				"/" + folderName + "/" + fileName }, null, "setupWorkspace_toTestLoadPolicy");
		
		Map<String, String> artifactIds = new HashMap<String, String>();
		Exception[] failure = new Exception[] { null };
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		if (isPersonalBuild) {
			IBuildResultHandle buildResultHandle = createPersonalBuildResult(buildDefinitionId, wsConnection.getResolvedWorkspace(),
					"my-personal-build-label-for-incremental-changes", listener);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, buildResultHandle.getItemId().getUuidValue());
			if (failure[0] != null) {
				throw failure[0];
			}
		} else {
			BuildUtil.createBuildResult(buildDefinitionId, connection, "my buildLabel for incremental changes", artifactIds);
		}
		wsConnection.closeChangeSets(Collections.singletonList(cs), null);
		return artifactIds;
	}

	private IBuildResultHandle createPersonalBuildResult(String buildDefinitionId, IWorkspaceHandle personalBuildWorkspace, String buildLabel,
			IConsoleOutput listener) throws TeamRepositoryException, RTCConfigurationException {

		ITeamRepository repo = connection.getTeamRepository();
		BuildConnection buildConnection = new BuildConnection(repo);
		IBuildDefinition buildDefinition = buildConnection.getBuildDefinition(buildDefinitionId, null);
		if (buildDefinition == null) {
			throw new RTCConfigurationException(Messages.getDefault().BuildConnection_build_definition_not_found(buildDefinitionId));
		}
		List<IBuildProperty> modifiedProperties = new ArrayList<IBuildProperty>();
		IBuildProperty originalProperty = buildDefinition.getProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID);
		if (originalProperty != null && !originalProperty.getValue().equals(personalBuildWorkspace.getItemId().getUuidValue())) {
			modifiedProperties.add(BuildItemFactory.createBuildProperty(IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, personalBuildWorkspace
					.getItemId().getUuidValue()));
		} else {
			AssertUtil.fail("Should override build workspace");
		}

		IBuildEngineHandle buildEngine = buildConnection.getBuildEngine(buildDefinition, null);
		if (buildEngine == null) {
			throw new RTCConfigurationException(Messages.getDefault().BuildConnection_no_build_engine_for_defn(buildDefinitionId));
		}

		IBuildRequestParams params = BuildItemFactory.createBuildRequestParams();
		params.setBuildDefinition(buildDefinition);
		params.getNewOrModifiedBuildProperties().addAll(modifiedProperties);
		params.setAllowDuplicateRequests(true);
		params.setPersonalBuild(true);
		params.getPotentialHandlers().add(buildEngine);
		params.setStartBuild(true);
		IBuildRequest buildRequest = ClientFactory.getTeamBuildRequestClient(repo).requestBuild(params, new NullProgressMonitor());
		IBuildResultHandle buildResultHandle = buildRequest.getBuildResult();

		if (buildLabel != null) {
			IBuildResult buildResult = (IBuildResult)repo.itemManager().fetchPartialItem(buildResultHandle, IItemManager.REFRESH,
					Arrays.asList(IBuildResult.PROPERTIES_VIEW_ITEM), new NullProgressMonitor());

			buildResult = (IBuildResult)buildResult.getWorkingCopy();
			buildResult.setLabel(buildLabel);
			ClientFactory.getTeamBuildClient(repo).save(buildResult, new NullProgressMonitor());
		}

		return buildResultHandle;
	}

}
