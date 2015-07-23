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
import hudson.scm.SCM;

import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.DataBoundConstructor;

import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.RTCScm.BuildType;


public class RTCScmStep extends SCMStep {
    
	private static final Logger LOGGER = Logger.getLogger(RTCScmStep.class.getName());

	
	private BuildType buildType;
	
	@DataBoundConstructor
	public RTCScmStep(BuildType buildType) {
		this.buildType = buildType;
	}
	
	@Override
	protected SCM createSCM() {
		LOGGER.finer("RTCScmStep.createSCM : Begin");
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
		
	}
}
