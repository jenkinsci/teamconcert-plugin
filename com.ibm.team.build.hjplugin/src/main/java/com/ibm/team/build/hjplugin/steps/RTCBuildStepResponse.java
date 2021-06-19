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

/**
 * Represents a response from invoking a RTC task.
 * This is not abstract but it doesn't have any members yet.
 * It exists to provide a  inheritance hierarchy for 
 * responses from all kinds of RTC tasks.
 */
public class RTCBuildStepResponse implements Serializable {

	private static final long serialVersionUID = 1L;
}
