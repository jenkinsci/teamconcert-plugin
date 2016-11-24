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
package com.ibm.team.build.internal.hjplugin.rtc.tests;

public class StringUtil {
	public static String fixEmptyAndTrim(String s) {
		if (s == null) {
			return null;
		}
		s = s.trim();
		if (s.equals("")) {
			return null;
		}
		return s;
	}
}
