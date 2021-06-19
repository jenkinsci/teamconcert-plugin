/*******************************************************************************
 * Copyright (c) 2013, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IDevelopmentLine;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProcessDefinition;
import com.ibm.team.process.common.IProcessDefinitionHandle;
import com.ibm.team.process.common.IProcessItem;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.process.common.ITeamAreaHierarchy;
import com.ibm.team.process.common.ProcessContentKeys;
import com.ibm.team.process.internal.common.service.IProcessService;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.TeamRepository;
import com.ibm.team.repository.common.IContent;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.workitem.client.IWorkItemClient;

/**
 * Utility class for managing process artifacts. Contains useful methods to create/delete project area and team area.
 * The code for project/team creation was originally implemented in workitem:259389.
 * 
 */
public class ProcessUtil {
	public static final String DEFAULT_PROCESS_AREA = "HJPluginTests"; //$NON-NLS-1$
	private static IProjectArea fProjectArea;

	@SuppressWarnings("unchecked")
	public static IProjectArea getProjectArea(ITeamRepository repo, String name) throws TeamRepositoryException {

		IProcessItemService processService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);
		List<IProjectArea> projectAreas = processService.findAllProjectAreas(IProcessClientService.ALL_PROPERTIES, null);
		for (IProjectArea area : projectAreas) {
			if (area.getName().equals(name)) {
				return area;
			}
		}
		return null;
	}

	public static IProjectArea getDefaultProjectArea(ITeamRepository repo) throws TeamRepositoryException {
		if (fProjectArea == null) {
			fProjectArea = getProjectArea(repo, DEFAULT_PROCESS_AREA);
		}
		return fProjectArea;
	}

	/**
	 * Helper method to setup the process area using a given process definition.
	 * 
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	public static IProcessArea getProcessArea(ITeamRepository repo, String name, IProcessDefinition processDefinition, boolean useStandaloneProject)
			throws Exception {

		IProjectArea projectArea = getProjectArea(repo, processDefinition, name);
		// append .team.area to the name of the project area
		ITeamArea teamArea = null;
		if (!useStandaloneProject) {
			teamArea = getTeamArea(repo, projectArea, name + ".team.area"); //$NON-NLS-1$
			IWorkItemClient workItemClient = (IWorkItemClient)repo.getClientLibrary(IWorkItemClient.class);
			workItemClient.setDefaultTeamArea(teamArea, new NullProgressMonitor());

			// If the team area was created...then the project area was
			// modified.
			projectArea = (IProjectArea)repo.itemManager().fetchCompleteItem(projectArea, IItemManager.UNSHARED, null);
		}

		IProcessArea processArea = useStandaloneProject ? projectArea : teamArea;
		return processArea;
	}

	public static IProcessDefinition getProcessDefinition(ITeamRepository repo, String processId,  
										boolean useStandaloneProject) throws Exception {
		return getProcessDefinition(repo, processId, null, useStandaloneProject);
	}
	/**
	 * Retrieves the existing process definition for the specified <code>processId</code>. If such a definition does not
	 * exist, it is created using the <code>getBasicProcessSpecification()</code>.
	 * 
	 * @param processId The id of the process definition to retrieve or create.
	 * @return the process definition
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static IProcessDefinition getProcessDefinition(ITeamRepository repo, String processId, String processXMLFileName, 
										boolean useStandaloneProject) throws Exception {

		IProcessItemService processService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);

		IProcessDefinition processDefinition = processService.findProcessDefinition(processId, IProcessItemService.ALL_PROPERTIES, null);

		if (processDefinition == null) {
			processDefinition = (IProcessDefinition)IProcessDefinition.ITEM_TYPE.createItem();
			processDefinition.setName(processId);
			processDefinition.setProcessId(processId);
			String processXML = null;
			if (processXMLFileName == null) {
				processXML = getBasicProcessSpecification(useStandaloneProject);
			} else {
				processXML = getBasicProcessSpecification(processXMLFileName);
			}
			Map definitionData = processDefinition.getProcessData();
			definitionData.put(ProcessContentKeys.PROCESS_SPECIFICATION_KEY,
					stringToContent(repo, processXML, null));
			definitionData.put(ProcessContentKeys.PROCESS_STATE_KEY, stringToContent(repo, getBasicProcessState(useStandaloneProject), null));

			processDefinition = (IProcessDefinition)processService.save(processDefinition, null);
		}

		return processDefinition;
	}

	/**
	 * Retrieves the basic process specification used by default in JUnit tests that require a process.
	 * 
	 * @return The basic process specification used by default in JUnit tests that require a process.
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	private static String getBasicProcessSpecification(boolean useStandaloneProject) throws Exception {
		String fileName = useStandaloneProject ? "BasicProcessSpecificationNoDevLine.xml" : "BasicProcessSpecification.xml"; //$NON-NLS-1$ //$NON-NLS-2$
		return getFileContents(fileName);
	}
	
	/**
	 * Retrieves the basic process specification used by default in JUnit tests that require a process.
	 * 
	 * @return The basic process specification used by default in JUnit tests that require a process.
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	private static String getBasicProcessSpecification(String processXMLFileName) throws Exception {
		return getFileContents(processXMLFileName);
	}

	private static String getFileContents(String fileName) throws IOException {
		StringBuilder fileContents = new StringBuilder();
		InputStream inputStream = null;
		try {
			inputStream = ProcessUtil.class.getResourceAsStream(fileName);
			byte[] buffer = new byte[4096];
			int bytesRead = inputStream.read(buffer);
			while (bytesRead != -1) {
				fileContents.append(new String(buffer, 0, bytesRead));
				bytesRead = inputStream.read(buffer);
			}
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
		}
		return fileContents.toString();
	}

	/**
	 * Retrieves the basic process state used by default in JUnit tests that require a process.
	 * 
	 * @return The basic process state used by default in JUnit tests that require a process.
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	private static String getBasicProcessState(boolean useStandaloneProject) throws Exception {
		String fileName = useStandaloneProject ? "BasicProcessStateNoDevLine.xml" : "BasicProcessState.xml"; //$NON-NLS-1$ //$NON-NLS-2$
		return getFileContents(fileName);
	}

	/**
	 * Retrieves the existing project area with the specified area name. If such a project area does not exist, it will
	 * be created.
	 * 
	 * @param processDefinitionHandle The project definition to create the project area in if it needs to be created.
	 * @param projectAreaName The name of the project area to retrieve.
	 * @return the project area
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	private static IProjectArea getProjectArea(ITeamRepository repo, IProcessDefinition processDefinition, String projectAreaName)
			throws Exception {

		IProcessItemService processService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);
		IProcessService processServerService= (IProcessService)((TeamRepository)repo).getServiceInterface(IProcessService.class);

		IProjectArea projectArea = (IProjectArea)processService.findProcessArea(new URI(encode(projectAreaName)), IProcessItemService.ALL_PROPERTIES,
				null);

		if (projectArea == null) {
			projectArea = (IProjectArea)IProjectArea.ITEM_TYPE.createItem();
			projectArea.setName(projectAreaName);
			projectArea.setProcessDefinition(processDefinition);
			projectArea = (IProjectArea)processService.save(projectArea, null);
		}
		
		if (!projectArea.isInitialized()) {
			// invoke the process server service as the client process interface doesn't have some classes pre-6.0.1
			projectArea= (IProjectArea)processServerService.initializeProjectArea(projectArea, null).getClientItems()[0];
			projectArea= (IProjectArea)projectArea.getWorkingCopy();
		}
//		// by default add the logged in user as a member
//		projectArea = (IProjectArea)projectArea.getWorkingCopy();
//		projectArea.addMember(repo.loggedInContributor());
//		projectArea.addRoleAssignments(repo.loggedInContributor(), processService.getClientProcess(projectArea, null).getRoles(projectArea, null));
//		projectArea = (IProjectArea)processService.save(projectArea, null);

		return projectArea;
	}

	/**
	 * Retrieves the existing team area with specified area name within <code>projectArea</code>. If such a team area
	 * does not exist, it will be created.
	 * 
	 * @param projectArea The project area context.
	 * @param areaName The name of the team area to retrieve.
	 * @return The project area handle.
	 * @throws Exception Throw all exceptions back to JUnit.
	 */
	public static ITeamArea getTeamArea(ITeamRepository repo, IProjectArea projectArea, String areaName) throws Exception {

		IProcessItemService processService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);

		ITeamArea teamArea = (ITeamArea)processService.findProcessArea(
				new URI(encode(projectArea.getName()) + "/" + encode(areaName)), IProcessItemService.ALL_PROPERTIES, null); //$NON-NLS-1$

		if (teamArea == null) {
			teamArea = (ITeamArea)ITeamArea.ITEM_TYPE.createItem();
			teamArea.setProjectArea(projectArea);
			teamArea.setName(areaName);

			projectArea = (IProjectArea)repo.itemManager().fetchCompleteItem(projectArea, IItemManager.REFRESH, null);
			projectArea = (IProjectArea)projectArea.getWorkingCopy();
			ITeamAreaHierarchy hierarchy = projectArea.getTeamAreaHierarchy();
			hierarchy.addRoot(teamArea, getDevelopmentLine(repo, projectArea, "development")); //$NON-NLS-1$

			IProcessItem[] items = processService.save(new IProcessItem[] { projectArea, teamArea }, null);

			teamArea = (ITeamArea)items[1];
		}

		return teamArea;
	}

	@SuppressWarnings("rawtypes")
	private static IDevelopmentLine getDevelopmentLine(ITeamRepository repo, IProjectArea projectArea, String id) throws TeamRepositoryException {
		List developmentLines = repo.itemManager().fetchCompleteItems(Arrays.asList(projectArea.getDevelopmentLines()), IItemManager.DEFAULT, null);

		for (Object object : developmentLines) {
			IDevelopmentLine devlopmentLine = (IDevelopmentLine)object;
			if (devlopmentLine.getId().equals(id)) {
				return devlopmentLine;
			}
		}
		return null;
	}
	
	/**
	 * Delete the project area and process definition, if any, created for the test run.
	 * 
	 * @param repo
	 * @param artifactIds
	 * @throws Exception
	 */
	public static void deleteProcessArtifacts(ITeamRepository repo, Map<String, String> artifactIds) throws Exception {
		deleteProjectArea(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID));
		deleteProjectArea(repo, artifactIds.get(TestSetupTearDownUtil.ARTIFACT_PROCESS_DEFINITION_ITEM_ID));
	}
	
	/**
	 * Delete the project area with the given item id.
	 * 
	 * @param repo
	 * @param projectAreaItemId
	 * @throws TeamRepositoryException
	 */
	public static void deleteProjectArea(ITeamRepository repo, String projectAreaItemId) throws TeamRepositoryException {
		if (projectAreaItemId == null) {
			return;
		}
		UUID projectAreaUUID = UUID.valueOf(projectAreaItemId);
		IProjectAreaHandle projectAreaHandle = (IProjectAreaHandle)IProjectArea.ITEM_TYPE.createItemHandle(projectAreaUUID, null);
		IProcessItemService processService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);
		// Deletes contained team area as well.
		processService.delete(projectAreaHandle, true, null);
	}

	/**
	 * Delete the process definition with the given item id.
	 * 
	 * @param repo
	 * @param processDefinitionItemId
	 * @throws TeamRepositoryException
	 */
	@SuppressWarnings("rawtypes")
	public static void deleteProcessDefinition(ITeamRepository repo, String processDefinitionItemId) throws TeamRepositoryException {
		if (processDefinitionItemId == null) {
			return;
		}

		UUID processDefinitionUUID = UUID.valueOf(processDefinitionItemId);
		IProcessDefinitionHandle processDefinitionHandle = (IProcessDefinitionHandle)IProcessDefinition.ITEM_TYPE.createItemHandle(
				processDefinitionUUID, null);
		IProcessItemService processService = (IProcessItemService)repo.getClientLibrary(IProcessItemService.class);
		// First delete any project areas referencing the definition.
		// This won't be the project area created by this test, but
		// could
		// be an old project area created by a test that didn't get to
		// clean up properly because of an exception.
		List projectAreas = processService.findAllProjectAreas(IProcessClientService.ALL_PROPERTIES, null);
		for (Object object : projectAreas) {
			IProjectArea tmpProjectArea = (IProjectArea)object;
			if (tmpProjectArea.getProcessDefinition().getItemId().equals(processDefinitionHandle.getItemId())) {
				processService.delete(tmpProjectArea, true, null);
			}
		}
		processService.delete(processDefinitionHandle, true, null);
	}

	/**
	 * Converts a <code>String</code> into content stored in the repository.
	 * 
	 * @param teamRepository The repository to store the content in.
	 * @param stringContent The <code>String</code> to convert to content.
	 * @param progressMonitor Used to track progress of storing the content. This can be <code>null</code>.
	 * @return The content the <code>String</code> was converted to or <code>null</code> if the specified
	 *         <oode>stringContent</code> or <code>teamRepository</code> was <code>null</code>.
	 * @throws TeamRepositoryException if the content could not be stored.
	 * @LongOp This is a long operation; it may block indefinitely; must not be called from a responsive thread.
	 */
	private static IContent stringToContent(ITeamRepository teamRepository, String stringContent, IProgressMonitor progressMonitor)
			throws TeamRepositoryException {
		IContent content = null;
		if (null != teamRepository && null != stringContent) {
			content = teamRepository.contentManager().storeContent(IContent.CONTENT_TYPE_TEXT, stringContent, progressMonitor);
		}
		return content;
	}

	/**
	 * Encode URI text.
	 * 
	 * @param text
	 * @return
	 */
	private static String encode(String text) {
		try {
			if (text == null)
				return null;
			return URLEncoder.encode(text, "UTF-8").replace("+", "%20"); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
		} catch (UnsupportedEncodingException x) {
			throw new RuntimeException(x);
		}
	}

}
