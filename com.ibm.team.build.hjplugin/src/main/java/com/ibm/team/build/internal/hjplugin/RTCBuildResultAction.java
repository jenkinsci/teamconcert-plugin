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

import hudson.EnvVars;
import hudson.Util;
import hudson.model.Action;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RTCBuildResultAction implements Serializable, Action, EnvironmentContributingAction {

	static final String BUILD_RESULT_UUID = "buildResultUUID";
	private static final String RTC_BUILD_RESULT_UUID = "RTCBuildResultUUID";
	private static final String SLASH = "/"; //$NON-NLS-1$
	private static final String ITEM_OID = "resource/itemOid/com.ibm.team.build.BuildResult/";

	private static final long serialVersionUID = 1L;

	private final String buildResultUUID;
	private final String serverURI;
	private final boolean createdBuildResult;
	private final Map<String, String> buildProperties = new HashMap<String, String>();
	
	RTCBuildResultAction(String serverURI, String buildResultUUID, boolean createdBuildResult) {
		this.buildResultUUID = buildResultUUID;
        String uri = Util.fixEmpty(serverURI);
        if (uri != null && !uri.endsWith(SLASH)) {
        	uri = uri + SLASH;
        }
        this.serverURI = uri;
        this.createdBuildResult = createdBuildResult;
        
        this.buildProperties.put(BUILD_RESULT_UUID, buildResultUUID);
        this.buildProperties.put(RTC_BUILD_RESULT_UUID, buildResultUUID);
	}
	
	String getBuildResultUUID() {
		return buildResultUUID;
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		for (Map.Entry<String, String> entry : buildProperties.entrySet()) {
			env.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public String getIconFileName() {
		// TODO Use a Jenkins one for now
		return "star-gold.gif"; //$NON-NLS-1$
	}

	@Override
	public String getDisplayName() {
		if (serverURI != null) {
			return Messages.RTCBuildResultAction_display_name();
		}
		return null;
	}

	@Override
	public String getUrlName() {
		if (serverURI != null) {
			return serverURI + ITEM_OID + buildResultUUID;
		}
		return null;
	}
	
	public boolean createdBuildResult() {
		return createdBuildResult;
	}
	
	public void addBuildProperties(Map<String, String> buildProperties) {
		for (Map.Entry<String, String> entry : buildProperties.entrySet()) {
			this.buildProperties.put(entry.getKey(), entry.getValue());
		}
	}
}
