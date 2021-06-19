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
package com.ibm.team.build.internal.hjplugin.tasks;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCTask;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

import hudson.Functions;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class RetrieveWorkspaceDetailsTask extends RTCTask<String> {

	/**
	 * Key to idenitfy the workspace UUID from the map.
	 */
	private static final String WORKSPACE_UUID_KEY = "workspaceUUID";

	private static final String RETRIEVE_WORKSPACE_DETAILS_STARTED_MSG = "Retrieving workspace details for " //$NON-NLS-1$
			+ " build definition %s, build workspace %s"; //$NON-NLS-1$

	private static final String RETRIEVE_WORKSPACE_DETAILS_COMPLETED_MSG = "Retrieving workspace details complete." //$NON-NLS-1$
			+ "Return values : \n"  //$NON-NLS-1$
			+ "workspaceUUID : %s"; //$NON-NLS-1$

	private static final Logger LOGGER = Logger.getLogger(RetrieveWorkspaceDetailsTask.class.getName());

	private static final long serialVersionUID = 1L;
	
	private String buildToolkitPath;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	Locale clientLocale;
	private String buildDefinitionId;
	private String buildWorkspaceName;

	public RetrieveWorkspaceDetailsTask(
			String buildToolkitPath,
			String serverURI,
			String userId,
			String password,
			int timeout,
			String buildDefinitionId,
			String buildWorkspaceName,
			Locale clientLocale,
			boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.clientLocale = clientLocale;
		this.buildDefinitionId = buildDefinitionId;
		this.buildWorkspaceName = buildWorkspaceName;
		
		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n"
					+ "serverURI: %s\n"
					+ "userId : %s\n"
					+ "buildDefinitionId: %s\n"
					+ "buildWorkspaceName: %s\n",
					buildToolkitPath, 
					serverURI,
					userId,
					(buildDefinitionId == null) ? "n/a" : buildDefinitionId,
					(buildWorkspaceName == null) ? "n/a" : buildWorkspaceName));
		}
	}
	
	@Override
	public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
		getLogger().entering(this.getClass().getName(), "invoke");
		try {
			printDebugMsgStart();
			// We are not checking the following
			// Null/empty build definition ID
			// Null/empty build workspace name
			// since we can assume that the caller does the sanity check. 
			
			// If the buildtoolkit path is not found in the agent,
			// then this will fail with an appropriate exception message sent to the user.
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);

			// Resolve the workspace (from the build definition or the build workspace)
			// and get the build workspace UUID
			@SuppressWarnings("unchecked")
			Map<String, String> props =
					(Map<String, String>) facade.invoke("getWorkspaceUUID", new Class[] { //$NON-NLS-1$
							String.class, // serverURI,
							String.class, // userId,
							String.class, // password,
							int.class, // timeout,
							String.class, // build definition ID
							String.class, // build workspace
							Object.class, // listener
							Locale.class // clientLocale
					}, serverURI, userId, password,
					   timeout, buildDefinitionId, buildWorkspaceName,
					   new TaskListenerWrapper(getListener()), 
					   clientLocale);
			
			printDebugMsgEnd(props.get(WORKSPACE_UUID_KEY));
			
			// Workspace UUID cannot be null. If the workspace was not found,
			// then we would be getting an exception.
			return props.get(WORKSPACE_UUID_KEY);
		} catch (Exception exp) {
			Throwable eToReport = exp;
			if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
			}
			if (eToReport instanceof InterruptedException) {
				getListener().getLogger().println(getInterruptedErrMsg(eToReport));				
				throw (InterruptedException) eToReport;
			} 
			String message = getErrorErrMsg(eToReport);
			if (Helper.unexpectedFailure(eToReport)) {
	            Functions.printStackTrace(eToReport, getListener().error(message));
			}
			throw new IOException(message); 
		} finally {
			getLogger().exiting(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		}
	}

	private String getInterruptedErrMsg(Throwable eToReport) {
		if (buildDefinitionId != null) {
			return Messages.RetrieveWorkspaceDetails_build_definition_interrupted(
				buildDefinitionId, eToReport.getMessage());
		} else {
			return Messages.RetrieveWorkspaceDetails_build_workspace_interrupted(
					buildWorkspaceName, eToReport.getMessage());
		}
	}
	
	private String getErrorErrMsg(Throwable eToReport) {
		if (buildDefinitionId != null) {
			return Messages.RetrieveWorkspaceDetails_build_definition_error(
					buildDefinitionId, eToReport.getMessage());
		} else {
			return Messages.RetrieveWorkspaceDetails_build_workspace_error(
					buildWorkspaceName, eToReport.getMessage());
		}
	}

	private void printDebugMsgStart() {
		if (getIsDebug()) {
			getListener().getLogger().println(
					String.format(RETRIEVE_WORKSPACE_DETAILS_STARTED_MSG, 
						(buildDefinitionId == null) ? "n/a" : buildDefinitionId,
						(buildWorkspaceName == null) ? "n/a" : buildWorkspaceName));
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			logFine(String.format(RETRIEVE_WORKSPACE_DETAILS_STARTED_MSG, 
					(buildDefinitionId == null) ? "n/a" : buildDefinitionId,
					(buildWorkspaceName == null) ? "n/a" : buildWorkspaceName));
		}
	}
	
	private void printDebugMsgEnd(String workspaceUUID) {
		if (getIsDebug()) {
			getListener().getLogger().println(
					String.format(RETRIEVE_WORKSPACE_DETAILS_COMPLETED_MSG,
					workspaceUUID));
		}
		if (getLogger().isLoggable(Level.FINE)) {
			logFine(String.format(RETRIEVE_WORKSPACE_DETAILS_COMPLETED_MSG,
					workspaceUUID));
		}
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
