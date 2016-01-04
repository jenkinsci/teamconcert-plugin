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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import hudson.model.Run;

public class RtcExtensionProvider implements ExtensionPoint {
	
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

	public boolean isApplicable(Run<?, ?> build) {
		return false;
	}
	
	public void preUpdateFileCopyArea(String workspaceUUID, String workspaceName, String buildResultUUID, String repoURL, String userId, String password, File workspace, PrintStream logger) {
		
	}
	
    public void postUpdateFileCopyArea(String workspaceUUID, String workspaceName, String buildResultUUID, String repoURL, String userId, String password, File workspace, PrintStream logger) {
		
	}
	
	public Map<String, String> getComponentLoadRules(String workspaceUUID, String workspaceName, String buildResultUUID, Map<String, String> componentInfo, String repoURL, String userId, String password, PrintStream logger) {
		return null;
	}
	
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
