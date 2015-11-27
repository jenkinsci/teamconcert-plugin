/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import hudson.scm.SCM;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.util.RTCBuildResultHelper;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;
import com.ibm.team.build.internal.hjplugin.util.RTCScmConfigHelper;


@SuppressWarnings("rawtypes")
@Extension
public class RTCRunListener extends RunListener<Run> {

	private static final Logger LOGGER = Logger.getLogger(RTCRunListener.class.getName());

	public RTCRunListener() {
		super(Run.class);
	}

	@Override
	public void onCompleted(Run build, TaskListener listener) {
		LOGGER.finest("onCompleted : Start");
		// if launched by Hudson/Jenkins terminate the build created in RTC
		try {
			List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
			for (RTCBuildResultAction action : actions) {
				try {
					if (action.ownsBuildResultLifecycle()) {
						SCM scmSystem = null;
						if (build instanceof AbstractBuild) {
							LOGGER.finer("build is not a workflow job");
							scmSystem = ((AbstractBuild)build).getProject().getScm();
						}
						
						RTCScm scm = null;
						if (scmSystem instanceof RTCScm) {
							scm = (RTCScm) scmSystem;
						} else {
							// we are assuming that the action is from when the build was started and that
							// transient SCM is still available.
							LOGGER.finer("Getting SCM from action");
							scm = action.getScm();
							if (scm == null && build instanceof AbstractBuild) {
								// perhaps the action has be re-constituted from the serialized format.
								// see if we can pull it out of the multi-SCM plugin.
								Set<RTCScm> rtcScms = RTCScmConfigHelper.getCurrentConfigs(((AbstractBuild)build).getProject());
								scm = RTCScmConfigHelper.findRTCScm(rtcScms, action);
							}
						}
						
						if (scm != null) {
			    if (!scm.getKeepBuildResultOpen()) {
							LOGGER.finer("Completed Build: " + build.getDisplayName() + //$NON-NLS-1$
									" Build Result UUID: " + action.getBuildResultUUID() + //$NON-NLS-1$
									" Server URI=\"" + scm.getServerURI() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
									" Build result=\"" + build.getResult() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
		
							String masterBuildToolkit = scm.getDescriptor().getMasterBuildToolkit(scm.getBuildTool(), listener);
							RTCLoginInfo loginInfo = scm.getLoginInfo(build.getParent(), masterBuildToolkit);
				    		RTCFacadeFacade.terminateBuild(masterBuildToolkit,
									loginInfo.getServerUri(),
									loginInfo.getUserId(), loginInfo.getPassword(),
									loginInfo.getTimeout(),
									scm.getAvoidUsingToolkit(),
									action.getBuildResultUUID(),
					build.getResult(), listener);
			    } else {
				LOGGER.finer("Current Build: " + build.getDisplayName() + //$NON-NLS-1$
					" Build Result UUID: "
					+ action.getBuildResultUUID() + //$NON-NLS-1$
					" Build Result set to be kept open (ensure to close it afterwards - i.e. Ant Task)"); //$NON-NLS-1$
			    }
						} else {
							LOGGER.finer("Completed Build: " + build.getDisplayName() + //$NON-NLS-1$
								" Build Result UUID: " + action.getBuildResultUUID() + //$NON-NLS-1$
								" Unable to manage lifecycle (no access to the H/J SCM configuration)"); //$NON-NLS-1$
							PrintStream writer = listener.getLogger();
							writer.println(Messages.RTCRunListener_build_result_not_completed(scmSystem.getClass().getName()));
				    		writer.println(Messages.RTCRunListener_manually_abandon_build());
						}
					} else {
						LOGGER.finer("Completed Build: " + build.getDisplayName() + //$NON-NLS-1$
								" Build Result UUID: " + action.getBuildResultUUID() + //$NON-NLS-1$
								" initiated/managed by RTC"); //$NON-NLS-1$
					}
				} catch (InvocationTargetException e) {
		    		Throwable eToReport = e.getCause();
		    		if (eToReport == null) {
		    			eToReport = e;
		    		}
		    		PrintWriter writer = listener.error(Messages.RTCRunListener_build_termination_failure(eToReport.getMessage()));
		    		writer.println(Messages.RTCRunListener_manually_abandon_build());
		    		if (RTCScm.unexpectedFailure(eToReport)) {
		    			eToReport.printStackTrace(writer);
		    		}
		    		LOGGER.log(Level.FINER, "terminateBuild failed " + eToReport.getMessage(),  eToReport); //$NON-NLS-1$
	    		
		    	} catch (Exception e) {
		    		PrintWriter writer = listener.fatalError(Messages.RTCRunListener_build_termination_failure2(e.getMessage()));
		    		writer.println(Messages.RTCRunListener_manually_abandon_build());
		    		if (RTCScm.unexpectedFailure(e)) {
		    			e.printStackTrace(writer);
		    		}
		    		LOGGER.log(Level.FINER, "terminateBuild failed " + e.getMessage(), e); //$NON-NLS-1$
				} 
			}
			if (actions.isEmpty()) {
				LOGGER.finer("Completed Build: " + build.getDisplayName() + " No RTC build result associated."); //$NON-NLS-1$ //$NON-NLS-2$
			}

    	} finally {
    		super.onCompleted(build, listener);
    	}
		LOGGER.finest("onCompleted : End");
	}

	@Override
	public void onDeleted(Run r) {
		LOGGER.finest("onDeleted : Start");
		// delete the build results associated with the build if any
		try {
			List<RTCBuildResultAction> buildResultActions = r.getActions(RTCBuildResultAction.class);
			if (!buildResultActions.isEmpty() && r instanceof AbstractBuild) {
				LOGGER.finer("build is an AbstractBuild");
				// get the RTCScms configured if any (could be >1 if Multi SCM plugin involved.
				// (job may of changed so that it nolonger has RTCScm as the SCM provider)
				Set<RTCScm> rtcScmConfigs = RTCScmConfigHelper.getCurrentConfigs(((AbstractBuild)r).getProject());
				RTCBuildResultHelper.deleteRTCBuildResults(buildResultActions, ((AbstractBuild)r).getProject(), rtcScmConfigs);
			}
		} finally {
			super.onDeleted(r);
		}
		LOGGER.finest("onDeleted : End");
	}
}
