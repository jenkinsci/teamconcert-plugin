/*******************************************************************************
 * Copyright © 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;

import hudson.model.TaskListener;

public class TaskListenerWrapper implements Serializable {
	/**
	 * default serial id
	 */
	private static final long serialVersionUID = 1L;
	
	private TaskListener taskListener;
	
	public TaskListenerWrapper(TaskListener listener) {
		taskListener = listener;
	}
	
	public PrintStream getLogger() {
		return taskListener.getLogger();
	}
	
    public PrintWriter error(String msg) {
    	return taskListener.error(msg);
    }

    public PrintWriter error(String format, Object... args) {
    	return taskListener.error(format, args);
    }

    public PrintWriter fatalError(String msg) {
    	return taskListener.error(msg);
    }

    public PrintWriter fatalError(String format, Object... args) {
    	return taskListener.error(format, args);
    }
}
