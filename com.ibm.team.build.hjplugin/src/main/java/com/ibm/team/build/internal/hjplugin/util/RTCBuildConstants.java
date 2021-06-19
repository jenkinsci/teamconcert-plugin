/*******************************************************************************
 * Copyright (c) 2014, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.util;

/**
 * Common constants used from RTC Build.
 */
public interface RTCBuildConstants {

	public static final String SEPARATOR = "/";
    /**
     * Indicates the build has not been started. Also known as PENDING.
     */
    public static final String BUILD_STATE_NOT_STARTED = "NOT_STARTED"; //$NON-NLS-1$

    /**
     * Indicates that the request for the build has been canceled.
     */
    public static final String BUILD_STATE_CANCELED = "CANCELED"; //$NON-NLS-1$

    /**
     * Indicates that the build is currently in progress.
     */
    public static final String BUILD_STATE_IN_PROGRESS = "IN_PROGRESS"; //$NON-NLS-1$

    /**
     * Indicates that the build was not completed normally. Also known as ABANDONED.
     */
    public static final String BUILD_STATE_INCOMPLETE = "INCOMPLETE"; //$NON-NLS-1$

    /**
     * Indicates that the build was completed normally (but may have failures).
     */
    public static final String BUILD_STATE_COMPLETED = "COMPLETED"; //$NON-NLS-1$
    
    // Rest API constants from BuildRest

    /**
     * URI for build definitions. For example:
     * http://localhost:9080/jazz/resource/virtual/build/definitions
     */
    public static final String URI_DEFINITIONS = "resource/virtual/build/definitions"; //$NON-NLS-1$

    /**
     * Query parameter for specifying the build definition id, such as
     * "integration".
     */
    public static final String QUERY_PARAM_BUILD_DEFINITION = "definition"; //$NON-NLS-1$
    
    /**
     * URI for a build definition. For example:
     * http://localhost:9080/jazz/resource/virtual/build/definition/_yamC4NWiEdylmcAI5HeTUQ
     */
    public static final String URI_DEFINITION = "resource/virtual/build/definition"; //$NON-NLS-1$
    
    /**
     * URI for a build definition's supporting build engines. For example:
     * http://localhost:9080/jazz/resource/virtual/build/definition/_EOCVoBizEd6zruFy5LB2ow/supportingengines
     */
    public static final String URI_SEGMENT_SUPPORTING_ENGINES = "supportingengines"; //$NON-NLS-1$

    /**
     * URI for a build result. For example:
     * http://localhost:9080/jazz/resource/virtual/build/result/_ccvC4NWiEdylmcAI5HeTUQ
     */
    public static final String URI_RESULT = "resource/virtual/build/result/"; //$NON-NLS-1$

    /**
     * URI segment for the build state. For example:
     * http://localhost:9080/jazz/resource/virtual/build/result/_ccvC4NWiEdylmcAI5HeTUQ/buildState
     * The result of a GET is the BuildStateDTO. 
     * The PUT with a BuildStateDTO will result in the build state being updated if a state is set.
     * The status will be updated only if status is set & the new status is more severe than the current status.
     */
    public static final String URI_SEGMENT_BUILD_STATE = "buildstate"; //$NON-NLS-1$

    /**
     * URI for incoming changes. For example:
     * 
     * Any incoming changes based on the source control participant logic of the build definition:
     * http://localhost:9080/jazz/resource/virtual/build/incomingchanges?definitionUUID=_yamC4NWiEdylmcAI5HeTUQ
     * 
     * Any incoming changes based on the flow table of the workspace specified:
     * http://localhost:9080/jazz/resource/virtual/build/incomingchanges?workspaceUUID=_nhsC4NWiEdylmcAI5HeTUQ
     * 
     * Result is of type {@link IncomingChangesResponseDTO} a query can be made for only 1 build definition or workspace
     */
    public static final String URI_INCOMING_CHANGES = "resource/virtual/build/incomingchanges"; //$NON-NLS-1$
    
    public static final String URI_SEGMENT_RESURCE = "resource/virtual";
    
    
    public static final String URI_SEGMENT_RESULT = "build/result";

    public static final String URI_SEGMENT_RESULT_PARTICIPANT = "participant"; //$NON-NLS-1$

    /**
     * URI for checking compatibility of the RTC server. We need 6.0 or higher in order to
     * use the Rest services. Servers 5.0.2 or lesser will not be supported
     */
    public static final String URI_COMPATIBILITY_CHECK = "versionCompatibility?clientVersion=6.0"; //$NON-NLS-1$
       
    /**
     * URI for checking compatibility of the RTC server version 6.0.4
     */
    public static final String URI_COMPATIBILITY_CHECK_604 = "versionCompatibility?clientVersion=6.0.4"; //$NON-NLS-1$
    
    /**
     * URI for checking compatibility of the RTC server version 6.0.4 M5
     */
    public static final String URI_COMPATIBILITY_CHECK_604_M5 = "versionCompatibility?clientVersion=6.0.4M5"; //$NON-NLS-1$

    public static final String MINIMUM_SERVER_VERSION = "6.0";
    
    public static final String MINIMUM_SERVER_VERSION_FOR_PBDELIVER = "6.0.4";
    
    public static final String URI_COMPATIBILITY_CHECK_WITHOUT_VERSION = "versionCompatibility?clientVersion=";

    /**
     * URI for searching for workspaces (uses ITeamModelledRestService)
     * 
     * Finds exact matches of a particular workspace name:
     * http://localhost:9080/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchWorkspaces?workspaceNameKind=exact&workspaceName=myWorkspace
     */
	public static final String URI_SEARCH_WORKSPACES = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchWorkspaces?workspaceNameKind=exact&workspaceName="; //$NON-NLS-1$
	
	/**
	 * URI to fetch components by item ID. You can specify only one component id
	 * with this URI. If you want to batch fetch components by id then you need
	 * to add multiple componentItemIds parameter.
	 * 
	 * For example:
	 * 
	 * <pre>
	 * 	https://localhost:9443/jazz/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/components2?componentItemIds=_ITkVUNlIEeWHquPepBzvOQ
	 * </pre>
	 */
	public static final String URI_GET_COMPONENTS = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/components2?componentItemIds="; //$NON-NLS-1$
	
	/**
	 * URI to search components by name. This URI is configured to perform a
	 * case sensitive search of components by the exact name specified in
	 * namePattern.
	 * 
	 * For example:
	 * 
	 * <pre>
	 * https://localhost:9443/jazz/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchComponents2?exact=true&namePattern=JUnit
	 * </pre>
	 * 
	 */
	public static final String URI_SEARCH_COMPONENTS = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchComponents2?exact=true&namePattern="; //$NON-NLS-1$
	
	/**
	 * URI to fetch a versionable by path in the context of the given workspace
	 * and component.
	 * 
	 * For example
	 * 
	 * <pre>
	 * https://localhost:9443/jazz/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/versionable?
	 * 		contextItemNamespace=com.ibm.team.scm&contextItemType=Workspace&contextItemId=_IY9ysNlIEeWHquPepBzvOQ&componentItemId=_ITkVUNlIEeWHquPepBzvOQ&path=/
	 * </pre>
	 */
	public static final String URI_GET_VERSIONABLE_BY_PATH = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/versionable?contextItemNamespace=com.ibm.team.scm&contextItemType=Workspace&path="; //$NON-NLS-1$
	
	/**
	 * URI to fetch a versionable by item ID in the context of the given
	 * workspace and component.
	 * 
	 * For example
	 * 
	 * <pre>
	 * https://localhost:9443/jazz/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/versionable?
	 * 		contextItemNamespace=com.ibm.team.scm&contextItemType=Workspace&contextItemId=_d6NJwNlMEeWHquPepBzvOQ&componentItemId=_xYEzgdlTEeWHquPepBzvOQ
	 * 		&itemId=_oxfJMNn4EeW6lec1vN4vpA&itemType=FileItem&itemNamespace=com.ibm.team.filesystem
	 * </pre>
	 */
	public static final String URI_GET_VERSIONABLE_BY_ID = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/versionable?contextItemNamespace=com.ibm.team.scm&contextItemType=Workspace&itemType=FileItem&itemNamespace=com.ibm.team.filesystem&itemId="; //$NON-NLS-1$

    
    /**
     * URI for searching for streams (uses ITeamModelledRestService)
     * 
     * Finds exact matches of a particular stream name:
     * http://localhost:9080/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchWorkspaces?workspaceKind=streams&workspaceNameKind=exact&workspaceName=myWorkspace
     */
    public static final String URI_SEARCH_STREAMS = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchWorkspaces?workspaceKind=streams&workspaceNameKind=exact&workspaceName="; //$NON-NLS-1$  
    
    // Configuration element constants
    /**
     * The engine element ID.  Constant from HudsonConfigurationElement
     */
    public static final String HJ_ENGINE_ELEMENT_ID = "com.ibm.rational.connector.hudson.engine"; //$NON-NLS-1$

	/**
	 * The definition's element ID. Constant from HudsonConfigurationElement (not available in the toolkit)
	 */
	public static final String HJ_ELEMENT_ID = "com.ibm.rational.connector.hudson";  //$NON-NLS-1$

	/**
	 * The definition's element ID. Constant from IJazzScmConfigurationElement
	 */
	public static final String SCM_ELEMENT_ID = "com.ibm.team.build.jazzscm";
    
    public static final String URI_SEGMENT_CONTRIBUTION = "contribution"; //$NON-NLS-1$


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
	 * Whether post build deliver is enabled
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
	 * Key for ignoring trigger policy when invoking post build deliver participant
	 */
	public static final String PB_DELIVER_IGNORE_TRIGGER_POLICY_KEY = "ignoreTriggerPolicy";
	
	/**
	 * Key for specifying the build result item id 
	 */
	public static final String PB_DELIVER_BUILD_RESULT_ITEMID_KEY = "buildResultItemId";
	
	/**
	 * Key for specifying the the participant to be invoked
	 */
	public static final String PB_DELIVER_PARTICIPANT_KEY = "participant";
	
    /** 
     * Id of Auto Deliver post build participant on the server
     */
    public static final String POST_BUILD_DELIVER_PARTICIPANT = "com.ibm.team.build.service.AutoDeliverPostBuildParticipant"; //$NON-NLS-1$
    
    /**
     * Key for finding whether post build participant actually delivered code
     */
    public static final String AUTO_DELIVER_POST_BUILD_PARTICIPANT_DELIVERED = "AutoDeliverPostBuildParticipant.delivered"; //$NON-NLS-1$
    
    /**
     * Textual summary of the post build deliver
     */
    public static final String AUTO_DELIVER_POST_BUILD_PARTICIPANT_SUMMARY = "AutoDeliverPostBuildParticipant.summary"; //$NON-NLS-1$
    
	
	/**
	 * Generic constants
	 */
	public static final String TRUE = "true";
	
	public static final String FALSE = "false";
	
	/**
	 * Extended contribution property related constants
	 */
	String EXTENDED_CONTRIB_PROPERTY_NAME = "name";
	
	String EXTENDED_CONTRIB_PROPERTY_VALUE = "value";
	
	String EXTENDED_CONTRIB_PROPERTIES_MEMBER = "extendedContributionProperties";
	
	/**
	 *  Identifies generic build information
	 */
	public static final String GENERIC_INFO_ID = "general";

	/**
	 * Build definition id
	 */
	public static final String GENERIC_BUILD_DEFINITION_ID_KEY = "id";
	
	/**
	 * Build Result label
	 */
	public static final Object BUILD_RESULT_LABEL = "buildLabel";
	
	
	/**
	 * Key to identify build result state in a map
	 */
	public static final String BUILD_STATE_KEY = "buildState";
	
	/**
	 * Key to identify build result status in a map
	 */
	public static final String BUILD_STATUS_KEY = "buildStatus";
	
	
	/**
	 * Key to identify snapshot name in a map
	 */
	public static final String SNAPSHOT_NAME_KEY = "snapshotName";
	
	/**
	 * Key to identify snapshot UUID in a map
	 */
	public static final String SNAPSHOT_UUID_KEY = "snapshotUUID";
	
	
	/**
	 * Contribution types
	 * 
	 */
	
	/**
	 * Represents a build result contribution of type "artifact". 
	 * Such contributions will appear in the "Downloads" tab of the 
	 * build result. 
	 * 
	 */
	public static final String ARTIFACT_TYPE = "artifact";
	
	/**
	 * Represents a build result contribution of type "log".
	 * Such contributions will appear in the "Logs" tab of the 
	 * build result.
	 */
	public static final String LOG_TYPE = "log";
}
