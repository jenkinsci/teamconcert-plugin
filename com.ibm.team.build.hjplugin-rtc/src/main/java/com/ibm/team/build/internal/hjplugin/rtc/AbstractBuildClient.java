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

import java.io.File;
import java.util.Locale;

/**
 * Interface to the Build and SCM client libraries.
 */
public abstract class AbstractBuildClient {

	/**
	 * Get the encapsulation of connection details
	 * @param repositoryAddress URL of RTC server
	 * @param userId The user id to use for login. Never <code>null</code>
	 * @param password The password to use for login. Hint, use {@link #determinePassword(String, File)}
	 * first to determine the password to use.
	 * @param timeout The timeout value for testing the connection.
	 * @throws Exception if anything should go wrong retrieving the password from the password file
	 * @ShortOp
	 */
	public abstract ConnectionDetails getConnectionDetails(String repositoryAddress, String userId,
			String password, int timeout) throws Exception;
	
	/**
	 * Returns a repository connection for the specified repository and credentials.  If a new one is created, it is cached.
	 * If one is already cached, it is returned.
	 * This does not log in automatically.
	 * 
	 * @param connectionDetails Specification of the repository server to connect along with the credentials to use.
	 * @throws Exception if an error occurs
	 * @ShortOp
	 */
	public abstract RepositoryConnection getRepositoryConnection(ConnectionDetails connectionDetails) throws Exception;

	
	/**
	 * Creates and returns a new repository connection for the specified repository and credentials.
	 * This does not log in automatically.
	 * 
	 * @param connectionDetails Specification of the repository server to connect along with the credentials to use.
	 * @throws Exception if an error occurs
	 * @ShortOp
	 */
	public abstract RepositoryConnection createRepositoryConnection(ConnectionDetails connectionDetails) throws Exception;


	/**
	 * Removes a repository connection for the specified repository and credentials.
	 * 
	 * @param connectionDetails Specification of the repository server to connect along with the credentials to use.
	 * @throws Exception if an error occurs
	 * @ShortOp
	 */
	public abstract RepositoryConnection removeRepositoryConnection(ConnectionDetails connectionDetails);

	/**
	 * Determines the password to use when connecting to a repository. If a password file is supplied,
	 * it is extracted from there. Otherwise the password given will be used. If no password or the
	 * empty string, this is considered an error. If the password file is invalid (doesn't exist,
	 * contents are not an obfuscated password) then this is also an error. In general, the password
	 * should be determined on the Master.
	 * @param password The password to use by default if there is no password file. Can be
	 * <code>null</code> if the password file is supplied
	 * @param passwordFile A file containing an obfuscated password. Can be <code>null</code>
	 * @param clientLocale The locale of the requesting client
	 * @return The password to use.
	 */
	public abstract String determinePassword(String password, File passwordFile, Locale clientLocale) throws Exception;

}