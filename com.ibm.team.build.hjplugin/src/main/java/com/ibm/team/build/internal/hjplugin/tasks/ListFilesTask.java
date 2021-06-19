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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.hjplugin.steps.ListFilesStepResponse.ListFilesStepResponseBuilder;
import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
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

public class ListFilesTask extends RTCTask<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(ListFilesTask.class
											.getName());

	final private String buildToolkitPath;
	final private String serverURI;
	final private String userId;
	final private String password;
	final private int timeout;
	final private String buildResultUUID;
	final private String fileNameOrPattern;
	final private String componentName;
	final private String contributionType;
	final private int maxResults;

	private TaskListenerWrapper listenerWrapper;
	
	private static final String FILEINFOS_KEY = "fileInfos"; //$NON-NLS-1$

	public ListFilesTask(
			String buildToolkitPath,
			String serverURI,
			String userId,
			String password,
			int timeout,
			String buildResultUUID,
			String fileNameOrPattern,
			String componentName,
			String contributionType,
			int maxResults,
			boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.listenerWrapper = new TaskListenerWrapper(listener);
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildResultUUID = buildResultUUID;
		this.fileNameOrPattern = fileNameOrPattern;
		this.componentName = componentName;
		this.contributionType = contributionType;
		this.maxResults = maxResults;

		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n" //$NON-NLS-1$
					+ "serverURI: %s\n" //$NON-NLS-1$
					+ "userId : %s\n" //$NON-NLS-1$
					+ "buildResultUUID: %s\n" //$NON-NLS-1$
					+ "fileNameOrPattern: %s\n" //$NON-NLS-1$
					+ "componentName: %s\n" //$NON-NLS-1$
					+ "contributionType: %s\n" //$NON-NLS-1$
					+ "maxResults: %d\n", //$NON-NLS-1$
					buildToolkitPath, 
					serverURI,
					userId,
					buildResultUUID,
					fileNameOrPattern,
					componentName,
					contributionType,
					maxResults));
		}
	}

	private static final long serialVersionUID = 1L;

	@Override
	public RTCBuildStepResponse invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
		getLogger().entering(this.getClass().getName(), "invoke"); //$NON-NLS-1$

		try {
			printDebugMsgsStart();
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			@SuppressWarnings("unchecked")
			Map<String, Object> ret = (Map<String, Object>)facade.invoke("listFiles",  //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildResultUUID
							String.class, // fileNameOrPattern
							String.class, // componentName
							String.class, // contributionType
							int.class, // maxResults
							Object.class, // listener
							Locale.class }, // clientLocale
					serverURI, userId, password, 
					timeout, buildResultUUID, 
					fileNameOrPattern, componentName, contributionType,
					maxResults, new TaskListenerWrapper(getListener()), 
					Locale.getDefault());
			@SuppressWarnings("unchecked")
			List<List<String>> files = (List<List<String>>) ret.get(FILEINFOS_KEY); //$NON-NLS-1$
			// files cannot be null if we reach this point.
			ListFilesStepResponseBuilder builder= new ListFilesStepResponseBuilder(files.size());
			for (List<String> file : files)  {
				// Index by position to get the right fields for constructing a 
				// fileInfo
				builder.add(file.get(0), file.get(1),
						file.get(2), file.get(3), file.get(4),
						Long.parseLong(file.get(5)), file.get(6));
			}
			return builder.build();
		} catch (Exception exp) {
			Throwable eToReport = exp;
    		if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
    		}
    		if (eToReport instanceof InterruptedException) {
    			listenerWrapper.getLogger().println(
						getListFilesInterruptedErrorMsg(eToReport.getMessage()));
    			throw (InterruptedException) eToReport;
    		} 
    		String message = getListFilesErrorMsg(eToReport.getMessage());
    		if (Helper.unexpectedFailure(eToReport)) {
                Functions.printStackTrace(eToReport, getListener().error(message));
    		}
			throw new IOException(exp.getMessage());
		} finally {
			printDebugMsgsEnd();
			getLogger().exiting(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		}
	}

	private String getListFilesErrorMsg(String errorMsg) {
		if (RTCBuildConstants.ARTIFACT_TYPE.equals(contributionType)) {
			return Messages.ListArtifactsStep_error(buildResultUUID, errorMsg);
		} else {
			return Messages.ListLogsStep_error(buildResultUUID, errorMsg);
		}
	}

	private String getListFilesInterruptedErrorMsg(String errorMsg) {
		if (RTCBuildConstants.ARTIFACT_TYPE.equals(contributionType)) {
			return Messages.ListArtifactsStep_interrupted(buildResultUUID, errorMsg);
		} else {
			return Messages.ListLogsStep_interrupted(buildResultUUID, errorMsg);
		}
	}
	
	private void printDebugMsgsStart() {
		String LISTING_LOGS_MSG = String.format("Listing logs for build result %s", //$NON-NLS-1$ 
				buildResultUUID);
		String LISTING_ARTIFACTS_MSG = String.format("Listing artifacts for build result %s", //$NON-NLS-1$ 
				buildResultUUID);
		switch (contributionType) { 
			case RTCBuildConstants.ARTIFACT_TYPE:
				if (getLogger().isLoggable(Level.FINEST)) 
					getLogger().finest(LISTING_ARTIFACTS_MSG);
				if (getIsDebug()) 
					listenerWrapper.getLogger().println(LISTING_ARTIFACTS_MSG);
				break;
			case RTCBuildConstants.LOG_TYPE:
				if (getLogger().isLoggable(Level.FINEST)) 
					getLogger().finest(LISTING_LOGS_MSG);
				if (getIsDebug()) 
					listenerWrapper.getLogger().println(LISTING_LOGS_MSG);
				break;
			default:
				break;
		}
	}
	
	private void printDebugMsgsEnd() {
		String LIST_LOGS_COMPLETE_MSG = "List Logs task complete."; //$NON-NLS-1$
		String LIST_ARTIFACTS_COMPLETE_MSG = "List Artifacts task complete."; //$NON-NLS-1$
		switch (contributionType) { 
			case RTCBuildConstants.ARTIFACT_TYPE:
				if (getLogger().isLoggable(Level.FINE)) 
					getLogger().finest(LIST_ARTIFACTS_COMPLETE_MSG);
				if (getIsDebug()) 
					listenerWrapper.getLogger().println(LIST_ARTIFACTS_COMPLETE_MSG);
				break;
			case RTCBuildConstants.LOG_TYPE:
				if (getLogger().isLoggable(Level.FINE)) 
					getLogger().finest(LIST_LOGS_COMPLETE_MSG);
				if (getIsDebug()) 
					listenerWrapper.getLogger().println(LIST_LOGS_COMPLETE_MSG);
				break;
			default:
				break;
		}
	}

	@Override
	public Logger getLogger() {
		return LOGGER;
	}
}
