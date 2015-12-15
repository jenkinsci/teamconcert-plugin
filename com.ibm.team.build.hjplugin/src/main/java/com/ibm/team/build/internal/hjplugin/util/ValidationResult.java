/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.util;

import hudson.util.FormValidation;

import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;

/**
 * Structure to capture the info calculated during the validation of a 
 * connection information
 */
public class ValidationResult {

	public RTCLoginInfo loginInfo;
	public FormValidation validationResult;
	public String buildToolkitPath;
}
