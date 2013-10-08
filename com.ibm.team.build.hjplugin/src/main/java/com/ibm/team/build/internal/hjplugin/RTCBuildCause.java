/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2013. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  
 * Use, duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp. 
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin;

import org.kohsuke.stapler.export.Exported;

import hudson.model.Cause;

/**
 * This is a build action that is persisted with the build describing
 * how it was started if it was initiated from within RTC. It complements
 * the one that H/J will create for the build.
 * If you change it, be sure that old builds can still be opened.
 */
public class RTCBuildCause extends Cause {

	private boolean isScheduled;
	private boolean isPersonalBuild;
	private String requestor;

	public RTCBuildCause(BuildResultInfo buildResultInfo) {
		this.isScheduled = buildResultInfo.isScheduled();
		this.isPersonalBuild = buildResultInfo.isPersonalBuild();
		this.requestor = buildResultInfo.getRequestor();
	}

	@Override
	@Exported(visibility = 3)
	public String getShortDescription() {
		if (isScheduled) {
			return Messages.RTCBuildCause_scheduled();
		} else if (isPersonalBuild && requestor != null) {
			return Messages.RTCBuildCause_personal(requestor);
		} else if (isPersonalBuild) {
			return Messages.RTCBuildCause_unknown_personal();
		} else if (requestor != null) {
			return Messages.RTCBuildCause_requested(requestor);
		} else {
			return Messages.RTCBuildCause_unknown();
		}
	}
}
