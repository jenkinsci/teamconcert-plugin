/*******************************************************************************
 * Copyright (c) 2016, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests.utils;

import com.ibm.team.build.internal.hjplugin.rtc.BuildConfiguration;
import com.ibm.team.build.internal.hjplugin.rtc.RTCWorkspaceUtils;
import com.ibm.team.repository.client.ITeamRepository;

/**
 * This helper exposes methods that allows us to control some behavior of the
 * superclass {@link BuildConfiguration}
 *
 */
public class BuildConfigurationHelper extends BuildConfiguration {
	private RTCWorkspaceUtils workspaceUtils = super.getRTCWorkspaceUtils();
	private int retryCount = super.getRetryCount();
	private long sleepTimeout = super.getSleepTimeout();
	
	public BuildConfigurationHelper(ITeamRepository repository, String sandboxPath) {
		super(repository, sandboxPath);
	}
	
	public void setRTCWorkspaceUtils(RTCWorkspaceUtils workspaceUtils) {
		this.workspaceUtils = workspaceUtils;
	}

	@Override
	public RTCWorkspaceUtils getRTCWorkspaceUtils() {
		return workspaceUtils;
	}
	
	@Override
	public int getRetryCount() {
		return retryCount;
	}
	
	@Override
	public long getSleepTimeout() {
		return sleepTimeout;
	}
	
	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}
	
	public void setSleepTimeout(long sleepTimeout) {
		this.sleepTimeout = sleepTimeout;
	}
}
