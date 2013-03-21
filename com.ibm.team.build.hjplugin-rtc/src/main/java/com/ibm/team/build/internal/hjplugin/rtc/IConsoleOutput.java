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

package com.ibm.team.build.internal.hjplugin.rtc;

/**
 * Facade in front of the Hudson/Jenkins listener
 * Used so we can provide messages to the listener within the RTC specific functionality
 */
public interface IConsoleOutput {
	
	/**
	 * Log an information message to any listeners
	 * @param message The message to log
	 */
	public void log(String message);
	
	/**
	 * Log an error message to any listeners
	 * @param message The message to log
	 * @param e The error exception to log
	 */
	public void log(String message, Exception e);
}
