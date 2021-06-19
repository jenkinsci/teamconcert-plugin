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
package com.ibm.team.build.hjplugin.steps;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Represents response from 'waitForBuild' step.
 * Provides the build's state and build's status at the time 
 * the task returned from waiting on the build.
 */
public class WaitForBuildStepResponse extends RTCBuildStepResponse {
	private static final long serialVersionUID = 1L;
	
	private String buildState;
	private String buildStatus;
	private boolean timedout;
	
	/**
	 * 
	 * It is expected that this cannot be instantiated from within 
	 * the pipeline script due to sandboxing.
	 * 
	 */
	public WaitForBuildStepResponse(String buildState, String buildStatus, 
								boolean timedout) {
		this.buildState = buildState;
		this.buildStatus = buildStatus;
		this.timedout = timedout;
	}
	
	/**
	 * Get the state of the build result
	 * 
	 * @return A String representing one of the states of  
	 * {@link com.ibm.team.build.internal.hjplugin.util.RTCBuildState} 
	 */
	@Whitelisted
	public String getBuildState() {
		return buildState;
	}
	

	/**
	 * Get the state of the build result
	 * 
	 * @return A String representing one of the states of  
	 * {@link com.ibm.team.build.internal.hjplugin.util.RTCBuildStatus} 
	 */
	@Whitelisted
	public String getBuildStatus() {
		return buildStatus;
	}
	
	/**
	 * Whether waitForBuild step timed out or not. You can use this value 
	 * without having to compare the expected build state with the actual 
	 * build state.
	 *  
	 * @return <code>true</code> if the step timed out, <code>false</code> otherwise.
	 */
	@Whitelisted
	public boolean getTimedout() {
		return timedout;
	}
}
