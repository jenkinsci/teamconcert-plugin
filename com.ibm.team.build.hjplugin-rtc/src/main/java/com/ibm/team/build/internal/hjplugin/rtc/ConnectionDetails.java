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

package com.ibm.team.build.internal.hjplugin.rtc;


public class ConnectionDetails {

	private final String fRepositoryAddress;
	private final String fUserId;
	private final String fPassword;
	private final int fTimeout;

	/**
	 * @param repositoryAddress URL of RTC server
	 * @param userId The user id to use for login. Never <code>null</code>
	 * @param password The password to use for login. Either password or password file should be supplied. Not both.
	 * @param timeout The timeout value for testing the connection.
	 */
	public ConnectionDetails(String repositoryAddress, String userId,
			String password, int timeout) {
		this.fRepositoryAddress = repositoryAddress;
		this.fUserId = userId;
		this.fPassword = password;
		this.fTimeout = timeout;
	}

	public String getRepositoryAddress() {
		return fRepositoryAddress;
	}

	public String getUserId() {
		return fUserId;
	}

	public String getPassword() {
		return fPassword;
	}

	public int getTimeout() {
		return fTimeout;
	}

	public String getHashKey() {
		return getRepositoryAddress() + ":" + getUserId() + ":" + getTimeout(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public String toString() {
		return "ConnectionDetails [repositoryAddress=" + getRepositoryAddress() //$NON-NLS-1$
				+ ", userId=" + getUserId() + ", password=" //$NON-NLS-1$ //$NON-NLS-2$
				+ (getPassword() == null ? null : "......") //$NON-NLS-1$
				+ ", timeout=" + getTimeout() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
}
