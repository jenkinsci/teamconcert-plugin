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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.build.internal.client.util.ContentUtil;
import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IDevelopmentLine;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProcessDefinition;
import com.ibm.team.process.common.IProcessDefinitionHandle;
import com.ibm.team.process.common.IProcessItem;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.process.common.ITeamAreaHierarchy;
import com.ibm.team.process.common.ProcessContentKeys;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IWorkItemClient;

public class ProcessUtil {
	public static final String DEFAULT_PROCESS_AREA = "HJPluginTests"; //$NON-NLS-1$

    private ITeamArea fTeamArea;
    private IProjectArea fProjectArea;
    private IProcessDefinition fProcessDefinition;

    /**
     * Helper method to setup the process area using a basic process definition.
     * 
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    public IProcessArea getProcessArea(ITeamRepository repo, String name, boolean useStandaloneProject) throws Exception {

        if (fProcessDefinition == null) {
            IProcessDefinition processDefinition = getProcessDefinition(repo, name, useStandaloneProject);
            IProjectArea projectArea = getProjectArea(repo, processDefinition, name);
            ITeamArea teamArea = null;
            if (!useStandaloneProject) {
            	teamArea = getTeamArea(repo, projectArea, name + ".team.area"); //$NON-NLS-1$
                IWorkItemClient workItemClient = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
                workItemClient.setDefaultTeamArea(teamArea, new NullProgressMonitor());

                // If the team area was created...then the project area was
                // modified.
                projectArea = (IProjectArea) repo.itemManager().fetchCompleteItem(projectArea, IItemManager.UNSHARED, null);
            }

            // only assign to fields at end if all goes well
            fProjectArea = projectArea;
            fTeamArea = teamArea;
            fProcessDefinition = processDefinition;
        }
        IProcessArea processArea = useStandaloneProject ? fProjectArea : fTeamArea;
        return processArea;
    }

    /**
     * Retrieves the existing process definition for the specified
     * <code>processId</code>. If such a definition does not exist, it is
     * created using the <code>getBasicProcessSpecification()</code>.
     * 
     * @param processId
     *            The id of the process definition to retrieve or create.
     * @return the process definition
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    private IProcessDefinition getProcessDefinition(ITeamRepository repo, String processId,
    		boolean useStandaloneProject) throws Exception {

        IProcessItemService processService = (IProcessItemService) repo.getClientLibrary(IProcessItemService.class);

        IProcessDefinition processDefinition = processService.findProcessDefinition(processId,
                IProcessItemService.ALL_PROPERTIES, null);

        if (processDefinition == null) {
            processDefinition = (IProcessDefinition) IProcessDefinition.ITEM_TYPE.createItem();
            processDefinition.setName(processId);
            processDefinition.setProcessId(processId);

            Map definitionData = processDefinition.getProcessData();
            definitionData.put(ProcessContentKeys.PROCESS_SPECIFICATION_KEY, ContentUtil.stringToContent(
                    repo, getBasicProcessSpecification(useStandaloneProject)));
            definitionData.put(ProcessContentKeys.PROCESS_STATE_KEY, ContentUtil.stringToContent(repo,
                    getBasicProcessState(useStandaloneProject)));

            processDefinition = (IProcessDefinition) processService.save(processDefinition, null);
        }

        return processDefinition;
    }

    /**
     * Retrieves the basic process specification used by default in JUnit tests
     * that require a process.
     * 
     * @return The basic process specification used by default in JUnit tests
     *         that require a process.
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    private String getBasicProcessSpecification(boolean useStandaloneProject) throws Exception {
        String fileName = useStandaloneProject ? "BasicProcessSpecificationNoDevLine.xml" : "BasicProcessSpecification.xml"; //$NON-NLS-1$ //$NON-NLS-2$
    	return getFileContents(fileName);
    }

	private String getFileContents(String fileName) throws IOException {
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
     * Retrieves the basic process state used by default in JUnit tests that
     * require a process.
     * 
     * @return The basic process state used by default in JUnit tests that
     *         require a process.
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    private String getBasicProcessState(boolean useStandaloneProject) throws Exception {
        String fileName = useStandaloneProject ? "BasicProcessStateNoDevLine.xml" : "BasicProcessState.xml"; //$NON-NLS-1$ //$NON-NLS-2$
        return getFileContents(fileName);
    }
    /**
     * Retrieves the existing project area with the specified area name. If such
     * a project area does not exist, it will be created.
     * 
     * @param processDefinitionHandle
     *            The project definition to create the project area in if it
     *            needs to be created.
     * @param projectAreaName
     *            The name of the project area to retrieve.
     * @return the project area
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    private IProjectArea getProjectArea(ITeamRepository repo,
    		IProcessDefinitionHandle processDefinitionHandle, String projectAreaName)
            throws Exception {

        IProcessItemService processService = (IProcessItemService) repo.getClientLibrary(IProcessItemService.class);

        IProjectArea projectArea = (IProjectArea) processService.findProcessArea(new URI(projectAreaName),
                IProcessItemService.ALL_PROPERTIES, null);

        if (projectArea == null) {
            projectArea = (IProjectArea) IProjectArea.ITEM_TYPE.createItem();
            projectArea.setName(projectAreaName);
            projectArea.setProcessDefinition(processDefinitionHandle);
            projectArea = (IProjectArea) processService.save(projectArea, null);
        }
        if (!projectArea.isInitialized()) {
        	// This call fails with ClassNotFound - org.eclipse.jface.text.BadLocationException
        	// Consider pre-initializing the project area
            projectArea = processService.initialize(projectArea, null);
        }

        return projectArea;
    }
    /**
     * Retrieves the existing team area with specified area name within
     * <code>projectArea</code>. If such a team area does not exist, it will
     * be created.
     * 
     * @param projectArea
     *            The project area context.
     * @param areaName
     *            The name of the team area to retrieve.
     * @return The project area handle.
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    public static ITeamArea getTeamArea(ITeamRepository repo,
    		IProjectArea projectArea, String areaName) throws Exception {

        IProcessItemService processService = (IProcessItemService) repo.getClientLibrary(IProcessItemService.class);

        ITeamArea teamArea = (ITeamArea) processService.findProcessArea(new URI(projectArea.getName()
                + "/" + areaName), IProcessItemService.ALL_PROPERTIES, null); //$NON-NLS-1$

        if (teamArea == null) {
            teamArea = (ITeamArea) ITeamArea.ITEM_TYPE.createItem();
            teamArea.setProjectArea(projectArea);
            teamArea.setName(areaName);

            projectArea = (IProjectArea) repo.itemManager().fetchCompleteItem(projectArea,
                    IItemManager.REFRESH, null);
            projectArea = (IProjectArea) projectArea.getWorkingCopy();
            ITeamAreaHierarchy hierarchy = projectArea.getTeamAreaHierarchy();
            hierarchy.addRoot(teamArea, getDevelopmentLine(repo, projectArea, "development")); //$NON-NLS-1$

            IProcessItem[] items = processService.save(new IProcessItem[] { projectArea, teamArea }, null);

            teamArea = (ITeamArea) items[1];
        }

        return teamArea;
    }

    private static IDevelopmentLine getDevelopmentLine(ITeamRepository repo, IProjectArea projectArea, String id) throws TeamRepositoryException {
        List developmentLines = repo.itemManager().fetchCompleteItems(
                Arrays.asList(projectArea.getDevelopmentLines()), IItemManager.DEFAULT, null);

        for (Object object : developmentLines) {
            IDevelopmentLine devlopmentLine = (IDevelopmentLine) object;
            if (devlopmentLine.getId().equals(id)) {
                return devlopmentLine;
            }
        }
        return null;
    }

    /**
     * Tears down the process area.
     * 
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    protected void tearDownProcessArea(ITeamRepository repo) throws Exception {

    	IProcessItemService processService = (IProcessItemService) repo.getClientLibrary(IProcessItemService.class);

        if (fProjectArea != null) {
            // Deletes contained team area as well.
            processService.delete(fProjectArea, true, null);
            fProjectArea = null;
            fTeamArea = null;
        }

        if (fProcessDefinition != null) {
            // First delete any project areas referencing the definition.
            // This won't be the project area created by this test, but
            // could
            // be an old project area created by a test that didn't get to
            // clean up properly because of an exception.
            List projectAreas = processService.findAllProjectAreas(IProcessClientService.ALL_PROPERTIES, null);
            for (Object object : projectAreas) {
                IProjectArea projectArea = (IProjectArea) object;
                if (projectArea.getProcessDefinition().getItemId().equals(fProcessDefinition.getItemId())) {
                    processService.delete(projectArea, true, null);
                }
            }

            processService.delete(fProcessDefinition, true, null);
            fProcessDefinition = null;
        }
    }
}
