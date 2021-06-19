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

import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
import com.ibm.team.build.hjplugin.steps.RetrieveSnapshotStepResponse;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCTask;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildConstants;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

import hudson.Functions;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class RetrieveSnapshotTask extends RTCTask<RTCBuildStepResponse> {
	private static final String RETRIEVE_SNAPSHOT_FOR_BUILD_STARTED_MSG = "Retrieving snapshot "
			+ "for build result %s";

	private static final String RETRIEVE_SNAPSHOT_FOR_BUILD_COMPLETE_MSG = "Retrieving snapshot complete. "//$NON-NLS-1$
			+ "Return values : \n" + //$NON-NLS-1$
			"Snapshot UUID - %s\nSnapshot Name - %s\n"; //$NON-NLS-1$
	
	private static final Logger LOGGER = Logger.getLogger(RetrieveSnapshotTask.class
													.getName());
	private String buildToolkitPath;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	private String buildResultUUID;
	
	public RetrieveSnapshotTask(
			String buildToolkitPath,
			String serverURI,
			String userId,
			String password,
			int timeout,
			String buildResultUUID,
			boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildResultUUID = buildResultUUID;

		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n"
					+ "serverURI: %s\n"
					+ "userId : %s\n"
					+ "buildResultUUID: %s\n",
					buildToolkitPath, 
					serverURI,
					userId,
					buildResultUUID));
		}
	}

	private static final long serialVersionUID = 1L;

	@Override
	public RTCBuildStepResponse invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
		getLogger().entering(this.getClass().getName(), "invoke");
		try {
			// We are not checking the following
			// Null/empty build result UUID
			// since we can assume that the caller does the sanity check. 
			
			// If the buildtoolkit path is not found in the agent, then this will fail with an 
			// appropriate exception message sent to the user.
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			if (getIsDebug()) {
				getListener().getLogger().println(String.format(RETRIEVE_SNAPSHOT_FOR_BUILD_STARTED_MSG, 
													buildResultUUID));
			}
			if (LOGGER.isLoggable(Level.FINE)) {
				logFine(String.format(RETRIEVE_SNAPSHOT_FOR_BUILD_STARTED_MSG,
											buildResultUUID));
			}
			@SuppressWarnings("unchecked")
			Map<String, String> ret = (Map<String, String>) facade.invoke("retrieveSnapshotFromBuild", 
							new Class[] { String.class, // serverURI
									String.class, // userId
									String.class, // password
									int.class, // timeout
									String.class, // buildResulUUID,
									Object.class, // listener
									Locale.class }, // clientLocale
							serverURI, getUserId(), getPassword(), 
							timeout, buildResultUUID,
							new TaskListenerWrapper(getListener()), 
							Locale.getDefault());
			
			// Once the method completes, get back the snapshot UUID and name.
			String snapshotUUID = ret.getOrDefault(RTCBuildConstants.SNAPSHOT_UUID_KEY, "");
			String snapshotName = ret.getOrDefault(RTCBuildConstants.SNAPSHOT_NAME_KEY, "");
			if (getIsDebug()) {
				getListener().getLogger().println(String.format(RETRIEVE_SNAPSHOT_FOR_BUILD_COMPLETE_MSG,
						snapshotUUID, snapshotName));
			}
			if (getLogger().isLoggable(Level.FINE)) {
				logFine(String.format(RETRIEVE_SNAPSHOT_FOR_BUILD_COMPLETE_MSG,
									snapshotUUID, snapshotName));
			}
			return new RetrieveSnapshotStepResponse(snapshotName, snapshotUUID);
		} catch (Exception exp) {
			Throwable eToReport = exp;
    		if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
    		}
    		if (eToReport instanceof InterruptedException) {
				getListener().getLogger().println(
						Messages.RetrieveSnapshotStep_interrupted(buildResultUUID, eToReport.getMessage()));
    			throw (InterruptedException) eToReport;
    		} 
    		String message = Messages.RetrieveSnapshotStep_error(buildResultUUID, eToReport.getMessage());
    		if (Helper.unexpectedFailure(eToReport)) {
                Functions.printStackTrace(eToReport, getListener().error(message));
    		}
    		throw new IOException(message); 
		} finally {
			getLogger().exiting(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		}
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}

	public String getUserId() {
		return userId;
	}

	public String getPassword() {
		return password;
	}
}