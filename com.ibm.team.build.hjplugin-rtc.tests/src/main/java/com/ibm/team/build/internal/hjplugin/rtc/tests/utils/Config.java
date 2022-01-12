/*******************************************************************************
 * Copyright (c) 2016, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests.utils;

import java.text.MessageFormat;

public class Config {

	public static final String CONFIGURED = "com.ibm.team.build.configured";
	public static final String TOOLKIT = "com.ibm.team.build.toolkit";
	public static final String SERVER_URI = "com.ibm.team.build.serverURI";
	public static final String USER_ID = "com.ibm.team.build.userId";
	public static final String USER_ID_FOR_AUTHENTICATION_FAILURES = "com.ibm.team.build.userIdForAuthenticationFailures";
	public static final String PASSWORD = "com.ibm.team.build.password";
	public static final String TIMEOUT = "com.ibm.team.build.timeout";
	
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
				serverURI="https://localhost:9443/jazz/";
			} else {
				if (!serverURI.endsWith("/")) {
					serverURI = serverURI + "/";
				}
			}

			userId = System.getProperty(USER_ID);
			if (userId == null) {
				userId = "ADMIN";
			}
			
			if (userIDForAuthenticationFailures == null) {
				userIDForAuthenticationFailures = Long.toHexString(System.currentTimeMillis());
			}

			password = System.getProperty(PASSWORD);
			if (password == null) {
				throw new IllegalStateException(MessageFormat.format(
						MISSING_PROPERTY, PASSWORD));
			}

			timeout = Integer.parseInt("480");
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

	public String getUserIDForAuthenticationFailures() {
		validateConfigured();
		return userIDForAuthenticationFailures;
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