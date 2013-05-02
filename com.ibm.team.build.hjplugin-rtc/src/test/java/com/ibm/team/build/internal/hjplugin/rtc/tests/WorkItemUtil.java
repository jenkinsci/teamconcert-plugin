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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.foundation.common.text.XMLString;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.client.WorkItemOperation;
import com.ibm.team.workitem.client.WorkItemWorkingCopy;
import com.ibm.team.workitem.common.model.ICategory;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.IWorkItemType;

public class WorkItemUtil {
	
	public static List<IWorkItemHandle> findSomeWorkItems(ITeamRepository repository, int count) throws TeamRepositoryException {
		List<IWorkItemHandle> result = new ArrayList<IWorkItemHandle>(count);
		IWorkItemClient workItemClient = (IWorkItemClient) repository.getClientLibrary(IWorkItemClient.class);
		List<Integer> ids = new ArrayList(100);
		for (int i = 0; i < 100; i++) {
			ids.add(i);
		}

		List<IWorkItemHandle> workItemsFound = workItemClient.findWorkItemsById(ids, null);
		for (IWorkItemHandle workItemFound : workItemsFound) {
			if (workItemFound != null) {
				result.add(workItemFound);
				if (result.size() == count) {
					break;
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
     * @param projectArea
     *            the project area to use.
     * @param summary
     *            the summary for the work item
     * @return the handle for the new work item
     * @throws Exception
     *             Throw all exceptions back to JUnit.
     */
    public static IWorkItemHandle createWorkItem(ITeamRepository teamRepository, final IProjectArea projectArea,
            final String summary) throws Exception {
        final IWorkItemClient workItemClient = (IWorkItemClient) teamRepository.getClientLibrary(IWorkItemClient.class);

        WorkItemOperation operation = new WorkItemOperation("Create work item", IWorkItem.FULL_PROFILE) { //$NON-NLS-1$
            protected void execute(WorkItemWorkingCopy workingCopy, IProgressMonitor monitor)
                    throws TeamRepositoryException {
                workingCopy.getWorkItem().setHTMLSummary(XMLString.createFromPlainText(summary));
                List<ICategory> findCategories= workItemClient.findCategories(projectArea, ICategory.FULL_PROFILE, null);
                ICategory category;
                if (findCategories.isEmpty()) {
                	category = workItemClient.createCategory(projectArea, ProcessUtil.DEFAULT_PROCESS_AREA, monitor);
                    category = workItemClient.saveCategory(category, monitor);
                } else {
                	category = findCategories.get(0);
                }
                workingCopy.getWorkItem().setCategory(category);
            }
        };

        List<IWorkItemType> workItemTypes = workItemClient.findWorkItemTypes(projectArea, null);

        IWorkItemHandle workItemHandle = operation.run(workItemTypes.get(0), new NullProgressMonitor());
        return workItemHandle;
    }
    
    public static void deleteWorkItem(ITeamRepository repo, IWorkItemHandle wi) throws TeamRepositoryException {
        IWorkItemClient service = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
        service.deleteWorkItem(wi, new NullProgressMonitor());
    }

}
