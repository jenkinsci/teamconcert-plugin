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

/**
 * Enumeration that defines the valid team build statuses.
 */
public enum RTCBuildStatus {

    /**
     * The build status represents the nominal case.
     */
    OK,

    /**
     * The build status is informational only.
     */
    INFO,

    /**
     * The build status represents the warning case.
     */
    WARNING,

    /**
     * The build status represents the error case.
     */
    ERROR;

    /**
     * Determine if the status is more severe then the specified status.
     * 
     * @param status
     *            The status to compare to.
     * @return <code>true</code> if this status is more severe than the
     *         specified status
     */
    public boolean isMoreSevere(RTCBuildStatus status) {
        return this.compareTo(status) > 0;
    }

}
