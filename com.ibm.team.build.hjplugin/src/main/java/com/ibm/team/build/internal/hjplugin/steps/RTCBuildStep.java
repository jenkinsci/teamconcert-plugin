/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2008, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.steps;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildToolInstallation;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;
import com.ibm.team.build.internal.hjplugin.RTCScm;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildConstants;
import com.ibm.team.build.internal.hjplugin.util.Tuple;
import com.ibm.team.build.internal.hjplugin.util.ValidationHelper;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class RTCBuildStep extends Step {
	
	private static final Logger LOGGER = Logger.getLogger(RTCBuildStep.class.getName());

	private static final String RTC_BUILD_STEP_NAME = "rtcBuild";
	private static final String WAIT_FOR_BUILD = "waitForBuild";
	private static final String REQUEST_BUILD = "requestBuild";
	private static final String LIST_LOGS = "listLogs";
	private static final String LIST_ARTIFACTS = "listArtifacts";
	private static final String DOWNLOAD_LOG = "downloadLog";
	private static final String DOWNLOAD_ARTIFACT = "downloadArtifact";
	private static final String RETRIEVE_SNAPSHOT = "retrieveSnapshot";
	
	private static final int WAIT_BUILD_TIMEOUT = DescriptorImpl.defaultWaitBuildTimeout; // Wait forever
	private static final int WAIT_BUILD_INTERVAL = DescriptorImpl.defaultWaitBuildInterval; // 30 seconds
	private static final String DEFAULT_BUILD_STATES = DescriptorImpl.defaultBuildStates;
	private static final int DEFAULT_MAX_RESULTS = DescriptorImpl.defaultMaxResults;

	// TODO We should have a server configuration object
	// makes it easy for replacing variables in groovy
	// Users don't have to know it is a separate object though
	
	private String serverURI;
	private String credentialsId;
	private int timeout = 0;
	private String buildTool;
	private RTCTask task;
	
    public static class RTCTask extends AbstractDescribableImpl<RTCTask> {
		private String name;
		private String buildDefinitionId;
		private boolean deleteProperties;
		private List<BuildProperty> propertiesToDelete;
		private boolean addOrOverrideProperties;
		private List<BuildProperty> propertiesToAddOrOverride;
		private boolean linkEWMBuild;
		private String buildResultUUID;
		
		// Fields related to waitForBuild 
		private String buildStates= DEFAULT_BUILD_STATES;
		private long waitBuildTimeout = WAIT_BUILD_TIMEOUT;
		private long waitBuildInterval = WAIT_BUILD_INTERVAL;

		// Fields specific to listLogs/listArtifacts
		private String fileNameOrPattern;
		private int maxResults = DEFAULT_MAX_RESULTS;
		
		// Fields specific to downloadLog/downloadArtifact
		private String fileName;
		private String contentId;
		private String destinationFileName;
		
		
		// Fields for listLogs/listArtifacts/downloadLog/downloadArtifact
		private String componentName;

		@DataBoundConstructor
		public RTCTask(String name) {
			this.name =name;
		}
		
		public String getTaskName() {
			return this.name;
		}
		
		@DataBoundSetter
		public void setBuildDefinitionId(String buildDefinitionId) {
			this.buildDefinitionId = buildDefinitionId;		
		}
		
		public String getBuildDefinitionId() {
			return this.buildDefinitionId;
		}
		
		@DataBoundSetter
		public void setDeleteProperties(boolean deleteProperties) {
			this.deleteProperties = deleteProperties;
		}

		public boolean getDeleteProperties() {
			return this.deleteProperties;
		}

		@DataBoundSetter
		public void setPropertiesToDelete(List<BuildProperty> propertiesToDelete) {
			this.propertiesToDelete = propertiesToDelete;
		}

		public List<BuildProperty> getPropertiesToDelete() {
			return this.propertiesToDelete;
		}

		@DataBoundSetter
		public void setAddOrOverrideProperties(boolean addOrOverrideProperties) {
			this.addOrOverrideProperties = addOrOverrideProperties;
		}

		public boolean getAddOrOverrideProperties() {
			return this.addOrOverrideProperties;
		}

		@DataBoundSetter
		public void setPropertiesToAddOrOverride(List<BuildProperty> propertiesToAddOrOverride) {
			this.propertiesToAddOrOverride = propertiesToAddOrOverride;
		}

		public List<BuildProperty> getPropertiesToAddOrOverride() {
			return this.propertiesToAddOrOverride;
		}
		
		@DataBoundSetter
		public void setLinkEWMBuild(boolean linkEWMBuild) {
			this.linkEWMBuild = linkEWMBuild;
		}

		public boolean getLinkEWMBuild() {
			return this.linkEWMBuild;
		}
		
		@DataBoundSetter
		public void setBuildResultUUID(String buildResultUUID) {
			this.buildResultUUID = buildResultUUID;		
		}
		
		public String getBuildResultUUID() {
			return this.buildResultUUID;
		}

		public String getName() {
			return this.name;
		}
		
		@DataBoundSetter
		public void setBuildStates(String buildStates) {
			this.buildStates = buildStates;
		}
		
		@DataBoundSetter
		public void setWaitBuildTimeout(int waitBuildTimeout) {
			this.waitBuildTimeout = waitBuildTimeout;
		}
		
		public long getWaitBuildTimeout() {
			return waitBuildTimeout;
		}

		public String getBuildStates() { 
			return buildStates;
		}
		
		public String getFileNameOrPattern() {
			return fileNameOrPattern;
		}
		
		@DataBoundSetter
		public void setFileNameOrPattern(String fileNameOrPattern) {
			this.fileNameOrPattern = fileNameOrPattern;
		}
		
		public String getFileName() {
			return fileName;
		}
		
		@DataBoundSetter
		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public String getComponentName() {
			return componentName;
		}

		@DataBoundSetter
		public void setComponentName(String componentName) {
			this.componentName = componentName;
		}

		public int getMaxResults() {
			return maxResults;
		}

		@DataBoundSetter
		public void setMaxResults(int maxResults) {
			this.maxResults = maxResults;
		}

		public String getDestinationFileName() {
			return destinationFileName;
		}

		@DataBoundSetter
		public void setDestinationFileName(String destinationFileName) {
			this.destinationFileName = destinationFileName;
		}
		
		public String getContentId() {
			return contentId;
		}
		
		@DataBoundSetter
		public void setContentId(String contentId) {
			this.contentId = contentId;
		}
		
		@Extension
		public static class DescriptorImpl extends Descriptor<RTCTask> {
			
		}
		
		@DataBoundSetter
		public void setWaitBuildInterval(long waitBuildInterval) {
			this.waitBuildInterval = waitBuildInterval;
		}
		
		public long getWaitBuildInterval () {
			return this.waitBuildInterval;
		}
	}
    
    public static class BuildProperty {
		private String propertyName;
		private String propertyValue;

		@DataBoundConstructor
		public BuildProperty(String propertyName) {
			this.propertyName = propertyName;
		}

		@DataBoundSetter
		public void setPropertyName(String propertyName) {
			this.propertyName = propertyName;
		}

		public String getPropertyName() {
			return this.propertyName;
		}

		@DataBoundSetter
		public void setPropertyValue(String propertyValue) {
			this.propertyValue = propertyValue;
		}

		public String getPropertyValue() {
			return this.propertyValue;
		}
	}

	@DataBoundConstructor
	public RTCBuildStep(RTCTask task, String taskName) {
		this.task = task;
		// if RTCTask had data bound setters, the generated snippet does not 
		// have ( ) surrounding it, which leads to an NPE.
		// The only option is to add a dummy parameter here which 
		// can be null but its value is derived from the task. 
	}
	
	public String getTaskName() {
		return this.getTask().getName();
	}
	
	@DataBoundSetter
	public void setBuildTool(String buildTool) {
		this.buildTool = Util.fixEmptyAndTrim(buildTool);
	}
	
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }
	
	@DataBoundSetter
	public void setCredentialsId(String credentialsId) {
		this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
	}
	
	@DataBoundSetter
	public void setServerURI(String serverURI) {
		this.serverURI = Util.fixEmptyAndTrim(serverURI);
	}
	
	@DataBoundSetter
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	public int getTimeout() {
		// timeout should never be 0 or negative.
		// if the user explicitly sets a 0 or a negative value,  
		// we will pick the global one. There is no real way 
		// to say if timeout was set or not since it gets 
		// the default value 0, if not set explicitly.
		if (timeout == 0) {
			return getDescriptor().getGlobalTimeout();
		}
		return this.timeout;
	}
	public String getServerURI() {
		if (serverURI == null || serverURI.isEmpty()) {
			return getDescriptor().getGlobalServerURI();
		}
		return this.serverURI;
	}
	
	public String getCredentialsId() {
		if (credentialsId == null || credentialsId.isEmpty()) {
			return getDescriptor().getGlobalCredentialsId();
		}
		return this.credentialsId;
	}
	
	public String getBuildTool() {
		if (buildTool == null || buildTool.isEmpty()) {
			// Return the global one 
			return getDescriptor().getGlobalBuildTool();
		}
		return this.buildTool;
	}
	
	public RTCTask getTask() {
		return this.task;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		LOGGER.entering(this.getClass().getName(), "start");
		// We have a single step but different StepExecution to separate  
		// out responsibilities.
		String taskName = Util.fixEmptyAndTrim(getTask().getName());
		if (taskName == null) {
			throw new IllegalArgumentException(Messages.RTCBuildStep_no_task());
		}
		// Since serverURI, credentialsId and buildTool are optional, it is  
		// possible that even the global values are empty (not configured globally).
		switch(taskName) {
			case REQUEST_BUILD:
				return new RequestBuildStepExecution(this, context);
			case WAIT_FOR_BUILD:
				return new WaitForBuildStepExecution(this, context);
			case LIST_LOGS:
				return new ListFilesStepExecution(this, context,  
						RTCBuildConstants.LOG_TYPE);
			case LIST_ARTIFACTS:
				return new ListFilesStepExecution(this, context,  
						RTCBuildConstants.ARTIFACT_TYPE);
			case DOWNLOAD_LOG:
				return new DownloadFileStepExecution(this, context, 
						RTCBuildConstants.LOG_TYPE);
			case DOWNLOAD_ARTIFACT:
				return new DownloadFileStepExecution(this, context, 
						RTCBuildConstants.ARTIFACT_TYPE);
			case RETRIEVE_SNAPSHOT:
				return new RetrieveSnapshotStepExecution(this, context);
			default:
				throw new IllegalArgumentException(Messages.RTCBuildStep_invalid_task(getTask().getName()));
		}
	}
	
	@Extension
	public static final class DescriptorImpl extends StepDescriptor {
	
		public static int defaultMaxResults = Helper.DEFAULT_MAX_RESULTS;
		public static final int defaultWaitBuildTimeout = Helper.DEFAULT_WAIT_BUILD_TIMEOUT;
		public static final String defaultBuildStates = Helper.DEFAULT_BUILD_STATES_STR;
		public static final int defaultWaitBuildInterval = Helper.DEFAULT_WAIT_BUILD_INTERVAL;

		@Override
		public String getFunctionName() {
			return RTC_BUILD_STEP_NAME;
		}
		
		@Override
		public String getDisplayName() {
			return Messages.RTCBuildStep_display_message();
		}
		
		@Override
		public Set<Class<?>> getRequiredContext() {
			Set<Class<?>> set = new HashSet<>();
			set.add(Run.class);
			set.add(TaskListener.class);
			set.add(Computer.class);
			set.add(FilePath.class);
			return Collections.unmodifiableSet(set);
		}
		
		/**
		 * Provides a list box of the defined build tools to pick from. Also includes
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
		
		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?, ?> project, 
									@QueryParameter String serverURI) {
			// Need to adopt changes for user credentials enhancement.
			return new StandardListBoxModel()
			.withEmptySelection()
			.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, 
							project, ACL.SYSTEM, URIRequirementBuilder.fromUri(serverURI).build()));
		}
		
		public String getGlobalCredentialsId() {
			RTCScm.DescriptorImpl descriptor = (com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl)
					Jenkins.getInstance().getDescriptor(RTCScm.class);
			return descriptor.getGlobalCredentialsId();
		}
		
		public String getGlobalServerURI() {
			RTCScm.DescriptorImpl descriptor = (com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl)
					Jenkins.getInstance().getDescriptor(RTCScm.class);
			return descriptor.getGlobalServerURI();
		}

		public int getGlobalTimeout() {
			RTCScm.DescriptorImpl descriptor = (com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl)
					Jenkins.getInstance().getDescriptor(RTCScm.class);
			return descriptor.getGlobalTimeout();
		}
		
		public String getGlobalBuildTool() {
			RTCScm.DescriptorImpl descriptor = (com.ibm.team.build.internal.hjplugin.RTCScm.DescriptorImpl)
					Jenkins.getInstance().getDescriptor(RTCScm.class);
			return descriptor.getGlobalBuildTool();
		}

		// validation for build states, and build wait timeout (cannot be negative)
		
		/**
		 * Called from the forms to validate the timeout value.
		 * @param timeout The timeout value.
		 * @return Whether the timeout is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckTimeout(@QueryParameter String timeout) {
			return RTCLoginInfo.validateTimeout(timeout);
		}
		
		/**
		 * Called from the forms to validate the timeout value.
		 * @param timeout The timeout value.
		 * @return Whether the timeout is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckBuildStates(@QueryParameter String buildStates) {
			LOGGER.entering(this.getClass().getName(), "doCheckBuildStates");
			
			buildStates = Util.fixEmptyAndTrim(buildStates);
			if (buildStates == null) {
				return FormValidation.error(Messages.RTCBuildStep_buildStates_required());
			}

			if (Helper.isAParameter(buildStates)) {
				return FormValidation.ok();
			}

			Tuple<String[], String[]> buildStatesWithDuplicates = 
							Helper.extractBuildStatesWithDuplicates(buildStates);
			String [] buildStatesArr = buildStatesWithDuplicates.getFirst();
			String [] invalidStates = Helper.getInvalidStates(buildStatesArr);
			if (invalidStates.length > 0) {
				return FormValidation.error(Messages.RTCBuildStep_invalid_build_states_1(
									String.join(",", invalidStates), 
									String.join(",", Helper.getAllBuildStates())));
			} else {
				// Check for duplicate states
				String [] duplicateBuildStatesArr = buildStatesWithDuplicates.getSecond();
				if (duplicateBuildStatesArr.length > 0) {
					return FormValidation.warning(Messages.RTCBuildStep_build_states_repeated(
								String.join(",", duplicateBuildStatesArr)));
				} else {
					return FormValidation.ok();
				}
			}
		}
		
		public FormValidation doCheckWaitBuildInterval(@QueryParameter String waitBuildInterval) {
			LOGGER.entering(this.getClass().getName(), "doCheckWaitBuildInterval");
			waitBuildInterval = Util.fixEmptyAndTrim(waitBuildInterval);
			if (StringUtils.isEmpty(waitBuildInterval)) {
				LOGGER.finer("Wait build timeout value missing"); //$NON-NLS-1$
				return FormValidation.error(Messages.RTCBuildStep_waitBuildInterval_required());
			}
			
			if (Helper.isAParameter(waitBuildInterval)) {
				return FormValidation.ok();
			}

			try {
				int waitBuildIntervalInt = Integer.parseInt(waitBuildInterval);
				FormValidation result = FormValidation.ok();
				if (waitBuildIntervalInt < 0) {
					result = FormValidation.validatePositiveInteger(waitBuildInterval);
					if (FormValidation.Kind.ERROR == result.kind) { 
						return FormValidation.error(
								Messages.RTCBuildStep_invalid_waitBuildInterval(waitBuildInterval));
					}
				} 
				return result;
			} catch (NumberFormatException exp) {
				return FormValidation.error(
						Messages.RTCBuildStep_invalid_waitBuildInterval(waitBuildInterval));
			}
		}
		/**
		 * Called from the forms to validate the build wait timeout value.
		 * @param waitBuildTimeout The wait build timeout value.
		 * @return Whether the timeout is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckWaitBuildTimeout(@QueryParameter String waitBuildTimeout) {
			LOGGER.entering(this.getClass().getName(), "doCheckWaitBuildTimeout");

			waitBuildTimeout = Util.fixEmptyAndTrim(waitBuildTimeout);
			if (StringUtils.isEmpty(waitBuildTimeout)) {
				LOGGER.finer("Wait build timeout value missing"); //$NON-NLS-1$
				return FormValidation.error(Messages.RTCBuildStep_waitBuildTimeout_required());
			}
			
			if (Helper.isAParameter(waitBuildTimeout)) {
				return FormValidation.ok();
			}

			try {
				int waitBuildTimeoutInt = Integer.parseInt(waitBuildTimeout);
				FormValidation result = FormValidation.ok();
				if (waitBuildTimeoutInt != -1) {
					result = FormValidation.validatePositiveInteger(waitBuildTimeout);
					if (FormValidation.Kind.ERROR == result.kind) { 
						return FormValidation.error(
								Messages.RTCBuildStep_invalid_waitBuildTimeout(waitBuildTimeout));
					}
				} 
				return result;
			} catch (NumberFormatException exp) {
				return FormValidation.error(
						Messages.RTCBuildStep_invalid_waitBuildTimeout(waitBuildTimeout));
			}
		}
		
		/**
		 * Validate whether maxResults parameter is a valid positive integer 
		 * less than {@link Helper#MAX_RESULTS_UPPER_LIMIT}
		 * 
		 * @param maxResults    - The value of maxResults param from the form
		 * @return  {@link FormValidation}
		 */
		public FormValidation doCheckMaxResults(@QueryParameter String maxResults) {
			LOGGER.entering(this.getClass().getName(), "doCheckMaxResults");
			
			if (Helper.isAParameter(maxResults)) {
				return FormValidation.ok();
			}

			maxResults = Util.fixEmptyAndTrim(maxResults);
			return ValidationHelper.validateMaxResultsParm(maxResults);
		}

		/**
		 * Validate whether fileNameOrPattern parameter is valid. 
		 * 
		 * @param fileNameOrPattern   The value of fileNameOrPattern param
		 * @return {@link FormValidation}
		 * 
		 */
		public FormValidation doCheckFileNameOrPattern(@QueryParameter String fileNameOrPattern) {
			LOGGER.entering(this.getClass().getName(), "doCheckFileNameOrPattern");

			fileNameOrPattern = Util.fixEmptyAndTrim(fileNameOrPattern);
			if (fileNameOrPattern == null) {
				return FormValidation.ok();
			}

			// If fileNameOrPattern is a property, skip validation
			if (Helper.isAParameter(fileNameOrPattern)) {
				return FormValidation.ok();
			}
			
			return ValidationHelper.validatePattern(fileNameOrPattern);
		}

		/**
		 * Validate destinationFIleName parameter is not empty
		 * 
		 * @param destinationFileName     The value of the destinationFileName param
		 * @return {@link FormValidation}
		 */
		public FormValidation doCheckDestinationFileName(@QueryParameter String destinationFileName) {
			LOGGER.entering(this.getClass().getName(), "doCheckDestinationFileName");
			
			destinationFileName = Util.fixEmptyAndTrim(destinationFileName);
			if (destinationFileName == null) {
				return FormValidation.ok();
			}
			// If destinationFileName is a property, skip validation
			if (Helper.isAParameter(destinationFileName)) {
				return FormValidation.ok();
			}
			 
			return ValidationHelper.validateFileName(destinationFileName);
		}
	}
}
