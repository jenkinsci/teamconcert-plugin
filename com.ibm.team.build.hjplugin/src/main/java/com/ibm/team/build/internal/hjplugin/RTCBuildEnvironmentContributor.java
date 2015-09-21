/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

@Extension
public class RTCBuildEnvironmentContributor extends EnvironmentContributor {
    private static final Logger LOGGER = Logger.getLogger(RTCBuildEnvironmentContributor.class.getName());

    /**
     * Only the {@link Run} variant is overriden because we are trying to mimic {@link RTCBuildResultAction#buildEnvVars}
     */
	@Override
    public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars env, @Nonnull TaskListener listener) throws IOException, InterruptedException {
		LOGGER.finest("RTCBuildEnvironmentContributor.buildEnvironmentFor : Enter"); //$NON-NLS-1$
		List<RTCBuildResultAction> actions = r.getActions(RTCBuildResultAction.class);
		for (RTCBuildResultAction action : actions) {
			Map<String,String> buildProperties = action.getBuildProperties();
			if (buildProperties == null)
				continue;
			try {
				for (Map.Entry<String, String> entry : buildProperties.entrySet()) {
					if (LOGGER.isLoggable(Level.FINEST)) {
						LOGGER.finest("Adding entry Key : " + entry.getKey() + " value : " + entry.getValue());
					}
					env.put(entry.getKey(), entry.getValue());
				}
			}
			catch (Exception exp) {
				LOGGER.finer("Error adding build properties to environment");
			}
		}
		LOGGER.finest("RTCBuildEnvironmentContributor.buildEnvironmentFor : End"); //$NON-NLS-1$
		super.buildEnvironmentFor(r, env, listener);
    }
}