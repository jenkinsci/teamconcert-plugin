/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport.BaselineSetReport;
import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport.ChangeSetReport;
import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport.ComponentReport;
import com.ibm.team.build.internal.hjplugin.rtc.ChangeReport.ChangeSetReport.WorkItemEntry;
import com.ibm.team.build.internal.scm.AcceptReport;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.ILinkHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.IItem;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IVersionableManager;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IChange;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IAncestorReport;
import com.ibm.team.scm.common.dto.IChangeHistorySyncReport;
import com.ibm.team.scm.common.dto.IChangeSetLinkSummary;
import com.ibm.team.scm.common.dto.INameItemPair;
import com.ibm.team.scm.common.links.ILinkConstants;
import com.ibm.team.scm.common.providers.ProviderFactory;
import com.ibm.team.scm.common.providers.ScmProvider;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;

/**
 * Builds a detailed change report up from handles.
 * TODO Depending on the boundaries of responsibility and what classes
 * are available for use, this could move into {@link ChangeReport}
 */
@SuppressWarnings("restriction") 
public class ChangeReportBuilder {

    private static final Logger LOGGER = Logger.getLogger(ChangeReportBuilder.class.getName());
	
	// TODO These localized messages should not be added to the change log because they are not user data
	private final String UNKNOWN_PARENT_FOLDER = Messages.getDefault().ChangeReportBuilder_unknown_parent_folder();
	private final String UNKNOWN_AUTHOR = Messages.getDefault().ChangeReportBuilder_unknown_author();
	private final String UNKNOWN_COMPONENT = Messages.getDefault().ChangeReportBuilder_unknown_component();
	private final String UNKNOWN_VERSIONABLE_NAME = Messages.getDefault().ChangeReportBuilder_unknown_versionable();
	private ITeamRepository fRepository;

	public ChangeReportBuilder(ITeamRepository repository) {
		this.fRepository = repository;
	}

	/**
	 * Populate the contents of an empty ChangeReport with the 
	 * results of the accept
	 * @param changeReport The change report to be expanded
	 * @param workspaceHandle The workspace to use for context (usually build workspace)
	 * @param acceptReport The result of the accept
	 */
	public void populateChangeReport(ChangeReport changeReport,
			IWorkspaceHandle workspaceHandle, 
			AcceptReport acceptReport, IConsoleOutput listener,
			IProgressMonitor progress) throws TeamRepositoryException {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		
		// record build workspace
		changeReport.setWorkspaceItemId(workspaceHandle.getItemId().getUuidValue());
		
		// record snapshot if one was created
		fillSnapshot(changeReport, acceptReport, listener, monitor.newChild(5));
		
		// record component additions/removals
		fillComponentChanges(changeReport, acceptReport, listener, monitor.newChild(10)); 

		// record change sets accepted/discarded
		fillChangeSetChanges(changeReport, acceptReport, workspaceHandle, listener, monitor.newChild(85));
	}
	
	/**
	 * Populates the given {@link ChangeReport} from a {@link IChangeHistorySyncReport}
	 * @param changeReport - the {@link ChangeReport} to fill in
	 * @param workspaceHandle - the {@link IWorkspaceHandle} to resolve file paths/names against 
	 * @param snapshot - the snapshot for this {@link ChangeReport}
	 * @param snapshotName - the name of the snapshot
	 * @param acceptReport - the {@link IChangeHistorySyncReport} 
	 * @param listener - a {@link IConsoleOutput} to which messages should be written to
	 * @param progress - a {@link IProgressMonitor} to report progress
	 * @throws TeamRepositoryException
	 */
	public void populateChangeReport(ChangeReport changeReport,
			IWorkspaceHandle workspaceHandle, IBaselineSetHandle snapshot, String snapshotName,
			IChangeHistorySyncReport acceptReport, IConsoleOutput listener,
			IProgressMonitor progress) throws TeamRepositoryException {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		
		// record build workspace
		changeReport.setWorkspaceItemId(workspaceHandle.getItemId().getUuidValue());
		
		// record snapshot if one was created
		fillSnapshot(changeReport, snapshot, snapshotName, listener, monitor.newChild(5));
		
		// record component additions/removals
		fillComponentChanges(changeReport, acceptReport, listener, monitor.newChild(10)); 

		// record change sets accepted/discarded
		fillChangeSetChanges(changeReport, acceptReport, workspaceHandle, listener, monitor.newChild(85));
	}

	/**
	 * Populate the contents of an empty ChangeReport with the 
	 * info related to the personal build
	 * @param changeReport The change report to be expanded
	 * @param isPersonalBuild Whether this is a personal build
	 */
	public void populateChangeReport(ChangeReport changeReport,
			boolean isPersonalBuild, IConsoleOutput listener) {
		changeReport.setIsPersonalBuild(isPersonalBuild);
	}
	
	/**
	 * Populate the contents of empty ChangeReport with only snapshot link
	 */
	public void populateChangeReport(ChangeReport changeReport, IBaselineSetHandle baselineSetHandle, String snapshotName,
										IConsoleOutput listener, IProgressMonitor progress) {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		// record snapshot if one was created
		fillSnapshot(changeReport, baselineSetHandle, snapshotName, listener, monitor.newChild(5));
	}

	private void fillSnapshot(ChangeReport changeReport,
			AcceptReport acceptReport, IConsoleOutput listener,
			IProgressMonitor progress) {
		IBaselineSet snapshot = acceptReport.getSnapshot();
		if (snapshot != null) {
			ChangeReport.BaselineSetReport baseLineSet = new BaselineSetReport(snapshot.getItemId().getUuidValue(), snapshot.getName());
			changeReport.baselineSetCreated(baseLineSet);
		}
	}
	
	private void fillSnapshot(ChangeReport changeReport,
			final IBaselineSetHandle snapshot, final String snapshotName, IConsoleOutput listener,
			IProgressMonitor progress) {
		if (snapshot != null) {
			ChangeReport.BaselineSetReport baseLineSet = new BaselineSetReport(snapshot.getItemId().getUuidValue(), snapshotName);
			changeReport.baselineSetCreated(baseLineSet);
		}
	}

	private void fillChangeSetChanges(ChangeReport changeReport,
			AcceptReport acceptReport, IWorkspaceHandle workspaceHandle,
			IConsoleOutput listener, IProgressMonitor progress) throws TeamRepositoryException {
		// Retrieve the full changesets and create reports representing them
		// then free memory for the full change sets
		// Retrieve the links and get just the minimum work item info needed.
		// Things are done this way rather than using ChangeSetLinks.resolveLinks which returns full change set
		// and full workitems all at the same time because we don't need the full work items and we don't expect
		// the full info to be previously cached.
		// My concern is that the ChangeSetLinks might not scale for larger build accepts
		IChangeSetHandle[] changeSetsAccepted = acceptReport.getAcceptChangeSets();
		IChangeSetHandle[] changeSetsDisarded = acceptReport.getDiscardChangeSets();
		fillChangeSetChanges(changeReport, changeSetsAccepted, changeSetsDisarded, workspaceHandle, listener, progress);
	}
	
	private void fillChangeSetChanges(ChangeReport changeReport,
			IChangeHistorySyncReport compareReport, IWorkspaceHandle workspaceHandle,
			IConsoleOutput listener, IProgressMonitor progress) throws TeamRepositoryException {
		List changeSetsAcceptedList = compareReport.outgoingChangeSets();
		List changeSetsDisardedList = compareReport.incomingChangeSets();
		IChangeSetHandle [] changeSetsAccepted = new IChangeSetHandle[changeSetsAcceptedList.size()];
		IChangeSetHandle [] changeSetsDisarded = new IChangeSetHandle[changeSetsDisardedList.size()];
		changeSetsAcceptedList.toArray(changeSetsAccepted);
		changeSetsDisardedList.toArray(changeSetsDisarded);
		fillChangeSetChanges(changeReport, changeSetsAccepted, changeSetsDisarded, workspaceHandle, listener, progress);
	}
	
	private void fillChangeSetChanges(ChangeReport changeReport,
			final IChangeSetHandle[] changeSetsAccepted, final IChangeSetHandle[] changeSetsDisarded,
			IWorkspaceHandle workspaceHandle, IConsoleOutput listener, IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		List<IChangeSetHandle> changeSetHandles = new ArrayList<IChangeSetHandle>(changeSetsAccepted.length + changeSetsDisarded.length);
		int numberOfAdds = changeSetsAccepted.length;
		for (IChangeSetHandle changeSetHandle : changeSetsAccepted) {
			changeSetHandles.add(changeSetHandle);
		}
		for (IChangeSetHandle changeSetHandle : changeSetsDisarded) {
			changeSetHandles.add(changeSetHandle);
		}

		try {
			List<IChangeSet> changeSets = fRepository.itemManager().fetchCompleteItems(changeSetHandles, IItemManager.DEFAULT, monitor.newChild(25));
			List<ChangeSetReport> changeSetReports = new ArrayList<ChangeReport.ChangeSetReport>(changeSetHandles.size());
			Map<UUID, IContributorHandle> contributorHandles = new HashMap<UUID, IContributorHandle>();
			Map<UUID, IComponentHandle> componentHandles = new HashMap<UUID, IComponentHandle>();
			Map<UUID, Map<UUID, IVersionableHandle>> uniqueVersionableByComponent = new HashMap<UUID, Map<UUID,IVersionableHandle>>();
			int i = 0;
			for (IChangeSet changeSet : changeSets) {
				if (changeSet == null) {
					// TODO This localized message should not be added to the change log because they are not user data
					changeReport.changeSetsChange(new ChangeSetReport(i < numberOfAdds, changeSetHandles.get(i).getItemId().getUuidValue(),
							null, Messages.getDefault().ChangeReportBuilder_unable_to_get_change_set(), null, 0));
				} else {
					ChangeSetReport report = new ChangeSetReport(i < numberOfAdds, changeSet.getItemId().getUuidValue(), changeSet.getComponent().getItemId().getUuidValue(),
							changeSet.getComment(), changeSet.getLastChangeDate(), (long) changeSet.changes().size());
					changeReport.changeSetsChange(report);
					
					// fill in further details of the report
					changeSetReports.add(report);
					IComponentHandle componentHandle = changeSet.getComponent();
					if (changeSet.changes().size() > 0 && changeSet.changes().size() < ChangeReport.VERSIONABLE_CHANGES_LIMIT) {
						Map<UUID, IVersionableHandle> uniqueVersionables = uniqueVersionableByComponent.get(componentHandle.getItemId());
						if (uniqueVersionables == null) {
							uniqueVersionables = new HashMap<UUID, IVersionableHandle>();
							uniqueVersionableByComponent.put(changeSet.getComponent().getItemId(), uniqueVersionables);
						}
						for (IChange change : (List<IChange>) changeSet.changes()) {
							IVersionableHandle item = change.item();
							uniqueVersionables.put(item.getItemId(), item);
						}
					}
					contributorHandles.put(changeSet.getAuthor().getItemId(), changeSet.getAuthor());
					componentHandles.put(componentHandle.getItemId(), componentHandle);
				}
				i++;
			}
			
			fillChangeSetDetails(changeSets, changeSetReports, contributorHandles, componentHandles, listener, monitor.newChild(25));
			// free memory
			contributorHandles = null;
			componentHandles = null;
			
			fillVersionables(changeSets, changeSetReports, workspaceHandle, uniqueVersionableByComponent,
					listener, monitor.newChild(25));
			// free memory
			uniqueVersionableByComponent = null;
			changeSets = null;
			
			fillWorkItems(changeSetHandles, changeSetReports, listener, monitor.newChild(25));
			
		} catch (TeamRepositoryException e) {
			listener.log(Messages.getDefault().ChangeReportBuilder_unable_to_expand_change_sets(e.getMessage()), e);
			LOGGER.log(Level.FINER, "Unable to resolve change sets " + e.getMessage(), e); //$NON-NLS-1$
			// capture anyhow that change sets were accepted/discarded
			int i = 0;
			for (IChangeSetHandle changeSetHandle : changeSetHandles) {
				changeReport.changeSetsChange(new ChangeSetReport(i < numberOfAdds, changeSetHandle.getItemId().getUuidValue(),
						null, Messages.getDefault().ChangeReportBuilder_unable_to_get_change_set2(), null, 0));
				i++;
			}
			if (Utils.isCancellation(e)) {
				throw e;
			}
		}
	}

	private void fillChangeSetDetails(List<IChangeSet> changeSets, List<ChangeSetReport> changeSetReports,
			Map<UUID, IContributorHandle> contributorHandles, Map<UUID, IComponentHandle> componentHandles,
			IConsoleOutput listener, IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			List<IItemHandle> itemsToFetch = new ArrayList<IItemHandle>(componentHandles.size() + contributorHandles.size());
			itemsToFetch.addAll(contributorHandles.values());
			itemsToFetch.addAll(componentHandles.values());
			List<IItem> fullItems = fRepository.itemManager().fetchCompleteItems(itemsToFetch, IItemManager.DEFAULT, monitor);
			Map<UUID, IContributor> contributors = new HashMap<UUID, IContributor>();
			Map<UUID, IComponent> components = new HashMap<UUID, IComponent>();
			for (IItem item : fullItems) {
				if (item instanceof IContributor) {
					contributors.put(item.getItemId(), (IContributor) item);
				} else if (item instanceof IComponent) {
					components.put(item.getItemId(), (IComponent) item);
				}
			}
			Iterator<ChangeSetReport> iterator = changeSetReports.iterator();
			for (IChangeSet changeSet : changeSets) {
				ChangeSetReport changeSetReport = iterator.next();
				IContributor contributor = contributors.get(changeSet.getAuthor().getItemId());
				if (contributor != null) {
					changeSetReport.setOwner(contributor.getUserId());	
				} else {
					changeSetReport.setOwner(UNKNOWN_AUTHOR);
				}
				IComponent component = components.get(changeSet.getComponent().getItemId());
				if (component != null) {
					changeSetReport.setComponentName(component.getName());
				} else {
					changeSetReport.setComponentName(UNKNOWN_COMPONENT);
				}
			}
		} catch (TeamRepositoryException e) {
			listener.log(Messages.getDefault().ChangeReportBuilder_unable_to_get_authors(e.getMessage()), e);
			LOGGER.log(Level.FINER, "Unable to resolve contributors " + e.getMessage(), e); //$NON-NLS-1$
			for (ChangeSetReport changeSetReport : changeSetReports) {
				changeSetReport.setOwner(UNKNOWN_AUTHOR);	
			}
			
			if (Utils.isCancellation(e)) {
				throw e;
			}
		}
	}

	private void fillComponentChanges(ChangeReport changeReport,
			AcceptReport acceptReport, IConsoleOutput listener,
			IProgressMonitor progress) throws TeamRepositoryException {
		IComponentHandle[] componentAdds = acceptReport.getComponentAdds();
		IComponentHandle[] componentRemovals = acceptReport.getComponentRemovals();
		fillComponentChanges(changeReport, componentAdds, componentRemovals, listener, progress);
	}
	
	private void fillComponentChanges(ChangeReport changeReport,
			IChangeHistorySyncReport compareReport, IConsoleOutput listener,
			IProgressMonitor progress) throws TeamRepositoryException {
		List<IComponentHandle> componentAddsList = new ArrayList<IComponentHandle>();
		List<IComponentHandle> componentRemovalsList = new ArrayList<IComponentHandle>();
		final List localComponents= compareReport.localComponents();
		final List remoteComponents = compareReport.remoteComponents();
		for (int i = 0 ; i < localComponents.size(); i++ ) {
			IComponentHandle localComponent = (IComponentHandle) localComponents.get(i);
			boolean matched = false;
			for (int j = 0 ; j < remoteComponents.size() ; j++) {
				IComponentHandle remoteComp = (IComponentHandle) remoteComponents.get(j);
				if (localComponent.sameItemId(remoteComp)) {
					matched = true;
					break;
				}
			}
			if (!matched) {
				componentAddsList.add(localComponent);
			}
		}
		for (int i = 0 ; i < remoteComponents.size(); i++ ) {
			final IComponentHandle remoteComponent = (IComponentHandle) remoteComponents.get(i);
			boolean matched = false;
			for (int j = 0 ; j < localComponents.size() ; j++) {
				final IComponentHandle localComponent = (IComponentHandle) localComponents.get(j);
				if (remoteComponent.sameItemId(localComponent)) {
					matched = true;
					break;
				}
			}
			if (!matched) {
				componentRemovalsList.add(remoteComponent);
			}
		}
		IComponentHandle [] componentAdds = new IComponentHandle[componentAddsList.size()];
		IComponentHandle [] componentRemovals = new IComponentHandle[componentRemovalsList.size()];
		componentAddsList.toArray(componentAdds);
		componentRemovalsList.toArray(componentRemovals);
		fillComponentChanges(changeReport, componentAdds, componentRemovals, listener, progress);
	}
	
	private void fillComponentChanges(ChangeReport changeReport,
			final IComponentHandle[] componentAdds, final IComponentHandle[] componentRemovals, IConsoleOutput listener,
			IProgressMonitor progress) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		// look up the components added & removed to get their names

		int numberOfAdds = componentAdds.length;
		List<IComponentHandle> componentHandles = new ArrayList<IComponentHandle>(componentAdds.length + componentRemovals.length);
		for (IComponentHandle componentHandle : componentAdds) {
			componentHandles.add(componentHandle);
		}
		for (IComponentHandle componentHandle : componentRemovals) {
			componentHandles.add(componentHandle);
		}

		try {
			List<IComponent> components = fRepository.itemManager().fetchCompleteItems(componentHandles, IItemManager.DEFAULT, monitor);
			int i = 0;
			for (IComponent component : components) {
				if (component == null) {
					changeReport.componentChange(new ComponentReport((i<numberOfAdds), componentHandles.get(i).getItemId().getUuidValue(),
								Messages.getDefault().ChangeReportBuilder_unable_to_get_component_name()));
				} else {
					changeReport.componentChange(new ComponentReport((i<numberOfAdds), component.getItemId().getUuidValue(), component.getName()));
				}
				i++;
			}
		} catch (TeamRepositoryException e) {
			listener.log(Messages.getDefault().ChangeReportBuilder_unable_to_get_component(e.getMessage()), e);
			LOGGER.log(Level.FINER, "Unable to resolve components " + e.getMessage(), e); //$NON-NLS-1$
			// capture anyhow that components were added/removed
			int i = 0;
			for (IComponentHandle componentHandle : componentHandles) {
				changeReport.componentChange(new ComponentReport(i<numberOfAdds, componentHandle.getItemId().getUuidValue(),
						Messages.getDefault().ChangeReportBuilder_unable_to_get_component_name2()));
				i++;
			}
			
			if (Utils.isCancellation(e)) {
				throw e;
			}
		}
	}

	private void fillVersionables(List<IChangeSet> changeSets,
			List<ChangeSetReport> changeSetReports,
			IWorkspaceHandle workspaceHandle,
			Map<UUID, Map<UUID, IVersionableHandle>> uniqueVersionableByComponent,
			IConsoleOutput listener, IProgressMonitor progress) throws TeamRepositoryException {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		
		// We will get the repository path of the versionable as it is NOW within the build workspace
		// This is cheaper than interpretting the change set which would attempt to give us the path
		// or atleast the name of the versionable at the end of the changeset being interpretted but
		// before other change sets have been applied. The interpretting will essentially also do the
		// same lookup as below but also retrieve states and compare them and attempt to find the before
		// path of the item too.
		// We in theory could try to look up the items on disk, to get the path but that will be error-prone.
		// Depending on how the component is loaded, the path may not match the repo path. Because the load occurs
		// after the change report is built (we want change report even if the load fails) we will have the
		// wrong path for items that are to move and no path for items that are to be added. Even if we did
		// the lookup after loading, we would then not be able to get the path for the items deleted.
		try {
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(fRepository);
			IWorkspaceConnection workspaceConnection = workspaceManager.getWorkspaceConnection(workspaceHandle, monitor.newChild(25));
			Map<UUID, Map<UUID, String>> pathsByComponent = new HashMap<UUID, Map<UUID,String>>();
			monitor.setWorkRemaining(25 + uniqueVersionableByComponent.size() * 10);
			for (Map.Entry<UUID, Map<UUID, IVersionableHandle>> entryByComponent : uniqueVersionableByComponent.entrySet()) {
				IComponentHandle componentHandle = (IComponentHandle) IComponent.ITEM_TYPE.createItemHandle(entryByComponent.getKey(), null);
				Map<UUID, IVersionableHandle> uniqueVersionables = entryByComponent.getValue();
				if (!uniqueVersionables.isEmpty()) {
					Map<UUID, String> paths = new HashMap<UUID, String>();
					pathsByComponent.put(entryByComponent.getKey(), paths);

					List<IVersionableHandle> versionables = new ArrayList<IVersionableHandle>(uniqueVersionables.size());
					versionables.addAll(uniqueVersionables.values());
					List<IAncestorReport> ancestorReports = workspaceConnection.configuration(componentHandle).determineAncestorsInHistory(versionables, monitor.newChild(10));
					for (IAncestorReport ancestorReport : ancestorReports) {
						if (!ancestorReport.getNameItemPairs().isEmpty()) {
							IVersionableHandle lastItem = null;
							StringBuilder path = new StringBuilder();
							boolean first = true;
							for (INameItemPair pair : (List<INameItemPair>) ancestorReport.getNameItemPairs()) {
								lastItem = pair.getItem();
								if (first) {
									// skip the component root
									first = false;
								} else {
									path.append("/").append(pair.getName()); //$NON-NLS-1$
								}
							}
							if (path.length() == 0) {
								// we have the component root
								paths.put(lastItem.getItemId(), "/"); //$NON-NLS-1$
							} else {
								paths.put(lastItem.getItemId(), path.toString());
							}
						}
					}
				}
			}
			
			// Set of parallel arrays hopefully to be empty. Idea is if there is if no path can be found for an item
			// then we will try to get the name from the versionable state and report /<unknown>/name as the name.
			// The arrays track the change that is missing the path and the change report it should be added to once figured out.
			List<IVersionableHandle> versionablesMissingPaths = new ArrayList<IVersionableHandle>();
			List<IChange> changesMissingPaths = new ArrayList<IChange>();
			List<ChangeSetReport> reportsMissingChanges = new ArrayList<ChangeSetReport>();
			int i = 0;
			for (IChangeSet changeSet : changeSets) {
				if (changeSet != null) {
					if (changeSet.changes().size() > 0 && changeSet.changes().size() < ChangeReport.VERSIONABLE_CHANGES_LIMIT) {
						ChangeSetReport changeSetReport = changeSetReports.get(i);
						Map<UUID, String> paths = pathsByComponent.get(changeSet.getComponent().getItemId());
						int missingCount = 0;
						for (IChange change : (List<IChange>) changeSet.changes()) {
							IVersionableHandle versionable = change.item();
							String path = paths.get(versionable.getItemId());
							IVersionableHandle versionableChangedHandle = null;
							if (change.afterState() != null) {
								versionableChangedHandle = change.afterState();
							} else if (change.beforeState() != null) {
								versionableChangedHandle = change.beforeState();
							}
							if (path == null) {
								if (versionableChangedHandle != null) {
									versionablesMissingPaths.add(versionableChangedHandle);
									changesMissingPaths.add(change);
									reportsMissingChanges.add(changeSetReport);
									missingCount++;
								} else {
									// We don't have a state to look up the item's name
									ChangeSetReport.ChangeEntry changeEntry = new ChangeSetReport.ChangeEntry(change.kind(), UNKNOWN_VERSIONABLE_NAME,
											versionable.getItemType().getName(),
											versionable.getItemId().getUuidValue(),
											null);
									changeSetReport.addChange(changeEntry);
								}
							} else {
								ChangeSetReport.ChangeEntry changeEntry = new ChangeSetReport.ChangeEntry(change.kind(), path,
										versionable.getItemType().getName(),
										versionable.getItemId().getUuidValue(),
										versionableChangedHandle == null ?  null : versionableChangedHandle.getStateId().getUuidValue());
								changeSetReport.addChange(changeEntry);
							}
						}
						changeSetReport.setAdditionalChanges(missingCount);
					}
				}
				i++;
			}
			
			// If we have missing paths, try to get the name from the final state
			if (!versionablesMissingPaths.isEmpty()) {
				// Have to ask for whole state because we have a mix of types even though we only want the common name property of them all.
				IVersionableManager versionableManager = workspaceManager.versionableManager();
				List<IVersionable> fullVersionables = versionableManager.fetchCompleteStates(versionablesMissingPaths, monitor.newChild(25));
				int j=0;
				for (IVersionable versionable : fullVersionables) {
					ChangeSetReport report = reportsMissingChanges.get(j);
					IChange change = changesMissingPaths.get(j);
					if (versionable == null) {
						IVersionableHandle versionableState = versionablesMissingPaths.get(j);
						ChangeSetReport.ChangeEntry changeEntry = new ChangeSetReport.ChangeEntry(change.kind(),
								"/" + UNKNOWN_PARENT_FOLDER, //$NON-NLS-1$
								versionableState.getItemType().getName(),
								versionableState.getItemId().getUuidValue(),
								versionableState.getStateId().getUuidValue());
						report.addChange(changeEntry);
					} else {
						ChangeSetReport.ChangeEntry changeEntry = new ChangeSetReport.ChangeEntry(change.kind(),
								"/" + UNKNOWN_PARENT_FOLDER + "/" + versionable.getName(), //$NON-NLS-1$ //$NON-NLS-2$
								versionable.getItemType().getName(),
								versionable.getItemId().getUuidValue(),
								versionable.getStateId().getUuidValue());
						report.addChange(changeEntry);
					}
					report.setAdditionalChanges(report.getAdditionalChanges()-1);
					j++;
				}
			}
		} catch (TeamRepositoryException e) {
			// The record was already created with the additional change count so no need to do anything else.
			listener.log(Messages.getDefault().ChangeReportBuilder_unable_to_get_versionable_names(e.getMessage()), e);
			LOGGER.log(Level.FINER, "Unable to resolve versionable names " + e.getMessage(), e); //$NON-NLS-1$
			
			if (Utils.isCancellation(e)) {
				throw e;
			}
		}
	}

    private void fillWorkItems(List<IChangeSetHandle> changeSetHandles,
			List<ChangeSetReport> changeSetReports, IConsoleOutput listener,
			IProgressMonitor progress) throws TeamRepositoryException {
    	SubMonitor monitor = SubMonitor.convert(progress, 100);
    	ProviderFactory providerFactory = (ProviderFactory) fRepository.getClientLibrary(ProviderFactory.class);
        ScmProvider scmProvider = providerFactory.getScmProvider();
        
        try {
	        // get the IChangeSetLinkSummary for all change sets
        	if (!changeSetHandles.isEmpty()) {
		        List<IChangeSetLinkSummary> linkSummaries = scmProvider.getChangeSetLinkSummary(changeSetHandles, monitor.newChild(10));
		        Map<UUID, IChangeSetLinkSummary> linkSummaryByChangeSet = new HashMap<UUID, IChangeSetLinkSummary>();
		        List<ILinkHandle> toRetrieve = new ArrayList<ILinkHandle>(linkSummaries.size());
		        for (IChangeSetLinkSummary summary : linkSummaries) {
		        	linkSummaryByChangeSet.put(summary.getChangeSet().getItemId(), summary);
		        	toRetrieve.addAll(summary.getLinks());
		        }
		        // get the ILink referenced by all the change set link summaries
		        List<ILink> linksFetched = fRepository.itemManager().fetchCompleteItems(toRetrieve, IItemManager.DEFAULT, monitor.newChild(10));
		        List<IWorkItemHandle> toFetch = new ArrayList<IWorkItemHandle>();
		        Map<UUID, ILink> links = new HashMap<UUID, ILink>();
		        for (ILink link : linksFetched) {
		        	if (link.getLinkType().getLinkTypeId().equals(ILinkConstants.CHANGESET_WORKITEM_LINKTYPE_ID)) {
		                Object linkTarget = link.getTargetRef().resolve();
		                if(linkTarget instanceof IWorkItemHandle) {
		                	// link is interesting
		            		links.put(link.getItemId(), link);
		                    toFetch.add((IWorkItemHandle)linkTarget);
		                }
		        	}
		        }
	        
		        // get the IWorkItem (small profile) for all links referencing them
				List<IWorkItem> workItemsFetched = fRepository.itemManager().fetchPartialItems(toFetch,
						IItemManager.DEFAULT, IWorkItem.SMALL_PROFILE.getProperties(), monitor.newChild(10));
				Map<UUID, IWorkItem> workItems = new HashMap<UUID, IWorkItem>();
				for (IWorkItem workItem : workItemsFetched) {
					if (workItem != null) {
						workItems.put(workItem.getItemId(), workItem);
					}
				}
		        
				// now populate the change report
				for (ChangeSetReport changeSetReport : changeSetReports) {
					IChangeSetLinkSummary changeSetLinkSummary = linkSummaryByChangeSet.get(UUID.valueOf(changeSetReport.getItemId()));
					if (changeSetLinkSummary != null) {
						for (ILinkHandle linkHandle : changeSetLinkSummary.getLinks()) {
							ILink link = links.get(linkHandle.getItemId());
							if (link != null) {
								Object target = link.getTargetRef().resolve();
								if (target instanceof IWorkItemHandle) {
									IWorkItem workItem = workItems.get(((IWorkItemHandle) target).getItemId());
									if (workItem != null) {
										ChangeSetReport.WorkItemEntry workItemEntry = new WorkItemEntry(workItem.getId(),
												workItem.getHTMLSummary().getPlainText());
										changeSetReport.addWorkItem(workItemEntry);
									} else {
										// TODO we could record the summary that is in the link instead...
										// Not sure if its a good idea as if it is null it is either deleted
										// or user doesn't have permission to see it
										LOGGER.finer("Unable to resolve work item summary " + changeSetLinkSummary.getSummary() //$NON-NLS-1$
												+ " for change set " + changeSetReport.getItemId()); //$NON-NLS-1$
									}
								}
							}
						}
					}
				}
        	}
        } catch (TeamRepositoryException e) {
        	listener.log(Messages.getDefault().ChangeReportBuilder_unable_to_get_work_items(e.getMessage()), e);
        	LOGGER.log(Level.FINER, "Unable to resolve work items " + e.getMessage(), e); //$NON-NLS-1$

        	if (Utils.isCancellation(e)) {
				throw e;
			}
        }
	}
}
