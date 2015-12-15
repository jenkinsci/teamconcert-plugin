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
 * Enumeration that defines the valid states for a team build.
 * A build in the {@link #NOT_STARTED} state can transition to {@link #CANCELED}
 * or {@link #IN_PROGRESS}.
 * A build in the {@link #IN_PROGRESS} state can transition
 * to {@link #INCOMPLETE} or {@link #COMPLETED}.
 * {@link #CANCELED} {@link #INCOMPLETE} and {@link #COMPLETED} are final states.
 */
public enum RTCBuildState {

    /**
     * The build has not been started. Also known as PENDING.
     */
    NOT_STARTED,

    /**
     * The request for the build has been canceled.
     */
    CANCELED,

    /**
     * The build is currently in progress.
     */
    IN_PROGRESS,

    /**
     * The build was not completed normally. Also known as ABANDONED.
     */
    INCOMPLETE,

    /**
     * The build was completed normally (but may have failures).
     */
    COMPLETED;
}
