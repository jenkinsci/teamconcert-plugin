/*******************************************************************************
 * Copyright © 2017, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.PostBuildDeliverResult;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildConstants;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildStatus;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;
import com.ibm.team.build.internal.hjplugin.util.PostBuildDeliverTriggerPolicy;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

/**
 * 
 * A publisher to perform post build deliver for RTC SCM
 * 
 * Works only if RTC SCM is configured with a build definition and there is a non null
 * build result UUID in {@link RTCBuildResultAction}
 * For other build configuration, no action is performed.
 * 
 * If the user wishes to mark the build as failed if post build deliver fails, for instance 
 *    <ul>
 *    	<li>HTTP connection errors</li>
 *      <li>Server exceptions (DB connection issues?) </li>
 *      <li>Post Build Deliver could not be performed due to configuration error</li>
 *      <li>Post Build Deliver failed due to StaleDataExceptions</li>
 *    </ul>
 *  then the Jenkins build will marked as failed.   
 *    
 *
 */
public class RTCPostBuildDeliverPublisher extends Recorder implements SimpleBuildStep {
	private static final Logger LOGGER = Logger.getLogger(RTCPostBuildDeliverPublisher.class.getName());
	private boolean failOnError = true;
	private static String EXPECTED_SERVER_VERSION="6.0.4";
	
	@DataBoundConstructor
	public RTCPostBuildDeliverPublisher(boolean failOnError) {
		this.failOnError = failOnError;
	}
	
	public boolean getFailOnError() {	
		return failOnError;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		String buildResultItemId = "<unknown>";
		String buildResultLabel = "<unknown>";
		LOGGER.finest("Entering to perform post build deliver");
		List<RTCBuildResultAction> actions = build.getActions(RTCBuildResultAction.class);
		for (RTCBuildResultAction action : actions) {
			try {
				// First check whether post build deliver was handled
				boolean handled = false;
				if (action.getBuildProperties() != null && action.getBuildProperties().containsKey(RTCJobProperties.POST_BUILD_DELIVER_HANDLED)) {
					handled = Boolean.parseBoolean(action.getBuildProperties().get(RTCJobProperties.POST_BUILD_DELIVER_HANDLED));
				}
				if (handled) {
					continue;
				}
				buildResultItemId = (Util.fixEmptyAndTrim(action.getBuildResultUUID()) != null ) ? action.getBuildResultUUID(): buildResultItemId;
				buildResultLabel = (action.getBuildProperties() != null && 
									action.getBuildProperties().get(RTCBuildConstants.BUILD_RESULT_LABEL) != null) ? 
													action.getBuildProperties().get(RTCBuildConstants.BUILD_RESULT_LABEL) : buildResultLabel;
				boolean performed = handlePostBuildDeliver(build, workspace, action, listener);
				if (performed) {
					listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_success(buildResultLabel, buildResultItemId));
				}
				else {
					if (buildResultItemId.equals("<unknown>")) {
						listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_skipped());
					} else {
						listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_skipped2(buildResultLabel, buildResultItemId));
					}
				}
			} catch (Exception exp) {
				LOGGER.log(Level.WARNING, 
						String.format("Post build deliver failed for %s with build label %s. Exception is : ", 
								buildResultItemId, buildResultLabel), exp); 
				/*
				 * Log the exception and mark the build as failed, if the user wishes to do so
				 */ 
				if (failOnError) {
					listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_setting_build_to_failure());
					build.setResult(Result.FAILURE);
				}
				
				// Add a message to the listener to indicate that PB deliver has failed with an exception.
				listener.getLogger().println(
						Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_failure_for_build_result(buildResultLabel, buildResultItemId));
			} finally {
				// Add a marker to RTCBuildResultAction to indicate that post build deliver was handled
				if (action.getBuildProperties() != null) {
					action.getBuildProperties().put(RTCJobProperties.POST_BUILD_DELIVER_HANDLED, RTCBuildConstants.TRUE);
				}
				// The idea is to continue with other post build deliver irrespective of one failure
				// If the following post build deliver's trigger policy allows for delivering changes, then it will happen
				// otherwise it will be skipped.
			}
		}
	}
	
	/**
	 * If the given RTC SCM invocation (represented by the {@link RTCBuildResultAction}) is for a 
	 * build definition, then perform a post build deliver.
	 * First we check whether the server supports post build deliver (any server above 6.0.4 and above)
	 * Then we perform the following checks
	 * 1) Whether post build deliver is configured on the build definition. If post build deliver is not  
	 *    configured, then we skip to the next RTC Scm
	 * 2) Whether its trigger policy allows PB deliver to proceed given the status of the Jenkins build
	 * 3) If both checks are successful, then post build deliver is invoked.
	 * 
	 * Note:
	 * It is possible that the build definition is configured with post build 
	 * deliver, but this is a personal build. In that case, post build deliver will not throw
	 * any errors.
	 * 
	 * @param build - The build for which post build deliver is to be performed. Cannot be <code>null</code>.
	 * @param action - The action containing the build result UUID. Cannot be <code>null</code>.
	 * @param listener - The listener to which messages can logged.
	 * 
	 * @return <code>true</code> if post build deliver was performed. 
	 * 		   <code>false</code> if post build deliver was skipped
	 * @throws Exception if post build deliver was not performed due to some error.
	 */
	private boolean handlePostBuildDeliver(Run<?, ?> build, FilePath workspace, RTCBuildResultAction action, TaskListener listener) throws Exception {
		// First get the RTCScm out from the build
		RTCScm scm = RTCRunListener.getRTCScm(build, action);
		if (scm != null) {
			// Check whether build definition is configured, if not, we can skip this RTC SCM instance
			if (!scm.getBuildTypeStr().equals(RTCScm.BUILD_DEFINITION_TYPE)) {
				listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_ignored_not_configured_for_build_definition());
				return false;
			}
			
			// Check whether we have a non null/empty build result UUID
			String buildResultItemId = Util.fixEmptyAndTrim(action.getBuildResultUUID());
			if (buildResultItemId == null) {
				listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_no_build_result());
				throw new IOException(Messages.RTCPostBuildDeliverPublisher_no_build_result(), null);
			}
			
			Node node = workspace.toComputer().getNode();
			String localBuildToolkit = scm.getDescriptor().getBuildToolkit(scm.getBuildTool(), node, listener);
			String masterBuildToolkit = scm.getDescriptor().getMasterBuildToolkit(scm.getBuildTool(), listener);
			RTCLoginInfo loginInfo = scm.getLoginInfo(build.getParent(), masterBuildToolkit);

			// Handle version compatibility at this point. If we know that the version is less than 6.0.4,
			// return a reasonable message and skip to the next SCM action
			String errorMessage = RTCFacadeFacade.testConnection(null, loginInfo.getServerUri(), 
								loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), 
								RTCBuildConstants.URI_COMPATIBILITY_CHECK_604, true);
			if (errorMessage != null && errorMessage.length() > 0) {
					listener.getLogger().println(
							Messages.RTCPostBuildDeliverPublisher_incompatible_server_version(loginInfo.getServerUri(), EXPECTED_SERVER_VERSION, errorMessage));
					return false;
			}
			
			// Deliberately pass the toolkit resolved in the slave or may be master, 
			// if that is where we are performing this build step
			// It is possible that the user runs checkout in one slave and post build deliver in another
			// In that case, we expect the build toolkit to be present in the other slave.
			// If not, we will get an exception.
			RTCBuildDefinitionDetailsTask task = new RTCBuildDefinitionDetailsTask(localBuildToolkit,
								loginInfo.getServerUri(), loginInfo.getUserId(),  
								loginInfo.getPassword(), loginInfo.getTimeout(), 
								buildResultItemId, LocaleProvider.getLocale(), 
								Helper.isDebugEnabled(build, listener), 
								listener);
			BuildDefinitionInfo buildDefinitionInfo = workspace.act(task);
			
			log(buildDefinitionInfo);
			
			// First check whether post build deliver is configured.
			// If yes, get the trigger policy to decide whether to continue with post build deliver.
			// If no, then we can return while logging a message that post build deliver is not configured
			if (buildDefinitionInfo.isPBConfigured()) {
				
				// Check whether pb deliver is enabled
				if (!buildDefinitionInfo.isPBEnabled()) {
					listener.getLogger().println(
							Messages.RTCPostBuildDeliverPublisher_post_build_deliver_disabled(buildDefinitionInfo.getId()));
					return false;
				}
				
				if (buildDefinitionInfo.isPBTriggerPolicyUnknown()) {

					listener.getLogger().println(
							Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_incorrect_trigger_policy(scm.getBuildDefinition()));
					throw new IOException(Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_incorrect_trigger_policy(scm.getBuildDefinition()), null);
					
				} else {
					
					// Match trigger policy to Jenkins build status
					String triggerPolicyStr = buildDefinitionInfo.getPBTriggerPolicy();
					PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.valueOf(triggerPolicyStr);
					
					// Check whether the trigger policy matches Jenkins build status
					// In a pipeline build, the result may not be set yet.
					// So assume success and then proceed to post build deliver
					if (build.getResult() == null || triggerPolicy.matches(build.getResult())) {
						
						LOGGER.finest("Proceeding with post build deliver after trigger policy match");
						
						String buildResultLabel = (action.getBuildProperties() != null && 
								action.getBuildProperties().get(RTCBuildConstants.BUILD_RESULT_LABEL) != null) ? 
												action.getBuildProperties().get(RTCBuildConstants.BUILD_RESULT_LABEL) : null;
						// Everything is fine, start post build deliver
						PostBuildDeliverResult result = RTCFacadeFacade.postBuildDeliver(loginInfo.getServerUri(),
								loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), buildResultItemId, buildResultLabel, listener);
						// If build result is not OK, only print the participant summary and log the same as well, throw an exception with the participant
						// summary.
						// If build result is OK, check whether PB deliver really happened.
						// If yes, then print the participant log.
						// else, print the participant summary and return that PB deliver was skipped.
						if (RTCBuildStatus.OK.equals(result.getBuildStatus())) {
							// Check whether post build deliver was done
							if (result.isDelivered()) {
								if (result.getParticipantLog() != null) {
									listener.getLogger().println(result.getParticipantLog());
								} else {
									// Log a message that content URI could not be retrieved
									listener.getLogger().println(
											Messages.RTCPostBuildDeliverPublisher_unable_to_retrieve_log(
													(result.getContentURI() != null)? result.getContentURI() : "<no URL>"));
								}
								return true;
							} else {
								listener.getLogger().println(result.getParticipantSummary());
								// Post build deliver was aborted on purpose (due to some configuration issue)
								// This is also considered non fatal.
								return false;
							}
						} else { // PB deliver failed due to a condition that was unavoidable but known like SDE
							listener.getLogger().println(result.getParticipantSummary());
							throw new IOException(result.getParticipantSummary());
						}
					} else {
						// Post build deliver could not be performed because the trigger policy prevented it.
						// But this is not fatal, so we return normally
						listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_trigger_policy_prevents_pb_deliver(
													buildResultItemId, build.getResult().toString(), triggerPolicy.toString()));
						return false;
					}
				}
				
			} else {
				listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_postbuild_deliver_not_configured(scm.getBuildDefinition()));
				return false;
			}
		} else {
			LOGGER.warning("Build indicates that RTC SCM was used but unable to retrieve RTC SCM configuration"); //$NON-NLS-1$
			listener.getLogger().println(Messages.RTCPostBuildDeliverPublisher_unable_to_find_rtc_scm());
			throw new IOException(Messages.RTCPostBuildDeliverPublisher_unable_to_find_rtc_scm(), null);
		}
	}

	private void log(BuildDefinitionInfo buildDefinitionInfo) {
		if (buildDefinitionInfo == null) {
			LOGGER.warning("Received a null build definition info");
		} else {
			LOGGER.finest("BuildDefinitionInfo : \n" +
						"id : "  + buildDefinitionInfo.getId() + "\n" +
						"configured : " + buildDefinitionInfo.isPBConfigured() + "\n" +
						"triggerPolicy : " + buildDefinitionInfo.getPBTriggerPolicy() + "\n" + 
						"triggerPolicyUnknown : " + buildDefinitionInfo.isPBTriggerPolicyUnknown()
						);
		}
		
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public String getDisplayName() {
			return Messages.RTCPostBuildDeliverPublisher_post_build_deliver_title();
		}

		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> arg0) {
			return true;
		}
		
		@Override
        public RTCPostBuildDeliverPublisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(RTCPostBuildDeliverPublisher.class,formData);
		}
	}
}
