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

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility=999)
public class RTCChangeLogSet extends ChangeLogSet<RTCChangeLogSetEntry> {

    @ExportedBean(defaultVisibility=999)
    public static class ComponentDescriptor implements Comparable<ComponentDescriptor> {

		private String itemId;
		private String name;

		public ComponentDescriptor(String componentItemId, String componentName) {
			this.itemId = componentItemId;
			this.name = componentName;
		}
		
		@Exported
		public String getItemId() {
			return itemId;
		}

		@Exported
		public String getName() {
			return name;
		}

		public int compareTo(ComponentDescriptor o) {
			// sort by name
			if (name == null) {
				return -1;
			} else {
				return name.compareTo(o.name);
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime
					* result
					+ ((itemId == null) ? 0 : itemId
							.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ComponentDescriptor other = (ComponentDescriptor) obj;
			if (itemId == null) {
				if (other.itemId != null)
					return false;
			} else if (!itemId.equals(other.itemId))
				return false;
			return true;
		}
	}
	
	private String baselineSetItemId;
	private String baselineSetName;
	private String workspaceItemId;
	private List<RTCChangeLogComponentEntry> componentChanges;
	private Map<String, List<RTCChangeLogChangeSetEntry>> changesAccepted;
	private Map<String, List<RTCChangeLogChangeSetEntry>> changesDiscarded;
	private int changesAcceptedCount;
	private int changesDiscardedCount;
	private TreeSet<ComponentDescriptor> affectedComponents;
	private transient boolean componentChangesSorted;
	private transient List<RTCChangeLogSetEntry> allChanges;
	
	public RTCChangeLogSet(AbstractBuild<?, ?> build) {
		super(build);
		this.componentChanges = new ArrayList<RTCChangeLogComponentEntry>(0);
		this.affectedComponents = new TreeSet();
		this.changesAccepted = new HashMap<String, List<RTCChangeLogChangeSetEntry>>();
		this.changesDiscarded = new HashMap<String, List<RTCChangeLogChangeSetEntry>>();
		this.changesAcceptedCount = 0;
		this.changesDiscardedCount = 0;
	}

	public Iterator<RTCChangeLogSetEntry> iterator() {
		List<RTCChangeLogSetEntry> changes = getAllChanges();
		return changes.iterator();
	}

	@Exported
	@Override
	public boolean isEmptySet() {
		return changesAccepted.isEmpty() && changesDiscarded.isEmpty() && componentChanges.isEmpty();
	}
	
	private List<RTCChangeLogSetEntry> getAllChanges() {
		if (allChanges == null) {
			ArrayList<RTCChangeLogSetEntry> changes = new ArrayList<RTCChangeLogSetEntry>();
			for (RTCChangeLogComponentEntry componentChange : componentChanges) {
				changes.add(componentChange);
			}
			for (List<RTCChangeLogChangeSetEntry> changeSets : changesAccepted.values()) {
				changes.addAll(changeSets);
			}
			for (List<RTCChangeLogChangeSetEntry> changeSets : changesDiscarded.values()) {
				changes.addAll(changeSets);
			}
			allChanges = changes;
		}
		return allChanges;
	}
	
	public void add(RTCChangeLogComponentEntry entry) {
		entry.setParent(this);
		componentChanges.add(entry);
	}

	public void add(RTCChangeLogChangeSetEntry entry) {
		entry.setParent(this);
		List<RTCChangeLogChangeSetEntry> changes;
		if (entry.isAccept()) {
			changesAcceptedCount++;
			changes = changesAccepted.get(entry.getComponentItemId());
			if (changes == null) {
				changes = new ArrayList<RTCChangeLogChangeSetEntry>();
				changesAccepted.put(entry.getComponentItemId(), changes);
			}
		} else {
			changesDiscardedCount++;
			changes = changesDiscarded.get(entry.getComponentItemId());
			if (changes == null) {
				changes = new ArrayList<RTCChangeLogChangeSetEntry>();
				changesDiscarded.put(entry.getComponentItemId(), changes);
			}
		}
		changes.add(entry);
		
		ComponentDescriptor componentDescriptor = new ComponentDescriptor(entry.getComponentItemId(), entry.getComponentName());
		affectedComponents.add(componentDescriptor);
	}
	
	public void setBaselineSetItemId(String itemId) {
		this.baselineSetItemId = itemId;
	}
	
	public String getBaselineSetItemId() {
		return baselineSetItemId;
	}
	
	public void setBaselineSetName(String name) {
		this.baselineSetName = name;
	}

	@Exported
	public String getBaselineSetName() {
		return baselineSetName;
	}

	public void setWorkspaceItemId(String itemId) {
		this.workspaceItemId = itemId;
	}

	public String getWorkspaceItemId() {
		return workspaceItemId;
	}

	@Exported
	public List<RTCChangeLogComponentEntry> getComponentChanges() {
		synchronized (componentChanges) {
			if (!componentChangesSorted) {
				Collections.sort(componentChanges);
				componentChangesSorted = true;
			}
		}
		return componentChanges;
	}
	
	@Exported
	public Set<ComponentDescriptor> getAffectedComponents() {
		return affectedComponents;
	}
	
	@Exported
	public List<RTCChangeLogChangeSetEntry> getChangeSetsAccepted(String componentItemId) {
		List<RTCChangeLogChangeSetEntry> result = changesAccepted.get(componentItemId);
		if (result == null) {
			return Collections.EMPTY_LIST;
		}
		return result;
	}
	
	@Exported
	public List<RTCChangeLogChangeSetEntry> getChangeSetsDiscarded(String componentItemId) {
		List<RTCChangeLogChangeSetEntry> result = changesDiscarded.get(componentItemId);
		if (result == null) {
			return Collections.EMPTY_LIST;
		}
		return result;
	}
	
	@Exported
	public int getComponentChangeCount() {
		return componentChanges.size();
	}
	
	@Exported
	public int getChangeSetsAcceptedCount() {
		return changesAcceptedCount;
	}
	
	@Exported
	public int getChangeSetsDiscardedCount() {
		return changesDiscardedCount;
	}
}