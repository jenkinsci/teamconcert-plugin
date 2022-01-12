/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.MethodSorters;

import com.ibm.team.build.internal.hjplugin.rtc.ConnectionDetails;
import com.ibm.team.build.internal.hjplugin.rtc.Constants;
import com.ibm.team.build.internal.hjplugin.rtc.Messages;
import com.ibm.team.build.internal.hjplugin.rtc.RTCBuildUtils;
import com.ibm.team.build.internal.hjplugin.rtc.RTCConfigurationException;
import com.ibm.team.build.internal.hjplugin.rtc.RepositoryConnection;
import com.ibm.team.build.internal.hjplugin.rtc.tests.utils.Config;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ListFilesAPIIT {
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	
	@Rule
	public TemporaryFolder sandbox = new TemporaryFolder();

	@Rule
	public TemporaryFolder scratchFolder = new TemporaryFolder();

	private static String buildResultUUID;
	private static HashMap<String, String> setupArtifacts;
	
	private TestSetupTearDownUtil fTestSetupTearDownUtil;

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
	public void testAAA() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		setupArtifacts = new HashMap<String, String>();
		buildResultUUID = setupBuildResult(connection, setupArtifacts);
	}
	
	@Test
	public void testListFilesBuildResultUUIDValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		buildResultParamValidationHelper(new FunctionWithException<String, Object, Exception>(){
			public Object apply(String buildResultUUID) throws Exception {
				RTCBuildUtils.getInstance().listFiles(buildResultUUID, 
				    "test", "test", "artifact", 300, connection.getTeamRepository(), 
				    listener, Locale.getDefault(), getProgressMonitor());
				return null;
			}});
	}
	
	@Test
	public void testListFilesFileNamePatternParam() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// Test with an invalid pattern
		String fileNamePattern="[a-aA-Z\\.][][][']";
		
		// Setup
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		try {
			RTCBuildUtils.getInstance().listFiles(UUID.generate().getUuidValue(), 
					fileNamePattern, "test", "arifact", 100, 
					connection.getTeamRepository(), 
				    listener, Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertTrue(exp.getMessage(), exp.getMessage().contains(
				"The value \"" + fileNamePattern + "\" provided for \"fileNameOrPattern\" is an "
						+ "invalid regular expression."));
			Assert.assertTrue(exp.getMessage().contains("Unclosed character class")); 
		}
	}
	
	@Test
	public void testListFilesContributionTypeParam() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// Setup
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		for (String contributionType : new String [] {"logsz", "artifactX", "abc", "Def"}) {
			try {
				RTCBuildUtils.getInstance().listFiles(UUID.generate().getUuidValue(), 
						"test", "test", contributionType, 100, 
						connection.getTeamRepository(), 
					    listener, Locale.getDefault(), getProgressMonitor());
			} catch (RTCConfigurationException exp) {
				Assert.assertTrue(exp.getMessage(), exp.getMessage().contains(
						Messages.getDefault().RTCBuildUtils_invalid_contribution_type_specified(
								contributionType)));
			}
		}
	}
	
	@Test
	public void testListFilesMaxResultsParam() throws Exception {
		// Test with 0, greater than max allowed value and negative value
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		
		// Negative tests
		for (int i : new int [] { -1, 0, RTCBuildUtils.LIST_FILES_MAX_RESULTS + 1} ) {
			try {
				RTCBuildUtils.getInstance().listFiles(
						UUID.generate().getUuidValue(), "test", "Test",
						"artifact", i, connection.getTeamRepository(), listener,
						Locale.getDefault(), getProgressMonitor());
						
			} catch (RTCConfigurationException exp) {
				Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_maxResults_is_invalid(
								i, RTCBuildUtils.LIST_FILES_MAX_RESULTS),
					exp.getMessage());
			}
		}
	}
	
	@Test
	public void testListFilesNoFileNameComponentNameProvided() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			int totalLogs=20, totalArtifacts = 20;
			
			listLogsAndVerify(buildResultUUID, null, null, 
					512, connection.getTeamRepository(), totalLogs,
					null);
			
			listArtifactsAndVerify(buildResultUUID, null, null, 
					512, connection.getTeamRepository(), totalArtifacts,
					null);
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}
	
	@Test
	public void testListFilesWithMaxResults() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			int maxResults = 5;
			
			listLogsAndVerify(buildResultUUID, null, null, 
					maxResults, connection.getTeamRepository(), 5,
					new String [][] { 
						{"log-10-90.txt", "log-10-90 log"},
						{"log-11-91.txt", "log-11-91 log"},
						{"log-12-92.txt", "log-12-92 log"},
						{"log-13-93.txt", "log-13-93 log"},
						{"log-14-94.txt", "log-14-94 log"}
						});
			
			listArtifactsAndVerify(buildResultUUID, null, null, 
					maxResults, connection.getTeamRepository(), 5,
					new String [][] {
					{"artifact-10-90.txt", "artifact-10-90 artifact"},
					{"artifact-11-91.txt", "artifact-11-91 artifact"},
					{"artifact-12-92.txt", "artifact-12-92 artifact"},
					{"artifact-13-93.txt", "artifact-13-93 artifact"},
					{"artifact-14-94.txt", "artifact-14-94 artifact"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}
	
	@Test
	public void testListFilesComponentNameOnly() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			listLogsAndVerify(buildResultUUID, null, "comp1", 
					512, connection.getTeamRepository(), 3,
					new String [][] { 
						{"log-10-90.txt", "log-10-90 log"},
						{"log-11-91.txt", "log-11-91 log"},
						{"log-12-92.txt", "log-12-92 log"}
						});
			listArtifactsAndVerify(buildResultUUID, null, "comp1", 
					512, connection.getTeamRepository(), 3,
					new String [][] {
					{"artifact-10-90.txt", "artifact-10-90 artifact"},
					{"artifact-11-91.txt", "artifact-11-91 artifact"},
					{"artifact-12-92.txt", "artifact-12-92 artifact"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}

	@Test
	public void testListFilesFileNameSingleMatchOnly() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			String logFileNameToMatch = "log-10-90.txt";
			listLogsAndVerify(buildResultUUID, logFileNameToMatch, null, 
					512, connection.getTeamRepository(), 1,
					new String [][] { 
						{logFileNameToMatch, "log-10-90 log"}});
			
			String artifactFileNameToMatch = "artifact-10-90.txt";
			listArtifactsAndVerify(buildResultUUID, artifactFileNameToMatch, null, 
					512, connection.getTeamRepository(), 1,
					new String [][] {
					{artifactFileNameToMatch, "artifact-10-90 artifact"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}
	
	/**
	 * Even without a pattern, listing by filename could yield multiple  
	 * matches across components, as verified by this test case.
	 * log-18-98.txt
	 * artifact-18-98.txt
	 * @throws Exception
	 */
	@Test
	public void testListFilesFileNameMultiMatchOnly() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			String logFileNameToMatch = "log-18-98.txt";
			listLogsAndVerify(buildResultUUID, logFileNameToMatch, null, 
					512, connection.getTeamRepository(), 2,
					new String [][] { 
						{logFileNameToMatch, "log-18-98 log"},
						{logFileNameToMatch, "log-18-98 log dup"}});
			
			String artifactFileNameToMatch = "artifact-18-98.txt";
			listArtifactsAndVerify(buildResultUUID, artifactFileNameToMatch, null, 
					512, connection.getTeamRepository(), 2,
					new String [][] {
					{artifactFileNameToMatch, "artifact-18-98 artifact"},
					{artifactFileNameToMatch, "artifact-18-98 artifact dup"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}

	@Test
	public void testListFilesFileNameMultiMatchWithinComponent() throws Exception {
		// Given a file name that can match across components, providing 
		// a component name restricts the result set
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			String componentToMatch = "comp4";
			String logFileNameToMatch = "log-18-98.txt";
			listLogsAndVerify(buildResultUUID, 
					logFileNameToMatch, componentToMatch, 
					512, connection.getTeamRepository(), 1,
					new String [][] { 
						{logFileNameToMatch, "log-18-98 log dup"}});
			
			String artifactFileNameToMatch = "artifact-18-98.txt";
			listArtifactsAndVerify(buildResultUUID, 
					artifactFileNameToMatch, componentToMatch, 
					512, connection.getTeamRepository(), 1,
					new String [][] {
					{artifactFileNameToMatch, "artifact-18-98 artifact dup"}});
			
			// However, if a filename has multiple entries in the same 
			// component, providing a component name makes no difference 
			// to that component's entries but restricts entries from other component
			componentToMatch = "comp4";
			logFileNameToMatch = "log-19-99.txt";
			listLogsAndVerify(buildResultUUID, 
					logFileNameToMatch, componentToMatch, 
					512, connection.getTeamRepository(), 2,
					new String [][] { 
						{logFileNameToMatch, "log-19-99 log dup"},
						{logFileNameToMatch, "log-19-99 log dup 1"}});
			
			artifactFileNameToMatch = "artifact-19-99.txt";
			listArtifactsAndVerify(buildResultUUID, 
					artifactFileNameToMatch, componentToMatch, 
					512, connection.getTeamRepository(), 2,
					new String [][] {
					{artifactFileNameToMatch, "artifact-19-99 artifact dup"},
					{artifactFileNameToMatch, "artifact-19-99 artifact dup 1"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}
	
	@Test
	public void testListFilesFileNamePatternMultiMatchNoComponent() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
	
			String logFileNameToMatch = "log-10.*.txt";
			int maxResults = 4;
			listLogsAndVerify(buildResultUUID, logFileNameToMatch, null, 
					maxResults, connection.getTeamRepository(), maxResults,
					new String [][] { 
						{"log-10-90.txt", "log-10-90 log"},
						{"log-100-900.txt", "log-100-900 log"},
						{"log-101-901.txt", "log-101-901 log"},
						{"log-102-902.txt", "log-102-902 log"}
			});
			
			// With default maxResults (512)
			listLogsAndVerify(buildResultUUID, logFileNameToMatch, null, 
					512, connection.getTeamRepository(), 6,
					new String [][] { 
						{"log-10-90.txt", "log-10-90 log"},
						{"log-100-900.txt", "log-100-900 log"},
						{"log-101-901.txt", "log-101-901 log"},
						{"log-102-902.txt", "log-102-902 log"},
						{"log-103-903.txt", "log-103-903 log"},
						{"log-104-904.txt", "log-104-904 log"}
			});
			
			String artifactFileNameToMatch = "artifact-10.*.txt";
			maxResults = 4;
			listArtifactsAndVerify(buildResultUUID, artifactFileNameToMatch, null, 
					maxResults, connection.getTeamRepository(), maxResults,
					new String [][] {
					{"artifact-10-90.txt", "artifact-10-90 artifact"},
					{"artifact-100-900.txt", "artifact-100-900 artifact"},
					{"artifact-101-901.txt", "artifact-101-901 artifact"},
					{"artifact-102-902.txt", "artifact-102-902 artifact"}});
			
			// With default maxResults, we should have 6 files
			listArtifactsAndVerify(buildResultUUID, artifactFileNameToMatch, null, 
					512, connection.getTeamRepository(), 6,
					new String [][] {
					{"artifact-10-90.txt", "artifact-10-90 artifact"},
					{"artifact-100-900.txt", "artifact-100-900 artifact"},
					{"artifact-101-901.txt", "artifact-101-901 artifact"},
					{"artifact-102-902.txt", "artifact-102-902 artifact"},
					{"artifact-103-903.txt", "artifact-103-903 artifact"},
					{"artifact-104-904.txt", "artifact-104-904 artifact"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}
	
	@Test
	public void testListFilesFileNamePatternMultiMatchWithComponent() throws Exception {
		// List only the files narrowed down to the component
		// Since component name is provided, even though files match from 
		// other components, they will not be considered
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		try {
			String componentToMatch="comp1";
			String logFileNameToMatch = "log-10.*.txt";
			listLogsAndVerify(buildResultUUID, 
					logFileNameToMatch, componentToMatch, 
					512, connection.getTeamRepository(), 1,
					new String [][] { 
						{"log-10-90.txt", "log-10-90 log"}});
			
			String artifactFileNameToMatch = "artifact-10.*.txt";
			listArtifactsAndVerify(buildResultUUID, 
					artifactFileNameToMatch, componentToMatch, 
					512, connection.getTeamRepository(), 1,
					new String [][] {
					{"artifact-10-90.txt", "artifact-10-90 artifact"}});
		} finally {
			if (scratchFolder != null) {
				scratchFolder.delete();
			}
		}
	}
	

	@Test
	public void testListFilesFileNameNoMatch() throws Exception {
		String logFileName = "log-200-200.txt";
		String artifactFileName = "artifact-200-200.txt";
		String componentName = null;
		helperTestListFilesNoMatchScenario(logFileName, artifactFileName, componentName);
	}
	
	@Test
	public void testListFilesComponentNameNoMatch() throws Exception {
		String logFileName = null;
		String artifactFileName = null;
		String componentName = "comp300";
		helperTestListFilesNoMatchScenario(logFileName, artifactFileName, componentName);
	}
	
	@Test
	public void testListFilesFileNameMatchComponentNameNoMatch() throws Exception {
		String logFileName = "log-10-90.txt";
		String componentName = "comp300";
		String artifactFileName = "artifact-10-90.txt";
		helperTestListFilesNoMatchScenario(logFileName, artifactFileName, componentName);
	}

	@Test
	public void testListFilesNoFileNameMatchComponentNameMatch() throws Exception {
		String logFileName = "log-200-200.txt";
		String componentName = "comp3";
		String artifactFileName = "artifact-200-200.txt";
		helperTestListFilesNoMatchScenario(logFileName, artifactFileName, componentName);
	}

	@Test
	public void testListFilesFileNameComponentNameNoMatch() throws Exception {
		String logFileName = "log-200-200.txt";
		String componentName = "comp3";
		String artifactFileName = "artifact-200-200.txt";
		helperTestListFilesNoMatchScenario(logFileName, artifactFileName, componentName);
	}
	
	@Test
	public void testZZZ() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		testClient.tearDown(connectionDetails, setupArtifacts, getProgressMonitor());
	}
	
	private void validateFileInfo(List<String> fileInfo, String fileName, String label) {
		Assert.assertEquals(fileName, fileInfo.get(0));
		Assert.assertEquals(label, fileInfo.get(2));
	}
	
	private NullProgressMonitor getProgressMonitor() {
		return new NullProgressMonitor();
	}
	
	private <E extends Exception> void buildResultParamValidationHelper
				(FunctionWithException<String, Object, E> f) throws Exception {
			// Tests with null, empty UUID
		for (String buildResultUUID : new String[] {null, ""}) {
			
			try {
				f.apply(buildResultUUID);
			} catch (Exception exp) {
				Assert.assertTrue(exp instanceof RTCConfigurationException);
				Assert.assertEquals(
						Messages.getDefault().RTCBuildUtils_build_result_id_is_null(), 
								exp.getMessage());
			}
		}
		
		// Invalid UUID 
		try {
			f.apply("abcd");
		} catch (Exception exp) {
			Assert.assertTrue(exp instanceof IllegalArgumentException);
			Assert.assertEquals(
					Messages.getDefault().RTCBuildUtils_build_result_UUID_invalid("abcd"), 
							exp.getMessage());
		}
		
		{
			String nonExistentBuidResultUUID = UUID.generate().getUuidValue();
			// Non existent build result UUID 
			try {
				f.apply(nonExistentBuidResultUUID);
			} catch (Exception exp) {
				Assert.assertTrue(exp instanceof RTCConfigurationException);
				Assert.assertEquals(
						Messages.getDefault().RTCBuildUtils_build_result_id_not_found(
								nonExistentBuidResultUUID), exp.getMessage());
			}
		}
	}

	private String setupBuildResult(RepositoryConnection connection,
				Map<String, String> setupArtifacts) throws Exception {
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		setupArtifacts = BuildUtil.setupBuildResultWithLogsAndArtifacts(connection, 
								buildDefinitionId, scratchFolder.getRoot().getAbsolutePath());
		String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.
									ARTIFACT_BUILD_RESULT_ITEM_ID);
		return buildResultUUID;
	}
	
	private void listArtifactsAndVerify(String buildResultUUID, String fileName,
			String componentName, int maxResults, ITeamRepository repository,
			int contentSize, String [][] contentToVerify) throws Exception {
		ConsoleOutputHelper consoleOutput = new ConsoleOutputHelper();

		// list artifacts from comp1, expect 3 files to be returned
		Map<String, Object> retMap = RTCBuildUtils.getInstance().
				listFiles(buildResultUUID, fileName, componentName, 
				Constants.RTCBuildUtils_ARTIFACT_TYPE_KEY, 
				maxResults, repository, 
				consoleOutput, Locale.getDefault(), getProgressMonitor());
		@SuppressWarnings("unchecked")
		List<List<String>> fileInfos = (List<List<String>>) 
							retMap.get(Constants.RTCBuildUtils_FILEINFOS_KEY);
		Assert.assertEquals(contentSize, fileInfos.size());
		
		// Sometimes, we don't want to verify the content.
		if (contentToVerify == null) {
			return;
		}
		for (int i = 0; i < contentToVerify.length; i++) {
			validateFileInfo(fileInfos.get(i), 
					contentToVerify[i][0], contentToVerify[i][1]);
		}
	}

	private void listLogsAndVerify(String buildResultUUID, String fileName, 
			String componentName, int maxResults, ITeamRepository repository, 
			int contentSize, String [][] contentToVerify) throws Exception {
		ConsoleOutputHelper consoleOutput = new ConsoleOutputHelper();
		
		// list logs from comp1, expect 3 files to be returned
		Map<String,Object> retMap = RTCBuildUtils.getInstance().
				listFiles(buildResultUUID, fileName, 
				componentName, Constants.RTCBuildUtils_LOG_TYPE_KEY, 
				maxResults, repository,	consoleOutput, 
				Locale.getDefault(), getProgressMonitor());
		
		@SuppressWarnings("unchecked")
		List<List<String>> fileInfos = 
					(List<List<String>>) retMap.get(Constants.RTCBuildUtils_FILEINFOS_KEY);
		
		Assert.assertEquals(contentSize, fileInfos.size());

		// Sometimes, we don't want to verify the content.
		if (contentToVerify == null) {
			return;
		}
		for (int i = 0; i < contentToVerify.length; i++) {
			validateFileInfo(fileInfos.get(i), 
					contentToVerify[i][0], contentToVerify[i][1]);
		}
	}
	
	@FunctionalInterface
	interface FunctionWithException<T,R, E extends Exception> {
		R apply(T t) throws E;
	}
	
	private void helperTestListFilesNoMatchScenario(String logFileName,
			String artifactFileName, String componentName) throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());

		listLogsAndVerify(buildResultUUID, 
				logFileName, componentName, 
				512, connection.getTeamRepository(), 0, null);
		
		listArtifactsAndVerify(buildResultUUID, 
				artifactFileName, componentName,
				512, connection.getTeamRepository(), 0,
				null);
	}
}
