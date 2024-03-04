/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2021, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tasks;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
import com.ibm.team.build.hjplugin.steps.WaitForBuildStepResponse;
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

public class WaitForBuildTask extends RTCTask<RTCBuildStepResponse> {
	private static final String WAIT_FOR_BUILD_STARTED_MSG = "Waiting "
			+ "for build result %s for build states %s till %d seconds";

	private static final String WAIT_FOR_BUILD_COMPLETE_MSG = "WaitForBuild complete. "//$NON-NLS-1$
			+ "Return values : \n" + //$NON-NLS-1$
			"buildState - %s\nbuildStatus - %s\ntimed out - %s"; //$NON-NLS-1$
	
	private static final String TIMEDOUT = "timedout";
	private static final Logger LOGGER = Logger.getLogger(WaitForBuildTask.class
													.getName());
	private String buildToolkitPath;
	private String serverURI;
	private String userId;
	private String password;
	private int timeout;
	private String buildResultUUID;
	private String [] buildStatesToWait;
	private long waitBuildTimeout;
	private long waitBuildInterval;
	
	public WaitForBuildTask(
			String buildToolkitPath,
			String serverURI,
			String userId,
			String password,
			int timeout,
			String buildResultUUID,
			String[] buildStatesToWait,
			long waitBuildTimeout, long waitBuildInterval,
			boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildResultUUID = buildResultUUID;
		this.buildStatesToWait = buildStatesToWait;
		this.waitBuildTimeout = waitBuildTimeout;
		this.waitBuildInterval = waitBuildInterval;

		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n"
					+ "serverURI: %s\n"
					+ "userId : %s\n"
					+ "buildResultUUID: %s\n"
					+ "buildStates : %s\n"
					+ "waitBuildTimeout : %d\n"
					+ "waitBuildInterval : %d\n"
					+ "isDebug : %s",
					buildToolkitPath, 
					serverURI,
					userId,
					buildResultUUID, 
					Arrays.deepToString(buildStatesToWait),
					waitBuildTimeout, waitBuildInterval,
					Boolean.toString(getIsDebug())));
		}
	}

	private static final long serialVersionUID = 1L;

	@Override
	public RTCBuildStepResponse invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
		getLogger().entering(this.getClass().getName(), "invoke");
		try {
			// We are not checking the following
			// Invalid build state in the states array or non empty states array.
			// Null/empty build result UUID
			// waitBuildTiemout is either -1 or greater than 0
			// since we can assume that the caller does the sanity check. 
			
			// If the buildtoolkit path is not found in the agent, then this will fail with an 
			// appropriate exception message sent to the user.
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			if (getIsDebug()) {
				getListener().getLogger().println(String.format(WAIT_FOR_BUILD_STARTED_MSG, 
						buildResultUUID, Arrays.toString(buildStatesToWait), waitBuildTimeout));
			}
			if (LOGGER.isLoggable(Level.FINE)) {
				logFine(String.format(WAIT_FOR_BUILD_STARTED_MSG,
							buildResultUUID, Arrays.toString(buildStatesToWait), waitBuildTimeout));
			}
			@SuppressWarnings("unchecked")
			Map<String, String> ret = (Map<String, String>) facade.invoke("waitForBuild", 
							new Class[] { String.class, // serverURI
									String.class, // userId
									String.class, // password
									int.class, // timeout
									String.class, // buildResulUUID,
									Object.class, // buildStates
									long.class,// waitBuildTimeout
									long.class, // waitBuildInterval
									boolean.class, // isDebug
									Object.class, // listener
									Locale.class }, // clientLocale
							serverURI, getUserId(), getPassword(), 
							timeout, buildResultUUID, buildStatesToWait, waitBuildTimeout, waitBuildInterval,
							getIsDebug(), new TaskListenerWrapper(getListener()), Locale.getDefault());
			
			// Once the method completes, get back the build state,  
			// build status and whether the step returned from a timeout.
			String buildState = ret.get(RTCBuildConstants.BUILD_STATE_KEY);
			String buildStatus = ret.get(RTCBuildConstants.BUILD_STATUS_KEY);
			boolean timedout = Boolean.parseBoolean(ret.get(TIMEDOUT));
			if (getIsDebug()) {
				getListener().getLogger().println(String.format(WAIT_FOR_BUILD_COMPLETE_MSG,
						buildState, buildStatus, Boolean.toString(timedout)));
			}
			if (getLogger().isLoggable(Level.FINE)) {
				logFine(String.format(WAIT_FOR_BUILD_COMPLETE_MSG,
						buildState, buildStatus, Boolean.toString(timedout)));
			}
			return new WaitForBuildStepResponse(buildState, buildStatus, timedout);
		} catch (Exception exp) {
			Throwable eToReport = exp;
    		if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
    		}
    		if (eToReport instanceof InterruptedException) {
				getListener().getLogger().println(
						Messages.WaitBuildStep_interrupted(buildResultUUID, eToReport.getMessage()));
    			throw (InterruptedException) eToReport;
    		} 
    		String message = Messages.WaitBuildStep_error(buildResultUUID, eToReport.getMessage());
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