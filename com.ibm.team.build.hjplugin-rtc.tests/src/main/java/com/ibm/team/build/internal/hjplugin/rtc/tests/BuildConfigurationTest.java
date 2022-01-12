/*******************************************************************************
 * Copyright (c) 2016, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import org.eclipse.core.runtime.IProgressMonitor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;
// Mockito is not shipped in p2 repo, we cannot put it in required bundles so we mark it in imported packages to keep Eclipse happy
// The mockito package comes from repository.common's lib folder.
// If you see an error in Eclipse that this package cannot be found ,then load the target platform from 
// Target Definitions component
import org.mockito.Mockito;
import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;
import com.ibm.team.build.internal.hjplugin.rtc.RTCInternalException;
import com.ibm.team.build.internal.hjplugin.rtc.RTCWorkspaceUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.hjplugin.rtc.tests.utils.BuildConfigurationHelper;
import com.ibm.team.build.internal.hjplugin.rtc.tests.utils.Config;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.InternalRepositoryException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.IBaselineSet;

public class BuildConfigurationTest {
	private TestSetupTearDownUtil fTestSetupTearDownUtil;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	
	@Rule
	public TemporaryFolder sandbox = new TemporaryFolder();
	
	private synchronized TestSetupTearDownUtil getTestSetupTearDownUtil() {
		if (fTestSetupTearDownUtil == null) {
			 fTestSetupTearDownUtil = new TestSetupTearDownUtil();
		}
		return fTestSetupTearDownUtil;
	}
	
	@Before
	public void setup() {
		if (Config.DEFAULT.isConfigured()) {
			serverURI = Config.DEFAULT.getServerURI();
			password = Config.DEFAULT.getPassword();
			userId = Config.DEFAULT.getUserID();
			timeout = Config.DEFAULT.getTimeout();
		}
	}

	@Test
	public void testCreateWorkspaceWithRetrySuccess() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		IWorkspaceConnection workspaceConnection = Mockito.mock(IWorkspaceConnection.class);
		RTCWorkspaceUtils workspaceUtils = Mockito.mock(RTCWorkspaceUtils.class);
		
		Mockito.when(workspaceUtils.createWorkspace(Mockito.any(ITeamRepository.class), 
				Mockito.any(IBaselineSet.class),
				Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class)))
				.thenReturn(workspaceConnection);
		
		BuildConfigurationHelper configurationHelper = getBuildConfigurationHelper(connection.getTeamRepository(), 
								workspaceUtils, 5, 10);
		IWorkspaceConnection receivedWorkspaceConnection = configurationHelper.createRepositoryWorkspaceWithRetry(
					connection.getTeamRepository(), Mockito.mock(IBaselineSet.class), 
					"Dummy", "Dummy Comment",
					connection.getTeamRepository().loggedInContributor(),
					Mockito.mock(IConsoleOutput.class), Mockito.mock(IProgressMonitor.class));

		Mockito.verify(workspaceUtils, Mockito.times(1)).createWorkspace(Mockito.any(ITeamRepository.class), 
				Mockito.any(IBaselineSet.class),
				Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class));
	}

	@Test
	public void testCreateWorkspaceWithRetryFailedWithTRE() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		IWorkspaceConnection workspaceConnection = Mockito.mock(IWorkspaceConnection.class);
		RTCWorkspaceUtils workspaceUtils = Mockito.mock(RTCWorkspaceUtils.class);
		
		Mockito.when(workspaceUtils.createWorkspace(Mockito.any(ITeamRepository.class), 
				Mockito.any(IBaselineSet.class),
				Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class)))
				.thenThrow(new TeamRepositoryException("Intentionally throwing a TRE"));
		
		BuildConfigurationHelper configurationHelper = getBuildConfigurationHelper(connection.getTeamRepository(), 
								workspaceUtils, 5, 10);
		try {
			IWorkspaceConnection receivedWorkspaceConnection = configurationHelper.createRepositoryWorkspaceWithRetry(
						connection.getTeamRepository(), Mockito.mock(IBaselineSet.class), 
						"Dummy", "Dummy Comment",
						connection.getTeamRepository().loggedInContributor(),
						Mockito.mock(IConsoleOutput.class), Mockito.mock(IProgressMonitor.class));
		} catch (TeamRepositoryException exp) {
			Assert.assertEquals("Intentionally throwing a TRE", exp.getMessage());
			Mockito.verify(workspaceUtils, Mockito.times(1)).createWorkspace(Mockito.any(ITeamRepository.class), 
					Mockito.any(IBaselineSet.class),
					Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class));
		}
	}
	
	@Test
	public void testCreateWorkspaceWithRetrySuccessWithOneInternalRepositoryException() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		IWorkspaceConnection workspaceConnection = Mockito.mock(IWorkspaceConnection.class);
		RTCWorkspaceUtils workspaceUtils = Mockito.mock(RTCWorkspaceUtils.class);
		
		Mockito.when(workspaceUtils.createWorkspace(Mockito.any(ITeamRepository.class), 
				Mockito.any(IBaselineSet.class),
				Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class)))
				.thenThrow(new InternalRepositoryException("Intentionally throwing an exception"))
				.thenReturn(workspaceConnection);
		
		BuildConfigurationHelper configurationHelper = getBuildConfigurationHelper(connection.getTeamRepository(), 
								workspaceUtils, 5, 10);
		
		configurationHelper.createRepositoryWorkspaceWithRetry(
						connection.getTeamRepository(), Mockito.mock(IBaselineSet.class), 
						"Dummy", "Dummy Comment",
						connection.getTeamRepository().loggedInContributor(),
						Mockito.mock(IConsoleOutput.class), Mockito.mock(IProgressMonitor.class));

		Mockito.verify(workspaceUtils, Mockito.times(2)).createWorkspace(Mockito.any(ITeamRepository.class), 
				Mockito.any(IBaselineSet.class),
				Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class));
	}
	
	@Test
	public void testCreateWorkspaceWithRetryFailureWithAllInternalRepositoryException() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		RTCWorkspaceUtils workspaceUtils = Mockito.mock(RTCWorkspaceUtils.class);
		
		Mockito.when(workspaceUtils.createWorkspace(Mockito.any(ITeamRepository.class), 
				Mockito.any(IBaselineSet.class),
				Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class)))
				.thenThrow(new InternalRepositoryException("Intentionally throwing an exception"));

		BuildConfigurationHelper configurationHelper = getBuildConfigurationHelper(connection.getTeamRepository(), 
													workspaceUtils, 5, 10);

		try {
			IWorkspaceConnection receivedWorkspaceConnection = configurationHelper.createRepositoryWorkspaceWithRetry(
							connection.getTeamRepository(), Mockito.mock(IBaselineSet.class), 
							"Dummy", "Dummy Comment",
							connection.getTeamRepository().loggedInContributor(),
							Mockito.mock(IConsoleOutput.class), Mockito.mock(IProgressMonitor.class));
			Assert.fail("Was excepting an exception to be thrown"); 
		} catch (RTCInternalException exp) {
			Assert.assertEquals(String.format("Failed to create a Repository Workspace after %s retries", configurationHelper.getRetryCount()),
							exp.getMessage());
			// Caught the expected exception;
			Mockito.verify(workspaceUtils, Mockito.times(configurationHelper.getRetryCount())).createWorkspace(Mockito.any(ITeamRepository.class), 
					Mockito.any(IBaselineSet.class),
					Mockito.any(String.class), Mockito.any(String.class), Mockito.any(IContributor.class), Mockito.any(IProgressMonitor.class));
		}
	}
	
	private BuildConfigurationHelper getBuildConfigurationHelper(ITeamRepository teamRepository,
						RTCWorkspaceUtils workspaceUtils, int retryCount,
						long sleepTimeout) {
		BuildConfigurationHelper configurationHelper = new BuildConfigurationHelper(teamRepository, 
				sandbox.getRoot().getAbsolutePath());
		configurationHelper.setRTCWorkspaceUtils(workspaceUtils);
		configurationHelper.setSleepTimeout(10);
		configurationHelper.setRetryCount(5);
		return configurationHelper;
	}
}
