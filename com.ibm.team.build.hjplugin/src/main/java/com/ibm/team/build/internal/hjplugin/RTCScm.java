/*******************************************************************************
 * Copyright © 2013, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.localizer.LocaleProvider;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.extensions.RtcExtensionProvider;
import com.ibm.team.build.internal.hjplugin.tasks.GenerateChangelogTask;
import com.ibm.team.build.internal.hjplugin.tasks.RetrieveWorkspaceDetailsTask;
import com.ibm.team.build.internal.hjplugin.util.Helper;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;
import com.ibm.team.build.internal.hjplugin.util.Tuple;
import com.ibm.team.build.internal.hjplugin.util.ValidationResult;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.Queue.Task;
import hudson.remoting.RemoteOutputStream;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

@ExportedBean(defaultVisibility=999)
public class RTCScm extends SCM {
	
	private static final BigInteger BIGINT_ZERO = new BigInteger("0"); //$NON-NLS-1$
	
	private static final BigInteger BIGINT_ONE = new BigInteger("1");  //$NON-NLS-1$
	
	public static final Logger LOGGER = Logger.getLogger(RTCScm.class.getName());
	
	private static final String METRONOME_OPTIONS_PROPERTY_NAME = "metronomeOptions"; //$NON-NLS-1$
    
	private static final String METRONOME_DATA_PROPERTY_NAME = "metronomeData"; //$NON-NLS-1$

	private static final String CALL_CONNECTOR_TIMEOUT_PROPERTY = "com.ibm.team.build.callConnector.timeout"; //$NON-NLS-1$
	
	private static final String IGNORE_OUTGOING_FROM_BUILD_WS_WHILE_POLLING = "com.ibm.team.build.ignoreOutgoingFromBuildWorkspaceWhilePolling"; //$NON-NLS-1$
	
	private static final String JOB_PROPERTY_OVERRIDES_SYSTEM_PROPERTY = "com.ibm.team.build.jobPropertyOverride"; //$NON-NLS-1$
	
	private static final String DEPRECATED_CREDENTIAL_EDIT_ALLOWED = "com.ibm.team.build.credential.edit"; //$NON-NLS-1$
	
	private static final String TEAMCONCERT_FOLDER_NAME = "teamconcert"; //$NON-NLS-1$
	
	private static final String TEAMCONCERT_METRONOME_NAME = "diagnostics"; //$NON-NLS-1$
	
	private static final String STATISTICS_DATA_FILE_SUFFIX_VALUE = ".csv";
	
	private static final String STATISTICS_REPORT_FILE_SUFFIX_VALUE = ".log";

	private static final String STATISTICS_DATA_VALUE = "Statistics Data";

	private static final String STATISTICS_REPORT_VALUE = "Statistics Report";

	private static final String STATISTICS_DATA_FILE_PREFIX_VALUE = "statisticsData-";

	private static final String STATISTICS_REPORT_FILE_PREFIX_VALUE = "statistics-";

	private static final String STATISTICS_DATA_LABEL_PROPERTY_NAME = "statisticsDataLabel";

	private static final String STATISTICS_REPORT_LABEL_PROPERTY_NAME = "statisticsReportLabel";

	public static final String STATISTICS_DATA_FILE_PREFIX_PROPERTY_NAME = "statisticsDataFilePrefix";
	
	private static final String STATISTICS_REPORT_FILE_PREFIX_PROPERTY_NAME = "statisticsReportFilePrefix";
	
	private static final String STATISTICS_DATA_FILE_SUFFIX_PROPERTY_NAME = "statisticsDataFileSuffix";
	
	private static final String STATISTICS_REPORT_FILE_SUFFIX_PROPERTY_NAME = "statisticsReportFileSuffix";

	
	// persisted fields for SCM
    private boolean overrideGlobal;
    
    // Global setting that have been overridden by the job (if overrideGlobal is true)
    private String buildTool;
	private String serverURI;
	private int timeout;
	private String userId;
	private Secret password;
	private String passwordFile;
	private String credentialsId;

	public static final String BUILD_WORKSPACE_TYPE = RTCJobProperties.BUILD_WORKSPACE_TYPE;
	public static final String BUILD_DEFINITION_TYPE = RTCJobProperties.BUILD_DEFINITION_TYPE;
	public static final String BUILD_SNAPSHOT_TYPE = "buildSnapshot"; //$NON-NLS-1$
	public static final String BUILD_STREAM_TYPE = "buildStream"; //$NON-NLS-1$
	
	// constants for the possible values for snapshot owner type 
	public static final String SNAPSHOT_OWNER_TYPE_NONE = "none"; //$NON-NLS-1$
	public static final String SNAPSHOT_OWNER_TYPE_STREAM = "stream"; //$NON-NLS-1$
	public static final String SNAPSHOT_OWNER_TYPE_WORKSPACE = "workspace"; //$NON-NLS-1$
	
	// constants for the keys that identify various fields in the snapshot context map
	public static final String SNAPSHOT_OWNER_TYPE_KEY = "snapshotOwnerType"; //$NON-NLS-1$
	public static final String PROCESS_AREA_OF_OWNING_STREAM_KEY = "processAreaOfOwningStream"; //$NON-NLS-1$
	public static final String OWNING_STREAM_KEY = "owningStream"; //$NON-NLS-1$
	public static final String OWNING_WORKSPACE_KEY = "owningWorkspace"; //$NON-NLS-1$
	
	public static final String LOAD_POLICY_USE_LOAD_RULES = "useLoadRules"; //$NON-NLS-1$
	public static final String LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG = "useComponentLoadConfig"; //$NON-NLS-1$
	public static final String LOAD_POLICY_USE_DYNAMIC_LOAD_RULES = "useDynamicLoadRules"; //$NON-NLS-1$
	
	public static final List<String> validLoadPolicyValues = Arrays.asList(LOAD_POLICY_USE_LOAD_RULES, LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG,
			LOAD_POLICY_USE_DYNAMIC_LOAD_RULES);

	public static final String COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS = "loadAllComponents"; //$NON-NLS-1$
	public static final String COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS = "excludeSomeComponents"; //$NON-NLS-1$

	public static final List<String> validComponentLoadConfigValues = Arrays.asList(COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS,
			COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS);

	public static final int DEFAULT_SERVER_TIMEOUT = DescriptorImpl.DEFAULT_SERVER_TIMEOUT;

	// Job configuration settings
	private BuildType buildType;
	
	private String buildTypeStr;
	private String buildWorkspace;
	private String buildDefinition;
	private String buildSnapshot;
	private String buildStream;
	private String loadDirectory;
	private boolean clearLoadDirectory;
	private boolean createFoldersForComponents;
	private String acceptBeforeLoad;
	private boolean generateChangelogWithGoodBuild;
	
	// Don't persist the browser because it references the server url that can be changing in the
	// global config.
	@Deprecated 
	private transient RTCRepositoryBrowser browser;

	// Use rest or whatever services instead of the build toolkit.
	// Available in RTC 5.0 or 4.0.6 + retro-fitted changes
	private boolean avoidUsingToolkit;
	// process area associated with the configuration. Currently applicable only to stream configuration and it is used
	// to lookup the stream by name.
	private String processArea;
	// see the config file for significance of this field
	private String currentSnapshotOwnerType;
	// object containing the snapshot owner details
	private BuildSnapshotContext buildSnapshotContext;
	// determines if we have to override the default name given to the snapshot that is created after accepting the
	// changes
	private boolean overrideDefaultSnapshotName;
	// snapshot name, configured in the job, that overrides the default name given to the snapshot that is created after
	// accepting the changes
	private String customizedSnapshotName;	
	
	// load policy field - determines whether to use load rule file or load the specified components
	private String loadPolicy;
	// component load config field - when load policy is set to use component load config this field determines whether
	// to load all components or exclude some components
	private String componentLoadConfig;
	// comma separated list of id/name of components
	private String componentsToExclude;
	// path to the load rule file.
	// right now only remote path to the load rule file in the format<component name>/<remote path to the load rule file> is supported
	private String pathToLoadRuleFile;
	// property specific to buildDefinition configuration that determines whether to invoke dynamic load rule generator.
	// When set to true, the loadPolicy value is set to useDynamicLoadRules, irrespective of the value already set in
	// build definition
	private boolean useDynamicLoadRules;
	
	/**
	 *  When set to true, a <em>Related Artifact</em> link pointing to the Jenkins build
	 *  will be added to the work item. This option takes effect only when the 
	 *  job type is "Build Workspace" or "Stream"
	 */
	private boolean addLinksToWorkItems;
	
	/**
	 * From 2.3.0, a new boolean parameter <code>pollingOnly</code> has been added 
	 * which controls the core behavior of RTCScm.  
	 * 
	 * When <code>pollingOnly</code> is set to <code>true</code>, then RTCScm will perform 
	 * polling only but will not accept or load the repository workspace.  
	 *  
	 * 
	 *
	 * In the default mode, RTCScm will perform the following activities
	 * <ul>
	 * <li>Poll, if polling is enabled at the job level</li>
	 * <li>accept</li>
	 * <li>load</li>
	 * <li>recording changelog</li>
	 * <li>Updating jenkins build with EWM specific data</li>
	 * </ul>
	 * 
	 * If <code>pollyOnly</code> is set to <em>true</em>, then RTCScm will perform 
	 * polling only but will not perform the following actions,
	 * <ul>
	 * <li>accept</li>
	 * <li>load</li>
	 * <li>recording changelog</li>
	 * <li>Updating jenkins build with specific data</li>
	 * </ul>
	 * 
	 * Evolution and backward compatibility:
	 *   For jobs created before 2.3.0, the  parameter's value will be <em>false</em>.
	 *   For new jobs, the mode parameter's value will be <em>false</em>.
	 * 
	 * Constraints:
	 *   pollingOnly is supported for pipeline jobs
	 *   pollingOnly is supported build definition and build workspace types.
	 *   
	 * Exception behavior:
	 * During polling, if pollingOnly is set for any job type other than pipeline job, an exception is thrown.
	 * During build, if pollingOnly is set for any job type other than pipeline job, an exception is thrown
	 * During polling, if pollingOnly is set for any other build type other than build definition or workspace, an exception is thrown. 
	 * During build, if pollingOnly is set for any other build type other than build definition or workspace, an exception is thrown.  
	 */
	private boolean pollingOnly;
	
	private RTCBuildResultAction buildResultAction = null;

	private PollingOnlyData pollingOnlyData;

	/**
	 * Class defining the snapshot context configuration data.
	 */
	public static class BuildSnapshotContext {
		public String snapshotOwnerType;
		public String processAreaOfOwningStream;
		public String owningStream;
		public String owningWorkspace;
		
		@DataBoundConstructor
		public BuildSnapshotContext(String snapshotOwnerType, String processAreaOfOwningStream, String owningStream, String owningWorkspace) {
			this.snapshotOwnerType = snapshotOwnerType;
			this.processAreaOfOwningStream = processAreaOfOwningStream;
			this.owningStream = owningStream;
			this.owningWorkspace = owningWorkspace;
		}
		
		/**
		 * Construct the map with the current instance.
		 * 
		 */
		public Map<String, String> getContextMap() {
			return getBuildSnapshotContextMap(snapshotOwnerType, processAreaOfOwningStream, owningStream, owningWorkspace);
		}
		
		/**
		 * Construct the map with the provided details.
		 * 
		 * Snapshot context is a complex datastructure. Since complex datastructures cannot be shared through a data
		 * model between hjplugin.jar and hjplugin-rtc.jar, the complex data structure is converted to a map of fields
		 * and values; this map, that is sent by hjplugin.jar, is read and converted to a object by hjplugin-rtc.jar.
		 * Though this is hacky, it is better than passing individual fields. This class has to be retained until we
		 * design a permanent solution to pass complex data structures between the two jars.
		 */
		public static Map<String, String> getBuildSnapshotContextMap(String snapshotOwnerType, String processAreaOfOwningStream, String owningStream,
				String owningWorkspace) {
			Map<String, String> contextMap = new HashMap<String, String>();
			contextMap.put(SNAPSHOT_OWNER_TYPE_KEY, snapshotOwnerType);
			contextMap.put(PROCESS_AREA_OF_OWNING_STREAM_KEY, processAreaOfOwningStream);
			contextMap.put(OWNING_STREAM_KEY, owningStream);
			contextMap.put(OWNING_WORKSPACE_KEY, owningWorkspace);
			return contextMap;
		}
	}
	
	public static class PollingOnlyData {
		private String snapshotUUID;
		
		@DataBoundConstructor
		public PollingOnlyData(String snapshotUUID) {
			this.snapshotUUID = Util.fixEmptyAndTrim(snapshotUUID);
		}
		
		public String getSnapshotUUID() {
			return snapshotUUID;
		}
	}
	
	/**
	 * Structure that represents the radio button selection for build workspace, stream, snapshot definition
	 * choice in config.jelly (job configuration) 
	 */
	public static class BuildType {
		public String value;
		public String buildDefinition;
		public String buildWorkspace;
		public String buildSnapshot;
		public String buildStream;	
		public String loadDirectory;
		public boolean clearLoadDirectory;
		public boolean createFoldersForComponents;
		private String acceptBeforeLoad = "true"; //$NON-NLS-1$
		private boolean addLinksToWorkItems;
		private boolean generateChangelogWithGoodBuild;
		private String processArea;
		private String currentSnapshotOwnerType;
		private BuildSnapshotContext buildSnapshotContext;
		private boolean overrideDefaultSnapshotName;
		private String customizedSnapshotName;
		private String loadPolicy;
		private String componentLoadConfig;
		private String componentsToExclude;
		private String pathToLoadRuleFile;
		private boolean useDynamicLoadRules;
		private boolean pollingOnly = false;
		private PollingOnlyData pollingOnlyData;
		
		@DataBoundConstructor
		public BuildType(String value, String buildDefinition, String buildWorkspace, String buildSnapshot, String buildStream) {
			this.value = value;
			this.buildDefinition = buildDefinition;
			this.buildWorkspace = buildWorkspace;
			this.buildSnapshot = buildSnapshot;
			this.buildStream = buildStream;
		}
		
		@DataBoundSetter
		public void setLoadDirectory(String loadDirectory) {
			this.loadDirectory = loadDirectory;
		}

		public String getLoadDirectory() {
			return this.loadDirectory;
		}

		@DataBoundSetter
		public void setClearLoadDirectory(boolean clearLoadDirectory) {
			this.clearLoadDirectory = clearLoadDirectory;
		}
		
		public boolean getClearLoadDirectory() {
			return clearLoadDirectory;
		}
		
		@DataBoundSetter
		public void setCreateFoldersForComponents(boolean createFoldersForComponents) {
			this.createFoldersForComponents = createFoldersForComponents;
		}
		
		public boolean getCreateFoldersForComponents() {
			return createFoldersForComponents;
		}
		
		@DataBoundSetter
		public void setAcceptBeforeLoad(boolean acceptBeforeLoad) {
			this.acceptBeforeLoad = String.valueOf(acceptBeforeLoad);
		}
		
		public boolean getAcceptBeforeLoad() {
			return Boolean.parseBoolean(acceptBeforeLoad);
		}
		
		public boolean getGenerateChangelogWithGoodBuild() {
			return generateChangelogWithGoodBuild;
		}
		
		@DataBoundSetter
		public void setGenerateChangelogWithGoodBuild(boolean generateChangelogWithGoodBuild) {
			this.generateChangelogWithGoodBuild = generateChangelogWithGoodBuild;
		}
		
		@DataBoundSetter
		public void setProcessArea(String processArea) {
			this.processArea = processArea;
		}
		
		public String getProcessArea() {
			return processArea;
		}
		
		@DataBoundSetter
		public void setCurrentSnapshotOwnerType(String currentSnapshotOwnerType) {
			this.currentSnapshotOwnerType = currentSnapshotOwnerType;
		}
		
		public String getCurrentSnapshotOwnerType() {
			return currentSnapshotOwnerType;
		}
		
		@DataBoundSetter
		public void setBuildSnapshotContext(BuildSnapshotContext buildSnapshotContext) {
			this.buildSnapshotContext = buildSnapshotContext;
		}
		
		public BuildSnapshotContext getBuildSnapshotContext() {
			return buildSnapshotContext;
		}
		
		@DataBoundSetter
		public void setOverrideDefaultSnapshotName(boolean overrideDefaultSnapshotName) {
			this.overrideDefaultSnapshotName = overrideDefaultSnapshotName;
		}
		
		public boolean getOverrideDefaultSnapshotName() {
			return overrideDefaultSnapshotName;
		}
		
		@DataBoundSetter
		public void setCustomizedSnapshotName(String customizedSnapshotName) {
			this.customizedSnapshotName = customizedSnapshotName;
		}
		
		public String getCustomizedSnapshotName() {
			return customizedSnapshotName;
		}
		
		@DataBoundSetter
		public void setLoadPolicy(String loadPolicy) {
			this.loadPolicy = loadPolicy;
		}

		public String getLoadPolicy() {
			return this.loadPolicy;
		}		
		
		@DataBoundSetter
		public void setComponentLoadConfig(String componentLoadConfig) {
			this.componentLoadConfig = componentLoadConfig;
		}

		public String getComponentLoadConfig() {
			return this.componentLoadConfig;
		}		
		
		@DataBoundSetter
		public void setComponentsToExclude(String componentsToExclude) {
			this.componentsToExclude = componentsToExclude;
		}
		
		public String getComponentsToExclude() {
			return this.componentsToExclude;
		}
				
		@DataBoundSetter
		public void setPathToLoadRuleFile(String pathToLoadRuleFile) {
			this.pathToLoadRuleFile = pathToLoadRuleFile;
		}

		public String getPathToLoadRuleFile() {
			return this.pathToLoadRuleFile;
		}
		
		@DataBoundSetter
		public void setUseDynamicLoadRules(boolean useDynamicLoadRules) {
			this.useDynamicLoadRules = useDynamicLoadRules;
		}

		public boolean getUseDynamicLoadRules() {
			return useDynamicLoadRules;
		}
		
		@DataBoundSetter
		public void setAddLinksToWorkItems(boolean addLinksToWorkItems) {
			this.addLinksToWorkItems = addLinksToWorkItems;
		}

		public boolean getAddLinksToWorkItems() {
			return this.addLinksToWorkItems;
		}

		
		@DataBoundSetter
		public void setPollingOnlyData(PollingOnlyData pollingOnlyData) {
			if (pollingOnlyData == null) {
				this.setPollingOnly(false);
			} else {
				this.setPollingOnly(true);
				this.pollingOnlyData = pollingOnlyData;
			}
		}
		
		public PollingOnlyData getPollingOnlyData() {
			return this.pollingOnlyData;
		}

		/**
		 * Retained for ease of use
		 * 
		 * @param pollingOnly
		 */
		@DataBoundSetter
		public void setPollingOnly(boolean pollingOnly) {
			this.pollingOnly = pollingOnly;
		}
		
		public boolean getPollingOnly() {
			return this.pollingOnly;
		}
	}
	
	// Descriptor class - contains the global configuration settings
	@Extension
	public static class DescriptorImpl extends SCMDescriptor<RTCScm> {
		
		private static final String DEFAULT_SERVER_URI = "https://localhost:9443/ccm"; //$NON-NLS-1$

		private static final int DEFAULT_SERVER_TIMEOUT = 480;
		
		private static transient boolean deprecatedCredentialEditAllowed = Boolean.getBoolean(DEPRECATED_CREDENTIAL_EDIT_ALLOWED);
		
		// persisted fields

		private String globalBuildTool;
		private String globalServerURI;
		private int globalTimeout;
		private String globalCredentialsId;

		// explicit UserId & password or password file
		private String globalUserId;
		private Secret globalPassword;
		private String globalPasswordFile;
		
		// Use rest or whatever services instead of the build toolkit.
		// Available in RTC 5.0 or 4.0.6 + retro-fitted changes
		private boolean globalAvoidUsingToolkit;
		
		public DescriptorImpl() {
			super(RTCScm.class, RTCRepositoryBrowser.class);
			load();
		}

		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
 			RTCScm scm = (RTCScm) super.newInstance(req, formData);
			
 			new RTCRepositoryBrowser(scm.getServerURI());
			return scm;
		}

		@Override
		public String getDisplayName() {
			return Messages.RTCScm_RTC_display_name();
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException {
			LOGGER.finest("DescriptorImpl.configure: Begin");
			globalBuildTool = Util.fixEmptyAndTrim(json.optString("buildTool")); //$NON-NLS-1$
			globalServerURI = Util.fixEmptyAndTrim(json.optString("serverURI")); //$NON-NLS-1$
			globalUserId = Util.fixEmptyAndTrim(json.optString("userId")); //$NON-NLS-1$
			String timeout = json.optString("timeout"); //$NON-NLS-1$
			globalAvoidUsingToolkit = json.containsKey("avoidUsingToolkit"); //$NON-NLS-1$
			
			try {
				globalTimeout = timeout == null ? 0 : Integer.parseInt(timeout);
			} catch (NumberFormatException e) {
				globalTimeout = 0;
			}
			String password = json.optString("password"); //$NON-NLS-1$
			globalPassword = password == null || password.length() == 0 ? null : Secret.fromString(password);
			globalPasswordFile = Util.fixEmptyAndTrim(json.optString("passwordFile")); //$NON-NLS-1$
			globalCredentialsId = Util.fixEmptyAndTrim(json.optString("credentialsId")); //$NON-NLS-1$
			if (globalCredentialsId != "") {
				// They are saving with credentials. Remove the vestiges of the old authentication method
				globalPassword = null;
				globalPasswordFile = null;
				globalUserId = null;
			}

			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("configure : " + //$NON-NLS-1$
						"\" globalServerURI=\"" + globalServerURI + //$NON-NLS-1$
						"\" globalUserid=\"" + globalUserId + //$NON-NLS-1$
						"\" globalTimeout=\"" + globalTimeout + //$NON-NLS-1$
						"\" globalPassword " + (globalPassword == null ? "is not supplied" //$NON-NLS-1$ //$NON-NLS-2$
								: "(" + Secret.toString(globalPassword).length() + " characters)") + //$NON-NLS-1$ //$NON-NLS-2$
						" globalPasswordFile=\"" + globalPasswordFile + //$NON-NLS-1$ //$NON-NLS-2$
						"\" globalCredentialsId=\"" + globalCredentialsId + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			save();
		    return true;
		}

		public String getGlobalBuildTool() {
			return globalBuildTool;
		}

		/**
		 * Determine if they are using the deprecated authentication way
		 * @return <code>true</code> if using user id & password or password file
		 * <code>false</code> if using credentials or no password authentication setup.
		 */
		public boolean usingDeprecatedPassword() {
			if (deprecatedCredentialEditAllowed()) {
				return true;
			}
			String globalCredId = getGlobalCredentialsId();
			String userIdToUse = getGlobalUserId();
			String passwordToUse = getGlobalPassword();
			String passwordFileToUse = getGlobalPasswordFile();
			if (globalCredId == null || globalCredId.isEmpty()) {
				// consider them to be using user id & old password way if supplied
				// not using strict validation since we would still work if too much info supplied.
				if (userIdToUse != null	&& !userIdToUse.isEmpty()
						&& ((passwordToUse != null && !passwordToUse.isEmpty())
							|| (passwordFileToUse != null && !passwordFileToUse.isEmpty()))) {
					return true;
				}
			}
			return false;
		}
		
		/**
		 * @return Whether the global password file should be shown in the UI
		 */
		public boolean showGlobalPasswordFile() {
			String passwordFileToUse = getGlobalPasswordFile();
			if (deprecatedCredentialEditAllowed() || (passwordFileToUse != null && !passwordFileToUse.isEmpty())) {
				return true;
			}
			return false;
		}
		
		/**
		 * @return Whether the global password should be shown in the UI
		 */
		public boolean showGlobalPassword() {
			String passwordToUse = getGlobalPassword();
			if (deprecatedCredentialEditAllowed() || (passwordToUse != null && !passwordToUse.isEmpty())) {
				return true;
			}
			return false;
		}
		public boolean deprecatedCredentialEditAllowed() {
			return deprecatedCredentialEditAllowed;
		}

		public String getGlobalCredentialsId() {
			return globalCredentialsId;
		}

		public String getGlobalPassword() {
			String result;
			if (globalPassword == null) {
				result = null;
			} else {
				result = globalPassword.getPlainText();
			}
			return result;
		}
		
		public String getGlobalPasswordFile() {
			return globalPasswordFile;
		}
		
		public String getGlobalServerURI() {
			if (globalServerURI == null || globalServerURI.length() == 0) {
				return DEFAULT_SERVER_URI;
			}
			return globalServerURI;
		}
		
		public String getGlobalUserId() {
			return globalUserId;
		}
		
	    public int getGlobalTimeout() {
	    	if (globalTimeout == 0) {
	    		return DEFAULT_SERVER_TIMEOUT;
	    	}
    		return globalTimeout;
	    }
	    
	    public boolean getGlobalAvoidUsingToolkit() {
	    	return globalAvoidUsingToolkit;
	    }
		
	    /**
	     * Get the path on the Master to the build toolkit for the given Build tool.
	     * @param buildTool The id of the build tool
		 * @param listener Listener to log any problems encountered.
		 * @return Path to the Build Toolkit directory
	     * @throws IOException
	     * @throws InterruptedException
	     */
		public String getMasterBuildToolkit(String buildTool, TaskListener listener) throws IOException, InterruptedException {
			return getBuildToolkit(buildTool, Hudson.getInstance(), listener);
		}
		
		@Override
		public boolean isApplicable(Job project) {
			return true;
		}
		
		/**
		 * For a given build tool on a given node, resolve what directory is expected to contains the build toolkit.
		 * Jenkins provides tool installation abilities as well as a way of describing where a tool is located on 
		 * as Slave. We will delegate the path resolution to Jenkins.
		 * @param buildTool The build tool that we want the path for
		 * @param node The node (Master or a particular slave) the build toolkit will used on. 
		 * @param listener Listener to log any problems encountered.
		 * @return Path to the Build Toolkit directory
		 * @throws IOException
		 * @throws InterruptedException
		 */
		public String getBuildToolkit(String buildTool, Node node, TaskListener listener) throws IOException, InterruptedException {
	        RTCBuildToolInstallation[] installations = RTCBuildToolInstallation.allInstallations();
	        for (RTCBuildToolInstallation buildToolIntallation : installations) {
	        	if (buildToolIntallation.getName().equals(buildTool)) {
	        		return buildToolIntallation.forNode(node, listener).getHome();
	        	}
	        }
			return null;
		}

		/**
		 * Provides a listbox of the defined build tools to pick from. Also includes
		 * an entry to signify no toolkit is chosen.
		 * @return The valid build tool options
		 */
		public ListBoxModel doFillBuildToolItems() {
			ListBoxModel listBox = new ListBoxModel();
			listBox.add(new ListBoxModel.Option(Messages.RTCScm_no_build_tool_name(), ""));
			RTCBuildToolInstallation[] allTools = RTCBuildToolInstallation.allInstallations();
			for (RTCBuildToolInstallation tool : allTools) {
				ListBoxModel.Option option = new ListBoxModel.Option(tool.getName());
				listBox.add(option);
			}
			return listBox;
		}

		@RequirePOST
		public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?, ?> project,
								@QueryParameter String serverURI) {
			if (project == null) {
				// This is called in the context of global settings page also
				Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
			} else {
				project.checkPermission(Item.CONFIGURE);
			}
			return new StandardListBoxModel()
			.withEmptySelection()
			.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM, URIRequirementBuilder.fromUri(serverURI).build()));

			// TODO look into us being able to support certificates
//			return new StandardListBoxModel()
//			.withEmptySelection()
//			.withMatching(CredentialsMatchers.anyOf(
//					CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
//					CredentialsMatchers.instanceOf(StandardCertificateCredentials.class)),
//					CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM, URIRequirementBuilder.fromUri(url).build()));
		}

		/**
		 * Called from the forms to validate the timeout value.
		 * @param timeout The timeout value.
		 * @return Whether the timeout is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckTimeout(@QueryParameter String timeout) {
			return RTCLoginInfo.validateTimeout(timeout);
		}

		/**
		 * Called from the forms to validate that the build tool is selected
		 * and that the underlying build tool points to a valid build toolkit.
		 * @param buildTool The name of the build tool to validate
		 * @return Whether the build tool is valid or not. Never <code>null</code>
		 */
		public FormValidation doCheckBuildTool(
				@QueryParameter("buildTool") String buildTool) {
			LOGGER.finest("DescriptorImpl.doCheckBuildTool: Begin");
			if (Util.fixEmptyAndTrim(buildTool) == null) {
				return FormValidation.error(Messages.RTCScm_build_tool_needed_for_job());
			}

			// validate the build toolkit path
			String buildToolkitPath;
			try {
				buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
			} catch (Exception e) {
				return FormValidation.error(e, Messages.RTCScm_no_build_toolkit(e.getMessage()));
			}
			FormValidation buildToolkitCheck = RTCBuildToolInstallation.validateBuildToolkit(false, buildToolkitPath);

			if (!buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
				return buildToolkitCheck;
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckCredentialsId(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			LOGGER.finest("DescriptorImpl.doCheckCredentialsId: Begin");
			return RTCLoginInfo.validateCredentials(credentialsId, userId,
					passwordFile, password);
		}
		
		public FormValidation doCheckUserId(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			return RTCLoginInfo.validateUserId(credentialsId, userId,
					passwordFile, password);
		}

		public FormValidation doCheckPassword(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			return RTCLoginInfo.validatePassword(credentialsId, userId,
					passwordFile, password);
		}

		public FormValidation doCheckPasswordFile(
				@QueryParameter("credentialsId") String credentialsId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile) {
			return RTCLoginInfo.validatePasswordFile(credentialsId, userId,
					passwordFile, password);
		}

		/**
		 * For the default global configuration check that the connection works (with whatever
		 * access preferred). Main idea is check server, user credentials are valid for logging in.
		 * @param buildTool The build tool selected to be used in builds
		 * @param serverURI The RTC server uri
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied.
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied.
		 * @param credId Credential id that will identify the user id and password to use
		 * @param timeout The timeout period for the connection
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection.
	     * @return The result of the validation (will be ok if valid)
		 */
		@RequirePOST
		public FormValidation doCheckGlobalConnection(
				@QueryParameter("buildTool") final String buildTool,
				@QueryParameter("serverURI") final String serverURI,
				@QueryParameter("userId") final String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("timeout") final String timeout,
				@QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit) {
			LOGGER.finest("DescriptorImpl.doCheckGlobalConnection: Begin");
			Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
			boolean avoidUsingToolkit = Boolean.parseBoolean(Util.fixNull(avoidUsingBuildToolkit));
			ValidationResult result = validateConnectInfo(null, true, buildTool, serverURI, userId, password, passwordFile, credId, timeout, avoidUsingToolkit);

			// validate the connection
			if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return result.validationResult;
			} else {
				FormValidation connectCheck = checkConnect(result.buildToolkitPath, avoidUsingToolkit, result.loginInfo);
				return Helper.mergeValidationResults(result.validationResult, connectCheck);
			}
		}

		/**
		 * For the job configuration check that the connection works (with whatever
		 * access preferred). Use either the information supplied or the previously stored
		 * global information depending on whether to override the global information.
		 * Main idea is check server, user credentials are valid for logging in.
		 * @param override Whether to use the global settings or not
		 * @param buildTool The build tool selected to be used in builds
		 * @param serverURI The RTC server uri
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied.
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied.
		 * @param credId Credential id that will identify the user id and password to use
		 * @param timeout The timeout period for the connection
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection.
	     * @return The result of the validation (will be ok if valid)
		 */
		@RequirePOST
		public FormValidation doCheckJobConnection(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit) {
			LOGGER.finest("DescriptorImpl.doCheckJobConnection: Begin");
			if (project == null) {
				return FormValidation.ok();
			} else {
				project.checkPermission(Item.CONFIGURE);
			}
			boolean overrideGlobal = Boolean.parseBoolean(Util.fixNull(override));
			boolean avoidUsingToolkit;
			if (!overrideGlobal) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(Util.fixNull(avoidUsingBuildToolkit));
			}

			ValidationResult result = validateConnectInfo(project, !overrideGlobal, buildTool, serverURI, userId, password, passwordFile, credId, timeout, avoidUsingToolkit);

			// validate the connection
			if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return result.validationResult;
			} else {
				FormValidation connectCheck = checkConnect(result.buildToolkitPath, avoidUsingToolkit, result.loginInfo);
				return Helper.mergeValidationResults(result.validationResult, connectCheck);
			}
		}

		/**
		 * Check that we can connect to the RTC server. If avoiding the toolkit, we
		 * will not validate it since it will not be used. 
		 * @param checkingGlobalSettings Whether the settings are global or job. Affects
		 * the error message given.
		 * @param buildTool The build tool configured. Only used if avoidUsingBuildToolkit is off
		 * @param serverURI The RTC server to connect to
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied.
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied.
		 * @param credId Credential id that will identify the user id and password to use
		 * @param timeout The timeout period for the connection
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection.
	     * @return The result of the validation (will be ok if valid)
		 */
		private ValidationResult validateConnectInfo(
				final Job<?,?> project,
				final boolean checkingGlobalSettings,
				final String buildTool,
				final String serverURI,
				final String userId,
				final String password,
				String passwordFile,
				String credId,
				final String timeout,
				final boolean avoidUsingToolkit) {
			LOGGER.finest("DescriptorImpl.validateConnectInfo: Begin");
			passwordFile = Util.fixEmptyAndTrim(passwordFile);
			credId = Util.fixEmptyAndTrim(credId);

			ValidationResult result = new ValidationResult();

			// in the global case the build tool doesn't need to really be supplied. It could be supplied
			// by the Job. However, if they want to check the connection, they would need to have avoid using toolkit
			// enabled and not use a password file.
			boolean warnOnly = avoidUsingToolkit && (credId != null || passwordFile == null);
			

			// we need the build tool to validate
			if (Util.fixEmptyAndTrim(buildTool) == null) {
				String errorMessage;
				if (warnOnly) {
					if (checkingGlobalSettings) {
						errorMessage = Messages.RTCScm_global_build_tool_needed_for_job();
					} else {
						errorMessage = Messages.RTCScm_build_tool_needed_for_job();
					}
					result.validationResult = FormValidation.warning(errorMessage);
				} else {
					if (checkingGlobalSettings) {
						errorMessage = Messages.RTCScm_missing_global_build_tool();
					} else {
						errorMessage = Messages.RTCScm_missing_build_tool();
					}
					result.validationResult = FormValidation.error(errorMessage);
					return result;
				}
			} else {

				// validate the build toolkit path
				try {
					result.buildToolkitPath = getMasterBuildToolkit(buildTool, TaskListener.NULL);
				} catch (Exception e) {
					String errorMessage;
					if (checkingGlobalSettings) {
						errorMessage = Messages.RTCScm_no_global_build_toolkit3(e.getMessage());
					} else {
						errorMessage = Messages.RTCScm_no_build_toolkit(e.getMessage());
					}
					if (warnOnly) {
						result.validationResult = FormValidation.warning(errorMessage);
					} else {
						result.validationResult = FormValidation.error(errorMessage);
					}
					return result;
				}
			
				result.validationResult = RTCBuildToolInstallation.validateBuildToolkit(warnOnly, result.buildToolkitPath);
				if (result.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
					return result;
				}
			}
			
			// validate the authentication information
	    	FormValidation basicValidate = RTCLoginInfo.basicValidate(credId, userId, passwordFile, password, timeout);
	    	if (basicValidate.kind.equals(FormValidation.Kind.ERROR)) {
	    		result.validationResult = Helper.mergeValidationResults(result.validationResult, basicValidate);
	    		return result;
	    	}
			
			try {
				result.loginInfo = new RTCLoginInfo(project, result.buildToolkitPath,
						serverURI, userId, password, passwordFile, credId,
						Integer.parseInt(timeout));
			} catch (InvalidCredentialsException e) {
				result.validationResult = FormValidation.error(e, e.getMessage());
			}
			return result;
		}
		
		/**
		 * Check the connection to the RTC server. This will validate that we can connect to the server with the
		 * credentials defined. Connection may be through the toolkit or Rest services. This means a version
		 * compatibility check is also done.
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingTookit Whether to try to avoid using the build toolkit
		 * @param loginInfo The credentials for logging into the server.
		 * @return Whether the connection is valid or not. Never <code>null</code>
		 */
		private FormValidation checkConnect(String buildToolkitPath, boolean avoidUsingTookit, RTCLoginInfo loginInfo) {
			LOGGER.finest("DescriptorImpl.checkConnect: Begin");
	    	try {
				String errorMessage = RTCFacadeFacade.testConnection(buildToolkitPath,
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), avoidUsingTookit);
				if (errorMessage != null && errorMessage.length() != 0) {
	    			return FormValidation.error(errorMessage);
				}
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport == null) {
	    			eToReport = e;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkConnect attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkConnect invocation failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkConnect attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkConnect failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, Messages.RTCScm_failed_to_connect(e.getMessage()));
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_connect_success());
	    }
		
		/**
		 * Validates the build workspace configuration. Called by the forms. Fields validated apart from connection info
		 * - Build Workspace, Path to the load rule file.
		 * 
		 * @param project The Job that is going to be run
		 * @param override Whether to override the global connection settings
		 * @param buildTool The build tool selected to be used in builds (Job setting)
		 * @param serverURI The RTC server uri (Job setting)
		 * @param timeout The timeout period for the connection (Job setting)
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id (Job setting)
		 * @param password The password to use when logging in to RTC. Must supply this or a password file if the
		 *            credentials id was not supplied. (Job setting)
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or a
		 *            password if the credentials id was not supplied. (Job setting)
		 * @param credId Credential id that will identify the user id and password to use (Job setting)
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the
		 *            connection. (Job setting)
		 * @param buildWorkspace The build workspace to validate
		 * @param loadPolicy Load policy field
		 * @param pathToLoadRuleFile Path to the load rule file in the format: <component name>/<remote path to the load
		 *            rule file>
		 * @param pollingOnly Whether to perform polling only and skip accept and load.
		 * @return Whether the build workspace configuration is valid or not. Never <code>null</code>
		 */
		@RequirePOST
		public FormValidation doValidateBuildWorkspaceConfiguration(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit,
				@QueryParameter("buildWorkspace") String buildWorkspace,
				@QueryParameter("loadPolicy") String loadPolicy,
				@QueryParameter("pathToLoadRuleFile") String pathToLoadRuleFile,
				@QueryParameter("pollingOnly") String pollingOnly) {
			LOGGER.finest("DescriptorImpl.doValidateBuildWorkspaceConfiguration: Begin"); //$NON-NLS-1$
			if (project == null) {
				return FormValidation.ok();
			} else {
				project.checkPermission(Item.CONFIGURE);
			}
			// validate if required fields are provided
			if (Util.fixEmptyAndTrim(buildWorkspace) == null) {
				return FormValidation.error(Messages.RTCScm_build_workspace_empty());
			}
			// If polling only is selected, make sure that this is a pipeline job
			if (true == Boolean.parseBoolean(pollingOnly)) {
				if (!Helper.isPipelineJob(project)) {
					return FormValidation.error(Messages.Helper_polling_supported_only_for_pipeline());
				}
				// This is a pipeline job for pollingOnly and hence allow the rest of the validation to continue.
			} 
			boolean overrideGlobalSettings = Boolean.parseBoolean(override);
			boolean avoidUsingToolkit;
			if (!overrideGlobalSettings) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(avoidUsingBuildToolkit);
			}
			// validate the info for connecting to the server (including toolkit & auth info).
			ValidationResult connectInfoCheck = validateConnectInfo(project,
					!overrideGlobalSettings, buildTool, serverURI, userId,
					password, passwordFile, credId, timeout, avoidUsingToolkit);
			if (connectInfoCheck.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			} 
			// connection info is good now validate the build workspace
			boolean parameterizedWorkspace = Helper.isAParameter(buildWorkspace);
			FormValidation buildWorkspaceValidationResult = null;
			if (parameterizedWorkspace) {
				buildWorkspaceValidationResult = FormValidation.warning(Messages.RTCScm_repository_workspace_not_validated());
			} else {

				buildWorkspaceValidationResult = checkBuildWorkspace(connectInfoCheck.buildToolkitPath, avoidUsingToolkit, connectInfoCheck.loginInfo,
						buildWorkspace);
			}
			// error fail right away
			if (buildWorkspaceValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return buildWorkspaceValidationResult;
			}
			// validate load rule file path, if specified
			FormValidation loadRuleValidationResult = FormValidation.ok();
			if (Util.fixEmptyAndTrim(pathToLoadRuleFile) != null) {
				if (parameterizedWorkspace) {
					loadRuleValidationResult = FormValidation.warning(Messages.RTCScm_path_to_load_rule_file_not_validated_parameterized_ws());
				} else {
					loadRuleValidationResult = checkLoadRules(connectInfoCheck.buildToolkitPath, avoidUsingToolkit, connectInfoCheck.loginInfo, null,
							false, buildWorkspace, loadPolicy, pathToLoadRuleFile);
				}
			}
			// error fail right away
			if (loadRuleValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return loadRuleValidationResult;
			}
			loadRuleValidationResult = Helper.mergeValidationResults(buildWorkspaceValidationResult, loadRuleValidationResult);
			// If the load rule validation completed with OK then recreate the result with a configuration valid
			// message
			if (loadRuleValidationResult.kind.equals(FormValidation.Kind.OK)) {
				loadRuleValidationResult = FormValidation.ok(Messages.RTCScm_validation_success());
			}
			return Helper.mergeValidationResults(connectInfoCheck.validationResult, loadRuleValidationResult);
		}

		/**
		 * Validates the build definition configuration. Called by the forms.
		 * Fields validated apart from the connection info - Build Definition.
		 * @param project The Job that is going to be run
		 * @param override Whether to override the global connection settings
		 * @param buildTool The build tool selected to be used in builds (Job setting)
		 * @param serverURI The RTC server uri (Job setting)
		 * @param timeout The timeout period for the connection (Job setting)
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id (Job setting)
		 * @param password The password to use when logging in to RTC. Must supply this or a password file
		 * 				if the credentials id was not supplied. (Job setting)
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or 
		 * 				a password if the credentials id was not supplied. (Job setting)
		 * @param credId Credential id that will identify the user id and password to use (Job setting)
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the connection. (Job setting)
		 * @param buildDefinition The build definition to validate
		 * @param pollingOnly Whether to perform polling only and skip accept and load.
		 * @return Whether the build definition configuration is valid or not. Never <code>null</code>
		 */
		@RequirePOST
		public FormValidation doValidateBuildDefinitionConfiguration(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("avoidUsingToolkit") final String avoidUsingBuildToolkit,
				@QueryParameter("buildDefinition") final String buildDefinition,
				@QueryParameter("pollingOnly") String pollingOnly) {
			LOGGER.finest("DescriptorImpl.doValidateBuildDefinitionConfiguration: Begin"); //$NON-NLS-1$
			if (project == null) {
				return FormValidation.ok();
			} else {
				project.checkPermission(Item.CONFIGURE);
			}
			
			// validate if required fields are provided
			if (Util.fixEmptyAndTrim(buildDefinition) == null) {
				return FormValidation.error(Messages.RTCScm_build_definition_empty());
			}
			// If polling only is selected, make sure that this is a pipeline job
			if (true == Boolean.parseBoolean(pollingOnly)) {
				if (!Helper.isPipelineJob(project)) {
					return FormValidation.error(Messages.Helper_polling_supported_only_for_pipeline());
				}
				// This is a pipeline job for pollingOnly and hence allow the rest of the validation to continue.
			}
	    	boolean overrideGlobalSettings = Boolean.parseBoolean(override);
			boolean avoidUsingToolkit;
			if (!overrideGlobalSettings) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(avoidUsingBuildToolkit);
			}
			// validate the info for connecting to the server (including toolkit & auth info).
			ValidationResult connectInfoCheck = validateConnectInfo(project,
					!overrideGlobalSettings, buildTool, serverURI, userId,
					password, passwordFile, credId, timeout, avoidUsingToolkit);
			if (connectInfoCheck.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			} 
			// connection info is good now validate the build definition
			boolean parameterizedBuildDefinition = Helper.isAParameter(buildDefinition);
			FormValidation buildDefinitionCheck = null;
			if (parameterizedBuildDefinition) {
				buildDefinitionCheck = FormValidation.warning(Messages.RTCScm_build_definition_not_validated());
			} else {
				// See if pollingOnly is set to true. If yes, then we can ignore the requirement 
				// the build definition and the corresponding engine has to be a Hudson/Jenkins 
				// engine
				boolean doIgnoreJenkinsConfiguration = false;
				if (Boolean.parseBoolean(pollingOnly) == true) {
					doIgnoreJenkinsConfiguration = true;
				}
				buildDefinitionCheck = checkBuildDefinition(connectInfoCheck.buildToolkitPath, 
						avoidUsingToolkit, connectInfoCheck.loginInfo,
						buildDefinition, doIgnoreJenkinsConfiguration);
			}
			// If the build definition validation completed with OK then recreate the result with a configuration valid
			// message
			if (buildDefinitionCheck.kind.equals(FormValidation.Kind.OK)) {
				buildDefinitionCheck = FormValidation.ok(Messages.RTCScm_validation_success());
			}
			return Helper.mergeValidationResults(connectInfoCheck.validationResult, buildDefinitionCheck);
		}

		/**
		 * Validates the build stream configuration. Called by the forms. Fields validated apart from the connection
		 * info - Project or Team Area(if specified), Build Stream, Path to the load rule file(if specified)
		 * 
		 * @param project The Job that is going to be run
		 * @param override Whether to override the global connection settings
		 * @param buildTool The build tool selected to be used in builds (Job setting)
		 * @param serverURI The RTC server uri (Job setting)
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id (Job setting)
		 * @param password The password to use when logging in to RTC. Must supply this or a password file if the
		 *            credentials id was not supplied. (Job setting)
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or a
		 *            password if the credentials id was not supplied. (Job setting)
		 * @param credId Credential id that will identify the user id and password to use (Job setting)
		 * @param timeout The timeout period for the connection (Job setting)
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the
		 *            connection. (Job setting)
		 * @param processArea Project or Team Area owning the stream
		 * @param buildStream The build stream to validate
		 * @param loadPolicy Load policy field
		 * @param pathToLoadRuleFile Path to the load rule file in the format <component name>/<remote path to the load
		 *            rule file>
		 * @return Whether the build stream configuration is valid or not. Never <code>null</code>
		 */
		@RequirePOST
		public FormValidation doValidateBuildStreamConfiguration(
				@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override,
				@QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI,
				@QueryParameter("timeout") String timeout,
				@QueryParameter("userId") String userId,
				@QueryParameter("password") String password,
				@QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId,
				@QueryParameter("avoidUsingToolkit") final String avoidUsingBuildToolkit,
				@QueryParameter("processArea") final String processArea,
				@QueryParameter("buildStream") final String buildStream,
				@QueryParameter("loadPolicy") String loadPolicy,
				@QueryParameter("pathToLoadRuleFile") final String pathToLoadRuleFile) {
			LOGGER.finest("DescriptorImpl.doValidateBuildStreamConfiguration : Enter"); //$NON-NLS-1$
			
			if (project == null) {
				return FormValidation.ok();
			} else {
				project.checkPermission(Item.CONFIGURE);
			}
			// validate if required fields are provided
			if (Util.fixEmptyAndTrim(buildStream) == null) {
				return FormValidation.error(Messages.RTCScm_build_stream_empty());
			}
			boolean overrideGlobal = Boolean.parseBoolean(override);
			boolean avoidUsingToolkit;
			if (!overrideGlobal) {
				// use the global settings
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(avoidUsingBuildToolkit);
			}

			// First do a connection check, then check the process area and stream
			ValidationResult connectionInfoResult = validateConnectInfo(project, !overrideGlobal, buildTool, serverURI, userId, password,
					passwordFile, credId, timeout, avoidUsingToolkit);
			if (connectionInfoResult.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return connectionInfoResult.validationResult;
			}
			// build toolkit is required to validate process area
			if (avoidUsingToolkit && connectionInfoResult.buildToolkitPath == null && Util.fixEmptyAndTrim(processArea) != null) {
				return Helper.mergeValidationResults(connectionInfoResult.validationResult,
						FormValidation.error(Messages.RTCScm_build_toolkit_required_to_validate_process_area()));
			}
			
			// no error, proceed to check the stream. checking the stream in turn validates project area existence
			boolean parameterizedStream = Helper.isAParameter(buildStream);
			FormValidation streamValidationResult = null;
			if (parameterizedStream) {
				// validate owner details for parameterized stream
				FormValidation ownerValidationResult = FormValidation.ok();
				if (Util.fixEmptyAndTrim(processArea) != null) {
					ownerValidationResult = checkProcessArea(connectionInfoResult.buildToolkitPath, connectionInfoResult.loginInfo, processArea);
				}
				// error fail right away
				if (ownerValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
					return ownerValidationResult;
				}
				// warn that parameterized values cannot be validated, the configuration is still valid from validation
				// perspective
				streamValidationResult = Helper.mergeValidationResults(ownerValidationResult,
						FormValidation.warning(Messages.RTCScm_stream_not_validated()));
			} else {
				streamValidationResult = checkBuildStream(connectionInfoResult.buildToolkitPath, avoidUsingToolkit, connectionInfoResult.loginInfo,
						processArea, buildStream);
			}
			// error fail right away
			if (streamValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return streamValidationResult;
			}
			// validate load rule file path, if specified
			FormValidation loadRuleValidationResult = FormValidation.ok();
			if (Util.fixEmptyAndTrim(pathToLoadRuleFile) != null) {
				if (parameterizedStream) {
					loadRuleValidationResult = FormValidation.warning(Messages.RTCScm_path_to_load_rule_file_not_validated_parameterized_stream());
				} else {
					loadRuleValidationResult = checkLoadRules(connectionInfoResult.buildToolkitPath, avoidUsingToolkit,
							connectionInfoResult.loginInfo, processArea, true, buildStream, loadPolicy, pathToLoadRuleFile);
				}
			}
			// error fail right away
			if (loadRuleValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return loadRuleValidationResult;
			}
			loadRuleValidationResult = Helper.mergeValidationResults(streamValidationResult, loadRuleValidationResult);
			// If the load rule validation completed with OK then recreate the result with a configuration valid
			// message
			if (loadRuleValidationResult.kind.equals(FormValidation.Kind.OK)) {
				loadRuleValidationResult = FormValidation.ok(Messages.RTCScm_validation_success());
			}
			return Helper.mergeValidationResults(connectionInfoResult.validationResult, loadRuleValidationResult);
		}

		/**
		 * Validates the build snapshot configuration. Called by the forms. Fields validated apart from the connection
		 * info - Project or Team Area(if specified), Build Snapshot.
		 * 
		 * @param project The Job that is going to be run
		 * @param override Whether to override the global connection settings
		 * @param buildTool The build tool selected to be used in builds (Job setting)
		 * @param serverURI The RTC server uri (Job setting)
		 * @param userId The user id to use when logging in to RTC. Must supply this or a credentials id (Job setting)
		 * @param password The password to use when logging in to RTC. Must supply this or a password file if the
		 *            credentials id was not supplied. (Job setting)
		 * @param passwordFile File containing the password to use when logging in to RTC. Must supply this or a
		 *            password if the credentials id was not supplied. (Job setting)
		 * @param credId Credential id that will identify the user id and password to use (Job setting)
		 * @param timeout The timeout period for the connection (Job setting)
		 * @param avoidUsingBuildToolkit Whether to use REST api instead of the build toolkit when testing the
		 *            connection. (Job setting)
		 * @param currentSnapshotOwnerType Currently selected snapshot owner type - "none", "stream", or "workspace"
		 * @param processAreaOfOwningStream Name of the project or team area of the stream owning the snapshot
		 * @param owningStream Name of the stream owning the snapshot
		 * @param owningWorkspace Name of the workspace owning the snapshot
		 * @param buildSnapshot The build snapshot to validate
		 * @param pathToLoadRuleFile Path to the load rule file in the format: <component name>/<remote path to the load
		 *            rule file>
		 *            
		 * @return Whether the build snapshot configuration is valid or not. Never <code>null</code>
		 */
		@RequirePOST
		public FormValidation doValidateBuildSnapshotConfiguration(@AncestorInPath Job<?, ?> project,
				@QueryParameter("overrideGlobal") final String override, @QueryParameter("buildTool") String buildTool,
				@QueryParameter("serverURI") String serverURI, @QueryParameter("timeout") String timeout, @QueryParameter("userId") String userId,
				@QueryParameter("password") String password, @QueryParameter("passwordFile") String passwordFile,
				@QueryParameter("credentialsId") String credId, @QueryParameter("avoidUsingToolkit") String avoidUsingBuildToolkit,
				@QueryParameter("currentSnapshotOwnerType") String currentSnapshotOwnerType,
				@QueryParameter("processAreaOfOwningStream") String processAreaOfOwningStream, @QueryParameter("owningStream") String owningStream,
				@QueryParameter("owningWorkspace") String owningWorkspace, @QueryParameter("buildSnapshot") String buildSnapshot,
				@QueryParameter("pathToLoadRuleFile") String pathToLoadRuleFile) {

			LOGGER.finest("DescriptorImpl.doValidateBuildSnapshotConfiguration: Begin"); //$NON-NLS-1$
			if (project == null) {
				return FormValidation.ok();
			} else {
				project.checkPermission(Item.CONFIGURE);
			}
			
			// validate if a value is specified for build snapshot
			if (Util.fixEmptyAndTrim(buildSnapshot) == null) {
				return FormValidation.error(Messages.RTCScm_build_snapshot_empty());
			}
			boolean overrideGlobalSettings = Boolean.parseBoolean(override);
			boolean avoidUsingToolkit;
			if (!overrideGlobalSettings) {
				// use the global settings instead of the ones supplied
				buildTool = getGlobalBuildTool();
				serverURI = getGlobalServerURI();
				credId = getGlobalCredentialsId();
				userId = getGlobalUserId();
				password = getGlobalPassword();
				passwordFile = getGlobalPasswordFile();
				timeout = Integer.toString(getGlobalTimeout());
				avoidUsingToolkit = getGlobalAvoidUsingToolkit();
			} else {
				avoidUsingToolkit = Boolean.parseBoolean(avoidUsingBuildToolkit);
			}
			// validate the info for connecting to the server (including toolkit
			// & auth info).
			ValidationResult connectInfoCheck = validateConnectInfo(project, !overrideGlobalSettings, buildTool, serverURI, userId, password,
					passwordFile, credId, timeout, avoidUsingToolkit);
			if (connectInfoCheck.validationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return connectInfoCheck.validationResult;
			}
			// build toolkit is required to validate the snapshot configuration
			if (avoidUsingToolkit && connectInfoCheck.buildToolkitPath == null) {
				return FormValidation.error(Messages.RTCScm_build_toolkit_required_to_validate_snapshot());
			}
			// validate snapshot
			boolean parameterizedSnapshot = Helper.isAParameter(buildSnapshot);
			FormValidation buildSnapshotValidationResult = null;
			if (parameterizedSnapshot) {
				// validate snapshot owner details for parameterized snapshot
				FormValidation ownerValidationResult = FormValidation.ok();
				if (Util.fixEmptyAndTrim(currentSnapshotOwnerType) != null) {
					if (SNAPSHOT_OWNER_TYPE_WORKSPACE.equals(currentSnapshotOwnerType) && Util.fixEmptyAndTrim(owningWorkspace) != null) {
						ownerValidationResult = checkBuildWorkspace(connectInfoCheck.buildToolkitPath, avoidUsingToolkit, connectInfoCheck.loginInfo,
								owningWorkspace);
					} else if (SNAPSHOT_OWNER_TYPE_STREAM.equals(currentSnapshotOwnerType)) {
						if (Util.fixEmptyAndTrim(owningStream) != null) {
							ownerValidationResult = checkBuildStream(connectInfoCheck.buildToolkitPath, avoidUsingToolkit,
									connectInfoCheck.loginInfo, processAreaOfOwningStream, owningStream);
						} else if (Util.fixEmptyAndTrim(processAreaOfOwningStream) != null) {
							ownerValidationResult = checkProcessArea(connectInfoCheck.buildToolkitPath, connectInfoCheck.loginInfo,
									processAreaOfOwningStream);
						}
					}
				}
				// error fail right away
				if (ownerValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
					return ownerValidationResult;
				}
				// warn that parameterized values cannot be validated, the configuration is still valid from validation
				// perspective
				buildSnapshotValidationResult = Helper.mergeValidationResults(ownerValidationResult,
						FormValidation.warning(Messages.RTCScm_build_snapshot_not_validated()));
			} else {
				buildSnapshotValidationResult = checkBuildSnapshot(connectInfoCheck.buildToolkitPath, connectInfoCheck.loginInfo,
						BuildSnapshotContext.getBuildSnapshotContextMap(currentSnapshotOwnerType, processAreaOfOwningStream, owningStream,
								owningWorkspace), buildSnapshot);

			}
			// error fail right away
			if (buildSnapshotValidationResult.kind.equals(FormValidation.Kind.ERROR)) {
				return buildSnapshotValidationResult;
			}
			// load rule validation not supported for snapshot
			if (Util.fixEmptyAndTrim(pathToLoadRuleFile) != null) {
				buildSnapshotValidationResult = Helper.mergeValidationResults(buildSnapshotValidationResult,
						FormValidation.warning(Messages.RTCScm_path_to_load_rule_not_validated_snapshot()));
			}
			
			// If the build snapshot validation completed with OK then recreate the result with a configuration valid
			// message
			if (buildSnapshotValidationResult.kind.equals(FormValidation.Kind.OK)) {
				buildSnapshotValidationResult = FormValidation.ok(Messages.RTCScm_validation_success());
			}
			return Helper.mergeValidationResults(connectInfoCheck.validationResult, buildSnapshotValidationResult);
		}

		/** 
		 * Validate that the build workspace exists and there is just one.
		 * This is done in the next "layer" below using either the toolkit
		 * or the rest service.
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingToolkit Whether to avoid using the build toolkit
		 * @param loginInfo The login credentials 
		 * @param buildWorkspace The name of the workspace to validate
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkBuildWorkspace(String buildToolkitPath, boolean avoidUsingToolkit, RTCLoginInfo loginInfo, String buildWorkspace) {

			try {
	    		String errorMessage = RTCFacadeFacade.testBuildWorkspace(buildToolkitPath,
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
						avoidUsingToolkit, buildWorkspace);
				if (errorMessage != null && errorMessage.length() != 0) {
	    			return FormValidation.error(errorMessage);
				}
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport == null) {
	    			eToReport = e;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildWorkspace attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildWorkspace invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildWorkspace attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildWorkspace failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, e.getMessage());
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_build_workspace_success());
	    }
	    
	    private FormValidation checkBuildStream(String buildToolkitPath, boolean avoidUsingToolkit, RTCLoginInfo loginInfo, String processArea, String buildStream) {
	    	try {
	    		String errorMessage = RTCFacadeFacade.testBuildStream(buildToolkitPath,
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
						avoidUsingToolkit, processArea, buildStream);
				errorMessage = Util.fixEmptyAndTrim(errorMessage);
				if (errorMessage != null) {
	    			return FormValidation.error(errorMessage);
				}
	    	}
	    	catch (InvocationTargetException exp){ 
	    		Throwable eToReport = exp.getCause();
	    		if (eToReport == null) {
	    			eToReport = exp;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildStream attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildStream=\"" + buildStream + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildStream invocation failure " + eToReport.getMessage(), exp); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	}
	    	catch (Exception exp) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    			LOGGER.finer("checkBuildStream attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    	        "\" buildStream=\"" + buildStream + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildStream failed " + exp.getMessage(), exp); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(exp, exp.getMessage());
	    	}
	    	return FormValidation.ok(Messages.RTCScm_build_stream_success());
	    
	    }
	    
	    /** 
		 * Validate that the build snapshot exists and there is just one. Validation using REST service is not supported.
		 * 
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingToolkit Whether to avoid using the build toolkit
		 * @param loginInfo The login credentials 
		 * @param buildSnapshotContextMap Name-Value pairs representing the snapshot owner details
		 * @param buildWorkspace The name of the workspace to validate
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkBuildSnapshot(String buildToolkitPath, RTCLoginInfo loginInfo,  Map<String, String> buildSnapshotContextMap, String buildSnapshot) {
			try {
				// need not have to route through RTCFacadeFacade as we don't have rest services to validate snapshot
				RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
				String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_BUILD_SNAPSHOT,
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								Map.class, //buildSnapshotContextMap
								String.class, // buildSnapshot
								Locale.class }, // clientLocale
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), buildSnapshotContextMap,
						buildSnapshot, LocaleProvider.getLocale());
				errorMessage = Util.fixEmptyAndTrim(errorMessage);
				if (errorMessage != null) {
					return FormValidation.error(errorMessage);
				}
			} catch (InvocationTargetException exp) {
				Throwable eToReport = exp.getCause();
				if (eToReport == null) {
					eToReport = exp;
				}
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkBuildSnapshot attempted with " + //$NON-NLS-1$
							" buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
							"\" buildSnapshot=\"" + buildSnapshot + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER, "checkBuildSnapshot invocation failure " + eToReport.getMessage(), exp); //$NON-NLS-1$
				}
				return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
			} catch (Exception exp) {
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkBuildSnapshot attempted with " + //$NON-NLS-1$
							" buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
							"\" buildSnapshot=\"" + buildSnapshot + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER, "checkBuildSnapshot failed " + exp.getMessage(), exp); //$NON-NLS-1$
				}
				return FormValidation.error(exp, exp.getMessage());
			}
			return FormValidation.ok(Messages.RTCScm_build_stream_success());

		}

	    /**
		 * Validate the build definition is a H/J definition and usable
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingToolkit Whether to avoid using the build toolkit
		 * @param loginInfo The login credentials 
		 * @param buildDefinition The id of the build definition to validate
		 * @param ignoreJenkinsConfiguration 
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkBuildDefinition(String buildToolkitPath, 
				boolean avoidUsingToolkit, RTCLoginInfo loginInfo, String buildDefinition,
				boolean ignoreJenkinsConfiguration) {

			try {
				String errorMessage = RTCFacadeFacade.testBuildDefinition(buildToolkitPath, loginInfo.getServerUri(),
						loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
						avoidUsingToolkit,
						buildDefinition, ignoreJenkinsConfiguration);
				if (errorMessage != null && errorMessage.length() != 0) {
	    			return FormValidation.error(errorMessage);
				}
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport == null) {
	    			eToReport = e;
	    		}
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildDefinition attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() +  //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    	        "\" buildDefinition=\"" + buildDefinition +"\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildDefinition invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
	    	} catch (Exception e) {
	    		if (LOGGER.isLoggable(Level.FINER)) {
	    	    	LOGGER.finer("checkBuildDefinition attempted with " +  //$NON-NLS-1$
	    	    	        " buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
	    	    			"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
	    	    			"\" timeout=\"" + loginInfo.getTimeout() +  //$NON-NLS-1$
	    	    			"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
	    	    	        "\" buildDefinition=\"" + buildDefinition + "\""); //$NON-NLS-1$ //$NON-NLS-2$
	    			LOGGER.log(Level.FINER, "checkBuildDefinition failed " + e.getMessage(), e); //$NON-NLS-1$
	    		}
	    		return FormValidation.error(e, e.getMessage());
	    	}
	    	
	    	return FormValidation.ok(Messages.RTCScm_build_definition_success());
	    }

		/**
		 * Validate that the project area/team area exists. Validation using REST services is not supported.
		 * 
		 * @param buildToolkitPath Path to the build toolkit
		 * @param loginInfo The login credentials
		 * @param process The name of the RTC project area or team area
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkProcessArea(String buildToolkitPath, RTCLoginInfo loginInfo, String processArea) {
			try {
				// need not have to route through RTCFacadeFacade as we don't have rest services to validate project area
				RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
				String errorMessage = (String)facade.invoke(RTCFacadeWrapper.TEST_PROCESS_AREA,
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								String.class, // processArea
								Locale.class }, // clientLocale
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), processArea,
						LocaleProvider.getLocale());
				errorMessage = Util.fixEmptyAndTrim(errorMessage);
				if (errorMessage != null) {
					return FormValidation.error(errorMessage);
				}
			} catch (InvocationTargetException e) {
				Throwable eToReport = e.getCause();
				if (eToReport == null) {
					eToReport = e;
				}
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkProcessArea attempted with " + //$NON-NLS-1$
							" buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" processArea=\"" + processArea + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER, "checkProcessArea invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
				}
				return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
			} catch (Exception e) {
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkProcessArea attempted with " + //$NON-NLS-1$
							" buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" processArea=\"" + processArea + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER, "checkProcessArea failed " + e.getMessage(), e); //$NON-NLS-1$
				}
				return FormValidation.error(e, e.getMessage());
			}
			// since we do collective validation, it is ok not to return any message
			return FormValidation.ok();
		}
		
		/**
		 * Validate if the specified component/load rule file exist in the repository and included in the given
		 * workspace.
		 * 
		 * @param buildToolkitPath Path to the build toolkit
		 * @param avoidUsingToolkit Whether to avoid using the build toolkit
		 * @param loginInfo The login credentials
		 * @param processArea The name of the project or team area
		 * @param isStreamConfiguration Flag that determines if the <code>buildWorkspace</code> corresponds to a workspace or stream
		 * @param buildWorkspace The name of the workspace to validate
		 * @param loadPolicy load policy value
		 * @param pathToLoadRuleFile Path to the load rule file in the format <component-name>/<path-to-the-load-rule-file>.
		 * @return The result of the validation. Never <code>null</code>
		 */
		private FormValidation checkLoadRules(String buildToolkitPath, boolean avoidUsingToolkit, RTCLoginInfo loginInfo, String processArea,
				boolean isStreamConfiguration, String buildWorkspace, String loadPolicy, String pathToLoadRuleFile) {
			
			// validate load rule file path only if load policy is set to use a load rule file
			if (!LOAD_POLICY_USE_LOAD_RULES.equals(loadPolicy)) {
				return FormValidation.ok();
			}
			
			if (Helper.isAParameter(pathToLoadRuleFile)) {
				return FormValidation.warning(Messages.RTCScm_path_to_load_rule_file_not_validated());
			}

			if (avoidUsingToolkit && buildToolkitPath == null) {
				return FormValidation.error(Messages.RTCScm_path_to_load_rule_file_toolkit_required());
			}
			
			try {				
				RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, null);
				String errorMessage = (String)facade.invoke(
						RTCFacadeWrapper.TEST_LOAD_RULES, //$NON-NLS-1$
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								int.class, // timeout
								String.class, // processArea
								boolean.class, // isStreamConfiguration
								String.class, // buildWorkspace
								String.class, // pathToLoadRuleFile
								Locale.class }, // clientLocale
						loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), processArea,
						isStreamConfiguration, buildWorkspace, pathToLoadRuleFile, LocaleProvider.getLocale());
				if (errorMessage != null && errorMessage.length() != 0) {
					return FormValidation.error(errorMessage);
				}
			} catch (InvocationTargetException e) {
				Throwable eToReport = e.getCause();
				if (eToReport == null) {
					eToReport = e;
				}
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkLoadRules attempted with " + //$NON-NLS-1$
							" buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
							"\" processArea=\"" + processArea + ////$NON-NLS-1$
							"\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
							"\" pathToLoadRuleFile=\"" + pathToLoadRuleFile + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER, "checkLoadRules invocation failure " + eToReport.getMessage(), e); //$NON-NLS-1$
				}
				return FormValidation.error(eToReport, Messages.RTCScm_failed_to_connect(eToReport.getMessage()));
			} catch (Exception e) {
				if (LOGGER.isLoggable(Level.FINER)) {
					LOGGER.finer("checkLoadRules attempted with " + //$NON-NLS-1$
							" buildToolkitPath=\"" + buildToolkitPath + //$NON-NLS-1$
							"\" processArea=\"" + processArea + ////$NON-NLS-1$
							"\" buildWorkspace=\"" + buildWorkspace + //$NON-NLS-1$
							"\" pathToLoadRuleFile=\"" + pathToLoadRuleFile + //$NON-NLS-1$
							"\" serverURI=\"" + loginInfo.getServerUri() + //$NON-NLS-1$
							"\" userId=\"" + loginInfo.getUserId() + //$NON-NLS-1$
							"\" timeout=\"" + loginInfo.getTimeout() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
					LOGGER.log(Level.FINER, "checkLoadRules failed " + e.getMessage(), e); //$NON-NLS-1$
				}
				return FormValidation.error(e, e.getMessage());
			}
			return FormValidation.ok();
		}
	}	
	
	/*
	 * Convenience constructor for instantiating RTCSCM with only a buildType and the rest of the parameters are set to defaults	 * 
	 */
	public RTCScm(BuildType buildType) {
		this(false, null, null, DEFAULT_SERVER_TIMEOUT, null, null, null, null, buildType, false);
		LOGGER.finest("RTCScm constructor 1: Begin");
	}
	
	@DataBoundConstructor
	public RTCScm(boolean overrideGlobal, String buildTool, String serverURI, int timeout, String userId, Secret password, String passwordFile,
			String credentialsId, BuildType buildType, boolean avoidUsingToolkit) {

		
		LOGGER.finest("RTCScm DataBound constructor: Begin");
		this.overrideGlobal = overrideGlobal;
		if (this.overrideGlobal) {
			this.buildTool = buildTool;
			this.serverURI = serverURI;
			this.timeout = timeout;
			this.credentialsId = credentialsId;
			if (this.credentialsId == null || credentialsId.isEmpty()) {
				this.userId = userId;
				this.password = password;
				this.passwordFile = passwordFile;
			}
			this.avoidUsingToolkit = avoidUsingToolkit;
		}
		this.buildType = buildType;
		if(buildType != null) {
			this.buildTypeStr = buildType.value;
			this.buildWorkspace = buildType.buildWorkspace;
			this.buildDefinition = buildType.buildDefinition;
			this.buildSnapshot = buildType.buildSnapshot;
			this.buildStream = buildType.buildStream;
			this.loadDirectory = buildType.loadDirectory;
			this.clearLoadDirectory = buildType.clearLoadDirectory;
			this.createFoldersForComponents = buildType.createFoldersForComponents;
			this.acceptBeforeLoad = buildType.acceptBeforeLoad;
			this.generateChangelogWithGoodBuild = buildType.generateChangelogWithGoodBuild;
			this.addLinksToWorkItems = buildType.addLinksToWorkItems;
			this.processArea = buildType.processArea;
			this.currentSnapshotOwnerType = buildType.currentSnapshotOwnerType;
			this.buildSnapshotContext = buildType.buildSnapshotContext;
			this.overrideDefaultSnapshotName = buildType.overrideDefaultSnapshotName;
			this.customizedSnapshotName = buildType.customizedSnapshotName;
			this.loadPolicy = buildType.loadPolicy;
			this.componentLoadConfig = buildType.componentLoadConfig;
			this.componentsToExclude = buildType.componentsToExclude;
			this.pathToLoadRuleFile = buildType.pathToLoadRuleFile;
			this.useDynamicLoadRules = buildType.useDynamicLoadRules;
			this.pollingOnly = buildType.pollingOnly;
			this.pollingOnlyData = buildType.pollingOnlyData;
		}
		
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("RTCScm constructed with " + //$NON-NLS-1$
					" overrideGlobal=\"" + this.overrideGlobal + //$NON-NLS-1$
					"\" buildTool=\"" + this.buildTool + //$NON-NLS-1$
					"\" serverURI=\"" + this.serverURI + //$NON-NLS-1$
					"\" timeout=\"" + this.timeout + //$NON-NLS-1$
					"\" userId=\"" + this.userId + //$NON-NLS-1$
					"\" password " + (this.password == null ? "is not supplied" : "(" + Secret.toString(this.password).length() +" characters)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					" passwordFile=\"" + this.passwordFile + //$NON-NLS-1$
					"\" credentialsId=\"" + this.credentialsId + //$NON-NLS-1$
					"\" buildType=\"" + this.buildTypeStr + //$NON-NLS-1$
					"\" buildWorkspace=\"" + this.buildWorkspace + //$NON-NLS-1$
					"\" buildDefinition=\"" + this.buildDefinition +  //$NON-NLS-1$
					"\" buildSnapshot=\"" + this.buildSnapshot + //$NON-NLS-1$
					"\" buildStream=\"" + this.buildStream + //$NON-NLS-1$
					"\" loadDirectory=\"" + this.loadDirectory + //$NON-NLS-1$
					"\" clearLoadDirectory=\"" + this.clearLoadDirectory + //$NON-NLS-1$  
					"\" createFoldersForComponents=\"" + this.createFoldersForComponents + //$NON-NLS-1$  
					"\" acceptBeforeLoad=\"" + this.acceptBeforeLoad + 
					"\" generateChangelogWithGoodBuild=\"" + this.generateChangelogWithGoodBuild +  //$NON-NLS-1$ //$NON-NLS-2$
					"\" addLinksToWorkitems=\"" + this.addLinksToWorkItems + //$NON-NLS-1$ //$NON-NLS-2$
					"\" pollingOnly=\"" + this.pollingOnly); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
	
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

	@Override
	public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException,
			InterruptedException {
        LOGGER.finest("RTCScm.calcRevisionsFromBuild : Begin");
		// Our check for incoming changes uses the flow targets and does a real time compare
		// So for now we don't return a special revision state
		return SCMRevisionState.NONE;
	}
	
	/**
	 * Expecting buildResultUUID as non-null, match with buildDefinition and buildWorkspace
	 * @param loginInfo
	 * @param buildResultUUID
	 * @param buildDefinition
	 * @param buildWorkspace
	 * @return true if matches, false otherwise
	 */
	private boolean match(RTCLoginInfo loginInfo, String buildToolkit, String buildResultUUID, String buildDefinition, String buildWorkspace,
			boolean debug, TaskListener listener, Locale clientLocale) throws InterruptedException, AbortException {
		if (buildDefinition == null) {
			return false;
		}
		
		RTCFacadeWrapper facade;
		Map<String, String> details = null;
		try {
			facade = RTCFacadeFactory.getFacade(buildToolkit, debug ? listener.getLogger() : null);
			
			// get buildDefinition and buildWorkspace for buildResultUUID...
			details = (Map<String, String>) facade.invoke("getBuildResultUUIDDetails", new Class[] { //$NON-NLS-1$
					String.class, // serverURI,
					String.class, // userId,
					String.class, // password,
					int.class, // timeout,
					String.class, // buildResultUUID,
					Object.class, // listener
					Locale.class // clientLocale
			}, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
					loginInfo.getTimeout(), buildResultUUID,
					listener, clientLocale);
		} catch (Exception e) {
			Throwable eToReport = e;
			if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
				eToReport = e.getCause();
			}
			if (eToReport instanceof InterruptedException) {
				listener.getLogger().println("Exception while getting buildResultUUID details");
				throw (InterruptedException) eToReport;
			}
//			if (debug) {
//				debug("Failed to get buildResultUUID details", eToReport); //$NON-NLS-1$
//			}
			// throw AbortException with this message
			throw new AbortException(e.getMessage());
		}
		
		String buildDefinitionId = details.get("buildDefinitionId");
		
		if (!buildDefinition.equals(buildDefinitionId)) {
			return false;
		}
/*		
		String buildWorkspaceName = details.get("buildWorkspaceName");
		if (buildWorkspace != null) {
			if (!buildWorkspace.equals(buildWorkspaceName)) {
				return false;
			}
		}
*/
		return true;
	}

	@Override
	public void checkout(Run<?, ?> build, Launcher arg1,
			FilePath workspacePath, TaskListener listener, File changeLogFile, SCMRevisionState baseline) throws IOException,
			InterruptedException {
		
        LOGGER.finest("RTCScm.checkout : Begin");
		listener.getLogger().println(Messages.RTCScm_checkout_started());

		String label = getLabel(build);
		String localBuildToolkit;
		String nodeBuildToolkit;
		
		String buildWorkspace = (getBuildTypeStr().equals(BUILD_WORKSPACE_TYPE)) ?
					Helper.parseConfigurationValue(build, null, Util.fixEmptyAndTrim(getBuildWorkspace()), listener):
					Util.fixEmptyAndTrim(getBuildWorkspace());
					
		String buildDefinition = (getBuildTypeStr().equals(BUILD_DEFINITION_TYPE)) ?
					Helper.parseConfigurationValue(build, null, Util.fixEmptyAndTrim(getBuildDefinition()), listener):
					Util.fixEmptyAndTrim(getBuildDefinition());
					
		String buildSnapshot = (getBuildTypeStr().equals(BUILD_SNAPSHOT_TYPE)) ?
					Helper.parseConfigurationValue(build, RTCJobProperties.RTC_BUILD_SNAPSHOT, Util.fixEmptyAndTrim(getBuildSnapshot()), listener):
					Util.fixEmptyAndTrim(getBuildSnapshot());
					
		String buildStream = (getBuildTypeStr().equals(BUILD_STREAM_TYPE)) ?
					Helper.parseConfigurationValue(build, null, Util.fixEmptyAndTrim(getBuildStream()), listener):
					Util.fixEmptyAndTrim(getBuildStream()); 

	    if (true == getPollingOnly()) {
	    	handlePollingOnlyMode(build, workspacePath, listener, changeLogFile, 
	    			buildDefinition, buildWorkspace);
	    	// Exit from checkout without performing accept/load
			return;
	    }

		String buildResultUUID = getBuildResultUUID(build, listener);
		
		validateInput(getBuildTypeStr(), buildSnapshot, buildStream);
		
		validateLoadPolicyAndComponentLoadConfig(Util.fixEmptyAndTrim(loadPolicy), Util.fixEmptyAndTrim(componentLoadConfig));
		
		Node node = workspacePath.toComputer().getNode();
		localBuildToolkit = getDescriptor().getMasterBuildToolkit(getBuildTool(), listener);
		// Get the build toolkit on the node where the checkout is happening.
		nodeBuildToolkit = getDescriptor().getBuildToolkit(getBuildTool(), node, listener);

		boolean debug = Helper.isDebugEnabled(build, listener);

		RTCLoginInfo loginInfo;
		try {
			loginInfo = getLoginInfo2(build, localBuildToolkit, listener, debug);
		} catch (InvalidCredentialsException e1) {
			throw new AbortException(e1.getMessage());
		}
		
		if (workspacePath.isRemote()) {
			// Slaves do a lazy remote class loader. The slave's class loader will request things as needed
			// from the remote master. The class loader on the master is the one that knows about the hjplugin-rtc.jar
			// but not any of the toolkit jars. So trying to send the class (& its references) is problematic.
			// The hjplugin-rtc.jar won't be able to be found on the slave either from the regular class loader either
			// (since its on the master). So what we do is send our hjplugin-rtc.jar over to the slave to "prepopulate"
			// it in the class loader. This way we can create our special class loader referencing it and all the toolkit
			// jars.
			Helper.sendJarsToAgent(workspacePath);
		}
		
		// Before proceeding, log the details of master and node buildtoolkit
		logBuildToolkitVersions(workspacePath, listener, localBuildToolkit, nodeBuildToolkit, debug);

		// if buildResultUUID is not null then we need to match...
		if (buildResultUUID != null) {
			if (!match(loginInfo, localBuildToolkit, buildResultUUID, buildDefinition, buildWorkspace, debug, listener, LocaleProvider.getLocale())) {
				buildResultUUID = null;
			}
		}

		String buildType = getBuildTypeStr();
		boolean useBuildDefinitionInBuild = BUILD_DEFINITION_TYPE.equals(buildType) || buildResultUUID != null;
		
		// Log in build result where the build was initiated from RTC
		// Because if initiated from RTC we will ignore build workspace if its a buildWorkspaceType
		if (buildResultUUID != null) {
			listener.getLogger().println(Messages.RTCScm_build_initiated_by());
		}
		
		try {
			
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("checkout : " + build.getParent().getName() + " " + build.getDisplayName() + " " + node.getNodeName() + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" Load directory=\"" + workspacePath.getRemote() + "\"" +  //$NON-NLS-1$ //$NON-NLS-2$
						" Build tool=\"" + getBuildTool() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Local Build toolkit=\"" + localBuildToolkit + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Node Build toolkit=\"" + nodeBuildToolkit + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Server URI=\"" + loginInfo.getServerUri() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Userid=\"" + loginInfo.getUserId() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" BuildType=\"" + buildType + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" ProcessArea=\"" + getProcessArea() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Build definition=\"" + buildDefinition + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Build workspace=\"" + buildWorkspace + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Build snapshot=\"" + buildSnapshot + "\"" +  //$NON-NLS-1$ //$NON-NLS-2$
						" useBuildDefinitionInBuild=\"" + useBuildDefinitionInBuild + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" Baseline Set name=\"" + label + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			RTCBuildResultSetupTask buildResultSetupTask = new RTCBuildResultSetupTask(build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
					nodeBuildToolkit,
					loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
					useBuildDefinitionInBuild, buildDefinition, buildResultUUID,
					label, listener, workspacePath.isRemote(), debug, LocaleProvider.getLocale());
			
			BuildResultInfo buildResultInfo = buildResultSetupTask.localInvocation();
			if (buildResultInfo == null) {
				buildResultInfo = workspacePath.act(buildResultSetupTask);
			}

			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("checkout : " + build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName() + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" initial buildResultUUID=\"" + buildResultUUID + "\"" +  //$NON-NLS-1$ //$NON-NLS-2$
						" current buildResultUUID=\"" + buildResultInfo.getBuildResultUUID() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" scheduled=\"" + buildResultInfo.isScheduled() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" personal build=\"" + buildResultInfo.isPersonalBuild() + "\"" + //$NON-NLS-1$ //$NON-NLS-2$
						" requested by=\"" + (buildResultInfo.getRequestor() == null ? "" : buildResultInfo.getRequestor()) + "\"" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						" own build life cycle=\"" + buildResultInfo.ownLifeCycle() + "\""); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if (buildResultUUID != null) {
				// if build started by RTC, record the cause of the build
				RTCBuildCause rtcBuildCause = new RTCBuildCause(buildResultInfo);
				CauseAction cause = build.getAction(CauseAction.class);
				if (cause == null) {
					cause = new CauseAction(rtcBuildCause);
					build.addAction(cause);
				} else {
					try {
						cause.getCauses().add(rtcBuildCause);
					} catch (UnsupportedOperationException exception) {
						// with the changes made in Jenkins Issue 33467 in 1.655, the list of Causes returned by
						// CauseAction is immutable. So construct a new list with the exiting causes, append
						// our RTCBuildCause instance to this list and replace the CauseAction instance in the build
						// Post 1.2.0.0 we should always be replacing the CauseAction, instead of appending to the
						// existing list of Causes
						LOGGER.fine("RTCScm.checkout: We should have tried to modify the immutable list of Causes."); //$NON-NLS-1$
						List<Cause> newCauses = new ArrayList<Cause>(cause.getCauses());
						newCauses.add(rtcBuildCause);
						CauseAction newCauseAction = new CauseAction(newCauses);
						build.replaceAction(newCauseAction);
					}
				}
			}
			
			// now that the build has been setup, start working with the actual build result
			// independent of whether RTC or the plugin created it
			buildResultUUID = buildResultInfo.getBuildResultUUID();
			
			// add the build result information (if any) to the build through an action
			// properties may later be added to this action
			setBuildResultAction(new RTCBuildResultAction(loginInfo.getServerUri(), buildResultUUID, buildResultInfo.ownLifeCycle(), this));
			build.addAction(getBuildResultAction());

			RemoteOutputStream changeLog = null;
			if (changeLogFile != null) {
				OutputStream changeLogStream = new FileOutputStream(changeLogFile);
				changeLog = new RemoteOutputStream(changeLogStream);
			}
			
			String strCallConnectorTimeout = build.getEnvironment(listener).get(CALL_CONNECTOR_TIMEOUT_PROPERTY);
			if ((strCallConnectorTimeout == null) || !strCallConnectorTimeout.matches("\\d+")) { //$NON-NLS-1$
				strCallConnectorTimeout = ""; //$NON-NLS-1$
			}

			String previousSnapshotUUIDForChangeLog = null;
			Run<?,?> previousBuild = null;
			// Get previous snapshot UUID for comparison in stream case
			Tuple<Run<?,?>, String> previousSnapshotDetails = Helper.getSnapshotUUIDFromPreviousBuild(build, 
					localBuildToolkit, loginInfo, getProcessArea(),
					buildStream, getGenerateChangelogWithGoodBuild(), LocaleProvider.getLocale());
			if (previousSnapshotDetails != null) {
				previousBuild = previousSnapshotDetails.getFirst();
				previousSnapshotUUIDForChangeLog = previousSnapshotDetails.getSecond();
			}

			// By default for build definition, the snapshotName variable is set to #<Build Number> from the hjplugin,
			// the RTC plugin adds "<Build Definition Id>_" as a prefix and sets "<Build Definition Id>_#<Build Number>
			// as the snapshot name
			// For repository workspace and stream, the snapshotName is set to
			// "<Job Name>_#<Build Number> and used as is by the RTC plugin"
			String snapshotName = useBuildDefinitionInBuild ? label : build.getParent().getName() + "_" + label;
			// check if a custom snapshot name is configured in the job
			// custom snapshot name, if provided, overrides the above determined default snapshot name
			boolean isCustomSnapshotName = false;
			if (overrideDefaultSnapshotName && Util.fixEmptyAndTrim(customizedSnapshotName) != null) {
				// resolve any references to environment variables and build variables
				String resolvedSnapshotName = Helper.resolveCustomSnapshotName(build, customizedSnapshotName, listener);
				// make sure that the resolved value is not null and not empty
				// if empty or null just go with the default name computed and set earlier
				if (Util.fixEmptyAndTrim(resolvedSnapshotName) != null) {
					isCustomSnapshotName = true;
					snapshotName = resolvedSnapshotName;
				} else {
					listener.getLogger().println(Messages.RTCScm_empty_resolved_snapshot_name(customizedSnapshotName));
				}
			}
			
			// This map will hold future options to be added
			Map<String, Object> options = new HashMap<String, Object>();
			// Check whether metronome report should be collected
			Map<String, Object> metronomeOptions = createMetronomeOptions(build, listener);
			options.put(METRONOME_OPTIONS_PROPERTY_NAME, metronomeOptions);

			String parentActivityId = ""; //$NON-NLS-1$
			String connectorId = "";
			Map<String, String> streamData = new HashMap<String, String>();
			Map<String, String> buildSnapshotContextMap = buildSnapshotContext != null ? buildSnapshotContext.getContextMap() : null;
			Map<String, String> buildUrls = Helper.constructBuildURLMap(build);
			RTCAcceptTask acceptTask = new RTCAcceptTask(
					 build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
					nodeBuildToolkit, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(), 
					getProcessArea(), 
					buildResultUUID,
					buildWorkspace,
					buildSnapshotContextMap,
					buildSnapshot,
					buildStream, isCustomSnapshotName,
					snapshotName, previousSnapshotUUIDForChangeLog,
					listener, changeLog,
					workspacePath.isRemote(), debug, LocaleProvider.getLocale(), strCallConnectorTimeout, 
					getAcceptBeforeLoad(),
					getAddLinksToWorkItems(),
					// If you are not comparing with any snapshot, no need to put the previous build URL
					buildUrls,
					Helper.getTemporaryWorkspaceComment(build),
					// Add future options to this object instead of adding new parameters to this method
					options);

			// publish in the build result links to the project and the build
			if (buildResultUUID != null) {
				String rootUrl = Hudson.getInstance().getRootUrl();
				if (rootUrl != null) {
					rootUrl = Util.encode(rootUrl);
				}
				String projectUrl = build.getParent().getUrl();
				if (projectUrl != null) {
					projectUrl = Util.encode(projectUrl);
				}
				String buildUrl = build.getUrl();
				if (buildUrl != null) {
					buildUrl = Util.encode(buildUrl);
				}
				acceptTask.setLinkURLs(rootUrl,  projectUrl, buildUrl);
			}
			
			// NOTE:
			// For buildStream case, we have created a temporary repository workspace. Its UUID is stored in streamData.
			// The temporary repository workspace will be used in load() and deleted at the end of load. If we do add any 
			// intermediate steps between accept and load and there is some exception during those tasks (even now,
			// we can get problems with componentsToExclude ad loadRules), we need to ensure that the temporary workspace
			// is deleted.
			// 1) A better option is to create the temporary workspace and snapshot upfront using a separate task from checkout()
			// That way, if there is any exception after that, we can safely delete the workspace in finally()	
			// 2) Create and delete workspace in accept and then use load from snapshot (that already creates and deletes a 
			// workspace).
			// 3) Create a workspace in accept but delete it at the end of the build in RTCRunListener. However, if load fails
			// the workspace is deleted immediately.
			// However, deleting the temporary workspace requires build toolkit on master. Some cases, the build toolkit configured
			// on master is invalid. In order to account for this scenario where master buildtoolkit is invalid, the temporary
			// repository workspace will be deleted during checkout() itself. 
			boolean isValidMasterBuildToolkit = validateBuildToolkitPath(localBuildToolkit);
			boolean shouldDeleteTemporaryWorkspace = !isValidMasterBuildToolkit;
			Map<String, Object> acceptResult = workspacePath.act(acceptTask);
			Map<String, String> buildProperties = (Map<String, String>)acceptResult.get("buildProperties"); //$NON-NLS-1$
			buildResultAction.addBuildProperties(buildProperties);
			parentActivityId = (String)acceptResult.get("parentActivityId"); //$NON-NLS-1$
			connectorId = (String)acceptResult.get("connectorId"); //$NON-NLS-1$
			streamData = (Map<String, String>)acceptResult.get("buildStreamData"); //$NON-NLS-1$
			String loadPolicyTemp = loadPolicy;
			
			if (useBuildDefinitionInBuild) {
				loadPolicyTemp =  useDynamicLoadRules? LOAD_POLICY_USE_DYNAMIC_LOAD_RULES : null;
			}

			// maintain backward compatibility give preference to "com.ibm.team.build.useExtension" job property
			if (RtcExtensionProvider.isEnabled(build, listener)) {
				loadPolicyTemp = LOAD_POLICY_USE_DYNAMIC_LOAD_RULES;
			}

			// get the extension provider
			RtcExtensionProvider extProvider = null;
			if (loadPolicyTemp != null && LOAD_POLICY_USE_DYNAMIC_LOAD_RULES.equals(loadPolicyTemp.trim())) {
				LOGGER.finer("RTCScm.checkout : Using dynamic load rules.");
				extProvider = RtcExtensionProvider.getExtensionProvider(build, listener);
			}
			
			RTCLoadTask loadTask = new RTCLoadTask(
					build.getParent().getName() + " " + build.getDisplayName() + " " + node.getDisplayName(), //$NON-NLS-1$ //$NON-NLS-2$
					nodeBuildToolkit, loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(), loginInfo.getTimeout(),
					getProcessArea(), buildResultUUID, buildWorkspace, buildSnapshotContextMap, buildSnapshot, buildStream, streamData,
					isCustomSnapshotName, snapshotName, listener, workspacePath.isRemote(), debug, LocaleProvider.getLocale(), parentActivityId,
					connectorId, extProvider, Util.fixEmptyAndTrim(loadPolicyTemp), Util.fixEmptyAndTrim(componentLoadConfig), Helper.parseConfigurationValue(build, null,
							Util.fixEmptyAndTrim(getComponentsToExclude()), listener), Helper.parseConfigurationValue(build, null,
							Util.fixEmptyAndTrim(getPathToLoadRuleFile()), listener), clearLoadDirectory, createFoldersForComponents,
					        getAcceptBeforeLoad(), Helper.getTemporaryWorkspaceComment(build), shouldDeleteTemporaryWorkspace, options);

			if (buildResultUUID != null) {
				String rootUrl = Hudson.getInstance().getRootUrl();
				if (rootUrl != null) {
					rootUrl = Util.encode(rootUrl);
				}
				String projectUrl = build.getParent().getUrl();
				if (projectUrl != null) {
					projectUrl = Util.encode(projectUrl);
				}
				String buildUrl = build.getUrl();
				if (buildUrl != null) {
					buildUrl = Util.encode(buildUrl);
				}
				loadTask.setLinkURLs(rootUrl,  projectUrl, buildUrl);
			}
			
			// build properties given by load will be added to RTCBuildResultAction
			Map<String, Object> loadResult = null;
			if (Util.fixEmptyAndTrim(loadDirectory) != null) {
				FilePath newWorkspacePath = workspacePath.child(loadDirectory);
				loadResult = newWorkspacePath.act(loadTask);
			} else {
				loadResult = workspacePath.act(loadTask);
			}
			addTemporaryWorkspaceDetailsToAction(loadResult, buildResultAction);
			
			// Before leaving, add metronome data to build
			addMetronomeDataToBuild(build, loadResult, metronomeOptions, listener);
    	} catch (Exception e) {
    		Throwable eToReport = e;
    		if (eToReport instanceof InvocationTargetException) {
    			if (e.getCause() != null) {
    				eToReport = e.getCause();
    			}
    		}
    		if (eToReport instanceof InterruptedException) {
        		LOGGER.log(Level.FINER, "build interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
    			throw (InterruptedException) eToReport;
    		}
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checkout_failure3(eToReport.getMessage()));
    		if (Helper.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		LOGGER.log(Level.FINER, "Create build result failure " + eToReport.getMessage(), eToReport); //$NON-NLS-1$

    		// if we can't check out then we can't build it
    		throw new AbortException(Messages.RTCScm_checkout_failure4(e.getMessage()));
    	}
		finally {
			LOGGER.finer("RTCScm.checkout : End");
		}
	}

	private void handlePollingOnlyMode(Run<?,?> build, FilePath workspacePath, 
								TaskListener listener, File changeLogFile,
								String buildDefinitionId,
								String buildWorkspaceName) throws IOException, InterruptedException {
		// We can ignore pollingOnly for non pipeline jobs or other build types
    	// But asserting and throwing an error helps users avoid misconfiguration
    	Helper.assertPollingOnlyConditions(build.getParent(), getBuildTypeStr());
    	
    	// If both the checks pass, just return and don't perform accept/load
    	// if no snapshot has been provided for comparison.
    	LOGGER.info("Polling-only option is selected. Skipping accept and load of the build workspace."); //$NON-NLS-1$
    	listener.getLogger().println(Messages.RTCScm_pollingonly_selected_skipping_accept_load());
    	listener.getLogger().flush();
    	
    	// Check if pollingData is set. If yes, then perform a compare
    	PollingOnlyData pollingOnlyData = this.getPollingOnlyData();
    	// Nothing to do if there is no polling data
    	// This cannot be the case if we are in this method, but just to  be sure
    	if (pollingOnlyData == null) {
    		return;
    	}
    	
    	String snapshotUUIDForCompare = Util.fixEmptyAndTrim(pollingOnlyData.getSnapshotUUID());
    	// Nothing to do if snapshot UUID is empty
    	if (snapshotUUIDForCompare == null) {
    		return;
    	}
    	
    	listener.getLogger().println(Messages.RTCScm_pollingonly_selected_generating_changelog(
    					snapshotUUIDForCompare));
    	
		Node node = workspacePath.toComputer().getNode();
		// Get the build toolkit on the node where the task will execute
		String nodeBuildToolkit = getDescriptor().getBuildToolkit(getBuildTool(), node, listener);

		RTCLoginInfo loginInfo;
		try {
			// TODO will be converted to use new the credentials tracking entry point
			// We wouldn't need the nodeBuildToolkit at that point
			loginInfo = getLoginInfo(build.getParent(), nodeBuildToolkit);
		} catch (InvalidCredentialsException e1) {
			throw new AbortException(e1.getMessage());
		}
		
		if (workspacePath.isRemote()) {
			Helper.sendJarsToAgent(workspacePath);
		}

		boolean debug = Helper.isDebugEnabled(build, listener);
		// Before proceeding, log the details of master and node build toolkit
		{
			String masterBuildToolkit = getDescriptor().getMasterBuildToolkit(getBuildTool(), listener);
			logBuildToolkitVersions(workspacePath, listener, masterBuildToolkit, nodeBuildToolkit, debug);
		}

		// Create the task to retrieve the workspaceUUID
		RetrieveWorkspaceDetailsTask retrieveWorkspaceDetailsTask = new RetrieveWorkspaceDetailsTask(nodeBuildToolkit,
				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
				loginInfo.getTimeout(), buildDefinitionId, buildWorkspaceName, 
				 LocaleProvider.getLocale(), debug, listener);
		// Workspace UUID cannot be null unless someone deleted the workspace on the fly 
		// or modified the build definition to remove jazz source control configuration
		String workspaceUUID = workspacePath.act(retrieveWorkspaceDetailsTask);
		
		// Retrieve the old snapshotUUID to compare with
		String previousSnapshotUUID = Helper.getSnapshotUUIDFromPreviousBuild(build, workspaceUUID, debug, 
				listener, Locale.getDefault());
		
		// Call the generate changelog task to generate the changelog.
		if (debug) {
			listener.getLogger().println(Messages.
					RTCScm_generating_changelog_writing_to_file(changeLogFile));
		}
		RemoteOutputStream changeLog = null;
		if (changeLogFile != null) {
			OutputStream changeLogStream = new FileOutputStream(changeLogFile);
			changeLog = new RemoteOutputStream(changeLogStream);
		}
		
		GenerateChangelogTask generateChangeLogTask = new GenerateChangelogTask(nodeBuildToolkit,
				loginInfo.getServerUri(), loginInfo.getUserId(),
				loginInfo.getPassword(), loginInfo.getTimeout(),
				snapshotUUIDForCompare, workspaceUUID, previousSnapshotUUID, changeLog, 
				LocaleProvider.getLocale(), debug, listener);
		Map<String, Object> retData = workspacePath.act(generateChangeLogTask);
		@SuppressWarnings("unchecked")
		Map<String, String> buildProperties = (Map<String, String>) retData.get("buildProperties");
		setBuildResultAction(new RTCBuildResultAction(loginInfo.getServerUri(), 
											null ,false, this));
		RTCBuildResultAction action = getBuildResultAction();
		build.addAction(action);
		action.addBuildProperties(buildProperties);
	}

	public PollingOnlyData getPollingOnlyData() {
		return pollingOnlyData;
	}

	/**
	 * 
	 * Logs the build toolkit version for the local build toolkit (master) and node build toolkit
	 * If checkout happens on the master, then local and node build toolkit are the same.
	 * 
	 * If debug is <code>false</code>, then this method does not print anything on the console
	 * 
	 * @param workspacePath The Jenkins workspace path, used to determine if the checkout is happening 
	 *  					on a node or on master
	 * @param listener Log messages 
	 * @param localBuildToolkit The build toolkit on master 
	 * @param nodeBuildToolkit The build toolkit on node 
	 * @param debug Is debug turned on, this determines whether this method will log the versions
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private void logBuildToolkitVersions(FilePath workspacePath, TaskListener listener, String localBuildToolkit,
			String nodeBuildToolkit, boolean debug) throws InterruptedException, IOException {
		if (!debug) {
			return;
		}
		String masterBuildToolkitVersion = null;
		String nodeBuildToolkitVersion = ""; //$NON-NLS-1$
		if (localBuildToolkit != null) {
			masterBuildToolkitVersion = BuildToolkitVersionTask.getBuildToolkitVersion(localBuildToolkit, 
							 "master", debug, LocaleProvider.getLocale(), listener); //$NON-NLS-1$
		}
		String nodeName = workspacePath.getParent().toComputer().getDisplayName();
		if (workspacePath.isRemote()) {
			BuildToolkitVersionTask task = new BuildToolkitVersionTask(nodeBuildToolkit,
						nodeName, 
						debug, LocaleProvider.getLocale(), listener);
			nodeBuildToolkitVersion = workspacePath.act(task);
		}
		if (masterBuildToolkitVersion != null) {
			listener.getLogger().println(Messages.RTCScm_buildtoolkit_version_on_master(
					localBuildToolkit, masterBuildToolkitVersion));
		} else {
			listener.getLogger().println(
					Messages.RTCScm_buildtoolkit_version_on_master(localBuildToolkit,
							"n/a"));
		}
		if (workspacePath.isRemote()) {
			if (nodeBuildToolkitVersion != null) {
				listener.getLogger().println(
					Messages.RTCScm_buildtoolkit_version_on_node(nodeBuildToolkit, 
							nodeName, nodeBuildToolkitVersion));		
			} else {
				listener.getLogger().println(
						Messages.RTCScm_buildtoolkit_version_on_node(nodeBuildToolkit, 
								nodeName, "n/a"));		
			}
		}
	}

	private String getLabel(Run<?, ?> build) {
		
		// TODO if we have a build definition & build result id we should probably
		// follow a similar algorithm to RTC?
		// In the simple plugin case, generate the name from the project and the build
		return Messages.RTCScm_build_label(build.getNumber());
	}

	@Override
	public boolean supportsPolling() {
		LOGGER.finest("RTCScm.supportsPolling : Begin");
		return true;
	}

	@Override
	public boolean requiresWorkspaceForPolling() {
		LOGGER.finest("RTCScm.requiresWorkspaceForPolling : Begin");
		return false;
	}

	@Override
	public PollingResult compareRemoteRevisionWith(
			Job<?, ?> project, Launcher launcher, FilePath workspacePath,
			TaskListener listener, SCMRevisionState revisionState) throws IOException,
			InterruptedException {
		// if #requiresWorkspaceForPolling is false, expect that launcher and workspacePath are null
		LOGGER.finest("RTCScm.compareRemoteRevisionWith : Begin");
		
		listener.getLogger().println(Messages.RTCScm_checking_for_changes());
		
		// Get values for jazz scm stream, jazz scm workspace and build definition from default values of 
		// job parameters if any
		String buildType = getBuildTypeStr();
		String buildDefinition = (BUILD_DEFINITION_TYPE.equals(buildType)) ? Helper.parseConfigurationValue(project, Util.fixEmptyAndTrim(getBuildDefinition()), listener)
																		: Util.fixEmptyAndTrim(getBuildDefinition());
		String buildWorkspace = (BUILD_WORKSPACE_TYPE.equals(buildType)) ? Helper.parseConfigurationValue(project, Util.fixEmptyAndTrim(getBuildWorkspace()), listener)
																		: Util.fixEmptyAndTrim(getBuildWorkspace());
		String buildStream = (BUILD_STREAM_TYPE.equals(buildType)) ? Helper.parseConfigurationValue(project, Util.fixEmptyAndTrim(getBuildStream()), listener):
																		 Util.fixEmptyAndTrim(getBuildStream());
		
		// Validate that given buildType is build from stream, buildStream is non empty
		if (BUILD_STREAM_TYPE.equals(buildType) && buildStream == null) {
			throw new AbortException(Messages.RTCScm_checking_for_changes_failure(Messages.RTCScm_build_stream_empty()));
		}
		
		// Error checking for pollingOnly mode
		// If pollingOnly is set to true, then buildType should be buildDefinition or build workspace.
		// The job type should be pipeline.
		// If any one of the conditions don't match, then abort the operation.
		if (true == getPollingOnly()) {
			// Assert the conditions, if both pass, then continue with polling
			Helper.assertPollingOnlyConditions(project, buildType);
		}

		// Current polling behavior will not perform another build if the current one is not finished, 
		// unless the property team_scm_acceptPhaseOver. This is desirable for pipelines where only
		// one build should be currently active.
		// If a snapshot is added to the checkout step for change log generation, then "team_scm_acceptPhaseOver" 
		// will be added to the Jenkins build.
		
		// check to see if there are incoming changes
    	try {
    		// If the current configuration does not support polling, return no changes
    		if (!isConfigSupportsPolling(getBuildTypeStr())) {
    			listener.getLogger().println(Messages.RTCScm_polling_not_supported());
    			LOGGER.finer("Polling is not supported for this configuration");
    			return new PollingResult(revisionState, new RTCRevisionState(BIGINT_ZERO), Change.NONE);  
    		}

    		// Handle polling request for build workspace or build definition
    		boolean bAvoidUsingToolkit = getAvoidUsingToolkit();
    		String masterToolkit = getDescriptor().getMasterBuildToolkit(getBuildTool(), listener);
    		RTCLoginInfo loginInfo = getLoginInfo(project, masterToolkit);
    		boolean useBuildDefinitionInBuild = BUILD_DEFINITION_TYPE.equals(getBuildTypeStr());
    		
    		if (useBuildDefinitionInBuild && bAvoidUsingToolkit) {
    			//if toolkit is not to be used, and
	    		//if the build is in queue then their are changes, avoid a recheck and resetting the Quiet period
	    		if(isInQueue(project)) {
	    			if (LOGGER.isLoggable(Level.FINER)) {
	    				LOGGER.finer("The build request for the project " + project.getName() + //$NON-NLS-1$ //$NON-NLS-2$
	    						" is already in queue, return polling result as NO_CHANGES to avoid resetting the quiet time"); //$NON-NLS-1$
	    			}
	    			return new PollingResult(revisionState, new RTCRevisionState(BIGINT_ZERO), Change.NONE); 
	    		}

	    		Boolean changesIncoming = RTCFacadeFacade.incomingChangesUsingBuildDefinitionWithREST(masterToolkit,
	    				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(),
						buildDefinition,
						buildWorkspace,
						listener);
	    		

	    		// We have to replicate the check for project#isBuilding
	    		// to make sure even if changes are detected, a build is queued 
	    		// only if the previous build has completed the accept phase (subject to 
	    		// identifying the right RTCBuildResultAction.
	    		
	    		// Even if we don't get to the right RTCBuildResultAction, 
	    		// we need to sync up the option in the job "do not allow concurrent builds" 
	    		// to reduce the chances of queuing a build which may not have any valid changes.
	    		// If the option is present and a project is building, always return no changes?
	    		if (changesIncoming.equals(Boolean.TRUE)) {
	    			listener.getLogger().println(Messages.RTCScm_changes_found());
	    			// arbitrarily specify non-zero revision hash in current revision state
	    			return new PollingResult(revisionState, new RTCRevisionState(BIGINT_ONE), Change.SIGNIFICANT);
	    		} else {
	    			listener.getLogger().println(Messages.RTCScm_no_changes_found());
	    			return new PollingResult(revisionState, new RTCRevisionState(BIGINT_ZERO), Change.NONE);
	    		}
    		} else { // buildWorkspace
    			// If acceptBeforeLoad is false, we should always build.
    			if (!getAcceptBeforeLoad()) {
	    			listener.getLogger().println("Checking incoming changes for \"" + buildWorkspace + "\"");
	    			listener.getLogger().println("RTCScm is not configured to accept latest changes.");
	    			listener.getLogger().println("In this configuration, polling will result in unnecessary builds.");
	    			listener.getLogger().println(Messages.RTCScm_changes_found());
    				return new PollingResult(revisionState, new RTCRevisionState(BIGINT_ZERO), Change.SIGNIFICANT);
    			}
    			String strIgnoreOutgoingFromBuildWorkspace = getIgnoreOutoingFromBuildWorkspaceParamValue(project, listener);
    			boolean ignoreOutgoingFromBuildWorkspace = "true".equals(strIgnoreOutgoingFromBuildWorkspace); 
    			// Get the previous snapshot for stream case
    			String streamChangesData = Helper.getStreamChangesDataFromLastBuild(project, masterToolkit, loginInfo, getProcessArea(), buildStream, LocaleProvider.getLocale()).getSecond();
    			
    			BigInteger currentRevisionHash = RTCFacadeFacade.incomingChangesUsingBuildToolkit(masterToolkit,
	    				loginInfo.getServerUri(), loginInfo.getUserId(), loginInfo.getPassword(),
						loginInfo.getTimeout(), getProcessArea(), useBuildDefinitionInBuild,
						buildDefinition,
						buildWorkspace,
						buildStream,
						streamChangesData,
						listener, ignoreOutgoingFromBuildWorkspace);
    			LOGGER.finer("currentRevisionHash is " + currentRevisionHash.toString());
    			RTCRevisionState currentRevisionState = new RTCRevisionState(currentRevisionHash);
    			Change change = null;
    			if (isInQueue(project)) {
    				LOGGER.finer("Project is already in QUEUE");
    				// get last revision hash
    				if (revisionState instanceof RTCRevisionState) {
    					// If current hash is not equal to previous hash, then return SIGNIFICANT change
    					// This makes the polling reset quiet period
    					// otherwise we will let the quiet period to expire
	    				BigInteger lastRevisionHash = ((RTCRevisionState)revisionState).getLastRevisionHash();
	    				if (LOGGER.isLoggable(Level.FINER)) {
	    					LOGGER.finer("LAST REVISION STATE " + lastRevisionHash.toString());
	    				}
	    				change = currentRevisionHash.equals(lastRevisionHash) ? Change.NONE : Change.SIGNIFICANT;
    				} else {
    					// we are in quiet period, and previous revisionState is not of RTCRevisionState type...
    					// ideally we should never come here.
    					if (LOGGER.isLoggable(Level.FINER)) {
    	    				LOGGER.finer("The build request for the project " + project.getName() + //$NON-NLS-1$ //$NON-NLS-2$
    	    						" is already in queue, return polling result with changes as NONE to avoid resetting the quiet time"); //$NON-NLS-1$
    	    			}
    					change = Change.NONE;
    				}
    			} else {
    				if (project.isBuilding()) {
        				LOGGER.finer("Project is building");
						// This will not work if multiple RTCBuildResultActions are present, we will not be sure which 
        				// RTCBuildResultAction we should use. The fool proof way to do so is to check if the build result action 
        				// has the same job properties like build type, repository URL, build definition id and use it for 
        				/// fetching the property. If no such Build Result action is found, then we should return Change.NONE to 
        				// avoid triggering a build unnecessarily.
    					RTCBuildResultAction rtcBuildResultAction = project.getLastBuild().getAction(RTCBuildResultAction.class);
    					if ((rtcBuildResultAction != null) && (rtcBuildResultAction.getBuildProperties() != null) &&
    							"true".equals(rtcBuildResultAction.getBuildProperties().get("team_scm_acceptPhaseOver"))) {
    						// snapshot has been created, 
    						// hence further changes would not be taken by the running build...
    						LOGGER.finer("Changes detected and accept phase is over"); 
    						change = (currentRevisionHash.equals(BIGINT_ZERO)) ? Change.NONE : Change.SIGNIFICANT;
    					} else {
    						// build is running, but snapshot has not been created yet,
    						// hence any further changes can still be considered by the running build
    						// therefore return no changes to avoid queuing another build
    						LOGGER.finer("Cannot determine if accept phase is over. Avoiding queuing a new build.");
    						change = Change.NONE;
    					}
    				} else {
        				LOGGER.finer("Project is not in queue nor it is building");
        				// This could be a problem if  accept failed or if no accept happened. Polling would repeatedly claim there are 
        				// new changes when no new changes might have been detected when compared to the previous revision 
        				// hash. By not comparing with previous revision hash but only comparing with zero, RTCSCM 
        				// will repeatedly send "changes found". Sometimes, this is not desirable. If new changes were 
        				// really found, then it should be different from previously computed revision hash. The side effect 
        				// of such a change is that only new changes will trigger a build and perhaps fail in the  accept phase 
        				// but if no new changes are detected when compared to previous revision hash, no new builds will be triggered.
        				// Thus avoiding infinite builds in Jenkins, especially with short polling intervals. 
    					change = (currentRevisionHash.equals(BIGINT_ZERO)) ? Change.NONE : Change.SIGNIFICANT;
    				}
    			}
    			
    			if (LOGGER.isLoggable(Level.FINER)) {
    				LOGGER.finer("Change is " + change.toString());
    			}
    			if (change != Change.NONE) {
	    			listener.getLogger().println(Messages.RTCScm_changes_found());
    			} else {
	    			listener.getLogger().println(Messages.RTCScm_no_changes_found());
    			}
    			return new PollingResult(revisionState, currentRevisionState, change);
    		}
    		
    	} catch (Exception e) {
    		Throwable eToReport = e;
    		if (eToReport instanceof InvocationTargetException) {
    			if (e.getCause() != null) {
    				eToReport = e.getCause();
    			}
    		}
    		if (e instanceof InterruptedException) {
        		LOGGER.log(Level.FINER, "Checking for changes interrupted " + eToReport.getMessage(), eToReport); //$NON-NLS-1$
    			throw (InterruptedException) e;
    		}
    		PrintWriter writer = listener.fatalError(Messages.RTCScm_checking_for_changes_failure(eToReport.getMessage()));
    		if (Helper.unexpectedFailure(eToReport)) {
    			eToReport.printStackTrace(writer);
    		}
    		
    		// if we can't check for changes then we can't build it
    		throw new AbortException(Messages.RTCScm_checking_for_changes_failure2(eToReport.getMessage()));
    	}
    	finally {
    		LOGGER.finer("RTCScm.compareRemoteRevisionWith : End");
    	}
	}

	private String getIgnoreOutoingFromBuildWorkspaceParamValue(Job<?,?> job, TaskListener listener) {
		String value = "false";
		if (Boolean.parseBoolean(System.getProperty(JOB_PROPERTY_OVERRIDES_SYSTEM_PROPERTY, "false"))) {
			value = Helper.getStringBuildParameter(job, IGNORE_OUTGOING_FROM_BUILD_WS_WHILE_POLLING, listener);
		} else {
			if(System.getProperty(IGNORE_OUTGOING_FROM_BUILD_WS_WHILE_POLLING) != null) {
				value = Util.fixEmptyAndTrim(System.getProperty(IGNORE_OUTGOING_FROM_BUILD_WS_WHILE_POLLING));
			}
		}
		return value;
	}

	private boolean isInQueue(Job<?, ?> project) {
		LOGGER.finest("RTCScm.isInQueue : Begin");
		if(project instanceof AbstractProject) {
			return project.isInQueue();
		}else if (project instanceof Task && Jenkins.getInstance().getQueue() != null) {
			return Jenkins.getInstance().getQueue().contains((Task)project);
		} else {
			// returning false, since there is no good way to determine if job is in queue
			return false;
		}
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		LOGGER.finest("RTCScm.createChangeLogParser : Begin");
		return new RTCChangeLogParser();
	}

	@Override
	public RTCRepositoryBrowser getBrowser() {
		LOGGER.finest("RTCScm.getBrowser : Begin");
		return new RTCRepositoryBrowser(getServerURI());
	}
	
	/**
	 * This is actually an override. We are building against 1.580.1 and this method does 
	 * not exist till 2.60. 
	 */
	//@Override
	public void buildEnvironment(Run<?,?> build, Map<String, String> environment) {
		LOGGER.log(Level.FINEST, "Entering RTCScm:buildEnvironment");
		if (getBuildResultAction() == null) {
			return;
		}
		if (getBuildResultAction().getBuildProperties() == null) {
			return;
		}
		for (Entry<String, String> entry : getBuildResultAction().getBuildProperties().entrySet()) {
			environment.put(entry.getKey(), entry.getValue());
		}
		// NOTE We could put metronome data as is to the build environment
		// which pipeline builds can read off as a string.
		LOGGER.log(Level.FINEST, "Exiting RTCScm:buildEnvironment");
	}

	public boolean getOverrideGlobal() {
		return overrideGlobal;
	}
	
	public RTCLoginInfo getLoginInfo(Job<?, ?> job, String toolkit) throws InvalidCredentialsException {
		return new RTCLoginInfo(job, toolkit, getServerURI(), getUserId(), getPassword(), getPasswordFile(), getCredentialsId(), getTimeout());
	}
	
	public RTCLoginInfo getLoginInfo2(Run<?, ?> build, String toolkit, TaskListener listener, 
						boolean isDebug) throws InvalidCredentialsException {
		if(getCredentialsId() == null)
			return getLoginInfo(build.getParent(), toolkit);
		else
			return new RTCLoginInfo(build, getServerURI(), getCredentialsId(), getTimeout(),
						listener, isDebug);
	}
	
	public String getBuildTool() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalBuildTool();
		}
		return buildTool;
	}

	public boolean getAvoidUsingToolkit() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalAvoidUsingToolkit();
		}
		return avoidUsingToolkit;
	}

	public String getServerURI() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalServerURI();
		}
		return serverURI;
	}

	public int getTimeout() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalTimeout();
		}
		return timeout;
	}

	public String getCredentialsId() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalCredentialsId();
		}
		return credentialsId;
	}

	public String getUserId() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalUserId();
		}
		return userId;
	}
	
	public String getPassword() {
		String result;
		if (!overrideGlobal) {
			result = getDescriptor().getGlobalPassword();
		} else if (password == null) {
			result = null;
		} else {
			result = password.getPlainText();
		}
		return result;
	}
	
	public String getPasswordFile() {
		if (!overrideGlobal) {
			return getDescriptor().getGlobalPasswordFile();
		}
		return passwordFile;
	}
	
	public File getPasswordFileFile() {
		String file = getPasswordFile();
		if (file != null && file.length() > 0) {
			return new File(file);
		}
		return null;
	}

	/**
	 * Determine if they are using the deprecated authentication in any way
	 * Takes into account whether they are overriding the global configuration or not.
	 * @return <code>true</code> if using user id & password or password file
	 * <code>false</code> if using credentials or no password authentication setup.
	 */
	public boolean usingDeprecatedPassword() {
		if (getDescriptor().deprecatedCredentialEditAllowed()) {
			return true;
		}
		String credentials = getCredentialsId();
		if (overrideGlobal && (credentials == null || credentials.isEmpty())) {
			// consider them to be using user id & old password way if supplied
			// not using strict validation since we would still work if too much info supplied.
			if (userId != null	&& !userId.isEmpty()
					&& ((password != null && password.getPlainText() != null && !password.getPlainText().isEmpty())
						|| (passwordFile != null && !passwordFile.isEmpty()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Show the deprecated password file in the UI if editting allowed or its available for use
	 * @return <code>true</code> to show it <code>false</code> otherwise
	 */
	public boolean showPasswordFile() {
		if (getDescriptor().deprecatedCredentialEditAllowed() 
				|| (overrideGlobal && passwordFile != null && !passwordFile.isEmpty())) {
			return true;
		}
		return false;

	}

	public boolean showPassword() {
		if (getDescriptor().deprecatedCredentialEditAllowed() 
				|| (overrideGlobal && password != null && password.getPlainText() != null && !password.getPlainText().isEmpty())) {
			return true;
		}
		return false;
	}

    @Exported
	public String getBuildTypeStr() {
		// migrate existing jobs with only build workspace or build definition defined
    	boolean oldData = false;
		if (buildTypeStr == null && buildWorkspace != null) {
			buildTypeStr = BUILD_WORKSPACE_TYPE;
			oldData = true;
		}else if(buildTypeStr == null && buildDefinition != null) {
			buildTypeStr = BUILD_DEFINITION_TYPE;
			oldData = true;
		}
		if(oldData) {
			LOGGER.warning("The job's config.xml has data stored in an older format, to migrate to the newer format resave the job's configuration");
		} 
		return buildTypeStr;
	}
    
    public BuildType getBuildType() {
		return buildType;
	}
	
	public String getBuildWorkspace() {
		return buildWorkspace;
	}
	
	public String getLoadDirectory() {
		return loadDirectory;
	}	
	
	public String getLoadPolicy() {
		if (this.loadPolicy == null  && getCreateFoldersForComponents()) {
			this.loadPolicy = LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG;
		}
		return this.loadPolicy;
	}	
	
	public String getComponentLoadConfig() {
		return this.componentLoadConfig;
	}
	
	public String getPathToLoadRuleFile() {
		return this.pathToLoadRuleFile;
	}
	
	public String getComponentsToExclude() {
		return this.componentsToExclude;
	}
	
	public boolean getClearLoadDirectory() {
		return clearLoadDirectory;
	}
	
	public String getBuildSnapshot() {
		return buildSnapshot;
	}
	
	public String getBuildStream() {
		return buildStream;
	}
	
	public boolean getCreateFoldersForComponents() {
		return createFoldersForComponents;
	}
	
	public boolean getAcceptBeforeLoad() {
		// return true if the options has not been set.
		return !"false".equals(acceptBeforeLoad);
	}
	
    @Exported
	public String getBuildDefinition() {
		return buildDefinition;
	}
    
    public String getProcessArea() {
    	return processArea;
    }
    
    public String getCurrentSnapshotOwnerType() {
    	return currentSnapshotOwnerType;
    }
    
    public BuildSnapshotContext getBuildSnapshotContext() {
    	return buildSnapshotContext;
    }
    
    public boolean getOverrideDefaultSnapshotName() {
    	return overrideDefaultSnapshotName;
    }

	public String getCustomizedSnapshotName() {
		return customizedSnapshotName;
	}

	public boolean getUseDynamicLoadRules() {
		return useDynamicLoadRules;
	}
	
	@Override
	@Exported
	public String getType() {
		return super.getType();
	}
	
	public boolean getGenerateChangelogWithGoodBuild() {
		return generateChangelogWithGoodBuild;
	}
	
	public boolean getAddLinksToWorkItems() {
		return addLinksToWorkItems;
	}
	
	public boolean getPollingOnly() {
		return this.pollingOnly;
	}

	@Override
    public String getKey() {
		LOGGER.finest("RTCScm.getKey : Begin");
		StringBuilder key = new StringBuilder();
		key.append("teamconcert-")
			.append(getServerURI())
			.append("-")
			.append(getBuildTool());

		if (buildDefinition != null) {	
			key.append("-")
			   .append(buildDefinition);
		}
		if (buildWorkspace != null) {
			key.append("-")
			   .append(buildWorkspace);
		}
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("RTCScm.getKey key is " + key);
		}
		LOGGER.finest("RTCScm.getKey : End");
		return key.toString();
    }
	
	private static String getBuildResultUUID(Run<?,?> build, TaskListener listener) throws IOException, InterruptedException {
		 LOGGER.finest("RTCScm.getBuildResultUUID : Begin");
		 return Helper.getStringBuildParameter(build, RTCJobProperties.BUILD_RESULT_UUID, listener);
	}

	private boolean isConfigSupportsPolling(String buildType) {
		if (buildType.equals(BUILD_DEFINITION_TYPE) || buildType.equals(BUILD_WORKSPACE_TYPE) 
				|| buildType.equals(BUILD_STREAM_TYPE)) {
			return true;
		}
		return false;
	}
	
	private static void validateInput(String buildType,	String buildSnapshotNameOrUUID, String buildStreamName) throws AbortException {
		// If  snapshot UUID is null, we cannot proceed with checkout 
		if (BUILD_SNAPSHOT_TYPE.equals(buildType) && buildSnapshotNameOrUUID == null) {
			throw new AbortException(Messages.RTCScm_checkout_failure4(Messages.RTCScm_snapshot_not_provided()));
		}
		
		if (BUILD_STREAM_TYPE.equals(buildType) && buildStreamName == null) {
			throw new AbortException(Messages.RTCScm_checkout_failure4(Messages.RTCScm_stream_not_provided()));
		}	
	}

	private void validateLoadPolicyAndComponentLoadConfig(String loadPolicy, String componentLoadConfig) throws AbortException {
		if (loadPolicy != null && !validLoadPolicyValues.contains(loadPolicy)) {
			throw new AbortException(Messages.RTCScm_checkout_failure4(Messages.RTCScm_invalid_value_for_loadPolicy(
					LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG, LOAD_POLICY_USE_LOAD_RULES, LOAD_POLICY_USE_DYNAMIC_LOAD_RULES, loadPolicy)));
		}
		if (componentLoadConfig != null) {
			if ((loadPolicy == null || (loadPolicy != null && LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)))
					&& !validComponentLoadConfigValues.contains(componentLoadConfig)) {
				throw new AbortException(Messages.RTCScm_checkout_failure4(Messages.RTCScm_invalid_value_for_componentLoadConfig(
						COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS, COMPONENT_LOAD_CONFIG_EXCLUDE_SOME_COMPONENTS, componentLoadConfig)));
			}
			if (loadPolicy != null && !LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)) {
				LOGGER.finest("componentLoadConfig attribute is not applicable when the value for the loadPolicy attribute is set to '" + loadPolicy
						+ "'.");
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void addTemporaryWorkspaceDetailsToAction(Map<String, Object> loadResult, RTCBuildResultAction action) {
		if (loadResult == null || action == null) {
			return;
		}
		Map<String, String> temporaryWorkspaceProperties = (Map<String, String>) loadResult.get(RTCJobProperties.TEMPORARY_REPO_WORKSPACE_DATA);
		if (temporaryWorkspaceProperties != null) {
			action.addBuildProperties(temporaryWorkspaceProperties);
		}		
	}
	
	/**
	 * Add the metronome data to the Build Result action's properties.
	 * This will be exported to the Jenkins build as environment variables.
	 * 
	 * @param loadResult The result from the load phase
	 * @param action The RTC Build Result action
	 */
	private void addMetronomeDataToBuild(Run<?,?> build, Map<String, Object> loadResult, 
								Map<String, Object> metronomeOptions, TaskListener listener) {
		
		// This ensures that metronome data is added only if the user has a property in the Jenkins 
		// job to upload the logs
		// For a build definition based build, we will upload the logs only to the build result. 
		// hence we will return immediately if the shouldCreateMetronome is set to false in the Jenkins 
		// job.
		Boolean shouldCreateMetronomeReport = (Boolean) metronomeOptions.get(
								RTCJobProperties.TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME);
		if (shouldCreateMetronomeReport == null || 
				Boolean.FALSE.equals(shouldCreateMetronomeReport) || 
				getBuildTypeStr().equals(BUILD_DEFINITION_TYPE)) {
			return;
		}
		// Check if metronome data should be pushed
		@SuppressWarnings("unchecked")
		Map<String, String> metronomeData = (Map<String, String>)loadResult.get(METRONOME_DATA_PROPERTY_NAME);
		if (metronomeData == null) {
			return;
		}
		
		String	statisticsData= Util.fixEmpty(metronomeData.get(RTCJobProperties.STATISTICS_DATA_PROPERTY_NAME));
		String	statisticsReport = Util.fixEmpty(metronomeData.get(RTCJobProperties.STATISTICS_REPORT_PROPERTY_NAME));
		if (statisticsData == null && statisticsReport == null) {
			return;
		}
		try {
			// Put the statistics files under a new folder called "teamconcert"
			File teamConcertDir = new File(build.getRootDir(), TEAMCONCERT_FOLDER_NAME); //$NON-NLS-1$
			if (!teamConcertDir.exists()) {
				teamConcertDir.mkdir();
			}
			// Create a folder called metronome under rtcscm folder
			File metronomeDir = new File(teamConcertDir, TEAMCONCERT_METRONOME_NAME); //$NON-NLS-1$
			if (!metronomeDir.exists()) {
				metronomeDir.mkdir();
			}
			// We need the suffix because there could be multiple invocations of RTCScm in a 
			// pipeline job 
			File statisticsDataFile = new File(metronomeDir, getStatisticsDataFileName(metronomeOptions));
			File statisticsReportFile =  new File(metronomeDir, getStatisticsReportFileName(metronomeOptions));
			writeMetronomeContentToFile(statisticsData, statisticsDataFile);
			writeMetronomeContentToFile(statisticsReport, statisticsReportFile);
			
			// We could export the file names to the build properties
			// This will help users identify which file belongs to this run of RTCScm 
			// (especially in Pipeline jobs)
		}
		catch (Throwable e) {
			// Don't have to fail the build.
			LOGGER.log(Level.WARNING, "Unable to create directory to write metronome data", e); //$NON-NLS-1$
			listener.getLogger().println(Messages.Metronome_Unable_To_Write_MetronomeFile());
		}
	}

	private void writeMetronomeContentToFile(String statisticsData, File statisticsDataFile) {
		if (statisticsData == null) {
			return;
		}
		try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(statisticsDataFile), "UTF-8"))) { //$NON-NLS-1$
			printWriter.print(statisticsData);
			printWriter.flush();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			// Don't have to fail the build here
			LOGGER.log(Level.WARNING, 
					String.format("Error occurred when writing metronome data to file %s",  //$NON-NLS-1$
							statisticsDataFile.getAbsolutePath()), e);
		}
	}
	
	/**
	 * Create the metronome options for Accept and Load phase
	 * 
	 * @param shouldCreateMetronomeReport If the user requested to create metronome report
	 * @return A map of options, keyed by the option name and value is a generic {@link Object}
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public static Map<String, Object> createMetronomeOptions(Run<?,?> build, TaskListener listener)
									throws IOException, InterruptedException {
		boolean shouldCreateMetronomeReport = Boolean.parseBoolean(Helper.getStringBuildParameter(build, 
				RTCJobProperties.TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME, listener));
		Map<String, Object> metronomeOptions = new HashMap<String, Object>(); 
		metronomeOptions.put(RTCJobProperties.TEAM_BUILD_REPORT_STATISTICS_PROPERTY_NAME,
								new Boolean(shouldCreateMetronomeReport));
		
		String timestamp = Long.toString(System.currentTimeMillis());
		String statisticsDataFilePrefix = STATISTICS_DATA_FILE_PREFIX_VALUE + 
								timestamp;
		String statisticsReportFilePrefix = STATISTICS_REPORT_FILE_PREFIX_VALUE +
								timestamp;
		
		metronomeOptions.put(STATISTICS_DATA_FILE_PREFIX_PROPERTY_NAME, 
						statisticsDataFilePrefix); 
		metronomeOptions.put(STATISTICS_REPORT_FILE_PREFIX_PROPERTY_NAME, 
						statisticsReportFilePrefix);
		
		metronomeOptions.put(STATISTICS_DATA_FILE_SUFFIX_PROPERTY_NAME, STATISTICS_DATA_FILE_SUFFIX_VALUE);
		metronomeOptions.put(STATISTICS_REPORT_FILE_SUFFIX_PROPERTY_NAME, STATISTICS_REPORT_FILE_SUFFIX_VALUE);
		
		metronomeOptions.put(STATISTICS_DATA_LABEL_PROPERTY_NAME, STATISTICS_DATA_VALUE);
		metronomeOptions.put(STATISTICS_REPORT_LABEL_PROPERTY_NAME, STATISTICS_REPORT_VALUE);
		
		return metronomeOptions;
	}
	
	private static String getStatisticsDataFileName(Map<String, Object> metronomeOptions) {
		String statisticsDataFilePrefix = (String) metronomeOptions.get(STATISTICS_DATA_FILE_PREFIX_PROPERTY_NAME);
		String fileSuffix = (String) metronomeOptions.get(STATISTICS_DATA_FILE_SUFFIX_PROPERTY_NAME);
		return statisticsDataFilePrefix + fileSuffix;
	}
	
	private static String getStatisticsReportFileName(Map<String, Object> metronomeOptions) {
		String statisticsDataFilePrefix = (String) metronomeOptions.get(STATISTICS_REPORT_FILE_PREFIX_PROPERTY_NAME);
		String fileSuffix = (String) metronomeOptions.get(STATISTICS_REPORT_FILE_SUFFIX_PROPERTY_NAME);
		return statisticsDataFilePrefix + fileSuffix;
	}
	
	private boolean validateBuildToolkitPath(String buildToolkitPath) {
		// Check whether build toolkit on master is valid
		FormValidation buildToolkitCheck = RTCBuildToolInstallation.validateBuildToolkit(false, buildToolkitPath);

		if (buildToolkitCheck.kind.equals(FormValidation.Kind.OK)) {
			 return true;
		}
		return false;
	}

	private void setBuildResultAction(RTCBuildResultAction action) {
		this.buildResultAction = action;
	}
	
	private RTCBuildResultAction getBuildResultAction() {
		return buildResultAction;
	}
}
