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
package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.LinkedHashMap;
import java.util.Map;

class ArtifactLogResourceContainer {
	// To preserve iteration order.
	static Map<String, String[][]> artifactInfo = new LinkedHashMap<String, String[][]>();
	static Map<String, String[][]> logInfo = new LinkedHashMap<String, String[][]>();
	
	static {
		artifactInfo.put("comp1", new String[][] { 
					{"artifact-10-90.txt", "artifact-10-90 artifact", "artifact-10-90.txt"},
					{"artifact-11-91.txt", "artifact-11-91 artifact", "artifact-11-91.txt"},
					{"artifact-12-92.txt", "artifact-12-92 artifact", "artifact-12-92.txt"}});
		artifactInfo.put("comp2", new String[][] {
					{"artifact-13-93.txt", "artifact-13-93 artifact", "artifact-13-93.txt"},
					{"artifact-14-94.txt", "artifact-14-94 artifact", "artifact-14-94.txt"},
					{"artifact-15-95.txt", "artifact-15-95 artifact", "artifact-15-95.txt"}
					});
		
		artifactInfo.put("comp3", new String[][] {
			{"artifact-16-96.txt", "artifact-16-96 artifact", "artifact-16-96.txt"},
			{"artifact-17-97.txt", "artifact-17-97 artifact", "artifact-17-97.txt"},
			{"artifact-18-98.txt", "artifact-18-98 artifact", "artifact-18-98.txt"},
			{"artifact-19-99.txt", "artifact-19-99 artifact", "artifact-19-99.txt"}});
		
		artifactInfo.put("comp4", new String[][] {
			{"artifact-12-92-dup.txt", "artifact-12-92 artifact dup", "artifact-12-92.txt"},
			{"artifact-15-95-dup.txt", "artifact-15-95 artifact dup", "artifact-15-95.txt"},
			{"artifact-18-98-dup.txt", "artifact-18-98 artifact dup", "artifact-18-98.txt"},
			{"artifact-19-99-dup.txt", "artifact-19-99 artifact dup", "artifact-19-99.txt"},
			{"artifact-19-99-dup-1.txt", "artifact-19-99 artifact dup 1", "artifact-19-99.txt"}});
		
		artifactInfo.put("",  new String[][] {
			{"artifact-100-900.txt", "artifact-100-900 artifact", "artifact-100-900.txt"},
			{"artifact-101-901.txt", "artifact-101-901 artifact", "artifact-101-901.txt"},
			{"artifact-102-902.txt", "artifact-102-902 artifact", "artifact-102-902.txt"},
			{"artifact-103-903.txt", "artifact-103-903 artifact", "artifact-103-903.txt"},
			{"artifact-104-904.txt", "artifact-104-904 artifact", "artifact-104-904.txt"}});
		
		logInfo.put("comp1", new String[][] { 
			{"log-10-90.txt", "log-10-90 log", "log-10-90.txt"},
			{"log-11-91.txt", "log-11-91 log", "log-11-91.txt"},
			{"log-12-92.txt", "log-12-92 log", "log-12-92.txt"}});
		
		logInfo.put("comp2", new String[][] {
			{"log-13-93.txt", "log-13-93 log", "log-13-93.txt"},
			{"log-14-94.txt", "log-14-94 log", "log-14-94.txt"},
			{"log-15-95.txt", "log-15-95 log", "log-15-95.txt"}
			});

		logInfo.put("comp3", new String[][] {
			{"log-16-96.txt", "log-16-96 log", "log-16-96.txt"},
			{"log-17-97.txt", "log-17-97 log", "log-17-97.txt"},
			{"log-18-98.txt", "log-18-98 log", "log-18-98.txt"},
			{"log-19-99.txt", "log-19-99 log", "log-19-99.txt"}});
		
		logInfo.put("comp4", new String[][] {
			{"log-12-92-dup.txt", "log-12-92 log dup", "log-12-92.txt"},
			{"log-15-95-dup.txt", "log-15-95 log dup", "log-15-95.txt"},
			{"log-18-98-dup.txt", "log-18-98 log dup", "log-18-98.txt"},
			{"log-19-99-dup.txt", "log-19-99 log dup", "log-19-99.txt"},
			{"log-19-99-dup-1.txt", "log-19-99 log dup 1", "log-19-99.txt"}});
		
		logInfo.put("",  new String[][] {
			{"log-100-900.txt", "log-100-900 log", "log-100-900.txt"},
			{"log-101-901.txt", "log-101-901 log", "log-101-901.txt"},
			{"log-102-902.txt", "log-102-902 log", "log-102-902.txt"},
			{"log-103-903.txt", "log-103-903 log", "log-103-903.txt"},
			{"log-104-904.txt", "log-104-904 log", "log-104-904.txt"}});
	}
	
}
