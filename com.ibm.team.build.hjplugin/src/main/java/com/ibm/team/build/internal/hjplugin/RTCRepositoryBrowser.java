/*******************************************************************************
 * Copyright (c) 2013, 2015 IBM Corporation and others.
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
import hudson.Util;
import hudson.model.Descriptor;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.ChangeDesc;

public class RTCRepositoryBrowser extends RepositoryBrowser<RTCChangeLogSetEntry> {
    private static final Logger LOGGER = Logger.getLogger(RTCRepositoryBrowser.class.getName());

	private static final long serialVersionUID = 1L;

	private static final String SLASH = "/"; //$NON-NLS-1$
	
	private String url;

	@DataBoundConstructor
	public RTCRepositoryBrowser(String serverURI) {
		super();
		LOGGER.finest("RTCRepositoryBrowser.constructor : Begin"); //$NON-NLS$1
        this.url = getServerURI(serverURI);
	}
	
	/**
	 * @return the server uri guaranteed to have a trailing slash or <code>null</code> if none.
	 */
	private String getServerURI(String serverURI) {
        LOGGER.finest("RTCRepositoryBrowser.getServerURI : Begin"); //$NON-NLS$1
		serverURI = Util.fixEmpty(serverURI);
        if (serverURI != null && !serverURI.endsWith(SLASH)) {
        	serverURI = serverURI + SLASH;
        }
        return serverURI;
	}
	
	/**
	 * @return the server uri guaranteed to have a trailing slash or <code>null</code> if none.
	 */
	private String getServerURI() {
		return getServerURI(this.url);
	}
	
	@Override
	public URL getChangeSetLink(RTCChangeLogSetEntry changeSet)
			throws IOException {
        LOGGER.finest("RTCRepositoryBrowser.getChangeSetLink : Begin"); //$NON-NLS$1
		if (changeSet instanceof RTCChangeLogChangeSetEntry) {
	        LOGGER.finer("RTCRepositoryBrowser.getChangeSetLink : changeSet is from RTC"); //$NON-NLS$1
			RTCChangeLogChangeSetEntry changeSetEntry = (RTCChangeLogChangeSetEntry) changeSet;
			String workspaceItemId = changeSetEntry.getWorkspaceItemId();
			String changeSetItemId = changeSetEntry.getChangeSetItemId();
			
			String serverURI = getServerURI();
			if (changeSetItemId != null && serverURI != null) {
				StringBuilder buffer = new StringBuilder(serverURI);
				buffer.append("resource/itemOid/com.ibm.team.scm.ChangeSet/"); //$NON-NLS-1$
				buffer.append(changeSetItemId);
				if (workspaceItemId != null && workspaceItemId.length() > 0) {
					buffer.append("?workspace=").append(workspaceItemId); //$NON-NLS-1$
				}
				return new URL(buffer.toString());
			}
		}
		return null;
	}
	
	public URL getVersionableStateLink(RTCChangeLogChangeSetEntry changeSet, ChangeDesc changeDesc) throws MalformedURLException {
        LOGGER.finest("RTCRepositoryBrowser.getVersionableStateLink : Begin"); //$NON-NLS$1
		String workspaceItemId = changeSet.getWorkspaceItemId();
		String componentItemId = changeSet.getComponentItemId();
		String versionableItemId = changeDesc.getItemId();
		String versionableStateId = changeDesc.getStateId();
		String serverURI = getServerURI();
		if (serverURI != null && workspaceItemId != null && componentItemId != null 
				&& versionableItemId != null && versionableStateId != null
				&& !changeDesc.isFolderChange()) {
			StringBuilder buffer = new StringBuilder(serverURI);
			buffer.append("resource/itemOid/com.ibm.team.scm.Versionable/"); //$NON-NLS-1$
			buffer.append(versionableItemId).append("/").append(versionableStateId); //$NON-NLS-1$
			buffer.append("?workspace=").append(workspaceItemId); //$NON-NLS-1$
			buffer.append("&component=").append(componentItemId); //$NON-NLS-1$
			return new URL(buffer.toString());
		}
		return null;
	}
	
	public URL getWorkItemLink(RTCChangeLogChangeSetEntry.WorkItemDesc workItem) throws MalformedURLException {
        LOGGER.finest("RTCRepositoryBrowser.getWorkItemLink : Begin"); //$NON-NLS$1
		String serverURI = getServerURI();
		if (serverURI != null && workItem != null && workItem.getNumber() != null && workItem.getNumber().length() > 0) {
	        LOGGER.finer("RTCRepositoryBrowser.getWorkItemLink : Creating work item link"); //$NON-NLS$1
			StringBuilder buffer = new StringBuilder(serverURI);
			buffer.append("resource/itemName/com.ibm.team.workitem.WorkItem/"); //$NON-NLS-1$
			buffer.append(workItem.getNumber());
			return new URL(buffer.toString());
		}
		return null;
	}

	public URL getBaselineSetLink(RTCChangeLogSet changeLog) throws MalformedURLException {
        LOGGER.finest("RTCRepositoryBrowser.getBaselineSetLink : Begin"); //$NON-NLS$1
		String serverURI = getServerURI();
		if (serverURI != null && changeLog != null && changeLog.getBaselineSetItemId() != null && changeLog.getBaselineSetItemId().length() > 0) {
	        LOGGER.finer("RTCRepositoryBrowser.getBaselineSetLink : Creating baselineLink"); //$NON-NLS$1
			StringBuilder buffer = new StringBuilder(serverURI);
			buffer.append("resource/itemOid/com.ibm.team.scm.BaselineSet/"); //$NON-NLS-1$
			buffer.append(changeLog.getBaselineSetItemId());
			return new URL(buffer.toString());
		}
		return null;
	}
	
    @Extension
    public static final class DescriptorImpl extends Descriptor<RepositoryBrowser<?>> {

        public DescriptorImpl() {
            super(RTCRepositoryBrowser.class);
        }

        @Override
        public String getDisplayName() {
            return Messages.RTCRepositoryBrowser_display_name();
        }
    }
}
