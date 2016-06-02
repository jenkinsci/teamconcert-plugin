/*******************************************************************************
 * Copyright (c) 2014, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.util;

import hudson.Util;
import hudson.model.Result;
import hudson.model.TaskListener;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.logging.Logger;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.client.protocol.HttpClientContext;
import org.jvnet.localizer.LocaleProvider;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.util.HttpUtils.GetResult;

/**
 * A facade in front of the facade in the -rtc plugin that uses the toolkit to 
 * communicate with RTC. Allows us to choose between Rest API and the toolkit
 * For use on the master
 */
public class RTCFacadeFacade {
	private static final String SLASH = "/"; //$NON-NLS-1$
	private static final String BUILD_STATUS = "buildStatus"; //$NON-NLS-1$
	private static final String BUILD_STATE = "buildState"; //$NON-NLS-1$
	private static final Logger LOGGER = Logger.getLogger(RTCFacadeFacade.class.getName());

	// JSON fields for #testConnection
	private static final String JSON_PROP_COMPATIBLE = "compatible"; //$NON-NLS-1$
	private static final String JSON_PROP_IS_JTS = "isJTS"; //$NON-NLS-1$
	private static final String JSON_PROP_SERVER_VERSION = "serverVersion"; //$NON-NLS-1$
	private static final String JSON_PROP_MESSAGE = "message"; //$NON-NLS-1$
	private static final String JSON_PROP_URI = "uri"; //$NON-NLS-1$

	// JSON fields for incomingChanges
    private static final String JSON_PROP_CHANGES = "changes"; //$NON-NLS-1$
    
    // JSON fields for searchWorkspaces
	private static final String JSON_PROP_SOAPENV_BODY = "soapenv:Body"; //$NON-NLS-1$
	private static final String JSON_PROP_RESPONSE = "response"; //$NON-NLS-1$
	private static final String JSON_PROP_RETURN_VALUE = "returnValue"; //$NON-NLS-1$
	private static final String JSON_PROP_VALUE = "value"; //$NON-NLS-1$
	private static final String JSON_PROP_WORKSPACES = "workspaces"; //$NON-NLS-1$

	// JSON fields for validating build definition
	private static final String JSON_PROP_BUILD_ENGINE = "buildEngine"; //$NON-NLS-1$
	private static final String JSON_PROP_ENGINE_STATUS_RECORDS = "engineStatusRecords"; //$NON-NLS-1$
	private static final String JSON_PROP_ITEM_ID = "itemId"; //$NON-NLS-1$
	private static final String JSON_PROP_ELEMENT_ID = "elementId"; //$NON-NLS-1$
	private static final String JSON_PROP_CONFIGURATION_ELEMENTS = "configurationElements"; //$NON-NLS-1$
	
	private static final String JSON_PROP_COMPONENTS = "components"; //$NON-NLS-1$
	private static final String JSON_PROP_NAME = "name"; //$NON-NLS-1$
	private static final String JSON_PROP_DATE_MODIFIED = "dateModified"; //$NON-NLS-1$
	private static final String JSON_PROP_VERSIONABLE = "versionable"; //$NON-NLS-1$
	private static final String JSON_PROP_TYPE = "com.ibm.team.repository.typeName"; //$NON-NLS-1$
	
	private static final String FILE_ITEM_VALUE = "com.ibm.team.filesystem.FileItem";//$NON-NLS-1$
	
	/**
	 * The search components by name service returns a maximum of 512 components
	 * ordered by their modification date. To get the rest of the matching
	 * components, if any, you need to record the modification date of the last
	 * component and send it across in the service invocation.
	 */
	public static final String SEARCH_COMPONENTS_REQUEST_PARAM_LAST_COMP_MODIFIED_DATE = "&lastComponentModifiedDate="; //$NON-NLS-1$
	
	/**
	 * Maximum number of components returned by the search components by name service.
	 */
	public static final int MAX_COMP_SEARCH_RESULTS = 512;

	
	private static final String GET_VERSIONABLE_REQUEST_PARAM_CONTEXT_ITEM_ID = "&contextItemId="; //$NON-NLS-1$
	private static final String GET_VERSIONABLE_REQUEST_PARAM_COMPONENT_ITEM_ID = "&componentItemId="; //$NON-NLS-1$
	
	
	// The following date formats are copied from
	// com.ibm.team.repository.common.utils.DateUtils.
	
    private static final SimpleDateFormat _RFC3339DateFormatInternal;
    private static final SimpleDateFormat _RFC3339DateNoMillisFormatInternal;
    private static final SimpleTimeZone _gmtTimeZoneInternal;

	static {
		
        _gmtTimeZoneInternal = new SimpleTimeZone(0, "GMT"); //$NON-NLS-1$

        _RFC3339DateNoMillisFormatInternal = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH); //$NON-NLS-1$
        _RFC3339DateNoMillisFormatInternal.setTimeZone(_gmtTimeZoneInternal);

        _RFC3339DateFormatInternal = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH); //$NON-NLS-1$
        _RFC3339DateFormatInternal.setTimeZone(_gmtTimeZoneInternal);
	}



	/**
	 * Checks to see if there are incoming changes for the RTC build workspace (meaning a build is needed).
	 * Use the rest service to ask the server
	 * (avoid the toolkit and we have a build definition configured).
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name (id) of the build definition that describes the build workspace.
	 * May be <code>null</code> if a buildWorkspace is supplied. Only one of buildWorkspace/buildDefinition
	 * should be supplied.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if a
	 * buildDefinition is supplied. Only one of buildWorkspace/buildDefinition
	 * should be supplied.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @return Returns <code>true</code> if there are changes to the build workspace;
	 * <code>false</code> otherwise
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public static Boolean incomingChangesUsingBuildDefinitionWithREST(String buildToolkitPath, String serverURI, 
			String userId, String password,
			int timeout, String buildDefinitionId,
			String workspaceName, TaskListener listener) throws Exception {
		
		// Attempt to use Rest api if this is for a build definition
		listener.getLogger().println(Messages.RTCFacadeFacade_check_incoming_with_rest());

		String uri = RTCBuildConstants.URI_INCOMING_CHANGES + "?"; //$NON-NLS-1$
		uri += RTCBuildConstants.QUERY_PARAM_BUILD_DEFINITION + "=" + Util.encode(buildDefinitionId); //$NON-NLS-1$
		JSON json = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, listener).getJson();
		Boolean changes = JSONHelper.getBoolean(json, JSON_PROP_CHANGES);
		if (changes != null) {
			return changes;
    	} else {
    		// problems reading response from RTC;
			LOGGER.finer("Unexpected response to " + uri + " received: " + (json == null ? "null" : json.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException(Messages.RTCFacadeFacade_unexpected_incoming_response(uri, (json == null ? "null" : json.getClass())));  //$NON-NLS-1$
		}

	}
	
	/**
	 * Checks to see if there are incoming changes for the RTC build workspace (meaning a build is needed).
	 * Use the facade based on the configuration info.
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name (id) of the build definition that describes the build workspace.
	 * May be <code>null</code> if a buildWorkspace is supplied. Only one of buildWorkspace/buildDefinition
	 * should be supplied.
	 * @param buildWorkspace The name of the RTC build workspace. May be <code>null</code> if a
	 * buildDefinition is supplied. Only one of buildWorkspace/buildDefinition
	 * should be supplied.
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @return Returns <code>true</code> if there are changes to the build workspace;
	 * <code>false</code> otherwise
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public static BigInteger incomingChangesUsingBuildToolkit(String buildToolkitPath, String serverURI, 
			String userId, String password,
			int timeout, boolean useBuildDefinition, String buildDefinitionId,
			String workspaceName, String streamName, String streamChangesData, TaskListener listener, boolean ignoreOutgoingFromBuildWorkspace) throws Exception {
		listener.getLogger().println(Messages.RTCFacadeFacade_check_incoming_with_toolkit());
		
		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, listener.getLogger());
		BigInteger changesIncoming = null;;
		if (streamName != null) {
			changesIncoming = (BigInteger) facade.invoke("computeIncomingChangesForStream", // $NON-NLS-1$
					new Class[] { String.class, String.class,
							String.class, int.class,
							String.class, String.class, Object.class, Locale.class},
					serverURI,
					userId,
					password,
					timeout,
					streamName,
					streamChangesData,
					listener,
					LocaleProvider.getLocale());
		}
		else {
			changesIncoming = (BigInteger) facade.invoke(
				"incomingChanges", //$NON-NLS-1$
				new Class[] { String.class, // serverURI
						String.class, // userId
						String.class, // password
						int.class, // timeout
						String.class, // buildDefinition
						String.class, // buildWorkspace
						Object.class, // listener
						Locale.class, // clientLocale
						boolean.class}, // ignoreOutgoingFromBuildWorkspace
				serverURI, userId, password,
				timeout, 
				(useBuildDefinition ? buildDefinitionId : ""), //$NON-NLS-1$
				(useBuildDefinition ? "" : workspaceName), //$NON-NLS-1$
				listener, LocaleProvider.getLocale(), ignoreOutgoingFromBuildWorkspace);
		}
		return changesIncoming;

	}
	/**
	 * Logs into the repository to test the connection. Essentially exercises the configuration parameters supplied.
	 * Either the rest service or the build toolkit will be used to test the connection.
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testConnection(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit)
			throws Exception {
		
		// Attempt to use Rest api if this is for a build definition
		if (avoidUsingToolkit) {
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;
			try {
				
				// Validate that the server version is sufficient 
				JSON json = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();
				errorMessage = ensureCompatability(json);
				
				if (errorMessage == null) {
					// Make sure the credentials are good
					HttpUtils.validateCredentials(serverURI, userId, password, timeout);
				}
			} catch (InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			}
			return errorMessage;
		} else {
			
			// use the toolkit to test the connection
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String) facade.invoke("testConnection", new Class[] { //$NON-NLS-1$
					String.class, // serverURI
					String.class, // userId
					String.class, // password
					int.class, // timeout
					Locale.class}, // clientLocale
					serverURI, userId, password,
					timeout, 
					LocaleProvider.getLocale());
			return errorMessage;
		}
	}
	
	private static String ensureCompatability(JSON compatibilityCheckResult) throws GeneralSecurityException {
		String errorMessage = null;

		// Validate that the server version is sufficient 
		Boolean isJTS = JSONHelper.getBoolean(compatibilityCheckResult, JSON_PROP_IS_JTS);

		// isJTS might be null in 3.0 RC0 and earlier, because earlier versions of
		// the VersionCompatibilityRestService did not include this functionality.
		// If null, don't throw  an error in this block, but instead fall through
		// handle as a version mismatch below
		if ((isJTS != null) && (isJTS == true)) {
			errorMessage = Messages.RTCFacadeFacade_client_not_allowed_to_connect_to_JTS();
		}

		Boolean compatible = JSONHelper.getBoolean(compatibilityCheckResult, JSON_PROP_COMPATIBLE);
		if (compatible == null) {
			errorMessage = Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service();
		} else if (compatible == false) {
			String upgradeURI = JSONHelper.getString(compatibilityCheckResult, JSON_PROP_URI);
			String upgradeMessage = JSONHelper.getString(compatibilityCheckResult, JSON_PROP_MESSAGE);
			String serverVersion = JSONHelper.getString(compatibilityCheckResult, JSON_PROP_SERVER_VERSION);
			if ((upgradeURI == null) || (upgradeMessage == null) || (serverVersion == null)) {
				errorMessage = Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service();
			} else {
				errorMessage = Messages.RTCFacadeFacade_incompatible2(serverVersion);
			}
		}
		return errorMessage;
	}

	/**
	 * Logs into the repository to test the connection and validates the RTC build definition is valid for use.
	 * password first.
	 * The rest service or the build toolkit will be used
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildDefinition The name of the RTC build definition
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testBuildDefinition(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, String buildDefinitionId)
			throws Exception {
		LOGGER.finest("RTCFacadeFacade.testBuildDefinition : Enter");
		if (avoidUsingToolkit) {
			// Make sure that the server is compatible. Rest api on older releases ignores
			// the query parameter which would mean it finding more than returning possibly > 1 element
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;
			try {
				
				// Validate that the server version is sufficient 
				GetResult getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null);
				errorMessage = ensureCompatability(getResult.getJson());
				
				if (errorMessage != null) {
					return errorMessage;
				}

				// validate the build definition exists
				uri = RTCBuildConstants.URI_DEFINITIONS + "?" //$NON-NLS-1$
						+ RTCBuildConstants.QUERY_PARAM_BUILD_DEFINITION + "=" //$NON-NLS-1$
						+ Util.encode(buildDefinitionId);
				
				// Since we are going to make more than one Rest call, hang onto the context so that
				// login only needs to be done once.
				HttpClientContext context = getResult.getHttpContext();
				
				getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, context, null); 
				JSON json = getResult.getJson();
	
				if (json instanceof JSONArray) {
					JSONArray definitions = (JSONArray) json;
					if (definitions.size() == 0) {
						// not found
						return Messages.RTCFacadeFacade_definition_not_found(buildDefinitionId);
					} else {
						// validate the build definition is a hudson/jenkins build definition
						JSONObject buildDefinition = definitions.getJSONObject(0);
						boolean found = JSONHelper.searchJSONArray(buildDefinition, JSON_PROP_CONFIGURATION_ELEMENTS, JSON_PROP_ELEMENT_ID, RTCBuildConstants.HJ_ELEMENT_ID);
						if (!found) {
							return Messages.RTCFacadeFacade_build_definition_missing_hudson_config();
						}
						// validate the build definition as a Jazz Source Control option
						found = JSONHelper.searchJSONArray(buildDefinition, JSON_PROP_CONFIGURATION_ELEMENTS, JSON_PROP_ELEMENT_ID, RTCBuildConstants.SCM_ELEMENT_ID);
						if (!found) {
							return Messages.RTCFacadeFacade_build_definition_missing_jazz_scm_config();
						}
							
						// get the supporting build engines for the definition
						uri = RTCBuildConstants.URI_DEFINITION + SLASH + buildDefinition.getString(JSON_PROP_ITEM_ID) + SLASH + RTCBuildConstants.URI_SEGMENT_SUPPORTING_ENGINES;
						getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, context, null);
						context = getResult.getHttpContext();
						json = getResult.getJson();
						JSONArray engineStatusRecords = JSONHelper.getJSONArray(json, JSON_PROP_ENGINE_STATUS_RECORDS);
						if (engineStatusRecords != null && engineStatusRecords.size() > 0) {
							JSONObject buildEngineStatusRecord = engineStatusRecords.getJSONObject(0);
							JSONObject buildEngine = buildEngineStatusRecord.getJSONObject(JSON_PROP_BUILD_ENGINE);
							// validate the build definition has a hudson/jenkins build engine
							found = JSONHelper.searchJSONArray(buildEngine, JSON_PROP_CONFIGURATION_ELEMENTS, JSON_PROP_ELEMENT_ID, RTCBuildConstants.HJ_ENGINE_ELEMENT_ID);
							if (!found) {
								return Messages.RTCFacadeFacade_build_definition_missing_build_engine_hudson_config();
							}
							
							// everything is valid
							return null;
						}
						return Messages.RTCFacadeFacade_build_definition_missing_build_engine();
					}
		    	} else {
	    			LOGGER.finer("Unexpected response to " + uri + " received: " + (json == null ? "null" : json.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					throw new IOException(Messages.RTCFacadeFacade_unexpected_validate_build_definition_response(uri, (json == null ? "null" : json.toString(4)))); //$NON-NLS-1$
				}
			} catch (InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			}
			return errorMessage;
		} else {
	
			// Use the toolkit to validate the build definition
			LOGGER.finer("Testing Build Definition using the toolkit"); //$NON-NLS-1$
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String) facade.invoke(RTCFacadeWrapper.TEST_BUILD_DEFINITION, //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildDefinition
							Locale.class}, // clientLocale
					serverURI, userId, password, timeout,
					buildDefinitionId, LocaleProvider.getLocale());
			return errorMessage;
		}
	}

	/**
	 * validate the name of the workspace is a valid workspace for build purposes
	 * Use either the rest service or the build toolkit
	 * 
 	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildWorkspace The name of the RTC build workspace
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testBuildWorkspace(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, String buildWorkspace) throws Exception {
		LOGGER.finest("RTCFacadeFacade.testBuildWorkspace : Enter");
		// Ensure that workspace is non null and not empty
		buildWorkspace = Util.fixEmptyAndTrim(buildWorkspace);
		if (buildWorkspace == null) {
			return Messages.RTCFacadeFacade_invalid_workspace_name();
		}
		// ensure only 1 workspace is found.
		if (avoidUsingToolkit) {
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;

			try {
				// Validate that the server version is sufficient 
				GetResult getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null);
				errorMessage = ensureCompatability(getResult.getJson());
				
				if (errorMessage != null) {
					return errorMessage;
				}
				validateWorkspaceExists(serverURI, userId, password, timeout, getResult.getHttpContext(), buildWorkspace);
			} catch (InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			} catch (IllegalArgumentException e) {
				errorMessage =e.getMessage();
			}
			return errorMessage;
		} else {
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String) facade.invoke(RTCFacadeWrapper.TEST_BUILD_WORKSPACE,
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildWorkspace
							Locale.class}, // clientLocale
					serverURI, userId, password, timeout,
					buildWorkspace, LocaleProvider.getLocale());
			return errorMessage;
		}
	}
	
	public static String testBuildStream(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, String buildStream) throws Exception {
		LOGGER.finest("RTCFacadeFacade.testBuildStream : Enter");
		
		buildStream = Util.fixEmptyAndTrim(buildStream);
		if (buildStream == null) {
			return Messages.RTCFacadeFacade_invalid_stream_name();
		}
		
		// ensure only 1 stream is found.
		if (avoidUsingToolkit) { 
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;
			
			try {
				GetResult result =   HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null);
				errorMessage = ensureCompatability(result.getJson());
				
				if (errorMessage != null) {
					return errorMessage;
				}
				
				uri = RTCBuildConstants.URI_SEARCH_STREAMS + Util.encode(buildStream);
				JSON jsonResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, result.getHttpContext(), null).getJson();
				
				if (jsonResult instanceof JSONObject) {
					try {
						JSONObject value = getValueObjectFromJson((JSONObject)jsonResult);
						Object streams = value.get(JSON_PROP_WORKSPACES);
						
						if (streams == null || !(streams instanceof JSONArray)) {
							return Messages.RTCFacadeFacade_stream_not_found(buildStream);
						}
						else {
							int size = ((JSONArray) streams).size();
							if (size == 0) {
								return Messages.RTCFacadeFacade_stream_not_found(buildStream);
							} else if (size > 1) {
								return Messages.RTCFacadeFacade_multiple_streams(buildStream);
							}
						}
					} catch (JSONException exp) {
						throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_stream_response(exp.getMessage(), uri, jsonResult.toString(4)), exp);
					}
					return null; // no errors
				}
				else {
					throw new IOException(Messages.RTCFacadeFacade_unexpected_search_streams_response(uri, (jsonResult == null ? "null" : jsonResult.toString(4)))); //$NON-NLS-1$
				}
			}
			catch (InvalidCredentialsException exp) {
				errorMessage = exp.getMessage();
			}
			catch (IOException exp) {
				errorMessage = exp.getMessage();
			}
			return errorMessage;
			
		} else {
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String) facade.invoke(RTCFacadeWrapper.TEST_BUILD_STREAM, 
												new Class[] { String.class, // serverURI
															String.class, // userId
															String.class, // password
															int.class, // timeout
															String.class, // buildStream
															Locale.class},// clientLocale
											serverURI, userId, password, timeout, buildStream,
											LocaleProvider.getLocale());
			return errorMessage;
		}
	}
	
	/**
	 * Terminate an RTC build previously started by the H/J build.
	 * Either the rest service or the buld toolkit will be used
	 *  
 	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The UUID for the build result to be ended.
	 * @param buildResult The state of the Jenkins build (success, failure or unstable)
	 * @param aborted Whether the Jenkins build was aborted
	 * @param listener A listener that will be notified of the progress and errors encountered.
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public static void terminateBuild(String masterBuildToolkit, String serverURI, String userId, 
			String password, int timeout, boolean avoidUsingToolkit, String buildResultUUID,
			Result buildResult, TaskListener listener) throws Exception {
		// post to create; put to update
		if (avoidUsingToolkit) {
			// Since we will be making more than 1 rest call, manage the context so we only login once
			HttpClientContext context = null;

			// perform compatibility check to let them know that the status was not updated
			// since we won't fail the termination, only do it if we can log it.
			if (listener != null && buildResult != Result.ABORTED && buildResult != Result.SUCCESS) {
				String errorMessage = null;
				String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;
				
				// Validate that the server version is sufficient 
				GetResult getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null);
				errorMessage = ensureCompatability(getResult.getJson());
				context = getResult.getHttpContext();
				
				if (errorMessage != null) {
					errorMessage = Messages.RTCFacadeFacade_build_termination_status_incomplete(errorMessage);
					listener.error(errorMessage);
				}
			}
			String uri = RTCBuildConstants.URI_RESULT + buildResultUUID + SLASH + RTCBuildConstants.URI_SEGMENT_BUILD_STATE;

			GetResult getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, context, listener);
			context = getResult.getHttpContext();
			JSON json = getResult.getJson();

			String state = JSONHelper.getString(json, BUILD_STATE); 
			String status = JSONHelper.getString(json, BUILD_STATUS);
			if (state != null) {
				JSONObject buildStateDTO = (JSONObject) json; 
    			RTCBuildState currentState = RTCBuildState.valueOf(state);
				if (currentState == RTCBuildState.CANCELED || currentState == RTCBuildState.COMPLETED
						|| currentState == RTCBuildState.INCOMPLETE) {
					// we can not set the state of the build, its already been terminated on the RTC side
					return;
				}

				if (currentState == RTCBuildState.NOT_STARTED && buildResult != Result.ABORTED) {
					// I don't think we will ever be in this state because for us to want to complete
					// the build we should own it which means we put it in the IN PROGRESS State.
					
					// But for safety, Before completing the build we need to start it otherwise
					// it will be deleted as not necessary.
					buildStateDTO.put(BUILD_STATE, RTCBuildState.IN_PROGRESS.name());
					
					// update the state
					context = HttpUtils.performPut(serverURI, uri, userId, password, timeout, buildStateDTO, context, listener);
					
					// get the updated status
					getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, context, listener);
					json = getResult.getJson();
					
					if (json instanceof JSONObject) {
						buildStateDTO = (JSONObject) json; 
		    			state = buildStateDTO.getString(BUILD_STATE);
					} else {
		    			LOGGER.finer("Unexpected response to " + uri + " received: " + (json == null ? "null" : json.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						throw new IOException(Messages.RTCFacadeFacade_unexpected_build_state_response(uri, (json == null ? "null" : json.getClass()))); //$NON-NLS-1$
					}
				}
				
    			RTCBuildStatus currentStatus = null;
				if (buildResult == Result.ABORTED) {
					if (currentState == RTCBuildState.NOT_STARTED) {
						currentState = RTCBuildState.CANCELED;
					} else {
						currentState = RTCBuildState.INCOMPLETE;
					}
				} else {
					currentState = RTCBuildState.COMPLETED;
					if (buildResult == Result.UNSTABLE) {
						currentStatus = RTCBuildStatus.WARNING;
					} else if (buildResult == Result.FAILURE) {
						currentStatus = RTCBuildStatus.ERROR;
					} else if (status == null) {
						currentStatus = RTCBuildStatus.OK;
					}
				}

				buildStateDTO.put(BUILD_STATE, currentState.name());
				if (currentStatus != null) {
					buildStateDTO.put(BUILD_STATUS, currentStatus.name());
				}
				
				context = HttpUtils.performPut(serverURI, uri, userId, password, timeout, buildStateDTO, context, listener);
	    	} else {
    			LOGGER.finer("Unexpected response to " + uri + " received: " + (json == null ? "null" : json.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				throw new IOException(Messages.RTCFacadeFacade_unexpected_build_state_put_response(uri, (json == null ? "null" : json.getClass()))); //$NON-NLS-1$
			}
		} else {
			// use the toolkit
			boolean aborted = false;
			int buildState = 0;
			if (buildResult.equals(Result.ABORTED)) {
				aborted = true;
			} else if (buildResult.equals(Result.UNSTABLE)) {
				buildState = 1;
			} else if (!buildResult.equals(Result.SUCCESS)) {
				buildState = 2;
			}
	
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(masterBuildToolkit, null);
			facade.invoke(
					"terminateBuild", //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildResultUUID
							boolean.class, // aborted,
							int.class, // buildState,
							Object.class, // listener
							Locale.class}, // clientLocale
					serverURI,
					userId, password,
					timeout,
					buildResultUUID,
					aborted, buildState,
					listener, Locale.getDefault());
		}
	}

	/**
	 * Delete an RTC build previously created by running a H/J build (whether initiated from
	 * RTC or H/J).
	 * Either the rest service or the build toolkit will be used
	 *  
 	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param buildResultUUID The UUID for the build result to be ended.
	 * @throws Exception If any non-recoverable error occurs.
	 */
	public static void deleteBuild(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, String buildResultUUID) throws Exception {
		// post to create; put to update
		if (avoidUsingToolkit) {
			String uri = RTCBuildConstants.URI_RESULT + buildResultUUID;
			HttpUtils.performDelete(serverURI, uri, userId, password, timeout, null, TaskListener.NULL);
			
		} else {
			// use the toolkit
	
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			facade.invoke(
					"deleteBuildResult", //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildResultUUID
							Object.class, // listener
							Locale.class}, // clientLocale
					serverURI,
					userId, password,
					timeout,
					buildResultUUID,
					TaskListener.NULL,
					Locale.getDefault());
		}
	}

	/**
	 * Validate if the specified components exist in the repository and included in the given workspace. Use either the
	 * rest service or the build toolkit to perform the validations.
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param isStreamConfiguration Flag that determines if <code>buildWorkspace</code> corresponds to a workspace or stream
	 * @param buildWorkspace The name of the RTC build workspace
	 * @param componentsToExclude Json string specifying the list of components to exclude
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testComponentsToExclude(String buildToolkitPath, String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, boolean isStreamConfiguration, String buildWorkspace, String componentsToExclude) throws Exception {

		if (avoidUsingToolkit) {
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;

			try {
				// Validate that the server version is sufficient
				GetResult getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null);
				errorMessage = ensureCompatability(getResult.getJson());

				if (errorMessage != null) {
					return errorMessage;
				}
				HttpClientContext httpContext = getResult.getHttpContext();

				// Validate the workspace specified in the build configuration
				String workspaceItemId = isStreamConfiguration ? validateStreamExists(serverURI, userId, password, timeout, httpContext,
						buildWorkspace) : validateWorkspaceExists(serverURI, userId, password, timeout, httpContext, buildWorkspace);

				JSONObject jsonObj = JSONObject.fromObject(componentsToExclude);
				JSONArray componentsArray = jsonObj.getJSONArray(Helper.COMPONENTS_TO_EXCLUDE);
				// Iterate through the list of components and validate
				for (int i = 0; i < componentsArray.size(); i++) {
					JSONObject comp = (JSONObject)componentsArray.get(i);
					// Trim the input values here, as it is not evident if the values are trimmed elsewhere
					if (comp.has(Helper.COMPONENT_ID)) {
						validateComponentWithIdExistsInWorkspace(serverURI, userId, password, timeout, httpContext, isStreamConfiguration, workspaceItemId,
								comp.getString(Helper.COMPONENT_ID));
					} else {
						validateComponentWithNameExistsInWorkspace(serverURI, userId, password, timeout, httpContext, isStreamConfiguration, workspaceItemId,
								buildWorkspace, comp.getString(Helper.COMPONENT_NAME));
					}
				}
				return null;
			} catch (InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			} catch(IllegalArgumentException e) {
				errorMessage = e.getMessage();
			}

			return errorMessage;
		} else {

			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String)facade.invoke("testComponentsToExclude", //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							boolean.class, // isStreamConfiguration
							String.class, // buildWorkspace
							String.class, // componentsToExclude
							Locale.class }, // clientLocale
					serverURI, userId, password, timeout, isStreamConfiguration, buildWorkspace, componentsToExclude, LocaleProvider.getLocale());
			return errorMessage;
		}
	}

	/**
	 * Validate if the specified components/load rule files exist in the repository and included in the given workspace.
	 * Use either the rest service or the build toolkit to perform the validations.
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param isStreamConfiguration Flag that determines if <code>buildWorkspace</code> corresponds to a workspace or stream
	 * @param buildWorkspace The name of the RTC build workspace
	 * @param loadRules Json string specifying the component-to-load-rule file mapping
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */

	public static String testLoadRules(String buildToolkitPath, String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, boolean isStreamConfiguration, String buildWorkspace, String loadRules) throws Exception {

		if (avoidUsingToolkit) {
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;

			try {
				// Validate that the server version is sufficient
				GetResult getResult = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null);
				errorMessage = ensureCompatability(getResult.getJson());

				if (errorMessage != null) {
					return errorMessage;
				}
				HttpClientContext httpContext = getResult.getHttpContext();
				String workspaceItemId = isStreamConfiguration ? validateStreamExists(serverURI, userId, password, timeout, httpContext,
						buildWorkspace) : validateWorkspaceExists(serverURI, userId, password, timeout, httpContext, buildWorkspace);

				JSONObject jsonObj = JSONObject.fromObject(loadRules);
				JSONArray loadRulesArray = jsonObj.getJSONArray(Helper.LOAD_RULES);
				// Iterate through the list of component-to-load-rule file mapping and validate
				for (int i = 0; i < loadRulesArray.size(); i++) {
					JSONObject loadRule = (JSONObject)loadRulesArray.get(i);
					Component component = null;
					// Trim the input values here, as it is not evident if the values are trimmed elsewhere
					// validate component
					if (loadRule.has(Helper.COMPONENT_ID)) {
						component = validateComponentWithIdExistsInWorkspace(serverURI, userId, password, timeout, httpContext,
								isStreamConfiguration, workspaceItemId, loadRule.getString(Helper.COMPONENT_ID));
					} else {
						component = validateComponentWithNameExistsInWorkspace(serverURI, userId, password, timeout, httpContext,
								isStreamConfiguration, workspaceItemId, buildWorkspace, loadRule.getString(Helper.COMPONENT_NAME));
					}
					// validate load rule file
					if (loadRule.has(Helper.FILE_ITEM_ID)) {
						validateFileWithIdExistsInWorkspace(serverURI, userId, password, timeout, httpContext, isStreamConfiguration,
								workspaceItemId, buildWorkspace, component.getItemId(), component.getName(), loadRule.getString(Helper.FILE_ITEM_ID));
					} else {
						validateFileInPathExistsInWorkspace(serverURI, userId, password, timeout, httpContext, isStreamConfiguration,
								workspaceItemId, buildWorkspace, component.getItemId(), component.getName(), loadRule.getString(Helper.FILE_PATH));
					}

				}
				return null;
			} catch (InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			} catch (IllegalArgumentException e) {
				errorMessage = e.getMessage();
			}

			return errorMessage;
		} else {
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String)facade.invoke("testLoadRules", //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							boolean.class, // isStreamConfiguration
							String.class, // buildWorkspace
							String.class, // componentsToExclude
							Locale.class }, // clientLocale
					serverURI, userId, password, timeout, isStreamConfiguration, buildWorkspace, loadRules, LocaleProvider.getLocale());
			return errorMessage;
		}
	}
	
	/**
	 * Validate if the RTC project area/team area exists. Validation using REST service is not supported
	 * 
 	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param processArea The name of the RTC project area/team area
	 * @return an error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testProcessArea(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			String processArea)
			throws Exception {
		LOGGER.finer("Testing Process Area using the toolkit"); //$NON-NLS-1$
		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
		String errorMessage = (String) facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA, //$NON-NLS-1$
				new Class[] { String.class, // serverURI
						String.class, // userId
						String.class, // password
						int.class, // timeout
						String.class, // processArea
						Locale.class}, // clientLocale
				serverURI, userId, password, timeout,
				processArea, LocaleProvider.getLocale());
		return errorMessage;

	}

	/**
	 * Validate if the given workspace exists.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server
	 * @param timeout The timeout period for requests made to the server
	 * @param httpContext HttpContext obtained from a previous response
	 * @param buildWorkspace Name of the workspace configured in the build
	 * @return Item ID of the workspace
	 * @throws Exception
	 */
	private static String validateWorkspaceExists(String serverURI, String userId, String password, int timeout, HttpClientContext httpContext,
			String buildWorkspace) throws Exception {
		String workspaceItemId = null;
		String uri = RTCBuildConstants.URI_SEARCH_WORKSPACES + Util.encode(buildWorkspace);
		JSON wsJson = HttpUtils.performGet(serverURI, uri, userId, password, timeout, httpContext, null).getJson();

		if (!(wsJson instanceof JSONObject)) {
			LOGGER.finer("Unexpected response to " + uri + " received: " + (wsJson == null ? "null" : wsJson.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException(Messages.RTCFacadeFacade_unexpected_search_workspaces_response(uri, (wsJson == null ? "null" : wsJson.toString(4)))); //$NON-NLS-1$
		}
		try {
			JSONObject value = getValueObjectFromJson((JSONObject)wsJson);
			Object workspaces = value.get(JSON_PROP_WORKSPACES);
			if (workspaces == null || !(workspaces instanceof JSONArray)) {
				// not found
				throw new IllegalArgumentException(Messages.RTCFacadeFacade_workspace_not_found(buildWorkspace));
			} else {
				int size = ((JSONArray)workspaces).size();
				if (size == 0) {
					// not found
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_workspace_not_found(buildWorkspace));
				} else if (size > 1) {
					// more than one
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_multiple_workspaces(buildWorkspace));
				}
			}
			workspaceItemId = ((JSONArray)workspaces).getJSONObject(0).getString(JSON_PROP_ITEM_ID);
		} catch (JSONException e) {
			throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_workspaces_response(e.getMessage(), uri, wsJson.toString(4)), e);
		}
		return workspaceItemId;
	}
	
	/**
	 * Validate if the given stream exists.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server
	 * @param timeout The timeout period for requests made to the server
	 * @param httpContext HttpContext obtained from a previous response
	 * @param buildStream Name of the stream configured in the build
	 * @return Item ID of the workspace
	 * @throws Exception
	 */
	private static String validateStreamExists(String serverURI, String userId, String password, int timeout, HttpClientContext httpContext,
			String buildStream) throws Exception {
		String streamItemId = null;
		String uri = RTCBuildConstants.URI_SEARCH_STREAMS + Util.encode(buildStream);
		JSON streamJson = HttpUtils.performGet(serverURI, uri, userId, password, timeout, httpContext, null).getJson();

		if (!(streamJson instanceof JSONObject)) {
			LOGGER.finer("Unexpected response to " + uri + " received: " + (streamJson == null ? "null" : streamJson.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException(Messages.RTCFacadeFacade_unexpected_search_streams_response(uri,
					(streamJson == null ? "null" : streamJson.toString(4)))); //$NON-NLS-1$
		}
		try {
			JSONObject value = getValueObjectFromJson((JSONObject)streamJson);
			Object streams = value.get(JSON_PROP_WORKSPACES);
			if (streams == null || !(streams instanceof JSONArray)) {
				// not found
				throw new IllegalArgumentException(Messages.RTCFacadeFacade_stream_not_found(buildStream));
			} else {
				int size = ((JSONArray)streams).size();
				if (size == 0) {
					// not found
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_stream_not_found(buildStream));
				} else if (size > 1) {
					// more than one
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_multiple_streams(buildStream));
				}
			}
			streamItemId = ((JSONArray)streams).getJSONObject(0).getString(JSON_PROP_ITEM_ID);
		} catch (JSONException e) {
			throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_stream_response(e.getMessage(), uri, streamJson.toString(4)), e);
		}
		return streamItemId;
	}


	/**
	 * Validate if a component with the given item ID exists in the repository and included in the specified workspace.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server
	 * @param timeout The timeout period for requests made to the server
	 * @param httpContext HttpContext obtained from a previous response
	 * @param isStreamConfiguration Flag that determines if <code>workspaceItemId</code> corresponds to a workspace or stream
	 * @param workspaceItemId Item ID of the workspace specified in the build configuration
	 * @param componentItemId Item ID of the component
	 * 
	 * @return An object encapsulating the component item ID and component name
	 * 
	 * @throws Exception
	 */
	private static Component validateComponentWithIdExistsInWorkspace(String serverURI, String userId, String password, int timeout,
			HttpClientContext httpContext, boolean isStreamConfiguration, String workspaceItemId, String componentItemId) throws Exception {

		Component component = null;
		String uri = RTCBuildConstants.URI_GET_COMPONENTS + Util.encode(componentItemId);
		JSON compJsonResponse = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();
		// validate if component is in repository
		if (!(compJsonResponse instanceof JSONObject)) {
			LOGGER.finer("Unexpected response to " + uri + " received: " + (compJsonResponse == null ? "null" : compJsonResponse.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException(Messages.RTCFacadeFacade_unexpected_search_components_response(uri,
					(compJsonResponse == null ? "null" : compJsonResponse.toString(4)))); //$NON-NLS-1$
		}
		try {
			JSONObject value = getValueObjectFromJson((JSONObject)compJsonResponse);
			Object components = value.get(JSON_PROP_COMPONENTS);

			if (components == null || !(components instanceof JSONArray)) {
				throw new IllegalArgumentException(Messages.RTCFacadeFacade_component_with_id_not_found(componentItemId));
			} else {
				int size = ((JSONArray)components).size();
				if (size == 0) {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_component_with_id_not_found(componentItemId));
				}
				// we are searching by id so there will exactly be one component

				JSONObject componentJson = ((JSONArray)components).getJSONObject(0);
				component = new Component(componentItemId, componentJson.getString(JSON_PROP_NAME));
			}

		} catch (JSONException e) {
			throw new IOException(
					Messages.RTCFacadeFacade_error_parsing_search_components_response(e.getMessage(), uri, compJsonResponse.toString(4)), e);
		}
		// validate if component is included in a workspace
		// IScmRichClientRestService does not provide any method that validates
		// if a component is included in a workspace. Invoke getVersionable
		// passing the path as "/" indicating root folder of the component. This
		// service request will fail if the component is not in the workspace.
		uri = RTCBuildConstants.URI_GET_VERSIONABLE_BY_PATH + Util.encode(SLASH) + GET_VERSIONABLE_REQUEST_PARAM_CONTEXT_ITEM_ID
				+ Util.encode(workspaceItemId) + GET_VERSIONABLE_REQUEST_PARAM_COMPONENT_ITEM_ID + Util.encode(componentItemId);
		JSON versionableJsonResponse = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();
		if (!(versionableJsonResponse instanceof JSONObject)) {
			LOGGER.finer("Unexpected response to " + uri + " received: " + (versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (isStreamConfiguration) {
				throw new IOException(Messages.RTCFacadeFacade_unexpected_search_components_in_stream_response(uri,
						(versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4)))); //$NON-NLS-1$

			} else {
				throw new IOException(Messages.RTCFacadeFacade_unexpected_search_components_in_workspace_response(uri,
						(versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4)))); //$NON-NLS-1$
			}
		}

		return component;
	}

	/**
	 * Validate if a component with the given name exists in the repository and included in the specified workspace.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server
	 * @param timeout The timeout period for requests made to the server
	 * @param httpContext HttpContext obtained from a previous response
	 * @param isStreamConfiguration Flag that determines if <code>workspaceItemId</code> corresponds to a workspace or a stream
	 * @param workspaceItemId Item ID of the workspace specified in the build configuration
	 * @param workspaceName Name of the workspace specified in the build configuration
	 * @param componentName Name of the component
	 * 
	 * @return An object encapsulating the component item ID and component name
	 * 
	 * @throws Exception
	 */
	private static Component validateComponentWithNameExistsInWorkspace(String serverURI, String userId, String password, int timeout,
			HttpClientContext httpContext, boolean isStreamConfiguration,String workspaceItemId, String workspaceName, String componentName) throws Exception {

		Component component = null;
		boolean fetch = false;
		// performs case sensitive, exact name lookup
		String uri = RTCBuildConstants.URI_SEARCH_COMPONENTS + Util.encode(componentName);
		List<String> componentItemIds = new ArrayList<String>();
		Long lastComponentModifiedDate = null;
		do {
			// fetch will be true if the search returned the maximum number of
			// components, it could
			// in which case we need to search again for some more possible
			// matches by passing on the modified date of the last fetched
			// component
			if (fetch) {
				uri = uri + SEARCH_COMPONENTS_REQUEST_PARAM_LAST_COMP_MODIFIED_DATE + lastComponentModifiedDate;
			}
			JSON compJsonResponse = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();
			if (!(compJsonResponse instanceof JSONObject)) {
				LOGGER.finer("Unexpected response to " + uri + " received: " + (compJsonResponse == null ? "null" : compJsonResponse.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				throw new IOException(Messages.RTCFacadeFacade_unexpected_search_components_response(uri,
						(compJsonResponse == null ? "null" : compJsonResponse.toString(4)))); //$NON-NLS-1$
			}

			try {
				JSONObject value = getValueObjectFromJson((JSONObject)compJsonResponse);
				Object components = value.get(JSON_PROP_COMPONENTS);

				if (components == null || !(components instanceof JSONArray)) {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_component_with_name_not_found(componentName));
				} else {
					int size = ((JSONArray)components).size();
					// we are fetching the components for the first time and there is none
					if (size == 0 && !fetch) {
						throw new IllegalArgumentException(Messages.RTCFacadeFacade_component_with_name_not_found(componentName));
					}
					// we need to search again as we have got the maximum number
					// of components returned in one run, there could be some
					// more matches
					fetch = (size == MAX_COMP_SEARCH_RESULTS);
				}
				JSONArray componentsArray = (JSONArray)components;
				int size = componentsArray.size();
				for (int i = 0; i < size; i++) {
					JSONObject componentJson = componentsArray.getJSONObject(i);
					// Though we perform a search by exact name, to be on the
					// safer side we further filter by name
					if (componentName.equals(componentJson.getString(JSON_PROP_NAME))) {
						componentItemIds.add(componentJson.getString(JSON_PROP_ITEM_ID));
					}
					// if we have to rerun search, make a note of the modified time on the last returned components
					// components are ordered by modified date, newest to oldest
					if (fetch && i == (size - 1) && componentJson.getString(JSON_PROP_DATE_MODIFIED) != null) {
						lastComponentModifiedDate = parseTimeRFC3339(componentJson.getString(JSON_PROP_DATE_MODIFIED)).getTime();
					}
				}
				// In case the service returns partial matches and none of them
				// had the exact match
				if (componentItemIds.size() == 0) {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_component_with_name_not_found(componentName));
				}
			} catch (JSONException e) {
				throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_components_response(e.getMessage(), uri,
						compJsonResponse.toString(4)), e);
			}
		} while (fetch);

		// iterate through all components matching the specified name and validate if it is included in the workspace
		// if only one component with the given name is found in the workspace, we are good
		// if either none of the components are found in the workspace or more
		// than one component with the given name is found in the workspace then
		// error out
		boolean found = false;
		for (String tmpComponentItemId : componentItemIds) {
			JSON versionableJsonResponse = null;
			try {
				// IScmRichClientRestService does not provide any method that validates
				// if a component is included in a workspace. Invoke getVersionable
				// passing the path as "/" indicating root folder of the component. This
				// service request will fail if the component is not in the workspace.
				uri = RTCBuildConstants.URI_GET_VERSIONABLE_BY_PATH + Util.encode(SLASH) + GET_VERSIONABLE_REQUEST_PARAM_CONTEXT_ITEM_ID
						+ Util.encode(workspaceItemId) + GET_VERSIONABLE_REQUEST_PARAM_COMPONENT_ITEM_ID + Util.encode(tmpComponentItemId);
				versionableJsonResponse = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();
			} catch (IOException e) {
				LOGGER.finer("Exception accessing " + uri + e.getMessage()); //$NON-NLS-1$
				// ignore component not found exceptions and error out for
				// others
				// when a component is not found in the workspace, we get back
				// an IOException and the error message contains the component
				// name
				if (!e.getMessage().contains(componentName)) {
					throw e;
				}
				// ignore component not found and continue
				continue;
			}
			if (!(versionableJsonResponse instanceof JSONObject)) {
				LOGGER.finer("Unexpected response to " + uri + " received: " + (versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				if (isStreamConfiguration) {
					throw new IOException(Messages.RTCFacadeFacade_unexpected_search_components_in_stream_response(uri,
							(versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4)))); //$NON-NLS-1$
				} else {
					throw new IOException(Messages.RTCFacadeFacade_unexpected_search_components_in_workspace_response(uri,
							(versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4)))); //$NON-NLS-1$
				}
			}
			// at this point we know that the component exists in the workspace,
			// if we have already found one component with the same name in the
			// workspace then error out
			if (found) {
				if (isStreamConfiguration) {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_multiple_components_with_name_in_stream(componentName, workspaceName));
				} else {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_multiple_components_with_name_in_workspace(componentName,
							workspaceName));
				}
			}
			// found a component with the given name in the workspace for the
			// first time
			found = true;
			component = new Component(tmpComponentItemId, componentName);

		}
		// no component with the given name included in the workspace
		if (!found) {
			throw new IllegalArgumentException(Messages.RTCFacadeFacade_component_with_name_not_found_ws(componentName, workspaceName));
		}
		return component;
	}

	/**
	 * Validate if the load rule file with the given id exists in the repository and included in the specified
	 * workspace/component context.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server
	 * @param timeout The timeout period for requests made to the server
	 * @param httpContext HttpContext obtained from a previous response
	 * @param isStreamConfiguration Flag that determines if <code>workspaceItemId</code> corresponds to a workspace or stream
	 * @param workspaceItemId Item ID of the workspace specified in the build configuration
	 * @param workspaceName Name of the workspace specified in the build configuration
	 * @param componentItemId Item ID of the component
	 * @param componentName Name of the component
	 * @param fileItemId Item ID of the file
	 * 
	 * @throws Exception
	 */
	private static void validateFileWithIdExistsInWorkspace(String serverURI, String userId, String password, int timeout,
			HttpClientContext httpContext, boolean isStreamConfiguration, String workspaceItemId, String workspaceName, String componentItemId,
			String componentName, String fileItemId) throws Exception {

		String uri = RTCBuildConstants.URI_GET_VERSIONABLE_BY_ID + Util.encode(fileItemId) + GET_VERSIONABLE_REQUEST_PARAM_CONTEXT_ITEM_ID
				+ Util.encode(workspaceItemId) + GET_VERSIONABLE_REQUEST_PARAM_COMPONENT_ITEM_ID + Util.encode(componentItemId);
		JSON versionableJsonResponse = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();

		if (!(versionableJsonResponse instanceof JSONObject)) {
			LOGGER.finer("Unexpected response to " + uri + " received: " + (versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException(Messages.RTCFacadeFacade_unexpected_search_versionable_response(uri,
					(versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4)))); //$NON-NLS-1$
		}

		try {
			JSONObject value = getValueObjectFromJson((JSONObject)versionableJsonResponse);
			Object versionable = value.getJSONObject(JSON_PROP_VERSIONABLE);
			// file not found in workspace
			if (versionable == null || !(versionable instanceof JSONObject)) {
				if (isStreamConfiguration) {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_file_with_id_not_found_stream(fileItemId, componentName,
							workspaceName));
				} else {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_file_with_id_not_found_ws(fileItemId, componentName,
							workspaceName));
				}
			} else {
				// doesn't resolve to a file in workspace
				String type = ((JSONObject)versionable).getString(JSON_PROP_TYPE);
				if (!FILE_ITEM_VALUE.equals(type)) {
					if (isStreamConfiguration) {
						throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_with_id_not_a_file_stream(fileItemId, componentName,
								workspaceName));
					} else {
						throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_with_id_not_a_file_ws(fileItemId, componentName,
								workspaceName));
					}
				}
			}
		} catch (JSONException e) {
			throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_versionable_response(e.getMessage(), uri,
					versionableJsonResponse.toString(4)), e);
		}

	}

	/**
	 * Validate if the load rule file in the given path exists in the workspace/component context.
	 * 
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server
	 * @param timeout The timeout period for requests made to the server
	 * @param httpContext HttpContext obtained from a previous response
	 * @param isStreamConfiguration Flag that determines if <code>workspaceItemId</code> corresponds to a workspace or stream
	 * @param workspaceItemId Item ID of the workspace specified in the build configuration
	 * @param workspaceName Name of the workspace specified in the build configuration
	 * @param componentItemId Item ID of the component
	 * @param componentName Name of the component
	 * @param filePath Path to the file in the repository
	 * 
	 * @throws Exception
	 */
	private static void validateFileInPathExistsInWorkspace(String serverURI, String userId, String password, int timeout,
			HttpClientContext httpContext, boolean isStreamConfiguration, String workspaceItemId, String workspaceName, String componentItemId, String componentName, String filePath)
			throws Exception {

		if (filePath.length() == 0) {
			throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_file_path_empty(filePath));
		}
		
		// getVersionable expects the paths to start with a "/"
		String tmpFilePath = filePath;
		if (!tmpFilePath.startsWith(SLASH)) {
			tmpFilePath = SLASH + tmpFilePath;
		}
		String uri = RTCBuildConstants.URI_GET_VERSIONABLE_BY_PATH + Util.encode(tmpFilePath) + GET_VERSIONABLE_REQUEST_PARAM_CONTEXT_ITEM_ID
				+ Util.encode(workspaceItemId) + GET_VERSIONABLE_REQUEST_PARAM_COMPONENT_ITEM_ID + Util.encode(componentItemId);
		JSON versionableJsonResponse = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, null).getJson();

		if (!(versionableJsonResponse instanceof JSONObject)) {
			LOGGER.finer("Unexpected response to " + uri + " received: " + (versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			throw new IOException(Messages.RTCFacadeFacade_unexpected_search_versionable_response(uri,
					(versionableJsonResponse == null ? "null" : versionableJsonResponse.toString(4)))); //$NON-NLS-1$
		}

		try {
			JSONObject value = getValueObjectFromJson((JSONObject)versionableJsonResponse);
			Object versionable = value.getJSONObject(JSON_PROP_VERSIONABLE);
			// file not found in workspace
			if (versionable == null || !(versionable instanceof JSONObject)) {
				if (isStreamConfiguration) {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_file_not_found_stream(filePath, componentName, workspaceName));
				} else {
					throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_file_not_found_ws(filePath, componentName, workspaceName));
				}
			} else {
				// doesn't resolve to a file in workspace
				String type = ((JSONObject)versionable).getString(JSON_PROP_TYPE);
				if (!type.contains(FILE_ITEM_VALUE)) {
					if (isStreamConfiguration) {
						throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_not_a_file_stream(filePath, componentName, workspaceName));
					} else {
						throw new IllegalArgumentException(Messages.RTCFacadeFacade_load_rule_not_a_file_ws(filePath, componentName, workspaceName));
					}
				}
			}
		} catch (JSONException e) {
			throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_versionable_response(e.getMessage(), uri,
					versionableJsonResponse.toString(4)), e);
		}

	}

	/**
	 * Copied from com.ibm.team.repository.common.utils.DateUtils.
	 * 
	 * <p>
	 * IScmRichClientRestService.getSearchComponents2(ParmsGetComponentsByOwner) returns the modified date on the
	 * components as a Timestamp value. This timestamp value is marshalled to String using one of the date formats
	 * declared initially in this class.
	 * </p>
	 * <p>
	 * Since we get back the plain text json and we don't have the repository component's demarshaller to reconstruct
	 * the tiemstamp, we do it ourselves. A long value equivalent to the modified date of the last fetched component
	 * needs to be set on further calls to searchComponents when the number of found components exceeds the maximum,
	 * which is 512.
	 * </p>
	 * 
	 * @param time String representation of a timestamp
	 * 
	 * @return Timestamp corresponding to the specified string
	 * 
	 * @throws ParseException
	 */
	private static Timestamp parseTimeRFC3339(String time) throws ParseException {
		if (time == null)
			throw new IllegalArgumentException("time must not be null"); //$NON-NLS-1$
		// We have 2 formats we support. Test them both.
		try {
			// Try the full format first
			return new Timestamp(_RFC3339DateFormatInternal.parse(time).getTime());
		} catch (ParseException e) {
			// we failed. Save the exception to throw if the other format fails,
			// too.
			try {
				// Now try the format w/o milliseconds
				return new Timestamp(_RFC3339DateNoMillisFormatInternal.parse(time).getTime());
			} catch (ParseException e1) {
				// Both formats failed. Throw the exception for the preferred
				// format.
				throw e;
			}
		}
	}

	/**
	 * Parse the standard json response from the RTC server and get the value specific to the individual requests.
	 * 
	 * @param responseObject JSON response from the RTC server
	 * 
	 * @return Request specific value included in the response
	 */
	private static JSONObject getValueObjectFromJson(JSONObject responseObject) {
		JSONObject body = responseObject.getJSONObject(JSON_PROP_SOAPENV_BODY);
		JSONObject response = body.getJSONObject(JSON_PROP_RESPONSE);
		JSONObject returnValue = response.getJSONObject(JSON_PROP_RETURN_VALUE);
		return returnValue.getJSONObject(JSON_PROP_VALUE);
	}

	private static class Component {
		private String itemId;
		private String name;

		public Component(String itemId, String name) {
			this.itemId = itemId;
			this.name = name;
		}

		public String getItemId() {
			return this.itemId;
		}

		public String getName() {
			return this.name;
		}
	}

}
