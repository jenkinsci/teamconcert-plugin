/*******************************************************************************
 * Copyright (c) 2014, 2020 IBM Corporation and others.
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.http.Header;
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
	
	protected static HttpUtilsHelper httpUtilsHelper = new HttpUtilsHelper();
	
	/**
	 * Used only for testing purposes
	 * 
	 * @param h
	 */
	protected static void setHttpUtilsHelper(HttpUtilsHelper h) {
		httpUtilsHelper = h;
	}
	
	/**
	 * Used only for testing purposes
	 * 
	 */
	private static HttpUtilsHelper getHttpUtilsHelper() {
		return httpUtilsHelper;
	}

	/**
	 * Perform post build deliver for the given build result. 
	 * 
	 * @param serverURI
	 * @param userId
	 * @param password
	 * @param timeout
	 * @param buildResultUUID
	 * @param buildResultLabel
	 * @param listener
	 * @throws Exception - An IOException is thrown if there is a TeamRepositoryException or if the 
	 * 			HTTP connection was broken.
	 * 					   A GeneralSecurityException is thrown if there is a problem performing HTTPs 
	 * 					  communication
	 * 					   An InvalidCredentialsException is thrown if the user name or password is incorrect
	 */
	public static PostBuildDeliverResult postBuildDeliver(String serverURI, String userId, String password, int timeout,
				String buildResultUUID, String buildResultLabel, TaskListener listener) throws Exception {

		if (buildResultLabel != null) {
			listener.getLogger().println(Messages.RTCFacadeFacade_starting_post_build_deliver2(buildResultLabel, buildResultUUID));
		} else {
			listener.getLogger().println(Messages.RTCFacadeFacade_starting_post_build_deliver(buildResultUUID));
		}
		
		LOGGER.finest(String.format("RTCFacadeFacade:postBuildDeliver : Enter for %s", buildResultUUID));
		
		// There is some issue in the authentication flow when doing a POST
		// So we have to do a get that logs in to the repository and then do a post
		String uri = RTCBuildConstants.URI_RESULT + buildResultUUID + SLASH + RTCBuildConstants.URI_SEGMENT_BUILD_STATE;
		GetResult result = HttpUtils.performGet(serverURI, uri, userId, password, timeout, null, listener);
		HttpClientContext context = result.getHttpContext();

		// Create a JSON object that puts in the details for post build deliver
		JSONObject participantRequest = new JSONObject();
		participantRequest.put(RTCBuildConstants.PB_DELIVER_PARTICIPANT_KEY,  RTCBuildConstants.POST_BUILD_DELIVER_PARTICIPANT);
		participantRequest.put(RTCBuildConstants.PB_DELIVER_BUILD_RESULT_ITEMID_KEY, buildResultUUID);
		participantRequest.put(RTCBuildConstants.PB_DELIVER_IGNORE_TRIGGER_POLICY_KEY, RTCBuildConstants.TRUE);
		
		// Construct the URI for invoking a participant
		uri = RTCBuildConstants.URI_RESULT + buildResultUUID +
					RTCBuildConstants.SEPARATOR + RTCBuildConstants.URI_SEGMENT_RESULT_PARTICIPANT;
		
		HttpUtils.performPost(serverURI, uri, userId, password, timeout, participantRequest, context, listener);
		
		// If this is reached here, we got a 200.
		if (context != null) {
			Header h = context.getResponse().getFirstHeader("Location");
			if (h != null && h.getValue() != null) {

				String contribUri = h.getValue();
				contribUri = contribUri.replace(RTCBuildConstants.URI_SEGMENT_RESULT_PARTICIPANT, RTCBuildConstants.URI_SEGMENT_CONTRIBUTION);
				contribUri = contribUri.substring(serverURI.length());
				
				// Log the contribution URI
				LOGGER.finest(String.format("Retrieving post build deliver contribution from URI : ", contribUri));
				
				// Get the contribution
				JSON contribResult = HttpUtils.performGet(serverURI, contribUri, userId, password, timeout, context, listener).getJson();
				JSONObject contrib =  JSONHelper.getJSONObject(contribResult,"contribution");
				if (contrib != null) {
					// This determines the current build's status (if the user has set failOnError to true)  
					String contributionStatus = JSONHelper.getString(contrib, "contributionStatus");

					// Get the extended contribution properties to see if post build deliver happened
					// and what is the summary
					boolean delivered = false;
					String participantSummary = null;
					String participantLog = null;
					String contentURI = null;
					JSONArray extendedContribProperties = JSONHelper.getJSONArray(contrib, RTCBuildConstants.EXTENDED_CONTRIB_PROPERTIES_MEMBER);
					for (@SuppressWarnings("unchecked")
					Iterator<JSONObject> iter = extendedContribProperties.iterator(); iter.hasNext();) {
			            JSONObject contribProperty = iter.next();
			            if (contribProperty.get(RTCBuildConstants.EXTENDED_CONTRIB_PROPERTY_NAME).equals(RTCBuildConstants.AUTO_DELIVER_POST_BUILD_PARTICIPANT_DELIVERED)) {
			            	delivered = Boolean.parseBoolean((String)contribProperty.get(RTCBuildConstants.EXTENDED_CONTRIB_PROPERTY_VALUE));
			            }
			            if (contribProperty.get(RTCBuildConstants.EXTENDED_CONTRIB_PROPERTY_NAME).equals(RTCBuildConstants.AUTO_DELIVER_POST_BUILD_PARTICIPANT_SUMMARY)) {
			            	participantSummary = contribProperty.getString(RTCBuildConstants.EXTENDED_CONTRIB_PROPERTY_VALUE);
			            }
					}
					if (delivered && contributionStatus.equals("OK")) {
						// Get the link to the participant log and print it to the log
						// This can be done only if delivered is true and build status is set to OK
						contentURI = JSONHelper.getString(contribResult, "contentUri");
						if (contentURI != null) {
							// Make it absolute by appending repository URI
							try {
								Tuple<HttpClientContext, String> tr = HttpUtils.performRawGet(serverURI, contentURI, userId, password, timeout, context, listener);
								participantLog = tr.getSecond();
							} catch (Exception exp) {
								LOGGER.log(Level.WARNING, String.format("Unable to retrieve participant log from content URI %s. Exception is :", contentURI), exp);
							}
							
						}
					}
					return new PostBuildDeliverResult(contributionStatus, delivered, participantSummary, participantLog, contentURI);
				} else {
					// TODO extra information on what response was expected??
					throw new IOException(Messages.RTCFacadeFacade_unexpected_response_postbuild_deliver(), null);

				}
			} else {
				// TODO extra information on what response was expected??
				throw new IOException(Messages.RTCFacadeFacade_unexpected_response_postbuild_deliver(), null);
			}
		} else {
			// TODO extra information on what response was expected??
			throw new IOException(Messages.RTCFacadeFacade_unexpected_response_postbuild_deliver(), null);
		}
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
		logCheckIncomingChangesMessage(buildDefinitionId, listener);
		logCheckIncomingChangesMessage(workspaceName, listener);
		
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
	 * @param processArea The name of the project or team area
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
			int timeout, String processArea, boolean useBuildDefinition, String buildDefinitionId,
			String workspaceName, String streamName, String streamChangesData, TaskListener listener, boolean ignoreOutgoingFromBuildWorkspace) throws Exception {
		listener.getLogger().println(Messages.RTCFacadeFacade_check_incoming_with_toolkit());
		logCheckIncomingChangesMessage(buildDefinitionId, listener);
		logCheckIncomingChangesMessage(workspaceName, listener);
		logCheckIncomingChangesMessage(streamName, listener);

		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, listener.getLogger());
		BigInteger changesIncoming = null;;
		if (streamName != null) {
			changesIncoming = (BigInteger) facade.invoke("computeIncomingChangesForStream",  //$NON-NLS-1$
					new Class[] { String.class, String.class,
							String.class, int.class, String.class,
							String.class, String.class, Object.class, Locale.class},
					serverURI,
					userId,
					password,
					timeout,
					processArea,
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
	 * @return An error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testConnection(String buildToolkitPath, 
			String serverURI, String userId, String password, int timeout,
			boolean avoidUsingtoolkit) throws Exception {
		return testConnection(buildToolkitPath, serverURI, userId, password,
				timeout, RTCBuildConstants.URI_COMPATIBILITY_CHECK,
				RTCBuildConstants.MINIMUM_SERVER_VERSION, avoidUsingtoolkit);
	}
			
	/**
	 * Logs into the repository to test the connection with a given level of compatibility.
	 * Essentially exercises the configuration parameters supplied.
	 * Either the rest service or the build toolkit will be used to test the connection.
	 * 
	 * @param buildToolkitPath The path to the build toolkit should the toolkit need to be used
	 * @param avoidUsingToolkit Whether to avoid using the build toolkit (use rest service instead)
	 * @param serverURI The address of the repository server
	 * @param userId The user id to use when logging into the server
	 * @param password The password to use when logging into the server.
	 * @param timeout The timeout period for requests made to the server
	 * @param compatbilityURI The expected version of the server that we should be compatible with
	 * @return An error message to display, or null if no problem
	 * @throws Exception
	 */
	public static String testConnection(String buildToolkitPath,
			String serverURI, String userId, String password, int timeout,
			String compatibilityURI, String minimumServerVersion, 
			boolean avoidUsingToolkit)
			throws Exception {
		
		// Attempt to use Rest API if this is for a build definition
		if (avoidUsingToolkit) {
			Tuple<String, GetResult> t = testConnectionHTTP(serverURI, userId, password, timeout, 
										compatibilityURI, minimumServerVersion);
			return t.getFirst();
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
	
	/**
	 * Checks the version compatibility of the client with the EWM server. 
	 * It first checks if the minimum client compatibility requirement is satisfied.
	 * If yes, then it authenticates to the server.
	 * If no and the response is a version compatibility error, it extracts the server version.
	 *    It then checks if the server version is greater than the client version. 
	 *    If yes, then it retries the client compatibility check with the exact server version
	 *    If no, then it errors out.
	 * If no and the response is not a version compatibility error, it errors out as before.
	 *   
	 * @param serverURI - The EWM server URI to check 
	 * @param userId - The user Id 
	 * @param password - The password
	 * @param timeout - The timeout for the server connection
	 * @param compatibilityURI - The compatibility URI fragment
	 * @param minimumServerVersion - The minimum server version to start the  compatibility check with
	 *   For some specific services, a different version will be sent. The method will redo the compatibility 
	 *   check with the same server version only if the advertised server version is greater than the version
	 *   specified by <code>minimumServerVersion</code>
	 * @return a {@link Tuple> that contains the error message (if any) and the result of the HTTP Get call
	 * @throws Exception
	 */
	public static Tuple<String, GetResult> testConnectionHTTP(String serverURI, String userId, String password, int timeout,
							String compatibilityURI, String minimumServerVersion) throws Exception {
		LOGGER.finest("testConnectionHTTP :Enter");
		Tuple<CompatibilityResult, GetResult> t = testConnectionHTTPHelper(serverURI, userId, password, timeout, compatibilityURI);
		GetResult result = t.getSecond();
		CompatibilityResult compatibilityResult = t.getFirst();
		String errorMessage = compatibilityResult.getErrorMessage();
		// If errorMessage is non null, then the error can be invalid server, 
		// invalid response, invalid credentials, or version compatibility check error. 
		// If the errorMessage is non null, then serverVersion should be non null 
		// for the error to be a version compatibility error. Otherwise, we just 
		// return the error message as is.
		if (errorMessage != null) {
			String serverVersion = Util.fixEmptyAndTrim(compatibilityResult.getServerVersion());
			// It is possible that serverVersion is null because the error is 
			// not a client compatibility error. In that case, just return the error 
			// message as is. If the server version is not present because the server is JTS, then 
			// serverVersion will still be null.
			if (serverVersion != null) {
				LOGGER.finest("EWM server version is " + serverVersion);
				// If server version came back along with the error message, we can check whether
				// the server version is greater than the minimum client version. 
				// If yes, then retry the HTTP Get call with the server version itself.
				String serverVersionWithoutMileStone = extractServerVersionWithoutMilestone(serverVersion);
				LOGGER.finest("EWM server version without milestone is " + serverVersionWithoutMileStone);
				// If extraction failed, then print out an error message
				if (serverVersionWithoutMileStone != null) {
					boolean isServerVersionHigher = isServerVersionEqualOrHigher(
							serverVersionWithoutMileStone, minimumServerVersion);
					if (isServerVersionHigher) {
						t = testConnectionHTTPHelper(serverURI, userId, password, timeout,
										RTCBuildConstants.URI_COMPATIBILITY_CHECK_WITHOUT_VERSION + serverVersion);
						errorMessage = t.getFirst().getErrorMessage();
						result = t.getSecond();
					}
				} else {
					// Make sure that we send a  error message back to the caller.
					// This part of the error should say that the server version could not be extracted - 
					// add it to the existing error message and send it back.
					LOGGER.finest("Unable to extract server version information in the "
							+ "response from version compatibility service");
					errorMessage = errorMessage + "\n" + 
									Messages.RTCFacadeFacade_error_extract_server_version();
				}
			}
		}
		// At this point, result and errorMessage could have been modified in the 
		// if / else sections.
		return new Tuple<>(errorMessage, result);
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
			// Make sure that the server is compatible. Rest API on older releases ignores
			// the query parameter which would mean it finding more than returning possibly > 1 element
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;
			try {
				
				// Validate that the server version is sufficient 
				Tuple<String, GetResult> t = testConnectionHTTP(serverURI, userId, password, timeout, uri,
										RTCBuildConstants.MINIMUM_SERVER_VERSION);
				errorMessage = t.getFirst();
				if (errorMessage != null) {
					return errorMessage;
				}
				GetResult getResult = t.getSecond();
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
		// ensure only 1 workspace is found.
		if (avoidUsingToolkit) {
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;

			try {
				// Validate that the server version is sufficient 
				Tuple<String, GetResult> t = testConnectionHTTP(serverURI, userId, password, timeout, uri,
										RTCBuildConstants.MINIMUM_SERVER_VERSION);
				errorMessage = t.getFirst();

				if (errorMessage != null) {
					return errorMessage;
				}
				GetResult getResult = t.getSecond();
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
			boolean avoidUsingToolkit, String processArea, String buildStream) throws Exception {
		LOGGER.finest("RTCFacadeFacade.testBuildStream : Enter");
		
		buildStream = Util.fixEmptyAndTrim(buildStream);
		
		// ensure only 1 stream is found.
		// when avoidUsingToolkit is true and processArea is null use the rest service
		// process area is not validated by the rest service, so we always use the buildToolkit when a process area is
		// specified
		// a build toolkit is always available when a process area is specified. The validation for buildToolkit is
		// performed upstream in RTCScm.doValidateBuildStreamConfiguration.
		if (avoidUsingToolkit && Util.fixEmptyAndTrim(processArea) == null) {
			String errorMessage = null;
			String uri = RTCBuildConstants.URI_COMPATIBILITY_CHECK;
			
			try {
				// Validate that the server version is sufficient 
				Tuple<String, GetResult> t = testConnectionHTTP(serverURI, userId, password, timeout, uri,
										RTCBuildConstants.MINIMUM_SERVER_VERSION);
				errorMessage = t.getFirst();

				if (errorMessage != null) {
					return errorMessage;
				}
				GetResult result = t.getSecond();
				
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
															String.class, //processArea
															String.class, // buildStream
															Locale.class},// clientLocale
											serverURI, userId, password, timeout, processArea, 
											buildStream, LocaleProvider.getLocale());
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
				Tuple<String, GetResult> t = testConnectionHTTP(serverURI, userId, password, timeout, uri, 
											RTCBuildConstants.MINIMUM_SERVER_VERSION);
				errorMessage = t.getFirst();
				
				if (errorMessage != null) {
					errorMessage = Messages.RTCFacadeFacade_build_termination_status_incomplete(errorMessage);
					listener.error(errorMessage);
				}
				context = t.getSecond().getHttpContext();
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
		String uri = RTCBuildConstants.URI_SEARCH_WORKSPACES + Util.rawEncode(buildWorkspace).replace("&", "%26"). //$NON-NLS-1$ //$NON-NLS-2$
					replace("=", "%3D");  //$NON-NLS-1$ //$NON-NLS-2$
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

	private static void logCheckIncomingChangesMessage(String item, TaskListener listener) {
		if (item != null) {
			listener.getLogger().println(Messages.RTCFacadeFacade_checking_incoming_changes_for(item));
		}
	}
	
	/**
	 * Check if the serverVersion without milestone is greater than or equal to the minimum server 
	 * version.
	 * 
	 * @param serverVersionWithoutMilestone -The server version without milestone
	 * @param minimumServerVersion - The minimum server version.
	 * @return - <code>true</code> if the server version is greater than or equal to minimum server 
	 * 			version, <code>false</code> otherwise. 
	 * 
	 * Note: This is protected for testing purposes
	 */
	protected static boolean isServerVersionEqualOrHigher(String serverVersionWithoutMilestone, 
						String minimumServerVersion) {
		if (Util.fixEmptyAndTrim(serverVersionWithoutMilestone) == null 
				|| Util.fixEmptyAndTrim(minimumServerVersion) == null) {
			// This indicates invalid input but we already log the fact that 
			// serverVersionWithoutMileStone was null before making this call
			return false;
		}
		String [] serverFields = serverVersionWithoutMilestone.split("\\.");
		String [] minimumServerFields = minimumServerVersion.split("\\.");
		LOGGER.finest("EWM Client fields "  + Arrays.toString(minimumServerFields) +
				"EWM Server fields " + Arrays.toString(serverFields));
		Boolean isEqualOrGreater = null;
		int i = 0, j=0;
		while ( i < serverFields.length  && j < minimumServerFields.length) {
			int sf = (int)serverFields[i].charAt(0);
			int cf = (int)minimumServerFields[j].charAt(0);
			if (sf > cf) {
				isEqualOrGreater = Boolean.TRUE;
				break;
			} else if (sf < cf){
				isEqualOrGreater = Boolean.FALSE;
				break;
			} else {
				// Continue to the next digit
				i++;
				j++;
			}
		}
		if (isEqualOrGreater == null) {
			// The scenario is when the server version is a prefix of the client version 
			// and client version is greater than the server 
			// (or)
			// The client version is a prefix of the server version and server version is greater 
			// than the client 
			// (or)
			// The server and client are of the same version
			if (serverFields.length < minimumServerFields.length) {
				// Clearly the server version is smaller than the client version 
				isEqualOrGreater = Boolean.FALSE;
			}
			else if (serverFields.length > minimumServerFields.length) {
				// The server version is greater than minimum server version
				isEqualOrGreater = Boolean.TRUE;
			} else {
				// Both strings are of equal length and server version is same as minimum 
				// server version
				isEqualOrGreater = Boolean.TRUE;
			}
		}
		LOGGER.finest("Is server version greater than client ? " + isEqualOrGreater);
		return isEqualOrGreater;
	}


	/**
	 * This extracts the server version without the milestone.
	 * 
	 * @param serverVersion
	 * @return the extracted server version without milestone identifiers (S1,M2,RC1 etc.,)
	 * 
	 * Note: This is protected for testing purposes
	 */
	protected static String extractServerVersionWithoutMilestone(String serverVersion) {
		Pattern p = Pattern.compile("\\d\\.\\d(\\.\\d(\\.\\d)?)?");
		Matcher m = p.matcher(serverVersion);
		while (m.find()) {
			return m.group();
		}
		return null;
	}

	/**
	 * Helper for test connection using HTTP
	 * 
	 * @param serverURI The server URI
	 * @param userId The user's id
	 * @param password The user's password
	 * @param timeout The timeout for the connection 
	 * @param compatibilityURI The URI fragment for compatibility check
	 * @return a tuple with {@link CompatibilityResult} and the {@link GetResult}
	 * @throws Exception if there is a problem in communicating with RTC server, 
	 * 			invalid credentials or some security exception
	 * 
	 * Note: This is protected for testing purposes
	 */
	protected static Tuple<CompatibilityResult, GetResult> testConnectionHTTPHelper(String serverURI, String userId, String password, int timeout,
										String compatibilityURI) throws Exception  {
		String errorMessage = null;
		String uri = compatibilityURI;
		CompatibilityResult compatibilityResult = null;
		String serverVersion = null;
		GetResult result = null;
		try {
			// Validate that the server version is sufficient 
			result = getHttpUtilsHelper().performGet(serverURI, uri, userId, password, timeout, null, null);
			JSON json = result.getJson();
			compatibilityResult  = ensureCompatability(json);
			errorMessage = compatibilityResult.getErrorMessage();
			serverVersion = compatibilityResult.getServerVersion();
			if (errorMessage == null) {
				// Make sure the credentials are good
				getHttpUtilsHelper().validateCredentials(serverURI, userId, password, timeout);
			}			
		} catch (InvalidCredentialsException e) {
			errorMessage = e.getMessage();
		} catch (IOException e) {
			errorMessage = e.getMessage();
		}
		// At this point, the error can be a compatibility error or invalid credentials
		// error or any kind of server error. We capture the error message in the compatibility result
		return new Tuple<>(new CompatibilityResult(serverVersion, errorMessage), result);
	}
	
	/**
	 * Check the compatibilty result and return a {@link CompatibilityResult}
	 *  
	 * @param compatibilityCheckResult
	 * @return a {@link CompatibilityResult}
	 * @throws GeneralSecurityException
	 * 
	 * A non null errorMessage indicate something is wrong. If serverVersion is non null, then it is a compatibility error.
	 * We can add isCompatible boolean but that has to be tristate - to discount the possibility of misinterpreting a generic 
	 * error as a version compatibility error. Currently, I have decided not to have this additional boolean and use the 
	 * non emptiness of serverVersion as an indicator of version compatibility failure. 
	 */
	static CompatibilityResult ensureCompatability(JSON compatibilityCheckResult) throws GeneralSecurityException {
		String errorMessage = null;
		String serverVersion = null;
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
			// Create a object with the details. We can use this to determine if we want to retry with higher 
			// values for version compatibility.
			String upgradeURI = JSONHelper.getString(compatibilityCheckResult, JSON_PROP_URI);
			String upgradeMessage = JSONHelper.getString(compatibilityCheckResult, JSON_PROP_MESSAGE);
			serverVersion = JSONHelper.getString(compatibilityCheckResult, JSON_PROP_SERVER_VERSION);
			if ((upgradeURI == null) || (upgradeMessage == null) || (serverVersion == null)) {
				errorMessage = Messages.RTCFacadeFacade_invalid_response_invoking_version_compatibility_service();
			} else {
				errorMessage = Messages.RTCFacadeFacade_incompatible2(serverVersion);
			}
		}
		return new CompatibilityResult(serverVersion, errorMessage);
	}

	/**
	 * Class to store the compatibility error information
	 */
	public static class CompatibilityResult {
		private final String errorMessage;
		private final String serverVersion;

		public CompatibilityResult(String serverVersion, String errorMessage) {
			this.serverVersion = serverVersion;
			this.errorMessage = errorMessage;
		}

		public boolean isCompatible() {
			// TODO Auto-generated method stub
			return false;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public String getServerVersion() {
			return serverVersion;
		}
	}
	
	/**
	 * This helper class allows to inject alternatives for testing purposes 
	 * A default static instance is created in {@link RTCFacadeFacade}. 
	 * While testing, the test class can set an instance that mocks out the 
	 * n/w calls and additional logic to track the number of calls made.
	 * 
	 * Currently this helper instance is used in 
	 *  {@link RTCFacadeFacade#testConnection(String, String, String, String, int, String, String, boolean)
	 *  and {@link RTCFacadeFacade#testConnectionHTTPHelper(String, String, String, int, String) to 
	 *  test those methods. If new methods are to be extensively tested and n/w depenendencies should be mocked out,
	 *  then use the helper instance {@link RTCFacadeFacade#httpUtilsHelper} 
	 */
	public static class HttpUtilsHelper {
		
		public GetResult performGet(String serverURI, String uri, String userId, String password, 
				int timeout, HttpClientContext context, TaskListener listener )	throws InvalidCredentialsException, IOException, GeneralSecurityException {
			return HttpUtils.performGet(serverURI, uri, userId, password, timeout, context, listener);
		}
		
		public void validateCredentials(String serverURI, String userId, String password, int timeout) 
				throws InvalidCredentialsException, IOException, GeneralSecurityException {
			// Make sure the credentials are good
			HttpUtils.validateCredentials(serverURI, userId, password, timeout);
		}
	}
}
