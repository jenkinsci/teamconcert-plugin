/*******************************************************************************
 * Copyright (c) 2016, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TestUtils {
    /**
     * This returns an unique name
     * @return A name that is unique
     * @throws UnknownHostException 
     */
	public static String getUniqueName(String prefix) {
		String suffix1 = (SimpleDateFormat.getDateTimeInstance
				(DateFormat.LONG, DateFormat.FULL)).format(new Date()).replace(" ","-");
		SecureRandom random = new SecureRandom();
		Long suffix2 = random.nextLong();
		return prefix + "_" + suffix1 + "_" + Long.toHexString(suffix2);
	}

	public static String getBuildDefinitionUniqueName() {
		return getUniqueName("BuildDefinition");
	}
	
	public static String getRepositoryWorkspaceUniqueName() {
		return getUniqueName("RepositoryWorkspace");
	}
	
	public static String getComponentUniqueName() {
		return getUniqueName("Component");
	}
	
	public static String getStreamUniqueName() {
		return getUniqueName("Stream");
	}
	
	public static String getProjectAreaUniqueName() {
		return getUniqueName("ProjectArea");
	}
	
	public static String getTeamAreaUniqueName() {
		return getUniqueName("TeamArea");
	}
	
	public static String getSnapshotUniqueName() {
		return getUniqueName("Snapshot");
	}
}
