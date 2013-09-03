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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.remoting.Channel;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.Secret;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

public class RTCScm extends SCM {

    private static final Logger LOGGER = Logger.getLogger(RTCScm.class.getName());

	private static final String DEBUG_PROPERTY = "com.ibm.team.build.debug"; //$NON-NLS-1$

	// persisted fields for SCM
    private boolean overrideGlobal;
    
    // Global setting that have been overridden by the job (if overrideGlobal is true)
    private String buildTool;
	private String serverURI;
	private int timeout;
	private String userId;
	private Secret password;
	private String passwordFile;

	// Job configuration settings
	public static final String BUILD_WORKSPACE_TYPE = "buildWorkspace"; //$NON-NLS-1$
	public static final String BUILD_DEFINITION_TYPE = "buildDefinition"; //$NON-NLS-1$
	
	private String buildType;
	private String buildWorkspace;
	private String buildDefinition;

	private RTCRepositoryBrowser browser;
	
	/**
	 * Structure that represents the radio button selection for build workspace/definition
	 * choice in config.jelly (job configuration) 
	 */
	public static class BuildType {
		public String type;
		public String buildDefinition;
		public String buildWorkspace;
		
		@DataBoundConstructor
		public BuildType(String value, String buildDefinition, String buildWorkspace) {
			this.type = value;
			this.buildDefinition = buildDefinition;
			this.buildWorkspace = buildWorkspace;
		}
		
	}
	
	// Descriptor class - contains the global configuration settings
	@Extension
	public static class DescriptorImpl extends SCMDescriptor<RTCScm> {
		
		private static final String DEFAULT_USER_ID = "???"; //$NON-NLS-1$

		private static final String DEFAULT_SERVER_URI = "https://localhost:9443/ccm"; //$NON-NLS-1$

		private static final int DEFAULT_SERVER_TIMEOUT = 480;
		
		// persisted fields

		private String globalBuildTool;
		private String globalServerURI;
		private int globalTimeout;
		
		// explicit UserId & password or password file
		private String globalUserId;
		private Secret globalPassword;
		private String globalPasswordFile;
		
		public DescriptorImpl() {
			super(RTCScm.class, RTCRepositoryBrowser.class);
			load();
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
 			RTCScm scm = (RTCScm) super.newInstance(req, formData);
			
 			scm.browser = new RTCRepositoryBrowser(scm.getServerURI());
			return scm;
		}

		@Override
		public String getDisplayName() {
			return Messages.RTCScm_RTC_display_name();
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			globalBuildTool = Util.fixEmptyAndTrim(req.getParameter("buildTool")); //$NON-NLS-1$
			globalServerURI = Util.fixEmptyAndTrim(req.getParameter("serverURI")); //$NON-NLS-1$
			globalUserId = Util.fixEmptyAndTrim(req.getParameter("userId")); //$NON-NLS-1$
			String timeout = req.getParameter("timeout"); //$NON-NLS-1$
			try {
				globalTimeout = timeout == null ? 0 : Integer.parseInt(timeout);
			} catch (NumberFormatException e) {
				globalTimeout = 0;
			}
			String password = req.getParameter("password"); //$NON-NLS-1$
			globalPassword = password == null || password.length() == 0 ? null : Secret.fromString(password);
			globalPasswordFile = Util.fixEmptyAndTrim(req.getParameter("passwordFile")); //$NON-NLS-1$
			
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("configure : " + //$NON-NLS-1$
						"\" globalServerURI=\"" + globalServerURI + //$NON-NLS-1$
						"\" globalUserid=\"" + globalUserId + //$NON-NLS-1$
						"\" globalTimeout=\"" + globalTimeout + //$NON-NLS-1$
						"\" globalPassword " + (globalPassword == null ? "is not supplied" //$NON-NLS-1$ //$NON-NLS-2$ 
								: "(" + Secret.toString(globalPassword).length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$
						" globalPasswordFile=\"" + globalPasswordFile + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			save();
		    return true;
		}

		public String getGlobalBuildTool() {
			return globalBuildTool;
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
			if (globalUserId == null || globalUserId.length() == 0) {
				return DEFAULT_USER_ID;
			}
			return globalUserId;
		}
		
	    public int getGlobalTimeout() {
	    	if (globalTimeout == 0) {
	    		return DEFAULT_SERVER_TIMEOUT;
	    	}
    		return globalTimeout;
	    }
	    
		public FormValidation doCheckBuildToolkit(@QueryParameter String value) {
			return RTCBuildToolInstallation.validateBuildToolkit(value);
		}
		
		public String getMasterBuildToolkit(String buildTool, TaskListener listener) throws IOException, InterruptedException {
			return getBuildToolkit(buildTool, Hudson.getInstance(), listener);
		}
		
		private String getBuildToolkit(String buildTool, Node node, TaskListener listener) throws IOException, InterruptedException {
	        RTCBuildToolInstallation[] installations = RTCBuildToolInstallation.allInstallations();
	        for (RTCBuildToolInstallation buildToolIntallation : installations) {
	        	if (buildToolIntallation.getName().equals(buildTool)) {
	        		return buildToolIntallation.forNode(node, listener).getHome();
	        	}
	        }
			return null;
		}
		
	    public FormValidation doCheckTimeout(@QueryParameter String timeout) {
	    	timeout = Util.fixEmptyAndTrim(timeout);
	        if (StringUtils.isEmpty(timeout)) {
	        	LOGGER.finer("timeout value missing"); //$NON-NLS-1$
	            return FormValidation.error(Messages.RTCScm_timeout_required());
	        }
	        return FormValidation.validatePositiveInteger(timeout);
		}
	    
	    public FormValidation doCheckPassword(@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
	    	password = Util.fixEmptyAndTrim(password);
	    	passwordFile = Util.fixEmptyAndTrim(passwordFile);
	    	
	    	if (password != null && passwordFile != null) {
	    		LOGGER.finer("Both password (" + password.length() + " characters) and password file are supplied : " + passwordFile); //$NON-NLS-1$ //$NON-NLS-2$
	    		return FormValidation.error(Messages.RTCScm_supply_password_or_file());
	    	} if (password == null && passwordFile == null) {
	    		LOGGER.finer("Neither password or password file are supplied"); //$NON-NLS-1$
	    		return FormValidation.error(Messages.RTCScm_password_or_file_required());
	    	} if (passwordFile != null) {
				File passwordFileFile = new File(passwordFile);
				if (!passwordFileFile.exists()) {
					LOGGER.finer("Password file does not exist " + passwordFileFile.getAbsolutePath()); //$NON-NLS-1$
					return FormValidation.error(Messages.RTCScm_password_file_not_found(passwordFile));
				}
				if (passwordFileFile.isDirectory()) {
					LOGGER.finer("Password file is a directory : " + passwordFileFile.getAbsolutePath()); //$NON-NLS-1$
					return FormValidation.error(Messages.RTCScm_password_file_is_directory(passwordFile));
				}
	    	}
	    	
			return FormValidation.ok();
		}
	    
	    public FormValidation doCheckPasswordFile(@QueryParameter("password") String password,
	    		@QueryParameter("passwordFile") String passwordFile) {
	    	return doCheckPassword(password, passwordFile);
	    }

	    public FormValidation doCheckJobConnection(
	    		@QueryParameter("overrideGlobal") final String override,
	    		@QueryParameter("buildTool") final String buildTool,
	    		@QueryParameter("serverURI") final String serverURI,
	    		@QueryParameter("userId") final String userId,
	    		@QueryParameter("password") String password,
	    		@QueryParameter("passwordFile") String passwordFile,
	    		@QueryParameter("timeout") final String timeout) {
	    	
	    	password = Util.fixEmptyAndTrim(password);
	    	passwordFile = Util.fixEmptyAndTrim(passwordFile);
	    	
	    	boolean overrideGlobalSettings = Boolean.parseBoolean(override);
	    	if (overrideGlobalSettings) {
		    	String buildToolkitPath;
				try {
					buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
				} catch (Exception e) {
					return FormValidation.error(e, Messages.RTCScm_no_build_toolkit(e.getMessage()));
				}
		    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
		    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
		    		return buildToolkitCheck;
		    	}

		    	// validate the timeout value
	    		FormValidation timeoutCheck = doCheckTimeout(timeout);
	    		if (!timeoutCheck.kind.equals(FormValidation.Kind.OK)) {
	    			return timeoutCheck;
	    		}
	    		
	    		// validate password specification
	    		FormValidation passwordCheck = doCheckPassword(password, passwordFile);
	    		if (!passwordCheck.kind.equals(FormValidation.Kind.OK)) {
	    			return passwordCheck;
	    		}

	    		return checkConnect(buildToolkitPath, serverURI, userId, Integer.parseInt(timeout), password,
	    				passwordFile == null ? null : new File(passwordFile));

	    	} else {
		    	String buildToolkitPath;
				try {
					buildToolkitPath = getMasterBuildToolkit(getGlobalBuildTool(), TaskListener.NULL);
				} catch (Exception e) {
					return FormValidation.error(e, Messages.RTCScm_no_global_build_toolkit(e.getMessage()));
				}
		    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
		    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
		    		return buildToolkitCheck;
		    	}
	    		
	    		String globalPasswordFile = getGlobalPasswordFile();
	    		File passwordFileFile = globalPasswordFile == null ? null : new File(globalPasswordFile);
	    		return checkConnect(buildToolkitPath, getGlobalServerURI(), getGlobalUserId(), getGlobalTimeout(), getGlobalPassword(), passwordFileFile);
	    	}
	    }

	    public FormValidation doValidateBuildWorkspace(
	    		@QueryParameter("overrideGlobal") final String override,
	    		@QueryParameter("buildTool") final String buildTool,
	    		@QueryParameter("serverURI") final String serverURI,
	    		@QueryParameter("timeout") final String timeout,
	    		@QueryParameter("userId") final String userId,
	    		@QueryParameter("password") String password,
	    		@QueryParameter("passwordFile") String passwordFile,
	    		@QueryParameter("buildWorkspace") final String buildWorkspace) {

	    	password = Util.fixEmptyAndTrim(password);
	    	passwordFile = Util.fixEmptyAndTrim(passwordFile);

	    	boolean overrideGlobalSettings = Boolean.parseBoolean(override);
	    	if (overrideGlobalSettings) {
		    	String buildToolkitPath;
				try {
					buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
				} catch (Exception e) {
					return FormValidation.error(e, Messages.RTCScm_no_build_toolkit2(e.getMessage()));
				}
		    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
		    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
		    		return buildToolkitCheck;
		    	}

		    	// validate the timeout value
	    		FormValidation timeoutCheck = doCheckTimeout(timeout);
	    		if (!timeoutCheck.kind.equals(FormValidation.Kind.OK)) {
	    			return timeoutCheck;
	    		}
	    		
	    		// validate password specification
	    		FormValidation passwordCheck = doCheckPassword(password, passwordFile);
	    		if (!passwordCheck.kind.equals(FormValidation.Kind.OK)) {
	    			return passwordCheck;
	    		}

	    		return checkBuildWorkspace(buildToolkitPath, serverURI, Integer.parseInt(timeout), userId, password,
	    				passwordFile == null ? null : new File(passwordFile), buildWorkspace);

	    	} else {
		    	String buildToolkitPath;
				try {
					buildToolkitPath = getMasterBuildToolkit(getGlobalBuildTool(), TaskListener.NULL);
				} catch (Exception e) {
					return FormValidation.error(e, Messages.RTCScm_no_global_build_toolkit2(e.getMessage()));
				}
		    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
		    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
		    		return buildToolkitCheck;
		    	}

		    	String passwordFileToUse = getGlobalPasswordFile();
	    		return checkBuildWorkspace(buildToolkitPath, getGlobalServerURI(), getGlobalTimeout(), getGlobalUserId(), getGlobalPassword(),
	    				passwordFileToUse == null ? null : new File(getGlobalPasswordFile()), buildWorkspace);
	    	}
	    }

	    public FormValidation doValidateBuildDefinition(
	    		@QueryParameter("overrideGlobal") final String override,
	    		@QueryParameter("buildTool") final String buildTool,
	    		@QueryParameter("serverURI") final String serverURI,
	    		@QueryParameter("timeout") final String timeout,
	    		@QueryParameter("userId") final String userId,
	    		@QueryParameter("password") String password,
	    		@QueryParameter("passwordFile") String passwordFile,
	    		@QueryParameter("buildDefinition") final String buildDefinition) {

	    	password = Util.fixEmptyAndTrim(password);
	    	passwordFile = Util.fixEmptyAndTrim(passwordFile);

	    	boolean overrideGlobalSettings = Boolean.parseBoolean(override);
	    	if (overrideGlobalSettings) {
		    	String buildToolkitPath;
				try {
					buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
				} catch (Exception e) {
					return FormValidation.error(e, Messages.RTCScm_no_build_toolkit2(e.getMessage()));
				}
		    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
		    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
		    		return buildToolkitCheck;
		    	}

		    	// validate the timeout value
	    		FormValidation timeoutCheck = doCheckTimeout(timeout);
	    		if (!timeoutCheck.kind.equals(FormValidation.Kind.OK)) {
	    			return timeoutCheck;
	    		}
	    		
	    		// validate password specification
	    		FormValidation passwordCheck = doCheckPassword(password, passwordFile);
	    		if (!passwordCheck.kind.equals(FormValidation.Kind.OK)) {
	    			return passwordCheck;
	    		}

	    		return checkBuildDefinition(buildToolkitPath, serverURI, Integer.parseInt(timeout), userId, password,
	    				passwordFile == null ? null : new File(passwordFile), buildDefinition);

	    	} else {
		    	String buildToolkitPath;
				try {
					buildToolkitPath = getMasterBuildToolkit(getGlobalBuildTool(), TaskListener.NULL);
				} catch (Exception e) {
					return FormValidation.error(e, Messages.RTCScm_no_global_build_toolkit2(e.getMessage()));
				}
		    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
		    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
		    		return buildToolkitCheck;
		    	}

		    	String passwordFileToUse = getGlobalPasswordFile();
	    		return checkBuildDefinition(buildToolkitPath, getGlobalServerURI(), getGlobalTimeout(), getGlobalUserId(), getGlobalPassword(),
	    				passwordFileToUse == null ? null : new File(getGlobalPasswordFile()), buildDefinition);
	    	}
	    }

	    public FormValidation doCheckGlobalConnection(
	    		@QueryParameter("buildTool") final String buildTool,
	    		@QueryParameter("serverURI") final String serverURI,
	    		@QueryParameter("userId") final String userId,
	    		@QueryParameter("password") String password,
	    		@QueryParameter("passwordFile") String passwordFile,
	    		@QueryParameter("timeout") final String timeout) {
	    	
	    	password = Util.fixEmptyAndTrim(password);
	    	passwordFile = Util.fixEmptyAndTrim(passwordFile);
	    	
	    	// validate the build toolkit
	    	String buildToolkitPath;
			try {
				buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
			} catch (Exception e) {
				return FormValidation.error(e, Messages.RTCScm_no_global_build_toolkit3(e.getMessage()));
			}
	    	FormValidation buildToolkitCheck = doCheckBuildToolkit(buildToolkitPath);
	    	if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
	    		return buildToolkitCheck;
	    	}
	    	
	    	// validate the timeout value
    		FormValidation timeoutCheck = doCheckTimeout(timeout);
    		if (!timeoutCheck.kind.equals(FormValidation.Kind.OK)) {
    			return timeoutCheck;
    		}
    		
    		// validate password specification
    		FormValidation passwordCheck = doCheckPassword(password, passwordFile);
    		if (!passwordCheck.kind.equals(FormValidation.Kind.OK)) {
    			return passwordCheck;
    		}
    		
    		// validate the connection
			return checkConnect(buildToolkitPath, serverURI, userId, Integer.parseInt(timeout), password,
					passwordFile == null ? null : new File(passwordFile));
	    }
	    
		private FormValidation checkConnect(String buildToolkitPath, String serverURI, String userId, int timeout, String password, File passwordFile) {

	    	try {
	    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
				String errorMessage = (String) facade.invoke("testConnection", new Class[] { //$NON-NLS-1$
						String.class, // serverURI
						String.class, // userId
						String.class, // password
						File.class, // passwordFile
						int.class, // timeout
						Locale.class}, // clientLocale
						serverURI, userId, password, passwordFile, timeout, LocaleProvider.getLocale());
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
	    	    			"\" serverURI=\"" + serverURI + //$NON-NLS-1$
	    	    			"\" userId=\"" + userId + //$NON-NLS-1$
	    	    			"\" password " + (password == null ? "is not supplied" : "(" + password.length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    	    			" passwordFile=\"" + (passwordFile == null ? "" : passwordFile.getAbsolutePath()) + //$NON-NLS-1$ //$NON-NLS-2$
	    	    			"\" timeout=\"" + timeout + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkConnect invocation failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkConnect attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + serverURI + //$NON-NLS-1$
	    	    			"\" userId=\"" + userId + //$NON-NLS-1$
	    	    			"\" password " + (password == null ? "is not supplied" : "(" + password.length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    	    			" passwordFile=\"" + (passwordFile == null ? "" : passwordFile.getAbsolutePath()) + //$NON-NLS-1$ //$NON-NLS-2$
	    	    			"\" timeout=\"" + timeout + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkConnect failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, Messages.RTCScm_failed_to_connect(e.getMessage()));
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_connect_success());
	    }

		private FormValidation checkBuildWorkspace(String buildToolkitPath, String serverURI, int timeout, String userId, String password, File passwordFile, String buildWorkspace) {

			try {
	    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
	    		String errorMessage = (String) facade.invoke("testBuildWorkspace", //$NON-NLS-1$
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								File.class, // passwordFile
								int.class, // timeout
								String.class, // buildWorkspace
								Locale.class}, // clientLocale
						serverURI, userId, password, passwordFile, timeout,
						buildWorkspace, LocaleProvider.getLocale());
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
	    	    			"\" serverURI=\"" + serverURI + //$NON-NLS-1$
	    	    			"\" userId=\"" + userId + //$NON-NLS-1$
	    	    			"\" password " + (password == null ? "is not supplied" : "(" + password.length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    	    			" passwordFile=\"" + (passwordFile == null ? "" : passwordFile.getAbsolutePath()) + //$NON-NLS-1$ //$NON-NLS-2$
	    	    			"\" timeout=\"" + timeout + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildWorkspace invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildWorkspace attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + serverURI + //$NON-NLS-1$
	    	    			"\" userId=\"" + userId + //$NON-NLS-1$
	    	    			"\" password " + (password == null ? "is not supplied" : "(" + password.length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    	    			" passwordFile=\"" + (passwordFile == null ? "" : passwordFile.getAbsolutePath()) + //$NON-NLS-1$ //$NON-NLS-2$
	    	    			"\" timeout=\"" + timeout + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildWorkspace failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, e.getMessage());
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_build_workspace_success());
	    }

		private FormValidation checkBuildDefinition(String buildToolkitPath, String serverURI, int timeout, String userId, String password, File passwordFile, String buildDefinition) {

			try {
	    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
	    		String errorMessage = (String) facade.invoke("testBuildDefinition", //$NON-NLS-1$
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								File.class, // passwordFile
								int.class, // timeout
								String.class, // buildDefinition
								Locale.class}, // clientLocale
						serverURI, userId, password, passwordFile, timeout,
						buildDefinition, LocaleProvider.getLocale());
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
	    	    			"\" serverURI=\"" + serverURI + //$NON-NLS-1$
	    	    			"\" timeout=\"" + timeout +  //$NON-NLS-1$
	    	    			"\" userId=\"" + userId + //$NON-NLS-1$
	    	    			"\" password " + (password == null ? "is not supplied" : "(" + password.length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    	    			" passwordFile=\"" + (passwordFile == null ? "" : passwordFile.getAbsolutePath()) + //$NON-NLS-1$ //$NON-NLS-2$
	    	    	        "\" buildDefinition=\"" + buildDefinition +"\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildDefinition invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildDefinition attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + serverURI + //$NON-NLS-1$
	    	    			"\" timeout=\"" + timeout + //$NON-NLS-1$
	    	    			"\" userId=\"" + userId + //$NON-NLS-1$
	    	    			"\" password " + (password == null ? "is not supplied" : "(" + password.length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	    	    			" passwordFile=\"" + (passwordFile == null ? "" : passwordFile.getAbsolutePath()) + //$NON-NLS-1$ //$NON-NLS-2$
	    	    	        "\" buildDefinition=\"" + buildDefinition + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildDefinition failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, e.getMessage());
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_build_definition_success());
	    }
	}
	
	@DataBoundConstructor
	public RTCScm(boolean overrideGlobal, String buildTool, String serverURI, int timeout, String userId, Secret password, String passwordFile,
			BuildType buildType) {

		this.overrideGlobal = overrideGlobal;
		if (this.overrideGlobal) {
			this.buildTool = buildTool;
			this.serverURI = serverURI;
			this.timeout = timeout;
			this.userId = userId;
			this.password = password;
			this.passwordFile = passwordFile;
			
		}
		this.buildType = buildType.type;
		this.buildWorkspace = buildType.buildWorkspace;
		this.buildDefinition = buildType.buildDefinition;
		
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("RTCScm constructed with " + //$NON-NLS-1$
					" overrideGlobal=\"" + this.overrideGlobal + //$NON-NLS-1$
					"\" buildTool=\"" + this.buildTool + //$NON-NLS-1$
					"\" serverURI=\"" + this.serverURI + //$NON-NLS-1$
					"\" timeout=\"" + this.timeout + //$NON-NLS-1$
					"\" userId=\"" + this.userId + //$NON-NLS-1$
					"\" password " + (this.password == null ? "is not supplied" : "(" + Secret.toString(this.password).length() +" characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					" passwordFile=\"" + this.passwordFile + //$NON-NLS-1$
					"\" buildType=\"" + this.buildType + //$NON-NLS-1$
					"\" buildWorkspace=\"" + this.buildWorkspace + //$NON-NLS-1$
					"\" buildDefinition=\"" + this.buildDefinition + "\""); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

	@Override
	public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build,
			Launcher launcher, TaskListener listener) throws IOException,
			InterruptedException {
		// Our check for incoming changes uses the flow targets and does a real time compare
		// So for now we don't return a special revision state
		return SCMRevisionState.NONE;
	}

	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher arg1,
			FilePath workspacePath, BuildListener listener, File changeLogFile) throws IOException,
			InterruptedException {
		
		listener.getLogger().println(Messages.RTCScm_checkout_started());

		File passwordFileFile = getPasswordFileFile();
		String label = getLabel(build);
		String localBuildToolKit;
		String nodeBuildToolKit;
		String passwordToUse = null;
		String jazzServerURI = getServerURI();
		String jazzUserId = getUserId();
		int jazzTimeout = getTimeout();
		String buildWorkspace = getBuildWorkspace();
		String buildDefinition = getBuildDefinition();
		String buildResultUUID = Util.fixEmptyAndTrim(build.getEnvironment(listener).get(RTCBuildResultAction.BUILD_RESULT_UUID));
		String buildType = getBuildType();
		boolean useBuildDefinitionInBuild = BUILD_DEFINITION_TYPE.equals(buildType) || buildResultUUID != null;

		// Log in build result where the build was initiated from RTC
		// Because if initiated from RTC we will ignore build workspace if its a buildWorkspaceType
		if (buildResultUUID != null) {
			listener.getLogger().println(Messages.RTCScm_build_initiated_by());
		}
		
		RTCBuildResultAction buildResultAction = null;
		
		try {
			localBuildToolKit = getDescriptor().getMasterBuildToolkit(getBuildTool(), listener);
			nodeBuildToolKit = getDescriptor().getBuildToolkit(getBuildTool(), build.getBuiltOn(), listener);
			
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("checkout : " + build.getProject().getName() + " " + build.getDisplayName() + " " + build.getBuiltOnStr() + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" Load directory=\"" + workspacePath.getRemote() + "\"" +  //$NON-NLS-1$ //$NON-NLS-2$
						" Build tool=\"" + getBuildTool() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Local Build toolkit=\"" + localBuildToolKit + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Node Build toolkit=\"" + nodeBuildToolKit + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Server URI=\"" + jazzServerURI + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Userid=\"" + jazzUserId + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Authenticating with " + (passwordFileFile == null ? " configured password " : passwordFileFile.getAbsolutePath()) +  //$NON-NLS-1$ //$NON-NLS-2$
						" Build definition=\"" + buildDefinition + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Build workspace=\"" + buildWorkspace + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" useBuildDefinitionInBuild=\"" + useBuildDefinitionInBuild + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Baseline Set name=\"" + label + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(localBuildToolKit, null);
			passwordToUse = (String) facade.invoke("determinePassword", new Class[] { //$NON-NLS-1$
					String.class, // password,
					File.class, // passwordFile,
					Locale.class // clientLocale
			}, getPassword(), getPasswordFileFile(), LocaleProvider.getLocale());
			
			// If we don't have a build result but have a build definition, create the
			// build result prior to going to the slave so that it can be in the slave's
			// environment. Also we don't depend on slave giving it back and that helps
			// with terminating the build result during failure cases.
			boolean createdBuildResult = false;
			if (buildResultUUID == null && useBuildDefinitionInBuild) {
				buildResultUUID = (String) facade.invoke(
						"createBuildResult", new Class[] { //$NON-NLS-1$
								String.class, // serverURI,
								String.class, // userId,
								String.class, // password,
								int.class, // timeout,
								String.class, // buildDefinition,
								String.class, // buildLabel,
								Object.class, // listener
								Locale.class // clientLocale
						}, jazzServerURI, jazzUserId, passwordToUse,
						jazzTimeout, buildDefinition, label, listener, LocaleProvider.getLocale());
				createdBuildResult = true;
			} else if (buildResultUUID != null) {
				
				// Capture the reason for initiation in a cause for the build
				try {
					BuildResultInfo buildResultInfo = new BuildResultInfo(buildResultUUID);
					facade.invoke("getBuildResultInfo", new Class[] { //$NON-NLS-1$
							String.class, // serverURI,
							String.class, // userId,
							String.class, // passwordToUse,
							int.class, // timeout,
							Object.class, // buildResultInfoObject,
							Object.class, // listener,
							Locale.class}, // clientLocale)
							getServerURI(), getUserId(), passwordToUse,
							getTimeout(), buildResultInfo, listener, LocaleProvider.getLocale());
					
					RTCBuildCause rtcBuildCause = new RTCBuildCause(buildResultInfo);
					CauseAction cause = build.getAction(CauseAction.class);
					if (cause == null) {
						cause = new CauseAction(rtcBuildCause);
						build.addAction(cause);
					} else {
						cause.getCauses().add(rtcBuildCause);
					}
				} catch (Exception e) {
					Throwable eToReport = e;
					if (e instanceof InvocationTargetException) {
						eToReport = e.getCause();
					}
		    		if (eToReport instanceof InterruptedException) {
		        		LOGGER.log(Level.FINER, "build cause detection interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
		    			throw (InterruptedException) eToReport;
		    		}

		    		// log (non-fatal) error and keep going with the build
					listener.getLogger().println(Messages.RTCScm_build_cause_determination_failure(e.getMessage()));
					LOGGER.log(Level.FINER, "Unable to determine the reason RTC initiated the build", e); //$NON-NLS-1$
				}
			}

			// publish in the build result links to the project and the build
			if (buildResultUUID != null) {
				String root = Hudson.getInstance().getRootUrl();
				if (root != null) {
					root = Util.encode(root);
				}
				String projectUrl = build.getProject().getUrl();
				if (projectUrl != null) {
					projectUrl = Util.encode(projectUrl);
				}
				String buildUrl = build.getUrl();
				if (buildUrl != null) {
					buildUrl = Util.encode(buildUrl);
				}
				try {
					facade.invoke(
							"createBuildLinks", new Class[] { //$NON-NLS-1$
							String.class, // serverURI,
									String.class, // userId,
									String.class, // password,
									int.class, // timeout,
									String.class, // buildResultUUID
									String.class, // rootUrl
									String.class, // projectUrl
									String.class, // buildUrl
									Object.class, // listener)
							}, jazzServerURI, jazzUserId, passwordToUse,
							jazzTimeout, buildResultUUID, root, projectUrl,
							buildUrl, listener);
				} catch (Exception e) {
					Throwable eToReport = e;
					if (e instanceof InvocationTargetException) {
						eToReport = e.getCause();
					}
		    		if (eToReport instanceof InterruptedException) {
		        		LOGGER.log(Level.FINER, "build link creation interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
		    			throw (InterruptedException) eToReport;
		    		}
					listener.getLogger().println(Messages.RTCScm_link_creation_failure(e.getMessage()));
					LOGGER.log(Level.FINER, "Unable to link Hudson/Jenkins build with the RTC build result", e); //$NON-NLS-1$
				}
			
				// add the build result information to the build through an action
				buildResultAction = new RTCBuildResultAction(jazzServerURI, buildResultUUID, createdBuildResult);
				build.addAction(buildResultAction);
				
			}

			
			if (workspacePath.isRemote()) {
				// Slaves do a lazy remote class loader. The slave's class loader will request things as needed
				// from the remote master. The class loader on the master is the one that knows about the hjplugin-rtc.jar
				// but not any of the toolkit jars. So trying to send the class (& its references) is problematic.
				// The hjplugin-rtc.jar won't be able to be found on the slave either from the regular class loader either
				// (since its on the master). So what we do is send our hjplugin-rtc.jar over to the slave to "prepopulate"
				// it in the class loader. This way we can create our special class loader referencing it and all the toolkit
				// jars.
				sendJarsToSlave(facade, workspacePath);
			}

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
    		LOGGER.log(Level.FINER, "determinePassword/create build result failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$

    		// if we can't check out then we can't build it
    		throw new AbortException(Messages.RTCScm_checkout_failure4(e.getMessage()));
    	}

		
		OutputStream changeLogStream = new FileOutputStream(changeLogFile);
		RemoteOutputStream changeLog = new RemoteOutputStream(changeLogStream);
		
		boolean debug = Boolean.parseBoolean(build.getEnvironment(listener).get(DEBUG_PROPERTY));
		RTCCheckoutTask checkout = new RTCCheckoutTask(
				build.getProject().getName() + " " + build.getDisplayName() + " " + build.getBuiltOnStr(), //$NON-NLS-1$ //$NON-NLS-2$
				nodeBuildToolKit,
				jazzServerURI, jazzUserId, passwordToUse,
				jazzTimeout, buildResultUUID,
				buildWorkspace,
				label,
				listener, changeLog,
				workspacePath.isRemote(), debug, LocaleProvider.getLocale());
		
		Map<String, String> buildProperties = workspacePath.act(checkout);
		
		if (buildResultAction != null) {
			buildResultAction.addBuildProperties(buildProperties);
		}

		return true;
	}

	private void sendJarsToSlave(RTCFacadeWrapper facade, FilePath workspacePath)
			throws MalformedURLException, IOException, InterruptedException {
		VirtualChannel channel = workspacePath.getChannel();
		if (channel instanceof Channel && facade.getFacadeJarURL() != null) {
			// try to find our jars
			Class<?> originalClass = RTCScm.class;
			ClassLoader originalClassLoader = (ClassLoader) originalClass.getClassLoader();
			URL [] jars = new URL[] {facade.getFacadeJarURL()};
			boolean result = ((Channel) channel).preloadJar(originalClassLoader, jars);
			LOGGER.finer("Prefetch result for sending jars is " + result);  //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private String getLabel(AbstractBuild<?, ?> build) {
		// TODO if we have a build definition & build result id we should probably
		// follow a similar algorithm to RTC?
		// In the simple plugin case, generate the name from the project and the build
		return build.getProject().getName() + " " + build.getDisplayName(); //$NON-NLS-1$
	}

	@Override
	public boolean supportsPolling() {
		return true;
	}

	@Override
	public boolean requiresWorkspaceForPolling() {
		return false;
	}

	@Override
	protected PollingResult compareRemoteRevisionWith(
			AbstractProject<?, ?> project, Launcher launcher, FilePath workspacePath,
			TaskListener listener, SCMRevisionState revisionState) throws IOException,
			InterruptedException {
		// if #requiresWorkspaceForPolling is false, expect that launcher and workspacePath are null
		
		listener.getLogger().println(Messages.RTCScm_checking_for_changes());

		// check to see if there are incoming changes
    	try {
    		boolean useBuildDefinitionInBuild = BUILD_DEFINITION_TYPE.equals(getBuildType());
    		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(getDescriptor().getMasterBuildToolkit(getBuildTool(), listener), null);
			Boolean changesIncoming = (Boolean) facade.invoke(
					"incomingChanges", //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class, // timeout
							String.class, // buildDefinition
							String.class, // buildWorkspace
							Object.class, // listener
							Locale.class}, // clientLocale
					getServerURI(), getUserId(), getPassword(),
					getPasswordFileFile(), getTimeout(), 
					(useBuildDefinitionInBuild ? getBuildDefinition() : ""),
					(useBuildDefinitionInBuild ? "" : getBuildWorkspace()),
					listener, LocaleProvider.getLocale());
    		if (changesIncoming.equals(Boolean.TRUE)) {
    			listener.getLogger().println(Messages.RTCScm_changes_found());
    			return PollingResult.SIGNIFICANT;
    		} else {
    			listener.getLogger().println(Messages.RTCScm_no_changes_found());
    			return PollingResult.NO_CHANGES;
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
	}

	public static boolean unexpectedFailure(Throwable e) {
		// This project can not reference types defined in the -rtc project, so we
		// need to do some clunky testing to see if the exception is notification of
		// a badly configured build. In that case, we just want to report the error
		// but not the whole stack trace.
		String name = e.getClass().getSimpleName();
		return !("RTCConfigurationException".equals(name) || e instanceof InterruptedException);
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return new RTCChangeLogParser();
	}

	@Override
	public RTCRepositoryBrowser getBrowser() {
		return browser;
	}

	public boolean getOverrideGlobal() {
		return overrideGlobal;
	}
	
	public String getBuildTool() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalBuildTool();
		}
		return buildTool;
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

	public String getBuildType() {

		// migrate existing jobs with only build workspace defined to build workspace type
		if (buildType == null && buildWorkspace != null) {
			buildType = BUILD_WORKSPACE_TYPE;
		}
		return buildType;
	}
	
	public String getBuildWorkspace() {
		return buildWorkspace;
	}
	
	public String getBuildDefinition() {
		return buildDefinition;
	}
}