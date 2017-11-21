/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.team.build.internal.scm.SourceControlUtility;
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.scm.client.IWorkspaceConnection;

/**
 * This class contains utility methods to determine the version of build toolkit and RTC server.
 */
@SuppressWarnings("restriction")
public class VersionCheckerUtil {
	
    private static final Logger LOGGER = Logger.getLogger(VersionCheckerUtil.class.getName());
    
    /**
	 * This method determines if the build toolkit in context is pre-603 or not, depending on the presence of
	 * SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, ILoadRule2, boolean,
	 * IProgressMonitor) method.
	 * 
	 * @return true if the method lookup fails otherwise return false.
	 */
	public static boolean isPre603BuildToolkit() {
		boolean isPre603BuildToolkit = true;
		try {
			SourceControlUtility.class.getMethod("updateFileCopyArea", IWorkspaceConnection.class, String.class, boolean.class, ILoadRule2.class,
					boolean.class, IProgressMonitor.class);
			isPre603BuildToolkit = false;
		} catch (NoSuchMethodException e) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, ILoadRule2, boolean, IProgressMonitor) "
						+ "method not found. Jenkins job should have been configured with a pre-603 build toolkit: " + e);
			}
		} catch (SecurityException e) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, ILoadRule2, boolean, IProgressMonitor) "
						+ "method not found. Jenkins job should have been configured with a pre-603 build toolkit: " + e);
			}
		}
		return isPre603BuildToolkit;
	}
}
