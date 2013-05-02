/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.listeners.RunListener;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;


@Extension
public class RTCRunListener extends RunListener<AbstractBuild> {

    private static final Logger LOGGER = Logger.getLogger(RTCRunListener.class.getName());

	public RTCRunListener() {
		super(AbstractBuild.class);
	}

	@Override
	public void onCompleted(AbstractBuild build, TaskListener listener) {
		
		// if launched by Hudson/Jenkins terminate the build created in RTC
		try {
			if (build.getProject().getScm() instanceof RTCScm) {
				RTCBuildResultAction action = build.getAction(RTCBuildResultAction.class);
				if (action != null) {
					if (action.createdBuildResult()) {

						RTCScm scm = (RTCScm) build.getProject().getScm();

						LOGGER.finer("Completed Build: " + build.getDisplayName() +
								" Build Result UUID: " + action.getBuildResultUUID() +
								" Server URI=\"" + scm.getServerURI() + "\"" +
								" Build result=\"" + build.getResult() + "\"");

						boolean aborted = false;
						Result buildResult = build.getResult();
						int buildState = 0;
						if (buildResult.equals(Result.ABORTED)) {
							aborted = true;
						} else if (buildResult.equals(Result.UNSTABLE)) {
							buildState = 1;
						} else if (!buildResult.equals(Result.SUCCESS)) {
							buildState = 2;
						}
						
						String masterBuildToolkit = scm.getDescriptor().getMasterBuildToolkit(scm.getBuildTool(), listener);
			    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(masterBuildToolkit, null);
						facade.invoke(
								"terminateBuild", //$NON-NLS-1$
								new Class[] { String.class, // serverURI
										String.class, // userId
										String.class, // password
										File.class, // passwordFile
										int.class, // timeout
										String.class, // buildResultUUID
										boolean.class, // aborted,
										int.class, // buildState,
										Object.class,}, // listener
								scm.getServerURI(), scm.getUserId(), scm.getPassword(),
								scm.getPasswordFileFile(), scm.getTimeout(), action.getBuildResultUUID(),
								aborted, buildState,
								listener);
					} else {
						LOGGER.finer("Completed Build: " + build.getDisplayName() +
								" Build Result UUID: " + action.getBuildResultUUID() +
								" initiated/managed by RTC");
					}
					
				} else {
					LOGGER.finer("Completed Build: " + build.getDisplayName() + " No RTC build result associated.");
				}
			}
    	} catch (InvocationTargetException e) {
    		Throwable eToReport = e.getCause();
    		if (eToReport == null) {
    			eToReport = e;
    		}
    		PrintWriter writer = listener.error(Messages.RTCRunListener_build_termination_failure(eToReport.getMessage()));
    		if (RTCScm.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		LOGGER.log(Level.FINER, "terminateBuild failed " + eToReport.getMessage(),  eToReport); //$NON-NLS-1$
    		
    	} catch (Exception e) {
    		PrintWriter writer = listener.fatalError(Messages.RTCRunListener_build_termination_failure2(e.getMessage()));
    		if (RTCScm.unexpectedFailure(e)) {
    			e.printStackTrace(writer);
    		}
    		LOGGER.log(Level.FINER, "terminateBuild failed " + e.getMessage(), e); //$NON-NLS-1$

    	}
		super.onCompleted(build, listener);
	}
}
