/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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

/**
 * An action that is associated with a Hudson/Jenkins build. Serialized so that it can contribute
 * information to the Hudson/Jenkins build result.
 * 
 * Contributes to the H/J build a link to the RTC build result.
 * Contributes to the environment properties that come from the RTC build definition & engine
 * Contains information about the RTC build result so that other extensions to the build can 
 * access it (example: RunListener to be able to terminate the RTC build result).
 */
public class RTCBuildResultAction implements Serializable, Action, EnvironmentContributingAction {

	static final String BUILD_RESULT_UUID = "buildResultUUID";
	private static final String RTC_BUILD_RESULT_UUID = "RTCBuildResultUUID";
	private static final String SLASH = "/"; //$NON-NLS-1$
	private static final String ITEM_OID = "resource/itemOid/com.ibm.team.build.BuildResult/";

	private static final long serialVersionUID = 1L;

	private final String buildResultUUID;
	private final String serverURI;
	// meaning has changed but the name remains the same for serialization
	private final boolean createdBuildResult;
	private final Map<String, String> buildProperties = new HashMap<String, String>();
	private final transient RTCScm scm;
	
	/**
	 * @param serverURI The RTC server uri
	 * @param buildResultUUID The UUID of the corresponding build result. <code>null</code>
	 * if there is no build result
	 * @param createdBuildResult Whether the build owns the RTC build's lifecycle or not.
	 * @param scm The RTCSCM responsible for the SCM part of the build. This may be
	 * different from the one supplied on the AbstractBuild if another SCM plugin
	 * incorporates our SCM provider (i.e. MultiSCM). 
	 */
	RTCBuildResultAction(String serverURI, String buildResultUUID, boolean ownsRTCBuildResultLifecycle, RTCScm scm) {
		this.buildResultUUID = buildResultUUID;
        String uri = Util.fixEmpty(serverURI);
        if (uri != null && !uri.endsWith(SLASH)) {
        	uri = uri + SLASH;
        }
        this.serverURI = uri;
        this.createdBuildResult = ownsRTCBuildResultLifecycle;
        this.scm = scm;
        
        if (buildResultUUID != null) {
        	this.buildProperties.put(BUILD_RESULT_UUID, buildResultUUID);
        	this.buildProperties.put(RTC_BUILD_RESULT_UUID, buildResultUUID);
        }
	}
	
	/**
	 * @return The build result UUID for the RTC build result
	 */
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
		if (serverURI != null && buildResultUUID != null) {
			return "star-gold.gif"; //$NON-NLS-1$
		}
		// show nothing in task list
		return null; 
	}

	@Override
	public String getDisplayName() {
		if (serverURI != null && buildResultUUID != null) {
			return Messages.RTCBuildResultAction_display_name();
		}
		return null;
	}

	@Override
	public String getUrlName() {
		if (serverURI != null && buildResultUUID != null) {
			return serverURI + ITEM_OID + buildResultUUID;
		}
		return null;
	}
	
	/**
	 * @return <code>true</code> if the build's lifecylce is owned by the plugin.
	 * This could be because it was inititiated in Hudson/Jenkins
	 * and the plugin created a build result in RTC (and this build is responsible
	 * for the lifecycle). Or if the RTC server's hudson integration simply created
	 * the build result but did not start the build.
	 * <code>false</code> if the build result lifecycle is not owned by this build.
	 */
	public boolean ownsBuildResultLifecycle() {
		return createdBuildResult;
	}
	
	/**
	 * Adds to the RTC Scm build the properties obtained from the build engine
	 * and build definition.
	 * @param buildProperties The build properties to include
	 */
	public void addBuildProperties(Map<String, String> buildProperties) {
		for (Map.Entry<String, String> entry : buildProperties.entrySet()) {
			this.buildProperties.put(entry.getKey(), entry.getValue());
		}
	}
	
	/**
	 * @return The SCM configuration for the build that created this action
	 */
	public RTCScm getScm() {
		return scm;
	}
}
