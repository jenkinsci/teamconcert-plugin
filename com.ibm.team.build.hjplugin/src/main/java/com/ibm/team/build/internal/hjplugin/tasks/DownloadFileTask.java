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

import com.ibm.team.build.hjplugin.steps.DownloadFileStepResponse;
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

public class DownloadFileTask extends RTCTask<RTCBuildStepResponse> {

	private static final Logger LOGGER = Logger.getLogger(DownloadFileTask.class
											.getName());

	final private String buildToolkitPath;
	final private String serverURI;
	final private String userId;
	final private String password;
	final private int timeout;
	final private String buildResultUUID;
	final private String fileName;
	final private String componentName;
	final private String contentId;
	final private String contributionType;
	final private String destinationFolder;
	final private String destinationFileName;
	
	/**
	 * Key to identify the name of a file that is downloaded from the 
	 * repository, in a map.
	 */
	final static private String FILENAME_KEY = "fileName"; //$NON-NLS-1$
	
	/**
	 * Key to identify the full path of the file that stores the content 
	 * downloaded from the repository, in a map.
	 */
	final static private String FILEPATH_KEY = "filePath"; //$NON-NLS-1$
	
	
	/**
	 * Key to identify the internal UUID of the contribution.
	 * This is for testing purposes only
	 */
	final static private String INTERNAL_UUID = "internalUUID"; //$NON-NLS-1$
	
	public DownloadFileTask(String buildToolkitPath,
			String serverURI,
			String userId,
			String password,
			int timeout,
			String buildResultUUID,
			String fileName,
			String componentName,
			String contentId,
			String contributionType,
			String destinationFolder,
			String destinationFileName,
			boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildResultUUID = buildResultUUID;
		this.fileName = fileName;
		this.componentName = componentName;
		this.contentId = contentId;
		this.contributionType = contributionType;
		this.destinationFolder = destinationFolder;
		this.destinationFileName = destinationFileName;
		
		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n" //$NON-NLS-1$
					+ "serverURI: %s\n" //$NON-NLS-1$
					+ "userId : %s\n" //$NON-NLS-1$
					+ "buildResultUUID: %s\n" //$NON-NLS-1$
					+ "fileName: %s\n" //$NON-NLS-1$
					+ "componentName: %s\n" //$NON-NLS-1$
					+ "contentId: %s\n"  //$NON-NLS-1$
					+ "contributionType: %s\n" //$NON-NLS-1$
					+ "destinationFolder: %s\n" //$NON-NLS-1$
					+ "destinationFileName: %s\n", //$NON-NLS-1$
					buildToolkitPath, 
					serverURI,
					userId,
					buildResultUUID,
					fileName,
					componentName,
					contentId,
					contributionType,
					destinationFolder,
					destinationFileName));
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
			Map<String, String> ret = (Map<String, String>)facade.invoke("downloadFile",  //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildResultUUID
							String.class, // fileName
							String.class, // componentName
							String.class, // contentId
							String.class, // contributionType
							String.class, // destinationFolder
							String.class, // destinationFileName
							Object.class, // listener
							Locale.class }, // clientLocale
					serverURI, userId, password, 
					timeout, buildResultUUID, 
					fileName, componentName, contentId,
					contributionType, destinationFolder, 
					destinationFileName, new TaskListenerWrapper(getListener()), 
					Locale.getDefault());
			DownloadFileStepResponse response = new DownloadFileStepResponse(
							ret.get(FILENAME_KEY), ret.get(FILEPATH_KEY));
			response.setInternalUUID(ret.get(INTERNAL_UUID));
			printDebugMsgsEnd();
			return response;
		} catch (Exception exp) {
			Throwable eToReport = exp;
    		if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
    		}
    		if (eToReport instanceof InterruptedException) {
				getListener().getLogger().println(
						getDownloadFileInterruptedErrorMsg(eToReport.getMessage()));
    			throw (InterruptedException) eToReport;
    		} 
    		String message = getDownloadFileErrorMsg(eToReport.getMessage());
    		if (Helper.unexpectedFailure(eToReport)) {
                Functions.printStackTrace(eToReport, getListener().error(message));
    		}
			throw new IOException(exp.getMessage());
		} finally {
			getLogger().exiting(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		}
	}
	
	private String getDownloadFileErrorMsg(String errorMsg) {
		if (RTCBuildConstants.ARTIFACT_TYPE.equals(contributionType)) {
			return Messages.DownloadArtifactStep_error(buildResultUUID, errorMsg);
		} else {
			return Messages.DownloadLogStep_error(buildResultUUID, errorMsg);
		}
	}

	private String getDownloadFileInterruptedErrorMsg(String errorMsg) {
		if (RTCBuildConstants.ARTIFACT_TYPE.equals(contributionType)) {
			return Messages.DownloadArtifactStep_interrupted(buildResultUUID, errorMsg);
		} else {
			return Messages.DownloadLogStep_interrupted(buildResultUUID, errorMsg);
		}
	}

	private void printDebugMsgsStart() {
		String DOWNLOAD_LOG_MSG = String.format("Downloading log for " //$NON-NLS-1$
				+ "build result %s start.", buildResultUUID); //$NON-NLS-1$
		String DOWNLOAD_ARTIFACT_MSG = String.format("Downloading artifact for " //$NON-NLS-1$ 
				+ "build result %s start.", buildResultUUID); //$NON-NLS-1$ 
		switch (contributionType) { 
			case RTCBuildConstants.ARTIFACT_TYPE:
				if (getLogger().isLoggable(Level.FINEST)) 
					getLogger().finest(DOWNLOAD_ARTIFACT_MSG);
				if (getIsDebug()) 
					getListener().getLogger().println(DOWNLOAD_ARTIFACT_MSG);
				break;
			case RTCBuildConstants.LOG_TYPE:
				if (getLogger().isLoggable(Level.FINEST)) 
					getLogger().finest(DOWNLOAD_LOG_MSG);
				if (getIsDebug()) 
					getListener().getLogger().println(DOWNLOAD_LOG_MSG);
				break;
			default:
				break;
		}
	}

	private void printDebugMsgsEnd() {
		String DOWNLOAD_LOG_MSG = String.format("Downloading log for " //$NON-NLS-1$
				+ "build result %s complete.", buildResultUUID); //$NON-NLS-1$
		String DOWNLOAD_ARTIFACT_MSG = String.format("Downloading artifact for " //$NON-NLS-1$ 
				+ "build result %s complete.", buildResultUUID); //$NON-NLS-1$ 
		switch (contributionType) { 
			case RTCBuildConstants.ARTIFACT_TYPE:
				if (getLogger().isLoggable(Level.FINEST)) 
					getLogger().finest(DOWNLOAD_ARTIFACT_MSG);
				if (getIsDebug()) 
					getListener().getLogger().println(DOWNLOAD_ARTIFACT_MSG);
				break;
			case RTCBuildConstants.LOG_TYPE:
				if (getLogger().isLoggable(Level.FINEST)) 
					getLogger().finest(DOWNLOAD_LOG_MSG);
				if (getIsDebug()) 
					getListener().getLogger().println(DOWNLOAD_LOG_MSG);
				break;
			default:
				break;
		}
	}
		
	@Override
	protected Logger getLogger() {
		return LOGGER;
	}
}
