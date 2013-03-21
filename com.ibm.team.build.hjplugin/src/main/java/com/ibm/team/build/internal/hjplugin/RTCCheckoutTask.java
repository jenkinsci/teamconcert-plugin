/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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
import hudson.FilePath.FileCallable;
import hudson.model.BuildListener;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

public class RTCCheckoutTask implements FileCallable<Void> {
	
	private String buildToolkit;
    private String serverURI;
	private String userId;
	private Secret password;
	private int timeout;
	private String buildWorkspace;
	private BuildListener listener;
	private String baselineSetName;
	private RemoteOutputStream changeLog;
	private boolean isRemote;
	private String contextStr;
	private boolean debug;

	private static final long serialVersionUID = 1L;

	public RTCCheckoutTask(String contextStr, String buildToolkit, String serverURI, String userId, String password,
			int timeout, String buildWorkspace,
			String baselineSetName,
			BuildListener listener, RemoteOutputStream changeLog, boolean isRemote,
			boolean debug) {
    	
		this.contextStr = contextStr;
		this.buildToolkit = buildToolkit;
    	this.serverURI = serverURI;
    	this.userId = userId;
    	this.password = Secret.fromString(password);
    	this.timeout = timeout;
    	this.buildWorkspace = buildWorkspace;
    	this.baselineSetName = baselineSetName;
    	this.listener = listener;
    	this.changeLog = changeLog;
    	this.isRemote = isRemote;
    	this.debug = debug;
	}

	public Void invoke(File workspace, VirtualChannel channel) throws IOException {
		if (debug) {
			listener.getLogger().println("Running " + contextStr); //$NON-NLS-1$
			listener.getLogger().println("serverURI " + serverURI); //$NON-NLS-1$
			listener.getLogger().println("userId " + userId); //$NON-NLS-1$
			listener.getLogger().println("timeout " + timeout); //$NON-NLS-1$
			listener.getLogger().println("buildWorkspace " + buildWorkspace); //$NON-NLS-1$
			listener.getLogger().println("listener is " + (listener == null ? "null" : "not null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			listener.getLogger().println("Running remote " + isRemote); //$NON-NLS-1$
			listener.getLogger().println("buildToolkit property " + buildToolkit); //$NON-NLS-1$
		}

		try {
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
			facade.invoke("checkout", new Class[] { //$NON-NLS-1$
					String.class, // serverURI,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, // workspaceName,
					String.class, // hjWorkspacePath,
					OutputStream.class, // changeLog,
					String.class, // baselineSetName,
					Object.class, // listener)
			}, serverURI, userId, Secret.toString(password),
					timeout, buildWorkspace,
					workspace.getAbsolutePath(), changeLog,
					baselineSetName,
					listener);

    	} catch (InvocationTargetException e) {
    		Throwable eToReport = e.getCause();
    		if (eToReport == null) {
    			eToReport = e;
    		}
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checkout_failure(eToReport.getMessage()));
    		eToReport.printStackTrace(writer);
    		
    		// if we can't check out then we can't build it
    		throw new AbortException(Messages.RTCScm_checkout_failure2(eToReport.getMessage()));
    	} catch (Exception e) {
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checkout_failure3(e.getMessage()));
    		e.printStackTrace(writer);

    		// if we can't check out then we can't build it
    		throw new AbortException(Messages.RTCScm_checkout_failure4(e.getMessage()));
    	}
		return null;
    }
}
