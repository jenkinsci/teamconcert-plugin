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
package com.ibm.team.build.internal.hjplugin;

import hudson.AbortException;
import hudson.model.TaskListener;
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
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

/**
 * Class responsible for Accepting incoming changes
 *
 */
public class RTCAcceptTask extends RTCTask<Map<String, Object>> {
	private static final Logger LOGGER = Logger.getLogger(RTCAcceptTask.class.getName());
	
	private String buildToolkit;
    private String serverURI;
	private String userId;
	private Secret password;
	private int timeout;
	private String processArea;
	private String buildWorkspace;
	private String buildResultUUID;
	private Map<String, String> buildSnapshotContextMap;
	private String buildSnapshot;
	private String buildStream;
	private TaskListener listener;
	private boolean isCustomSnapshotName;
	private String snapshotName;
	private String previousSnapshotUUID;
	private RemoteOutputStream changeLog;
	private boolean isRemote;
	private String contextStr;
	private boolean debug;
	private Locale clientLocale;
	private String callConnectorTimeout;
	private boolean acceptBeforeLoad;
	// The relative url of the previous build from which the previous snapshot uuid was obtained
	private String previousBuildUrl; 
	private String temporaryWorkspaceComment;
	
	/**
	 * Back links to Hudson/Jenkins that are to be set on the build result
	 */
	private String root;
	private String projectUrl;
	private String buildUrl;

	private static final long serialVersionUID = 1L;

	/**
	 * Task that performs accept work on the master or the slave
	 * 
	 * @param contextStr Context for logging
	 * @param buildToolkit The build toolkit to use when working with the facade
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param processArea The name of the project or team area
	 * @param buildDefinition The name (id) of the build definition to use. May be <code>null</code> if buildWorkspace
	 *            is supplied instead.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if buildDefinition is
	 *            supplied instead.
	 * @param buildSnapshotContextMap Name-Value pair representing the snapshot owner details. May be <code>null</code>
	 * @param buildStream The name of the RTC build stream. May be <code>null</code> if buildWorkspace or
	 *            buildDefinition is supplied instead.
	 * @param buildResultUUID The build result to relate build results with.
	 * @param isCustomSnapshotName Indicates if a custom snapshot name is configured in the Job
	 * @param snapshotName The name to set on the snapshot created during accept
	 * @param previousSnapshotUUID The uuid of the previous snapshot. Used for generating changelog in build stream
	 *            case. May be <code>null</code> if a buildResultUUID or buildWorkspace is supplied.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param changeLog Output stream to hold the Change log results. May be <code>null</code>.
	 * @param isRemote Whether this will be executed on the Master or a slave
	 * @param debug Whether to report debugging messages to the listener
	 * @param clientLocale The locale of the requesting client
	 * @param acceptBeforeLoad Accept latest changes before loading, if true
	 * @param previousBuildUrl 
	 * @param temporaryWorkspaceComment
	 */
	public RTCAcceptTask(String contextStr, String buildToolkit, String serverURI, String userId, String password, int timeout, String processArea,
			String buildResultUUID, String buildWorkspace, Map<String, String> buildSnapshotContextMap, String buildSnapshot, String buildStream,
			boolean isCustomSnapshotName, String snapshotName, String previousSnapshotUUID, TaskListener listener, RemoteOutputStream changeLog, boolean isRemote,
			boolean debug, Locale clientLocale, String strCallConnectorTimeout, boolean acceptBeforeLoad, String previousBuildUrl,
			String temporaryWorkspaceComment) {
    	
		super(debug, listener);
		this.contextStr = contextStr;
		this.buildToolkit = buildToolkit;
    	this.serverURI = serverURI;
    	this.userId = userId;
    	this.password = Secret.fromString(password);
    	this.timeout = timeout;
    	this.processArea = processArea;
    	this.buildWorkspace = buildWorkspace;
    	this.buildResultUUID = buildResultUUID;
    	this.buildSnapshotContextMap = buildSnapshotContextMap;
    	this.buildSnapshot = buildSnapshot;
    	this.buildStream = buildStream;
    	this.isCustomSnapshotName = isCustomSnapshotName;
    	this.snapshotName = snapshotName;
    	this.previousSnapshotUUID = previousSnapshotUUID;
    	this.listener = listener;
    	this.changeLog = changeLog;
    	this.isRemote = isRemote;
    	this.debug = debug;
    	this.clientLocale = clientLocale;
    	this.callConnectorTimeout = strCallConnectorTimeout;
    	this.acceptBeforeLoad = acceptBeforeLoad;
    	this.previousBuildUrl = previousBuildUrl;
    	this.temporaryWorkspaceComment = temporaryWorkspaceComment;
	}
	/**
	 * Provides the Urls to be set as links on the build result
	 * @param rootUrl The root url of the H/J server
	 * @param projectUrl The relative link to the H/J project
	 * @param buildUrl The relative link to the H/J build
	 */
	public void setLinkURLs(String rootUrl, String projectUrl, String buildUrl) {
		this.root = rootUrl;
		this.projectUrl = projectUrl;
		this.buildUrl = buildUrl;
	}

	public Map<String, Object> invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
		if (debug) {
			debug("Running " + contextStr); //$NON-NLS-1$
			debug("serverURI " + serverURI); //$NON-NLS-1$
			debug("userId " + userId); //$NON-NLS-1$
			debug("timeout " + timeout); //$NON-NLS-1$
			debug("processArea " + processArea); //$//$NON-NLS-1$
			debug("buildWorkspace " + (buildWorkspace == null ? "n/a" : buildWorkspace)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildResult " + (buildResultUUID == null ? "n/a" : "defined")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("buildStream " + (buildStream == null ? "n/a" : buildStream)); //$NON-NLS-1$ //$NON-NLS-2$ 
			debug("buildSnapshot " + (buildSnapshot == null ? "n/a" : buildSnapshot)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("previousSnapshotUUID" + (previousSnapshotUUID == null ? "n/a" : previousSnapshotUUID)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("listener is " + (listener == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("Running remote " + isRemote); //$NON-NLS-1$
			debug("buildToolkit property " + buildToolkit); //$NON-NLS-1$
			debug("prevBuildUrl" + ((previousBuildUrl != null)? previousBuildUrl :"No Url Provided")); //$NON-NLS-1$ //$NON-NLS-2$
			debug("temporaryWorkspacComment"+ temporaryWorkspaceComment); //$NON-NLS-1$
		}

		try {
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
    		if (debug) {
    			debug("hjplugin-rtc.jar " + RTCFacadeFactory.getFacadeJarURL(listener.getLogger()).toString()); //$NON-NLS-1$
    		}
    		if (buildResultUUID != null) {
				try {
					facade.invoke(
							"createBuildLinks", new Class[] { //$NON-NLS-1$
							String.class, // serverURI,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // buildResultUUID
									String.class, // rootUrl
									String.class, // projectUrl
									String.class, // buildUrl
									Object.class, // listener)
							}, serverURI, userId, Secret.toString(password), 
							timeout, buildResultUUID, root, projectUrl,
							buildUrl, listener);
				} catch (Exception e) {
	    			// failure to create links is not a reason to abort the build unless cancelled
					Throwable eToReport = e;
					if (e instanceof InvocationTargetException) {
						eToReport = e.getCause();
					}
		    		if (eToReport instanceof InterruptedException) {
		    			if (debug) {
		    				debug("build link creation interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
		    			}
		    			throw (InterruptedException) eToReport;
		    		}
					listener.getLogger().println(Messages.RTCScm_link_creation_failure(e.getMessage()));
	    			if (debug) {
	    				debug("Failed to create build links", eToReport); //$NON-NLS-1$
	    			}
				}
    		}
    		
			return (Map<String, Object>) facade.invoke("accept", new Class[] { //$NON-NLS-1$
					String.class, // serverURI,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, //processArea,
					String.class, // buildResultUUID,
					String.class, // buildWorkspace,
					Map.class, // buildSnapshotContext
					String.class, // buildSnapshot,
					String.class, // buildStream,
					String.class, // hjWorkspacePath,
					OutputStream.class, // changeLog,
					boolean.class, // isCustomSnapshotName
					String.class, // snapshotName,
					String.class, // previousSnapshotUUID
					Object.class, // listener
					Locale.class, // clientLocale
					String.class, // callConnectorTimeout
					boolean.class, // acceptBeforeLoad
					String.class, // previousBuildUrl
					String.class // temporaryWorkspaceComment
			}, serverURI, userId, Secret.toString(password),
					timeout, processArea, buildResultUUID, buildWorkspace, buildSnapshotContextMap, buildSnapshot,
					buildStream, workspace.getAbsolutePath(),
					changeLog, isCustomSnapshotName, snapshotName, previousSnapshotUUID,
					listener, clientLocale, callConnectorTimeout, acceptBeforeLoad, 
					previousBuildUrl, temporaryWorkspaceComment);

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

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
