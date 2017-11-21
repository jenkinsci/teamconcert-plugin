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
package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RtcExtensionProviderUtil {
	
	private static final Logger LOGGER = Logger.getLogger(RtcExtensionProviderUtil.class.getName());
	
	public static void preUpdateFileCopyArea(final Object lrProvider, final PrintStream logger, final File workspace, final String workspaceUUID,
			final String workspaceName, final String buildResultUUID, final String repoURL, String userId, String password) throws Exception {
		if (lrProvider != null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.preUpdateFileCopyArea: invoking preUpdateFileCopyArea on '" + lrProvider.getClass().getName() + "' - Begin"); //$NON-NLS-1$//$NON-NLS-2$
			}
			Method preUpdateFileCopyArea = lrProvider
					.getClass()
					.getMethod(
							"preUpdateFileCopyArea", String.class, String.class, String.class, String.class, String.class, String.class, File.class, PrintStream.class);//$NON-NLS-1$
			if (preUpdateFileCopyArea != null) {
				preUpdateFileCopyArea.invoke(lrProvider, workspaceUUID, workspaceName, buildResultUUID, repoURL, userId, password, workspace, logger);
			}
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.preUpdateFileCopyArea: invoking preUpdateFileCopyArea on '" + lrProvider.getClass().getName() + "' - End"); //$NON-NLS-1$//$NON-NLS-2$
			}
		}

	}

	public static void postUpdateFileCopyArea(final Object lrProvider, final PrintStream logger, final File workspace, final String workspaceUUID,
			final String workspaceName, final String buildResultUUID, final String repoURL, String userId, String password) throws Exception {
		if (lrProvider != null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.postUpdateFileCopyArea: invoking postUpdateFileCopyArea on '" + lrProvider.getClass().getName() + "' - Begin"); //$NON-NLS-1$//$NON-NLS-2$
			}
			Method postUpdateFileCopyArea = lrProvider
					.getClass()
					.getMethod(
							"postUpdateFileCopyArea", String.class, String.class, String.class, String.class, String.class, String.class, File.class, PrintStream.class);//$NON-NLS-1$
			if (postUpdateFileCopyArea != null) {
				postUpdateFileCopyArea
						.invoke(lrProvider, workspaceUUID, workspaceName, buildResultUUID, repoURL, userId, password, workspace, logger);
			}
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.postUpdateFileCopyArea: invoking postUpdateFileCopyArea on '" + lrProvider.getClass().getName() + "' - End"); //$NON-NLS-1$//$NON-NLS-2$
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static Map<String, String> getComponentLoadRules(final Object lrProvider, final PrintStream logger, final String workspaceUUID,
			final String workspaceName, final String snapshotUUID, final String buildResultUUID, Map<String, String> componentInfo,
			final String repoURL, String userId, String password) throws Exception {
		if (lrProvider != null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.getComponentLoadRules: invoking getComponentLoadRules on '" + lrProvider.getClass().getName() + "' - Begin"); //$NON-NLS-1$//$NON-NLS-2$
			}
			Method getComponentLoadRulesMethod = lrProvider
					.getClass()
					.getMethod(
							"getComponentLoadRules", String.class, String.class, String.class, Map.class, String.class, String.class, String.class, PrintStream.class);//$NON-NLS-1$
			if (getComponentLoadRulesMethod != null) {
				return (Map<String, String>)getComponentLoadRulesMethod.invoke(lrProvider, workspaceUUID, workspaceName, buildResultUUID,
						componentInfo, repoURL, userId, password, logger);
			}
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.getComponentLoadRules: invoking getComponentLoadRules on '" + lrProvider.getClass().getName() + "' - End"); //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return null;
	}

	public static String getExcludeComponentList(final Object lrProvider, final PrintStream logger, final String workspaceUUID,
			final String workspaceName, final String snapshotUUID, final String buildResultUUID, Map<String, String> componentInfo,
			final String repoURL, String userId, String password) throws Exception {
		if (lrProvider != null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.getExcludeComponentList: invoking getExcludeComponents on '" + lrProvider.getClass().getName() + "' - Begin"); //$NON-NLS-1$//$NON-NLS-2$
			}
			Method getExcludeComponents = lrProvider
					.getClass()
					.getMethod(
							"getExcludeComponents", String.class, String.class, String.class, Map.class, String.class, String.class, String.class, PrintStream.class);//$NON-NLS-1$
			if (getExcludeComponents != null) {
				@SuppressWarnings("unchecked")
				List<String> components = (List<String>)getExcludeComponents.invoke(lrProvider, workspaceUUID, workspaceName, buildResultUUID,
						componentInfo, repoURL, userId, password, logger);
				if (components != null && components.size() > 0) {
					StringBuffer result = new StringBuffer();
					for (String component : components) {
						if (component != null && component.trim().length() > 0)
							result.append(component.trim()).append(' ');
					}
					String resultStr = result.toString().trim();
					if (resultStr.length() > 0) {
						return resultStr;
					}
				}
			}
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.getExcludeComponentList: invoking getExcludeComponents on '" + lrProvider.getClass().getName() + "' - End"); //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return null;
	}
	
	public static String getPathToLoadRuleFile(final Object lrProvider, final PrintStream logger, final String workspaceUUID,
			final String workspaceName, final String snapshotUUID, final String buildResultUUID, Map<String, String> componentInfo,
			final String repoURL, String userId, String password) throws Exception {
		if (lrProvider != null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.getPathToLoadRuleFile: invoking getPathToLoadRuleFile on '" + lrProvider.getClass().getName() + "' - Begin"); //$NON-NLS-1$//$NON-NLS-2$
			}
			Method getPathToLoadRuleFile = lrProvider
					.getClass()
					.getMethod(
							"getPathToLoadRuleFile", String.class, String.class, String.class, Map.class, String.class, String.class, String.class, PrintStream.class);//$NON-NLS-1$
			if (getPathToLoadRuleFile != null) {
				return (String)getPathToLoadRuleFile.invoke(lrProvider, workspaceUUID, workspaceName, buildResultUUID, componentInfo, repoURL,
						userId, password, logger);
			}
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("RtcExtensionProviderUtil.getPathToLoadRuleFile: invoking getPathToLoadRuleFile on '" + lrProvider.getClass().getName() + "' - End"); //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return null;
	}
}
