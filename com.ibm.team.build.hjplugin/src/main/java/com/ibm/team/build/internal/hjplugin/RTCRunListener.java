/*******************************************************************************
 * Copyright (c) 2013, 2017 IBM Corporation and others.
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.localizer.LocaleProvider;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.util.Helper;
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
						// This allows us to get the current SCM configuration. If it is RTC, then we get the 
						// latest configuration
						if (build instanceof AbstractBuild) {
							scmSystem = ((AbstractBuild)build).getProject().getScm(); 
						}
						
						RTCScm scm = null;
						if (scmSystem instanceof RTCScm) {
							scm = (RTCScm) scmSystem;
						} else {
							// we are assuming that the action is from when the build was started and that
							// transient SCM is still available.
							scm = action.getScm();
							// Special handling for freestyle project and multi scm configuration
							if (scm == null && build instanceof AbstractBuild) {
								// perhaps the action has be re-constituted from the serialized format.
								// see if we can pull it out of the multi-SCM plugin.
								Set<RTCScm> rtcScms = RTCScmConfigHelper.getCurrentConfigs(((AbstractBuild)build).getProject());
								scm = RTCScmConfigHelper.findRTCScm(rtcScms, action);
							}
							// In pipeline jobs, a build can be saved and restarted. 
							// In those cases, RTCScm will not be available from action 
							// since it is transient
						}
						
						if (scm != null) {
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
									build.getResult(),
									listener);
						} else {
							LOGGER.finer("Completed Build: " + build.getDisplayName() + //$NON-NLS-1$
								" Build Result UUID: " + action.getBuildResultUUID() + //$NON-NLS-1$
								" Unable to manage lifecycle (no access to the H/J SCM configuration)"); //$NON-NLS-1$
							PrintStream writer = listener.getLogger();
							if (scmSystem != null) {
								writer.println(Messages.RTCRunListener_build_result_not_completed(scmSystem.getClass().getName()));
							} else {
								writer.println(Messages.RTCRunListener_build_result_not_completed_no_scm());
							}
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
				// Handle deletion of temporary workspace created during snapshot or stream build
				handleDeleteOfTempRepositoryWorkspace(build, action, listener);				
			}
			if (actions.isEmpty()) {
				LOGGER.finer("Completed Build: " + build.getDisplayName() + " No RTC build result associated."); //$NON-NLS-1$ //$NON-NLS-2$
				LOGGER.finer("No Repository Workpsaces to delete."); //$NON-NLS-1$
			}

    	} finally {
    		super.onCompleted(build, listener);
    	}
		LOGGER.finest("onCompleted : End");
	}

	/**
	 * Handles deletion of any temporary repository workspace created during the build
	 * The temporary repository workspace details are stored in RTCBuildResultAction.
	 * Note that if there is more than one invocation of RTCScm in a build, then there will
	 * be as many RTCBuildResultActions.
	 * The temporary repository workspace UUID and name are available in the following build
	 * properties
	 * rtcTempRepoWorkspaceUUID
	 * rtcTempRepoWorkspaceName
	 * @param build - The Build which created the temporary repository workspace
	 * @param action
	 * @param listener
	 */
	private void handleDeleteOfTempRepositoryWorkspace(Run<?,?> build, RTCBuildResultAction action, TaskListener listener) {
		String workspaceUUID = null;
		String workspaceName = null;
		try {
			LOGGER.finest("Entering to delete temporary Repsitory Workspaces created during stream or snapshot load");
			SCM scmSystem = null;
			// This allows us to get the current SCM configuration. If it is RTC, then we get the 
			// latest configuration
			if (build instanceof AbstractBuild) {
				scmSystem = ((AbstractBuild)build).getProject().getScm(); 
			}
			
			RTCScm scm = getRTCScm(build, action, scmSystem);
			
			workspaceUUID = getWorkspaceUUID(action);
			workspaceName = getWorkspaceName(action);
			
			if (workspaceUUID == null) {
				LOGGER.finer("Completed Build: " + build.getDisplayName() + //$NON-NLS-1$
							" No Repository Workspace to delete"); //$NON-NLS-1$
				return;
			}
			// At this point, we either have RTC SCM or we don't
			if (scm != null) {
				// Try to delete the workspace. If any exception is thrown, then
				// it is handled and never thrown out of this method
				String masterBuildToolkit = scm.getDescriptor().getMasterBuildToolkit(scm.getBuildTool(), listener);
				RTCLoginInfo loginInfo = scm.getLoginInfo(build.getParent(), masterBuildToolkit);
				boolean debug = Boolean.parseBoolean(Helper.getStringBuildParameter(build, RTCJobProperties.DEBUG_PROPERTY, listener));

				RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(masterBuildToolkit, debug?listener.getLogger():null);
				facade.invoke("deleteWorkspace", new Class[] { //$NON-NLS-1$
						String.class, // serverURI,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class, // workspaceUUID
						String.class, // workspaceName
						Object.class, // listener)
						Locale.class, // locale
				}, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), 
				loginInfo.getTimeout(), workspaceUUID, workspaceName, listener, LocaleProvider.getLocale());
				PrintStream writer = listener.getLogger();
				writer.println(Messages.RTCRunListener_delete_repo_workspace_success(workspaceName));
				
			} else {
				// If we don't have RTC SCM, then write to the build log
				// that temporary workspace by name and UUID
				// should be deleted manually.
				LOGGER.finer("Completed Build: " + build.getDisplayName() + //$NON-NLS-1$
						"Repository Workspace Name: " + workspaceName + //$NON-NLS-1$
						"Repository Workspace UUID: " + workspaceUUID + //$NON-NLS-1$
						"Unable to delete temporary Repository Workspace. No access to the H/J SCM Configuration");
				
				PrintStream writer = listener.getLogger();
				writer.println(Messages.RTCRunListener_repo_workspace_not_deleted(workspaceUUID, workspaceName));
				writer.println(Messages.RTCRunListener_manually_delete_repo_workspace());
			}
		} catch (InvocationTargetException e) {
			// Get the inner exception to report. If it is not available,
			// then just go with the current exception object.
			Throwable eToReport = e.getCause();
			if (eToReport == null) {
				eToReport = e;
			}
			PrintWriter writer = listener.error(Messages.RTCRunListener_repo_workspace_delete_failure(eToReport.getMessage()));
			printRepoWorkspaceNotDeleted(writer, workspaceUUID, workspaceName);
			
			if (RTCScm.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		LOGGER.log(Level.FINER, "delete temporary Repository Workspace failed " + eToReport.getMessage(),  eToReport); //$NON-NLS-1$
		} catch (Exception e) { // Generic exception is logged 
			PrintWriter writer = listener.fatalError(Messages.RTCRunListener_repo_workspace_delete_failure(e.getMessage()));
			printRepoWorkspaceNotDeleted(writer, workspaceUUID, workspaceName);
    		if (RTCScm.unexpectedFailure(e)) {
    			e.printStackTrace(writer);
    		}
    		LOGGER.log(Level.FINER, "delete temporary Repository Workspace failed " + e.getMessage(),  e); //$NON-NLS-1$
		} finally {
			LOGGER.finest("Exiting from delete temporary Repository Workspaces");
		}
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
	
	static RTCScm getRTCScm(Run<?,?> build, RTCBuildResultAction action) {
		SCM scmSystem = null;
		// This allows us to get the current SCM configuration. If it is RTC, then we get the 
		// latest configuration
		if (build instanceof AbstractBuild) {
			scmSystem = ((AbstractBuild)build).getProject().getScm(); 
		}
		
		return getRTCScm(build, action, scmSystem);
	}
	
	static RTCScm getRTCScm(Run<?,?> build, RTCBuildResultAction action, SCM scmSystem) {
		RTCScm scm = null;
		if (scmSystem != null && scmSystem instanceof RTCScm) {
			scm = (RTCScm) scmSystem;
		} else {
			// The action is from when the build was started and that
			// transient SCM is still available.
			// Some cases like pipeline that allows to builds to be saved 
			// and restarted, the transient SCM is not available because 
			// the state of RTCBuildResultAction that is serialized to disk
			// does not include RTCScm object.s
			scm = action.getScm();
			// Special handling for freestyle project and multi scm configuration
			if (scm == null && build instanceof AbstractBuild) {
				// perhaps the action has be re-constituted from the serialized format.
				// see if we can pull it out of the multi-SCM plugin.
				Set<RTCScm> rtcScms = RTCScmConfigHelper.getCurrentConfigs(((AbstractBuild)build).getProject());
				scm = RTCScmConfigHelper.findRTCScm(rtcScms, action);
			}
		}
		return scm;
	}
	
	private void printRepoWorkspaceNotDeleted(PrintWriter writer, String workspaceUUID, String workspaceName) {
		if (workspaceUUID != null) {
			writer.println(Messages.RTCRunListener_repo_workspace_not_deleted(workspaceUUID, workspaceName));
		}
		writer.println(Messages.RTCRunListener_manually_delete_repo_workspace());
	}

	private String getWorkspaceName(RTCBuildResultAction action) {
		Map<String, String> buildProperties = action.getBuildProperties();
		return buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_NAME);
	}

	private String getWorkspaceUUID(RTCBuildResultAction action) {
		Map<String, String> buildProperties = action.getBuildProperties();
		return buildProperties.get(RTCJobProperties.TEMPORARY_WORKSPACE_UUID);
	}

}
