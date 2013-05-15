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

import java.nio.channels.ClosedByInterruptException;

import org.eclipse.core.runtime.IStatus;

import com.ibm.team.filesystem.client.FileSystemStatusException;
import com.ibm.team.repository.common.TeamRepositoryException;

public class Utils {
	private Utils() {}
	
	public static Exception checkForCancellation(TeamRepositoryException e) {
		if (e instanceof FileSystemStatusException) {
			if (((FileSystemStatusException) e).getStatus().matches(IStatus.CANCEL)) {
				InterruptedException result = new InterruptedException(e.getMessage());
				result.initCause(e);
				return result;
			}
		}
		if (isInterruptedException(e)) {
			InterruptedException result = new InterruptedException(e.getMessage());
			result.initCause(e);
			return result;
		}
		return e;
	}
	
	private static boolean isInterruptedException(TeamRepositoryException e) {
		Throwable nested = e.getCause();
		// We are only digging 20 deep because I am paranoid and its unlikely that an interrupt
		// would be nested deeper than that.
		for (int i=0; i<20; i++) {
			if (nested == null) {
				return false;
			} else if ((nested instanceof InterruptedException) 
					|| (nested instanceof ClosedByInterruptException)) {
				return true;
			} else {
				nested = nested.getCause();
			}
		}
		return false;
	}

	/**
	 * Determine if the exception thrown is caused by a cancellation
	 * @param e The exception to investigate
	 * @return <code>true</code> if the exception represents a cancellation
	 * <code>false</code> otherwise.
	 */
	public static boolean isCancellation(TeamRepositoryException e) {
		if (e instanceof FileSystemStatusException) {
			if (((FileSystemStatusException) e).getStatus().matches(IStatus.CANCEL)) {
				return true;
			}
		}
		return isInterruptedException(e);
	}
}
