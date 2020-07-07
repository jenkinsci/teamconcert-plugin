/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;

import com.ibm.team.build.internal.scm.LoadComponents;
import com.ibm.team.build.internal.scm.SourceControlUtility;
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.IComponentHandle;

/**
 * This method contains utility methods to invoke new methods introduced in 701
 * in a backward compatible means.
 */
@SuppressWarnings("restriction")
public class BackwardCompatibilityUtilFor701 {

	private static final Logger LOGGER = Logger
			.getLogger(BackwardCompatibilityUtilFor701.class.getName());
	
	/**
	 * When using a pre-701 buildtoolkit, this method invokes
	 * SourceControlUtility.updateFileCopyArea implementation that supports
	 * loading a workspace when loadPolicy is set to useLoadRules and a load
	 * rule file is specified or when loadPolicy is set to useDynamicLoadRules
	 * and the dynamic load rule provider does not return the loadRule in
	 * getComponentLoadRules method.
	 * 
	 */
	@SuppressWarnings("deprecation")
	public static void invokeUpdateCopyFileArea(IWorkspaceConnection workspaceConnection, boolean synchronizeLoad, String fetchDestination,
			boolean deleteDestinationBeforeFetch, String loadMethod, String loadPolicy, String componentLoadConfig, String componentLoadRuleUuids,
			ILoadRule2 loadRule, boolean preserveFileTimestamps, boolean expandKeywords, IConsoleOutput consoleOutput, ITeamRepository repository,
			IProgressMonitor monitor) throws Exception {

		if (VersionCheckerUtil.isPre701BuildToolkit()) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Invoking SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, "
						+ "ILoadRule2, boolean, IProgressMonitor) in pre-701 build toolkit.");
			}
			SourceControlUtility.updateFileCopyArea(workspaceConnection, // workspaceConnection
					fetchDestination, // destinationPath
					synchronizeLoad, // synchronizeLoad
					loadRule, // loadRule
					false, // preserveFileTimestamps
					monitor); // monitor
		} else {
			invoke701UpdateCopyFileArea(workspaceConnection, // workspaceConnection
					synchronizeLoad, // synchronizeLoad
					fetchDestination, // fetchDestination
					deleteDestinationBeforeFetch, // deleteDestinationBeforeFetch
					loadMethod, // loadMethod
					loadPolicy, // loadPolicy
					false, // createFoldersForComponents
					componentLoadConfig, // componentLoadConfig
					false, // includeComponents
					null, // components
					componentLoadRuleUuids, // componentLoadRuleUuids
					null, // componentLoadRules
					loadRule, // loadRule
					preserveFileTimestamps, // preserveFileTimestamps
					expandKeywords, // expandKeywords
					consoleOutput, // consoleOutput
					repository, // repository
					monitor); // monitor
		}

	}

	/**
	 * When using pre-701 buildtoolkit, this method invokes
	 * SourceControlUtility.updateFileCopyArea implementation that supports
	 * loading a workspace when load rules and excludeComponents, if any, are
	 * specified together.
	 * 
	 * <pre>
	 * This will be invoked in one of the following scenarios:
	 * 1. When no loadPolicy is specified
	 * 2. When loadPolicy is specified and it is set to useComponentLoadConfig
	 * 3. When loadPolicy is set to useLoadRules and a load rule file is not specified
	 * 4. When loadPolicy is set to useDynamicLoadRules and the dynamic load rule provider returns loadRules in getComponentLoadRules method.
	 * </pre>
	 * 
	 */
	@SuppressWarnings("deprecation")
	public static void invokeUpdateCopyFileArea(IWorkspaceConnection workspaceConnection, boolean synchronizeLoad, String fetchDestination,
			boolean deleteDestinationBeforeFetch, String loadMethod, String loadPolicy, boolean createFoldersForComponents,
			String componentLoadConfig, boolean includeComponents, Collection<IComponentHandle> components, String componentLoadRuleUuids,
			Collection<ILoadRule2> componentLoadRules, boolean preserveFileTimestamps, boolean expandKeywords, IConsoleOutput consoleOutput,
			ITeamRepository repository, IProgressMonitor monitor) throws Exception {

		if (VersionCheckerUtil.isPre701BuildToolkit()) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("Invoking com.ibm.team.build.internal.scm.SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, "
						+ "String, boolean, Collection<IComponentHandle>, boolean, Collection<ILoadRule2>, boolean, IProgressMonitor) "
						+ "in pre-701 build toolkit.");
			}
			SourceControlUtility.updateFileCopyArea(workspaceConnection, // workspaceConnection
					fetchDestination, // destinationPath
					includeComponents, // includedComponents
					components, // components
					synchronizeLoad, // synchronizeLoad
					componentLoadRules, // loadRules
					createFoldersForComponents, // createFoldersForComponents
					monitor); // monitor
		} else {
			invoke701UpdateCopyFileArea(workspaceConnection, // workspaceConnection
					synchronizeLoad, // synchronizeLoad
					fetchDestination, // fetchDestination
					deleteDestinationBeforeFetch, // deleteDestinationBeforeFetch
					loadMethod, // loadMethod
					loadPolicy, // loadPolicy
					createFoldersForComponents, // createFoldersForComponents
					componentLoadConfig, // componentLoadConfig
					includeComponents, // includeComponents
					components, // components
					componentLoadRuleUuids, // componentLoadRuleUuids
					componentLoadRules, // componentLoadRules
					null, // loadRule
					preserveFileTimestamps, // preserveFileTimestamps
					expandKeywords, // expandKeywords
					consoleOutput, // consoleOutput
					repository, // repository
					monitor); // monitor
		}

	}

	private static void invoke701UpdateCopyFileArea(IWorkspaceConnection workspaceConnection, boolean synchronizeLoad, String fetchDestination,
			boolean deleteDestinationBeforeFetch, String loadMethod, String loadPolicy, boolean createFoldersForComponents,
			String componentLoadConfig, boolean includeComponents, Collection<IComponentHandle> components, String componentLoadRuleUuids,
			Collection<ILoadRule2> componentLoadRules, ILoadRule2 loadRule, boolean preserveFileTimestamps, boolean expandKeywords,
			IConsoleOutput consoleOutput, ITeamRepository repository, IProgressMonitor monitor) throws Exception {
		// if we are not dealing with dynamic load rules let the load code
		// determine the load rules
		if (!Constants.LOAD_POLICY_USE_DYNAMIC_LOAD_RULES.equals(loadPolicy)) {
			componentLoadRules = null;
			loadRule = null;
		}
		// In Jenkins we have supported null component load config values by
		// interpreting it as load all components
		// with 701 build toolkit specifying null values results in error,
		// workaround by setting the componentLoadConfig
		if (Constants.LOAD_POLICY_USE_COMPONENT_LOAD_CONFIG.equals(loadPolicy)
				&& (componentLoadConfig == null || componentLoadConfig.trim()
						.isEmpty())) {
			componentLoadConfig = Constants.COMPONENT_LOAD_CONFIG_LOAD_ALL_COMPONENTS;
		}
		// Invoke using reflection
		Class<?> buildScmLoadOptionsFactoryClass = Class
				.forName("com.ibm.team.build.internal.scm.BuildScmLoadOptions$JenkinsBuildScmLoadOptionsFactory");
		Method buildScmLoadOptionsFactoryGetInstanceMethod = buildScmLoadOptionsFactoryClass
				.getMethod("getInstance");
		Method getBuildScmLoadOptionsMethod = buildScmLoadOptionsFactoryClass
				.getMethod("getBuildScmLoadOptions", IWorkspaceConnection.class, // workspaceConnection
						boolean.class, // synchronizeLoad
						String.class, // fetchDestination
						boolean.class, // deleteDestinationBeforeFetch
						String.class, // loadMethod
						String.class, // loadPolicy
						boolean.class, // createFoldersForComponents
						String.class, // componentLoadConfig
						boolean.class, // includeComponents
						String.class, // componentUuids
						String.class, // componentLoadRuleUuids
						Collection.class, // dynamicLoadRules
						ILoadRule2.class, // dynamicLoadRule
						boolean.class, // preserveFileTimestamps
						boolean.class, // expandKeywords
						ITeamRepository.class, // repository
						IProgressMonitor.class); // monitor
		Object buildScmLoadOptionsFactoryInstance = buildScmLoadOptionsFactoryGetInstanceMethod
				.invoke(null);
		Object buildScmLoadOptionsInstance = getBuildScmLoadOptionsMethod
				.invoke(buildScmLoadOptionsFactoryInstance,
						workspaceConnection, // workspaceConnection
						synchronizeLoad, // synchronizeLoad
						fetchDestination, // fetchDestination
						deleteDestinationBeforeFetch, // deleteDestinationBeforeFetch
						loadMethod, // loadMethod
						loadPolicy, // loadPolicy
						createFoldersForComponents, // createFoldersForComponents
						componentLoadConfig,// componentLoadConfig
						includeComponents,// includeComponents
						components != null ? new LoadComponents(components)
								.getBuildProperty() : null, // componentUuids
						componentLoadRuleUuids, // componentLoadRuleUuids
						componentLoadRules, // dynamicLoadRules
						loadRule, // dynamicLoadRule
						preserveFileTimestamps, // preserveFileTimestamps
						expandKeywords, // expandKeywords
						repository, // repository
						monitor); // monitor

		Class<?> buildLogListenerInterface = Class.forName("com.ibm.team.build.internal.IBuildLogListener");

		Object buildLogListenerProxy = Proxy.newProxyInstance(buildLogListenerInterface.getClassLoader(), new Class[] { buildLogListenerInterface },
				new JenkinsBuildLogListener(consoleOutput));

		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Invoking com.ibm.team.build.internal.scm.SourceControlUtility.updateFileCopyArea(BuildScmLoadOptions, IProgressMonitor) "
					+ "in 701 build toolkit.");
		}

		Method updateFileCopyAreaMethod = SourceControlUtility.class.getMethod("updateFileCopyArea",
				Class.forName("com.ibm.team.build.internal.scm.BuildScmLoadOptions"), buildLogListenerInterface, IProgressMonitor.class);
		updateFileCopyAreaMethod.invoke(null, buildScmLoadOptionsInstance, buildLogListenerProxy, monitor);
	}
	
	private static class JenkinsBuildLogListener implements InvocationHandler {
		IConsoleOutput consoleOutput = null;
		
		public JenkinsBuildLogListener(IConsoleOutput consoleOutput) {
			this.consoleOutput = consoleOutput;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getName().equals("log") && args.length == 1 && args[0] instanceof String) {
				consoleOutput.log((String)args[0]);
			}
			return null;
		}
		
	}

}
