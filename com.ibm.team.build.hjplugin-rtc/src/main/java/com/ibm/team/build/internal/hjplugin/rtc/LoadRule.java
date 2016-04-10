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

import java.io.Serializable;

/**
 * This class represents a LoadRule.
 * Mainly used to create Load rules from Jenkins job 
 *
 */
public class LoadRule implements Serializable {
	
	private static final long serialVersionUID = 415373961346009684L;
	
	//private String componentName;
	private String componentId;
	//private String filePath;
	private String fileItemId;
	
	/*
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}
	*/
	public void setComponentId(String componentId) {
		this.componentId = componentId;
	}
	
	/*
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
	*/
	
	public void setFileItemId(String fileItemId) {
		this.fileItemId = fileItemId;
	}
	
	/**
	 * Provide load rule in a format as expected by toolkit
	 * 
	 */
	public String getLoadRuleAsExpectedByToolkit() {
		StringBuffer result = new StringBuffer();
		result.append("component=");
		if (componentId != null) {
			result.append(componentId);
		}
		
		result.append(" fileitem=");
		if (fileItemId != null) {
			result.append(fileItemId);
		}
		
		return result.toString();
	}
}
