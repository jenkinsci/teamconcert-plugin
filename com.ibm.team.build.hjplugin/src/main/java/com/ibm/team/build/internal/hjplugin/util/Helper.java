/*******************************************************************************
 * Copyright (c) 2014, 2017 IBM Corporation and others.
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
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.RTCJobProperties;
import com.ibm.team.build.internal.hjplugin.RTCLoginInfo;

public class Helper {
	private static final Logger LOGGER = Logger.getLogger(Helper.class.getName());
	
	private static final String previousSnapshotOwner = "team_scm_snapshotOwner"; //$NON-NLS-1$

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
			if (firstCheck.kind.equals(FormValidation.Kind.ERROR)
					|| (firstCheck.kind.equals(FormValidation.Kind.WARNING) && secondCheck.kind.equals(FormValidation.Kind.OK))) {
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
	 * @param parameter - the job parameter. Never <code>null</code>
	 * @param listener - task listener. Never <code>null</code>
	 * @return the value of the job parameter or <code>property</code> if the job parameter is not defined or 
	 * if the given property is not a job parameter 
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
	 * 
	 * @param job
	 * @param jobParameter
	 * @param listener
	 * @return
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
	 * @param build
	 * @param facade
	 * @param loginInfo
	 * @param streamName
	 * @param clientLocale
	 * @return
	 * @throws Exception
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
		}, toolkit, loginInfo, processArea, buildStream, onlyGoodBuild, "team_scm_snapshotUUID", clientLocale);
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("Helper.getSnapshotUUIDFromPreviousBuild : " + 
						((snapshotDetails.getSecond() == null) ? "No snapshotUUID found from a previous build" : snapshotDetails.getSecond()));
		}
		return snapshotDetails;
	}
	
	/**
	 * Returns the stream change data from the previous build irrespective of its status
	 * @param job
	 * @param toolkit
	 * @param loginInfo
	 * @param processArea
	 * @param buildStream
	 * @param clientLocale
	 * @return
	 * @throws Exception
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
			}}, toolkit, loginInfo, processArea, buildStream, false, "team_scm_streamChangesData", clientLocale);
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
					String owningStreamUUID = Util.fixEmptyAndTrim(buildProperties.get(previousSnapshotOwner));
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
						String owningStreamUUID = Util.fixEmptyAndTrim(buildProperties.get(previousSnapshotOwner));
						if (owningStreamUUID != null && owningStreamUUID.equals(streamUUID)) {
							value = buildProperties.get(key);
							previousBuild = build;
							// We don't we break here to maintain the following behavior for pipeline jobs
							// if we load the same stream multiple times, we get the the value from the last
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
	 * 
	 **/
	private static String getStringBuildParameter(Job<?,?> job, String parameter, TaskListener listener) {

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
}
