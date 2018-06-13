/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.net.URI;
import java.util.List;

import com.ibm.team.links.client.ILinkManager;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.factory.ILinkFactory;
import com.ibm.team.links.common.factory.IReferenceFactory;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.WorkItemLinkTypes;

public class WorkItemUtils {
	
	/**
	 * Add Jenkins build URL as related links to the given work items
	 *   
	 * @param repo The RTC repository
	 * @param workItemIds The work item ids
	 * @param url The URL to add 
	 * @param urlText The label for the URL
	 * @throws TeamRepositoryException  If there is an exception processing the request to add links to work items
	 * @throws NumberFormatException If any of the work item id is not an integer
	 */
	public static void addRelatedLinkToWorkItems(ITeamRepository repo, List<Integer> workItemIds,
			String url, String urlText) throws TeamRepositoryException {
		// For every work item, create a link with the given urlText
		for (Integer workItemId : workItemIds) {
			IWorkItemClient wcl = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
	        IWorkItem workItem = wcl.findWorkItemById(workItemId.intValue(), IWorkItem.FULL_PROFILE, null);
	        ILinkManager linkManager = (ILinkManager) repo.getClientLibrary(ILinkManager.class);
	        URI relatedArtifactURI = URI.create(url);
	       
	        IReference target = IReferenceFactory.INSTANCE.createReferenceFromURI(relatedArtifactURI, urlText, null, "text/html"); //$NON-NLS-1$
	        IReference source = IReferenceFactory.INSTANCE.createReferenceToItem((IItemHandle) workItem);

	        ILink link = ILinkFactory.INSTANCE.createLink(WorkItemLinkTypes.RELATED_ARTIFACT, source, target);
	        linkManager.saveLink(link, null);
		}
	}
}
