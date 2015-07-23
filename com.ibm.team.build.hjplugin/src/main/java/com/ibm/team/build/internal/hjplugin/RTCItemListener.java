/*******************************************************************************
 * Copyright (c) 2014, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.listeners.ItemListener;
import hudson.util.RunList;

import java.util.List;
import java.util.Set;

import com.ibm.team.build.internal.hjplugin.util.RTCBuildResultHelper;
import com.ibm.team.build.internal.hjplugin.util.RTCScmConfigHelper;

@Extension
public class RTCItemListener extends ItemListener {

	@Override
	public void onDeleted(Item item) {
		try {
			if (item instanceof AbstractProject) {
				AbstractProject<?, ?> project = (AbstractProject<?, ?>) item;
				Set<RTCScm> rtcScmConfigs = RTCScmConfigHelper.getCurrentConfigs(project);
				if (!rtcScmConfigs.isEmpty()) {
					// find all the builds for the project and delete any of the build results
					RunList<?> allBuilds = project.getBuilds();
					for (Object build : allBuilds) {
						if (build instanceof AbstractBuild<?, ?>) {
							List<RTCBuildResultAction> rtcBuildResultActions = ((AbstractBuild<?, ?>) build).getActions(RTCBuildResultAction.class);
							RTCBuildResultHelper.deleteRTCBuildResults(rtcBuildResultActions, project, rtcScmConfigs);
						}
					}
				}
			}
		} finally {
			super.onDeleted(item);
		}
	}
}
