/*******************************************************************************
 * Copyright (c) 2013, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.builddefinition.AutoDeliverTriggerPolicy;
import com.ibm.team.build.common.builddefinition.IAutoDeliverConfigurationElement;
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
import com.ibm.team.build.internal.common.model.BuildProperty;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
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
	
	public static IBuildDefinition getBuildDefinition(ITeamRepository repo, String buildDefinitionId) 
								throws TeamRepositoryException {
		ITeamBuildClient buildClient = getTeamBuildClient(repo);
		return buildClient.getBuildDefinition(buildDefinitionId, null);
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

	/**
	 * Add a configuration element for post build deliver to the build definition and return it 
	 * Few generic properties are added to the build definition
	 * @param repo - the repository to work with
	 * @param buildDefinitionId - the build definition to which post build deliver configuration element is to be added
	 * @param artifactIds - a map in which new items that are created will be added to.
	 * @param configOrGenericProperties - a map which contains values for generic or configuration properties
	 * @param progress - if progress monitoring is required
	 * @return - a configuration element for post build deliver.
	 * @throws Exception - if there is any error in retrieving/saving the build definition
	 */
	@SuppressWarnings("unchecked")
	public static IBuildConfigurationElement setupPBDeliverConfigurationElement(ITeamRepository repo,
				String buildDefinitionId, Map<String,String> artifactIds,
				Map<String, String> configOrGenericProperties, IProgressMonitor progress) throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			IBuildDefinition buildDefinition = (IBuildDefinition) getTeamBuildClient(repo).getBuildDefinition(buildDefinitionId, monitor.newChild(20)).getWorkingCopy();
			IBuildConfigurationElement configElement = BuildItemFactory.createBuildConfigurationElement();
			configElement.setName(buildDefinitionId);
			configElement.setElementId(IAutoDeliverConfigurationElement.ELEMENT_ID);
			buildDefinition.initializeConfiguration(configElement);
			// Ordering is important. Initialize with the config element and then add generic properties.
			String deliverEnabled = getValueOrDefault(configOrGenericProperties, IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ENABLED, "true");
			String triggerPolicy = getValueOrDefault(configOrGenericProperties, IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY, AutoDeliverTriggerPolicy.NO_ERRORS.name());
			buildDefinition.getProperty(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_ENABLED).setValue(deliverEnabled);
			buildDefinition.getProperty(IAutoDeliverConfigurationElement.PROPERTY_DELIVER_TRIGGER_POLICY).setValue(triggerPolicy);
			if (configOrGenericProperties  != null) {
				for (String configProperty : configOrGenericProperties.keySet()) {
					IBuildProperty property = buildDefinition.getProperty(configProperty);
					if (property != null) {
						property.setValue(configOrGenericProperties.get(configProperty));
					} else {
						buildDefinition.getProperties().add(
								BuildItemFactory.createBuildProperty(configProperty, 
										configOrGenericProperties.get(configProperty)));
					}
				}
			}
			getTeamBuildClient(repo).save(buildDefinition, monitor.newChild(50));
			return configElement;
		} finally {
			monitor.done();
		}
	}

	public static void deleteBuildArtifacts(ITeamRepository repo, Map<String, String> artifactIds) throws Exception {
		BuildUtil.deleteBuildResult(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID));
		for (int i = 1; i < 10; i++) {
			BuildUtil.deleteBuildResult(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID + i));
		}
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
	
	public static String createBuildResult(String buildDefinitionId, RepositoryConnection connection, String buildLabel, Map<String,String> artifactIds) throws Exception {
		 ConsoleOutputHelper listener = new ConsoleOutputHelper();
		 String itemId = connection.createBuildResult(buildDefinitionId, null, buildLabel, listener, null, Locale.getDefault());
		 if (listener.getFailure() != null) {
			 throw listener.getFailure();
		 }
		 artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, itemId);
		 return itemId;
	}

	public static String getValueOrDefault(Map<String, String> configOrGenericProperties, String key, String defaultValue) {
		if (configOrGenericProperties == null) {
			return defaultValue;
		}
		if (configOrGenericProperties.containsKey(key)) {
			return configOrGenericProperties.get(key);
		}
		return defaultValue;
	}

	public static void removeConfigurationElement(ITeamRepository repo, String buildDefinitionId,
			String elementId, IProgressMonitor progress) throws Exception, TeamRepositoryException {
		// Once done, remove the JazzSCM configuration element from the build definition
		IBuildDefinition buildDefinition = (IBuildDefinition) getBuildDefinition(repo, buildDefinitionId).getWorkingCopy();
		int indexToRemove = -1;
		for (int i = 0 ; i < buildDefinition.getConfigurationElements().size(); i++) {
			IBuildConfigurationElement configElement = (IBuildConfigurationElement) (buildDefinition.getConfigurationElements().get(i));
			if (configElement.getElementId().equals(elementId)) {
				indexToRemove = i;
				break;
			}
		}
		if (indexToRemove != -1) {
			buildDefinition.getConfigurationElements().remove(indexToRemove);
		}
		ITeamBuildClient buildClient = getTeamBuildClient(repo);
		buildClient.save(buildDefinition, null);
	}
	
	public static void save(ITeamRepository repo, IBuildResult item) throws IllegalArgumentException, TeamRepositoryException  {
		ITeamBuildClient buildClient = getTeamBuildClient(repo);
		buildClient.save(item, null);
	}
	
	@SuppressWarnings("unchecked")
	public static void addPropertyToBuildDefiniion(ITeamRepository repo, String buildDefinitionId, String propertyName, String propertyValue) throws TeamRepositoryException {
		IBuildDefinition buildDefinition = (IBuildDefinition) getBuildDefinition(repo, buildDefinitionId).getWorkingCopy();
        IBuildProperty property = BuildItemFactory.createBuildProperty();
        property.setName(propertyName);
        property.setValue(propertyValue);
        buildDefinition.getProperties().add(property);
		ITeamBuildClient buildClient = getTeamBuildClient(repo);
		buildClient.save(buildDefinition, null);      
	}
}
