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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

/**
 * Represents response from 'listLogs' or 'listArtifacts' task.
 * Enumerate the 'fileInfos' attribute to get data about 
 * each log or artifact listed.
 */
public class ListFilesStepResponse extends RTCBuildStepResponse {

	/**
	 * Default serial id
	 */
	private static final long serialVersionUID = 1L;
	
	private final FileInfo [] fileInfos;
	
	private ListFilesStepResponse(FileInfo [] fileInfos) {
		this.fileInfos = fileInfos;
	}
	
	@Whitelisted
	public FileInfo [] getFileInfos() {
		return fileInfos;
	}
	
	public static class FileInfo implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private final String fileName;
		private final String componentName;
		private final String description;
		private final String type;
		private final String contentId;
		private final long size;
		private String internalUUID;
	
		private FileInfo(String fileName, 
					String componentName,
					String description,
					String type,
					String contentId,
					long size, String internalUUID) {
			this.fileName = fileName;
			this.componentName = componentName;
			this.description = description;
			this.type = type;
			this.contentId = contentId;
			this.size = size;
			this.internalUUID = internalUUID;
		}
		
		@Whitelisted
		public String getFileName() {
			return this.fileName;
		}
		
		@Whitelisted
		public String getComponentName() {
			return this.componentName;
		}
		
		@Whitelisted
		public String getDescription() {
			return this.description;
		}
		
		@Whitelisted
		public String getType() {
			return this.type;
		}
		
		@Whitelisted
		public String getContentId() {
			return this.contentId;
		}
		
		@Whitelisted
		public long getSize() {
			return this.size;
		}
		
		public String getInternalUUID() {
			return this.internalUUID;
		}
	}
	/**
	 * Simple builder for constructing a {@link ListFilesStepResponse}
	 */
	public static class ListFilesStepResponseBuilder {
		private int count = 0;
		private List<FileInfo> responseList = new ArrayList<>();
		
		public ListFilesStepResponseBuilder(int count) {
			// 0 is a valid count
			if (count < 0 ) {
				throw new IllegalArgumentException("'count' has an incorrect value"); //NON-NLS-1$
			}
			this.count = count;
		}
		
		public void add(String fileName, String componentName, String description, 
				String type, String contentId, long size, String internalUUID) throws OperationNotSupportedException {
			if (count == 0) {
				throw new OperationNotSupportedException("Cannot add data"  //NON-NLS-1$
						+ "when no allocation has been done.");   //NON-NLS-1$
			}
			if (responseList.size() >= count ) { 
				new IllegalStateException("Not enough space to add more data."); //NON-NLS-1$
			}
			FileInfo fileInfo = new FileInfo(fileName, componentName, 
					description, type, contentId, size, internalUUID);
			responseList.add(fileInfo);
		}
		
		public ListFilesStepResponse build() {
			if (responseList.size() < count) {
				new IllegalStateException("Not enough data to construct an instance."); //NON-NLS-1$
			}
			return new ListFilesStepResponse(responseList.toArray(new FileInfo[count]));
		}
	}
}
