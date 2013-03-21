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
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * High-level facade for all things RTC.  Any requests that need to interface with RTC must go through here.
 * The API may only refer to plain Java types, not Hudson/Jenkins/Jazz/Eclipse types.
 * If you must pass through a more complex type in the API then supply it as an Object and use reflection to
 * access it. Not an ideal scenario, so avoid it if possible.
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
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient();
			String passwordToUse = buildClient.determinePassword(password, passwordFile);
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection();
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
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
		String errorMessage = null;
		try {
			AbstractBuildClient buildClient = getBuildClient(); 
			String passwordToUse = buildClient.determinePassword(password, passwordFile);
			ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
			RepositoryConnection repoConnection = buildClient.createRepositoryConnection(connectionDetails);
			repoConnection.testConnection();
			repoConnection.testBuildWorkspace(buildWorkspace);
		} catch (RTCValidationException e) {
			errorMessage = e.getMessage();
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
	 * @param buildWorkspace The name of the RTC build workspace
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @return Returns <code>true</code> if there are changes to the build workspace;
	 * <code>false</code> otherwise
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public boolean incomingChanges(String serverURI, String userId, String password, File passwordFile, int timeout, String buildWorkspace,
			Object listener) throws Exception {
		AbstractBuildClient buildClient = getBuildClient();
		String passwordToUse = buildClient.determinePassword(password, passwordFile);
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, passwordToUse, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		return repoConnection.incomingChanges(buildWorkspace, clientConsole);
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
	 * Accept changes into the build workspace and write a description of the changes into the ChangeLogFile.
	 * Load the contents of the updated build workspace at hjWorkspacePath.
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server. May be <code>null</code>
	 * 				in which case passwordFile should be supplied.
	 * @param passwordFile The file containing an obfuscated password to use when logging into
	 * 				the server. May be <code>null</code> in which case password should be supplied.
	 * @param timeout The timeout period for requests made to the server
	 * @param workspaceName The name of the RTC build workspace
	 * @param hjWorkspacePath The path where the contents of the RTC workspace should be loaded.
	 * @param changeLog The file where a description of the changes made should be written.
	 * @param baselineSetName The name to give the snapshot created. If <code>null</code> no snapshot
	 * 				will be created.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * This is defined as an Object due to class loader issues. It is expected to implement
	 * {@link TaskListener}.
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public void checkout(String serverURI, String userId, String password, int timeout, 
		String buildWorkspace, String hjWorkspacePath, OutputStream changeLog, String baselineSetName, final Object listener) throws Exception {
		AbstractBuildClient buildClient = getBuildClient(); 
		ConnectionDetails connectionDetails = buildClient.getConnectionDetails(serverURI, userId, password, timeout);
		IConsoleOutput clientConsole = getConsoleOutput(listener);
		RepositoryConnection repoConnection = buildClient.getRepositoryConnection(connectionDetails);
		ChangeReport report = new ChangeReport(changeLog);
		try	{
			repoConnection.checkout(buildWorkspace, hjWorkspacePath, report, baselineSetName, clientConsole);
		} finally {
			report.prepareChangeSetLog();
		}
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
