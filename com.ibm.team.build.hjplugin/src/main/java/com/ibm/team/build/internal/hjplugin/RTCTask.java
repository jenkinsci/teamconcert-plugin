/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin;

import hudson.model.BuildListener;
import hudson.model.TaskListener;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for tasks running on slaves. Handles common work like debug logging
 */
public abstract class RTCTask implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private boolean isDebug;
	private TaskListener listener;

	public RTCTask(boolean isDebug, TaskListener listener) {
		this.isDebug = isDebug;
		this.listener = listener;
	}
	

	protected void debug(String msg) {
		getLogger().finer(msg);
		if (isDebug) {
			listener.getLogger().println(msg);
		}
	}

	protected void debug(String msg, Throwable e) {
		getLogger().log(Level.FINER, msg, e);
		if (isDebug) {
			listener.getLogger().println(msg);
			e.printStackTrace(listener.getLogger());
		}
	}

	protected abstract Logger getLogger();
}
