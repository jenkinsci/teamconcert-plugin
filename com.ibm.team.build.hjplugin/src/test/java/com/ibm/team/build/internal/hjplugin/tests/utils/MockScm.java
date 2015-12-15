/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
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
import hudson.model.TaskListener;
import hudson.model.Job;
import hudson.model.Run;
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
			Run<?, ?> build, FilePath workspace, Launcher launcher,
			TaskListener listener) throws IOException, InterruptedException {
		return null;
	}

	@Override
	public PollingResult compareRemoteRevisionWith(
			Job<?, ?> project, Launcher launcher,
			FilePath workspace, TaskListener listener,
			SCMRevisionState baseline) throws IOException,
			InterruptedException {
		return null;
	}

	@Override
	public void checkout(Run<?, ?> build, Launcher launcher,
			FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline)
			throws IOException, InterruptedException {
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return null;
	}
	
}