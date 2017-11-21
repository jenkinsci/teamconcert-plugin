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

package com.ibm.team.build.internal.hjplugin.rtc;

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
	public static final String TEMPORARY_WORKSPACE_NAME = "rtcTempRepoWorkspaceName";
	public static final String TEMPORARY_WORKSPACE_UUID = "rtcTempRepoWorkspaceUUID";
	public static final String TEMPORARY_REPO_WORKSPACE_DATA = "temporaryRepoWorkspaceData";
	public static final String REPOSITORY_ADDRESS = "repositoryAddress";

	/**
	 * Build Definition Info constants
	 */
	
	/**
	 * Identifies post build deliver information
	 */
	public static final String PB_INFO_ID = "pbDeliver";
	
	/**
	 * Whether post build deliver is configured
	 */
	public static final String PB_CONFIGURED_KEY = "configured";
	
	/**
	 * Whether post build deliver is enabled.
	 */
	public static final String PB_ENABLED_KEY = "enabled";
	
	/**
	 * Identifies trigger policy for post build deliver.
	 */
	public static final String PB_TRIGGER_POLICY_KEY = "triggerPolicy";
	
	/**
	 * Used when the value of trigger policy is unknown
	 */
	public static final String PB_TRIGGER_POLICY_UNKNOWN_VALUE = "unknown";
	
	/**
	 * Generic constants
	 */
	public static final String TRUE = "true";
	
	public static final String FALSE = "false";
	
	/**
	 *  Identifies generic build information
	 */
	public static final String GENERIC_INFO_ID = "general";

	/**
	 * Build definition id
	 */
	public static final String GENERIC_BUILD_DEFINITION_ID_KEY = "id";
	
	/**
	 * Build result label
	 */
	static final String BUILD_RESULT_LABEL = "buildLabel";
	
	
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
}
