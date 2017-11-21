/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tests.utils;

import hudson.Extension;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.extensions.RtcExtensionProvider;

@Extension
public class NewDynamicLoadRuleGenerator extends RtcExtensionProvider {

	private static final long serialVersionUID = -1890593506345522335L;	

	@Override
	public boolean isApplicable(Run<?, ?> build) {
		boolean isApplicable = true;

		for (ParametersAction parametersAction : build.getActions(ParametersAction.class)) {
			if (parametersAction != null && parametersAction.getParameter("useOldDynamicLoadRuleGenerator") != null) {
				isApplicable = !Boolean.parseBoolean(((StringParameterValue)parametersAction.getParameter("useOldDynamicLoadRuleGenerator")).value);
			}
		}
		return isApplicable;
	}

	@Override
	public String getPathToLoadRuleFile(String workspaceUUID, String workspaceName, String buildResultUUID, Map<String, String> componentInfo,
			String repoURL, String userId, String password, PrintStream logger) throws Exception {
		String componentId = null;
		for (String id : componentInfo.keySet()) {
			if (!componentInfo.get(id).endsWith("c2")) {
				componentId = id;
				break;
			}
		}
		String componentName = componentInfo.get(componentId);
		String loadRule = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ "<scm:sourceControlLoadRule version=\"1\" xmlns:scm=\"http://com.ibm.team.scm\">\n" 
				+ "    <itemLoadRule>\n"
				+ "        <component name=\"" + componentName  + "\" />\n"
				+ "        <item repositoryPath=\"/" + componentName + "/f\"/>\n" 
				+ "    </itemLoadRule>\n" 
				+ "</scm:sourceControlLoadRule>\n";
		File loadRuleFile = File.createTempFile("temp", "DynamicLoadRuleFile");
		loadRuleFile.deleteOnExit();
		PrintWriter outputPrintWriter = null;
		try {
			outputPrintWriter = new PrintWriter(loadRuleFile);
			outputPrintWriter.write(loadRule);
		} finally {
			if (outputPrintWriter != null) {
				outputPrintWriter.close();
			}
		}
		return loadRuleFile.getAbsolutePath();
	}

	public List<String> getExcludeComponents(String workspaceUUID, String workspaceName, String buildResultUUID, Map<String, String> componentInfo,
			String repoURL, String userId, String password, PrintStream logger) throws Exception {
		List<String> componentsToExclude = new ArrayList<String>();
		for (String componentId : componentInfo.keySet()) {
			if (componentInfo.get(componentId).endsWith("c2")) {
				componentsToExclude.add(componentId);
			}
		}
		// though we return componentsToExclude this will be ignored as we don't return load rules using the deprecated
		// getComponentLoadRules() interface.
		return componentsToExclude;
	}
}
