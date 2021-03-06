/*******************************************************************************
 * Copyright © 2017, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import com.ibm.team.build.common.model.BuildState;
import com.ibm.team.build.common.model.BuildStatus;

public class Constants {
	public static final String TEAM_SCM_ACCEPT_PHASE_OVER = "team.scm.acceptPhaseOver"; //$NON-NLS-1$
	public static final String BUILD_PROPERTIES = "buildProperties"; //$NON-NLS-1$
	public static final String CONNECTOR_ID = "connectorId"; //$NON-NLS-1$
	public static final String PARENT_ACTIVITY_ID = "parentActivityId"; //$NON-NLS-1$
	public static final String TEAM_SCM_SNAPSHOT_OWNER = "team.scm.snapshotOwner"; //$NON-NLS-1$
	public static final String TEAM_SCM_STREAM_DATA_HASH = "team.scm.streamChangesData"; //$NON-NLS-1$
	public static final String STREAM_DATA_SNAPSHOTUUID = "buildStreamDataSnapshotUUID"; //$NON-NLS-1$
	public static final String STREAM_DATA_WORKSPACEUUID = "buildStreamDataWorkspaceUUID"; //$NON-NLS-1$
	public static final String STREAM_DATA = "buildStreamData"; //$NON-NLS-1$
	public static final String BUILD_DEFINITION_ID = "buildDefinitionId"; //$NON-NLS-1$
	public static final String DFLT_ENCODING = "UTF-8"; //$NON-NLS-1$
	public static final String PROCESS_AREA_PATH_SEPARATOR = "/"; //$NON-NLS-1$
	public static final String TEMPORARY_WORKSPACE_NAME = "rtcTempRepoWorkspaceName"; //$NON-NLS-1$
	public static final String TEMPORARY_WORKSPACE_UUID = "rtcTempRepoWorkspaceUUID"; //$NON-NLS-1$
	public static final String TEMPORARY_REPO_WORKSPACE_DATA = "temporaryRepoWorkspaceData"; //$NON-NLS-1$
	public static final String REPOSITORY_ADDRESS = "repositoryAddress"; //$NON-NLS-1$
	public static final String TEAM_SCM_PREVIOUS_SNAPSHOT_UUID="team.scm.previousSnapshotUUID"; //$NON-NLS-1$

	/**
	 * Build Definition Info constants
	 */
	
	/**
	 * Identifies post build deliver information
	 */
	public static final String PB_INFO_ID = "pbDeliver"; //$NON-NLS-1$
	
	/**
	 * Whether post build deliver is configured
	 */
	public static final String PB_CONFIGURED_KEY = "configured"; //$NON-NLS-1$
	
	/**
	 * Whether post build deliver is enabled.
	 */
	public static final String PB_ENABLED_KEY = "enabled"; //$NON-NLS-1$
	
	/**
	 * Identifies trigger policy for post build deliver.
	 */
	public static final String PB_TRIGGER_POLICY_KEY = "triggerPolicy"; //$NON-NLS-1$
	
	/**
	 * Used when the value of trigger policy is unknown
	 */
	public static final String PB_TRIGGER_POLICY_UNKNOWN_VALUE = "unknown"; //$NON-NLS-1$
	
	/**
	 * Generic constants
	 */
	public static final String TRUE = "true"; //$NON-NLS-1$
	
	public static final String FALSE = "false"; //$NON-NLS-1$
	
	/**
	 *  Identifies generic build information
	 */
	public static final String GENERIC_INFO_ID = "general"; //$NON-NLS-1$

	/**
	 * Build definition id
	 */
	public static final String GENERIC_BUILD_DEFINITION_ID_KEY = "id"; //$NON-NLS-1$
	
	/**
	 * Build result label
	 */
	static final String BUILD_RESULT_LABEL = "buildLabel"; //$NON-NLS-1$
	
	
	public static final String LOAD_POLICY_USE_LOAD_RULES = "useLoadRules"; //$NON-NLS-1$
	public static final String LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG = "useComponentLoadConfig"; //$NON-NLS-1$
	public static final String LOAD_POLICY_USE_DYNAMIC_LOAD_RULES = "useDynamicLoadRules"; //$NON-NLS-1$
	
	public static final String COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS = "loadAllComponents"; //$NON-NLS-1$
	public static final String COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS = "excludeSomeComponents"; //$NON-NLS-1$
	
	public static final String LOAD_POLICY_PROPERTY_STRING = "loadPolicy"; //$NON-NLS-1$	

    /**
     * Build property that determines whether to use load rules when loading
     * components from the build workspace or load the components based on the
     * component load configuration in the build definition.
     */
    public static final String PROPERTY_LOAD_POLICY = "team.scm.loadPolicy"; //$NON-NLS-1$

    /**
     * Build property that determines whether to load all components from the
     * workspace or exclude some of them, when loading components based on the
     * component load configuration in the build definition.
     */
    public static final String PROPERTY_COMPONENT_LOAD_CONFIG = "team.scm.componentLoadConfig"; //$NON-NLS-1$
    
    /**
     * Build property that determines the type of load i.e. full-load,
     * incremental-load or optimized-incremental-load.
     * 
     * @since 0.12.0.1 (RTC 7.0.1)
     */
    public static final String PROPERTY_LOAD_METHOD = "team.scm.loadMethod"; //$NON-NLS-1$

    /**
     * String representing full load - Deletes the directory before loading.
     * Fetches all the files from the workspace.
     * 
     * @since 0.12.0.1 (RTC 7.0.1)
     */
    public static final String LOAD_METHOD_FULL_LOAD = "fullLoad"; //$NON-NLS-1$

    /**
     * String representing incremental load - Does not delete the directory
     * before loading. Reverts local changes and deletes untracked files.
     * Fetches only modified files from the workspace.
     * 
     * @since 0.12.0.1 (RTC 7.0.1)
     */
    public static final String LOAD_METHOD_INCREMENTAL_LOAD = "incrementalLoad"; //$NON-NLS-1$

    /**
     * String representing optimized incremental load - Does not delete the
     * directory before loading. Ignores local changes and untracked files.
     * Fetches only modified files from the workspace.
     * 
     * @since 0.12.0.1 (RTC 7.0.1)
     */
    public static final String LOAD_METHOD_OPTIMIZED_INCREMENTAL_LOAD = "optimizedIncrementalLoad"; //$NON-NLS-1$
    
    /**
     * Constants related to build URL map sent by RTCScm
     */
	public static final String GENERIC_JENKINS_BUILD = "Jenkins Build"; //$NON-NLS-1$
	
	public static final String CURRENT_BUILD_FULL_URL = "currentBuildFullUrl"; //$NON-NLS-1$
	
	public static final String CURRENT_BUILD_LABEL = "currentBuildLabel"; //$NON-NLS-1$
	
	public static final String CURRENT_BUILD_URL = "currentBuildUrl"; //$NON-NLS-1$
	
	public static final String PREVIOUS_BUILD_URL = "previousBuildUrl"; //$NON-NLS-1$
	
	static final String ACCEPT_BEFORE_LOAD = "acceptBeforeLoad"; //$NON-NLS-1$
	
	static final String ADD_LINKS_TO_WORK_ITEMS = "addLinksToWorkItems"; //$NON-NLS-1$
	
    static final String METRONOME_OPTIONS_PROPERTY_NAME = "metronomeOptions";

	/**
	 * Name of the property that holds the metronome data in the result object
	 */
	public static final String METRONOME_DATA_PROPERTY_NAME = "metronomeData"; //$NON-NLS-1$
	public static final String STATISTICS_REPORT_PROPERTY_NAME = "statisticsReport"; //$NON-NLS-1$
	public static final String STATISTICS_DATA_PROPERTY_NAME = "statisticsData"; //$NON-NLS-1$
	public static final String TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME = 
			"team.build.reportStatistics"; //$NON-NLS-1$
	
	public static final String STATISTICS_REPORT_FILE_DEFAULT_PREFIX_VALUE = 
									"statistics-"; //$NON-NLS-1$
	
	public static final String STATISTICS_DATA_FILE_DEFAULT_PREFIX_VALUE =
									"statisticsData-"; //$NON-NLS-1$
	
	public static final String STATISTICS_REPORT_DEFAULT_LABEL_VALUE = 
									"Statistics Report"; //$NON-NLS-1$
	
	public static final String STATISTICS_DATA_DEFAULT_LABEL_VALUE = 
									"Statistics Data"; //$NON-NLS-1$
	
	public static final String STATISTICS_DATA_FILE_DEFAULT_SUFFIX_VALUE = ".csv"; //$NON-NLS-1$
	
	
	public static final String STATISTICS_REPORT_FILE_DEFAULT_SUFFIX_VALUE = ".log"; //$NON-NLS-1$
	

	public static final String STATISTICS_DATA_FILE_SUFFIX_PROPERTY_NAME = "statisticsDataFileSuffix"; //$NON-NLS-1$
	
	public static final String STATISTICS_REPORT_FILE_SUFFIX_PROPERTY_NAME = "statisticsReportFileSuffix"; //$NON-NLS-1$
	
	public static final String STATISTICS_DATA_LABEL_PROPERTY_NAME = 
								"statisticsDataLabel"; //$NON-NLS-1$
	
	public static final String STATISTICS_REPORT_LABEL_PROPERTY_NAME = 
								"statisticsReportLabel"; //$NON-NLS-1$
	
	public static final String STATISTICS_REPORT_FILE_PREFIX_PROPERTY_NAME = 
								"statisticsReportFilePrefix"; //$NON-NLS-1$
	
	public static final String STATISTICS_DATA_FILE_PREFIX_PROPERTY_NAME = 
								"statisticsDataFilePrefix"; //$NON-NLS-1$
	
    /**
     * Build property for "Add build result links in accepted work items".
     *
     * @since 0.12.0 (RTC 7.0)
     */
    public static final String PROPERTY_INCLUDE_LINKS_IN_WORKITEMS = "team.build.createWorkItemIncludeLinks"; //$NON-NLS-1$
    
    /**
     * Property that indicates whether the wait task timed out or not.
     * Holds a {@link boolean} value as a {@link String}  
     */
	public static final String RTCBuildUtils_TIMEDOUT = "timedout"; //$NON-NLS-1$
	
	/**
	 * Property to indicate the status of the build. Should be one 
	 * of {@link BuildStatus}
	 */
	public static final String RTCBuildUtils_BUILD_STATUS = "buildStatus"; //$NON-NLS-1$
	
	/**
	 * Property to indicate the value of the build result UUID
	 */
	public static final String RTCBuildUtils_BUILD_RESULT_UUID = "buildResultUUID"; //$NON-NLS-1$
	
	/**
	 * Property to indicate the state of the build. Should be one 
	 * of {@link BuildState}
	 */
	public static final String RTCBuildUtils_BUILD_STATE = "buildState"; //$NON-NLS-1$
	
	/**
	 * Represents a build result contribution of type "artifact". 
	 * Such contributions will appear in the "Downloads" tab of the 
	 * build result. 
	 * 
	 */
	public static final String RTCBuildUtils_ARTIFACT_TYPE_KEY = "artifact";
	
	/**
	 * Represents a build result contribution of type "log".
	 * Such contributions will appear in the "Logs" tab of the 
	 * build result.
	 */
	public static final String RTCBuildUtils_LOG_TYPE_KEY = "log";
	
	/**
	 * Key for identifying an list of lists containing details about logs 
	 * or artifacts in a map. 
	 */
	public static final String RTCBuildUtils_FILEINFOS_KEY = "fileInfos";
	
	/**
	 * Key to identify the name of a file that is downloaded from the 
	 * repository, in a map.
	 */
	public static final String RTCBuildUtils_FILENAME_KEY = "fileName";
	
	/**
	 * Key to identify the full path of the file that stores the content 
	 * downloaded from the repository, in a map.
	 */
	public static final String RTCBuildUtils_FILEPATH_KEY = "filePath";
	
	/**
	 * Key to identify the internal UUID of any helper object from the EWM 
	 * repository. Used for testing whether the contribution retrieved is same 
	 * as what is in the repository. 
	 */
	public static final String RTCBuildUtils_INTERNAL_UUID_KEY = "internalUUID";
	
	/**
	 * Key to identify snapshot name in a map
	 */
	public static final String RTCBuildUtils_SNAPSHOT_NAME_KEY = "snapshotName";
	
	/**
	 * Key to identify snapshot UUID in a map
	 */
	public static final String RTCBuildUtils_SNAPSHOT_UUID_KEY = "snapshotUUID";
	
	/**
	 * Key to idenitfy workspace UUID in a map
	 */
	public static final String RTCWorkspaceUtils_WORKSPACE_UUID_KEY = "workspaceUUID";
	
}
