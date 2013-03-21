/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.ChangeDesc;

public class RTCRepositoryBrowser extends RepositoryBrowser<RTCChangeLogSetEntry> {

	private static final long serialVersionUID = 1L;

	private static final String SLASH = "/"; //$NON-NLS-1$
	
	private String url;

	@DataBoundConstructor
	public RTCRepositoryBrowser(String serverURI) {
		super();
        this.url = Util.fixEmpty(serverURI);
        if (this.url != null && !this.url.endsWith(SLASH)) {
        	this.url = this.url + SLASH;
        }
	}
	
	@Exported
	@Override
	public URL getChangeSetLink(RTCChangeLogSetEntry changeSet)
			throws IOException {
		if (changeSet instanceof RTCChangeLogChangeSetEntry) {
			RTCChangeLogChangeSetEntry changeSetEntry = (RTCChangeLogChangeSetEntry) changeSet;
			String workspaceItemId = changeSetEntry.getWorkspaceItemId();
			String changeSetItemId = changeSetEntry.getChangeSetItemId();
			
			if (changeSetItemId != null && url != null) {
				StringBuilder buffer = new StringBuilder(url);
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
		String workspaceItemId = changeSet.getWorkspaceItemId();
		String componentItemId = changeSet.getComponentItemId();
		String versionableItemId = changeDesc.getItemId();
		String versionableStateId = changeDesc.getStateId();
		if (url != null && workspaceItemId != null && componentItemId != null 
				&& versionableItemId != null && versionableStateId != null
				&& !changeDesc.isFolderChange()) {
			StringBuilder buffer = new StringBuilder(url);
			buffer.append("resource/itemOid/com.ibm.team.scm.Versionable/"); //$NON-NLS-1$
			buffer.append(versionableItemId).append("/").append(versionableStateId); //$NON-NLS-1$
			buffer.append("?workspace=").append(workspaceItemId); //$NON-NLS-1$
			buffer.append("&component=").append(componentItemId); //$NON-NLS-1$
			return new URL(buffer.toString());
		}
		return null;
	}
	
	@Exported
	public URL getWorkItemLink(RTCChangeLogChangeSetEntry.WorkItemDesc workItem) throws MalformedURLException {
		if (url != null && workItem != null && workItem.getNumber() != null && workItem.getNumber().length() > 0) {
			StringBuilder buffer = new StringBuilder(url);
			buffer.append("resource/itemName/com.ibm.team.workitem.WorkItem/"); //$NON-NLS-1$
			buffer.append(workItem.getNumber());
			return new URL(buffer.toString());
		}
		return null;
	}

	public URL getBaselineSetLink(RTCChangeLogSet changeLog) throws MalformedURLException {
		if (url != null && changeLog != null && changeLog.getBaselineSetItemId() != null && changeLog.getBaselineSetItemId().length() > 0) {
			StringBuilder buffer = new StringBuilder(url);
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
