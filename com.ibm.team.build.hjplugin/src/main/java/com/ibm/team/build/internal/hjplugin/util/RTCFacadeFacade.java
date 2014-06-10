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

import hudson.Util;
import hudson.model.Result;
import hudson.model.TaskListener;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;
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

	public static Boolean incomingChanges(String buildToolkitPath, String serverURI, 
			String userId, String password,
			int timeout, boolean avoidUsingToolkit, boolean useBuildDefinition, String buildDefinitionId,
			String workspaceName, TaskListener listener) throws Exception {
		
		// Attempt to use Rest api if this is for a build definition
		if (useBuildDefinition && avoidUsingToolkit) {
			
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
				throw new IOException(Messages.RTCFacadeFacade_unexpected_incoming_response(uri, (json == null ? "null" : json.getClass()))); //$NON-NLS-2$  //$NON-NLS-1$
			}

		} else {
			
			listener.getLogger().println(Messages.RTCFacadeFacade_check_incoming_with_toolkit());
		
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, listener.getLogger());
			Boolean changesIncoming = (Boolean) facade.invoke(
					"incomingChanges", //$NON-NLS-1$
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							int.class, // timeout
							String.class, // buildDefinition
							String.class, // buildWorkspace
							Object.class, // listener
							Locale.class}, // clientLocale
					serverURI, userId, password,
					timeout, 
					(useBuildDefinition ? buildDefinitionId : ""), //$NON-NLS-1$
					(useBuildDefinition ? "" : workspaceName), //$NON-NLS-1$
					listener, LocaleProvider.getLocale());
			return changesIncoming;
		}
	}

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

	public static String testBuildDefinition(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, String buildDefinitionId)
			throws Exception {

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
			String errorMessage = (String) facade.invoke("testBuildDefinition", //$NON-NLS-1$
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
	 * @return Message describing what is wrong in the validation
	 */
	public static String testBuildWorkspace(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingToolkit, String buildWorkspace) throws Exception {
		
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
	
				uri = RTCBuildConstants.URI_SEARCH_WORKSPACES + Util.encode(buildWorkspace);
				
				JSON json = HttpUtils.performGet(serverURI, uri, userId, password, timeout, getResult.getHttpContext(), null).getJson();
				if (json instanceof JSONObject) {
					try {
						JSONObject jsonResponse = (JSONObject) json;
						JSONObject body = jsonResponse.getJSONObject(JSON_PROP_SOAPENV_BODY);
						JSONObject response = body.getJSONObject(JSON_PROP_RESPONSE);
						JSONObject returnValue = response.getJSONObject(JSON_PROP_RETURN_VALUE);
						JSONObject value = returnValue.getJSONObject(JSON_PROP_VALUE);
						Object workspaces = value.get(JSON_PROP_WORKSPACES);
						if (workspaces == null || !(workspaces instanceof JSONArray)) {
							// not found
							return Messages.RTCFacadeFacade_workspace_not_found(buildWorkspace);
						} else {
							int size = ((JSONArray) workspaces).size();
							if (size == 0) {
								// not found
								return Messages.RTCFacadeFacade_workspace_not_found(buildWorkspace);
							} else if (size > 1) {
								// more than one
								return Messages.RTCFacadeFacade_multiple_workspaces(buildWorkspace);
							}
						}
					} catch (JSONException e) {
						throw new IOException(Messages.RTCFacadeFacade_error_parsing_search_workspaces_response(e.getMessage(), uri, json.toString(4)), e); //$NON-NLS-2$
					}
					return null;
		    	} else {
	    			LOGGER.finer("Unexpected response to " + uri + " received: " + (json == null ? "null" : json.toString(4))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					throw new IOException(Messages.RTCFacadeFacade_unexpected_search_workspaces_response(uri, (json == null ? "null" : json.toString(4)))); //$NON-NLS-1$
				}
			} catch (InvalidCredentialsException e) {
				errorMessage = e.getMessage();
			} catch (IOException e) {
				errorMessage = e.getMessage();
			}
			return errorMessage;
		} else {
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
			String errorMessage = (String) facade.invoke("testBuildWorkspace", //$NON-NLS-1$
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
}
