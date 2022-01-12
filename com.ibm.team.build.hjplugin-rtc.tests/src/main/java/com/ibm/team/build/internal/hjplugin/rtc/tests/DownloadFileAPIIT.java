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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
import com.ibm.team.build.internal.hjplugin.rtc.tests.RTCBuildUtilsIT.FunctionWithException;
import com.ibm.team.build.internal.hjplugin.rtc.tests.utils.Config;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DownloadFileAPIIT {
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	
	@Rule
	public TemporaryFolder sandbox = new TemporaryFolder();

	@Rule
	public TemporaryFolder scratchFolder = new TemporaryFolder();

	ConsoleOutputHelper consoleOutput = new ConsoleOutputHelper();

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
	public void testDownloadFileBuildResultUUIDValidation() throws Exception {
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
				RTCBuildUtils.getInstance().downloadFile(buildResultUUID, 
				    "test", "test", "", "artifact", sandbox.getRoot().getAbsolutePath(),
				    "test", connection.getTeamRepository(), 
				    listener, Locale.getDefault(), getProgressMonitor());
				return null;
			}
		});
	}
	
	@Test
	public void testDownloadFileFileNameAndContentIdParamValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
						userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper listener = new ConsoleOutputHelper();
		
		// Provide both contentId and fileName
		try {
			RTCBuildUtils.getInstance().downloadFile(UUID.generate().getUuidValue(), 
				    null, "test", null, "artifact", sandbox.getRoot().getAbsolutePath(),
				    "test", connection.getTeamRepository(), 
				    listener, Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					Messages.getDefault().RTCBuiltUtils_fileNameContentId_null(),
					exp.getMessage());		}
		
		// Provide neither contentId and fileName
		try {
			RTCBuildUtils.getInstance().downloadFile(UUID.generate().getUuidValue(), 
				    "test", "test", UUID.generate().getUuidValue(), 
				    "artifact", sandbox.getRoot().getAbsolutePath(),
				    "test", connection.getTeamRepository(), 
				    listener, Locale.getDefault(), getProgressMonitor());
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					Messages.getDefault().RTCBuiltUtils_fileNameContentId_nonnull(),
					exp.getMessage());
		}
	}
	
	@Test
	public void testDownloadFileDestinationFolderValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper consoleOutput = new ConsoleOutputHelper();
		for (String destinationFolder : new String [] { null, ""}) {
			try {
				RTCBuildUtils.getInstance().downloadFile(UUID.generate().getUuidValue(), 
						"x", "y", null, 
						"artifact", destinationFolder, 
						"testfileName", connection.getTeamRepository(),
						 consoleOutput, Locale.getDefault(), getProgressMonitor());
			} catch (RTCConfigurationException exp) {
				Assert.assertEquals(Messages.getDefault().
						RTCBuildUtils_destination_folder_null(),
						 exp.getMessage());
			}
		}
	}
	
	@Test
	public void testDownloadFileDestinationFileNameValidation() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		// Destination file name contains OS' file or path separator
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		ConsoleOutputHelper consoleOutput = new ConsoleOutputHelper();
		for (String fileName : new String [] { "test" + File.separator + "cde",
				"test" + File.pathSeparator + "cde"}) {
			try {
				RTCBuildUtils.getInstance().downloadFile(UUID.generate().getUuidValue(), 
						"x", "y", null, 
						"artifact", sandbox.getRoot().getAbsolutePath(), 
						 fileName, connection.getTeamRepository(),
						 consoleOutput, Locale.getDefault(), getProgressMonitor());
			} catch (IOException exp) {
				Assert.assertEquals(Messages.getDefault().
						 RTCBuildUtils_destinationFileName_is_a_path(fileName),
						 exp.getMessage());
			}
		}
	}
	
	
	@Test
	public void testDownloadFileFileNameSingleMatch() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download Log and verify
		String fileName = "log-10-90.txt";
		downloadLogAndVerify(buildResultUUID, fileName, null, null, 
				null, connection.getTeamRepository(), new String[] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				"log-10-90 log"
				});
		
		// Download artifact
		fileName = "artifact-10-90.txt";
		downloadArtifactAndVerify(buildResultUUID, fileName, null, null,
				null,connection.getTeamRepository(), new String [] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				"artifact-10-90 artifact"});
	}
	

	/**
	 * There are multiple contributions with name "log-18-98.txt" and "artifact-18-89.txt"
	 * When no component name is specified, the first one gets downloaded.
	 * @throws Exception
	 */
	@Test
	public void testDownloadFileFileNameMultipleMatchNoComponent() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download Log and verify
		String fileName = "log-18-98.txt";
		downloadLogAndVerify(buildResultUUID, fileName, null, null, 
				null, connection.getTeamRepository(), new String[] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				"log-18-98 log"
				});
		
		// Download artifact
		fileName = "artifact-18-98.txt";
		downloadArtifactAndVerify(buildResultUUID, fileName, null, null,
				null,connection.getTeamRepository(), new String [] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				"artifact-18-98 artifact"});
	}
	
	
	/**
	 * File log-13-93.txt and artifact-13-93.txt are present in one component 
	 * only.
	 * @throws Exception
	 */
	@Test
	public void testDownloadFileFileNameSingleMatchWithinComponent() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download Log and verify
		String fileName = "log-13-93.txt";
		String componentName = "comp2";
		downloadLogAndVerify(buildResultUUID, fileName, componentName, null, 
				null, connection.getTeamRepository(), new String[] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				"log-13-93 log"
				});
		
		// Download artifact
		fileName = "artifact-13-93.txt";
		componentName = "comp2";
		downloadArtifactAndVerify(buildResultUUID, fileName, componentName, null,
				null,connection.getTeamRepository(), new String [] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				"artifact-13-93 artifact"});
	}


	/**
	 * There are multiple contributions with name "log-18-98.txt" and "artifact-18-98.txt"
	 * When a component name is specified, the specific file gets downloaded
	 * 
	 * @throws Exception
	 */
	@Test
	public void testDownloadFileFileNameMultiMatchAcrossComponents() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download Log and verify
		String fileName = "log-18-98.txt";
		String componentName = "comp4";
		String logFileContent = "log-18-98 log dup";
		downloadLogAndVerify(buildResultUUID, fileName, componentName, null, 
				null, connection.getTeamRepository(), new String[] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				logFileContent
				});
		
		// Download artifact
		fileName = "artifact-18-98.txt";
		componentName = "comp4";
		String downloadFileContent = "artifact-18-98 artifact dup";
		downloadArtifactAndVerify(buildResultUUID, fileName, componentName, null,
				null,connection.getTeamRepository(), new String [] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				downloadFileContent});
	}
	
	/**
	 * There are multiple contributions with name "log-19-99.txt" and "artifact-19-99.txt" 
	 * within the same component.
	 * Even if a component name is specified, only the first file gets downloaded.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testDownloadFileFileNameMultiMatchWithinComponent() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download Log and verify
		String fileName = "log-19-99.txt";
		String componentName = "comp4";
		String logFileContent = "log-19-99 log dup";
		downloadLogAndVerify(buildResultUUID, fileName, componentName, null, 
				null, connection.getTeamRepository(), new String[] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				logFileContent
				});
		
		// Download artifact
		fileName = "artifact-19-99.txt";
		componentName = "comp4";
		String downloadFileContent = "artifact-19-99 artifact dup";
		downloadArtifactAndVerify(buildResultUUID, fileName, componentName, null,
				null,connection.getTeamRepository(), new String [] {
				fileName, 
				new File(sandbox.getRoot(), fileName).getAbsolutePath(),
				downloadFileContent});
	}

	@Test
	public void testDownloadFileContentID() throws Exception {
		
	}
	
	@Test
	public void testDownloadFileNoMatchWithFileName() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download non existent log contribution
		String fileName = "log-2000-200.txt";
		String componentName = null;
		String logFileContent = "log-19-99 log dup";
		try {
			downloadLogAndVerify(buildResultUUID, fileName, componentName, null, 
					null, connection.getTeamRepository(), new String[] {
					fileName, 
					new File(sandbox.getRoot(), fileName).getAbsolutePath(),
					logFileContent
					});
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					String.format("No log file contribution with "
							+ "value \"%s\" for property \"%s\" was found.",
							fileName, "fileName"),
					exp.getMessage());
		}
		
		// Download non existent artifact contribution
		fileName = "artifact-2000-200.txt";
		componentName = "";
		String downloadFileContent = "artifact-19-99 artifact dup";
		try {
			downloadArtifactAndVerify(buildResultUUID, fileName, componentName, null,
					null,connection.getTeamRepository(), new String [] {
					fileName, 
					new File(sandbox.getRoot(), fileName).getAbsolutePath(),
					downloadFileContent});
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					String.format("No artifact (download) file contribution with "
							+ "value \"%s\" for property \"%s\" was found.",
							fileName, "fileName"),
					exp.getMessage());
		}
	}
	
	/**
	 * There is a valid log or artifact contribution but it is not present in the 
	 * specified component name
	 * @throws Exception
	 */
	@Test
	public void testDownloadFileValidFileNameButNoMatchForComponentName() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download non existent log contribution in a specific compnent
		String fileName = "log-10-90.txt";
		String componentName = "comp3";
		String logFileContent = "log-10-90 log";
		try {
			downloadLogAndVerify(buildResultUUID, fileName, componentName, null, 
					null, connection.getTeamRepository(), new String[] {
					fileName, 
					new File(sandbox.getRoot(), fileName).getAbsolutePath(),
					logFileContent
					});
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					String.format("No log file contribution with "
							+ "value \"%s\" for property \"%s\" "
							+ "in component \"%s\" was found.",
							fileName, "fileName", componentName),
					exp.getMessage());
		}
		
		// Download non existent artifact contribution
		fileName = "artifact-10-90.txt";
		componentName = "comp3";
		String downloadFileContent = "artifact-10-90 artifact";
		try {
			downloadArtifactAndVerify(buildResultUUID, fileName, componentName, null,
					null,connection.getTeamRepository(), new String [] {
					fileName, 
					new File(sandbox.getRoot(), fileName).getAbsolutePath(),
					downloadFileContent});
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					String.format("No artifact (download) file contribution with "
							+ "value \"%s\" for property \"%s\" "
							+ "in component \"%s\" was found.",
							fileName, "fileName", componentName),
					exp.getMessage());
		}
	}

	@Test
	public void testDownloadFileNoMatchForContentID() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());

		// Download non existent log contribution
		String fileName = "";
		String componentName = "";
		String contentId = UUID.generate().getUuidValue();
		String logFileContent = "log-10-90 log";
		try {
			downloadLogAndVerify(buildResultUUID, fileName, componentName, 
					contentId, null, connection.getTeamRepository(), 
					new String[] {
						fileName, 
						new File(sandbox.getRoot(), fileName).getAbsolutePath(),
						logFileContent
					});
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					String.format("No log file contribution with "
							+ "value \"%s\" for property \"%s\" "
							+ "was found.",
							contentId, "contentId"),
					exp.getMessage());
		}
		
		// Download non existent artifact contribution
		fileName = "";
		componentName = "";
		contentId = UUID.generate().getUuidValue();
		String downloadFileContent = "artifact-10-90 artifact";
		try {
			downloadArtifactAndVerify(buildResultUUID, fileName, componentName,
					contentId, null,connection.getTeamRepository(), 
					new String [] {
							fileName, 
							new File(sandbox.getRoot(), fileName).getAbsolutePath(),
							downloadFileContent}
					);
		} catch (RTCConfigurationException exp) {
			Assert.assertEquals(
					String.format("No artifact (download) file contribution with "
							+ "value \"%s\" for property \"%s\" "
							+ "was found.",
							contentId, "contentId"),
					exp.getMessage());
		}
	}
	
	@Test
	public void testDownloadFileDestinationFilenameNotNullAndNonExistent() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download Log and verify
		String fileName = "log-10-90.txt";
		String destinationFileName = "testMeLogFile";
		downloadLogAndVerify(buildResultUUID, fileName, null, null, 
				destinationFileName, connection.getTeamRepository(), new String[] {
				destinationFileName, 
				new File(sandbox.getRoot(), destinationFileName).getAbsolutePath(),
				"log-10-90 log"
				});
		
		// Download artifact
		fileName = "artifact-10-90.txt";
		destinationFileName = "testMeArtifactFile";
		downloadArtifactAndVerify(buildResultUUID, fileName, null, null,
				destinationFileName, connection.getTeamRepository(), new String [] {
				destinationFileName, 
				new File(sandbox.getRoot(), destinationFileName).getAbsolutePath(),
				"artifact-10-90 artifact"});
	}
	
	@Test
	public void testDownloadLogDestinationFileNameNullAndExists() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Log
		{
			final String logFileName = "log-10-90.txt";
			
			// First create a file in sandbox with the specific log file name
			File logFile = new File(sandbox.getRoot(), logFileName);
			logFile.createNewFile();
	
			Assert.assertEquals(1, sandbox.getRoot().listFiles().length);
			
			// Download Log and verify that there are two files in the folder
			downloadLogAndVerify2(buildResultUUID, logFileName, null,
					new FunctionWithException<File, File, Exception>() {
						
						@Override
						public File apply(File t) throws Exception {
							File [] children = t.listFiles();
							for (File child : children) {
								if (!child.getName().equals(logFileName)) {
									return child;
								}
							}
							return null;
						}
					},
					connection.getTeamRepository(), 
					"log-10-90 log");
			Assert.assertEquals(2, sandbox.getRoot().listFiles().length);
		}
	}
	
	@Test
	public void testDownloadArtifactDestinationFileNameNullAndExists() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download
		{
			final String artifactFileName = "artifact-10-90.txt";
	
			// First create a file in sandbox with the specific log file name
			File artifactFile = new File(sandbox.getRoot(), artifactFileName);
			artifactFile.createNewFile();
	
			Assert.assertEquals(1, sandbox.getRoot().listFiles().length);
			// Download artifact and verify that there are two files in the folder
			downloadArtifactAndVerify2(buildResultUUID, artifactFileName, 
					null,
					new FunctionWithException<File, File, Exception>() {
						
						@Override
						public File apply(File t) throws Exception {
							File [] children = t.listFiles();
							for (File child : children) {
								if (!child.getName().equals(artifactFileName)) {
									return child;
								}
							}
							return null;
						}
					},
					connection.getTeamRepository(), 
					"artifact-10-90 artifact");
	
			Assert.assertEquals(2, sandbox.getRoot().listFiles().length);
		}
	}

	@Test
	public void testDownloadLogDestinationFileNameNotNullAndExists() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Log
		{
			final String logFileName = "log-10-90.txt";
			final String destinationFileName = "testmefileLog";
			// First create a file in sandbox with the specific destinationFileName
			File logFile = new File(sandbox.getRoot(), destinationFileName);
			logFile.createNewFile();
	
			Assert.assertEquals(1, sandbox.getRoot().listFiles().length);
			
			// Download Log and verify that there are two files in the folder
			downloadLogAndVerify2(buildResultUUID, logFileName, destinationFileName,
					new FunctionWithException<File, File, Exception>() {
						
						@Override
						public File apply(File t) throws Exception {
							File [] children = t.listFiles();
							for (File child : children) {
								if (!child.getName().equals(destinationFileName)) {
									return child;
								}
							}
							return null;
						}
					},
					connection.getTeamRepository(), 
					"log-10-90 log");
			Assert.assertEquals(2, sandbox.getRoot().listFiles().length);
		}
	}
	

	@Test
	public void testDownloadArtifactDestinationFileNameNotNullAndExists() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Download
		{

			final String artifactFileName = "artifact-10-90.txt";
			final String destinationFileName = "testmefileArtifact";
			
			// First create a file in sandbox with the specific log file name
			File artifactFile = new File(sandbox.getRoot(), destinationFileName);
			artifactFile.createNewFile();
	
			Assert.assertEquals(1, sandbox.getRoot().listFiles().length);
			// Download artifact and verify that there are two files in the folder
			downloadArtifactAndVerify2(buildResultUUID, artifactFileName, 
					destinationFileName,
					new FunctionWithException<File, File, Exception>() {
						
						@Override
						public File apply(File t) throws Exception {
							File [] children = t.listFiles();
							for (File child : children) {
								if (!child.getName().equals(destinationFileName)) {
									return child;
								}
							}
							return null;
						}
					},
					connection.getTeamRepository(), 
					"artifact-10-90 artifact");
			Assert.assertEquals(2, sandbox.getRoot().listFiles().length);
		}
	}
	
	@Test
	public void testDownloadFileDestinationFileCannotBeCreated() throws Exception {
		TestSetupTearDownUtil testClient = getTestSetupTearDownUtil();
		ConnectionDetails connectionDetails = testClient.getConnectionDetails(serverURI, 
				userId, password, timeout);
		RepositoryConnection connection = testClient.getRepositoryConnection(connectionDetails);
		connection.ensureLoggedIn(getProgressMonitor());
		
		// Delete the sandbox folder
		sandbox.getRoot().delete();
		
		try {
			// Download Log and verify
			String fileName = "log-10-90.txt";
			String destinationFileName = "testMeLogFile";
			downloadLogAndVerify(buildResultUUID, fileName, null, null, 
					destinationFileName, connection.getTeamRepository(), new String[] {
					destinationFileName, 
					new File(sandbox.getRoot(), destinationFileName).getAbsolutePath(),
					"log-10-90 log"
					});
		
		} catch (IOException exp) {
			Assert.assertTrue(exp.getMessage(), exp.getMessage().contains("No such file or directory"));
		}
		try {
			// Download artifact
			String fileName = "artifact-10-90.txt";
			String destinationFileName = "testMeArtifactFile";
			downloadArtifactAndVerify(buildResultUUID, fileName, null, null,
					destinationFileName, connection.getTeamRepository(), new String [] {
					destinationFileName, 
					new File(sandbox.getRoot(), destinationFileName).getAbsolutePath(),
					"artifact-10-90 artifact"});

		} catch (IOException exp) {
			Assert.assertTrue(exp.getMessage(), exp.getMessage().contains("No such file or directory"));
		}
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
	
	private String setupBuildResult(RepositoryConnection connection,
				Map<String, String> setupArtifacts) throws Exception {
		String buildDefinitionId = TestUtils.getBuildDefinitionUniqueName();
		setupArtifacts = BuildUtil.setupBuildResultWithLogsAndArtifacts(connection, 
								buildDefinitionId, scratchFolder.getRoot().getAbsolutePath());
		String buildResultUUID = setupArtifacts.get(TestSetupTearDownUtil.
									ARTIFACT_BUILD_RESULT_ITEM_ID);
		return buildResultUUID;
	}
	
	private <E extends Exception> 
	void buildResultParamValidationHelper(FunctionWithException<String, Object, E> f) throws Exception {
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
	
	private NullProgressMonitor getProgressMonitor() {
		return new NullProgressMonitor();
	}
	
	private void downloadArtifactAndVerify2(String buildResultUUID, String fileName,
			String destinationFileName, 
			FunctionWithException<File, File, Exception> destinationFileGetter,
			ITeamRepository repository, String contentToVerify) throws Exception {
		// Download log
		Map<String, String> retVal = RTCBuildUtils.getInstance().downloadFile(buildResultUUID, 
						fileName, null, null, Constants.RTCBuildUtils_ARTIFACT_TYPE_KEY, 
						sandbox.getRoot().getAbsolutePath(), destinationFileName, repository, 
						consoleOutput, Locale.getDefault(), getProgressMonitor());
		
		File destination = destinationFileGetter.apply(sandbox.getRoot());
		Assert.assertEquals(destination.getName(), retVal.get(Constants.RTCBuildUtils_FILENAME_KEY));
		Assert.assertEquals(destination.getCanonicalPath(), retVal.get(Constants.RTCBuildUtils_FILEPATH_KEY));
		
		List<String> lines = Files.readAllLines(destination.toPath(), Charset.forName("utf-8"));
		Assert.assertEquals(1, lines.size());
		Assert.assertEquals(contentToVerify, lines.get(0));
	}
	
	private void downloadLogAndVerify2(String buildResultUUID, String fileName,
			String destinationFileName,
			FunctionWithException<File, File, Exception> destinationFileGetter,
			ITeamRepository repository, String contentToVerify) throws Exception {
		// Download log
		Map<String, String> retVal = RTCBuildUtils.getInstance().downloadFile(buildResultUUID, 
						fileName, null, null, Constants.RTCBuildUtils_LOG_TYPE_KEY, 
						sandbox.getRoot().getAbsolutePath(), destinationFileName, repository, 
						consoleOutput, Locale.getDefault(), getProgressMonitor());
		
		File destination = destinationFileGetter.apply(sandbox.getRoot());
		Assert.assertEquals(destination.getName(), retVal.get(Constants.RTCBuildUtils_FILENAME_KEY));
		Assert.assertEquals(destination.getCanonicalPath(), retVal.get(Constants.RTCBuildUtils_FILEPATH_KEY));
		
		List<String> lines = Files.readAllLines(destination.toPath(), Charset.forName("utf-8"));
		Assert.assertEquals(1, lines.size());
		Assert.assertEquals(contentToVerify, lines.get(0));
	}
	
	private void downloadLogAndVerify(String buildResultUUID, String fileName,
			String componentName, String contentId, String destinationFileName,
			ITeamRepository repository, String [] contentToVerify) throws Exception {
		// Download log
		Map<String, String> retVal = RTCBuildUtils.getInstance().downloadFile(buildResultUUID, 
						fileName, componentName, contentId, Constants.RTCBuildUtils_LOG_TYPE_KEY, 
						sandbox.getRoot().getAbsolutePath(), destinationFileName, repository, 
						consoleOutput, Locale.getDefault(), getProgressMonitor());
		Assert.assertEquals(contentToVerify[0], retVal.get(Constants.RTCBuildUtils_FILENAME_KEY));
		Assert.assertEquals(contentToVerify[1], retVal.get(Constants.RTCBuildUtils_FILEPATH_KEY));
		
		File f= new File(contentToVerify[1]);
		List<String> lines = Files.readAllLines(f.toPath(), Charset.forName("utf-8"));
		Assert.assertEquals(1, lines.size());
		Assert.assertEquals(contentToVerify[2], lines.get(0));
	}
	
	private void downloadArtifactAndVerify(String buildResultUUID, String fileName,
			String componentName, String contentId, String destinationFileName,
			ITeamRepository repository,String [] contentToVerify) throws Exception {
		// Download log
		Map<String, String> retVal = RTCBuildUtils.getInstance().downloadFile(buildResultUUID, 
						fileName, componentName, contentId, Constants.RTCBuildUtils_ARTIFACT_TYPE_KEY, 
						sandbox.getRoot().getAbsolutePath(), destinationFileName, repository, 
						consoleOutput, Locale.getDefault(), getProgressMonitor());
		Assert.assertEquals(contentToVerify[0], retVal.get(Constants.RTCBuildUtils_FILENAME_KEY));
		Assert.assertEquals(contentToVerify[1], retVal.get(Constants.RTCBuildUtils_FILEPATH_KEY));
		
		File f= new File(contentToVerify[1]);
		List<String> lines = Files.readAllLines(f.toPath(), Charset.forName("utf-8"));
		Assert.assertEquals(1, lines.size());
		Assert.assertEquals(contentToVerify[2], lines.get(0));
	}
}
