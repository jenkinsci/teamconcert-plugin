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
 * Represents response from 'downloadLog' or 'downloadArtifact' task.
 * Provides the name of the file and the file path on disk.
 * Note the the path is valid only in the context of the agent/workspace 
 * where the task ran.
 */
public class DownloadFileStepResponse extends RTCBuildStepResponse {
	
	/**
	 * Default serial id
	 */
	private static final long serialVersionUID = 1L;
	
	private final String fileName;
	private final String filePath;
	// For internal use only
	private String internalUUID;

	public DownloadFileStepResponse(String fileName, 
				String filePath) {
		this.fileName = fileName;
		this.filePath = filePath;
	}
	
	@Whitelisted
	public String getFileName() {
		return this.fileName;
	}
	
	@Whitelisted
	public String getFilePath() {
		return this.filePath;
	}
	
	public void setInternalUUID(String internalUUID) {
		this.internalUUID = internalUUID;
	}
	
	/**
	 * This can be removed or changed later. Use this method
	 * at your own risk.
	 * To access this field, approve the use of this API  
	 * in Jenkins' script approval settings.
	 */
	public String getInternalUUID() {
		return this.internalUUID;
	}
}
