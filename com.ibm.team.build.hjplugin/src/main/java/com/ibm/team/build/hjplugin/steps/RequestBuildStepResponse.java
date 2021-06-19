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
 * Represents the response from invoking a RequestBuild task
 * Provides the buildResultUUID after requesting a build.
 */
public class RequestBuildStepResponse extends RTCBuildStepResponse {
	private static final long serialVersionUID = 1L;

	private String buildResultUUID = null;
	
	/**
	 * It is expected that this cannot be instantiated from within 
	 * the pipeline script due to sandboxing.
	 */
	public RequestBuildStepResponse(String buildResultUUID) {
		this.buildResultUUID = buildResultUUID;
	}
	
	@Whitelisted
	public String getBuildResultUUID() {
		return buildResultUUID;
	}
}
