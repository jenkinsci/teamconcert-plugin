/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.hjplugin.steps;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Represents response from 'retrieveSnapshot' task.
 * Provides the snapshot name and snapshot UUID.
 */
public class RetrieveSnapshotStepResponse extends RTCBuildStepResponse {
	private static final long serialVersionUID = 1L;
	
	private String snapshotName;
	private String snapshotUUID;
	
	/**
	 * 
	 * It is expected that this cannot be instantiated from within 
	 * the pipeline script due to sandboxing.
	 * 
	 */
	public RetrieveSnapshotStepResponse(String snapshotName, 
							String snapshotUUID) { 
		this.snapshotName = snapshotName;
		this.snapshotUUID = snapshotUUID;
	}
	
	/**
	 * Get the name of the snapshot
	 * 
 	 * @return A String representing the name of the snapshot  
	 * 
	 */
	@Whitelisted
	public String getSnapshotName() {
		return snapshotName;
	}
	

	/**
	 * Get the UUUID of the snapshot
	 * 
	 * @return A String representing the UUID of the snapshot  
	 *  
	 */
	@Whitelisted
	public String getSnapshotUUID() {
		return snapshotUUID;
	}
}
