/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.client.ClientFactory;
import com.ibm.team.build.client.ITeamBuildClient;
import com.ibm.team.build.client.ITeamBuildRequestClient;
import com.ibm.team.build.internal.PasswordHelper;
import com.ibm.team.build.internal.scm.RepositoryManager;
import com.ibm.team.build.internal.scm.RepositoryManager.IConsole;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.common.TeamRepositoryException;

/**
 * Build client implementation.  Main entrypoint for access to Build and SCM client library APIs.
 */
public class BuildClient extends AbstractBuildClient {

    private static final Logger LOGGER = Logger.getLogger(BuildClient.class.getName());

	static {
		TeamPlatform.startup();
	}

	private final Map<String, RepositoryConnection> fRepositoryConnections = new HashMap<String, RepositoryConnection>();

	
	@Override
	public ConnectionDetails getConnectionDetails(String repositoryAddress,
			String userId, String password, int timeout) {
		return new ConnectionDetails(repositoryAddress, userId, password, timeout);
	}

	@Override
	public String determinePassword(File passwordFile, Locale clientLocale)
			throws Exception {
		String passwordToUse = getPasswordFromFile(passwordFile, clientLocale);
		if (passwordToUse == null || passwordToUse.length() == 0) {
			LOGGER.finer("No password determined because password determined is "  //$NON-NLS-1$
					+ (passwordToUse == null ? "null" : "empty"));  //$NON-NLS-1$ //$NON-NLS-2$
			throw new RTCConfigurationException(Messages.get(clientLocale).BuildClient_no_password());
		}
		return passwordToUse;
	}

    /**
     * Get the password as provided by the passwordFile
     */
	private String getPasswordFromFile(File passwordFile, Locale clientLocale) throws Exception {
        if (passwordFile != null) {
        	String decryptedPassword = null;
            try {
    			decryptedPassword = PasswordHelper.getPassword(passwordFile);
            } catch (Exception exception) {
            	LOGGER.log(Level.FINER, "Problem reading password file " + passwordFile.getAbsolutePath(), exception);
                throw new RTCConfigurationException(Messages.get(clientLocale).BuildClient_bad_password_file(passwordFile.getAbsolutePath()));
            }
            
            // An empty password text file returns an empty password string instead of throwing an exception
            // See 255010: PasswordHelper.getPassword(passwordFile) returns empty string for invalid password file
			if (decryptedPassword != null && decryptedPassword.length() == 0) {
				throw new RTCConfigurationException(Messages.get(clientLocale).BuildClient_bad_password_file(passwordFile.getAbsolutePath()));
			} else {
				return decryptedPassword;
			}
        }
        return null;
	}


	@Override
	public synchronized RepositoryConnection getRepositoryConnection(ConnectionDetails connectionDetails) throws Exception {
		RepositoryConnection connection = fRepositoryConnections.get(connectionDetails.getHashKey());
		
		// remove connection if password has changed 
		if (connection != null && !connection.getConnectionDetails().getPassword().equals(connectionDetails.getPassword())) {
			removeRepositoryConnection(connectionDetails);
			connection = null;
		}
		
		// create new connection
		if (connection == null) {
			connection = createRepositoryConnection(connectionDetails);
			fRepositoryConnections.put(connectionDetails.getHashKey(), connection);
			LOGGER.finer("Added connection " + connectionDetails.getHashKey()); //$NON-NLS-1$
			for (String connectionHashKey : fRepositoryConnections.keySet()) {
				LOGGER.finer("Stored connection " + connectionHashKey); //$NON-NLS-1$
			}
		}
		return connection;
	}

	@Override
	public RepositoryConnection createRepositoryConnection(ConnectionDetails connectionDetails) throws TeamRepositoryException {
		RepositoryManager repoMgr = createRepositoryManager(connectionDetails);
		ITeamRepository repo = repoMgr.getRepository(connectionDetails.getRepositoryAddress(), true);
		repo.setConnectionTimeout(connectionDetails.getTimeout());
		RepositoryConnection connection = new RepositoryConnection(this, connectionDetails, repoMgr, repo);
		return connection;
	}
	

	@Override
	public synchronized RepositoryConnection removeRepositoryConnection(ConnectionDetails connectionDetails) {
		RepositoryConnection repositoryConnection = fRepositoryConnections.get(connectionDetails.getHashKey());
		
		// remove connection if password matches
		if (repositoryConnection != null && repositoryConnection.getConnectionDetails().getPassword().equals(connectionDetails.getPassword())) {
			fRepositoryConnections.remove(connectionDetails.getHashKey());
			LOGGER.finer("Removed connection " + repositoryConnection.getConnectionDetails().getHashKey()); //$NON-NLS-1$
			for (String connectionHashKey : fRepositoryConnections.keySet()) {
				LOGGER.finer("Stored connection " + connectionHashKey); //$NON-NLS-1$
			}
			return repositoryConnection;
		}
		return null;
	}

    /**
     * Creates the repository manager for the given connection details.
     * 
     * @ShortOp
     */
    private RepositoryManager createRepositoryManager(ConnectionDetails connectionDetails) throws TeamRepositoryException {
        IConsole console = new IConsole() {
            public void print(String message) {
            	LOGGER.finer(message);
            }
        };

		return RepositoryManager.createForUsernameAndPassword(
				connectionDetails.getRepositoryAddress(),
				connectionDetails.getUserId(), connectionDetails.getPassword(),
				console);
    }

	private ITeamBuildClient getBuildClient(ITeamRepository repo) {
		return ClientFactory.getTeamBuildClient(repo);
	}
	
	private ITeamBuildRequestClient getBuildRequestClient(ITeamRepository repo) {
		return ClientFactory.getTeamBuildRequestClient(repo);
	}

}
