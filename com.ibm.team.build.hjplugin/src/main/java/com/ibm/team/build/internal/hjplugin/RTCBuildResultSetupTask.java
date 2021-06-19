/*******************************************************************************
 * Copyright (c) 2014, 2021 IBM Corporation and others.
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
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.util.Helper;

/**
 * Creates the build result or marks build result as started. Returns information
 * about who controls the build result lifecycle (Jenkins plugin/RTC), the build 
 * result uuid and who is responsible for starting the build if started in RTC.
 * 
 * This task groups work that should be run at the very start of the build whose
 * results are needed for record keeping. Additional work should be cautiously added
 * to this task since an exception (of any kind) in that will prevent lifecycle
 * info from being linked to the build.
 */
public class RTCBuildResultSetupTask extends RTCTask<BuildResultInfo> {

    private static final Logger LOGGER = Logger.getLogger(RTCBuildResultSetupTask.class.getName());

	private final String buildToolkit;
	private final String serverURI;
	private final String userId;
	private final Secret password;
	private final int timeout;
	private final boolean useBuildDefinitionInBuild;
	private final String buildDefinition;
	private final String buildResultUUID;
	private final String label;
	private final TaskListener listener;
	private final boolean isRemote;
	private final boolean debug;
	private final Locale clientLocale;
	private final String contextStr;

	private static final long serialVersionUID = 1L;

	/**
	 * Task that creates/starts a build result on the master or the slave
	 * It also retrieves info related to the source of the build request
	 * 
	 * @param contextStr Context for logging
	 * @param buildToolkit The build toolkit to use when working with the facade
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name (id) of the build definition to use. May be <code>null</code>
	 * if buildWorkspace is supplied instead.
	 * @param buildResultUUID The build result to relate build results with.
	 * @param label The name to give the baselineSet created
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @param isRemote Whether this will be executed on the Master or a slave
	 * @param debug Whether to report debugging messages to the listener
	 * @param clientLocale The locale of the requesting client
	 */
	public RTCBuildResultSetupTask(String contextStr, String buildToolkit,
			String serverURI, String userId, String password, int timeout,
			boolean useBuildDefinitionInBuild, String buildDefinition, String buildResultUUID,
			String label, TaskListener listener,
			boolean isRemote, boolean debug, Locale clientLocale) {
    	
		super(debug, listener);
		this.contextStr = contextStr;
		this.buildToolkit = buildToolkit;
    	this.serverURI = serverURI;
    	this.userId = userId;
    	this.password = Secret.fromString(password);
    	this.timeout = timeout;
    	this.useBuildDefinitionInBuild = useBuildDefinitionInBuild;
    	this.buildDefinition = buildDefinition;
    	this.buildResultUUID = buildResultUUID;
    	this.label = label;
    	this.listener = listener;
    	this.isRemote = isRemote;
    	this.debug = debug;
    	this.clientLocale = clientLocale;
	}

	@Override
	public BuildResultInfo invoke(File f, VirtualChannel channel) throws IOException,
			InterruptedException {

		if (debug) {
			debug("Build initialization for " + contextStr); //$NON-NLS-1$
			debug("serverURI " + serverURI); //$NON-NLS-1$
			debug("userId " + userId); //$NON-NLS-1$
			debug("timeout " + timeout); //$NON-NLS-1$
			debug("buildResult " + (buildResultUUID == null ? "n/a" : "defined")); //$NON-NLS-1$
			debug("listener is " + (listener == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			debug("Running remote " + isRemote); //$NON-NLS-1$
			debug("buildToolkit property " + buildToolkit); //$NON-NLS-1$
		}

		BuildResultInfo buildResultInfo = localInvocation();
		if (buildResultInfo == null) {
			
			try {
				RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkit,  debug ? listener.getLogger() : null);
		
				// If we don't have a build result but have a build definition, create the
				// build result prior to going to the slave so that it can be in the slave's
				// environment. Also we don't depend on slave giving it back and that helps
				// with terminating the build result during failure cases.
				if (buildResultUUID == null && useBuildDefinitionInBuild) {
					String actualBuildResultUUID = (String) facade.invoke(
							"createBuildResult", new Class[] { //$NON-NLS-1$
									String.class, // serverURI,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // buildDefinition,
									String.class, // buildLabel,
									Object.class, // listener
									Locale.class // clientLocale
							}, serverURI, userId,
							Secret.toString(password), timeout,
							buildDefinition, label, listener, clientLocale);
					buildResultInfo = new BuildResultInfo(actualBuildResultUUID, true);
		
				} else if (buildResultUUID != null) {
					// we own the build lifecycle if we start it.
					buildResultInfo = new BuildResultInfo(buildResultUUID, false);
					facade.invoke("startBuild", new Class[] { //$NON-NLS-1$
							String.class, // serverURI,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							Object.class, // buildResultInfoObject,
							String.class, // buildLabel
							Object.class, // listener
							Locale.class // clientLocale
						}, serverURI, userId,
						Secret.toString(password), timeout,
						buildResultInfo, label, listener, clientLocale);

				} else {
					// we don't have a build result and we are using a workspace
					// should never get here since localInvocation handles this case
					// and ideally we aren't run remotely
					buildResultInfo = new BuildResultInfo(buildResultUUID, false);
				}
			} catch (Exception e) {
				// serious issue with the build setup - report it
	    		Throwable eToReport = e;
	    		if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
					eToReport = e.getCause();
	    		}
	    		if (eToReport instanceof InterruptedException) {
					listener.getLogger().println(Messages.RTCScm_checkout_failure3(eToReport.getMessage()));
	    			throw (InterruptedException) eToReport;
	    		} 
	    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checkout_failure3(eToReport.getMessage()));
	    		if (Helper.unexpectedFailure(eToReport)) {
	    			eToReport.printStackTrace(writer);
	    		}
	    		
	    		// if we can't establish the build result -> we can't build it
	    		throw new AbortException(Messages.RTCScm_checkout_failure4(eToReport.getMessage()));
			}
		}
		return buildResultInfo;
	}

	/**
	 * If this task doesn't need to be run remotely give the appropriate answer back.
	 * The task should run remotely if it needs to communicate with RTC server.
	 * 
	 * @return <code>null</code> if the task needs to run remotely. Otherwise the 
	 * answer that can be determined locally.
	 */
	public BuildResultInfo localInvocation() {
		if (buildResultUUID == null && useBuildDefinitionInBuild) {
			return null;
		} else if (buildResultUUID != null) {
			return null;
		} else {
			// we don't have a build result and we are using a workspace
			return new BuildResultInfo(buildResultUUID, false);
		}
		
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
	
}
