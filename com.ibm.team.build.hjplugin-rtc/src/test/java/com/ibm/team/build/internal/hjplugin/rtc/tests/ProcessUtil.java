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

import java.util.List;

import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;

public class ProcessUtil {
	public static final String DEFAULT_PROCESS_AREA = "HJPluginTests"; //$NON-NLS-1$
	private static IProjectArea fProjectArea;

    public static IProjectArea getProjectArea(ITeamRepository repo, String name) throws TeamRepositoryException {

        IProcessItemService processService = (IProcessItemService) repo.getClientLibrary(IProcessItemService.class);
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

}
