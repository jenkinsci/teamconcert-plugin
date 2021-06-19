/*******************************************************************************
 * Copyright (c) 2014, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin;

import hudson.Util;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.LocaleProvider;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.util.Helper;

public class RTCLoginInfo {
	private static final Logger LOGGER = Logger.getLogger(RTCScm.class.getName());
	
	private String serverUri;
	private String userId;
	private String password;
	private int timeout;
	
	/**
	 * Figure out the info needed to log into RTC based on a variety of ways to authenticate
	 * It is expected that supplied values have been validated to some degree
	 * @param project The project to be built (hence credentials needed for) may be
	 * <code>null</code> when dealing with the global case
	 * @param buildToolkitPath The path of the build toolkit. Could be <code>null</code>
	 * if not using a password file
	 * @param serverUri The server to log into. Required
	 * @param userId The user id to use. May be <code>null</code> when working with
	 * credentials
	 * @param password The password to use. May be <code>null</code> when working with
	 * credentials or a password file.
	 * @param passwordFile The file containing the password. May be <code>null</code>
	 * when working with credentials or a password.
	 * @param credentialsId The id of Jenkins credentials. May be <code>null</code>
	 * when working with userId & either password/password file
	 * @param timeout The time out to use (in seconds). Required
	 * @throws Exception When something goes wrong determining the credentials.
	 */
	public RTCLoginInfo(Job<?,?> project, String buildToolkitPath,
			String serverUri, String userId, String password,
			String passwordFile, String credentialsId, int timeout) throws InvalidCredentialsException {
		credentialsId = Util.fixEmptyAndTrim(credentialsId);
    	password = Util.fixEmptyAndTrim(password);
    	passwordFile = Util.fixEmptyAndTrim(passwordFile);
    	userId = Util.fixEmptyAndTrim(userId);

		if (credentialsId != null) {
			// figure out userid & password from the credentials
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Looking up credentials for " +  //$NON-NLS-1$
						"credentialId=\"" + credentialsId + //$NON-NLS-1$
						"\" serverURI=\"" + serverUri +  //$NON-NLS-1$
						"\" project=" + (project == null ? "null" : "\"" + project.getName() + "\"")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ $NON-NLS-2$ $NON-NLS-3$ $NON-NLS-4$ 
			}

			List<StandardUsernamePasswordCredentials> allMatchingCredentials = CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM,
							URIRequirementBuilder.fromUri(serverUri).build());
			StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(allMatchingCredentials, 
							CredentialsMatchers.withId(credentialsId));
			if (credentials != null) {
				this.userId = credentials.getUsername();
				this.password = credentials.getPassword().getPlainText();
			} else {
				throw new InvalidCredentialsException(Messages.RTCLoginInfo_creds_unresolvable());
			}
			
		} else {
			this.userId = userId;
			
			if (this.userId == null) {
				if (passwordFile == null && password == null) {
					throw new InvalidCredentialsException(Messages.RTCLoginInfo_missing_creds());
				} else {
					throw new InvalidCredentialsException(Messages.RTCLoginInfo_missing_userid());
				}
			}

			if (passwordFile != null) {
				
				// figure out the password in the file
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("Looking up credentials for " +  //$NON-NLS-1$
							"userId=\"" + userId + //$NON-NLS-1$
							"\" passwordFile=\"" + passwordFile); //$NON-NLS-1$
				}
				try {
					RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
					this.password = (String) facade.invoke("determinePassword", new Class[] { //$NON-NLS-1$
							File.class, // passwordFile,
							Locale.class // clientLocale
					}, new File(passwordFile), LocaleProvider.getLocale());
				} catch (Exception e) {
		    		Throwable eToReport = e;
		    		if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
						eToReport = e.getCause();
		    		}
					if (LOGGER.isLoggable(Level.FINER)) {
						LOGGER.log(Level.FINER, "Failed to resolve password from passwordFile=\"" + passwordFile + "\" : " + eToReport.getMessage(), e); //$NON-NLS-1$ //$NON-NLS-2$
					}
					throw new InvalidCredentialsException(Messages.RTCLoginInfo_missing_password(passwordFile, eToReport.getMessage()), e);
				}
				
			} else if (password != null) {
				// password was given
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("Credentials supplied for " +  //$NON-NLS-1$
							"userId=\"" + userId + //$NON-NLS-1$
							"\" password=" + (password == null ? "null" : "non-null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				this.password = password;
				
			} else {
				// no password info supplied
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("No password supplied for " + //$NON-NLS-1$
							"userId=\"" + userId + //$NON-NLS-1$
							"\" password=" + (password == null ? "null" : "non-null")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				throw new InvalidCredentialsException(Messages.RTCLoginInfo_supply_password_file_or_password());
			}
		}
		this.serverUri = serverUri;
		this.timeout = timeout;
	}
	
	public RTCLoginInfo(Run<?, ?> build, String serverUri, String credentialsId, 
				int timeout, TaskListener listener, boolean isDebug) throws InvalidCredentialsException {
		credentialsId = Util.fixEmptyAndTrim(credentialsId);

		if (credentialsId != null) {
			String message = "Looking up credentials for " +  //$NON-NLS-1$
					"credentialId=\"" + credentialsId + //$NON-NLS-1$
					"\" serverURI=\"" + serverUri +  //$NON-NLS-1$
					"\" run=\"" + build.number +   //$NON-NLS-1$
					"\" project=" + (build.getParent() == null ? "null" : "\"" + build.getParent().getFullDisplayName() + "\"");
			// figure out userid & password from the credentials
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer(message);
			}
			if (isDebug) {
				listener.getLogger().println(message);
			}
			
			StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(credentialsId, StandardUsernamePasswordCredentials.class, build, URIRequirementBuilder.fromUri(serverUri).build());
			
			if (credentials != null) {
				this.userId = credentials.getUsername();
				this.password = credentials.getPassword().getPlainText();
				CredentialsProvider.track(build, credentials);
			} else {
				throw new InvalidCredentialsException(Messages.RTCLoginInfo_creds_unresolvable());
			}
			
		} else {
			throw new InvalidCredentialsException(Messages.RTCLoginInfo_missing_creds());
		}
		this.serverUri = serverUri;
		this.timeout = timeout;
	}
	
	public String getServerUri() {
		return serverUri;
	}

	public String getUserId() {
		return userId;
	}

	public String getPassword() {
		return password;
	}

	public int getTimeout() {
		return timeout;
	}
	
	/**
	 * Perform lightweight validation of the info required for logging in. By lightweight that means
	 * validate that information is supplied and combinations make sense.
	 * @param credentialsId The credentials id. Either it or userid/password/file info is valid
	 * @param userId The userId. Requires password or password file, not to be used in conjunction with credentials
	 * @param passwordFile The password file, must exist (contents not validated). To be used in conjunction with userId.
	 * not to be used in conjunction with credentials nor password.
	 * @param password The password, To be used in conjunction with userId. Not to be used in conjunction with credentials
	 * nor passwordFile.
	 * @param timeout The timeout. Should be a positive integer.
	 * @return validation result. Might be ok or warning or error.
	 */
	public static FormValidation basicValidate(String credentialsId, String userId, String passwordFile, String password, String timeout) {

		// validate the timeout value
		FormValidation result = validateTimeout(timeout);
		if (result.kind.equals(FormValidation.Kind.ERROR)) {
			return result;
		}
		
		// validate credentials
		result = validateCredentials(credentialsId, userId, passwordFile, password);
		if (result.kind.equals(FormValidation.Kind.ERROR)) {
			return result;
		}

		// validate userid
		FormValidation validationCheck = validateUserId(credentialsId, userId, passwordFile, password);
		if (validationCheck.kind.equals(FormValidation.Kind.ERROR)) {
			return validationCheck;
		} else if (validationCheck.kind.equals(FormValidation.Kind.WARNING)) {
			result = validationCheck;
		}
		
		// validate password file if given since thats what we will use to authenticate with
		// otherwise validate the password
		if (passwordFile != null) {
			validationCheck = validatePasswordFile(credentialsId, userId, passwordFile, password);
		} else {
			validationCheck = validatePassword(credentialsId, userId, passwordFile, password);
		}
		result = Helper.mergeValidationResults(result, validationCheck);
		return result;
	}
	
	/**
	 * Intended to validate the password field in isolation
	 * If credentials are being used and password info supplied, warn it will be ignored
	 * If both password and password file supplied warning that password file will be used
	 */
	public static FormValidation validatePassword(String credentialsId,
			String userId, String passwordFile, String password) {

		userId = Util.fixEmptyAndTrim(userId);
		password = Util.fixEmptyAndTrim(password);
		passwordFile = Util.fixEmptyAndTrim(passwordFile);
		credentialsId = Util.fixEmptyAndTrim(credentialsId);

		if (password != null) {
			if (credentialsId != null && userId == null) {
				return FormValidation.warning(Messages.RTCLoginInfo_password_ignored()); 
			} else if (credentialsId == null && passwordFile != null) {
				// assume they are still working with userids
				LOGGER.finer("Both password (" + password.length() + " characters) and password file are supplied : " + passwordFile); //$NON-NLS-1$ //$NON-NLS-2$
				return FormValidation.warning(Messages.RTCLoginInfo_both_supplied_password_ignored()); 
			}
		} else if (userId != null && credentialsId == null && passwordFile == null) {
    		LOGGER.finer("Missing password file (and password file) when using a user id to authenticate"); //$NON-NLS-1$
    		return FormValidation.error(Messages.RTCLoginInfo_supply_password_file_or_password());
    	}
		return FormValidation.ok();
	}

	
	/**
	 * Intended to validate the password file field in isolation
	 * If credentials are being used and password file info supplied, warn it will be ignored
	 * if password file doesn't exist or is not a file, its an error
	 * If both password and password file supplied warning that password file will be used
	 */
	public static FormValidation validatePasswordFile(String credentialsId,
			String userId, String passwordFile, String password) {

		userId = Util.fixEmptyAndTrim(userId);
		password = Util.fixEmptyAndTrim(password);
		passwordFile = Util.fixEmptyAndTrim(passwordFile);
		credentialsId = Util.fixEmptyAndTrim(credentialsId);

		if (passwordFile != null) {
			if (credentialsId != null && userId == null) {
				return FormValidation.warning(Messages.RTCLoginInfo_password_file_ignored());
			}
			File passwordFileFile = new File(passwordFile);
			if (!passwordFileFile.exists()) {
				LOGGER.finer("Password file does not exist " + passwordFileFile.getAbsolutePath()); //$NON-NLS-1$
				return FormValidation.error(Messages.RTCScm_password_file_not_found(passwordFile));
			}
			if (passwordFileFile.isDirectory()) {
				LOGGER.finer("Password file is a directory : " + passwordFileFile.getAbsolutePath()); //$NON-NLS-1$
				return FormValidation.error(Messages.RTCScm_password_file_is_directory(passwordFile));
			}
			if (credentialsId == null && password != null) {
				// assume they are still working with userids
				LOGGER.finer("Both password (" + password.length() + " characters) and password file are supplied : " + passwordFile); //$NON-NLS-1$ //$NON-NLS-2$
				return FormValidation.warning(Messages.RTCLoginInfo_both_supplied_password_file_used()); 
			}
    	} else if (userId != null && credentialsId == null && password == null) {
    		LOGGER.finer("Missing password file (and password) when using a user id to authenticate"); //$NON-NLS-1$
    		return FormValidation.error(Messages.RTCLoginInfo_supply_password_file_or_password());
    	}
		return FormValidation.ok();
	}
	

	/**
	 * Validate the user id
	 * If credentials and user id supplied then warn that user id is ignored
	 * If no credentials and password info supplied but no user id then error user id is required.
	 * Otherwise ok.
	 */
	public static FormValidation validateUserId(String credentialsId,
			String userId, String passwordFile, String password) {

		userId = Util.fixEmptyAndTrim(userId);
		password = Util.fixEmptyAndTrim(password);
		passwordFile = Util.fixEmptyAndTrim(passwordFile);
		credentialsId = Util.fixEmptyAndTrim(credentialsId);

		if (credentialsId == null && userId == null && (password != null || passwordFile != null)) {
			return FormValidation.error(Messages.RTCLoginInfo_missing_userid()); 
		} else if (credentialsId != null && userId != null) {
			return FormValidation.warning(Messages.RTCLoginInfo_credentials_used());
		}
		return FormValidation.ok();
	}

	/**
	 * Validate the timeout
	 * Must be a positive integer
	 */
	public static FormValidation validateTimeout(String timeout) {
		timeout = Util.fixEmptyAndTrim(timeout);
		if (StringUtils.isEmpty(timeout)) {
			LOGGER.finer("timeout value missing"); //$NON-NLS-1$
			return FormValidation.error(Messages.RTCScm_timeout_required());
		}
		return FormValidation.validatePositiveInteger(timeout);
	}

	/**
	 * Validate the credentials id
	 * We want credentials id if no auth given
	 */
	public static FormValidation validateCredentials(String credentialsId,
			String userId, String passwordFile, String password) {

		userId = Util.fixEmptyAndTrim(userId);
		password = Util.fixEmptyAndTrim(password);
		passwordFile = Util.fixEmptyAndTrim(passwordFile);
		credentialsId = Util.fixEmptyAndTrim(credentialsId);
		
		if (credentialsId == null && userId == null && password == null && passwordFile == null) {
			return FormValidation.error(Messages.RTCScm_credentials_required());
		}
		return FormValidation.ok();
	}
}
