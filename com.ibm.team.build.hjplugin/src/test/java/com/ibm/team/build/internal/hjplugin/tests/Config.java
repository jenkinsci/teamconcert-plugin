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

package com.ibm.team.build.internal.hjplugin.tests;

import java.text.MessageFormat;

public class Config {

	public static final String CONFIGURED = "com.ibm.team.build.configured";
	public static final String TOOLKIT = "com.ibm.team.build.toolkit";
	public static final String SERVER_URI = "com.ibm.team.build.serverURI";
	public static final String USER_ID = "com.ibm.team.build.userId";
	public static final String PASSWORD = "com.ibm.team.build.password";
	public static final String PASSWORD_FILE = "com.ibm.team.build.passwordFile";
	public static final String TIMEOUT = "com.ibm.team.build.timeout";

	private static final String MISSING_PROPERTY = "Missing {0} property";
	private static final String NOT_CONFIGURED = "Not configured";

	private boolean configured;
	private String toolkit;
	private String serverURI;
	private String userId;
	private String password;
	private String passwordFile;
	private int timeout;

	public static final Config DEFAULT = new Config();

	private Config() {
		String configuredString = System.getProperty(CONFIGURED);
		if (configuredString == null) {
			configured = false;
		}
		configured = Boolean.parseBoolean(configuredString);

		if (configured) {

			toolkit = System.getProperty(TOOLKIT);
			if (toolkit == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, TOOLKIT));
			}

			serverURI = System.getProperty(SERVER_URI);
			if (serverURI == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, SERVER_URI));
			}

			userId = System.getProperty(USER_ID);
			if (userId == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, USER_ID));
			}

			password = System.getProperty(PASSWORD);
			if (password == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, PASSWORD));
			}

			passwordFile = System.getProperty(PASSWORD_FILE);
			if (passwordFile == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, PASSWORD_FILE));
			}

			String timeoutString = System.getProperty(TIMEOUT);
			if (timeoutString == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, TIMEOUT));
			}
			timeout = Integer.parseInt(timeoutString);

		}
	}

	public boolean isConfigured() {
		return configured;
	}

	private void validateConfigured() {
		if (!configured) {
			throw new IllegalStateException(NOT_CONFIGURED);
		}
	}

	public String getToolkit() {
		validateConfigured();
		return toolkit;
	}

	public String getServerURI() {
		validateConfigured();
		return serverURI;
	}

	public String getUserID() {
		validateConfigured();
		return userId;
	}

	public String getPassword() {
		validateConfigured();
		return password;
	}

	public String getPasswordFile() {
		validateConfigured();
		return passwordFile;
	}

	public int getTimeout() {
		validateConfigured();
		return timeout;
	}

}
