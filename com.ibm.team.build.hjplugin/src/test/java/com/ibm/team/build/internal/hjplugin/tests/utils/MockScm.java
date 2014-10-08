/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests.utils;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import java.io.File;
import java.io.IOException;

/**
 * Non-RTC SCM class for unit testing RTCScmConfigHelper
 */
public class MockScm extends SCM {

	String me;
	public MockScm(String string) {
		me = string;
	}
	
	@Override
	public SCMRevisionState calcRevisionsFromBuild(
			AbstractBuild<?, ?> build, Launcher launcher,
			TaskListener listener) throws IOException, InterruptedException {
		return null;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener,
			SCMRevisionState baseline) throws IOException,
			InterruptedException {
		return null;
	}

	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {
		return false;
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return null;
	}
	
}