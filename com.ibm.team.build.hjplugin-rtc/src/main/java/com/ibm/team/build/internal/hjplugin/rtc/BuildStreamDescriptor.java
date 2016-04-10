/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

public class BuildStreamDescriptor {
	private String name;
	private String snapshotUUID;
	
	public BuildStreamDescriptor(String name, String snapshotUUID) {
		setName(name);
		setSnapshotUUID(snapshotUUID);
	}
	
	public String getName() {
		return name;
	}
	private void setName(String name) {
		this.name = name;
	}
	public String getSnapshotUUID() {
		return snapshotUUID;
	}
	private void setSnapshotUUID(String snapshotUUID) {
		this.snapshotUUID = snapshotUUID;
	}
}
