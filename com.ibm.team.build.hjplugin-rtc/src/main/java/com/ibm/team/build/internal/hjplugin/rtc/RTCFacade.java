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

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
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
	 * Logs into the repository to test the connection. Essentially excercises the configuration parameters supplied.
	 * This is expected to be called on the Master. If the decision changes and we are to pass the request
	 * out to a slave, then {@link #determinePassword(String, File)} should be used to determine the
	 * password first.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @param timeout The timeout period for requests made to the server
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public String testConnection(String serverURI, String userId, String password, File passwordFile, int timeout) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();

		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient();
			String passwordToUse = buildClient.determinePassword(password, passwordFile);
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor);
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}
	
	/**
	 * Logs into the repository to test the connection and validates the RTC build workspace is valid for use.
	 * This is expected to be called on the Master. If the decision changes and we are to pass the request
	 * out to a slave, then {@link #determinePassword(String, File)} should be used to determine the
	 * password first.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildWorkspace The name of the RTC build workspace
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public String testBuildWorkspace(String serverURI, String userId, String password, File passwordFile, int timeout, String buildWorkspace) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			String passwordToUse = buildClient.determinePassword(password, passwordFile);
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testBuildWorkspace(buildWorkspace, monitor.newChild(50));
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}
	
	/**
	 * Logs into the repository to test the connection and validates the RTC build definition is valid for use.
	 * This is expected to be called on the Master. If the decision changes and we are to pass the request
	 * out to a slave, then {@link #determinePassword(String, File)} should be used to determine the
	 * password first.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name of the RTC build definition
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public String testBuildDefinition(String serverURI, String userId, String password, File passwordFile, int timeout, String buildDefinition) throws Exception {
		SubMonitor monitor = getProgressMonitor();
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			String passwordToUse = buildClient.determinePassword(password, passwordFile);
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection(monitor.newChild(50));
			repoConnection.testBuildDefinition(buildDefinition, monitor.newChild(50));
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
		} catch (RTCConfigurationException e) {
			errorMessage = e.getMessage();
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
		return errorMessage;
	}

	/**
	 * Checks to see if there are incoming changes for the RTC build workspace (meaning a build is needed).
	 * This is expected to be called on the Master. If the decision changes and we are to pass the request
	 * out to a slave, then {@link #determinePassword(String, File)} should be used to determine the
	 * password first.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
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
	 * @return Returns <code>true</code> if there are changes to the build workspace;
	 * <code>false</code> otherwise
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public boolean incomingChanges(String serverURI, String userId,
			String password, File passwordFile, int timeout,
			String buildDefinition, String buildWorkspace, Object listener)
			throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		String passwordToUse = buildClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			return repoConnection.incomingChanges(buildDefinition, buildWorkspace, clientConsole, monitor);
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Determines the password to use when connecting to the repository. The password file
	 * has precedence if both are supplied.
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @return The password determined for use
	 * @throws Exception If no password can be determined
	 */
	public String determinePassword(String password, File passwordFile) throws Exception {
		AbstractBuildClient buildClient = getBuildClient();
		return buildClient.determinePassword(password, passwordFile);
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
	 * @return The item id of the build result created
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public String createBuildResult(String serverURI,
			String userId,
			String password,
			int timeout,
			String buildDefinition,
			String buildLabel,
			Object listener) throws Exception {
		
		IProgressMonitor monitor = getProgressMonitor();
		if (monitor.isCanceled()) {
			throw new InterruptedException();
		}
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		try {
			return repoConnection.createBuildResult(buildDefinition, null, buildLabel, clientConsole, monitor);
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
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
			throw new InterruptedException(e.getMessage());
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
	}

	/**
	 * Terminate an RTC build previously started by the H/J build 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The UUID for the build result to be ended.
	 * @param aborted Whether the Jenkins build was aborted
	 * @param buildState Whether the Jenkins build was a success, failure, or unstable
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public void terminateBuild(String serverURI,
			String userId,
			String password,
			File passwordFile,
			int timeout,
			String buildResultUUID,
			boolean aborted, int buildState,
			Object listener) throws Exception {
		
		SubMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient();
		String passwordToUse = buildClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		try {
			repoConnection.terminateBuild(buildResultUUID, aborted, buildState, clientConsole, monitor);
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
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
	 * @param changeLog The file where a description of the changes made should be written.
	 * @param baselineSetName The name to give the snapshot created. If <code>null</code> no snapshot
	 * 				will be created.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @return <code>Map<String, String></code> of build properties
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public Map<String, String> checkout(String serverURI, String userId, String password,
			int timeout, String buildResultUUID, String buildWorkspace,
			String hjWorkspacePath, OutputStream changeLog,
			String baselineSetName, final Object listener) throws Exception {
		IProgressMonitor monitor = getProgressMonitor();
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		ChangeReport report = new ChangeReport(changeLog);
		try	{
			return repoConnection.checkout(buildResultUUID, buildWorkspace,
					hjWorkspacePath, report, baselineSetName, clientConsole, monitor);
		} catch (OperationCanceledException e) {
			throw new InterruptedException(e.getMessage());
		} catch (TeamRepositoryException e) {
			throw Utils.checkForCancellation(e);
		}
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
}
