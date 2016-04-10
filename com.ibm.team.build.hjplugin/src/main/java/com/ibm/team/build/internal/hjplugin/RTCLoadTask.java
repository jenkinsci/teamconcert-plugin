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
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.extensions.RtcExtensionProvider;


/**
 * Class responsible for Loading
 *
 */
public class RTCLoadTask extends RTCTask<Void> {
	private static final Logger LOGGER = Logger.getLogger(RTCLoadTask.class.getName());
	
	private String buildToolkit;
    private String serverURI;
	private String userId;
	private Secret password;
	private int timeout;
	private String buildStream;
	private String buildSnapshot;
	private String buildWorkspace;
	private String buildResultUUID;
	private TaskListener listener;
	private String baselineSetName;
	private boolean isRemote;
	private String contextStr;
	private boolean debug;
	private Locale clientLocale;
	private String parentActivityId;
	private String connectorId;
	private RtcExtensionProvider extProvider;
	private boolean isDeleteNeeded;
	private boolean createFoldersForComponents;
	private String componentsToExclude;
	private String loadRules;
	private boolean acceptBeforeLoad;
	private Map<String,String> buildStreamData;
	
	/**
	 * Back links to Hudson/Jenkins that are to be set on the build result
	 */
	private String root;
	private String projectUrl;
	private String buildUrl;

	private static final long serialVersionUID = 1L;
	
	/**
	 * Task that performs accept work on the master or the slave
	 * @param contextStr Context for logging
	 * @param buildToolkit The build toolkit to use when working with the facade
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
 	 * @param buildResultUUID The build result to relate build results with.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code>
	 * if buildDefinition or buildSnapshot or buildStream is supplied instead
	 * @param buildSnapshot the name/uuid of the RTC build snapshot. May be <code>null</code>
	 * if buildDefinition or buildWorkspace or buildStream is supplied instead
	 * @param buildStream The name of the RTC build stream. May be <code>null</code> if one of 
	 * buildDefinition or buildWorkspace or buildSnapshot is supplied instead
	 * @param buildStreamData the additional data from stream load obtained from {@link RTCAcceptTask} 
	 * @param baselineSetName The name to give the baselineSet created
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param isRemote Whether this will be executed on the Master or a slave
	 * @param debug Whether to report debugging messages to the listener
	 * @param clientLocale The locale of the requesting client
	 * @param extProvider The extension provider can be <code>null</code> 
	 * @param isDeleteNeeded true if Jenkins job is configured to delete load directory before fetching
	 * @param createFoldersForComponents create folders for components if true
	 * @param componentsToExclude json text representing the list of components to exclude during load
	 * @param loadRules json text representing the component to load rule file mapping
	 * @throws Exception 
	 */
	public RTCLoadTask(String contextStr, String buildToolkit,
			String serverURI, String userId, String password, int timeout,
			String buildResultUUID, String buildWorkspace,
			String buildSnapshot, String buildStream,
			Map<String, String> buildStreamData,
			String baselineSetName, TaskListener listener,
			boolean isRemote, boolean debug, Locale clientLocale, 
			String parentActivityId, String connectorId, RtcExtensionProvider extProvider, boolean isDeleteNeeded, 
			boolean createFoldersForComponents, String componentsToExclude, String loadRules, boolean acceptBeforeLoad) {
    	
		super(debug, listener);
		this.contextStr = contextStr;
		this.buildToolkit = buildToolkit;
    	this.serverURI = serverURI;
    	this.userId = userId;
    	this.password = Secret.fromString(password);
    	this.timeout = timeout;
    	this.buildWorkspace = buildWorkspace;
    	this.buildSnapshot = buildSnapshot;
    	this.buildStream = buildStream;
    	this.buildResultUUID = buildResultUUID;
    	this.buildStreamData = buildStreamData;
    	this.baselineSetName = baselineSetName;
    	this.listener = listener;
    	this.isRemote = isRemote;
    	this.debug = debug;
    	this.clientLocale = clientLocale;
    	this.parentActivityId = parentActivityId;
    	this.connectorId = connectorId;
    	this.extProvider = extProvider;
    	this.isDeleteNeeded = isDeleteNeeded;
    	this.createFoldersForComponents = createFoldersForComponents;
    	this.componentsToExclude = componentsToExclude;
    	this.loadRules = loadRules;
    	this.acceptBeforeLoad = acceptBeforeLoad;
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

	public Void invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
		if (debug) {
			debug("Running " + contextStr); //$NON-NLS-1$
			debug("serverURI " + serverURI); //$NON-NLS-1$
			debug("userId " + userId); //$NON-NLS-1$
			debug("timeout " + timeout); //$NON-NLS-1$
			debug("buildWorkspace " + (buildWorkspace == null ? "n/a" : buildWorkspace)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildSnapshot " + (buildSnapshot == null ? "n/a" : buildSnapshot)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildStream " + (buildStream == null ? "n/a" : buildStream)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildResult " + (buildResultUUID == null ? "n/a" : "defined")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("buildStreamData is " + (buildStreamData == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("listener is " + (listener == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("Running remote " + isRemote); //$NON-NLS-1$
			debug("buildToolkit property " + buildToolkit); //$NON-NLS-1$
			debug("parentActivityId " + parentActivityId);
		}

		try {
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
    		if (debug) {
    			debug("hjplugin-rtc.jar " + RTCFacadeFactory.getFacadeJarURL(listener.getLogger()).toString()); //$NON-NLS-1$
    		}
    		
    		
    		facade.invoke("load", new Class[] { //$NON-NLS-1$
					String.class, // serverURI,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, // buildResultUUID,
					String.class, // buildWorkspace,
					String.class, // buildSnapshot,
					String.class, // buildStream,
					Map.class, // buildStreamData,
					String.class, // hjWorkspacePath,
					String.class, // baselineSetName,
					Object.class, // listener
					Locale.class, // clientLocale
					String.class, // parentActivityId
					String.class, // connectorId
					Object.class, //extension provider
					PrintStream.class, //print stream
					boolean.class, // isDeleteNeeded
					boolean.class, // createFoldersForComponents
					String.class, // componentsToBeExcluded
					String.class, // loadRules
					boolean.class // acceptBeforeLoad
			}, serverURI, userId, Secret.toString(password),
					timeout, buildResultUUID, buildWorkspace,
					buildSnapshot, buildStream, buildStreamData,
					workspace.getAbsolutePath(),
					baselineSetName,
					listener, clientLocale, parentActivityId, connectorId,
					extProvider, listener.getLogger(), isDeleteNeeded, 
					createFoldersForComponents, componentsToExclude, loadRules, acceptBeforeLoad);

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
		return null;
    }

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
	
}
