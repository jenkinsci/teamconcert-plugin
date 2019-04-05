/*******************************************************************************
 * Copyright © 2013, 2018 IBM Corporation and others.
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
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.foundation.common.text.XMLString;
import com.ibm.team.process.common.IDevelopmentLine;
import com.ibm.team.process.common.IDevelopmentLineHandle;
import com.ibm.team.process.common.IIteration;
import com.ibm.team.process.common.IIterationHandle;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.client.WorkItemOperation;
import com.ibm.team.workitem.client.WorkItemWorkingCopy;
import com.ibm.team.workitem.common.model.ICategory;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.IWorkItemReferences;
import com.ibm.team.workitem.common.model.IWorkItemType;

@SuppressWarnings({"boxing"})
public class WorkItemUtil {
	
	public static List<IWorkItemHandle> findSomeWorkItems(ITeamRepository repository, int count) throws TeamRepositoryException {
		List<IWorkItemHandle> result = new ArrayList<IWorkItemHandle>(count);
		IWorkItemClient workItemClient = (IWorkItemClient) repository.getClientLibrary(IWorkItemClient.class);
		
		// This is a complete hack. We can't query for the work items associated with the project area because
		// queries require libraries not included in the toolkit. So instead look up by id. Unfortunately depending
		// on the test system we are running against, the work items that are accessible by this user may not be at
		// the start numerically. So define the ranges to be work item ranges that are likely to contain workitems to
		// test against.
		int[][] ranges = new int[][] {new int[] {0, 100}, new int[] {6099, 6110}};
		
		for (int j = 0; j < ranges.length; j++) {
			List<Integer> ids = new ArrayList<Integer>(100);
			for (int i = ranges[j][0]; i < ranges[j][1]; i++) {
				ids.add(i);
			}
	
			List<IWorkItemHandle> workItemsFound = workItemClient.findWorkItemsById(ids, null);
			for (IWorkItemHandle workItemFound : workItemsFound) {
				if (workItemFound != null) {
					result.add(workItemFound);
					if (result.size() == count) {
						return result;
					}
				}
			}
		}		
		return result;
	}

    /**
     * Creates and saves a new work item.
     * 
     * @param teamRepository
     *            the repository to use
     * @param projectAreaH
     *            the project area to use.
     * @param summary
     *            the summary for the work item
     * @return the handle for the new work item
     * @throws TeamRepositoryException - 
     *             Throw all exceptions back to JUnit.
     */
    public static IWorkItem createWorkItem(final ITeamRepository teamRepository, final IProjectAreaHandle projectAreaH,
            final String summary) throws TeamRepositoryException {
        final IWorkItemClient workItemClient = (IWorkItemClient) teamRepository.getClientLibrary(IWorkItemClient.class);

        WorkItemOperation operation = new WorkItemOperation("Create work item", IWorkItem.FULL_PROFILE) { //$NON-NLS-1$
            protected void execute(WorkItemWorkingCopy workingCopy, IProgressMonitor monitor)
                    throws TeamRepositoryException {
            	IProjectArea projectArea = (IProjectArea) teamRepository.itemManager().fetchCompleteItem(projectAreaH, IItemManager.REFRESH, 
            				new NullProgressMonitor());
                workingCopy.getWorkItem().setHTMLSummary(XMLString.createFromPlainText(summary));
                List<ICategory> findCategories= workItemClient.findCategories(projectAreaH, ICategory.FULL_PROFILE, null);
                ICategory category;
                if (findCategories.isEmpty()) {
                	category = workItemClient.createCategory(projectAreaH, ProcessUtil.DEFAULT_PROCESS_AREA, monitor);
                    category = workItemClient.saveCategory(category, monitor);
                } else {
                	category = findCategories.get(0);
                }
                workingCopy.getWorkItem().setCategory(category);
        		IDevelopmentLineHandle[] developmentLineHandles = projectArea.getDevelopmentLines();
        		@SuppressWarnings("unchecked")
				List<IDevelopmentLine> developmentLineList = teamRepository.itemManager().
        					fetchCompleteItems(Arrays.asList(developmentLineHandles),IItemManager.DEFAULT,monitor);
        		
        		// Search for target
        		IIteration backlog = null;
        		for (IDevelopmentLine developmentLine : developmentLineList) {
        			IIterationHandle[] iterationHandles = developmentLine.getIterations();
        			@SuppressWarnings("unchecked")
					List<IIteration> iterationList = teamRepository.itemManager().
								fetchCompleteItems(Arrays.asList(iterationHandles),IItemManager.DEFAULT,monitor);
        			// We will find "Backlog" at the top level
        			for (IIteration iteration : iterationList) {
        				if ("Backlog".equals(iteration.getName())) { //$NON-NLS-1$
        					backlog = iteration;
        				}
        				
        			}
        		}
       		    Assert.isNotNull(backlog);
                workingCopy.getWorkItem().setTarget(backlog);
                workingCopy.getWorkItem().setOwner(teamRepository.loggedInContributor());
                workingCopy.save(new NullProgressMonitor());
            }
        };

        List<IWorkItemType> workItemTypes = workItemClient.findWorkItemTypes(projectAreaH, null);

        IWorkItemHandle workItemHandle = operation.run(workItemTypes.get(0), new NullProgressMonitor());
        IWorkItem workItem = (IWorkItem) teamRepository.itemManager().fetchCompleteItem(workItemHandle, 
        							IItemManager.REFRESH, new NullProgressMonitor());
        return workItem;
    }
    
    public static void deleteWorkItem(ITeamRepository repo, IWorkItemHandle wi) throws TeamRepositoryException {
        IWorkItemClient service = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
        service.deleteWorkItem(wi, new NullProgressMonitor());
    }

	public static IWorkItem getWorkItem(ITeamRepository repo, String workItemid) throws NumberFormatException, TeamRepositoryException {
        final IWorkItemClient workItemClient = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
        IWorkItem workItem = workItemClient.findWorkItemById(Integer.valueOf(workItemid), IWorkItem.FULL_PROFILE, new NullProgressMonitor());
		return workItem;
	}
	
	public static IWorkItemReferences getWorkItemReferences(ITeamRepository repo, IWorkItem workItem) throws TeamRepositoryException {
        final IWorkItemClient workItemClient = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
		IWorkItemReferences references = workItemClient.resolveWorkItemReferences(workItem,
						new NullProgressMonitor());
		return references;
	}

	public static void deleteWorkItems(ITeamRepository repo, Map<String, String> setupArtifacts) throws TeamRepositoryException {
	  final IWorkItemClient workItemClient = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
	  String workItemId = setupArtifacts.get(TestSetupTearDownUtil.ARTIFACT_WORKITEM_ID);
	  if (workItemId != null) {
		  IWorkItemHandle wiH = workItemClient.findWorkItemById(
				  Integer.valueOf(workItemId), IWorkItem.MEDIUM_PROFILE, new NullProgressMonitor());
		  if (wiH != null) {
			  deleteWorkItem(repo, wiH);
		  }
	  }
	}
}
