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

/**
 * An RTC Validation Exception is an exception with a validation message. 
 */
public class RTCValidationException extends Exception {

	private static final long serialVersionUID = 1L;

	public RTCValidationException(String message) {
		super(message);
	}

}
