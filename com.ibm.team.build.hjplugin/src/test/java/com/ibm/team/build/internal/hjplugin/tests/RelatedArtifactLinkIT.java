/*******************************************************************************
 * Copyright © 2018, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */

package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;

public class RelatedArtifactLinkIT extends AbstractTestCase {
	@Rule
	public JenkinsRule r = new JenkinsRule();
	
	@Before
	public void setup() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
		createSandboxDirectory();
	}
	
	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
	}

	@Test
	public void testJenkinsBuildLinkCreatedInStreamConfigurationOptionIsTrue() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts =  Utils.setUpBuildStream_AcceptChanges(testingFacade, defaultC, streamName, 
				"WorkItem to test link is created");
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		String workspaceUUID = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);

		try {
			// get the work item id and assert that it is not null 
			String workItemId = setupArtifacts.get(Utils.ARTIFACT_WORKITEM_ID);
			assertNotNull("work item id is null", workItemId); //$NON-NLS-1$
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(getJenkinsRule(), defaultC, streamName, null, true);
			
			// Run a build
			String prevUrl = null;
			{
				FreeStyleBuild build = Utils.runBuild(prj, null);
				// Test that previousBuildUrl is none because there is nothing to compare with
				Utils.verifyStreamBuild(build, streamUUID, ""); //$NON-NLS-1$
				prevUrl = build.getUrl();
			}

			// Deliver the changes from the repository workspace to stream
			int changeSetsDelivered = Utils.deliverChangesToStream(testingFacade, defaultC, streamUUID, workspaceUUID);
			assertEquals(2, changeSetsDelivered);

			// Run the build again and changes will be accepted.
			{
				FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyStreamBuild(build, streamUUID, prevUrl); //$NON-NLS-1$
	
				// verify that there are two change log set
				RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();
				assertEquals(changeSetsDelivered, changelog.getChangeSetsAcceptedCount());
	
				// Verify that the work item has a related artifact link to the Jenkins build
				String buildUrl = Util.encode(Jenkins.getInstance().getRootUrl() + build.getUrl());
				Utils.testWorkItemHasRelatedArtifactLink(workItemId, buildUrl);
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	@Test
	public void testJenkinsBuildLinkNotCreatedInStreamConfigurationOptionIsFalse() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts =  Utils.setUpBuildStream_AcceptChanges(testingFacade, defaultC, streamName, 
				"WorkItem to test no link is created");
		String streamUUID = setupArtifacts.get(Utils.ARTIFACT_STREAM_ITEM_ID);
		String workspaceUUID = setupArtifacts.get(Utils.ARTIFACT_WORKSPACE_ITEM_ID);

		try {
			// get the work item id and assert that it is not null 
			String workItemId = setupArtifacts.get(Utils.ARTIFACT_WORKITEM_ID);
			assertNotNull("work item id is null", workItemId); //$NON-NLS-1$
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(this.getJenkinsRule(), defaultC, streamName, null, false);
			
			// Run a build
			String prevUrl = ""; //$NON-NLS-1$
			{
				FreeStyleBuild build = Utils.runBuild(prj, null);
				// Test that previousBuildUrl is none because there is nothing to compare with
				Utils.verifyStreamBuild(build, streamUUID, ""); //$NON-NLS-1$
				prevUrl = build.getUrl();
			}

			// Deliver the changes from the repository workspace to stream
			int changeSetsDelivered = Utils.deliverChangesToStream(testingFacade, defaultC, streamUUID, workspaceUUID);
			assertEquals(2, changeSetsDelivered);

			// Run the build again and changes will be accepted.
			{
				FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
				Utils.verifyStreamBuild(build, streamUUID, prevUrl); //$NON-NLS-1$
	
				// verify that there are two change log set
				RTCChangeLogSet changelog = (RTCChangeLogSet) build.getChangeSet();
				assertEquals(changeSetsDelivered, changelog.getChangeSetsAcceptedCount());
	
				// Verify that the work item has a related artifact link to the Jenkins build
				Utils.testWorkItemHasNoRelatedArtifactLink(workItemId);
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	private JenkinsRule getJenkinsRule() {
		return r;
	}

	@Test
	public void testJenkinsBuildLinkCreatedInWorkspaceConfigurationOptionIsTrue() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
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
						Integer.valueOf(loginInfo.getTimeout()), workspaceName,
						componentName, this.getSandboxDir().getAbsolutePath(),
						Boolean.TRUE, "This is work item for repository workspace accept changes with link"); //$NON-NLS-1$
		try {
			// get the work item id and assert that it is not null 
			String workItemId = setupArtifacts.get(Utils.ARTIFACT_WORKITEM_ID);
			assertNotNull("work item id is null", workItemId); //$NON-NLS-1$
			
			// Create a Jenkins freestyle job with repository workspace
			RTCScm.BuildType b = new RTCScm.BuildType("buildWorkspace", null, workspaceName, null, null); //$NON-NLS-1$
			b.setAddLinksToWorkItems(true);
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName, b);


			// Run the build once and changes will be accepted.
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.verifyRTCScmInBuild(build, false);

			// Verify that the work item has a related artifact link to the Jenkins build
			String buildUrl = Util.encode(Jenkins.getInstance().getRootUrl() + build.getUrl());
			Utils.testWorkItemHasRelatedArtifactLink(workItemId, buildUrl);
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	@Test
	public void testJenkinsBuildLinkNotCreatedInWorkspaceConfigurationOptionIsFalse() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		
		Config defaultC = Config.DEFAULT;
		RTCLoginInfo loginInfo = defaultC.getLoginInfo();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
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
				Integer.valueOf(loginInfo.getTimeout()), workspaceName,
				componentName, this.getSandboxDir().getAbsolutePath(),
				Boolean.TRUE, "This is work item for repository workspace accept changes with no link"); //$NON-NLS-1$
		try {
			// get the work item id and assert that it is not null 
			String workItemId = setupArtifacts.get(Utils.ARTIFACT_WORKITEM_ID);
			assertNotNull("work item id is null", workItemId); //$NON-NLS-1$
			
			// Create a Jenkins freestyle job with repository workspace
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(), workspaceName);

			// Run the build once and changes will be accepted.
			FreeStyleBuild build = Utils.runBuild(prj, Utils.getPactionsWithEmptyBuildResultUUID());
			Utils.verifyRTCScmInBuild(build, false);

			// Verify that the work item has a related artifact link to the Jenkins build
			Utils.testWorkItemHasNoRelatedArtifactLink(workItemId);
			
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
}
