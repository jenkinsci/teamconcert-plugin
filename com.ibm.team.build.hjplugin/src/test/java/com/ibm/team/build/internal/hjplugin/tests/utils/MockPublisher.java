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

import java.io.IOException;

import org.kohsuke.stapler.StaplerRequest;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

public class MockPublisher extends Recorder implements SimpleBuildStep {
	private final Result _result;
	private final RTCBuildResultAction[] _actions;
	
	public MockPublisher(Result result) {
		this._result = result;
		this._actions = new RTCBuildResultAction[0];
	}
	
	public MockPublisher(Result result, RTCBuildResultAction[] actions) {
		this._result = result;
		this._actions = actions;
	}
	
	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public void perform(Run<?, ?> build, FilePath path, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		build.setResult(_result);
		if (_actions != null) {
			for (RTCBuildResultAction action : _actions) {
				build.addAction(action);
			}
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public String getDisplayName() {
			return "MockPublisher";
		}
		
		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> arg0) {
			return true;
		}
		
		@Override
        public MockPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(MockPublisher.class,formData);
		}
	}
}
