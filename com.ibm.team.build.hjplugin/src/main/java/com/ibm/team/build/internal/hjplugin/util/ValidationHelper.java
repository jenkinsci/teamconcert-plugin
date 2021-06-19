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
package com.ibm.team.build.internal.hjplugin.util;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.ibm.team.build.internal.hjplugin.Messages;

import hudson.Util;
import hudson.util.FormValidation;

public class ValidationHelper {

	private static final int MAX_RESULTS_UPPER_LIMIT = Helper.MAX_RESULTS_UPPER_LIMIT;

	public static FormValidation validatePattern(String fileNameOrPattern) {
		try {
			@SuppressWarnings("unused")
			Pattern p =java.util.regex.Pattern.compile(fileNameOrPattern);
		} catch (PatternSyntaxException exp) {
			return FormValidation.error(
					Messages.RTCBuildStep_file_name_pattern_invalid(fileNameOrPattern));
		}
		return FormValidation.ok();
	}
	
	public static FormValidation validateMaxResultsParm(String maxResults) {
		if (maxResults == null) {
			return FormValidation.error(
					Messages.RTCBuildStep_maxResults_empty());
		}
		try {
			FormValidation result = FormValidation.ok();
			result = FormValidation.validatePositiveInteger(maxResults);
			
			if (FormValidation.Kind.ERROR == result.kind) { 
				return FormValidation.error(
						Messages.RTCBuildStep_maxResults_invalid_value(maxResults));
			}
			
			int maxResultsInt = Integer.parseInt(maxResults);
			result = (maxResultsInt <= MAX_RESULTS_UPPER_LIMIT) ? FormValidation.ok() : 
					FormValidation.error(
							Messages.RTCBuildStep_maxResults_invalid_value_greater_than_2048());
			return result;
		} catch (NumberFormatException exp) {
			return FormValidation.error(
					Messages.RTCBuildStep_maxResults_invalid_value(maxResults));
		}
	}

	public static FormValidation validateFileName(String fileName) {
		// throw an exception that absolute paths should not be provided.
		if (!Util.isRelativePath(fileName)) {
			return FormValidation.error(
					Messages.RTCBuildStep_destination_file_name_ispath(fileName));
		}
		
		// If the filename still contains \ or /, then is not valid as well.
		// Is '..' an acceptable character in the filename? On its own, yes 
		// .. is valid either at the beginning or end of a file name.
		if (fileName.contains("/") || fileName.contains("\\")) {
			return FormValidation.error(
					Messages.RTCBuildStep_destination_file_name_ispath(fileName));
		}
		// OS specific validation happens in 
		// com.ibm.team.build.internal.hjplugin.rtc.RTCBuildUtils.
		return FormValidation.ok();
	}
}
