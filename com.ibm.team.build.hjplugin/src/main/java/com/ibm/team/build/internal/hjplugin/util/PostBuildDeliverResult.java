/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.util;

public class PostBuildDeliverResult {
	
	private RTCBuildStatus buildStatus;
	
	private boolean delivered;
	
	private String participantSummary;
	
	private String participantLog;

	private String contentURI;

	public PostBuildDeliverResult(String rtcBuildStatus, boolean delivered, 
			String participantSummary, String participantLog, String contentURI) {
		this.buildStatus = RTCBuildStatus.valueOf(rtcBuildStatus);
		this.delivered = delivered;
		this.participantSummary = participantSummary;
		this.participantLog = participantLog;
		this.contentURI = contentURI;
	}
	
	public RTCBuildStatus getBuildStatus() {
		return buildStatus;
	}
	
	public boolean isDelivered() {
		return delivered;
	}
	
	public String getParticipantSummary() {
		return participantSummary;
	}
	
	public String getParticipantLog() {
		return participantLog;
	}
	
	public String getContentURI() {
		return contentURI;
	}
}
