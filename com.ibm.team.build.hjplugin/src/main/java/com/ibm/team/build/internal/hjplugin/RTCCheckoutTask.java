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

package com.ibm.team.build.internal.hjplugin;

import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

public class RTCCheckoutTask implements FileCallable<Map<String, String>> {
	
	private String buildToolkit;
    private String serverURI;
	private String userId;
	private Secret password;
	private int timeout;
	private String buildWorkspace;
	private String buildResultUUID;
	private BuildListener listener;
	private String baselineSetName;
	private RemoteOutputStream changeLog;
	private boolean isRemote;
	private String contextStr;
	private boolean debug;
	private Locale clientLocale;

	private static final long serialVersionUID = 1L;

	/**
	 * Task that performs checkout work on the master or the slave
	 * @param contextStr Context for logging
	 * @param buildToolkit The build toolkit to use when working with the facade
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name (id) of the build definition to use. May be <code>null</code>
	 * if buildWorkspace is supplied instead.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code>
	 * if buildDefinition is supplied instead.
	 * @param buildResultUUID The build result to relate build results with.
	 * @param baselineSetName The name to give the baselineSet created
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param changeLog Output stream to hold the Change log results
	 * @param isRemote Whether this will be executed on the Master or a slave
	 * @param debug Whether to report debugging messages to the listener
	 * @param clientLocale The locale of the requesting client
	 */
	public RTCCheckoutTask(String contextStr, String buildToolkit,
			String serverURI, String userId, String password, int timeout,
			String buildResultUUID, String buildWorkspace,
			String baselineSetName, BuildListener listener,
			RemoteOutputStream changeLog, boolean isRemote, boolean debug, Locale clientLocale) {
    	
		this.contextStr = contextStr;
		this.buildToolkit = buildToolkit;
    	this.serverURI = serverURI;
    	this.userId = userId;
    	this.password = Secret.fromString(password);
    	this.timeout = timeout;
    	this.buildWorkspace = buildWorkspace;
    	this.buildResultUUID = buildResultUUID;
    	this.baselineSetName = baselineSetName;
    	this.listener = listener;
    	this.changeLog = changeLog;
    	this.isRemote = isRemote;
    	this.debug = debug;
    	this.clientLocale = clientLocale;
	}

	public Map<String, String> invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
		if (debug) {
			listener.getLogger().println("Running " + contextStr); //$NON-NLS-1$
			listener.getLogger().println("serverURI " + serverURI); //$NON-NLS-1$
			listener.getLogger().println("userId " + userId); //$NON-NLS-1$
			listener.getLogger().println("timeout " + timeout); //$NON-NLS-1$
			listener.getLogger().println("buildWorkspace " + (buildWorkspace == null ? "n/a" : buildWorkspace)); //$NON-NLS-1$
			listener.getLogger().println("buildResult " + (buildResultUUID == null ? "n/a" : "defined")); //$NON-NLS-1$
			listener.getLogger().println("listener is " + (listener == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			listener.getLogger().println("Running remote " + isRemote); //$NON-NLS-1$
			listener.getLogger().println("buildToolkit property " + buildToolkit); //$NON-NLS-1$
		}

		try {
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
    		if (debug) {
    			listener.getLogger().println("hjplugin-rtc.jar " + facade.getFacadeJarURL().toString());
    		}
			return (Map<String, String>) facade.invoke("checkout", new Class[] { //$NON-NLS-1$
					String.class, // serverURI,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, // buildResultUUID,
					String.class, // buildWorkspace,
					String.class, // hjWorkspacePath,
					OutputStream.class, // changeLog,
					String.class, // baselineSetName,
					Object.class, // listener
					Locale.class // clientLocale
			}, serverURI, userId, Secret.toString(password),
					timeout, buildResultUUID, buildWorkspace,
					workspace.getAbsolutePath(),
					changeLog, baselineSetName,
					listener, clientLocale);

    	} catch (Exception e) {
    		Throwable eToReport = e;
    		if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
				eToReport = e.getCause();
    		}
    		if (eToReport instanceof InterruptedException) {
				listener.getLogger().println(Messages.RTCScm_checkout_failure3(eToReport.getMessage()));
    			throw (InterruptedException) eToReport;
    		} 
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checkout_failure(eToReport.getMessage()));
    		if (RTCScm.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		
    		// if we can't check out then we can't build it
    		throw new AbortException(Messages.RTCScm_checkout_failure2(eToReport.getMessage()));
    	}
    }
}
