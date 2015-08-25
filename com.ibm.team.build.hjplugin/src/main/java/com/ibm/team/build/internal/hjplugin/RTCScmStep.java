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

package com.ibm.team.build.internal.hjplugin;

import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Job;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.util.ListBoxModel;

import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;

public class RTCScmStep extends SCMStep {
	private static final Logger LOGGER = Logger.getLogger(RTCScmStep.class.getName());

	private BuildType buildType;
	private OverrideConfig overrideConfig;

	/*
	 * @param buildType - the type of the build
	 */
	@DataBoundConstructor
	public RTCScmStep(BuildType buildType) {
		LOGGER.finest("RTCScmStep constructor : Begin");
		this.buildType = buildType;
	}
	
	public BuildType getBuildType() {
		return buildType;
	}

	private boolean isOverrideGlobal() {
		return (overrideConfig != null);
	}

	@DataBoundSetter
	public void setOverrideConfig(OverrideConfig overrideConfig) {
		LOGGER.finest("RTCScmStep.setOverrideConfig: Begin");
		this.overrideConfig = overrideConfig;
	}

	public OverrideConfig getOverrideConfig() {
		return overrideConfig;
	}
	
	@Override
	protected SCM createSCM() {
		LOGGER.finest("RTCScmStep.createSCM : Begin");
		if (isOverrideGlobal()) {
			return new RTCScm(true, overrideConfig.getBuildTool(), overrideConfig.getServerUri(), 
					overrideConfig.getTimeout(), null, null, null, 
					overrideConfig.getCredentialsId(), buildType, overrideConfig.getAvoidUsingToolkit());
		}
		return new RTCScm(buildType);
	}
	
	@Extension(optional = true)
	public static class DescriptorImpl extends SCMStepDescriptor {

		public DescriptorImpl() {
			
		}

		@Override
		public String getFunctionName() {
			return "teamconcert";
		}

		@Override
		public String getDisplayName() {
			return "Team Concert";
		}
		
        public static int getDefaultTimeout() {
			return RTCScm.DEFAULT_SERVER_TIMEOUT;
		}

		/**
		 * Provides a listbox of the defined build tools to pick from. Also includes
		 * an entry to signify no toolkit is chosen.
		 * @return The valid build tool options
		 */
		public ListBoxModel doFillBuildToolItems() {
			ListBoxModel listBox = new ListBoxModel();
			listBox.add(new ListBoxModel.Option(Messages.RTCScm_no_build_tool_name(), ""));
			RTCBuildToolInstallation[] allTools = RTCBuildToolInstallation.allInstallations();
			for (RTCBuildToolInstallation tool : allTools) {
				ListBoxModel.Option option = new ListBoxModel.Option(tool.getName());
				listBox.add(option);
			}
			return listBox;
		}

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?, ?> project, @QueryParameter String serverUri) {
			return new StandardListBoxModel()
			.withEmptySelection()
			.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM, URIRequirementBuilder.fromUri(serverUri).build()));
		}
	}
	
	public static class OverrideConfig {
		private String serverUri;
		private String credentialsId;
		private String buildTool;
		private int timeout = RTCScm.DEFAULT_SERVER_TIMEOUT;
		private boolean avoidUsingToolkit = false;

		// TODO buildTool is perhaps not mandatory when overriding the server uri.
		// Need to decouple overrideGlobal flag from the buildTool parameter in @{link RTCScm}
		// Then we can use a @{link DataBoundSetter} for buildTool 
		@DataBoundConstructor
		public OverrideConfig(String serverUri, String credentialsId, String buildTool) {
			LOGGER.finest("RTCScmStep.OverrideConfig : Begin");
			this.serverUri = serverUri;
			this.credentialsId = credentialsId;
			this.buildTool = buildTool;
		}

		public String getServerUri() {
			return serverUri;
		}

		public String getCredentialsId() {
			return credentialsId;
		}
		
		public String getBuildTool() {
			return buildTool;
		}
		
		@DataBoundSetter
		public void setTimeout(int timeout) {
			if (timeout > 0) {
				this.timeout = timeout;
			}
		}

		public int getTimeout() {
			return timeout;
		}
		
		@DataBoundSetter
		public void setAvoidUsingToolkit(boolean avoidUsingToolkit) {
			this.avoidUsingToolkit = avoidUsingToolkit;
		}

		public boolean getAvoidUsingToolkit() {
			return avoidUsingToolkit;
		}
	}
}
