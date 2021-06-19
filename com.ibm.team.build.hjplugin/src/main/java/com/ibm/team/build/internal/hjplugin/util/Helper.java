/*******************************************************************************
 * Copyright © 2014, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.ibm.team.build.internal.hjplugin.InvalidCredentialsException;
import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;

import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

public class Helper {

	private static final Logger LOGGER = Logger.getLogger(Helper.class.getName());

	private static final String TEAM_SCM_STREAM_CHANGES_DATA = "team_scm_streamChangesData"; //$NON-NLS-1$
	private static final String PREVIOUS_BUILD_URL_KEY = "previousBuildUrl"; //$NON-NLS-1$
	private static final String CURRENT_BUILD_URL_KEY = "currentBuildUrl"; //$NON-NLS-1$
	private static final String CURRENT_BUILD_FULL_URL_KEY = "currentBuildFullUrl"; //$NON-NLS-1$
	private static final String CURRENT_BUILD_LABEL_KEY = "currentBuildLabel"; //$NON-NLS-1$
	
	private static final String SNAPSHOT_OWNER = "team_scm_snapshotOwner"; //$NON-NLS-1$
	private static final String TEAM_SCM_SNAPSHOT_UUID_PROPERTY = "team_scm_snapshotUUID";

	
	private static final String BUILD_STATE_DELIMITER = ",";
	private static final Set<String> ALL_BUILD_STATES = new HashSet<>();
	public static final String [] DEFAULT_BUILD_STATES = new String [] {RTCBuildState.COMPLETED.toString(),
									RTCBuildState.INCOMPLETE.toString()};
	public static final String DEFAULT_BUILD_STATES_STR = String.join(",", DEFAULT_BUILD_STATES);

	static {
		ALL_BUILD_STATES.addAll(Arrays.asList(RTCBuildState.NOT_STARTED.toString(), 
		RTCBuildState.CANCELED.toString(), RTCBuildState.IN_PROGRESS.toString(),
		RTCBuildState.INCOMPLETE.toString(), RTCBuildState.COMPLETED.toString()));
	}
	
	/**
	 * Default value to wait for a build result to change to the 
	 * required state. There is a duplicate variable in Constants class in 
	 * -rtc jar
	 */
	public static final int DEFAULT_WAIT_BUILD_TIMEOUT = -1;

	public static final int DEFAULT_MAX_RESULTS = 512;
			
	public static final int MAX_RESULTS_UPPER_LIMIT = 2048;
	
	/** 
	 * merge two results, if both are errors only one stack trace can be included
	 * @param firstCheck The first validation done
	 * @param secondCheck The second validaiton done
	 * @return The merge of the 2 validations with a concatenated message and the highest severity
	 */
	public static FormValidation mergeValidationResults(FormValidation firstCheck, FormValidation secondCheck) {
		// we do not want to merge validation results of different kinds
		// so, return only the validation result that is more inclined towards validation failures
		// i.e. if you have combination of error, warning and ok return the validation result which is an error, if
		// no error then return the warning
		// error warning - return firstCheck //
		// error OK - return firstCheck //
		// warning OK - return firstCheck //
		// warning error - return secondCheck//
		// OK error - return secondCheck
		// OK warning - return secondCheck //
		if (!firstCheck.kind.equals(secondCheck.kind)) {
			if (firstCheck.kind.equals(FormValidation.Kind.ERROR) || (firstCheck.kind.equals(FormValidation.Kind.WARNING) && 
							secondCheck.kind.equals(FormValidation.Kind.OK))) {
				return firstCheck;
			} else {
				return secondCheck;
			}
		}
		Throwable errorCause = secondCheck.getCause();
		if (errorCause == null) {
			errorCause = firstCheck.getCause();
		}
		String message;
		String firstMessage = firstCheck.getMessage();
		String secondMessage = secondCheck.getMessage();
		if (firstCheck.kind.equals(FormValidation.Kind.OK) && (firstMessage == null || firstMessage.isEmpty())) {
			message = secondCheck.renderHtml();
		} else if (secondCheck.kind.equals(FormValidation.Kind.OK) && (secondMessage == null || secondMessage.isEmpty())) {
			message = firstCheck.renderHtml();
		} else {
			message = firstCheck.renderHtml() +  "<br/>" + secondCheck.renderHtml(); //$NON-NLS-1$
		}
		FormValidation.Kind kind;
		if (firstCheck.kind.equals(secondCheck.kind)) {
			kind = firstCheck.kind;
		} else if (firstCheck.kind.equals(FormValidation.Kind.OK)) {
			kind = secondCheck.kind;
		} else if (firstCheck.kind.equals(FormValidation.Kind.ERROR) || secondCheck.kind.equals(FormValidation.Kind.ERROR)) {
			kind = FormValidation.Kind.ERROR;
		} else {
			kind = FormValidation.Kind.WARNING;
		}
		
		return FormValidation.respond(kind, message);
	}
	
	public static String getStringBuildParameter(Run<?,?> build, String property, TaskListener listener) throws IOException, InterruptedException {
		 LOGGER.finest("Helper.getStringBuildProperty : Begin"); //$NON-NLS-1$
		 if (LOGGER.isLoggable(Level.FINEST)) {
			 LOGGER.finest("Helper.getStringBuildProperty: Finding value for property '" + property + "' in the build environment variables.");	  //$NON-NLS-1$ //$NON-NLS-2$
		 }
		 String value = Util.fixEmptyAndTrim(build.getEnvironment(listener).get(property));
		 if (value == null) {
			if (LOGGER.isLoggable(Level.FINEST)) {
				LOGGER.finest("Helper.getStringBuildProperty: Cannot find value for property '" + property + "' in the build environment variables. Looking in the build parameters."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			// check if parameter is available from ParametersAction
			value = getValueFromParametersAction(build, property);			
			if (value == null) {
				if (LOGGER.isLoggable(Level.FINEST)) {
					LOGGER.finest("Helper.getStringBuildProperty: Cannot find value for property '" + property + "' in the build parameters."); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		 }
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Helper.getStringBuildProperty: Value for property '" + property + "' is '" + value + "'."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		 return value;
	}
	
	private static String getValueFromParametersAction(Run<?, ?> build, String key) {
		LOGGER.finest("Helper.getValueFromParametersAction : Begin"); //$NON-NLS-1$
		String value = null;
		for (ParametersAction paction : build.getActions(ParametersAction.class)) {
			List<ParameterValue> pValues = paction.getParameters();
			if (pValues == null) {
				continue;
			}
			for (ParameterValue pv : pValues) {
				if (pv instanceof StringParameterValue && pv.getName().equals(key)) {
					value = Util.fixEmptyAndTrim((String)pv.getValue());
					if (value != null) {
						break;
					}
				}
			}
			if (value != null) {
				break;
			}
		}
		if (LOGGER.isLoggable(Level.FINEST)) {
			if (value == null) {
				LOGGER.finest("Helper.getValueFromParametersAction : Unable to find a value for key : " + key); //$NON-NLS-1$
			} else {
				LOGGER.finest("Helper.getValueFromParametersAction : Found value : " + value + " for key : " + key);  //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return value;
	}
	
	/**
	 * Given a value that may be a parameter, resolve the parameter to its default value while trimming for whitespace.
	 * If the resolved value of the parameter is <code>null</null>, then the value is returned as such, 
	 * after trimming for whitespace.
	 * If the given value is not a parameter, then trim whitespace and return it.
	 * @param job
	 * @param value - the value which could be a parameter. May be <code>null</code>
	 * @param listener
	 * @return a {@link String} which is the value to the parameter resolves to or the value itself, after trimming for whitespace
	 */
	public static String parseConfigurationValue(Job<?,?> job, String value, TaskListener listener) {
		LOGGER.finest("Helper.parseConfigurationValue for Job: Enter");
		// First nullify the value if it is empty
		value = Util.fixEmptyAndTrim(value);
		if (value == null) {
			return null;
		}
		String paramValue = value;
		// Check whether value itself is a job parameter
		if (Helper.isAParameter(value)) {
			paramValue = Helper.resolveJobParameter(job, value, listener);
			if (paramValue != null) {
				if (LOGGER.isLoggable(Level.FINEST)) {
					LOGGER.finest("Found value for job parameter '" + value + "' : " + paramValue);
				}
				return paramValue;
			} else {
				paramValue = value;
			}
		}
		return paramValue;
	}
	
	/**
	 * This method first checks whether the given <param>jobParameter</param> exists and is not null
	 * If yes, then its value is returned. Otherwise, it checks whether <param>value</param> is actually
	 * a job property. If yes and <param>resolveValue</param> is true, it is resolved and returned. 
	 * Otherwise, the <param>value</param> is returned
	 * @param build
	 * @param jobParameter the job parameter to look for the value first. may be <code>null</code>
	 * @param value
	 * @param listener
	 * @return the value of the parameter 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static String parseConfigurationValue(Run<?, ?> build, String jobParameter, 
							String value, TaskListener listener) throws IOException, InterruptedException {
		// If a Job parameter exists and not null, then return it
		if (jobParameter != null) {
			String jobParameterValue = getStringBuildParameter(build, jobParameter, listener);
			if (jobParameterValue != null) {
				return jobParameterValue;
			}
		}
		
		// First trim the value
		value = Util.fixEmptyAndTrim(value);
		if (value == null) {
			return null;
		}

		// If jobParameterValue is null, check whether value itself is a job parameter
		// if it is and paramValue is not null, then return paramValue
		// otherwise just return the value provided in the job configuration as is.
		String paramValue = value;
		if (Helper.isAParameter(value)) {
			paramValue = Helper.resolveBuildParameter(build, value, listener);
			if (paramValue != null) {
				if (LOGGER.isLoggable(Level.FINEST)) {
					LOGGER.finest("Found value for job parameter '" + value + "' : " + paramValue);
				}
			} else {
				paramValue = value;
			}
		}
		// Our value is not a parameter, return it trimmed (see above where we have trimmed 'value')
		return paramValue;
	}
	
	/**
	 * Checks whether a given string is a property like ${a} 
	 * @param s The string to check. May be <code>null</code>
	 * @return <code>true</code> if s is a job property 
	 */
	public static boolean isAParameter(String s) {
		if (s == null) {
			return false;
		}
		if (s.startsWith("${") && s.endsWith("}")) {
			return true;
		}
		return false;
	}
	
	/**
	 * Resolve a given build parameter to its value
	 * @param build - the Jenkins build. Never <code>null</code>
	 * @param parameter - the build parameter. Never <code>null</code>
	 * @param listener - task listener. Never <code>null</code>
	 * @return the value of the build parameter or <code>property</code>. All first level property references resolved. May be <code>null</code>.
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public static String resolveBuildParameter(Run<?,?> build, String parameter, TaskListener listener) throws IOException, InterruptedException {
		if (parameter == null) {
			return null;
		}
		String parameterStripped = extractParameterFromValue(parameter);
		return getStringBuildParameter(build, parameterStripped, listener);
	}

	/**
	 *  Resolve a given build property to its value
	 * @param job The Jenkins job. Never <code>null</code>
	 * @param jobParameter The parameter (also known as property) in the job. Never <code>null</code>
	 * @param listener task listener. Never <code>null</code>
	 * @return the value of the build parameter or <code>property</code>. All first level property references resolved. May be <code>null</code>.
	 */
	public static String resolveJobParameter(Job <?,?> job, String jobParameter, TaskListener listener) {
		if (jobParameter == null) {
			return null;
		}
		String parameterStripped = extractParameterFromValue(jobParameter);
		return getStringBuildParameter(job, parameterStripped, listener);
	}
	
	/**
	 * Returns the snapshot UUID from the previous build, sometimes only a successful build
	 * 
	 * @param build The Jenkins build.
	 * @param toolkit The build toolkit to use. This should be a valid build toolkit on the master.
	 * @param loginInfo Login information for RTC
	 * @param streamName The name of the stream that owns the current snapshot (and the previous snapshot)
	 * @param clientLocale Locale of the client
	 * @return a tuple. First member is the build which contains the snapshot and the second is the snapshot UUID. 
	 * 			tuple is never <code>null<code> but its members may be <code>null</code> 
	 * @throws Exception If anything goes wrong when fetching the snapshot details
	 */
	public static Tuple<Run<?,?>, String> getSnapshotUUIDFromPreviousBuild(final Run<?,?> build, String toolkit, RTCLoginInfo loginInfo, String processArea, String buildStream, final boolean onlyGoodBuild, Locale clientLocale) throws Exception {
		Tuple<Run<?,?>, String> snapshotDetails = new Tuple<Run<?,?>, String>(null, null);
		if (buildStream == null) {
			return snapshotDetails;
		}
		snapshotDetails = getValueForBuildStream(new IJenkinsBuildIterator() {
			
			@Override
			public Run<?,?> nextBuild(Run<?, ?> build) {
				if (onlyGoodBuild) {
					return build.getPreviousSuccessfulBuild();
				} else {
					return build.getPreviousBuild();
				}
			}
			
			@Override
			public Run<?, ?> firstBuild() {
				return build;
			}
		}, toolkit, loginInfo, processArea, buildStream, onlyGoodBuild, TEAM_SCM_SNAPSHOT_UUID_PROPERTY, clientLocale); //$NON-NLS-1$
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Helper.getSnapshotUUIDFromPreviousBuild : " +  //$NON-NLS-1$
						((snapshotDetails.getSecond() == null) ? "No snapshotUUID found from a previous build" : snapshotDetails.getSecond())); //$NON-NLS-1$
		}
		return snapshotDetails;
	}
	
	/**
	 * Returns the stream change data from the last build of a job irrespective of whether the build succeeded or failed
	 * 
	 * @param job The Jenkins job
	 * @param toolkit The build toolkit to use. This should be a valid build toolkit on the master
	 * @param loginInfo RTC login information
	 * @param processArea The owner of the stream. The processArea name.
	 * @param buildStream The name of the stream
	 * @param clientLocale The locale of the client
	 * @return a tuple. First member is the build which contains the snapshot and the second is the snapshot UUID. 
	 * 			tuple is never <code>null<code> but its members may be <code>null</code> 
	 * @throws Exception If anything goes wrong when fetching the snapshot details
	 */
	public static Tuple<Run<?,?>, String> getStreamChangesDataFromLastBuild(final Job<?,?> job, String toolkit, RTCLoginInfo loginInfo, String processArea, String buildStream, Locale clientLocale) throws Exception {
		Tuple<Run<?,?>, String> streamChangesData = new Tuple<Run<?,?>, String>(null, null);
		if (buildStream == null) {
			return streamChangesData;
		}
		streamChangesData = getValueForBuildStream(new IJenkinsBuildIterator() {
			@Override
			public Run<?,?> nextBuild(Run<?, ?> build) {
				return build.getPreviousBuild();
			}
			
			@Override
			public Run<?, ?> firstBuild() {
				return job.getLastBuild();
			}}, toolkit, loginInfo, processArea, buildStream, false, TEAM_SCM_STREAM_CHANGES_DATA, clientLocale);
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Helper.getStreamChangesDataFromLastBuild : " + 
						((streamChangesData.getSecond() == null) ? "No stream changes data found from a previous build" : streamChangesData.getSecond()));
		}
		return streamChangesData;
	}
	
	/**
	 * 
	 * Returns the comment string for the temporary workspace to be created.
	 * 
	 * @param build The Jenkins {@link Run} from which some details are obtained. Never <code>null</code>
	 * @return A {@link String} that is the comment for the temporary workspace
	 */
	public static String getTemporaryWorkspaceComment(Run<?,?> build) {
		return Messages.RTCScm_temporary_workspace_comment(
									Integer.toString(build.getNumber()), 
									build.getParent().getName(), 
									Jenkins.getInstance().getRootUrl());
	}
	
	/**
	 * This method resolves any references to the environment variables and build parameters in the custom snapshot name
	 * configured in the job
	 * 
	 * @param build The Jenkins {@link Run} from which some details are obtained. Never <code>null</code>
	 * @param customSnapshotName Custom snapshot name configured in the job
	 * @param listener Task listener. Never <code>null</code>
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static String resolveCustomSnapshotName(Run<?, ?> build, String customSnapshotName, TaskListener listener)
			throws IOException, InterruptedException {
		if (customSnapshotName == null) {
			return null;
		}
		// Util.replaceMacro() replaces $$ with a single $
		// this replace is required to retain consecutive $, like $$, in the template string. 		
		customSnapshotName = customSnapshotName.replaceAll("\\$\\$", "\\$\\$\\$\\$");
		// lookup and resolve environment variables
		String s = build.getEnvironment(listener).expand(customSnapshotName);

		if (build instanceof AbstractBuild) {
			// Util.replaceMacro() replaces $$ with a single $
			// this replace is required to retain consecutive $, like $$, in the template string 	
			s = s.replaceAll("\\$\\$", "\\$\\$\\$\\$");
			// lookup and resolve build variables, Build variables include the parameters passed in the Build
			s = Util.replaceMacro(s, ((AbstractBuild<?, ?>)build).getBuildVariableResolver());
		}
        return s;
	}
	

	/**
	 * Return <code>true</code> if debug property {@link RTCJobProperties#DEBUG_PROPERTY} is defined and set to "true" in the build. 
	 * <code>false</code> otherwise
	 *  
	 * @param build - The build to inspect for the property
	 * @param listener - To log messages
	 * @return <code>true</code> if debug property is defined and set to "true". <code>false</code> otherwise. 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static boolean isDebugEnabled(Run<?, ?> build, TaskListener listener) throws IOException, InterruptedException {
		return Boolean.parseBoolean(Helper.getStringBuildParameter(build, RTCJobProperties.DEBUG_PROPERTY, listener));
	}
	
	/**
	 * Get Jenkins Build URLs and labels info a map
	 * 
	 * @param build The current build
	 * @return a map of build URLs and labels.
	 */
	@Nonnull
	public static Map<String, String> constructBuildURLMap(Run<?, ?> build) {
		// Add the following entries
		// previousBuildUrl and currentBuildUrl is relative
		// currentFullBuildUrl is a complete URL.
		Map<String, String> buildURLMap = new HashMap<String, String>();
		if (build.getPreviousBuild() != null) { 
			buildURLMap.put(PREVIOUS_BUILD_URL_KEY, build.getPreviousBuild().getUrl());
		}
		buildURLMap.put(CURRENT_BUILD_URL_KEY, build.getUrl());
		buildURLMap.put(CURRENT_BUILD_FULL_URL_KEY, Util.encode(Jenkins.getInstance().getRootUrl() + build.getUrl()));
		buildURLMap.put(CURRENT_BUILD_LABEL_KEY, build.getFullDisplayName());
		return buildURLMap;
	}

	/**
	 * Get the value of the property from a previous build's RTCBuildResultAction property map.
	 * The builds are enumerated by the iterator.
	 *   
	 * @param iterator The iterator that goes through previous builds.
	 * @param toolkit The build toolkit to use. This should be a valid build toolkit on master
	 * @param loginInfo The RTC login information
	 * @param processArea The owner of the stream
	 * @param buildStream The name of the stream
	 * @param onlyGoodBuild Whether only a previous good build should be considered
	 * @param key The property name for which the value has to be retrieved 
	 * @param clientLocale The locale of the client
	 * @return a tuple. First member is the build which contains the snapshot and the second is the snapshot UUID. 
	 * 			tuple is never <code>null<code> but its members may be <code>null</code>  
	 * @throws Exception If something goes wrong when fetching stream information
	 */
	private static Tuple<Run<?,?>, String> getValueForBuildStream(IJenkinsBuildIterator iterator, String toolkit, RTCLoginInfo loginInfo, String processArea, String buildStream, boolean onlyGoodBuild, String key, Locale clientLocale) throws Exception {
		if (buildStream == null) {
			return null;
		}
		Run <?,?> build = iterator.firstBuild();
		String value = null;
		Run <?, ?> previousBuild = null;
		RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(toolkit, null);
		String streamUUID =
				// Resolve the stream and get stream UUID
				(String) facade.invoke("getStreamUUID", new Class[] { //$NON-NLS-1$
						String.class, // serverURI,
						String.class, // userId,
						String.class, // password,
						int.class, // timeout,
						String.class, // processArea
						String.class, // buildStream,
						Locale.class // clientLocale
				}, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), processArea, buildStream, clientLocale);
		while (build != null && value == null) {
			List<RTCBuildResultAction> rtcBuildResultActions = build.getActions(RTCBuildResultAction.class);
			if (rtcBuildResultActions.size() == 1) { // the usual case for freestyle builds (without multiple SCM) and workflow build with only one invocation of RTCScm
				RTCBuildResultAction rtcBuildResultAction = rtcBuildResultActions.get(0);
				if ((rtcBuildResultAction != null) && (rtcBuildResultAction.getBuildProperties() != null)) {
					Map <String, String> buildProperties = rtcBuildResultAction.getBuildProperties();
					String owningStreamUUID = Util.fixEmptyAndTrim(buildProperties.get(SNAPSHOT_OWNER));
					if (owningStreamUUID != null && owningStreamUUID.equals(streamUUID)) {
						value = buildProperties.get(key);
						previousBuild = build;
					}
				}
			}
			else if (rtcBuildResultActions.size() > 1) {				
				for (RTCBuildResultAction rtcBuildResultAction : rtcBuildResultActions) {
					if ((rtcBuildResultAction != null) && (rtcBuildResultAction.getBuildProperties() != null)) {
						Map<String,String> buildProperties = rtcBuildResultAction.getBuildProperties();
						String owningStreamUUID = Util.fixEmptyAndTrim(buildProperties.get(SNAPSHOT_OWNER));
						if (owningStreamUUID != null && owningStreamUUID.equals(streamUUID)) {
							value = buildProperties.get(key);
							previousBuild = build;
							// We don't we break here to maintain the following behavior for pipeline jobs
							// If we load the same stream multiple times, we get the the value from the last
							// RTCBuildResultAction
						}
					}
				}
			}
			build = iterator.nextBuild(build);
		}
		return new Tuple<Run<?,?>, String>(previousBuild, value);
	}

	/**
	 * If the given value has a parameter inside it like ${A}, then remove {} from the value and 
	 * return the parameter name
	 * 
	 * @param parameter - the given value that has a parameter inside it
	 * @return a String that is the parameter name.
	 */
	private static String extractParameterFromValue(String parameter) {
		parameter = Util.fixEmptyAndTrim(parameter);
		if (parameter == null) {
			return null;
		}
		String parameterStripped = parameter.substring(2);
		parameterStripped = parameterStripped.substring(0, parameterStripped.length() - 1);
		
		return parameterStripped;
	}
	
	/**
	 * Returns the value of the given parameter from the job configuration
	 **/
	public static String getStringBuildParameter(Job<?,?> job, String parameter, TaskListener listener) {

		ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
		String paramValue = null;
		if (prop != null) {
			List<ParameterDefinition> definitions = prop.getParameterDefinitions();
			for (ParameterDefinition definition : definitions) {
				if (definition == null) {
					continue;
				}
				if (definition.getName().equals(parameter) && definition.getClass().equals(StringParameterDefinition.class)) {
					ParameterValue defaultParamValue = definition.getDefaultParameterValue();
					if (defaultParamValue != null) {
						paramValue = Util.fixEmptyAndTrim((String)defaultParamValue.getValue());
					}
				}
			}
		}
		return paramValue;
	}

	private interface IJenkinsBuildIterator {
		Run<?,?> firstBuild();
		Run<?,?> nextBuild(Run<?,?> build);
	}
	
	/**
	 * Assert the conditions required when pollingOnly option is enabled.
	 * 
	 * @param job The job
	 * @param buildTypeStr The type of the build 
	 */
	public static void assertPollingOnlyConditions(Job<?,?> job, String buildTypeStr) {
		String [] supportedBuildTypes = new String[] { RTCJobProperties.BUILD_DEFINITION_TYPE, 
								RTCJobProperties.BUILD_WORKSPACE_TYPE};
		assertPipelineJob(job, 
					Messages.Helper_polling_supported_only_for_pipeline());
    	assertBuildType(buildTypeStr, supportedBuildTypes,
    			    Messages.Helper_polling_supported_only_for_buildTypes());
	}

	/**
	 * Returns true if the given job is a pipeline job.
	 * 
	 * @param job
	 * @return <code>true</code> if the job is a pipeline job, <code>false</code> otherwise.
	 *  
	 */
	public static boolean isPipelineJob(Job <?,?> job) {
		return (job.getClass().getName().equals("org.jenkinsci.plugins.workflow.job.WorkflowJob"));
	}
	
	private static void assertPipelineJob(Job<?,?> job, String message) {
		if (!isPipelineJob(job)) {
			throw new UnsupportedOperationException(message);
		}
	}
	
	private static void assertBuildType(String buildTypeStr, 
						String [] supportedBuildTypes, String message) {
		boolean found = false;
		for (String buildType : supportedBuildTypes) {
			if (buildType.equals(buildTypeStr)) {
				found = true;
				break;
			}
		}
		if (!found) {
			throw new UnsupportedOperationException(message);
		}
	}

	/**
	 * Checks if the given <code>Throwable</code> is one of the failures 
	 * from -rtc project.
	 * 
	 * @param e    a <code>Throwable</code>
	 * @return     <code>true</code> if the <code>Throwable</code> is one of the expected 
	 *             failures. 
	 */
	public static boolean unexpectedFailure(Throwable e) {
		// This project can not reference types defined in the -rtc project, so we
		// need to do some clunky testing to see if the exception is notification of
		// a badly configured build. In that case, we just want to report the error
		// but not the whole stack trace.
		String name = e.getClass().getSimpleName();
		return !("RTCConfigurationException".equals(name)
				|| "AuthenticationException".equals(name)
				|| e instanceof InterruptedException 
				|| e instanceof InvalidCredentialsException ); //$NON-NLS-1$
	}

	/**
	 * Send -rtc jar to the agent from the control server, Since Jenkins does not 
	 * send them to the agent automatically.
	 *  
	 * @param workspacePath            The Jenkins workspace path in the agent.
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void sendJarsToAgent(FilePath workspacePath)
			throws MalformedURLException, IOException, InterruptedException {
		Helper.LOGGER.finest("Helper.sendJarsToAgent : Begin");
		VirtualChannel channel = workspacePath.getChannel();
		URL facadeJarURL = RTCFacadeFactory.getFacadeJarURL(null);
		if (channel instanceof Channel && facadeJarURL != null) {
			// try to find our jar
			Class<?> originalClass = Helper.class;
			ClassLoader originalClassLoader = (ClassLoader) originalClass.getClassLoader();
			URL [] jars = new URL[] {facadeJarURL};
			boolean result = ((Channel) channel).preloadJar(originalClassLoader, jars);
			Helper.LOGGER.finer("Prefetch result for sending jars is " + result);  //$NON-NLS-1$ //$NON-NLS-2$
		}
		Helper.LOGGER.finest("Helper.sendJarsToAgent : End");
	}

	/**
	 * Extracts build states out of the given string.
	 * The extractor does not know if the extract state is a valid 
	 * {@link RTCBuildState}. To validate build states, use 
	 * {@link #getInvalidStates(String[])

	 * @param buildStatesStr     A string that contains build states separated by comma
	 * @return                   An array of <code>String</code> that contains extracted
	 *                           build states.
	 */
	public static String[] extractBuildStates(String buildStatesStr) {
		LOGGER.entering(Helper.class.getName(), "extractBuildStates");
		Tuple<String[], String[]> buildStatesWithDuplicates = 
				extractBuildStatesWithDuplicates(buildStatesStr);
		return buildStatesWithDuplicates.getFirst();
	}
	
	/**
	 * Extracts build states out of the given string with duplicates 
	 * sent back in a separate list.
	 *  
	 * The extractor does not know if the extract state is a valid 
	 * {@link RTCBuildState}. To validate build states, use 
	 * {@link #getInvalidStates(String[])

	 * @param buildStatesStr     A string that contains build states separated by comma
	 * @return                   An array of <code>String</code> that contains extracted
	 *                           build states.
	 */
	public static Tuple<String[], String[]> extractBuildStatesWithDuplicates(String buildStatesStr) {
		LOGGER.entering(Helper.class.getName(), "extractBuildStates");
		if (buildStatesStr == null) {
			return new Tuple<>(new String[0], new String[0]);
		}
		StringTokenizer tokenizer = new StringTokenizer(buildStatesStr, 
								BUILD_STATE_DELIMITER);
        Set<String> buildStates = new LinkedHashSet<String>();
        Set<String> duplicateBuildStates = new LinkedHashSet<String>();
        while (tokenizer.hasMoreElements()) {
        	String state = tokenizer.nextToken().trim();
        	if (buildStates.contains(state)) {
        		duplicateBuildStates.add(state);
        	} else { 
        		buildStates.add(state);
        	}
        }
        return new Tuple<>(buildStates.toArray(new String[0]), 
        			duplicateBuildStates.toArray(new String[0]));
	}
	
	/**
	 * Given an array of build states, filter out the list of states that are invalid
	 * 
	 * @param buildStates   An array of build states, some of which can be invalid.
	 * 					    If any of the entry is <code>null</code>, then it is ignored
	 *  
	 * @return              An array of <code>String</code> that contains invalid states. 
	 *                      Never <code>null</code> but can be empty.
	 */
	public static String[] getInvalidStates(String [] buildStates) {
		LOGGER.entering(Helper.class.getName(), "getInvalidStates");
		Set<String> invalidBuildStates = new LinkedHashSet<String>();
		if (buildStates == null) {
			return invalidBuildStates.toArray(new String[0]);
		}
		for (String buildState : buildStates) {
			if (buildState == null) {
				continue;
			}
			if (Helper.getAllBuildStates().contains(buildState)) {
				continue;
			} else {
				invalidBuildStates.add(buildState);
			}
		}
		return invalidBuildStates.toArray(new String[invalidBuildStates.size()]);
	}
	
	public static Set<String> getAllBuildStates() {
		return ALL_BUILD_STATES;
	}

	/**
	 * Retrieve snapshot UUID from a build that has such a snapshotUUID that is "owned" by 
	 * the given <param>workspaceUUID</param>
	 * 
	 * It is possible that the immediate previous build does not have a snapshot uuid 
	 * that we are looking for because 
	 * 1. No snapshot UUID  was found (because the build failed before reaching the checkout step)  
	 * 2  Snapshot UUID was found but it is owned by a different workspace. (This happens when the 
	 * the previous build uses a different workspace as part of checkout).
	 * 
	 */
	public static String getSnapshotUUIDFromPreviousBuild(final Run<?, ?> buildParm, 
			String workspaceUUID, boolean debug, 
			TaskListener listener, Locale locale) {
		// If build definition or workspace is not null, then retrieve the workspace UUID
		// Use it to match the pollingOnlyCompareData 

		// First retrieve the pollingOnlyCompareData from the build, if available.
		// If found, then that data's workspace UUID should match the one that we are looking for.
		// Otherwise previous snapshot UUID is not found.
		String snapshotUUID = null;
		Run <?, ?> build = buildParm.getPreviousBuild();
		while (build != null && snapshotUUID == null) {
			List<RTCBuildResultAction> rtcBuildResultActions = build.getActions(RTCBuildResultAction.class);
			if (rtcBuildResultActions.size() == 1) { 
				// the usual case for freestyle builds (without multiple SCM) and 
				// pipeline build with only one invocation of RTCScm
				RTCBuildResultAction rtcBuildResultAction = rtcBuildResultActions.get(0);
				if ((rtcBuildResultAction != null) && (rtcBuildResultAction.getBuildProperties() != null)) {
					Map <String, String> buildProperties = rtcBuildResultAction.getBuildProperties();
					String owningWorkspaceUUID = Util.fixEmptyAndTrim(buildProperties.get(SNAPSHOT_OWNER));
					if (owningWorkspaceUUID != null && owningWorkspaceUUID.equals(workspaceUUID)) {
						snapshotUUID = buildProperties.get(TEAM_SCM_SNAPSHOT_UUID_PROPERTY);
					}
				}
			}
			else if (rtcBuildResultActions.size() > 1) {				
				for (RTCBuildResultAction rtcBuildResultAction : rtcBuildResultActions) {
					if ((rtcBuildResultAction != null) && (rtcBuildResultAction.getBuildProperties() != null)) {
						Map<String,String> buildProperties = rtcBuildResultAction.getBuildProperties();
						String owningWorkspaceUUID = Util.fixEmptyAndTrim(buildProperties.get(SNAPSHOT_OWNER));
						if (owningWorkspaceUUID != null && owningWorkspaceUUID.equals(workspaceUUID)) {
							snapshotUUID = buildProperties.get(TEAM_SCM_SNAPSHOT_UUID_PROPERTY);
							// We don't we break here to maintain the following behavior for pipeline jobs
							// If we load the same stream multiple times, we get the the value from the last
							// RTCBuildResultAction
						}
					}
				}
			}
			build = build.getPreviousBuild();
		}
		return snapshotUUID;
	}
}
 