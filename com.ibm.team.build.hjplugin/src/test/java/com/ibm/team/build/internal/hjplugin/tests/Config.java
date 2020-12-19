/*******************************************************************************
 * Copyright © 2013, 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests;

import java.text.MessageFormat;

import com.ibm.team.build.internal.hjplugin.InvalidCredentialsException;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;

@SuppressWarnings("nls")
public class Config {

	public static final String CONFIGURED = "com.ibm.team.build.configured";
	public static final String TOOLKIT = "com.ibm.team.build.toolkit";
	public static final String SERVER_URI = "com.ibm.team.build.serverURI";
	public static final String USER_ID = "com.ibm.team.build.userId";
	public static final String USER_ID_FOR_AUTHENTICATION_FAILURES = "com.ibm.team.build.userIdForAuthenticationFailures";
	public static final String PASSWORD = "com.ibm.team.build.password";
	public static final String PASSWORD_FILE = "com.ibm.team.build.passwordFile";
	public static final String TIMEOUT = "com.ibm.team.build.timeout";
	public static final String DUMP_LOG_FILES = "com.ibm.team.build.dumpLogFiles";
	public static final String BUILDTOOLKIT_VERSION = "com.ibm.team.build.buildToolkitVersion";

	public static final String SET_UP_ONLY = "com.ibm.team.build.setUpOnly";

	private static final String MISSING_PROPERTY = "Missing {0} property";
	private static final String NOT_CONFIGURED = "Not configured";

	private boolean configured;
	private String toolkit;
	private String serverURI;
	private String userId;
	private String userIDForAuthenticationFailures;
	private String password;
	private String passwordFile;
	private int timeout;
	private boolean setUpOnly;
	private boolean dumpLogFiles;
	private String buildToolkitVersion;

	public static final Config DEFAULT = new Config();

	private Config() {
		String configuredString = System.getProperty(CONFIGURED);
		if (configuredString == null) {
			setConfigured(false);
		}
		setConfigured(Boolean.parseBoolean(configuredString));

		if (isConfigured()) {

			setToolkit(System.getProperty(TOOLKIT));
			if (getToolkit() == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, TOOLKIT));
			}

			setServerURI(System.getProperty(SERVER_URI));
			if (getServerURI() == null) {
				setServerURI("https://localhost:9443/jazz/");
			} else {
				if (!getServerURI().endsWith("/")) {
					setServerURI(getServerURI() + "/");
				}
			}

			setUserId(System.getProperty(USER_ID));
			if (getUserId() == null) {
				setUserId("ADMIN");
			}
			
			if (getUserIDForAuthenticationFailures() == null) {
				setUserIDForAuthenticationFailures(Long.toHexString(System.currentTimeMillis()));
			}

			setPassword(System.getProperty(PASSWORD));
			if (getPassword() == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, PASSWORD));
			}

			setPasswordFile(System.getProperty(PASSWORD_FILE));

			String timeoutString = System.getProperty(TIMEOUT);
			if (timeoutString == null) {
				timeoutString = "480";
			}
			setTimeout(Integer.parseInt(timeoutString));
			
			String dumpLogFilesString = System.getProperty(DUMP_LOG_FILES);
			if (dumpLogFilesString == null) {
				dumpLogFilesString = "false";
			}
			setDumpLogFiles(Boolean.parseBoolean(dumpLogFilesString));
			
			String setUpOnlyString = System.getProperty(SET_UP_ONLY);
			if (setUpOnlyString == null) {
				setSetUpOnly(false);
			}
			setSetUpOnly(Boolean.parseBoolean(setUpOnlyString));
			
			String buildToolkitVersion = System.getProperty(BUILDTOOLKIT_VERSION);
			if (buildToolkitVersion == null) {
				buildToolkitVersion = "";
			}
			setBuildToolkitVersion(buildToolkitVersion);
		}
	}

	private void setBuildToolkitVersion(String buildToolkitVersion) {
		this.buildToolkitVersion = buildToolkitVersion;
		
	}

	public boolean isConfigured() {
		return this.configured;
	}

	private void validateConfigured() {
		if (!isConfigured()) {
			throw new IllegalStateException(NOT_CONFIGURED);
		}
	}

	public String getToolkit() {
		validateConfigured();
		return this.toolkit;
	}

	public String getServerURI() {
		validateConfigured();
		return this.serverURI;
	}

	public String getUserID() {
		validateConfigured();
		return getUserId();
	}

	public String getUserIDForAuthenticationFailures() {
		validateConfigured();
		return this.userIDForAuthenticationFailures;
	}

	public String getPassword() {
		validateConfigured();
		return this.password;
	}

	public String getPasswordFile() {
		validateConfigured();
		return this.passwordFile;
	}

	public int getTimeout() {
		validateConfigured();
		return this.timeout;
	}

	public boolean isSetUpOnly() {
		validateConfigured();
		return this.setUpOnly;
	}

	public boolean isDumpLogFiles() {
		validateConfigured();
		return this.dumpLogFiles;
	}
	
	public String getBuildToolkitVersion() {
		validateConfigured();
		return this.buildToolkitVersion;
	}

	public RTCLoginInfo getLoginInfo() throws InvalidCredentialsException {
		return new RTCLoginInfo(null, getToolkit(), getServerURI(), getUserID(), getPassword(), getPasswordFile(), null, getTimeout());
	}
	
	@Override
	public int hashCode() {
		StringBuffer b = new StringBuffer();
		b.append(getServerURI())
		  .append(getUserIDForAuthenticationFailures())
		  .append(getUserID())
		  .append(getToolkit())
		  .append(getTimeout())
		  .append(getPassword())
		  .append(isDumpLogFiles());
		if (getPasswordFile() != null) {
			b.append(getPasswordFile());
		}
		return b.hashCode();
	}

	private void setPassword(String password) {
		this.password = password;
	}

	public String getUserId() {
		return this.userId;
	}

	private void setUserId(String userId) {
		this.userId = userId;
	}

	private void setServerURI(String serverURI) {
		this.serverURI = serverURI;
	}

	private void setConfigured(boolean configured) {
		this.configured = configured;
	}

	private void setToolkit(String toolkit) {
		this.toolkit = toolkit;
	}

	private void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	private void setPasswordFile(String passwordFile) {
		this.passwordFile = passwordFile;
	}

	private void setDumpLogFiles(boolean dumpLogFiles) {
		this.dumpLogFiles = dumpLogFiles;
	}

	private void setSetUpOnly(boolean setUpOnly) {
		this.setUpOnly = setUpOnly;
	}

	private void setUserIDForAuthenticationFailures(String userIDForAuthenticationFailures) {
		this.userIDForAuthenticationFailures = userIDForAuthenticationFailures;
	}
}
