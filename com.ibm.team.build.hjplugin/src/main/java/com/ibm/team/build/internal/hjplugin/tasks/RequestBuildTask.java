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

import hudson.Functions;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.hjplugin.steps.RTCBuildStepResponse;
import com.ibm.team.build.hjplugin.steps.RequestBuildStepResponse;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCTask;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.TaskListenerWrapper;

public class RequestBuildTask extends RTCTask<RTCBuildStepResponse> {
	private static final String REQUEST_BUILD_COMPLETE_MSG = "Request Build task complete." //$NON-NLS-1$
						+ " Return values \n" + //$NON-NLS-1$ 
						"Build Result UUID - %s. If build result UUID is empty, " //$NON-NLS-1$
						+ "then check whether any of the supporting engines is active"; //$NON-NLS-1$
	private static final String REQUESTING_BUILD_FOR_BUILD_DEFINITION_MSG = 
						"Requesting build for build definition %s"; //$NON-NLS-1$
	
	private static final String BUILD_RESULT_UUID = "buildResultUUID"; //$NON-NLS-1$
	
	private static final Logger LOGGER = Logger.getLogger(RequestBuildTask.class
													.getName());
	final private String buildToolkitPath;
	final private String serverURI;
	final private String userId;
	final private String password;
	final private int timeout;
	final private String buildDefinitionId;
	final private String[] propertiesToDelete;
	final private HashMap<String, String> propertiesToAddOrOverride;
	
	public RequestBuildTask(
			String buildToolkitPath,
			String serverURI,
			String userId,
			String password,
			int timeout,
			String buildDefinitionId,
			String[] propertiesToDelete,
			HashMap<String, String> propertiesToAddOrOverride,
			boolean isDebug, TaskListener listener) {
		super(isDebug, listener);
		this.buildToolkitPath = buildToolkitPath;
		this.serverURI = serverURI;
		this.userId = userId;
		this.password = password;
		this.timeout = timeout;
		this.buildDefinitionId = buildDefinitionId;
		this.propertiesToDelete = propertiesToDelete;
		this.propertiesToAddOrOverride = propertiesToAddOrOverride;

		if (getIsDebug()) {
			listener.getLogger().println(String.format(
					  "buildToolkitPath: %s\n" //$NON-NLS-1$
					+ "serverURI: %s\n" //$NON-NLS-1$
					+ "userId : %s\n" //$NON-NLS-1$
					+ "buildDefinitionId: %s\n", //$NON-NLS-1$
					buildToolkitPath, 
					serverURI,
					userId,
					buildDefinitionId));
			if (propertiesToDelete != null && propertiesToDelete.length > 0) {
				listener.getLogger().println("Properties to delete:"); //$NON-NLS-1$
				for (String propertyName : propertiesToDelete) {
					listener.getLogger().println("Property Name: " + propertyName); //$NON-NLS-1$
				}
			}
			if (propertiesToAddOrOverride != null && !propertiesToAddOrOverride.isEmpty()) {
				listener.getLogger().println("Properties to add or override:"); //$NON-NLS-1$
				for (String propertyName : propertiesToAddOrOverride.keySet()) {
					listener.getLogger().println("Property Name: " + propertyName); //$NON-NLS-1$
					listener.getLogger().println("Property Value: " + propertiesToAddOrOverride.get(propertyName)); //$NON-NLS-1$
				}
			}
		}
	}

	private static final long serialVersionUID = 1L;

	@Override
	public RTCBuildStepResponse invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
		getLogger().entering(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		try {
			// Invoke a method in RTCFacade to request a build
			if (getIsDebug()) {
				getListener().getLogger().println(String.format(REQUESTING_BUILD_FOR_BUILD_DEFINITION_MSG, buildDefinitionId));
			}
			if (getLogger().isLoggable(Level.FINE)) {
				logFine(String.format(REQUESTING_BUILD_FOR_BUILD_DEFINITION_MSG, buildDefinitionId)); //$NON-NLS-1$
			}
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			@SuppressWarnings("unchecked")
			Map<String, String> ret = (Map<String, String>)facade.invoke("requestBuild",  //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildDefinition
							String[].class, // propertiesToDelete
							Map.class, // propertiesToAddOrOverride 
							Object.class, // listener
							Locale.class }, // clientLocale
					serverURI, getUserId(), getPassword(), 
					timeout, buildDefinitionId, propertiesToDelete, propertiesToAddOrOverride, 
					new TaskListenerWrapper(getListener()), Locale.getDefault());
			// If we are here, then the request is successful and it has a build result UUID
			// Another possibility is that a request could not be created for various reasons like  
			// no supporting engines , or given supporting engines are inactive or all
			// supporting engines are inactive
			String buildResultUUID = ret.get(BUILD_RESULT_UUID);
			if (getIsDebug()) {
				getListener().getLogger().println(String.format(REQUEST_BUILD_COMPLETE_MSG, buildResultUUID));
			}
			if (getLogger().isLoggable(Level.FINE)) {
				logFine(String.format(REQUEST_BUILD_COMPLETE_MSG, buildResultUUID));
			}
			return new RequestBuildStepResponse(buildResultUUID);
		} catch (Exception exp) {
			Throwable eToReport = exp;
    		if (eToReport instanceof InvocationTargetException && exp.getCause() != null) {
				eToReport = exp.getCause();
    		}
    		if (eToReport instanceof InterruptedException) {
				getListener().getLogger().println(
						Messages.RequestBuildStep_interrupted(buildDefinitionId, eToReport.getMessage()));
    			throw (InterruptedException) eToReport;
    		} 
    		String message = Messages.RequestBuildStep_error(buildDefinitionId, eToReport.getMessage());
    		if (Helper.unexpectedFailure(eToReport)) {
                Functions.printStackTrace(eToReport, getListener().error(message));
    		}
    		
    		throw new IOException(message);
		} finally {
			getLogger().exiting(this.getClass().getName(), "invoke"); //$NON-NLS-1$
		}
	}

	@Override
	public Logger getLogger() {
		return LOGGER;
	}

	private String getUserId() {
		return userId;
	}

	private String getPassword() {
		return password;
	}
}