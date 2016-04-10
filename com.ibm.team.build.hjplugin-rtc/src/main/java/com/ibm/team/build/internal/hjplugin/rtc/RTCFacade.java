/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.repository.common.TeamRepositoryException;

/**
 * High-level facade for all things RTC.  Any requests that need to interface with RTC must go through here.
 * 
 * The API may only refer to plain Java types, not Hudson/Jenkins/Jazz/Eclipse types.
 * If you must pass through a more complex type in the API then supply it as an Object and use reflection to
 * access it. Not an ideal scenario, so avoid it if possible.
 * 
 * To signal cancellation {@link InterruptedException} is thrown. RTC reports it through {@link OperationCanceledException}
 * sometimes its embedded within the {@link TeamRepositoryException} or subclass exceptions, so some translation of the exception
 * is required.
 */
public class RTCFacade {

    private static final Logger LOGGER = Logger.getLogger(RTCFacade.class.getName());

	private AbstractBuildClient fBuildClient;
	
	private synchronized AbstractBuildClient getBuildClient() {
		if (fBuildClient == null) {
			 fBuildClient = new BuildClient();
		}
		return fBuildClient;
	}
	
	/**
	 * Logs into the repository to test the connection. Essentially exercises the configuration parameters supplied.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param clientLocale The locale of the requesting client
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public String testConnection(String serverURI, String userId, String password, int timeout, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient();
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor);
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}
	
	/**
	 * Logs into the repository to test the connection and validates the RTC build workspace is valid for use.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildWorkspace The name of the RTC build workspace
	 * @param clientLocale The locale of the requesting client
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public String testBuildWorkspace(String serverURI, String userId, String password, int timeout, String buildWorkspace, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testBuildWorkspace(buildWorkspace, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}
	
	public String testBuildStream(String serverURI, String userId, String password, int timeout, String buildStream, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient();
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testBuildStream(buildStream, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException exp) {
			errorMessage = exp.getMessage();
		} catch (RTCValidationException exp) {
			errorMessage = exp.getMessage();
		} catch (OperationCanceledException exp) {
			throw Utils.checkForCancellation(exp);
		} catch (TeamRepositoryException exp) {
			throw Utils.checkForCancellation(exp);
		}
		return errorMessage;
	}
	
	/**
	 * Logs into the repository to test the connection and validates the RTC build definition is valid for use.
	 * password first.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name of the RTC build definition
	 * @param clientLocale The locale of the requesting client
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public String testBuildDefinition(String serverURI, String userId, String password, int timeout, String buildDefinition, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testBuildDefinition(buildDefinition, monitor.newChild(50), clientLocale);
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}

	/**
	 * Checks to see if there are incoming changes for the RTC build workspace (meaning a build is needed).
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name (id) of the build definition that describes the build workspace.
	 * May be <code>null</code> if a buildWorkspace is supplied. Only one of buildWorkspace/buildDefinition
	 * should be supplied.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if a
	 * buildDefinition is supplied. Only one of buildWorkspace/buildDefinition
	 * should be supplied.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @param ignoreOutgoingFromBuildWorkspace if true, then ignore any outgoing changes from build workspace
	 * @return Returns <code>Non zero</code> if there are changes to the build workspace;
	 * <code>0</code> otherwise
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public BigInteger incomingChanges(String serverURI, String userId,
			String password, int timeout,
			String buildDefinition, String buildWorkspace, Object listener, Locale clientLocale, boolean ignoreOutgoingFromBuildWorkspace)
			throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			int value = repoConnection.incomingChanges(buildDefinition, buildWorkspace, clientConsole, monitor, clientLocale, ignoreOutgoingFromBuildWorkspace);
			return new BigInteger(Integer.toString(value));
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}
	
	public BigInteger computeIncomingChangesForStream(String serverURI, String userId,
			String password, int timeout,
			String buildStream, String streamChangesData, Object listener, Locale clientLocale)
			throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			return repoConnection.computeIncomingChangesForStream(buildStream, streamChangesData, clientConsole, monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Determines the password to use when connecting to the repository from the file.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @param clientLocale The locale of the requesting client
	 * @return The password from the file
	 * @throws Exception If no password can be determined
	 */
	public String determinePassword(File passwordFile, Locale clientLocale) throws Exception {
		AbstractBuildClient buildClient = getBuildClient();
		return buildClient.determinePassword(passwordFile, clientLocale);
	}
	
	/**
	 * Request & start an RTC Build to provide a build result
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name (id) of the build definition to create the
	 * request and result for.
	 * @param buildLabel The label to give to the RTC build
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param clientLocale The locale of the requesting client 
	 * @return The item id of the build result created
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public String createBuildResult(String serverURI,
			String userId,
			String password,
			int timeout,
			String buildDefinition,
			String buildLabel,
			Object listener, Locale clientLocale) throws Exception {
		
		IProgressMonitor monitor = getProgressMonitor();
		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		try {
			return repoConnection.createBuildResult(buildDefinition, null, buildLabel, clientConsole, monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Create links in the RTC build result back to the H/J Job and build
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The UUID for the build result to be ended.
	 * @param rootUrl Hudson/Jenkins root url if known. <code>null</code> otherwise
	 * @param projectUrl Relative link to the Hudson/Jenkins job being built
	 * @param buildUrl Relative link to the Hudson/Jenkins build
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public void createBuildLinks(String serverURI,
			String userId,
			String password,
			int timeout,
			String buildResultUUID,
			String rootUrl,
			String projectUrl,
			String buildUrl,
			Object listener) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient(); 

		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		try {
			repoConnection.createBuildLinks(buildResultUUID, rootUrl, projectUrl, buildUrl, clientConsole, monitor);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Start an RTC build requested in RTC but not started by the RTC Hudson Integration 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultInfoObject An object in which to place info about the requestor of the build.
	 * @param buildLabel The label to give to the RTC build
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception If any non-recoverable error occurs.
	 * <code>false</code> if unable to start the build (already started, no requestor, etc).
	 */
	public void startBuild(String serverURI,
			String userId,
			String password,
			int timeout,
			Object buildResultInfoObject,
			String buildLabel,
			Object listener, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IBuildResultInfo buildResultInfo = getBuildResultInfo(buildResultInfoObject);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			repoConnection.startBuild(buildResultInfo, buildLabel, clientConsole, monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Terminate an RTC build previously started by the H/J build 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The UUID for the build result to be ended.
	 * @param aborted Whether the Jenkins build was aborted
	 * @param buildState Whether the Jenkins build was a success, failure, or unstable
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public void terminateBuild(String serverURI,
			String userId,
			String password,
			int timeout,
			String buildResultUUID,
			boolean aborted, int buildState,
			Object listener, Locale clientLocale) throws Exception {
		
		SubMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			repoConnection.terminateBuild(buildResultUUID, aborted, buildState, clientConsole, monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Accept changes into the build workspace and write a description of the changes into the ChangeLogFile.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The build result to relate build results with. It also specifies the
	 * build configuration. May be <code>null</code> if buildWorkspace is supplied. Only one of
	 * buildWorkspace/buildResultUUID should be supplied.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if a
	 * buildResultUUID is supplied. Only one of buildWorkspace/buildResultUUID
	 * should be supplied.
	 * @param buildSnapshot the name of the RTC build snapshot. May be <code>null</code>
	 * @param buildStream The name of the RTC build stream. May be <code>null</code> if a
	 * buildWorkspace or buildResultUUID is supplied. Only one of buildWorkspace/buildResultUUID/buildStream
	 * should be supplied.
	 * @param hjWorkspacePath The path where the contents of the RTC workspace should be loaded.
	 * @param changeLog The file where a description of the changes made should be written. May be <code> null </code>.
	 * @param baselineSetName The name to give the snapshot created. If <code>null</code> no snapshot
	 * 				will be created.
	 * @param previousSnapshotUUID The UUID as {@link String} of the previous snapshot. Used for comparing with the new snapshot
	 *        created for buildStream case. May be <code>null</code> if buildWorkspace of buildResultUUID is supplied.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @param callConnectorTimeout user defined value for call connector timeout
	 * @param acceptBeforeLoad Accept latest changes before loading, if true
	 * @return Map<String, Object> returns a map of objects see RepositoryConnection#accept for more details.
	 * @throws Exception
	 */
	public Map<String, Object> accept(String serverURI, String userId, String password,
			int timeout, String buildResultUUID, String buildWorkspace, final String buildSnapshot,
			final String buildStream, String hjWorkspacePath, OutputStream changeLog,
			String baselineSetName, final String previousSnapshotUUID, final Object listener, 
			Locale clientLocale, String callConnectorTimeout, boolean acceptBeforeLoad) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		ChangeReport report = null;
		if (changeLog != null) {
			report = new ChangeReport(changeLog);
		}
		try	{
			return repoConnection.accept(buildResultUUID, buildWorkspace, buildSnapshot, buildStream,
					hjWorkspacePath, report, baselineSetName, previousSnapshotUUID, clientConsole, 
					monitor, clientLocale, callConnectorTimeout, acceptBeforeLoad);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Load the contents of the updated build workspace at hjWorkspacePath.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The build result to relate build results with. It also specifies the
	 * build configuration. May be <code>null</code> if buildWorkspace or buildSnapshot or buildStream is supplied. Only one of
	 * buildWorkspace/buildResultUUID/buildSnapshot/buildStream should be supplied.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if a
	 * buildResultUUID or buildSnapshot or buildStream is supplied. Only one of buildWorkspace/buildResultUUID/buildSnapshot/buildStream
	 * should be supplied.
	 * @param buildSnapshot The name or UUID of the RTC build snapshot. May be <code>null</code> if a
	 * buildResultUUID or buildWorkspace or buildStream is supplied. Only one of buildWorkspace/buildResultUUID/buildSnapshot/buildStream
	 * should be supplied.
	 * @param buildStream The name or UUID of the RTC build stream. May be <code>null</code> if a buildResultUUID or 
	 * buildWorkspace or buildSnapshot is supplied. Only one of buildWorkspace/buildResultUUID/buildSnapshot/buildStream should be supplied.
	 * @param buildStreamData The additional stream data for stream load. 
	 * @param hjWorkspacePath The path where the contents of the RTC workspace should be loaded.
	 * @param baselineSetName The name to give the snapshot created. If <code>null</code> no snapshot
	 * 				will be created.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @param parentActivityId id for parent activity under which load has to be performed.
	 * @param connectorId id to locate the connector to retrieve object created by accept call.
	 * @param isDeleteNeeded true if Jenkins job is configured to delete load directory before fetching
	 * @param createFoldersForComponents Create folders for components if true
	 * @param componentsToExclude json text representing the list of components to exclude during load
	 * @param loadRules json text representing the component to load rule file mapping
	 * @param acceptBeforeLoad Accept latest changes before loading, if true
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public void load(String serverURI, String userId, String password,
			int timeout, String buildResultUUID, String buildWorkspace,
			String buildSnapshot, String buildStream, Map<String, String> buildStreamData,
			String hjWorkspacePath,
			String baselineSetName, final Object listener, Locale clientLocale, 
			String parentActivityId, String connectorId, Object extProvider, PrintStream logger,
			boolean isDeleteNeeded, boolean createFoldersForComponents, 
			String componentsToExclude, String loadRules, boolean acceptBeforeLoad) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try	{
			repoConnection.load(buildResultUUID, buildWorkspace, buildSnapshot, buildStream, buildStreamData,
					hjWorkspacePath, baselineSetName, clientConsole, monitor, clientLocale, parentActivityId, 
					connectorId, extProvider, logger, isDeleteNeeded, createFoldersForComponents, componentsToExclude, loadRules, acceptBeforeLoad);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}
	
	/**
	 * Accept changes into the build workspace and write a description of the changes into the ChangeLogFile.
	 * Load the contents of the updated build workspace at hjWorkspacePath.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if a
	 * buildResultUUID is supplied. Only one of buildWorkspace/buildResultUUID
	 * should be supplied.
	 * @param buildResultUUID The build result to relate build results with. It also specifies the
	 * build configuration. May be <code>null</code> if buildWorkspace is supplied. Only one of
	 * buildWorkspace/buildResultUUID should be supplied.
	 * @param hjWorkspacePath The path where the contents of the RTC workspace should be loaded.
	 * @param changeLog The file where a description of the changes made should be written. May be <code> null </code>.
	 * @param baselineSetName The name to give the snapshot created. If <code>null</code> no snapshot
	 * 				will be created.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @return <code>Map<String, String></code> of build properties
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public Map<String, String> checkout(String serverURI, String userId, String password,
			int timeout, String buildResultUUID, String buildWorkspace,
			String hjWorkspacePath, OutputStream changeLog,
			String baselineSetName, final Object listener, Locale clientLocale,
			Object extProvider, PrintStream logger) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		ChangeReport report = null;
		if (changeLog != null) {
			report = new ChangeReport(changeLog);
		}
		try	{
			return repoConnection.checkout(buildResultUUID, buildWorkspace,
					hjWorkspacePath, report, baselineSetName, clientConsole, monitor, clientLocale, 
					extProvider, logger);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}
	
	/**
	 * Get details regarding build workspace name, and build definition id form buildResultUUID. 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The build result to relate build results with. This should not be null.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 * @return <code>Map<String, String></code> of build workspace name, and build definition id
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public Map<String, String> getBuildResultUUIDDetails(String serverURI, String userId, String password,
			int timeout, String buildResultUUID, final Object listener, Locale clientLocale) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try	{
			return repoConnection.getBuildResultUUIDDetails(buildResultUUID, 
					clientConsole, monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * delete the build result identified
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The UUID of the build result to delete
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @param clientLocale The locale of the requesting client
	 */
	public void deleteBuildResult(String serverURI,
							String userId,
							String password,
							int timeout,
							String buildResultUUID,
							final Object listener,
							Locale clientLocale) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try	{
			repoConnection.deleteBuildResult(buildResultUUID, clientConsole, monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		
	}

	/**
	 * Validate the list of components to exclude.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param isStreamConfiguration Flag that determines if the <code>buildWorkspace</code> corresponds to a workspace or stream
	 * @param buildWorkspace Name of the workspace configured in the build
	 * @param componentsToExclude Json text specifying the list of components to exclude during load
	 * @param clientLocale The locale of the requesting client
	 * @return an error message to display or null if there is no problem
	 * @throws Exception
	 */
	public String testComponentsToExclude(String serverURI, String userId, String password, int timeout, boolean isStreamConfiguration,
			String buildWorkspace, String componentsToExclude, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testComponentsToExclude(isStreamConfiguration, buildWorkspace, componentsToExclude, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}
	
	/**
	 * Validate the component-to-load-rule file mapping.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param isStreamConfiguration Flag that determines if the <code>buildWorkspace</code> corresponds to a workspace or a stream
	 * @param buildWorkspace Name of the workspace configured in the build
	 * @param loadRules Json text specifying the component to load rule file mapping
	 * @param clientLocale The locale of the requesting client
	 * @return an error message to display or null if there is no problem
	 * @throws Exception
	 */
	public String testLoadRules(String serverURI, String userId, String password, int timeout,
			boolean isStreamConfiguration, String buildWorkspace, String loadRules, Locale clientLocale) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testLoadRules(isStreamConfiguration, buildWorkspace, loadRules, monitor.newChild(50), clientLocale);
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}

	protected SubMonitor getProgressMonitor() {
		IProgressMonitor progress = new NullProgressMonitor() {

			@Override
			public boolean isCanceled() {
				if (Thread.interrupted()) {
					setCanceled(true);
				}
				return super.isCanceled();
			}
			
		};
		return SubMonitor.convert(progress, 100);
	}

	/**
	 * The -rtc plugin is not to access any Hudson/Jenkins classes directly
	 * This uses reflection to access the logging available from BuildListener.
	 * @param listener The listener that can provide logging.
	 * @return Listener logging wrapper.
	 */
	protected IConsoleOutput getConsoleOutput(final Object listener) {

		IConsoleOutput logHandler = new IConsoleOutput() {

			private Method logMethod;
			private Method errorMethod;

			public void log(String message) {
				if (listener != null && logMethod == null) {
					try {
						logMethod = listener.getClass().getMethod("getLogger"); //$NON-NLS-1$
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				if (logMethod != null) {
					try {
						((PrintStream) logMethod.invoke(listener)).println(message);
					} catch (Exception e) {
						LOGGER.log(Level.FINER, "Unable to log message to listener", e); //$NON-NLS-1$
						LOGGER.finer(message);
					}
				} else {
					LOGGER.finer(message);
				}
			}

			public void log(String message, Exception e) {
				if (listener != null && errorMethod == null) {
					try {
						errorMethod = listener.getClass().getMethod("error", String.class); //$NON-NLS-1$
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				if (errorMethod != null) {
					try {
						PrintWriter writer = (PrintWriter) errorMethod.invoke(listener, message);
						e.printStackTrace(writer);
					} catch (Exception ex) {
						LOGGER.log(Level.FINER, "Unable to provide message to listener", ex); //$NON-NLS-1$
						LOGGER.log(Level.FINER, message, e);
					}
				} else {
					LOGGER.log(Level.FINER, message, e);
				}
			}
		};
		return logHandler;
	}
	
	/**
	 * The -rtc plugin is not to access any classes in the hjplugin project directly
	 * This uses reflection to access the BuildResultInfo class.
	 * @param buildResultInfo the class to be accessed in the -rtc project.
	 * @return BuildResultInfo wrapper.
	 */
	protected IBuildResultInfo getBuildResultInfo(final Object buildResultInfo) {
		if (buildResultInfo == null) {
			return null;
		}
		
		return new IBuildResultInfo() {

			private Method getBuildResultUUIDMethod;
			private Method setScheduledMethod;
			private Method setRequestorMethod;
			private Method setPersonalBuildMethod;
			private Method setOwnLifeCycleMethod;
			
			@Override
			public String getBuildResultUUID() {
				try {
					if (getBuildResultUUIDMethod == null) {
						getBuildResultUUIDMethod = buildResultInfo.getClass().getMethod("getBuildResultUUID"); //$NON-NLS-1$
					}
					return (String) getBuildResultUUIDMethod.invoke(buildResultInfo);
				} catch (IllegalAccessException e) {
					LOGGER.log(Level.FINER, "Unable to call getBuildResultUUID method", e);
				} catch (InvocationTargetException e) {
					LOGGER.log(Level.FINER, "Unable to call getBuildResultUUID method", e);
				} catch (SecurityException e) {
					LOGGER.log(Level.FINER, "Unable to find getBuildResultUUID method", e);
				} catch (NoSuchMethodException e) {
					LOGGER.log(Level.FINER, "Unable to find getBuildResultUUID method", e);
				}
				return null;
			}

			@Override
			public void setScheduled(boolean isScheduled) {
				try {
					if (setScheduledMethod == null) {
						setScheduledMethod = buildResultInfo.getClass().getMethod("setScheduled", boolean.class); //$NON-NLS-1$
					}
					setScheduledMethod.invoke(buildResultInfo, isScheduled);
				} catch (IllegalAccessException e) {
					LOGGER.log(Level.FINER, "Unable to call setScheduledMethod method", e);
				} catch (InvocationTargetException e) {
					LOGGER.log(Level.FINER, "Unable to call setScheduledMethod method", e);
				} catch (SecurityException e) {
					LOGGER.log(Level.FINER, "Unable to find setScheduledMethod method", e);
				} catch (NoSuchMethodException e) {
					LOGGER.log(Level.FINER, "Unable to find setScheduledMethod method", e);
				}
			}
			
			@Override
			public void setRequestor(String requestor) {
				try {
					if (setRequestorMethod == null) {
						setRequestorMethod = buildResultInfo.getClass().getMethod("setRequestor", String.class); //$NON-NLS-1$
					}
					setRequestorMethod.invoke(buildResultInfo, requestor);
				} catch (IllegalAccessException e) {
					LOGGER.log(Level.FINER, "Unable to call setRequestorMethod method", e);
				} catch (InvocationTargetException e) {
					LOGGER.log(Level.FINER, "Unable to call setRequestorMethod method", e);
				} catch (SecurityException e) {
					LOGGER.log(Level.FINER, "Unable to find setRequestorMethod method", e);
				} catch (NoSuchMethodException e) {
					LOGGER.log(Level.FINER, "Unable to find setRequestorMethod method", e);
				}
			}
			
			@Override
			public void setPersonalBuild(boolean isPersonalBuild) {
				try {
					if (setPersonalBuildMethod == null) {
						setPersonalBuildMethod = buildResultInfo.getClass().getMethod("setPersonalBuild", boolean.class); //$NON-NLS-1$
					}
					setPersonalBuildMethod.invoke(buildResultInfo, isPersonalBuild);
				} catch (IllegalAccessException e) {
					LOGGER.log(Level.FINER, "Unable to call setPersonalBuildMethod method", e);
				} catch (InvocationTargetException e) {
					LOGGER.log(Level.FINER, "Unable to call setPersonalBuildMethod method", e);
				} catch (SecurityException e) {
					LOGGER.log(Level.FINER, "Unable to find setPersonalBuildMethod method", e);
				} catch (NoSuchMethodException e) {
					LOGGER.log(Level.FINER, "Unable to find setPersonalBuildMethod method", e);
				}
			}

			@Override
			public void setOwnLifeCycle(boolean ownLifeCycle) {
				try {
					if (setOwnLifeCycleMethod == null) {
						setOwnLifeCycleMethod = buildResultInfo.getClass().getMethod("setOwnLifeCycle", boolean.class); //$NON-NLS-1$
					}
					setOwnLifeCycleMethod.invoke(buildResultInfo, ownLifeCycle);
				} catch (IllegalAccessException e) {
					LOGGER.log(Level.FINER, "Unable to call setOwnLifeCycleMethod method", e);
				} catch (InvocationTargetException e) {
					LOGGER.log(Level.FINER, "Unable to call setOwnLifeCycleMethod method", e);
				} catch (SecurityException e) {
					LOGGER.log(Level.FINER, "Unable to find setOwnLifeCycleMethod method", e);
				} catch (NoSuchMethodException e) {
					LOGGER.log(Level.FINER, "Unable to find setOwnLifeCycleMethod method", e);
				}
			}
		};
	}
	
	/**
	 * Returns the UUID of the given stream
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildStream The name of the Build Stream.
	 * @param clientLocale The locale of the requesting client
	 * @returns the UUID of the build stream as {@link String}
	 * @throws Exception
	 */
	public String getStreamUUID(String serverURI, String userId,
			String password, int timeout,
			String buildStream, Locale clientLocale)
			throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			return repoConnection.getBuildStreamUUID(buildStream,  monitor, clientLocale);
		} catch (OperationCanceledException e) {
			throw Utils.checkForCancellation(e);
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}
}
 