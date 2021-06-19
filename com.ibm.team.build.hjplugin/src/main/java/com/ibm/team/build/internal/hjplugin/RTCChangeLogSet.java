/*******************************************************************************
 * Copyright (c) 2013, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.model.Run;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility=999)
public class RTCChangeLogSet extends ChangeLogSet<RTCChangeLogSetEntry> {

	private static final Logger LOGGER = Logger.getLogger(RTCChangeLogSet.class.getName());

    @ExportedBean(defaultVisibility=999)
    public static class ComponentDescriptor implements Comparable<ComponentDescriptor> {

		private String itemId;
		private String name;

		public ComponentDescriptor(String componentItemId, String componentName) {
			this.itemId = componentItemId;
			this.name = componentName;
		}
		
		public String getItemId() {
			return itemId;
		}

		public String getName() {
			return name;
		}

		public int compareTo(ComponentDescriptor o) {
			// sort by name, but also be consistent with equals 
			if (name != null && o.name != null) {
				int result = name.compareTo(o.name);
				if (result == 0) {
					return itemId.compareTo(o.itemId);
				}
				return result;
			} else if (name == null && o.name == null) {
				return itemId.compareTo(o.itemId);
			} else if (name == null) {
				return -1;
			} else {
				return 1;
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
	private String previousBaselineSetItemId;
	private String previousBaselineSetName;
	private String workspaceItemId;
	private String workspaceName;
	private String buildDefinitionName;
	private String buildDefinitionItemId;
	private String streamName;
	private String streamItemId;
	private boolean isPersonalBuild;
	private String previousBuildUrl;
	private List<RTCChangeLogComponentEntry> componentChanges;
	private Map<String, List<RTCChangeLogChangeSetEntry>> changesAccepted;
	private Map<String, List<RTCChangeLogChangeSetEntry>> changesDiscarded;
	private int changesAcceptedCount;
	private int changesDiscardedCount;
	private TreeSet<ComponentDescriptor> affectedComponents;
	private transient boolean componentChangesSorted;
	private transient List<RTCChangeLogSetEntry> allChanges;
	
	public RTCChangeLogSet(Run<?, ?> build, RepositoryBrowser<?> browser) {
		super(build, browser);
		LOGGER.finest("RTCChangeLogset construtor : Begin");
		this.componentChanges = new ArrayList<RTCChangeLogComponentEntry>(0);
		this.affectedComponents = new TreeSet<ComponentDescriptor>();
		this.changesAccepted = new HashMap<String, List<RTCChangeLogChangeSetEntry>>();
		this.changesDiscarded = new HashMap<String, List<RTCChangeLogChangeSetEntry>>();
		this.changesAcceptedCount = 0;
		this.changesDiscardedCount = 0;
	}

	public Iterator<RTCChangeLogSetEntry> iterator() {
		LOGGER.finest("RTCChangeLogset iterator : Begin");
		List<RTCChangeLogSetEntry> changes = getAllChanges();
		LOGGER.finest("RTCChangeLogset iterator End");
		return changes.iterator();
	}

	@Override
	public String getKind() {
		LOGGER.finest("RTCChangeLogSet getKind : Begin");
		return "RTC";
	}

	@Override
	public boolean isEmptySet() {
		LOGGER.finest("RTCChangeLogSet isEmptySet : Begin");
		return changesAccepted.isEmpty() && changesDiscarded.isEmpty() && componentChanges.isEmpty();
	}
	
	private List<RTCChangeLogSetEntry> getAllChanges() {
		LOGGER.finest("RTCChangeLogset getAllChanges : Begin");
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
		LOGGER.finest("RTCChangeLogset add  RTCChangeLogComponentEntry: Begin");
		entry.setParent(this);
		componentChanges.add(entry);
	}

	public void add(RTCChangeLogChangeSetEntry entry) {
		LOGGER.finest("RTCChangeLogset add  RTCChangeLogChangeSetEntry: Begin");
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
	
	public void setIsPersonalBuild(boolean isPersonal) {
		LOGGER.finest("RTCChangeLogset setIsPersonalBuild: Begin");
		this.isPersonalBuild = isPersonal;
	}
	
	public boolean isPersonalBuild() {
		LOGGER.finest("RTCChangeLogset isPersonalBuild: Begin");
		return isPersonalBuild;
	}
	
	public String getPreviousBuildUrl() {
		LOGGER.finest("RTCChangeLogset getPreviousBuildUrl: Begin");
		return previousBuildUrl;
	}
	
	public void setPreviousBuildUrl(String previousBuildUrl) {
		LOGGER.finest("RTCChangeLogset setPreviousBuildUrl: Begin");
		this.previousBuildUrl = previousBuildUrl;
	}
	
	public void setBaselineSetItemId(String itemId) {
		LOGGER.finest("RTCChangeLogset setBaselineSetItemId: Begin");
		this.baselineSetItemId = itemId;
	}
	
	public String getBaselineSetItemId() {
		LOGGER.finest("RTCChangeLogset getBaselineSetItemId: Begin");
		return baselineSetItemId;
	}
	
	public void setBaselineSetName(String name) {
		LOGGER.finest("RTCChangeLogset setBaselineSetName: Begin");
		this.baselineSetName = name;
	}

	public String getBaselineSetName() {
		LOGGER.finest("RTCChangeLogset getBaselineSetName: Begin");
		return baselineSetName;
	}
	
	public void setPreviousBaselineSetItemId(String itemId) {
		LOGGER.finest("RTCChangeLogset setPreviousBaselineSetItemId: Begin");
		this.previousBaselineSetItemId = itemId;
	}
	
	public String getPreviousBaselineSetItemId() {
		LOGGER.finest("RTCChangeLogset getPreviousBaselineSetItemId: Begin");
		return previousBaselineSetItemId;
	}
	
	public void setPreviousBaselineSetName(String name) {
		LOGGER.finest("RTCChangeLogset setPreviousBaselineSetName: Begin");
		this.previousBaselineSetName = name;
	}

	public String getPreviousBaselineSetName() {
		LOGGER.finest("RTCChangeLogset getPreviousBaselineSetName: Begin");
		return previousBaselineSetName;
	}


	public void setWorkspaceItemId(String workspaceItemId) {
		this.workspaceItemId = workspaceItemId;
	}

	public String getWorkspaceItemId() {
		return workspaceItemId;
	}
	
	public void setWorkspaceName(String workspaceName) {
		this.workspaceName = workspaceName;
	}
	
	public String getWorkspaceName() {
		return workspaceName;
	}
	
	public void setBuildDefinitionName(String buildDefinitionName) {
		this.buildDefinitionName = buildDefinitionName;
	}
	
	public String getBuildDefinitionName() {
		return buildDefinitionName;
	}

	public String getBuildDefinitionItemId() {
		return buildDefinitionItemId;
	}

	public void setBuildDefinitionItemId(String buildDefinitionItemId) {
		this.buildDefinitionItemId = buildDefinitionItemId;
	}
	
	public String getStreamName() {
		return streamName;
	}

	public void setStreamName(String streamName) {
		this.streamName = streamName;
	}

	public String getStreamItemId() {
		return streamItemId;
	}

	public void setStreamItemId(String streamItemId) {
		this.streamItemId = streamItemId;
	}

	public List<RTCChangeLogComponentEntry> getComponentChanges() {
		LOGGER.finest("RTCChangeLogset getComponentChanges : Begin");
		synchronized (componentChanges) {
			if (!componentChangesSorted) {
				Collections.sort(componentChanges);
				componentChangesSorted = true;
			}
		}
		return componentChanges;
	}
	
	public Set<ComponentDescriptor> getAffectedComponents() {
		LOGGER.finest("RTCChangeLogset getAffectedComponents : Begin");
		return affectedComponents;
	}
	
	public List<RTCChangeLogChangeSetEntry> getChangeSetsAccepted(String componentItemId) {
		LOGGER.finest("RTCChangeLogset getChangeSetsAccepted : Begin");
		List<RTCChangeLogChangeSetEntry> result = changesAccepted.get(componentItemId);
		if (result == null) {
			return Collections.emptyList();
		}
		return result;
	}

	public List<RTCChangeLogChangeSetEntry> getChangeSetsDiscarded(String componentItemId) {
		LOGGER.finest("RTCChangeLogset getChangeSetsDiscarded : Begin");
		List<RTCChangeLogChangeSetEntry> result = changesDiscarded.get(componentItemId);
		if (result == null) {
			return Collections.emptyList();
		}
		return result;
	}
	
	public int getComponentChangeCount() {
		LOGGER.finest("RTCChangeLogset getComponentChangeCount : Begin");
		return componentChanges.size();
	}
	
	public int getChangeSetsAcceptedCount() {
		LOGGER.finest("RTCChangeLogset getChangeSetsAcceptedCount : Begin");
		return changesAcceptedCount;
	}
	
	public int getChangeSetsDiscardedCount() {
		LOGGER.finest("RTCChangeLogset getChangeSetsDiscardedCount : Begin");
		return changesDiscardedCount;
	}
}