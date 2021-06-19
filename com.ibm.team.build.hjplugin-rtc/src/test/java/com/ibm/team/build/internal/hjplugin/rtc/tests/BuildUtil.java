/*******************************************************************************
 * Copyright © 2013, 2021 IBM Corporation and others.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.client.ITeamBuildRequestClient;
import com.ibm.team.build.common.BuildItemFactory;
import com.ibm.team.build.common.TeamBuildDuplicateItemException;
import com.ibm.team.build.common.builddefinition.AutoDeliverTriggerPolicy;
import com.ibm.team.build.common.builddefinition.IAutoDeliverConfigurationElement;
import com.ibm.team.build.common.model.BuildPhase;
import com.ibm.team.build.common.model.BuildStatus;
import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IBuildDefinition;
import com.ibm.team.build.common.model.IBuildDefinitionHandle;
import com.ibm.team.build.common.model.IBuildEngine;
import com.ibm.team.build.common.model.IBuildEngineHandle;
import com.ibm.team.build.common.model.IBuildProperty;
import com.ibm.team.build.common.model.IBuildResult;
import com.ibm.team.build.common.model.IBuildResultContribution;
import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.common.model.query.IBaseBuildResultQueryModel.IBuildResultQueryModel;
import com.ibm.team.build.internal.common.builddefinition.IJazzScmConfigurationElement;
import com.ibm.team.build.internal.common.ds.Pair;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.publishing.ArtifactFilePublisher;
import com.ibm.team.build.internal.publishing.LogPublisher;
import com.ibm.team.filesystem.client.FileUploadHandler;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContent;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.query.IItemQuery;
import com.ibm.team.repository.common.query.IItemQueryPage;
import com.ibm.team.repository.common.query.ast.IItemHandleInputArg;
import com.ibm.team.repository.common.query.ast.IPredicate;
import com.ibm.team.repository.common.service.IQueryService;

public class BuildUtil {
	
	public static IBuildDefinition createBuildDefinition(ITeamRepository repo, String id, 
			String buildDefinitionElementID,
			IProcessArea processArea, String... scmProperties) throws Exception {
		
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

		if (buildDefinitionElementID == null) {
			buildDefinitionElementID = BuildConnection.HJ_ELEMENT_ID;
		}
		IBuildConfigurationElement configurationElement = BuildItemFactory.createBuildConfigurationElement();
		configurationElement.setName(id);
		configurationElement.setElementId(buildDefinitionElementID);
        buildDefinition.getConfigurationElements().add(configurationElement);
        
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
	
	private static IQueryService getQueryService(ITeamRepository repo) {
        return (IQueryService) repo.getClientLibrary(IQueryService.class);
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
    		IBuildDefinitionHandle buildDefinition, String buildEngineElementId, boolean isActive, String... extraProperties) throws TeamRepositoryException {
        IBuildEngine engine = BuildItemFactory.createBuildEngine(processArea);
        engine.setId(id);
        engine.setActive(isActive);
        if (null != buildDefinition) {
            engine.getSupportedBuildDefinitions().add(buildDefinition);
        }
        // No registered extensions for Hudson/Jenkins so manually create one
        IBuildConfigurationElement hjConfigurationElement = BuildItemFactory.createBuildConfigurationElement();
        if (buildEngineElementId == null) {
        	buildEngineElementId = BuildConnection.HJ_ELEMENT_ID;
        }
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

	/**
	 * Create a build definition with given ID. This method also supports creating an engine based on 
	 * <code>createBuildEngine</code>. This also sets up Jazz Source Control configuration element 
	 * in the build definition.
	 * 
	 * @param repo                  The repository in which build definition should be created
	 * @param buildDefinitionId     The id of the build definition to be created
	 * @param createBuildEngine     Whether to create a build engine and add it as a  
	 *                              supporting engine to the build definition
	 * @param artifactIds           A map in which  item IDs of all artifacts created 
	 *                              will be added to by this method
	 * @param buildProperties       Generic properties to be added to the build definition.
	 * @param scmProperties         SCM related generic properties to be added to the build definition.
	 * @throws Exception
	 */ 
	public static void createBuildDefinition(ITeamRepository repo, String buildDefinitionId,
			String buildDefinitionElementId,
			boolean createBuildEngine,
			String buildEngineElementId,
			Map<String, String> artifactIds, 
			Map<String, String> buildProperties,
			String... scmProperties) throws Exception {
		createBuildDefinition(repo, buildDefinitionId, buildDefinitionElementId,
				createBuildEngine, buildEngineElementId, null, artifactIds, buildProperties, scmProperties);
	}

	/**
	 * Create a build definition with given ID. This method also supports creating an engine based on 
	 * <code>createBuildEngine</code>. This also sets up Jazz Source Control configuration element 
	 * in the build definition.
	 * 
	 * @param repo               The repository in which build definition should be created
	 * @param buildDefinitionId  The id of the build definition to be created
	 * @param createBuildEngine  Whether to create a build engine and add it as a  
	 *                           supporting engine to the build definition
	 * @param projectAreaName    The name of the project area which should be the owner of the 
	 *                           build definition
	 * @param artifactIds        A map in which  item IDs of all artifacts created 
	 *                           will be added to by this method.
	 * @param buildProperties    Generic properties to be added to the build definition.
	 * @param scmProperties      SCM related generic properties to be added to the build definition.
	 * @throws Exception
	 */
	public static void createBuildDefinition(ITeamRepository repo, String buildDefinitionId,
			String buildDefinitionElementId,
			boolean createBuildEngine,
			String buildEngineElementId,
			String projectAreaName,
			Map<String, String> artifactIds, 
			Map<String, String> buildProperties,
			String... scmProperties) throws Exception {
		IProcessArea processArea  = null;
		if (projectAreaName == null) {
			processArea = ProcessUtil.getDefaultProjectArea(repo);
		} else {
			processArea = ProcessUtil.getProjectArea(repo, projectAreaName);
		}
		IBuildDefinition buildDefinition = BuildUtil.createBuildDefinition(repo, buildDefinitionId,
				buildDefinitionElementId,
				processArea, scmProperties);
		if (buildProperties != null && buildProperties.size() > 0) {
			// Apply all the properties and save the build definition again
			buildDefinition = (IBuildDefinition) buildDefinition.getWorkingCopy();
			for (Entry<String,String> property : buildProperties.entrySet()) {
				buildDefinition.getProperties().add(BuildItemFactory.createBuildProperty
						(property.getKey(), property.getValue())); 
			}
			buildDefinition = getTeamBuildClient(repo).save(buildDefinition, getNullProgressMonitor());
		}
		if (createBuildEngine) {
			IBuildEngine buildEngine = BuildUtil.createBuildEngine(repo, buildDefinitionId, 
					processArea, buildDefinition, buildEngineElementId, true);
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID, buildEngine.getItemId().getUuidValue());
			artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ID, buildEngine.getId());
		}
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID, buildDefinition.getItemId().getUuidValue());
		artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ID, buildDefinition.getId());

	}

	/**
	 * Add a configuration element for post build deliver to the build definition and return it 
	 * Few generic properties are added to the build definition
	 * 
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

	/**
	 * Delete the given build artifacts.
	 * 
	 * @param repo          The repository in which build definition should be created
	 * @param artifactIds   The map which contains item ids of several build artifacts. For a list of 
	 *                      artifacts to be deleted, see the implementation
	 * @throws Exception
	 */
	public static void deleteBuildArtifacts(ITeamRepository repo, Map<String, String> artifactIds) throws Exception {
		BuildUtil.deleteBuildResult(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID));
		for (int i = 1; i < 10; i++) {
			BuildUtil.deleteBuildResult(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID + i));
		}
		BuildUtil.deleteBuildDefinition(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_DEFINITION_ITEM_ID));
		BuildUtil.deleteBuildEngine(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_BUILD_ENGINE_ITEM_ID));
	}

	/**
	 * Delete the build definition given build definition itemId. If a build definition has 
	 * some build results, then all those build results will be deleted before 
	 * deleting the build definition,
	 * 
	 * @param repo                    The repository in which build definition should be created
	 * @param buildDefinitionItemId   The item ID of the build definition
	 * @throws TeamRepositoryException 
	 * @throws IllegalArgumentException
	 */
	public static void deleteBuildDefinition(ITeamRepository repo, String buildDefinitionItemId) 
							throws TeamRepositoryException, IllegalArgumentException {
		if (buildDefinitionItemId != null) {
			ITeamBuildClient buildClient = getTeamBuildClient(repo);
			ITeamBuildRequestClient buildRequestClient = (ITeamBuildRequestClient) 
							repo.getClientLibrary(ITeamBuildRequestClient.class);
			UUID buildDefinitionUuid = UUID.valueOf(buildDefinitionItemId);
			IBuildDefinitionHandle buildDefinitionHandle = (IBuildDefinitionHandle) 
							IBuildDefinition.ITEM_TYPE.createItemHandle(buildDefinitionUuid, null);
			// Enumerate all build results  and delete them
			IBuildResultQueryModel m = IBuildResultQueryModel.ROOT;
	        IItemQuery query = IItemQuery.FACTORY.newInstance(m);
			IPredicate p = m.buildDefinition()._eq(query.newItemHandleArg());
			query = (IItemQuery) query.filter(p);
			IItemQueryPage qp = buildRequestClient.queryItems(query, new Object [] {buildDefinitionHandle},
					  					512, new NullProgressMonitor());
	        if (qp.getSize() > 0) {
	            IItemHandle [] buildResultHandles = qp.handlesAsArray();
	            for (IItemHandle buildResultHandle : buildResultHandles) {
	            	buildClient.delete((IBuildResultHandle) buildResultHandle, new NullProgressMonitor());
	            }
	        }
			buildClient.delete(buildDefinitionHandle, null);
		}
	}

	/**
	 * Delete the build result given build result item ID.
	 * 
	 * @param repo                       The repository in which build definition should be created
	 * @param buildResultItemId          The item ID of the build result.
	 * @throws IllegalArgumentException
	 * @throws TeamRepositoryException
	 */
	public static void deleteBuildResult(ITeamRepository repo, String buildResultItemId) 
								throws IllegalArgumentException, TeamRepositoryException {
		if (buildResultItemId != null) {
			ITeamBuildClient buildClient = getTeamBuildClient(repo);
			UUID buildResultUuid = UUID.valueOf(buildResultItemId);
			IBuildResultHandle buildResultHandle = (IBuildResultHandle) 
									IBuildResult.ITEM_TYPE.createItemHandle(buildResultUuid, null);
			try {
				buildClient.delete(buildResultHandle, null);
			} catch (ItemNotFoundException e) {
				// already deleted - that's ok
			}
		}
	}

	/**
	 * Delete the build result given build engine item ID.
	 * 
	 * @param repo                       The repository in which build definition should be created
	 * @param buildEngineItemId          The item ID of the build engine.
	 * @throws IllegalArgumentException
	 * @throws TeamRepositoryException
	 */
	public static void deleteBuildEngine(ITeamRepository repo, String buildEngineItemId) 
								throws TeamRepositoryException, IllegalArgumentException {
		if (buildEngineItemId != null) {
			ITeamBuildClient buildClient = getTeamBuildClient(repo);
			UUID buildEngineUuid = UUID.valueOf(buildEngineItemId);
			IBuildEngineHandle buildEngineHandle = (IBuildEngineHandle) 
									IBuildEngine.ITEM_TYPE.createItemHandle(buildEngineUuid, null);
			buildClient.delete(buildEngineHandle, null);
		}
	}
	
	/**
	 * Create a build result for the given build definition.
	 * 
	 * @param buildDefinitionId     The build definition id.
	 * @param connection            The repository in which the build definition exists.
	 * @param buildLabel            Label for the build result
	 * @param artifactIds           A map in which  item IDs of all artifacts created 
	 *                              will be added to by this method.
	 * @return
	 * @throws Exception
	 */
	public static IBuildResultHandle createBuildResult(String buildDefinitionId, 
				RepositoryConnection connection, String buildLabel, 
				Map<String,String> artifactIds) throws Exception {
		 ConsoleOutputHelper listener = new ConsoleOutputHelper();
		 String itemId = connection.createBuildResult(buildDefinitionId, null, 
				 	buildLabel, listener, null, Locale.getDefault());
		 if (listener.getFailure() != null) {
			 throw listener.getFailure();
		 }
		 artifactIds.put(TestSetupTearDownUtil.ARTIFACT_BUILD_RESULT_ITEM_ID, itemId);
		 IBuildResultHandle br = (IBuildResultHandle) IBuildResult.ITEM_TYPE.
				 			createItemHandle(UUID.valueOf(itemId), null);
		 return br;
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

	/**
	 * This sets up a build result with log and artifact contributions. The filename, label( description), component name 
	 * and file location details into this method. To make this flexible, enter these details into a json or yaml 
	 * file and provide it to this method for parsing.
	 *  
	 * @param connection            The repository connection
	 * @param buildDefinitionId     The id of the build definition
	 * @param scratchFolder         A folder to temporarily store the files being copied
	 * @throws Exception            If something goes wrong while uploading contributions to the build result.
	 */
	public static Map<String, String> setupBuildResultWithLogsAndArtifacts(RepositoryConnection connection, String buildDefinitionId,
										String scratchFolder) throws Exception {
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		connection.ensureLoggedIn(getNullProgressMonitor());
		createBuildDefinition(connection.getTeamRepository(),
				buildDefinitionId, null, true, null, null, setupArtifacts, null, (String []) null);
		IBuildResultHandle brHandle = createBuildResult(buildDefinitionId, connection, "test", setupArtifacts);
		
		fileUploadHelper(connection, brHandle.getItemId().getUuidValue(), 
				scratchFolder,
				IBuildResultContribution.ARTIFACT_EXTENDED_CONTRIBUTION_ID, 
				ArtifactLogResourceContainer.artifactInfo);
		
		fileUploadHelper(connection, brHandle.getItemId().getUuidValue(),
				scratchFolder,
				IBuildResultContribution.LOG_EXTENDED_CONTRIBUTION_ID,
				ArtifactLogResourceContainer.logInfo);
		
		// Complete the build result
		ITeamBuildRequestClient client = (ITeamBuildRequestClient) connection.getTeamRepository().
								getClientLibrary(ITeamBuildRequestClient.class);
		client.makeBuildComplete(brHandle, false, null, getNullProgressMonitor());
		return setupArtifacts;
	}
	
	private static void fileUploadHelper(RepositoryConnection connection, String buildResultItemId,
			String scratchFolder, String contributionType, 
			Map<String, String[][]> fileInfos) throws TeamRepositoryException, IOException {
		
		// Upload content into specific components
		for (String componentName : fileInfos.keySet()) {
			String [][] artifactInfoEntries = fileInfos.get(componentName);
			for (String [] artifactInfoEntry : artifactInfoEntries) {
				File f = null;
				try {
					// artifactInfoEntry[0] - filename on disk, present in the same package as this class
					// artifactInfoEntry[1] - label (or) description
					String fileName = artifactInfoEntry[0];
					String label = artifactInfoEntry[1];
					String suggestedFileName = artifactInfoEntry[2];
					InputStream s = BuildUtil.class.getResourceAsStream(fileName);
					(f = new File(scratchFolder, suggestedFileName)).createNewFile();
					try (FileOutputStream of = new FileOutputStream(f)){
						int x;
						while((x = s.read()) != -1) {
							of.write(x);
						}
					}
					if (IBuildResultContribution.LOG_EXTENDED_CONTRIBUTION_ID.equals(contributionType)) {
						LogPublisher lp = new LogPublisher(f.getAbsolutePath(), label, 
									IContent.CONTENT_TYPE_TEXT,  IContent.ENCODING_UTF_8);
						if (!componentName.isEmpty()) {
							lp.setComponentName(componentName);
						}
						lp.publish((IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle
								(UUID.valueOf(buildResultItemId), null), 
								BuildStatus.OK, connection.getTeamRepository());
					} else {
						ArtifactFilePublisher ap = new ArtifactFilePublisher(f.getAbsolutePath(), 
								label, IContent.CONTENT_TYPE_TEXT,  IContent.ENCODING_UTF_8);
						if (!componentName.isEmpty()) {
							ap.setComponentName(componentName);
						}
						ap.publish((IBuildResultHandle)IBuildResult.ITEM_TYPE.createItemHandle
								(UUID.valueOf(buildResultItemId), null), 
								BuildStatus.OK, connection.getTeamRepository());	
					}
				} finally {
					if (f != null) {
						f.delete();
					}
				}
			}
		}
	}
	
	
	private static IProgressMonitor getNullProgressMonitor() {
		return new NullProgressMonitor();
	}

	public static void deActivateEngine(String buildEngineItemId, IProgressMonitor progress, ITeamRepository repository)
			throws TeamRepositoryException, TeamBuildDuplicateItemException {
		// Get hold of the build engine
		IBuildEngine buildEngine = (IBuildEngine) repository.itemManager().fetchCompleteItem(
				IBuildResult.ITEM_TYPE.createItemHandle(UUID.valueOf(buildEngineItemId), null),
				IItemManager.REFRESH, progress).getWorkingCopy();
		buildEngine.setActive(false);
		ITeamBuildClient bcl = (ITeamBuildClient) repository.getClientLibrary(ITeamBuildClient.class);
		bcl.save(buildEngine, progress);
	}
}
