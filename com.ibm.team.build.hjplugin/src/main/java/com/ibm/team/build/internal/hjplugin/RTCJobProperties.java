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

package com.ibm.team.build.internal.hjplugin;

public class RTCJobProperties {
	public static final String BUILD_RESULT_UUID = "buildResultUUID"; //$NON-NLS-1$
	public static final String USE_DYNAMIC_LOAD_RULE = "com.ibm.team.build.useExtension"; //$NON-NLS-1$
	public static final String RTC_BUILD_SNAPSHOT = "rtcBuildSnapshot"; //$NON-NLS-1$ 
	public static final String TEMPORARY_WORKSPACE_NAME = "rtcTempRepoWorkspaceName"; //$NON-NLS-1$
	public static final String TEMPORARY_WORKSPACE_UUID = "rtcTempRepoWorkspaceUUID"; //$NON-NLS-1$
	public static final String TEMPORARY_REPO_WORKSPACE_DATA = "temporaryRepoWorkspaceData"; //$NON-NLS-1$
	public static final String DEBUG_PROPERTY = "com.ibm.team.build.debug"; //$NON-NLS-1$
	public static final String POST_BUILD_DELIVER_HANDLED = "rtcPostBuildDeliverHandled"; //$NON-NLS-1$
	public static final String RTC_BUILD_RESULT_UUID = "RTCBuildResultUUID"; //$NON-NLS-1$
	public static final String STATISTICS_REPORT_PROPERTY_NAME = "statisticsReport"; //$NON-NLS-1$
	public static final String STATISTICS_DATA_PROPERTY_NAME = "statisticsData"; //$NON-NLS-1$
    public static final String TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME = "team.build.reportStatistics"; //$NON-NLS-1$

    public static final String BUILD_WORKSPACE_TYPE = "buildWorkspace"; //$NON-NLS-1$
    public static final String BUILD_DEFINITION_TYPE = "buildDefinition"; //$NON-NLS-1$
	public static final String WAIT_BUILD_COMMAND = "waitForBuild";
	public static final String REQUEST_BUILD_COMMAND = "requestBuild";

}
