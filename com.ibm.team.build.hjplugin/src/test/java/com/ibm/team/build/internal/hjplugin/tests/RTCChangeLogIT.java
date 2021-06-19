/*******************************************************************************
 * Copyright © 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.common.io.Files;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

/**
 * Integration tests around change log 
 *
 */
public class RTCChangeLogIT extends AbstractTestCase {
	
	/**
	 * Jenkins
	 */
	@Rule public JenkinsRule r = new JenkinsRule();

	/**
	 * Test whether change log has work item summary without control characters 
	 *  even if the actual work item summary has some.
	 * 
	 * @throws Exception - throw all exceptions to JUnit
	 */
	@Test
	public void testChangeLogGeneratedWithoutControlCharacterInWISummary() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		createSandboxDirectory();
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		@SuppressWarnings("unchecked")
		Map<String, String> setupArtifacts = (Map<String, String>) testingFacade
				.invoke("setupAcceptChanges", //$NON-NLS-1$
						new Class[] { String.class, // serverURL,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // workspaceName,
								String.class, // componentName
								String.class, // loadDirectory
								boolean.class, // createWorkItem
								String.class,// workItemSummary
								}, 
						loginInfo.getServerUri(),
						loginInfo.getUserId(),
						loginInfo.getPassword(),
						loginInfo.getTimeout(),
						workspaceName,
						componentName, getSandboxDir().getAbsolutePath(),
						true, "This is a work item with control \u0010 character");

		try  {
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName);
			// Run a build
			FreeStyleBuild build = Utils.runBuild(prj, null);
			// Get the change log file and check whether our parser can 
			// handle it
			File changelogFile = new File(build.getRootDir(), "changelog.xml"); //$NON-NLS-1$
			File temp = File.createTempFile("changelog", "mybuild");  //$NON-NLS-1$//$NON-NLS-2$
			Files.copy(changelogFile, temp);
			RTCChangeLogParser parser = new RTCChangeLogParser();
			RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, null, changelogFile);
			// Verify that one of the changelogEntry has a work item with the summary and a whitespace 
			// instead of the control character 
			boolean found = false;
			for (Object ce : result.getItems()) {
				RTCChangeLogChangeSetEntry rtcEntry = (RTCChangeLogChangeSetEntry) ce;
				List<WorkItemDesc> allWorkItems = new ArrayList<WorkItemDesc>();
				if (rtcEntry.getWorkItem() != null) allWorkItems.add(rtcEntry.getWorkItem());
				if (rtcEntry.getAdditionalWorkItems() != null) {
					allWorkItems.addAll(rtcEntry.getAdditionalWorkItems());
				}
				for (WorkItemDesc wi : allWorkItems) {
					if (wi.getSummary().startsWith("This is a work item with")) { //$NON-NLS-1$
						assertEquals("This is a work item with control   character",  //$NON-NLS-1$
								wi.getSummary());
						found = true;
						break;
					}
				}
			}
			assertTrue(found);
		} finally {
			Utils.tearDown(testingFacade, Config.DEFAULT, setupArtifacts);
		}
	}

	public JenkinsRule getJenkinsRule() {
		return this.r;
	}

	public void setJenkinsRule(JenkinsRule r) {
		this.r = r;
	}
}
