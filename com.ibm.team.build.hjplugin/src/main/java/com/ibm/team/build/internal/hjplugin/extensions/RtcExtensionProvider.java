/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.extensions;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.util.Helper;

/**
 * 
 * The {@link RtcExtensionProvider} extension point that allows extensions to dynamically provide load rules for the
 * components in the workspace(s). The extensions are called before the workspace(s) are loaded in the build.
 * 
 * Setting the build property "com.ibm.team.build.useExtension" to true makes Team Concert plugin load the extensions.
 * This property is deprecated from 1.2.0.4.
 * 
 * Instead set the "loadPolicy" configuration property for non-build definition configuration and "useDynamicLoadRules"
 * configuration property for build definition configuration.
 *
 */
public class RtcExtensionProvider implements ExtensionPoint, Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3204613070465348795L;
	
	private static final Logger LOGGER = Logger.getLogger(RtcExtensionProvider.class.getName()); 
	
	public static boolean isEnabled(Run<?, ?> build, TaskListener listener) {
		LOGGER.finest("RtcExtensionProvider.isEnabled : Begin"); //$NON-NLS-1$
		boolean enabled = false;
		try {
			enabled = Boolean.parseBoolean(Helper.getStringBuildParameter(build, RTCJobProperties.USE_DYNAMIC_LOAD_RULE, listener));
		} catch (Exception e) {
			LOGGER.finer("RtcExtensionProvider.isEnabled : Error reading property for dynamic load rules."); //$NON-NLS-1$
			enabled = false;
		}
		if (enabled) {
			LOGGER.finer("RtcExtensionProvider.isEnabled : Using dynamic load rules."); //$NON-NLS-1$
		} else {
			LOGGER.finer("RtcExtensionProvider.isEnabled : Not using dynamic load rules."); //$NON-NLS-1$
		}
		return enabled;
	}

	/**
	 * 
	 * @param build 
	 * @return <code>true</code> if the extension is applicable for the current job else <code>false</false>
	 * @throws Exception
	 */
	public boolean isApplicable(Run<?, ?> build) throws Exception {
		LOGGER.finest("RtcExtensionProvider.isApplicable : returning false"); //$NON-NLS-1$
		return false;
	}

	/**
	 * This method is called before the load is performed 
	 *  
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param repoURL {@link String} representing the url of the RTC repository 
	 * @param userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param workspace Object of type {@link File} holding the Jenkins build location 
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * @throws Exception
	 */
	public void preUpdateFileCopyArea(String workspaceUUID, String workspaceName, String buildResultUUID, String repoURL, String userId,
			String password, File workspace, PrintStream logger) throws Exception {
		LOGGER.finest("RtcExtensionProvider.preUpdateFileCopyArea : no op"); //$NON-NLS-1$
		
	}
	
	/**
	 * This method is called after the load is performed
	 * 
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param repoURL {@link String} representing the url of the RTC repository 
	 * @param userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param workspace Object of type {@link File} holding the Jenkins build location 
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * @throws Exception
	 *   
	 */
	public void postUpdateFileCopyArea(String workspaceUUID, String workspaceName, String buildResultUUID, String repoURL, String userId,
			String password, File workspace, PrintStream logger) throws Exception {
		LOGGER.finest("RtcExtensionProvider.postUpdateFileCopyArea : no op");		 //$NON-NLS-1$
	}
	
    /**
	 * This method is called after the accept and before the load. All components in the workspace, except those
	 * returned by getExcludeComponents() are loaded; components for which dynamic load rules are returned are loaded
	 * according to those rules.
	 * 
	 * This method is deprecated, instead construct and return the load rules in getPathToLoadRuleFile() method.
	 * 
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param componentInfo {@link Map<String, String>} of component UUID's as keys and component names as values. This
	 *            {@link Map} will contain all the components in the build workspace
	 * @param repoURL {@link String} representing the url of the RTC repository
	 * @param userId userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * @return {@link Map<String, String>} containing the path to the load rule files to be used for the components. The
	 *         key for the {@link Map} should be the component uuid and the value the path to the load rule file.
	 * 
	 */
	@Deprecated
	public Map<String, String> getComponentLoadRules(String workspaceUUID, String workspaceName, String buildResultUUID,
			Map<String, String> componentInfo, String repoURL, String userId, String password, PrintStream logger) throws Exception {
		LOGGER.finest("RtcExtensionProvider.getComponentLoadRules : returning null"); //$NON-NLS-1$
		return null;
	}
	
	 /**
	 * This method is called after the accept and before the load. Return the list of components to be excluded.
	 * 
	 * This method is deprecated, instead construct and return the load rules in getPathToLoadRuleFile() method. Only
	 * those components included in the generated load rules are loaded, so it is not required to explicitly return the
	 * components to be excluded.
	 * 
	 * This method is invoked only when getComponentLoadRules()method returns null.
	 * 
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param componentInfo {@link Map<String, String>} of component UUID's as keys and component names as values. This
	 *            {@link Map} will contain all the components in the build workspace
	 * @param repoURL {@link String} representing the url of the RTC repository
	 * @param userId userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * @return {@link List<String>} List of component uuid's to exclude from load
	 * @throws Exception
	 * 
	 */
	@Deprecated
	public List<String> getExcludeComponents(String workspaceUUID, String workspaceName, String buildResultUUID, Map<String, String> componentInfo,
			String repoURL, String userId, String password, PrintStream logger) throws Exception {
		LOGGER.finest("RtcExtensionProvider.getExcludeComponents : returning null"); //$NON-NLS-1$
		return null;
	}
	
	/**
	 * This method is called after the accept and before the load. When dynamic load rules are provided, only those
	 * components determined by the load rules are loaded, according to those rules. Note that only XML format is
	 * supported for load rules.
	 * 
	 * Note that this method will not be invoked until the deprecated getComponentLoadRules() method returns non-null
	 * load rules.
	 * 
	 * @param workspaceUUID UUID of the build workspace could be <code>null</code>
	 * @param workspaceName name of the build workspace could be <code>null</code>
	 * @param buildResultUUID UUID of the build result could be <code>null</code>
	 * @param componentInfo {@link Map<String, String>} of component UUID's as keys and component names as values. This
	 *            {@link Map} will contain all the components in the build workspace
	 * @param repoURL {@link String} representing the url of the RTC repository
	 * @param userId userId User id for the RTC repository
	 * @param password Password for the userId to login into the RTC repository
	 * @param logger Object of type {@link PrintStream}, can be used for logging
	 * @return path to the load rule file, in the local filesystem.
	 * 
	 */
	public String getPathToLoadRuleFile(String workspaceUUID, String workspaceName, String buildResultUUID,
			Map<String, String> componentInfo, String repoURL, String userId, String password, PrintStream logger) throws Exception {
		LOGGER.finest("RtcExtensionProvider.getPathToLoadRuleFile : returning null"); //$NON-NLS-1$
		return null;
	}

	/**
	 * 
	 * @param build
	 * @param listener
	 * @return
	 */
	public static RtcExtensionProvider getExtensionProvider(Run<?, ?> build, TaskListener listener) throws Exception {
		LOGGER.finest("RtcExtensionProvider.getExtensionProvider : Begin"); //$NON-NLS-1$
		for (RtcExtensionProvider lrProvider : RtcExtensionProvider.all()) {
			boolean canCreate = lrProvider.isApplicable(build);
			LOGGER.finer("RtcExtensionProvider.getExtensionProvider " + lrProvider.getClass().getName() + " return " + canCreate); //$NON-NLS-1$ //$NON-NLS-2$
			if (canCreate) {
				return lrProvider;
			}
		}
		LOGGER.finest("RtcExtensionProvider.getExtensionProvider : End"); //$NON-NLS-1$
		return null;
	}

	/**
	 * All registered {@link RtcExtensionProvider}s.
	 */
	public static ExtensionList<RtcExtensionProvider> all() {
		return Jenkins.getInstance().getExtensionList(RtcExtensionProvider.class);
	}
}
