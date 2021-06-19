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
import java.io.OutputStream;
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
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;

public class GenerateChangelogTask extends RTCTask<Map<String,Object>> {
	private static final String GENERATE_CHANGE_LOG_STARTED_MSG = "Generating change log for " //$NON-NLS-1$
			+ " snapshot %s, owning workspace UUID %s, previous snapshot %s"; //$NON-NLS-1$

	private static final String GENERATE_CHANGE_LOG_COMPLETED_MSG = "Generating change log complete."; //$NON-NLS-1$
	
	private static final long serialVersionUID = 1L;
	
	private static final Logger LOGGER = Logger.getLogger(GenerateChangelogTask.class.getName());

	private String buildToolkitPath;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	private String snapshotUUID;
	private String workspaceUUID;
	private String previousSnapshotUUID;
	private RemoteOutputStream changeLog;
	private Locale clientLocale;
	
    public GenerateChangelogTask(
    		String buildToolkitPath,
    		String serverURI,
    		String userId,
    		String password,
    		int timeout,
    		String snapshotUUID,
    		String workspaceUUID,
    		String previousSnapshotUUID,
    		RemoteOutputStream changeLog,
    		Locale clientLocale,
    		boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.snapshotUUID = snapshotUUID;
		this.workspaceUUID = workspaceUUID;
		this.previousSnapshotUUID = previousSnapshotUUID;
		this.changeLog = changeLog;
		this.clientLocale = clientLocale;
		
		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n"
					+ "serverURI: %s\n"
					+ "userId : %s\n"
					+ "snapshotUUID: %s\n"
					+ "workspaceUUID: %s\n"
				    + "previousSnapshotUUID: %s\n",
					buildToolkitPath, 
					serverURI,
					userId,
					snapshotUUID,
					workspaceUUID,
					(previousSnapshotUUID == null) ? "n/a" : previousSnapshotUUID));
		}
	}
    
	@Override
	public Map<String, Object> invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
		getLogger().entering(this.getClass().getName(), "invoke"); //$NON-NLS-1$

		try {
			printDebugMsgStart();
			
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			@SuppressWarnings("unchecked")
			Map<String, Object> ret =
					// Resolve the stream and get stream UUID
					(Map<String, Object> ) facade.invoke("generateChangelog", //$NON-NLS-1$ 
							new Class[] { 
								String.class, // serverURI,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // current Snapshot UUID
								String.class, // owning workspace UUID
								String.class, // previous Snapshot UUID
								OutputStream.class, // changelog
								Object.class, // listener
								Locale.class // clientLocale
						}, serverURI, userId, password, timeout,
							snapshotUUID, workspaceUUID, previousSnapshotUUID, 
							changeLog, new TaskListenerWrapper(getListener()), clientLocale);
			
			return ret;
		} catch (Exception exp) {
			Throwable eToReport = exp;
			if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
			}
			if (eToReport instanceof InterruptedException) {
				getListener().getLogger().println(
						Messages.GenerateChangelog_interrupted(snapshotUUID, eToReport.getMessage()));
				throw (InterruptedException) eToReport;
			} 
			String message = Messages.GenerateChangelog_error(snapshotUUID, eToReport.getMessage());
			if (Helper.unexpectedFailure(eToReport)) {
	            Functions.printStackTrace(eToReport, getListener().error(message));
			}
			throw new IOException(message); 
		} finally {
			printDebugMsgEnd();
			getLogger().exiting(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		}
	}

	private void printDebugMsgEnd() {
		if (getIsDebug()) {
			getListener().getLogger().println(GENERATE_CHANGE_LOG_COMPLETED_MSG); 
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			logFine(GENERATE_CHANGE_LOG_COMPLETED_MSG);
		}
		
	}

	private void printDebugMsgStart() {
		if (getIsDebug()) {
			getListener().getLogger().println(
					String.format(GENERATE_CHANGE_LOG_STARTED_MSG, 
							snapshotUUID, workspaceUUID, 
							(previousSnapshotUUID == null) ? "n/a" : previousSnapshotUUID ));
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			logFine(String.format(GENERATE_CHANGE_LOG_STARTED_MSG,
							snapshotUUID, workspaceUUID, 
							(previousSnapshotUUID == null) ? "n/a" : previousSnapshotUUID ));
		}
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
