/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * An action that is associated with a Hudson/Jenkins build. Serialized so that it can contribute
 * information to the Hudson/Jenkins build result.
 * 
 * Contributes to the H/J build a link to the RTC build result.
 * Contributes to the environment properties that come from the RTC build definition & engine
 * Contains information about the RTC build result so that other extensions to the build can 
 * access it (example: RunListener to be able to terminate the RTC build result).
 * 
 * The following attributes are exposed
 * a) buildResultUUID
 * b) displayName
 * c) serverURI
 * d) urlName - The URL of the build result
 * 
 * The visibility parameter of the Exported annotation ensures that the attributes are available 
 * when getting information about the build without using the depth query parameter.
 * 
 * For instance, /job_name/<buildnumber>/api/xml should return information about RTCBuildResultAction
 * 
 */
@ExportedBean
public class RTCBuildResultAction implements Serializable, Action, EnvironmentContributingAction {

    private static final Logger LOGGER = Logger.getLogger(RTCBuildResultAction.class.getName());

	private static final String SLASH = "/"; //$NON-NLS-1$
	private static final String ITEM_OID = "resource/itemOid/com.ibm.team.build.BuildResult/"; //$NON-NLS-1$

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
	public RTCBuildResultAction(String serverURI, String buildResultUUID, boolean ownsRTCBuildResultLifecycle, RTCScm scm) {
		LOGGER.finest("RTCBuildResultAction : Instantiating a build result action"); //$NON-NLS-1$

		this.buildResultUUID = buildResultUUID;
        String uri = Util.fixEmpty(serverURI);
        if (uri != null && !uri.endsWith(SLASH)) {
        	uri = uri + SLASH;
        	if (LOGGER.isLoggable(Level.FINER)) {
        		LOGGER.finer("RTCBuildResultAction : Received URI " + uri); //$NON-NLS-1$
        	}
        }
        this.serverURI = uri;
        this.createdBuildResult = ownsRTCBuildResultLifecycle;
        this.scm = scm;
        
        if (buildResultUUID != null) {
        	this.buildProperties.put(RTCJobProperties.RTC_BUILD_RESULT_UUID, buildResultUUID);
        	if (LOGGER.isLoggable(Level.FINER)) {
        		LOGGER.finer("RTCBuildResultAction : Received build result uuid " + buildResultUUID); //$NON-NLS-1$
        	}
        }
	}
	
	/**
	 * @return The build result UUID for the RTC build result
	 */
	@Exported(visibility=3)
	public String getBuildResultUUID() {
		return this.buildResultUUID;
	}
	
	/**
	 * @return the current map of build properties associated with this build result action.
	 */
	public Map<String, String> getBuildProperties() {
		return this.buildProperties;
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		LOGGER.finest("RTCBuildResultAction.buildEnvVars : Enter"); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : this.buildProperties.entrySet()) {
			env.put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public String getIconFileName() {
		// TODO Use a Jenkins one for now
		if (this.serverURI != null && this.buildResultUUID != null) {
			return "star-gold.gif"; //$NON-NLS-1$
		}
		// show nothing in task list
		return null; 
	}

	@Override
	@Exported(visibility=3)
	public String getDisplayName() {
		if (this.serverURI != null && this.buildResultUUID != null) {
			return Messages.RTCBuildResultAction_display_name();
		}
		return null;
	}

	@Override
	@Exported(visibility=3)
	public String getUrlName() {
		if (this.serverURI != null && this.buildResultUUID != null) {
			return this.serverURI + ITEM_OID + this.buildResultUUID;
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
		return this.createdBuildResult;
	}
	
	/**
	 * Adds to the RTC Scm build the properties obtained from the build engine
	 * and build definition.
	 * @param buildProperties The build properties to include. May be <code>null</code>
	 */
	public void addBuildProperties(Map<String, String> buildProperties) {
		if (buildProperties == null) {
			return;
		}
		LOGGER.finest("RTCBuildResultAction.addBuildProperties : Enter"); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : buildProperties.entrySet()) {
			LOGGER.finest("Key : "  + entry.getKey() + " Value : " + entry.getValue());  //$NON-NLS-1$//$NON-NLS-2$
			this.buildProperties.put(entry.getKey(), entry.getValue());
		}
	}
	
	/**
	 * @return return the server uri
	 */
	@Exported(visibility=3)
	public String getServerURI() {
		return this.serverURI;
	}
	
	/**
	 * @return The SCM configuration for the build that created this action
	 */
	public RTCScm getScm() {
		return this.scm;
	}
}
