/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
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

    /**
     * URI for checking compatibility of the RTC server. We need 5.0 or higher in order to
     * use the Rest services.
     */
    public static final String URI_COMPATIBILITY_CHECK = "versionCompatibility?clientVersion=5.0"; //$NON-NLS-1$

    /**
     * URI for searching for workspaces (uses ITeamModelledRestService)
     * 
     * Finds exact matches of a particular workspace name:
     * http://localhost:9080/service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchWorkspaces?workspaceNameKind=exact&workspaceName=myWorkspace
     */
    public static final String URI_SEARCH_WORKSPACES = "service/com.ibm.team.scm.common.rest.IScmRichClientRestService/searchWorkspaces?workspaceNameKind=exact&workspaceName="; //$NON-NLS-1$
    
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

}
