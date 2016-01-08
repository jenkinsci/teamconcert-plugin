/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.extensions;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import hudson.model.Run;

public class RtcExtensionProvider implements ExtensionPoint, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3204613070465348795L;
	
	private static final Logger LOGGER = Logger.getLogger(RtcExtensionProvider.class.getName());
	private static final String USE_DYNAMIC_LOAD_RULE = "com.ibm.team.build.useExtension"; 
	
	public static boolean isEnabled(Run<?, ?> build, TaskListener listener) {
		LOGGER.finest("LoadRuleProvider.isEnabled : Begin");
		boolean enabled = false;
		try {
			enabled = Boolean.parseBoolean(build.getEnvironment(listener).get(
					USE_DYNAMIC_LOAD_RULE));
		} catch (Exception e) {
			LOGGER.finer("LoadRuleProvider.isEnabled : Error reading property for dynamic load rules.");
			enabled = false;
		}
		if (enabled) {
			LOGGER.finer("LoadRuleProvider.isEnabled : Using dynamic load rules.");
		} else {
			LOGGER.finer("LoadRuleProvider.isEnabled : Not using dynamic load rules.");
		}
		return enabled;
	}

	/**
	 * 
	 * @param build 
	 * @return <code>true</code> if the extension is applicable for the current job else <code>false</false>
	 */
	public boolean isApplicable(Run<?, ?> build) {
		return false;
	}

	/**
	 * 
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param repoURL {@link String} representing the url of the RTC repository 
	 * @param userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param workspace Object of type {@link File} holding the Jenkins build location 
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * 
	 * This method is called before the load is performed  
	 */
	public void preUpdateFileCopyArea(String workspaceUUID, String workspaceName, String buildResultUUID, String repoURL, String userId, String password, File workspace, PrintStream logger) {
		
	}
	
	/**
	 * 
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param repoURL {@link String} representing the url of the RTC repository 
	 * @param userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param workspace Object of type {@link File} holding the Jenkins build location 
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * 
	 * This method is called after the load is performed  
	 */
    public void postUpdateFileCopyArea(String workspaceUUID, String workspaceName, String buildResultUUID, String repoURL, String userId, String password, File workspace, PrintStream logger) {
		
	}
	
    /**
     * 
     * @param workspaceUUID UUID of the build workspace could be <code>null</code>
     * @param workspaceName name of the build workspace could be <code>null</code>
     * @param buildResultUUID UUID of the build result could be <code>null</code>
     * @param componentInfo {@link Map<String, String>} of component UUID's as keys and component names as values. 
     *                      This {@link Map} will contain all the components in the build workspace
     * @param repoURL {@link String} representing the url of the RTC repository 
     * @param userId userId User id for the RTC repository
     * @param password Password for the userId to login into the RTC repository
     * @param logger Object of type {@link PrintStream}, can be used for logging
     * @return {@link Map<String, String>} containing the path to the load rule files to be used for the components. The key for the {@link Map} should be the component uuid and the value the path to the load rule file.
     * 
     * This method is called after the accept and before the load. 
     */
	public Map<String, String> getComponentLoadRules(String workspaceUUID, String workspaceName, String buildResultUUID, Map<String, String> componentInfo, String repoURL, String userId, String password, PrintStream logger) {
		return null;
	}
	
	 /**
     * 
     * @param workspaceUUID UUID of the build workspace could be <code>null</code>
     * @param workspaceName name of the build workspace could be <code>null</code>
     * @param buildResultUUID UUID of the build result could be <code>null</code>
     * @param componentInfo {@link Map<String, String>} of component UUID's as keys and component names as values. 
     *                      This {@link Map} will contain all the components in the build workspace
     * @param repoURL {@link String} representing the url of the RTC repository 
     * @param userId userId User id for the RTC repository
     * @param password Password for the userId to login into the RTC repository
     * @param logger Object of type {@link PrintStream}, can be used for logging
     * @return {@link List<String>} List of component uuid's to exclude from load
     * 
     * This method is called after the accept and before the load. 
     */
	public List<String> getExcludeComponents(String workspaceUUID, String workspaceName, String buildResultUUID, Map<String, String> componentInfo, String repoURL, String userId, String password, PrintStream logger) {
		return null;
	}

	public static RtcExtensionProvider getCompLoadRules(Run<?, ?> build, TaskListener listener) {
		if (isEnabled(build, listener)) {
			for (RtcExtensionProvider lrProvider : RtcExtensionProvider.all()) {
				boolean canCreate = lrProvider.isApplicable(build);
				LOGGER.finer("LoadRuleProvider.getCompLoadRules "+lrProvider.getClass().getName()+" return "+canCreate);
				if (canCreate) {
					return lrProvider;
				}
			}
		}
		return null;
	}

	/**
	 * All registered {@link Animal}s.
	 */
	public static ExtensionList<RtcExtensionProvider> all() {
		return Jenkins.getInstance().getExtensionList(RtcExtensionProvider.class);
	}

}
