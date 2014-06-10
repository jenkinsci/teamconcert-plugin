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

public class Helper {

	/** 
	 * merge two results, if both are errors only one stack trace can be included
	 * @param firstCheck The first validation done
	 * @param secondCheck The second validaiton done
	 * @return The merge of the 2 validations with a concatenated message and the highest severity
	 */
	public static FormValidation mergeValidationResults(
			FormValidation firstCheck, FormValidation secondCheck) {
		Throwable errorCause = secondCheck.getCause();
		if (errorCause == null) {
			errorCause = firstCheck.getCause();
		}
		String message;
		String firstMessage = firstCheck.getMessage();
		String secondMessage = secondCheck.getMessage();
		if (firstCheck.kind.equals(FormValidation.Kind.OK) && (firstMessage == null || firstMessage.isEmpty())) {
			message = secondCheck.renderHtml();
		} else if (secondCheck.kind.equals(FormValidation.Kind.OK) && (secondMessage == null || secondMessage.isEmpty())) {
			message = firstCheck.renderHtml();
		} else {
			message = firstCheck.renderHtml() +  "<br/>" + secondCheck.renderHtml(); //$NON-NLS-1$
		}
		FormValidation.Kind kind;
		if (firstCheck.kind.equals(secondCheck.kind)) {
			kind = firstCheck.kind;
		} else if (firstCheck.kind.equals(FormValidation.Kind.OK)) {
			kind = secondCheck.kind;
		} else if (firstCheck.kind.equals(FormValidation.Kind.ERROR) || secondCheck.kind.equals(FormValidation.Kind.ERROR)) {
			kind = FormValidation.Kind.ERROR;
		} else {
			kind = FormValidation.Kind.WARNING;
		}
		
		return FormValidation.respond(kind, message);
	}
}
