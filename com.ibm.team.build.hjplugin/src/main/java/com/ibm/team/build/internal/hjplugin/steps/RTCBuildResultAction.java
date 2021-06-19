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

package com.ibm.team.build.internal.hjplugin.steps;

import hudson.Util;
import hudson.model.Action;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.ibm.team.build.internal.hjplugin.Messages;

/**
 * An action that is associated with {@link RTCBuildStep}
 * 
 * Contributes to the Jenkins build a link to the RTC build result.
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
public class RTCBuildResultAction implements Serializable, Action {

    private static final Logger LOGGER = Logger.getLogger(RTCBuildResultAction.class.getName());

	private static final String SLASH = "/"; //$NON-NLS-1$
	private static final String ITEM_OID = "resource/itemOid/com.ibm.team.build.BuildResult/"; //$NON-NLS-1$

	private static final long serialVersionUID = 1L;

	private final String buildResultUUID;
	private final String serverURI;
	
	/**
	 * @param serverURI The RTC server uri
	 * @param buildResultUUID The UUID of the corresponding build result. <code>null</code>
	 * if there is no build result
	 */
	public RTCBuildResultAction(String serverURI, String buildResultUUID) {
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
        
        if (buildResultUUID != null) {
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
	 * @return return the server uri
	 */
	@Exported(visibility=3)
	public String getServerURI() {
		return this.serverURI;
	}
}
