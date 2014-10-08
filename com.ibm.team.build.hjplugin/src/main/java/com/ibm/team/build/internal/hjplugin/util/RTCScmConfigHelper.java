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

import hudson.model.AbstractProject;
import hudson.scm.SCM;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ibm.team.build.internal.hjplugin.RTCBuildResultAction;
import com.ibm.team.build.internal.hjplugin.RTCScm;

public class RTCScmConfigHelper {

    private static final String MULTI_SCM = "org.jenkinsci.plugins.multiplescms.MultiSCM";
	private static final String SLASH = "/"; //$NON-NLS-1$

    /**
     * If the project is using the MultiScm plugin as its configured SCM provider,
     * find the RTC Scm configuration(s) for the plugin.
     * @param project The project whose RTCScms are to be located
     * @param projectScms set of RTC Scms updated with the RTC SCMs found
     */
	private static void resolveMultiScmIfConfigured(AbstractProject<?, ?> project, Set<RTCScm> projectScms) {
        SCM projectScm = project.getScm();
        // Use reflection to determine if this is an instance of
        // org.jenkinsci.plugins.multiplescms.MultiSCM and see if RTCScm is being used
        // If this doesn't match because we are dealing with a subclass, then we need to dig
        // deeper at the classes. Not doing it now because it is not necessary and would add
        // extra overhead on all deletes of builds/projects when neither RTC nor the Multi SCM
        // plugin are configured.
        if (MULTI_SCM.equals(projectScm.getClass().getCanonicalName())) {
        	getRTCScm(projectScm, projectScms);
        }
    }

	/**
	 * Find the RTCScm configuration that best matches the one is appropriate
	 * for the server referenced by the build result action.
	 * 
	 * Its possible that the project (job) has more than 1 RTC Scm configured for it.
	 * In that case try to match based on server uri. Otherwise assume that the current
	 * SCM configuration is appropriate for the build result.
	 *
	 * The action does have an SCM associated with it, but it is transient. So it could be
	 * null by now. But even if it isn't null, the credential info could be stale, so
	 * look for the configuration on the project now. It is possible to return null
	 * (using some other multi-scm plugin that we don't know about, changed the configuration
	 * of the project since the build was run). 
	 * @param rtcScmConfigs The current RTC SCM configuration(s) for the project/job that was built. 
	 * @param buildResultAction Hint from the build created by the project. It hints at
	 * the RTC Scm configuration desired if more there is more than 1.
	 * @return The RTC Scm configuration of the project if found. <code>null</code> otherwise.
	 */
	public static RTCScm findRTCScm(Set<RTCScm> rtcScmConfigs,
			RTCBuildResultAction buildResultAction) {
		
		if (rtcScmConfigs.size() == 1) {
			// simple case - the build result should of been created by this SCM
			return rtcScmConfigs.iterator().next();

		} else if (rtcScmConfigs.size() > 0) {
			// go through the scm configurations and see which is the best match for the build result
			// Actions may add a trailing slash so normalize so both sides of the comparison have it.
			String serverURI = buildResultAction.getServerURI();
			if (serverURI != null) {
				if (!serverURI.endsWith(SLASH)) {
					serverURI = serverURI + SLASH;
				}
				for (RTCScm projectScm : rtcScmConfigs) {
					String scmServerUri = projectScm.getServerURI();
					if (scmServerUri != null && !scmServerUri.endsWith(SLASH)) {
						scmServerUri = scmServerUri + SLASH;
					}
					if (serverURI.equals(scmServerUri)) {
						return projectScm;
					}
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	/**
	 * Get from the Multi-SCM plugin SCM the configured RTCScms if any
	 * @param multiScm The Multi-SCM plugin configured SCM provider
	 * @param projectScms The set of RTCScm(s) found. Updated.
	 */
	private static void getRTCScm(SCM multiScm,
			Set<RTCScm> projectScms) {
		try {
			// Yes we are implementation aware.
			Method m = multiScm.getClass().getMethod("getConfiguredSCMs", (Class[]) null); // $NON-NLS-N$
			Object result = m.invoke(multiScm, (Object[]) null);
			if (result instanceof List) {
				for (Object configuredScm : (List<Object>) result) {
					if (configuredScm instanceof RTCScm) {
						projectScms.add((RTCScm) configuredScm);
					}
				}
			}
		} catch (NoSuchMethodException e) {
			// not the plugin we expected
		} catch (IllegalArgumentException e) {
			// not the plugin we expected
		} catch (IllegalAccessException e) {
			// not the plugin we expected
		} catch (InvocationTargetException e) {
			// not the plugin we expected
		}
	}

	/**
	 * Find the RTCScm config instance(s) the project is currently configured with.
	 * If this project is using the multi-scm, then there could be >1 RTCScm configured.
	 * @param project The project to find the RTCScm configs currently in use
	 * @return The RTCScm configs for the project. May be empty, never <code>null</code>
	 */
	public static Set<RTCScm> getCurrentConfigs(AbstractProject<?, ?> project) {
		Set<RTCScm> projectScms = new HashSet<RTCScm>();

		if (project != null) {
			SCM scm = project.getScm();
			if (scm instanceof RTCScm) {
				projectScms.add((RTCScm) scm);
			} else if (scm != null) {
				// see if this is a multi-scm project with 1 or more RTC SCMs.
				resolveMultiScmIfConfigured(project, projectScms);
			}
		}
		return projectScms;
	}
}

