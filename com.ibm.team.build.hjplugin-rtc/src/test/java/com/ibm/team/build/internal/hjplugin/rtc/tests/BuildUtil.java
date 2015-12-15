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

import java.util.Map;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.model.BuildPhase;
import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildDefinitionHandle;
import com.ibm.team.build.common.model.IBuildEngine;
import com.ibm.team.build.common.model.IBuildEngineHandle;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.common.model.BuildFactory;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;

public class BuildUtil {
	
	public static IBuildDefinition createBuildDefinition(ITeamRepository repo, String id, IProcessArea processArea, String... scmProperties) throws Exception {
		
		IBuildDefinition buildDefinition = makeBuildDefinition(id, processArea);
		
		if (scmProperties != null) {
			

	        IBuildConfigurationElement scmElement = BuildItemFactory.createBuildConfigurationElement();
	        scmElement.setName(id);
	        scmElement.setElementId(IJazzScmConfigurationElement.ELEMENT_ID);
	        scmElement.setBuildPhase(IJazzScmConfigurationElement.BUILD_PHASE);
	        buildDefinition.getConfigurationElements().add(scmElement);

			for (int i = 0; i < scmProperties.length; i += 2) {
	            IBuildProperty property = BuildItemFactory.createBuildProperty();
	            property.setName(scmProperties[i]);
	            property.setValue(scmProperties[i+1]);
	            buildDefinition.getProperties().add(property);
			}
		}

		IBuildConfigurationElement hudsonJenkinsElement = BuildItemFactory.createBuildConfigurationElement();
		hudsonJenkinsElement.setName(id);
		hudsonJenkinsElement.setElementId(BuildConnection.HJ_ELEMENT_ID);
        buildDefinition.getConfigurationElements().add(hudsonJenkinsElement);
        
		ITeamBuildClient buildClient = getTeamBuildClient(repo);
		return buildClient.save(buildDefinition, null);
	}

	private static ITeamBuildClient getTeamBuildClient(ITeamRepository repo) {
        return ClientFactory.getTeamBuildClient(repo);
    }

    /**
     * Create a build definition with the given id and process area.
     * 
     * @param id
     *            the id of the build definition
     * @param processArea
     *            the process area to associate the build definition with
     * @return A new <code>IBuildDefinition</code>. The build definition has
     *         not been saved.
     */
    public static IBuildDefinition makeBuildDefinition(String id, IProcessArea processArea) {
        IBuildDefinition buildDefinition = BuildItemFactory.createBuildDefinition(processArea);
        buildDefinition.setId(id);

        return buildDefinition;
    }

    /**
     * Create a build engine with the given id and process area.
     * 
     * @param id
     *            the id of the build engine
     * @param processArea
     *            the process area to associate the build engine with
     * @return a new <code>IBuildEngine</code>
     */
    public static IBuildEngine createBuildEngine(ITeamRepository repo, String id, IProcessArea processArea,
    		IBuildDefinitionHandle buildDefinition, boolean isActive, String... extraProperties) throws TeamRepositoryException {
        IBuildEngine engine = BuildItemFactory.createBuildEngine(processArea);
        engine.setId(id);
        engine.setActive(isActive);
        if (null != buildDefinition) {
            engine.getSupportedBuildDefinitions().add(buildDefinition);
        }
        // No registered extensions for Hudson/Jenkins so manually create one
        IBuildConfigurationElement hjConfigurationElement = BuildItemFactory.createBuildConfigurationElement();
        hjConfigurationElement.setElementId(BuildConnection.HJ_ENGINE_ELEMENT_ID);
        hjConfigurationElement.setName("H/J config element for JUnit testing");
        hjConfigurationElement.setBuildPhase(BuildPhase.UNSPECIFIED);
        hjConfigurationElement.setDescription("H/J config element for JUnit testing");
		engine.initializeConfiguration(hjConfigurationElement);
		initializeFromProperties(engine, extraProperties);

		engine = getTeamBuildClient(repo).save(engine, null);
        return engine;
    }

	private static void initializeFromProperties(IBuildEngine hudsonInfo, String... extraProperties) {
		hudsonInfo.setConfigurationProperty(BuildConnection.HJ_ENGINE_ELEMENT_ID, BuildConnection.PROPERTY_HUDSON_URL, "http://junit.ottawa.ibm.com:8081");
		if (extraProperties != null) {
			for (int i = 0; i < extraProperties.length; i += 2) {
				hudsonInfo.setConfigurationProperty(BuildConnection.HJ_ENGINE_ELEMENT_ID, extraProperties[i], extraProperties[i+1]);
			}
		}
	}

	public static void createBuildDefinition(ITeamRepository repo, String buildDefinitionId, boolean createBuildEngine,
			Map<String, String> artifactIds, String... scmProperties) throws Exception {
		IProcessArea processArea = ProcessUtil.getDefaultProjectArea(repo);
		IBuildDefinition buildDefinition = BuildUtil.createBuildDefinition(repo, buildDefinitionId, processArea, scmProperties);
		if (createBuildEngine) {
			IBuildEngine buildEnine = BuildUtil.createBuildEngine(repo, buildDefinitionId, processArea, buildDefinition, true);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID, buildEnine.getItemId().getUuidValue());
		}
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID, buildDefinition.getItemId().getUuidValue());
	}

	public static void deleteBuildArtifacts(ITeamRepository repo,
			Map<String, String> artifactIds) throws Exception {
		BuildUtil.deleteBuildResult(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID));
		BuildUtil.deleteBuildDefinition(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID));
		BuildUtil.deleteBuildEngine(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID));
	}

	public static void deleteBuildDefinition(ITeamRepository repo, String buildDefinitionItemId) throws TeamRepositoryException, IllegalArgumentException {
		if (buildDefinitionItemId != null) {
			ITeamBuildClient buildClient = getTeamBuildClient(repo);
			UUID buildDefinitionUuid = UUID.valueOf(buildDefinitionItemId);
			IBuildDefinitionHandle buildDefinitionHandle = (IBuildDefinitionHandle) IBuildDefinition.ITEM_TYPE.createItemHandle(buildDefinitionUuid, null);
			buildClient.delete(buildDefinitionHandle, null);
		}
	}

	public static void deleteBuildResult(ITeamRepository repo, String buildResultItemId) throws IllegalArgumentException, TeamRepositoryException {
		if (buildResultItemId != null) {
			ITeamBuildClient buildClient = getTeamBuildClient(repo);
			UUID buildResultUuid = UUID.valueOf(buildResultItemId);
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) IBuildResult.ITEM_TYPE.createItemHandle(buildResultUuid, null);
			try {
				buildClient.delete(buildResultHandle, null);
			} catch (ItemNotFoundException e) {
				// already deleted - that's ok
			}
		}
	}

	public static void deleteBuildEngine(ITeamRepository repo, String buildEngineItemId) throws TeamRepositoryException, IllegalArgumentException {
		if (buildEngineItemId != null) {
			ITeamBuildClient buildClient = getTeamBuildClient(repo);
			UUID buildEngineUuid = UUID.valueOf(buildEngineItemId);
			IBuildEngineHandle buildEngineHandle = (IBuildEngineHandle) IBuildEngine.ITEM_TYPE.createItemHandle(buildEngineUuid, null);
			buildClient.delete(buildEngineHandle, null);
		}
	}

}
