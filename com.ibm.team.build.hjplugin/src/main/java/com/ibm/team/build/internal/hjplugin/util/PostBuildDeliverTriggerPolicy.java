/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.util;

import hudson.model.Result;


public enum PostBuildDeliverTriggerPolicy {
	ALWAYS,
	
	NO_ERRORS,

	NO_WARNINGS;
	
	
	public boolean matches(Result buildResult){
		if (buildResult == null) {
			return false;
		}
		switch (this) {
			case NO_WARNINGS:
				if (buildResult.isBetterOrEqualTo(Result.SUCCESS)) {
					return true;
				}
				return false;
			case NO_ERRORS:
				if (buildResult.isBetterOrEqualTo(Result.UNSTABLE)) {
					return true;
				}
				return false;
			case ALWAYS:
				return true;
			default:
				return false;
		}
	}
}
