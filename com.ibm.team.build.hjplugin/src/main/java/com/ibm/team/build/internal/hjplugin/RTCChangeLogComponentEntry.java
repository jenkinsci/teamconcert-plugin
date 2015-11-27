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

import hudson.model.User;
import hudson.scm.EditType;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility=999)
public class RTCChangeLogComponentEntry extends RTCChangeLogSetEntry implements Comparable<RTCChangeLogComponentEntry> {
	
    private static final Logger LOGGER = Logger.getLogger(RTCChangeLogComponentEntry.class.getName());

	private String componentItemId;
	private String componentName;
	private String action;
	
	public void setItemId(String itemId) {
		componentItemId = itemId;
	}

	public String getItemId() {
		return componentItemId;
	}

	public void setName(String name) {
		componentName = name;
	}

	public void setAction(String action) {
		this.action = action;
	}
	
	@Override
	@Exported
	public String getMsg() {
		LOGGER.finest("RTCChangeLogComponentEntry.getMsg : Begin");
		if (action.equalsIgnoreCase("Added")) { //$NON-NLS-1$
			return Messages.RTCChangeLogComponentEntry_added_component(componentName);
		} else if (action.equalsIgnoreCase("Dropped")) { //$NON-NLS-1$
			return Messages.RTCChangeLogComponentEntry_dropped_component(componentName);
		}
		return Messages.RTCChangeLogComponentEntry_unknown_action(action, componentName);
	}

	@Override
	@Exported
	public User getAuthor() {
		LOGGER.finest("RTCChangeLogComponentEntry.getAuthor : Begin");
		// We don't know who added/removed a component from the stream
		return User.getUnknown();
	}

	@Override
	@Exported
	public Collection<String> getAffectedPaths() {
		LOGGER.finest("RTCChangeLogComponentEntry.getAffectedPaths : Begin");
		// There are no affected paths
		return Collections.emptyList();
	}
	
	public String getName() {
		return componentName;
	}
	
	public EditType getActionType() {
		if (action.equalsIgnoreCase("Added")) { //$NON-NLS-1$
			return new EditType(EditType.ADD.getName(), Messages.RTCChangeLogComponentEntry_added_the_component());
		} else if (action.equalsIgnoreCase("Dropped")) { //$NON-NLS-1$
			return new EditType(EditType.DELETE.getName(), Messages.RTCChangeLogComponentEntry_deleted_the_component());
		}
		return EditType.EDIT;
	}

	public int compareTo(RTCChangeLogComponentEntry o) {
		LOGGER.finest("RTCChangeLogComponentEntry.compareTo : Begin");
		if (!action.equalsIgnoreCase(o.action)) {
			return action.equalsIgnoreCase("Added") ? -1 : 1; //$NON-NLS-1$
		}
		// same action, sort by name
		if (componentName == null) {
			return -1;
		} else if (o.componentName == null) {
			return 1;
		}
		return componentName.compareTo(o.componentName);
	}

	@Override
	public int hashCode() {
		LOGGER.finest("RTCChangeLogComponentEntry.hashCode : Begin");
		final int prime = 31;
		int result = 1;
		result = prime * result + ((action == null) ? 0 : action.hashCode());
		result = prime * result
				+ ((componentItemId == null) ? 0 : componentItemId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		LOGGER.finest("RTCChangeLogComponentEntry.equals : Begin");
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RTCChangeLogComponentEntry other = (RTCChangeLogComponentEntry) obj;
		if (action == null) {
			if (other.action != null)
				return false;
		} else if (!action.equalsIgnoreCase(other.action))
			return false;
		if (componentItemId == null) {
			if (other.componentItemId != null)
				return false;
		} else if (!componentItemId.equals(other.componentItemId))
			return false;
		return true;
	}
}
