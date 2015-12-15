/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.AssertionFailedException;
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
import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;
import com.ibm.team.build.internal.hjplugin.rtc.Messages;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.build.internal.scm.ComponentLoadRules;
import com.ibm.team.build.internal.scm.LoadComponents;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspaceHandle;

@SuppressWarnings({ "nls", "restriction" })
public class BuildConfigurationTests {
	private RepositoryConnection connection;

	public BuildConfigurationTests(RepositoryConnection repositoryConnection) {
		this.connection = repositoryConnection;
	}

	public Map<String, String> setupComponentLoading(String workspaceName,
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
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
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

	public void testComponentLoading(String workspaceName,
			String testName, String hjPath, String buildPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse(buildConfiguration.isPersonalBuild(), "Should NOT be a personal build");
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue(buildConfiguration.acceptBeforeFetch(), "Should be accepting before fetching");
		AssertUtil.assertTrue(buildConfiguration.includeComponents(), "Should be a list of components to include");
		AssertUtil.assertTrue(buildConfiguration.createFoldersForComponents(), "Should be creating a folder for the component");
		AssertUtil.assertEquals(0, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null).size());
		AssertUtil.assertEquals(1, buildConfiguration.getComponents().size());
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID), buildConfiguration.getComponents().iterator().next().getItemId().getUuidValue());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse(buildConfiguration.isDeleteNeeded(), "Deletion should not be needed");
	}

	public Map<String, String> setupNewLoadRules(String workspaceName,
			String testName, String hjPath, String buildPath) throws Exception {
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
					new String[] {"/h/new.loadRule"}, 
					new String[] {getNewLoadRule(testName, "f")}); // load rule to load f directory
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
			});
			buildStream.closeChangeSets(Collections.singletonList(cs1), null);

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRule.getBuildPropertySetting(),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.EMPTY_LIST).getBuildProperty() );
			
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
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
			throw e;
		}
	}

	private String getNewLoadRule(String componentName, String folderName) {
		String loadRule = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<!--Built from the sandbox \"C:\\BuildPlugin3\" and the workspace \"JenkinsInitiation Development Workspace\"-->\n"
				+ "<!--Generated: 2013-04-02 14.49.08-->\n"
				+ "<scm:sourceControlLoadRule version=\"1\" xmlns:scm=\"http://com.ibm.team.scm\">\n"
				+ "    <parentLoadRule>\n"
				+ "        <component name=\"" + componentName + "\" />\n" 
				+ "        <parentFolder repositoryPath=\"/" + folderName + "\" />\n"
				+ "    </parentLoadRule>\n"
				+ "</scm:sourceControlLoadRule>\n";
		return loadRule;
	}

	public void testNewLoadRules(String workspaceName, String testName,
			String hjPath, String buildPath, Map<String, String> artifactIds) throws Exception {
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository(); 
		Exception[] failure = new Exception[] {null};
		IConsoleOutput listener = TestSetupTearDownUtil.getListener(failure);
		
		// get the build result
		String buildResultItemId = artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID);
		IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildResultItemId), null);

		BuildConfiguration buildConfiguration = new BuildConfiguration(repo, hjPath);
		buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse(buildConfiguration.isPersonalBuild(), "Should NOT be a personal build");
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue(buildConfiguration.acceptBeforeFetch(), "Should be accepting before fetching");
		AssertUtil.assertFalse(buildConfiguration.includeComponents(), "Should be a list of components to exclude");
		AssertUtil.assertFalse(buildConfiguration.createFoldersForComponents(), "Should not be creating a folder for the component");
		AssertUtil.assertEquals(1, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, buildPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertTrue(buildConfiguration.isDeleteNeeded(), "Deletion is needed");
	}

	public Map<String, String> setupOldLoadRules(String workspaceName,
			String testName, String hjPath) throws Exception {
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
					new String[] {"/h/new.loadRule"}, 
					new String[] {getOldLoadRule(testName, "f")}); // load rule to load f directory
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
			});
			buildStream.closeChangeSets(Collections.singletonList(cs1), null);

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "false",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRule.getBuildPropertySetting(),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.EMPTY_LIST).getBuildProperty() );
			
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
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID));
			SCMUtil.deleteWorkspace(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID));
			BuildUtil.deleteBuildArtifacts(repo, artifactIds);
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
		buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}

		AssertUtil.assertFalse(buildConfiguration.isPersonalBuild(), "Should NOT be a personal build");
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertFalse(buildConfiguration.acceptBeforeFetch(), "Should not be accepting before fetching");
		AssertUtil.assertFalse(buildConfiguration.includeComponents(), "Should be a list of components to exclude");
		AssertUtil.assertFalse(buildConfiguration.createFoldersForComponents(), "Should not be creating a folder for the component");
		AssertUtil.assertEquals(1, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse(buildConfiguration.isDeleteNeeded(), "Deletion is not needed");
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
					new String[] {getNewLoadRule(testName, "f"), getOldLoadRule(testName, "g")}); 
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
			});
			buildWorkspace.closeChangeSets(Collections.singletonList(cs1), null);

			// capture interesting uuids to verify against
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID, buildStream.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_WORKSPACE_ITEM_ID, buildWorkspace.getContextHandle().getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_COMPONENT1_ITEM_ID, component.getItemId().getUuidValue());
			
			// create the build definition
			BuildUtil.createBuildDefinition(repo, testName, true, artifactIds,
					IJazzScmConfigurationElement.PROPERTY_WORKSPACE_UUID, buildWorkspace.getContextHandle().getItemId().getUuidValue(),
					IJazzScmConfigurationElement.PROPERTY_FETCH_DESTINATION, buildPath,
					IJazzScmConfigurationElement.PROPERTY_ACCEPT_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_DELETE_DESTINATION_BEFORE_FETCH, "true",
					IJazzScmConfigurationElement.PROPERTY_CREATE_FOLDERS_FOR_COMPONENTS, "false",
					IJazzScmConfigurationElement.PROPERTY_COMPONENT_LOAD_RULES, loadRule.getBuildPropertySetting(),
					IJazzScmConfigurationElement.PROPERTY_LOAD_COMPONENTS, new LoadComponents(Collections.EMPTY_LIST).getBuildProperty(),
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
		buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
		if (failure[0] != null) {
			throw failure[0];
		}
		if (!propertiesLogged[0]) {
			AssertUtil.fail("Property substitutions not logged");
		}

		AssertUtil.assertTrue(buildConfiguration.isPersonalBuild(), "Should be a personal build");
		BuildWorkspaceDescriptor workspaceDescriptor = buildConfiguration.getBuildWorkspaceDescriptor();
		AssertUtil.assertEquals(artifactIds.get(TestSetupTearDownUtil.ARTIFACT_STREAM_ITEM_ID), workspaceDescriptor.getWorkspaceHandle().getItemId().getUuidValue());
		AssertUtil.assertEquals("my buildLabel", buildConfiguration.getBuildProperties().get("buildLabel"));
		AssertUtil.assertTrue(buildConfiguration.acceptBeforeFetch(), "Should be accepting before fetching");
		AssertUtil.assertFalse(buildConfiguration.includeComponents(), "Should be a list of components to exclude");
		AssertUtil.assertFalse(buildConfiguration.createFoldersForComponents(), "Should not be creating a folder for the component");
		AssertUtil.assertEquals(1, buildConfiguration.getComponentLoadRules(
				workspaceDescriptor.getConnection(connection.getRepositoryManager(), false, null), null).size());
		AssertUtil.assertEquals(0, buildConfiguration.getComponents().size());
		File expectedLoadDir = new File(hjPath);
		expectedLoadDir = new File(expectedLoadDir, "loadDir/here");
		AssertUtil.assertEquals(expectedLoadDir.getCanonicalPath(), buildConfiguration.getFetchDestinationFile().getCanonicalPath());
		AssertUtil.assertEquals(testName + "_builddef_my buildLabel", buildConfiguration.getSnapshotName());
		AssertUtil.assertFalse(buildConfiguration.isDeleteNeeded(), "Deletion is needed");
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
		buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
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
			buildConfiguration.initialize(buildResultHandle, "builddef_my buildLabel", listener, null, Locale.getDefault());
			if (failure[0] != null) {
				throw failure[0];
			}
			AssertUtil.fail("The relative fetch location should have been bad: " + buildPath);
		} catch (RTCConfigurationException e) {
			// good, the fetch location was bad
		}
	}
}
