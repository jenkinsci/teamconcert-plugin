/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.HashMap;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.process.common.IProcessDefinition;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;

/**
 * Test cases that validate the RTCFacade layer. This class is intended to test only those methods that doesn't need
 * elaborate validation scenarios like testProcessArea(). For methods like load() that need extensive test scenarios it
 * is recommended to create a separate test class.
 * 
 */
public class RTCFacadeTests {

	private RepositoryConnection connection;

	public RTCFacadeTests(RepositoryConnection repositoryConnection) {
		this.connection = repositoryConnection;
	}
	
	/**
	 * Create a project area with a single team area.
	 * 
	 * @param projectAreaName
	 * @return
	 * @throws Exception
	 */
	public Map<String, String> setupTestProcessArea_basic(String projectAreaName) throws Exception {
		Map<String, String> setupArtifacts = new HashMap<String, String>();
		connection.ensureLoggedIn(null);
		ITeamRepository repo = connection.getTeamRepository();
		String name = projectAreaName;
		// create and return the itemIds so that the teamconcert plugin test, that eventually invokes this method, knows
		// the artifacts to be deleted during tear down.
		IProcessDefinition processDefinition = ProcessUtil.getProcessDefinition(repo, name, false);
		setupArtifacts.put(TestSetupTearDownUtil.ARTIFACT_PROCESS_DEFINITION_ITEM_ID, processDefinition.getItemId().getUuidValue());
		ITeamArea teamArea = (ITeamArea)ProcessUtil.getProcessArea(repo, name, processDefinition, false);
		setupArtifacts.put(TestSetupTearDownUtil.ARTIFACT_PROJECT_AREA_ITEM_ID, teamArea.getProjectArea().getItemId().getUuidValue());
		return setupArtifacts;
	}
}
