/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.ParametersAction;
import hudson.model.Queue.Task;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.remoting.Channel;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.extensions.RtcExtensionProvider;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;
import com.ibm.team.build.internal.hjplugin.util.ValidationResult;

@ExportedBean(defaultVisibility=999)
public class RTCScm extends SCM {

    private static final Logger LOGGER = Logger.getLogger(RTCScm.class.getName());

	private static final String DEBUG_PROPERTY = "com.ibm.team.build.debug"; //$NON-NLS-1$
	
	private static final String CALL_CONNECTOR_TIMEOUT_PROPERTY = "com.ibm.team.build.callConnector.timeout"; //$NON-NLS-1$
	
	private static final String IGNORE_OUTGOING_FROM_BUILD_WS_WHILE_POLLING = "com.ibm.team.build.ignoreOutgoingFromBuildWorkspaceWhilePolling"; //$NON-NLS-1$
	
	private static final String DEPRECATED_CREDENTIAL_EDIT_ALLOWED = "com.ibm.team.build.credential.edit"; //$NON-NLS-1$

	// persisted fields for SCM
    private boolean overrideGlobal;
    
    // Global setting that have been overridden by the job (if overrideGlobal is true)
    private String buildTool;
	private String serverURI;
	private int timeout;
	private String userId;
	private Secret password;
	private String passwordFile;
	private String credentialsId;

	// Job configuration settings
	public static final String BUILD_WORKSPACE_TYPE = "buildWorkspace"; //$NON-NLS-1$
	public static final String BUILD_DEFINITION_TYPE = "buildDefinition"; //$NON-NLS-1$

	public static final int DEFAULT_SERVER_TIMEOUT = DescriptorImpl.DEFAULT_SERVER_TIMEOUT;

	private BuildType buildType;
	
	private String buildTypeStr;
	private String buildWorkspace;
	private String buildDefinition;

	// Don't persist the browser because it references the server url that can be changing in the
	// global config.
	@Deprecated 
	private transient RTCRepositoryBrowser browser;

	// Use rest or whatever services instead of the build toolkit.
	// Available in RTC 5.0 or 4.0.6 + retro-fitted changes
	private boolean avoidUsingToolkit;
	
	/**
	 * Structure that represents the radio button selection for build workspace/definition
	 * choice in config.jelly (job configuration) 
	 */
	public static class BuildType {
		public String value;
		public String buildDefinition;
		public String buildWorkspace;
		
		@DataBoundConstructor
		public BuildType(String value, String buildDefinition, String buildWorkspace) {
			this.value = value;
			this.buildDefinition = buildDefinition;
			this.buildWorkspace = buildWorkspace;
		}
	}
	
	// Descriptor class - contains the global configuration settings
	@Extension
	public static class DescriptorImpl extends SCMDescriptor<RTCScm> {
		
		private static final String DEFAULT_SERVER_URI = "https://localhost:9443/ccm"; //$NON-NLS-1$

		private static final int DEFAULT_SERVER_TIMEOUT = 480;
		
		private static transient boolean deprecatedCredentialEditAllowed = Boolean.getBoolean(DEPRECATED_CREDENTIAL_EDIT_ALLOWED);
		
		// persisted fields

		private String globalBuildTool;
		private String globalServerURI;
		private int globalTimeout;
		private String globalCredentialsId;

		// explicit UserId & password or password file
		private String globalUserId;
		private Secret globalPassword;
		private String globalPasswordFile;
		
		// Use rest or whatever services instead of the build toolkit.
		// Available in RTC 5.0 or 4.0.6 + retro-fitted changes
		private boolean globalAvoidUsingToolkit;
		
		public DescriptorImpl() {
			super(RTCScm.class, RTCRepositoryBrowser.class);
			load();
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
 			RTCScm scm = (RTCScm) super.newInstance(req, formData);
			
 			new RTCRepositoryBrowser(scm.getServerURI());
			return scm;
		}

		@Override
		public String getDisplayName() {
			return Messages.RTCScm_RTC_display_name();
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			LOGGER.finest("DescriptorImpl.configure: Begin");
			globalBuildTool = Util.fixEmptyAndTrim(json.optString("buildTool")); //$NON-NLS-1$
			globalServerURI = Util.fixEmptyAndTrim(json.optString("serverURI")); //$NON-NLS-1$
			globalUserId = Util.fixEmptyAndTrim(json.optString("userId")); //$NON-NLS-1$
			String timeout = json.optString("timeout"); //$NON-NLS-1$
			globalAvoidUsingToolkit = json.containsKey("avoidUsingToolkit"); //$NON-NLS-1$
			
			try {
				globalTimeout = timeout == null ? 0 : Integer.parseInt(timeout);
			} catch (NumberFormatException e) {
				globalTimeout = 0;
			}
			String password = json.optString("password"); //$NON-NLS-1$
			globalPassword = password == null || password.length() == 0 ? null : Secret.fromString(password);
			globalPasswordFile = Util.fixEmptyAndTrim(json.optString("passwordFile")); //$NON-NLS-1$
			globalCredentialsId = Util.fixEmptyAndTrim(json.optString("credentialsId")); //$NON-NLS-1$
			if (globalCredentialsId != "") {
				// They are saving with credentials. Remove the vestiges of the old authentication method
				globalPassword = null;
				globalPasswordFile = null;
				globalUserId = null;
			}

			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("configure : " + //$NON-NLS-1$
						"\" globalServerURI=\"" + globalServerURI + //$NON-NLS-1$
						"\" globalUserid=\"" + globalUserId + //$NON-NLS-1$
						"\" globalTimeout=\"" + globalTimeout + //$NON-NLS-1$
						"\" globalPassword " + (globalPassword == null ? "is not supplied" //$NON-NLS-1$ //$NON-NLS-2$
								: "(" + Secret.toString(globalPassword).length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$
						" globalPasswordFile=\"" + globalPasswordFile + //$NON-NLS-1$ //$NON-NLS-2$
						"\" globalCredentialsId=\"" + globalCredentialsId + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			save();
		    return true;
		}

		public String getGlobalBuildTool() {
			return globalBuildTool;
		}

		/**
		 * Determine if they are using the deprecated authentication way
		 * @return <code>true</code> if using user id & password or password file
		 * <code>false</code> if using credentials or no password authentication setup.
		 */
		public boolean usingDeprecatedPassword() {
			if (deprecatedCredentialEditAllowed()) {
				return true;
			}
			String globalCredId = getGlobalCredentialsId();
			String userIdToUse = getGlobalUserId();
			String passwordToUse = getGlobalPassword();
			String passwordFileToUse = getGlobalPasswordFile();
			if (globalCredId == null || globalCredId.isEmpty()) {
				// consider them to be using user id & old password way if supplied
				// not using strict validation since we would still work if too much info supplied.
				if (userIdToUse != null	&& !userIdToUse.isEmpty()
						&& ((passwordToUse != null && !passwordToUse.isEmpty())
							|| (passwordFileToUse != null && !passwordFileToUse.isEmpty()))) {
					return true;
				}
			}
			return false;
		}
		
		/**
		 * @return Whether the global password file should be shown in the UI
		 */
		public boolean showGlobalPasswordFile() {
			String passwordFileToUse = getGlobalPasswordFile();
			if (deprecatedCredentialEditAllowed() || (passwordFileToUse != null && !passwordFileToUse.isEmpty())) {
				return true;
			}
			return false;
		}
		
		/**
		 * @return Whether the global password should be shown in the UI
		 */
		public boolean showGlobalPassword() {
			String passwordToUse = getGlobalPassword();
			if (deprecatedCredentialEditAllowed() || (passwordToUse != null && !passwordToUse.isEmpty())) {
				return true;
			}
			return false;
		}
		public boolean deprecatedCredentialEditAllowed() {
			return deprecatedCredentialEditAllowed;
		}

		public String getGlobalCredentialsId() {
			return globalCredentialsId;
		}

		public String getGlobalPassword() {
			String result;
			if (globalPassword == null) {
				result = null;
			} else {
				result = globalPassword.getPlainText();
			}
			return result;
		}
		
		public String getGlobalPasswordFile() {
			return globalPasswordFile;
		}
		
		public String getGlobalServerURI() {
			if (globalServerURI == null || globalServerURI.length() == 0) {
				return DEFAULT_SERVER_URI;
			}
			return globalServerURI;
		}
		
		public String getGlobalUserId() {
			return globalUserId;
		}
		
	    public int getGlobalTimeout() {
	    	if (globalTimeout == 0) {
	    		return DEFAULT_SERVER_TIMEOUT;
	    	}
    		return globalTimeout;
	    }
	    
	    public boolean getGlobalAvoidUsingToolkit() {
	    	return globalAvoidUsingToolkit;
	    }
		
	    /**
	     * Get the path on the Master to the build toolkit for the given Build tool.
	     * @param buildTool The id of the build tool
		 * @param listener Listener to log any problems encountered.
		 * @return Path to the Build Toolkit directory
	     * @throws IOException
	     * @throws InterruptedException
	     */
		public String getMasterBuildToolkit(String buildTool, TaskListener listener) throws IOException, InterruptedException {
			return getBuildToolkit(buildTool, Hudson.getInstance(), listener);
		}
		
		@Override
		public boolean isApplicable(Job project) {
			return true;
		}
		
		/**
		 * For a given build tool on a given node, resolve what directory is expected to contains the build toolkit.
		 * Jenkins provides tool installation abilities as well as a way of describing where a tool is located on 
		 * as Slave. We will delegate the path resolution to Jenkins.
		 * @param buildTool The build tool that we want the path for
		 * @param node The node (Master or a particular slave) the build toolkit will used on. 
		 * @param listener Listener to log any problems encountered.
		 * @return Path to the Build Toolkit directory
		 * @throws IOException
		 * @throws InterruptedException
		 */
		private String getBuildToolkit(String buildTool, Node node, TaskListener listener) throws IOException, InterruptedException {
	        RTCBuildToolInstallation[] installations = RTCBuildToolInstallation.allInstallations();
	        for (RTCBuildToolInstallation buildToolIntallation : installations) {
	        	if (buildToolIntallation.getName().equals(buildTool)) {
	        		return buildToolIntallation.forNode(node, listener).getHome();
	        	}
	        }
			return null;
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

		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?, ?> project, @QueryParameter String serverURI) {
			return new StandardListBoxModel()
			.withEmptySelection()
			.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM, URIRequirementBuilder.fromUri(serverURI).build()));

			// TODO look into us being able to support certificates
//			return new StandardListBoxModel()
//			.withEmptySelection()
//			.withMatching(CredentialsMatchers.anyOf(
//					CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
//					CredentialsMatchers.instanceOf(StandardCertificateCredentials.class)),
//					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM, URIRequirementBuilder.fromUri(url).build()));
		}

		/**
		 * Called from the forms to validate the timeout value.
		 * @param timeout The timeout value.
		 * @return Whether the timeout is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckTimeout(@QueryParameter String timeout) {
			return RTCLoginInfo.validateTimeout(timeout);
		}

		/**
		 * Called from the forms to validate that the build tool is selected
		 * and that the underlying build tool points to a valid build toolkit.
		 * @param buildTool The name of the build tool to validate
		 * @return Whether the build tool is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckBuildTool(
				@QueryParameter("buildTool") String buildTool) {
			LOGGER.finest("DescriptorImpl.doCheckBuildTool: Begin");
			if (Util.fixEmptyAndTrim(buildTool) == null) {
				return FormValidation.error(Messages.RTCScm_build_tool_needed_for_job());
			}

			// validate the build toolkit path
			String buildToolkitPath;
			try {
				buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
			} catch (Exception e) {
				return FormValidation.error(e, Messages.RTCScm_no_build_toolkit(e.getMessage()));
			}
			FormValidation buildToolkitCheck = RTCBuildToolInstallation.validateBuildToolkit(false, buildToolkitPath);

			if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
				return buildToolkitCheck;
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckCredentialsId(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			LOGGER.finest("DescriptorImpl.doCheckCredentialsId: Begin");
			return RTCLoginInfo.validateCredentials(credentialsId, userId,
					passwordFile, password);
		}
		
		public FormValidation doCheckUserId(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			return RTCLoginInfo.validateUserId(credentialsId, userId,
					passwordFile, password);
		}

		public FormValidation doCheckPassword(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			return RTCLoginInfo.validatePassword(credentialsId, userId,
					passwordFile, password);
		}

		public FormValidation doCheckPasswordFile(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			return RTCLoginInfo.validatePasswordFile(credentialsId, userId,
					passwordFile, password);
		}

		/**
		 * For the default global configuration check that the connection works (with whatever
		 * access preferred). Main idea is check server, user credentials are valid for logging in.
		 * @param buildTool The build tool selected to be used in builds
		 * @param serverURI The RTC server uri
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied.
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied.
		 * @param credId Credential id that will identify the user id and password to use
		 * @param timeout The timeout period for the connection
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection.
	     * @return The result of the validation (will be ok if valid)
		 */
		public FormValidation doCheckGlobalConnection(
				@QueryParameter("buildTool") final String buildTool,
				@QueryParameter("serverURI") final String serverURI,
				@QueryParameter("userId") final String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("timeout") final String timeout,
				@QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit) {
			LOGGER.finest("DescriptorImpl.doCheckGlobalConnection: Begin");
			boolean avoidUsingToolkit = Boolean.parseBoolean(Util.fixNull(avoidUsingBuildToolkit));
			ValidationResult result = validateConnectInfo(true, buildTool, serverURI, userId, password, passwordFile, credId, timeout, avoidUsingToolkit);

			// validate the connection
			if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return result.validationResult;
			} else {
				FormValidation connectCheck = checkConnect(result.buildToolkitPath, avoidUsingToolkit, result.loginInfo);
				return Helper.mergeValidationResults(result.validationResult, connectCheck);
			}
		}

		/**
		 * For the job configuration check that the connection works (with whatever
		 * access preferred). Use either the information supplied or the previously stored
		 * global information dependin on whether to override the global information.
		 * Main idea is check server, user credentials are valid for logging in.
		 * @param override Whether to use the global settings or not
		 * @param buildTool The build tool selected to be used in builds
		 * @param serverURI The RTC server uri
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied.
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied.
		 * @param credId Credential id that will identify the user id and password to use
		 * @param timeout The timeout period for the connection
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection.
	     * @return The result of the validation (will be ok if valid)
		 */
		public FormValidation doCheckJobConnection(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit) {
			LOGGER.finest("DescriptorImpl.doCheckJobConnection: Begin");
			boolean overrideGlobal = Boolean.parseBoolean(Util.fixNull(override));
			boolean avoidUsingToolkit;
			if (!overrideGlobal) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(Util.fixNull(avoidUsingBuildToolkit));
			}

			ValidationResult result = validateConnectInfo(!overrideGlobal, buildTool, serverURI, userId, password, passwordFile, credId, timeout, avoidUsingToolkit);

			// validate the connection
			if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return result.validationResult;
			} else {
				FormValidation connectCheck = checkConnect(result.buildToolkitPath, avoidUsingToolkit, result.loginInfo);
				return Helper.mergeValidationResults(result.validationResult, connectCheck);
			}
		}

		/**
		 * Check that we can connect to the RTC server. If avoiding the toolkit, we
		 * will not validate it since it will not be used. 
		 * @param checkingGlobalSettings Whether the settings are global or job. Affects
		 * the error message given.
		 * @param buildTool The build tool configured. Only used if avoidUsingBuildToolkit is off
		 * @param serverURI The RTC server to connect to
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied.
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied.
		 * @param credId Credential id that will identify the user id and password to use
		 * @param timeout The timeout period for the connection
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection.
	     * @return The result of the validation (will be ok if valid)
		 */
		private ValidationResult validateConnectInfo(
				final boolean checkingGlobalSettings,
				final String buildTool,
				final String serverURI,
				final String userId,
				final String password,
				String passwordFile,
				String credId,
				final String timeout,
				final boolean avoidUsingToolkit) {
			LOGGER.finest("DescriptorImpl.validateConnectInfo: Begin");
			passwordFile = Util.fixEmptyAndTrim(passwordFile);
			credId = Util.fixEmptyAndTrim(credId);

			ValidationResult result = new ValidationResult();

			// in the global case the build tool doesn't need to really be supplied. It could be supplied
			// by the Job. However, if they want to check the connection, they would need to have avoid using toolkit
			// enabled and not use a password file.
			boolean warnOnly = avoidUsingToolkit && (credId != null || passwordFile == null);
			

			// we need the build tool to validate
			if (Util.fixEmptyAndTrim(buildTool) == null) {
				String errorMessage;
				if (warnOnly) {
					if (checkingGlobalSettings) {
						errorMessage = Messages.RTCScm_global_build_tool_needed_for_job();
					} else {
						errorMessage = Messages.RTCScm_build_tool_needed_for_job();
					}
					result.validationResult = FormValidation.warning(errorMessage);
				} else {
					if (checkingGlobalSettings) {
						errorMessage = Messages.RTCScm_missing_global_build_tool();
					} else {
						errorMessage = Messages.RTCScm_missing_build_tool();
					}
					result.validationResult = FormValidation.error(errorMessage);
					return result;
				}
			} else {

				// validate the build toolkit path
				try {
					result.buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
				} catch (Exception e) {
					String errorMessage;
					if (checkingGlobalSettings) {
						errorMessage = Messages.RTCScm_no_global_build_toolkit3(e.getMessage());
					} else {
						errorMessage = Messages.RTCScm_no_build_toolkit(e.getMessage());
					}
					if (warnOnly) {
						result.validationResult = FormValidation.warning(errorMessage);
					} else {
						result.validationResult = FormValidation.error(errorMessage);
					}
					return result;
				}
			
				result.validationResult = RTCBuildToolInstallation.validateBuildToolkit(warnOnly, result.buildToolkitPath);
				if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
					return result;
				}
			}
			
			// validate the authentication information
	    	FormValidation basicValidate = RTCLoginInfo.basicValidate(credId, userId, passwordFile, password, timeout);
	    	if (basicValidate.kind.equals(FormValidation.Kind.ERROR)) {
	    		result.validationResult = Helper.mergeValidationResults(result.validationResult, basicValidate);
	    		return result;
	    	}
			
			try {
				result.loginInfo = new RTCLoginInfo(null, result.buildToolkitPath,
						serverURI, userId, password, passwordFile, credId,
						Integer.parseInt(timeout));
			} catch (InvalidCredentialsException e) {
				result.validationResult = FormValidation.error(e, e.getMessage());
			}
			return result;
		}
		
		/**
		 * Check the connection to the RTC server. This will validate that we can connect to the server with the
		 * credentials defined. Connection may be through the toolkit or Rest services. This means a version
		 * compatibility check is also done.
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingTookit Whether to try to avoid using the build toolkit
		 * @param loginInfo The credentials for logging into the server.
		 * @return Whether the connection is valid or not. Never <code>null</code>
		 */
		private FormValidation checkConnect(String buildToolkitPath, boolean avoidUsingTookit, RTCLoginInfo loginInfo) {
			LOGGER.finest("DescriptorImpl.checkConnect: Begin");
	    	try {
				String errorMessage = RTCFacadeFacade.testConnection(buildToolkitPath,
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), avoidUsingTookit);
				if (errorMessage != null && errorMessage.length() != 0) {
	    			return FormValidation.error(errorMessage);
				}
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport == null) {
	    			eToReport = e;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkConnect attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkConnect invocation failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkConnect attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkConnect failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, Messages.RTCScm_failed_to_connect(e.getMessage()));
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_connect_success());
	    }

		/**
		 * Validate the Build workspace is valid. Called by the forms.
		 * @param project The Job that is going to be run
		 * @param override Whether to override the global connection settings
		 * @param buildTool The build tool selected to be used in builds (Job setting)
		 * @param serverURI The RTC server uri (Job setting)
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id (Job setting)
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied. (Job setting)
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied. (Job setting)
		 * @param credId Credential id that will identify the user id and password to use (Job setting)
		 * @param timeout The timeout period for the connection (Job setting)
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection. (Job setting)
		 * @param buildWorkspace The build workspace to validate
		 * @return Whether the build workspace is valid or not. Never <code>null</code>
		 */
		public FormValidation doValidateBuildWorkspace(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit,
				@QueryParameter("buildWorkspace") String buildWorkspace) {
			LOGGER.finest("DescriptorImpl.doValidateBuildWorkspace: Begin");
			boolean overrideGlobalSettings = Boolean.parseBoolean(override);
			boolean avoidUsingToolkit;
			if (!overrideGlobalSettings) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(avoidUsingBuildToolkit);
			}
			// validate the info for connecting to the server (including toolkit & auth info).
			ValidationResult connectInfoCheck = validateConnectInfo(
					!overrideGlobalSettings, buildTool, serverURI, userId,
					password, passwordFile, credId, timeout, avoidUsingToolkit);
			if (connectInfoCheck.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			} else {
				// connection info is good now validate the build workspace
				FormValidation buildWorkspaceCheck = checkBuildWorkspace(
						connectInfoCheck.buildToolkitPath, avoidUsingToolkit,
						connectInfoCheck.loginInfo, buildWorkspace);
				return Helper.mergeValidationResults(connectInfoCheck.validationResult, buildWorkspaceCheck);
			}
		}

		/**
		 * Validate the Build definition is valid. Called by the forms.
		 * @param project The Job that is going to be run
		 * @param override Whether to override the global connection settings
		 * @param buildTool The build tool selected to be used in builds (Job setting)
		 * @param serverURI The RTC server uri (Job setting)
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id (Job setting)
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied. (Job setting)
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied. (Job setting)
		 * @param credId Credential id that will identify the user id and password to use (Job setting)
		 * @param timeout The timeout period for the connection (Job setting)
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection. (Job setting)
		 * @param buildDefinition The build definition to validate
		 * @return Whether the build definition is valid or not. Never <code>null</code>
		 */
		public FormValidation doValidateBuildDefinition(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("avoidUsingToolkit") final String avoidUsingBuildToolkit,
				@QueryParameter("buildDefinition") final String buildDefinition) {
			LOGGER.finest("DescriptorImpl.doValidateBuildDefinition: Begin");
	    	boolean overrideGlobalSettings = Boolean.parseBoolean(override);
			boolean avoidUsingToolkit;
			if (!overrideGlobalSettings) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(avoidUsingBuildToolkit);
			}
			// validate the info for connecting to the server (including toolkit & auth info).
			ValidationResult connectInfoCheck = validateConnectInfo(
					!overrideGlobalSettings, buildTool, serverURI, userId,
					password, passwordFile, credId, timeout, avoidUsingToolkit);
			if (connectInfoCheck.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			} else {
				// connection info is good now validate the build definition
				FormValidation buildDefinitionCheck = checkBuildDefinition(
						connectInfoCheck.buildToolkitPath, avoidUsingToolkit,
						connectInfoCheck.loginInfo, buildDefinition);
				return Helper.mergeValidationResults(connectInfoCheck.validationResult, buildDefinitionCheck);
			}
		}

		/** 
		 * Validate that the build workspace exists and there is just one.
		 * This is done in the next "layer" below using either the toolkit
		 * or the rest service.
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingToolkit Whether to avoid using the build toolkit
		 * @param loginInfo The login credentials 
		 * @param buildWorkspace The name of the workspace to validate
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkBuildWorkspace(String buildToolkitPath, boolean avoidUsingToolkit, RTCLoginInfo loginInfo, String buildWorkspace) {

			try {
	    		String errorMessage = RTCFacadeFacade.testBuildWorkspace(buildToolkitPath,
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
						avoidUsingToolkit, buildWorkspace);
				if (errorMessage != null && errorMessage.length() != 0) {
	    			return FormValidation.error(errorMessage);
				}
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport == null) {
	    			eToReport = e;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildWorkspace attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildWorkspace invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildWorkspace attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildWorkspace failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, e.getMessage());
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_build_workspace_success());
	    }

		/**
		 * Validate the build definition is a H/J definition and usable
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingToolkit Whether to avoid using the build toolkit
		 * @param loginInfo The login credentials 
		 * @param buildDefinition The id of the build definition to validate
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkBuildDefinition(String buildToolkitPath, boolean avoidUsingToolkit, RTCLoginInfo loginInfo, String buildDefinition) {

			try {
				String errorMessage = RTCFacadeFacade.testBuildDefinition(buildToolkitPath, loginInfo.getServerUri(),
						loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
						avoidUsingToolkit,
						buildDefinition);
				if (errorMessage != null && errorMessage.length() != 0) {
	    			return FormValidation.error(errorMessage);
				}
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport == null) {
	    			eToReport = e;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildDefinition attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() +  //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    	        "\" buildDefinition=\"" + buildDefinition +"\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildDefinition invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildDefinition attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() +  //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    	        "\" buildDefinition=\"" + buildDefinition + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildDefinition failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, e.getMessage());
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_build_definition_success());
	    }
	}
	
	/*
	 * Convenience constructor for instantiating RTCSCM with only a buildType and the rest of the parameters are set to defaults	 * 
	 */
	public RTCScm(BuildType buildType) {
		this(false, null, null, DEFAULT_SERVER_TIMEOUT, null, null, null, null, buildType, false);
		LOGGER.finest("RTCScm constructor 1: Begin");
	}
	
	@DataBoundConstructor
	public RTCScm(boolean overrideGlobal, String buildTool, String serverURI, int timeout, String userId, Secret password, String passwordFile,
			String credentialsId, BuildType buildType, boolean avoidUsingToolkit) {

		
		LOGGER.finest("RTCScm DataBound constructor: Begin");
		this.overrideGlobal = overrideGlobal;
		if (this.overrideGlobal) {
			this.buildTool = buildTool;
			this.serverURI = serverURI;
			this.timeout = timeout;
			this.credentialsId = credentialsId;
			if (this.credentialsId == null || credentialsId.isEmpty()) {
				this.userId = userId;
				this.password = password;
				this.passwordFile = passwordFile;
			}
			this.avoidUsingToolkit = avoidUsingToolkit;
		}
		this.buildType = buildType;
		if(buildType != null) {
			this.buildTypeStr = buildType.value;
			this.buildWorkspace = buildType.buildWorkspace;
			this.buildDefinition = buildType.buildDefinition;
		}
		
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("RTCScm constructed with " + //$NON-NLS-1$
					" overrideGlobal=\"" + this.overrideGlobal + //$NON-NLS-1$
					"\" buildTool=\"" + this.buildTool + //$NON-NLS-1$
					"\" serverURI=\"" + this.serverURI + //$NON-NLS-1$
					"\" timeout=\"" + this.timeout + //$NON-NLS-1$
					"\" userId=\"" + this.userId + //$NON-NLS-1$
					"\" password " + (this.password == null ? "is not supplied" : "(" + Secret.toString(this.password).length() +" characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					" passwordFile=\"" + this.passwordFile + //$NON-NLS-1$
					"\" credentialsId=\"" + this.credentialsId + //$NON-NLS-1$
					"\" buildType=\"" + this.buildTypeStr + //$NON-NLS-1$
					"\" buildWorkspace=\"" + this.buildWorkspace + //$NON-NLS-1$
					"\" buildDefinition=\"" + this.buildDefinition + "\""); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException,
			InterruptedException {
        LOGGER.finest("RTCScm.calcRevisionsFromBuild : Begin");
		// Our check for incoming changes uses the flow targets and does a real time compare
		// So for now we don't return a special revision state
		return SCMRevisionState.NONE;
	}
	
	/**
	 * Expecting buildResultUUID as non-null, match with buildDefinition and buildWorkspace
	 * @param loginInfo
	 * @param buildResultUUID
	 * @param buildDefinition
	 * @param buildWorkspace
	 * @return true if matches, false otherwise
	 */
	private boolean match(RTCLoginInfo loginInfo, String buildToolkit, String buildResultUUID, String buildDefinition, String buildWorkspace,
			boolean debug, TaskListener listener, Locale clientLocale) throws InterruptedException, AbortException {
		if (buildDefinition == null) {
			return false;
		}
		
		RTCFacadeWrapper facade;
		Map<String, String> details = null;
		try {
			facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
			
			// get buildDefinition and buildWorkspace for buildResultUUID...
			details = (Map<String, String>) facade.invoke("getBuildResultUUIDDetails", new Class[] { //$NON-NLS-1$
					String.class, // serverURI,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, // buildResultUUID,
					Object.class, // listener
					Locale.class // clientLocale
			}, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
					loginInfo.getTimeout(), buildResultUUID,
					listener, clientLocale);
		} catch (Exception e) {
			Throwable eToReport = e;
			if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
				eToReport = e.getCause();
			}
			if (eToReport instanceof InterruptedException) {
				listener.getLogger().println("Exception while getting buildResultUUID details");
				throw (InterruptedException) eToReport;
			}
//			if (debug) {
//				debug("Failed to get buildResultUUID details", eToReport); //$NON-NLS-1$
//			}
			// throw AbortException with this message
			throw new AbortException(e.getMessage());
		}
		
		String buildDefinitionId = details.get("buildDefinitionId");
		
		if (!buildDefinition.equals(buildDefinitionId)) {
			return false;
		}
/*		
		String buildWorkspaceName = details.get("buildWorkspaceName");
		if (buildWorkspace != null) {
			if (!buildWorkspace.equals(buildWorkspaceName)) {
				return false;
			}
		}
*/
		return true;
	}

	@Override
	public void checkout(Run<?, ?> build, Launcher arg1,
			FilePath workspacePath, TaskListener listener, File changeLogFile, SCMRevisionState baseline) throws IOException,
			InterruptedException {
		
        LOGGER.finest("RTCScm.checkout : Begin");
		listener.getLogger().println(Messages.RTCScm_checkout_started());

		String label = getLabel(build);
		String localBuildToolkit;
		String nodeBuildToolkit;
		String buildWorkspace = getBuildWorkspace();
		String buildDefinition = getBuildDefinition();
		String buildResultUUID = getBuildResultUUID(build, listener);

		Node node = workspacePath.toComputer().getNode();
		localBuildToolkit = getDescriptor().getMasterBuildToolkit(getBuildTool(), listener);
		// Get the build toolkit on the node where the checkout is happening.
		nodeBuildToolkit = getDescriptor().getBuildToolkit(getBuildTool(), node, listener);

		boolean debug = Boolean.parseBoolean(build.getEnvironment(listener).get(DEBUG_PROPERTY));

		RTCLoginInfo loginInfo;
		try {
			loginInfo = getLoginInfo(build.getParent(), localBuildToolkit);
		} catch (InvalidCredentialsException e1) {
			// TODO Auto-generated catch block
			throw new AbortException(e1.getMessage());
		}
		// if buildResultUUID is not null then we need to match...
		if (buildResultUUID != null) {
			if (!match(loginInfo, localBuildToolkit, buildResultUUID, buildDefinition, buildWorkspace, debug, listener, LocaleProvider.getLocale())) {
				buildResultUUID = null;
			}
		}

		String buildType = getBuildTypeStr();
		boolean useBuildDefinitionInBuild = BUILD_DEFINITION_TYPE.equals(buildType) || buildResultUUID != null;
		
		// Log in build result where the build was initiated from RTC
		// Because if initiated from RTC we will ignore build workspace if its a buildWorkspaceType
		if (buildResultUUID != null) {
			listener.getLogger().println(Messages.RTCScm_build_initiated_by());
		}
		
		RTCBuildResultAction buildResultAction;
		
		try {
			
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("checkout : " + build.getParent().getName() + " " + build.getDisplayName() + " " + node.getNodeName() + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" Load directory=\"" + workspacePath.getRemote() + "\"" +  //$NON-NLS-1$ //$NON-NLS-2$
						" Build tool=\"" + getBuildTool() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Local Build toolkit=\"" + localBuildToolkit + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Node Build toolkit=\"" + nodeBuildToolkit + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Server URI=\"" + loginInfo.getServerUri() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Userid=\"" + loginInfo.getUserId() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Build definition=\"" + buildDefinition + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Build workspace=\"" + buildWorkspace + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" useBuildDefinitionInBuild=\"" + useBuildDefinitionInBuild + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Baseline Set name=\"" + label + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if (workspacePath.isRemote()) {
				// Slaves do a lazy remote class loader. The slave's class loader will request things as needed
				// from the remote master. The class loader on the master is the one that knows about the hjplugin-rtc.jar
				// but not any of the toolkit jars. So trying to send the class (& its references) is problematic.
				// The hjplugin-rtc.jar won't be able to be found on the slave either from the regular class loader either
				// (since its on the master). So what we do is send our hjplugin-rtc.jar over to the slave to "prepopulate"
				// it in the class loader. This way we can create our special class loader referencing it and all the toolkit
				// jars.
				sendJarsToSlave(workspacePath);
			}

			RTCBuildResultSetupTask buildResultSetupTask = new RTCBuildResultSetupTask(build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
					nodeBuildToolkit,
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
					useBuildDefinitionInBuild, buildDefinition, buildResultUUID,
					label, listener, workspacePath.isRemote(), debug, LocaleProvider.getLocale());
			
			BuildResultInfo buildResultInfo = buildResultSetupTask.localInvocation();
			if (buildResultInfo == null) {
				buildResultInfo = workspacePath.act(buildResultSetupTask);
			}

			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("checkout : " + build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName() + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" initial buildResultUUID=\"" + buildResultUUID + "\"" +  //$NON-NLS-1$ //$NON-NLS-2$
						" current buildResultUUID=\"" + buildResultInfo.getBuildResultUUID() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" scheduled=\"" + buildResultInfo.isScheduled() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" personal build=\"" + buildResultInfo.isPersonalBuild() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" requested by=\"" + (buildResultInfo.getRequestor() == null ? "" : buildResultInfo.getRequestor()) + "\"" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" own build life cycle=\"" + buildResultInfo.ownLifeCycle() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if (buildResultUUID != null) {
				// if build started by RTC, record the cause of the build
				RTCBuildCause rtcBuildCause = new RTCBuildCause(buildResultInfo);
				CauseAction cause = build.getAction(CauseAction.class);
				if (cause == null) {
					cause = new CauseAction(rtcBuildCause);
					build.addAction(cause);
				} else {
					cause.getCauses().add(rtcBuildCause);
				}
			}
			
			// now that the build has been setup, start working with the actual build result
			// independent of whether RTC or the plugin created it
			buildResultUUID = buildResultInfo.getBuildResultUUID();
			
			// add the build result information (if any) to the build through an action
			// properties may later be added to this action
			buildResultAction = new RTCBuildResultAction(loginInfo.getServerUri(), buildResultUUID, buildResultInfo.ownLifeCycle(), this);
			build.addAction(buildResultAction);

			RemoteOutputStream changeLog = null;
			if (changeLogFile != null) {
				OutputStream changeLogStream = new FileOutputStream(changeLogFile);
				changeLog = new RemoteOutputStream(changeLogStream);
			}
			
			//get the extension provider
			RtcExtensionProvider extProvider = RtcExtensionProvider.getExtensionProvider(build, listener);
			
			String strCallConnectorTimeout = build.getEnvironment(listener).get(CALL_CONNECTOR_TIMEOUT_PROPERTY);
			if ((strCallConnectorTimeout == null) || !strCallConnectorTimeout.matches("\\d+")) {
				strCallConnectorTimeout = "";
			}

			RTCAcceptTask acceptTask = new RTCAcceptTask(
					build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
					nodeBuildToolkit, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), buildResultUUID,
					buildWorkspace,
					label,
					listener, changeLog,
					workspacePath.isRemote(), debug, LocaleProvider.getLocale(), strCallConnectorTimeout);

			// publish in the build result links to the project and the build
			if (buildResultUUID != null) {
				String rootUrl = Hudson.getInstance().getRootUrl();
				if (rootUrl != null) {
					rootUrl = Util.encode(rootUrl);
				}
				String projectUrl = build.getParent().getUrl();
				if (projectUrl != null) {
					projectUrl = Util.encode(projectUrl);
				}
				String buildUrl = build.getUrl();
				if (buildUrl != null) {
					buildUrl = Util.encode(buildUrl);
				}
				acceptTask.setLinkURLs(rootUrl,  projectUrl, buildUrl);
			}
			
			Map<String, Object> acceptResult = workspacePath.act(acceptTask);
			Map<String, String> buildProperties = (Map<String, String>)acceptResult.get("buildProperties");
			buildResultAction.addBuildProperties(buildProperties);
			String parentActivityId = (String)acceptResult.get("parentActivityId");
			String connectorId = (String)acceptResult.get("connectorId");
			
			RTCLoadTask loadTask = new RTCLoadTask(
					build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
					nodeBuildToolkit, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), 
					buildResultUUID, buildWorkspace,	label, listener,
					workspacePath.isRemote(), debug, LocaleProvider.getLocale(), parentActivityId, connectorId, extProvider);
			if (buildResultUUID != null) {
				String rootUrl = Hudson.getInstance().getRootUrl();
				if (rootUrl != null) {
					rootUrl = Util.encode(rootUrl);
				}
				String projectUrl = build.getParent().getUrl();
				if (projectUrl != null) {
					projectUrl = Util.encode(projectUrl);
				}
				String buildUrl = build.getUrl();
				if (buildUrl != null) {
					buildUrl = Util.encode(buildUrl);
				}
				loadTask.setLinkURLs(rootUrl,  projectUrl, buildUrl);
			}
			workspacePath.act(loadTask);

    	} catch (Exception e) {
    		Throwable eToReport = e;
    		if (eToReport instanceof InvocationTargetException) {
    			if (e.getCause() != null) {
    				eToReport = e.getCause();
    			}
    		}
    		if (eToReport instanceof InterruptedException) {
        		LOGGER.log(Level.FINER, "build interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
    			throw (InterruptedException) eToReport;
    		}
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checkout_failure3(eToReport.getMessage()));
    		if (RTCScm.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		LOGGER.log(Level.FINER, "Create build result failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$

    		// if we can't check out then we can't build it
    		throw new AbortException(Messages.RTCScm_checkout_failure4(e.getMessage()));
    	}
		finally {
			LOGGER.finer("RTCScm.checkout : End");
		}
	}

	private void sendJarsToSlave(FilePath workspacePath)
			throws MalformedURLException, IOException, InterruptedException {
		LOGGER.finest("RTCScm.sendJarsToSlave : Begin");
		VirtualChannel channel = workspacePath.getChannel();
		URL facadeJarURL = RTCFacadeFactory.getFacadeJarURL(null);
		if (channel instanceof Channel && facadeJarURL != null) {
			// try to find our jar
			Class<?> originalClass = RTCScm.class;
			ClassLoader originalClassLoader = (ClassLoader) originalClass.getClassLoader();
			URL [] jars = new URL[] {facadeJarURL};
			boolean result = ((Channel) channel).preloadJar(originalClassLoader, jars);
			LOGGER.finer("Prefetch result for sending jars is " + result);  //$NON-NLS-1$ //$NON-NLS-2$
		}
		LOGGER.finest("RTCScm.sendJarsToSlave : End");
	}

	private String getLabel(Run<?, ?> build) {
		// TODO if we have a build definition & build result id we should probably
		// follow a similar algorithm to RTC?
		// In the simple plugin case, generate the name from the project and the build
		return Messages.RTCScm_build_label(build.getNumber());
	}

	@Override
	public boolean supportsPolling() {
		LOGGER.finest("RTCScm.supportsPolling : Begin");
		return true;
	}

	@Override
	public boolean requiresWorkspaceForPolling() {
		LOGGER.finest("RTCScm.requiresWorkspaceForPolling : Begin");
		return false;
	}

	@Override
	public PollingResult compareRemoteRevisionWith(
			Job<?, ?> project, Launcher launcher, FilePath workspacePath,
			TaskListener listener, SCMRevisionState revisionState) throws IOException,
			InterruptedException {
		// if #requiresWorkspaceForPolling is false, expect that launcher and workspacePath are null
		LOGGER.finest("RTCScm.compareRemoteRevisionWith : Begin");

		listener.getLogger().println(Messages.RTCScm_checking_for_changes());

		// check to see if there are incoming changes
    	try {
    		boolean bAvoidUsingToolkit = getAvoidUsingToolkit();
    		String masterToolkit = getDescriptor().getMasterBuildToolkit(getBuildTool(), listener);
    		RTCLoginInfo loginInfo = getLoginInfo(project, masterToolkit);
    		boolean useBuildDefinitionInBuild = BUILD_DEFINITION_TYPE.equals(getBuildTypeStr());
    		if (useBuildDefinitionInBuild && bAvoidUsingToolkit) {
    			//if toolkit is not to be used, and
	    		//if the build is in queue then their are changes, avoid a recheck and resetting the Quiet period
	    		if(isInQueue(project)) {
	    			if (LOGGER.isLoggable(Level.FINER)) {
	    				LOGGER.finer("The build request for the project " + project.getName() + //$NON-NLS-1$ //$NON-NLS-2$
	    						" is already in queue, return polling result as NO_CHANGES to avoid resetting the quiet time"); //$NON-NLS-1$
	    			}
	    			return new PollingResult(revisionState, new RTCRevisionState(0), Change.NONE); 
	    		}

	    		Boolean changesIncoming = RTCFacadeFacade.incomingChangesUsingBuildDefinitionWithREST(masterToolkit,
	    				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(),
						getBuildDefinition(),
						getBuildWorkspace(),
						listener);
	
	    		if (changesIncoming.equals(Boolean.TRUE)) {
	    			listener.getLogger().println(Messages.RTCScm_changes_found());
	    			// arbitrarily specify non-zero revision hash in current revision state
	    			return new PollingResult(revisionState, new RTCRevisionState(1), Change.SIGNIFICANT);
	    		} else {
	    			listener.getLogger().println(Messages.RTCScm_no_changes_found());
	    			return new PollingResult(revisionState, new RTCRevisionState(0), Change.NONE);
	    		}
    		} else {
    			String strIgnoreOutgoingFromBuildWorkspace = System.getProperty(IGNORE_OUTGOING_FROM_BUILD_WS_WHILE_POLLING);
    			boolean ignoreOutgoingFromBuildWorkspace = "true".equals(strIgnoreOutgoingFromBuildWorkspace); 
    			
    			Integer currentRevisionHash = RTCFacadeFacade.incomingChangesUsingBuildToolkit(masterToolkit,
	    				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), useBuildDefinitionInBuild,
						getBuildDefinition(),
						getBuildWorkspace(),
						listener, ignoreOutgoingFromBuildWorkspace);
    			RTCRevisionState currentRevisionState = new RTCRevisionState(currentRevisionHash);
    			Change change = null;
    			if (isInQueue(project)) {
    				// get last revision hash
    				if (revisionState instanceof RTCRevisionState) {
	    				Integer lastRevisionHash = ((RTCRevisionState)revisionState).getLastRevisionHash();
	    				change = currentRevisionHash.equals(lastRevisionHash) ? Change.NONE : Change.SIGNIFICANT;
    				} else {
    					// we are in quite period, and previous revisionState is not of RTCRevisionState type...
    					// ideally we should never come here.
    					if (LOGGER.isLoggable(Level.FINER)) {
    	    				LOGGER.finer("The build request for the project " + project.getName() + //$NON-NLS-1$ //$NON-NLS-2$
    	    						" is already in queue, return polling result with changes as NONE to avoid resetting the quiet time"); //$NON-NLS-1$
    	    			}
    					change = Change.NONE;
    				}
    			} else {
    				if (project.isBuilding()) {
    					RTCBuildResultAction rtcBuildResultAction = project.getLastBuild().getAction(RTCBuildResultAction.class);
    					if ((rtcBuildResultAction != null) && (rtcBuildResultAction.getBuildProperties() != null) &&
    							"true".equals(rtcBuildResultAction.getBuildProperties().get("team_scm_acceptPhaseOver"))) {
    						// snapshot has been created, 
    						// hence further changes would not be taken by this build...
    						change = (currentRevisionHash == 0) ? Change.NONE : Change.SIGNIFICANT;
    					} else {
    						// build is running, but snapshot has not been created yet,
    						// hence any further changes can still be considered by this build
    						change = Change.NONE;
    					}
    				} else {
    					change = (currentRevisionHash == 0) ? Change.NONE : Change.SIGNIFICANT;
    				}
    			}
    			return new PollingResult(revisionState, currentRevisionState, change);
    		}
    		
    	} catch (Exception e) {
    		Throwable eToReport = e;
    		if (eToReport instanceof InvocationTargetException) {
    			if (e.getCause() != null) {
    				eToReport = e.getCause();
    			}
    		}
    		if (e instanceof InterruptedException) {
        		LOGGER.log(Level.FINER, "Checking for changes interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
    			throw (InterruptedException) e;
    		}
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checking_for_changes_failure(eToReport.getMessage()));
    		if (RTCScm.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		
    		// if we can't check for changes then we can't build it
    		throw new AbortException(Messages.RTCScm_checking_for_changes_failure2(eToReport.getMessage()));
    	}
    	finally {
    		LOGGER.finer("RTCScm.compareRemoteRevisionWith : End");
    	}
	}

	private boolean isInQueue(Job<?, ?> project) {
		LOGGER.finest("RTCScm.isInQueue : Begin");
		if(project instanceof AbstractProject) {
			return project.isInQueue();
		}else if (project instanceof Task && Jenkins.getInstance().getQueue() != null) {
			return Jenkins.getInstance().getQueue().contains((Task)project);
		} else {
			// returning false, since there is no good way to determine if job is in queue
			return false;
		}
	}

	public static boolean unexpectedFailure(Throwable e) {
		// This project can not reference types defined in the -rtc project, so we
		// need to do some clunky testing to see if the exception is notification of
		// a badly configured build. In that case, we just want to report the error
		// but not the whole stack trace.
		String name = e.getClass().getSimpleName();
		return !("RTCConfigurationException".equals(name)
				|| "AuthenticationException".equals(name)
				|| e instanceof InterruptedException 
				|| e instanceof InvalidCredentialsException ); //$NON-NLS-1$
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		LOGGER.finest("RTCScm.createChangeLogParser : Begin");
		return new RTCChangeLogParser();
	}

	@Override
	public RTCRepositoryBrowser getBrowser() {
		LOGGER.finest("RTCScm.getBrowser : Begin");
		return new RTCRepositoryBrowser(getServerURI());
	}

	public boolean getOverrideGlobal() {
		return overrideGlobal;
	}
	
	public RTCLoginInfo getLoginInfo(Job<?, ?> job, String toolkit) throws InvalidCredentialsException {
		return new RTCLoginInfo(job, toolkit, getServerURI(), getUserId(), getPassword(), getPasswordFile(), getCredentialsId(), getTimeout());
	}
	
	public String getBuildTool() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalBuildTool();
		}
		return buildTool;
	}

	public boolean getAvoidUsingToolkit() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalAvoidUsingToolkit();
		}
		return avoidUsingToolkit;
	}

	public String getServerURI() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalServerURI();
		}
		return serverURI;
	}

	public int getTimeout() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalTimeout();
		}
		return timeout;
	}

	public String getCredentialsId() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalCredentialsId();
		}
		return credentialsId;
	}

	public String getUserId() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalUserId();
		}
		return userId;
	}
	
	public String getPassword() {
		String result;
		if (!overrideGlobal) {
			result = getDescriptor().getGlobalPassword();
		} else if (password == null) {
			result = null;
		} else {
			result = password.getPlainText();
		}
		return result;
	}
	
	public String getPasswordFile() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalPasswordFile();
		}
		return passwordFile;
	}
	
	public File getPasswordFileFile() {
		String file = getPasswordFile();
		if (file != null && file.length() > 0) {
			return new File(file);
		}
		return null;
	}

	/**
	 * Determine if they are using the deprecated authentication in any way
	 * Takes into account whether they are overriding the global configuration or not.
	 * @return <code>true</code> if using user id & password or password file
	 * <code>false</code> if using credentials or no password authentication setup.
	 */
	public boolean usingDeprecatedPassword() {
		if (getDescriptor().deprecatedCredentialEditAllowed()) {
			return true;
		}
		String credentials = getCredentialsId();
		if (overrideGlobal && (credentials == null || credentials.isEmpty())) {
			// consider them to be using user id & old password way if supplied
			// not using strict validation since we would still work if too much info supplied.
			if (userId != null	&& !userId.isEmpty()
					&& ((password != null && password.getPlainText() != null && !password.getPlainText().isEmpty())
						|| (passwordFile != null && !passwordFile.isEmpty()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Show the deprecated password file in the UI if editting allowed or its available for use
	 * @return <code>true</code> to show it <code>false</code> otherwise
	 */
	public boolean showPasswordFile() {
		if (getDescriptor().deprecatedCredentialEditAllowed() 
				|| (overrideGlobal && passwordFile != null && !passwordFile.isEmpty())) {
			return true;
		}
		return false;

	}

	public boolean showPassword() {
		if (getDescriptor().deprecatedCredentialEditAllowed() 
				|| (overrideGlobal && password != null && password.getPlainText() != null && !password.getPlainText().isEmpty())) {
			return true;
		}
		return false;
	}

    @Exported
	public String getBuildTypeStr() {
		// migrate existing jobs with only build workspace or build definition defined
    	boolean oldData = false;
		if (buildTypeStr == null && buildWorkspace != null) {
			buildTypeStr = BUILD_WORKSPACE_TYPE;
			oldData = true;
		}else if(buildTypeStr == null && buildDefinition != null) {
			buildTypeStr = BUILD_DEFINITION_TYPE;
			oldData = true;
		}
		if(oldData) {
			LOGGER.warning("The job's config.xml has data stored in an older format, to migrate to the newer format resave the job's configuration");
		} 
		return buildTypeStr;
	}
    
    public BuildType getBuildType() {
		return buildType;
	}
	
	public String getBuildWorkspace() {
		return buildWorkspace;
	}
	
    @Exported
	public String getBuildDefinition() {
		return buildDefinition;
	}
	
	@Override
	@Exported
	public String getType() {
		return super.getType();
	}
	
	@Override
    public String getKey() {
		LOGGER.finest("RTCScm.getKey : Begin");
		StringBuilder key = new StringBuilder();
		key.append("teamconcert-")
			.append(getServerURI())
			.append("-")
			.append(getBuildTool());

		if (buildDefinition != null) {	
			key.append("-")
			   .append(buildDefinition);
		}
		if (buildWorkspace != null) {
			key.append("-")
			   .append(buildWorkspace);
		}
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("RTCScm.getKey key is " + key);
		}
		LOGGER.finest("RTCScm.getKey : End");
		return key.toString();
    }
	
	private static String getBuildResultUUID(Run<?,?> build, TaskListener listener) throws IOException, InterruptedException {
		 LOGGER.finest("RTCScm.getBuildResultUUID : Begin");
		 String buildResultUUID = Util.fixEmptyAndTrim(build.getEnvironment(listener).get(RTCBuildResultAction.BUILD_RESULT_UUID));
		 if (buildResultUUID == null) {
			// check if buildResultUUID parameter is available from ParametersAction
			buildResultUUID = getValueFromParametersAction(build, RTCBuildResultAction.BUILD_RESULT_UUID);
		 }
		 return buildResultUUID;
	}
	
	private static String getValueFromParametersAction(Run<?,?> build, String key) {
		String value = null;
		LOGGER.finest("RTCScm.getValueFromParametersAction : Begin");
		for (ParametersAction paction: build.getActions(ParametersAction.class)) {
			List<ParameterValue> pValues = paction.getParameters();
			if (pValues == null) {
				continue;
			}
            for(ParameterValue pv : pValues) {
            	if (pv instanceof StringParameterValue && pv.getName().equals(key)) {
            		value = Util.fixEmptyAndTrim((String) pv.getValue());
            		if (value != null) {
            			break;
            		}
            	}
            }
            if (value != null) {
            	break;
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {
        	if (value == null) {
        		LOGGER.finer("RTCScm.getValueFromParametersAction : Unable to find a value for key : " + key + ".Could be a build initiated locally.");
        	}
        	else {
	             LOGGER.finer("RTCScm.getValueFromParametersAction : Found value : " + value + " for key : " + key);
			}
        }
		return value;
	}
}