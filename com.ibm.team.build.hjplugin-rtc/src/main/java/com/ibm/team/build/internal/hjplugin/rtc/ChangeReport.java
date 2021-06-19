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

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport.ChangeSetReport.ChangeEntry;
import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport.ChangeSetReport.WorkItemEntry;

public class ChangeReport {
	private static final String DROPPED = "Dropped"; //$NON-NLS-1$
	private static final String ADDED = "Added"; //$NON-NLS-1$
	static public final int VERSIONABLE_CHANGES_LIMIT = 256;
    
	public static class ComponentReport {
		private String itemId;
		private String name;
		private boolean added;

		public ComponentReport(boolean added, String itemId, String name) {
			this.added = added;
			this.itemId = itemId;
			this.name = name;
		}
		
		public boolean isAdded() {
			return added;
		}
		
		public String getName() {
			return name;
		}
		
		public String getItemId() {
			return itemId;
		}
	}

	public static class ChangeSetReport {
		private boolean accepted;
		private String itemId;
		private String componentItemId;
		private String componentName;
		private String comment;
		private String owner;
		private Date modifyDate;
		
		private long additionalChanges;
		private List<ChangeEntry> changes;
		private List<WorkItemEntry> workItems;

		public static class ChangeEntry {
			private String name;
			private int kind;
			private String itemType;
			private String itemId;
			private String stateId;
			
			public ChangeEntry(int kind, String name, String itemType, String itemId, String stateId) {
				this.kind = kind;
				this.name = name;
				this.itemType = itemType;
				this.itemId = itemId;
				this.stateId = stateId;
			}

			public String getName() {
				return name;
			}

			public int getKind() {
				return kind;
			}

			public String getItemType() {
				return itemType;
			}

			public String getItemId() {
				return itemId;
			}

			public String getStateId() {
				return stateId;
			}
		}
		
		public static class WorkItemEntry {
			private int number;
			private String summary;
			
			public WorkItemEntry(int number, String summary) {
				this.number = number;
				this.summary = summary;
			}

			public int getNumber() {
				return number;
			}

			public String getSummary() {
				return summary;
			}
			
		}
		
		public ChangeSetReport(boolean accepted, String itemId, String componentItemId, String comment,
				Date modifyDate, long additionalChanges) {
			this.accepted = accepted;
			this.itemId = itemId;
			this.componentItemId = componentItemId;
			this.comment = comment;
			this.modifyDate = modifyDate;
			this.additionalChanges = additionalChanges;
			this.changes = new LinkedList<ChangeReport.ChangeSetReport.ChangeEntry>();
			this.workItems = new LinkedList<ChangeReport.ChangeSetReport.WorkItemEntry>();
		}

		public void setOwner(String ownerUserId) {
			this.owner = ownerUserId;
		}
		
		public void setComponentName(String componentName) {
			this.componentName = componentName;
		}

		public void addChange(ChangeReport.ChangeSetReport.ChangeEntry change) {
			changes.add(change);
		}
		
		public void addWorkItem(ChangeReport.ChangeSetReport.WorkItemEntry workItem) {
			workItems.add(workItem);
		}

		public boolean isAccepted() {
			return accepted;
		}

		public String getItemId() {
			return itemId;
		}

		public String getCompoentItemId() {
			return componentItemId;
		}

		public String getComponentName() {
			return componentName;
		}

		public String getComment() {
			return comment;
		}

		public String getOwner() {
			return owner;
		}

		public Date getModifyDate() {
			return modifyDate;
		}

		public long getAdditionalChanges() {
			return additionalChanges;
		}

		public void setAdditionalChanges(long additionalChanges) {
			this.additionalChanges = additionalChanges;
		}

		public List<ChangeEntry> getChanges() {
			return changes;
		}

		public List<WorkItemEntry> getWorkItems() {
			return workItems;
		}
	}
	
	public static class BaselineSetReport extends ItemReport {
		
		public BaselineSetReport(String itemId, String name) {
			super(itemId, name);
		}
	}
	
	public static class BuildStreamReport extends ItemReport {
		
		public BuildStreamReport(String itemId, String name) {
			super(itemId, name);
		}
		
	}
	
	public static class BuildWorkspaceReport extends ItemReport {
		
		public BuildWorkspaceReport(String itemId, String name) {
			super(itemId, name);
		}
	}
	
	public static class BuildDefinitionReport extends ItemReport {
		
		public BuildDefinitionReport(String itemId, String name) {
			super (itemId, name);
		}

	}
	
	static abstract class ItemReport {
		protected final String itemId;
		protected final String name;
		
		public ItemReport(String itemId, String name) {
			this.itemId = itemId;
			this.name = name;
		}

		public String getItemId() {
			return itemId;
		}

		public String getName() {
			return name;
		}
	}
	
    private Collection<ComponentReport> componentChanges;
    private List<ChangeSetReport> changeSets;
    private BaselineSetReport baselineSet;
    private boolean isPersonalBuild;
	private OutputStream changeLog;
	private String previousBuildUrl;
	private BuildDefinitionReport buildDefinition;
	private BuildWorkspaceReport buildWorkspace;
    private BaselineSetReport previousBaselineSet;
	private BuildStreamReport buildStream;

    public ChangeReport(OutputStream changeLogFile) {
    	this.changeLog = changeLogFile;
    	this.componentChanges = new ArrayList<ChangeReport.ComponentReport>();
    	this.changeSets = new ArrayList<ChangeReport.ChangeSetReport>();
    }

    public void setIsPersonalBuild(boolean isPersonalBuild) {
    	this.isPersonalBuild = isPersonalBuild;
    }
    
    public void setPreviousBuildUrl(String previousBuildUrl) {
    	this.previousBuildUrl = previousBuildUrl;
    }

    public void componentChange(ComponentReport component) {
    	this.componentChanges.add(component);
    }
    
    public void changeSetsChange(ChangeSetReport changeSet) {
		changeSets.add(changeSet);
    }
    
    public void baselineSetCreated(BaselineSetReport baselineSet) {
    	this.baselineSet = baselineSet;
    }

    public void previousBaselineSetCreated(BaselineSetReport previousBaselineSet) {
    	this.previousBaselineSet = previousBaselineSet;
    }

	public Collection<ComponentReport> getComponentChanges() {
		return componentChanges;
	}

	public List<ChangeSetReport> getChangeSets() {
		return changeSets;
	}

	/**
	 * Helper method to get all work items from the change sets
	 * 
	 * @return A list of work item ids. 
	 * May be empty.
	 * Never <code>null<code>
	 */
	public List<Integer> getWorkItems() {
		if (this.changeSets == null) {
			return new ArrayList<Integer>();
		}
		List<Integer> workItems = new ArrayList<Integer>();
		for (ChangeSetReport changeSetReport : this.changeSets) {
			List<WorkItemEntry> changeSetWorkItems = changeSetReport.getWorkItems();
			if (changeSetWorkItems != null) {
				for(WorkItemEntry workItemEntry : changeSetWorkItems) {
					workItems.add(Integer.valueOf(workItemEntry.getNumber()));
				}
			}
		}
		return workItems;
	}

	/**
	 * Helper method to get all work items from accepted change sets
	 * 
	 * @return A list of work item ids. 
	 * May be empty.
	 * Never <code>null<code>
	 */
	public List<Integer> getAcceptedWorkItems() {
		if (this.changeSets == null) {
			return new ArrayList<Integer>();
		}
		List<Integer> workItems = new ArrayList<Integer>();
		for (ChangeSetReport changeSetReport : this.changeSets) {
			List<WorkItemEntry> changeSetWorkItems = changeSetReport.getWorkItems();
			if (changeSetWorkItems != null && changeSetReport.isAccepted()) {
				for(WorkItemEntry workItemEntry : changeSetWorkItems) {
					workItems.add(Integer.valueOf(workItemEntry.getNumber()));
				}
			}
		}
		return workItems;
	}

	public BaselineSetReport getBaselineSet() {
		return baselineSet;
	}
    
	public void buildDefinitionCreated(BuildDefinitionReport buildDefinition) {
		this.buildDefinition = buildDefinition;
	}
	
	public BuildDefinitionReport getBuildDefinition() {
		return buildDefinition;
		
	}
	
	public void buildWorkspaceCreated(BuildWorkspaceReport buildWorkspace) {
		this.buildWorkspace = buildWorkspace;
	}
	
	public BuildWorkspaceReport getBuildWorkspace() {
		return buildWorkspace;
		
	}
	
	public void buildStreamCreated(BuildStreamReport buildStream) {
		this.buildStream = buildStream;
	}
	
	public BuildStreamReport getBuildStream() {
		return buildStream;
		
	}

	public void prepareChangeSetLog() throws IOException {
        CharsetEncoder encoder = Charset.forName("UTF-8").newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT); //$NON-NLS-1$
        OutputStreamWriter osw = new OutputStreamWriter(changeLog, encoder);
        PrintWriter writer = new PrintWriter(osw);
        try {
        	writeChangeSetLog(writer);
        } finally {
			writer.close();
        }
	}

	protected void writeChangeSetLog(PrintWriter writer) {
		try {
	        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"); //$NON-NLS-1$
	        
	        recordChangelogEntry(writer);
	
	        // record the component changes
	        recordComponentUpdates(writer);
	        
	        // record the change set changes
	        recordChangeSetUpdates(writer);
	        
	        writer.println("</changelog>"); //$NON-NLS-1$
		} finally {
			writer.flush();
		}
	}

	private void recordChangelogEntry(PrintWriter writer) {
		if (baselineSet != null) {
			StringBuilder str = new StringBuilder();
			str.append("<changelog")
				.append(" baselineSetName=\"" +escapeXml(baselineSet.getName()) + "\"") //$NON-NLS-1$
				.append(" baselineSetItemId=\"" + baselineSet.getItemId() + "\""); //$NON-NLS-1$
			if (buildWorkspace != null) {
				str.append(" workspaceName=\"" + escapeXml(buildWorkspace.getName()) + "\"") //$NON-NLS-1$
				   .append(" workspaceItemId=\"" + buildWorkspace.getItemId() + "\"");
			}
			if (buildDefinition != null) {
				str.append(" buildDefinitionName=\"" + escapeXml(buildDefinition.getName()) + "\"")
				    .append(" buildDefinitionItemId=\"" + buildDefinition.getItemId() + "\"");
			}
			if (buildStream != null) {
				str.append(" streamName=\"" + escapeXml(buildStream.getName()) + "\"")
					.append(" streamItemId=\"" + buildStream.getItemId() + "\"");
			}
			str.append(" isPersonalBuild=\"" + isPersonalBuild + "\""); //$NON-NLS-1$
			
			if (previousBuildUrl != null) {
			   str.append(" previousBuildUrl=\"" + previousBuildUrl + "\""); //$NON-NLS-1$
			} else {// To differentiate between absence of data vs absence of a previous build url
				str.append(" previousBuildUrl=\"\""); //$NON-NLS-1$
			}
			if (previousBaselineSet != null) {
				str.append(" previousBaselineSetName=\"" +escapeXml(previousBaselineSet.getName()) + "\"") //$NON-NLS-1$
				.append(" previousBaselineSetItemId=\"" + previousBaselineSet.getItemId() + "\"");
			}
			str.append(">");

			writer.println(str);
		} else if (buildWorkspace != null) {
			writer.println(
					"<changelog workspaceItemId=\"" + buildWorkspace.getItemId() + //$NON-NLS-1$
					"\" isPersonalBuild=\"" + isPersonalBuild + //$NON-NLS-1$
					"\">"); //$NON-NLS-1$
		} else {
			writer.println("<changelog isPersonalBuild=\"" + isPersonalBuild + //$NON-NLS-1$
					"\">"); //$NON-NLS-1$
		}
	}

	private void recordComponentUpdates(PrintWriter writer) {
		for (ComponentReport componentReport : componentChanges) {
			String action = componentReport.added ? ADDED : DROPPED;
			writer.println("    <component action=\"" + action + //$NON-NLS-1$
					"\" name=\"" + escapeXml(componentReport.name) +  //$NON-NLS-1$
					"\" itemId=\"" + componentReport.itemId + //$NON-NLS-1$
					"\"/>"); //$NON-NLS-1$
		}
	}

	private void recordChangeSetUpdates(PrintWriter writer) {
		StringBuilder buffer = new StringBuilder();
		for (ChangeSetReport changeSetReport : changeSets) {
			
			String action = changeSetReport.isAccepted() ? ADDED : DROPPED; 
					
			buffer.append("    <changeset action=\"").append(action); //$NON-NLS-1$
			buffer.append("\" owner=\"").append(escapeXml(changeSetReport.owner)).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			if (changeSetReport.modifyDate != null) {
				buffer.append("date=\"").append(changeSetReport.modifyDate.getTime()).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (changeSetReport.comment != null || changeSetReport.comment.length() > 0) {
				buffer.append("comment=\"").append(escapeXml(changeSetReport.comment)).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
			buffer.append("changeSetItemId=\"").append(changeSetReport.itemId).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			if (changeSetReport.componentItemId != null) {
				buffer.append("componentItemId=\"").append(changeSetReport.componentItemId).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (changeSetReport.componentName != null) {
				buffer.append("componentName=\"").append(changeSetReport.componentName).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (changeSetReport.additionalChanges > 0) {
				buffer.append("additionalChanges=\"").append(changeSetReport.additionalChanges).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
			}
			buffer.append(" >"); //$NON-NLS-1$
			writer.println(buffer.toString());
			buffer.setLength(0);
			
			// log the individual changes
			if (!changeSetReport.changes.isEmpty()) {
				writer.println("        <changes>"); //$NON-NLS-1$
				for (ChangeEntry changeEntry : changeSetReport.changes) {
					recordChange(writer, changeEntry);
				}
				writer.println("        </changes>"); //$NON-NLS-1$
			}
			
			// log the associated work items
			if (!changeSetReport.workItems.isEmpty()) {
				writer.println("        <workItems>"); //$NON-NLS-1$
				for (WorkItemEntry workItemEntry : changeSetReport.workItems) {
					recordWorkItem(writer, workItemEntry);
				}
				writer.println("        </workItems>"); //$NON-NLS-1$
			}
			writer.println("    </changeset>"); //$NON-NLS-1$
		}
	}

	private void recordChange(PrintWriter writer, ChangeEntry changeEntry) {
		StringBuilder buffer = new StringBuilder();
		buffer.append("            <change kind=\"").append(changeEntry.getKind());  //$NON-NLS-1$
		buffer.append("\" name=\"").append(escapeXml(changeEntry.getName())); //$NON-NLS-1$
		buffer.append("\" itemType=\"").append(changeEntry.itemType); //$NON-NLS-1$
		buffer.append("\" itemId=\"").append(changeEntry.getItemId()).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
		if (changeEntry.getStateId() != null) {
			buffer.append("stateId=\"").append(changeEntry.getStateId()).append("\" "); //$NON-NLS-1$ //$NON-NLS-2$
		}
		buffer.append("/>"); //$NON-NLS-1$
		writer.println(buffer.toString());
	}

	private void recordWorkItem(PrintWriter writer, WorkItemEntry workItemEntry) {
		writer.println("            <workItem number=\"" + workItemEntry.getNumber() +  //$NON-NLS-1$
				"\" summary=\"" + escapeXml(workItemEntry.getSummary()) + "\" />"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private String escapeXml(String toEscape) {
		if (toEscape ==  null) {
			return toEscape;
		}
		
		StringBuilder buffer = new StringBuilder(toEscape.length());
        for(int index = 0; index < toEscape.length(); index ++) {
            //Convert special chars.
            char ch = toEscape.charAt(index);
            switch(ch)
            {
                case '&'  : 
                	buffer.append("&amp;"); //$NON-NLS-1$
                	break;
                case '<'  : 
                	buffer.append("&lt;"); //$NON-NLS-1$
                	break;
                case '>'  : 
                	buffer.append("&gt;"); //$NON-NLS-1$
                	break;
                case '\'' :
                	buffer.append("&apos;"); //$NON-NLS-1$
                	break;
                case '\"' :
                	buffer.append("&quot;"); //$NON-NLS-1$
                	break;
    			case '\r':
    				buffer.append("&#x0D;"); //$NON-NLS-1$
    			case '\n':
    				buffer.append("&#x0A;"); //$NON-NLS-1$
    			case '\u0009':
    				buffer.append("&#x09;"); //$NON-NLS-1$
                default:
                	buffer.append(ch);
            }
        }

		return buffer.toString();
	}

}
