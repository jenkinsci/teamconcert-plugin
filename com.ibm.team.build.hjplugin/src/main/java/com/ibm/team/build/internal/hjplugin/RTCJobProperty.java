/*******************************************************************************
 * Copyright © 2026 IBM Corporation and others.
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
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
/**
 * Job property to track the last non-personal build for efficient polling.
 */
public class RTCJobProperty extends JobProperty<Job<?, ?>> {

    private int lastNonPersonalBuildNumber = -1;
    private String lastNonPersonalBuildUUID;

    public RTCJobProperty() {
    }

    public RTCJobProperty(int buildNumber, String buildUUID) {
        this.lastNonPersonalBuildNumber = buildNumber;
        this.lastNonPersonalBuildUUID = buildUUID;
    }

    public int getLastNonPersonalBuildNumber() {
        return lastNonPersonalBuildNumber;
    }

    public void setLastNonPersonalBuildNumber(int number) {
        this.lastNonPersonalBuildNumber = number;
    }

    public String getLastNonPersonalBuildUUID() {
        return lastNonPersonalBuildUUID;
    }

    public void setLastNonPersonalBuildUUID(String uuid) {
        this.lastNonPersonalBuildUUID = uuid;
    }

    @Extension
    public static class DescriptorImpl extends JobPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "RTC Last Non-Personal Build Tracker";
        }
    }
}
