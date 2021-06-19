/*******************************************************************************
 * Copyright © 2016, 2021 IBM Corporation and others.
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
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;


/**
 * Class responsible for Loading the repository workspace for build definition and 
 * repository workspace configuration. For build stream and build snapshot configurations, 
 * it loads the temporary repository workspace created in {@link RTCAcceptTask} 
 */
public class RTCLoadTask extends RTCTask<Map<String, Object>> {
	private static final Logger LOGGER = Logger.getLogger(RTCLoadTask.class.getName());
	
	private String buildToolkit;
    private String serverURI;
	private String userId;
	private Secret password;
	private int timeout;
	private String processArea;
	private String buildStream;
	private Map<String, String> buildSnapshotContextMap;
	private String buildSnapshot;
	private String buildWorkspace;
	private String buildResultUUID;
	private TaskListener listener;
	boolean isCustomSnapshotName;
	private String snapshotName;
	private boolean isRemote;
	private String contextStr;
	private boolean debug;
	private Locale clientLocale;
	private String parentActivityId;
	private String connectorId;
	private RtcExtensionProvider extProvider;
	private String loadPolicy;
	private String componentLoadConfig;
	private String componentsToExclude;
	private String pathToLoadRuleFile;
	private boolean isDeleteNeeded;
	private boolean createFoldersForComponents;
	private boolean acceptBeforeLoad;
	private Map<String,String> buildStreamData;
	private String temporaryWorkspaceComment;
	private boolean shouldDeleteTemporaryWorkspace;
	private Map<String, Object> options;

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
	 * @param buildResultUUID The build result to relate build results with.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if buildDefinition or
	 *            buildSnapshot or buildStream is supplied instead
	 * @param buildSnapshotContextMap Name-Value pair representing the snapshot owner details. May be <code>null</code>
	 * @param buildSnapshot the name/uuid of the RTC build snapshot. May be <code>null</code> if buildDefinition or
	 *            buildWorkspace or buildStream is supplied instead
	 * @param buildStream The name of the RTC build stream. May be <code>null</code> if one of buildDefinition or
	 *            buildWorkspace or buildSnapshot is supplied instead
	 * @param buildStreamData the additional data from stream load obtained from {@link RTCAcceptTask}
	 * @param isCustomSnapshotName Indicates if a custom snapshot name is configured in the Job
	 * @param snapshotName The name of the snapshot created during accept
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param isRemote Whether this will be executed on the Master or a slave
	 * @param debug Whether to report debugging messages to the listener
	 * @param clientLocale The locale of the requesting client
	 * @param extProvider The extension provider can be <code>null</code>
	 * @param isDeleteNeeded true if Jenkins job is configured to delete load directory before fetching
	 * @param loadPolicy Determines whether to use load rules
	 * @param componentLoadConfig When load policy is set to useComponentLoadConfig this value determines whether to
	 *            load all components or exclude some components
	 * @param componentsToExclude Comma separated list of id/name of components to be excluded from load
	 * @param pathToLoadRuleFile path to the load rule file. Right now only remote path of the format <component
	 *            name>/<remote path to load rule file> is supported
	 * @param createFoldersForComponents create folders for components if true
	 * @param temporaryWorkspaceComment Description for the temporary repository workspace
	 * @param deleteTemporaryWorkspace - whether the temporary workspace create for snapshot build or already created
	 *            for stream build should be deleted at the end of load
	 * @param options List of options for various functionality. TFor future enhancements, use this map to store  
	 *                options instead of arguments to this method
	 * @throws Exception
	 */
	public RTCLoadTask(String contextStr, String buildToolkit, String serverURI, String userId, String password, int timeout, String processArea,
			String buildResultUUID, String buildWorkspace, Map<String, String> buildSnapshotContextMap, String buildSnapshot, String buildStream,
			Map<String, String> buildStreamData, boolean isCustomSnapshotName, String snaspshotName, TaskListener listener, boolean isRemote,
			boolean debug, Locale clientLocale, String parentActivityId, String connectorId, RtcExtensionProvider extProvider, String loadPolicy,
			String componentLoadConfig, String componentsToExclude, String pathToLoadRuleFile, boolean isDeleteNeeded,
			boolean createFoldersForComponents, boolean acceptBeforeLoad, String temporaryWorkspaceComment, 
			boolean deleteTemporaryWorkspace, Map<String, Object> options) {
    	
		super(debug, listener);
		this.contextStr = contextStr;
		this.buildToolkit = buildToolkit;
    	this.serverURI = serverURI;
    	this.userId = userId;
    	this.password = Secret.fromString(password);
    	this.timeout = timeout;
    	this.processArea = processArea;
    	this.buildWorkspace = buildWorkspace;
    	this.buildSnapshotContextMap = buildSnapshotContextMap;
    	this.buildSnapshot = buildSnapshot;
    	this.buildStream = buildStream;
    	this.buildResultUUID = buildResultUUID;
    	this.buildStreamData = buildStreamData;
    	this.isCustomSnapshotName = isCustomSnapshotName;
    	this.snapshotName = snaspshotName;
    	this.listener = listener;
    	this.isRemote = isRemote;
    	this.debug = debug;
    	this.clientLocale = clientLocale;
    	this.parentActivityId = parentActivityId;
    	this.connectorId = connectorId;
    	this.extProvider = extProvider;
    	this.isDeleteNeeded = isDeleteNeeded;
    	this.loadPolicy = loadPolicy;
    	this.componentLoadConfig = componentLoadConfig;
    	this.componentsToExclude = componentsToExclude;
    	this.pathToLoadRuleFile = pathToLoadRuleFile;
    	this.createFoldersForComponents = createFoldersForComponents;
    	this.acceptBeforeLoad = acceptBeforeLoad;
    	this.temporaryWorkspaceComment = temporaryWorkspaceComment;
    	this.shouldDeleteTemporaryWorkspace = deleteTemporaryWorkspace;
    	this.options = options;
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

	@SuppressWarnings("unchecked")
	public Map<String, Object> invoke(File workspace, VirtualChannel channel) throws IOException, InterruptedException {
		if (debug) {
			debug("Running " + contextStr); //$NON-NLS-1$
			debug("serverURI " + serverURI); //$NON-NLS-1$
			debug("userId " + userId); //$NON-NLS-1$
			debug("timeout " + timeout); //$NON-NLS-1$
			debug("processArea " + processArea); ////$NON-NLS-1$
			debug("buildWorkspace " + (buildWorkspace == null ? "n/a" : buildWorkspace)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildSnapshot " + (buildSnapshot == null ? "n/a" : buildSnapshot)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildStream " + (buildStream == null ? "n/a" : buildStream)); //$NON-NLS-1$ //$NON-NLS-2$
			debug("buildResult " + (buildResultUUID == null ? "n/a" : "defined")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("buildStreamData is " + (buildStreamData == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("listener is " + (listener == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("Running remote " + isRemote); //$NON-NLS-1$
			debug("buildToolkit property " + buildToolkit); //$NON-NLS-1$
			debug("parentActivityId " + parentActivityId); // $NON-NLS-1$
			debug("loadPolicy " + loadPolicy); // $NON-NLS-1$
			debug("componentLoadConfig " + componentLoadConfig); // $NON-NLS-1$
			debug("componentsToExclude " + componentsToExclude); // $NON-NLS-1$
			debug("pathToLoadRuleFile " + pathToLoadRuleFile); //$NON-NLS-1$
			debug("temporaryWorkspaceComment " + temporaryWorkspaceComment); // $NON-NLS-1$
			debug("shouldDeleteTemporaryWorkspace " + shouldDeleteTemporaryWorkspace); // $NON-NLS-1$
		}

		try {
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
    		if (debug) {
    			debug("hjplugin-rtc.jar " + RTCFacadeFactory.getFacadeJarURL(listener.getLogger()).toString()); //$NON-NLS-1$
    		}
    		
    		
    		return (Map<String, Object>) facade.invoke("load", new Class[] { //$NON-NLS-1$
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
					Map.class, // buildStreamData,
					String.class, // hjWorkspacePath,
					boolean.class, // isCustomSnapshotName
					String.class, // snapshotName,
					Object.class, // listener
					Locale.class, // clientLocale
					String.class, // parentActivityId
					String.class, // connectorId
					Object.class, //extension provider
					PrintStream.class, //print stream
					String.class, // load policy
					String.class, // component load config
					String.class, // components to exclude
					String.class, // path to load rule file
					boolean.class, // isDeleteNeeded
					boolean.class, // createFoldersForComponents
					boolean.class, // acceptBeforeLoad
					String.class, // temporaryWorkspaceComment
					boolean.class, // shouldDeleteTemporaryWorkspace
					Map.class
			}, serverURI, userId, Secret.toString(password),
					timeout, processArea, buildResultUUID, buildWorkspace, buildSnapshotContextMap,
					buildSnapshot, buildStream, buildStreamData,
					workspace.getAbsolutePath(), isCustomSnapshotName,
					snapshotName,
					new TaskListenerWrapper(listener), clientLocale, parentActivityId, connectorId,
					extProvider, listener.getLogger(), loadPolicy, componentLoadConfig, 
					componentsToExclude, pathToLoadRuleFile, isDeleteNeeded, 
					createFoldersForComponents, acceptBeforeLoad, temporaryWorkspaceComment, 
					shouldDeleteTemporaryWorkspace, options);

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
    		if (Helper.unexpectedFailure(eToReport)) {
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
