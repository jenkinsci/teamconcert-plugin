/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.scm.common.IBaselineSet;

/**
 *  Class that provides the information needed to identify a build snapshot
 */
public class BuildSnapshotDescriptor {
	
	private static final Logger LOGGER = Logger.getLogger(BuildSnapshotDescriptor.class.getName());
	
    private final String repositoryUri;
    private final String snapshotUUID;
    private final String snapshotName;
    
    /**
     * Creates a BuildSnapshotDescriptor
     * @param teamRepository
     * @param snapshotUUID
     */
    public BuildSnapshotDescriptor(ITeamRepository teamRepository, String snapshotUUID, IBaselineSet snapshot) {
        this.repositoryUri = teamRepository.getRepositoryURI();
        this.snapshotUUID = snapshotUUID;
        this.snapshotName = snapshot.getName();
        
        if (LOGGER.isLoggable(Level.FINER)) {
        	LOGGER.finer(new StringBuilder().append("BuildSnapshotDescriptor : received ")
    				.append("repositoryUri : ")
    				.append(repositoryUri).append(", ")
    				.append("snapshotUUID : ")
    				.append(this.snapshotUUID).append(", ")
    				.append("workspaceName : ").toString());
        }
    }
    
    /**
     * Return the snapshotUUID of the snapshot described by this snapshot descriptor
     * @return the snapshotUUID 
     */
    public String getSnapshotUUID() {
    	return snapshotUUID;
    }
    
    /**
     * Return the name of the snapshot associated with this snapshot descriptor
     * @return
     */
	public String getSnapshotName() {
		return snapshotName;
	}
}
