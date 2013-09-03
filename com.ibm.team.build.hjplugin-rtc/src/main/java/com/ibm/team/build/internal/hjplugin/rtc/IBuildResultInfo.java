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

public interface IBuildResultInfo {

	/**
	 * @return The build result for which info is requested
	 */
	public String getBuildResultUUID();

	/**
	 * Set whether the build is a personal build
	 * @param isPersonalBuild <code>true</code> if a personal build
	 * <code>false</code> otherwise
	 */
	public void setPersonalBuild(boolean isPersonalBuild);

	/**
	 * Set the requestor of the build
	 * @param requestor The Contributor's name 
	 */
	public void setRequestor(String requestor);

	/**
	 * Set whether the build was scheduled or not
	 * @param isScheduled <code>true</code> if scheduled <code>false</code>
	 * if initiated by an RTC Contributor.
	 */
	public void setScheduled(boolean isScheduled);

}
