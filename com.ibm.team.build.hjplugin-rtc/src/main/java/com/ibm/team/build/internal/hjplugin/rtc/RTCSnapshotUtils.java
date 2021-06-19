/*******************************************************************************
 * Copyright (c) 2016, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.ItemManager;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.dto.IBaselineSetSearchCriteria;

/**
 * Provides utility methods to retrieve information about a RTC SCM snapshot
 */
public final class RTCSnapshotUtils {
	
	private static final Logger LOGGER = Logger.getLogger(RTCSnapshotUtils.class.getName());
	
	// constants for the possible values for snapshot owner type 
	public static final String SNAPSHOT_OWNER_TYPE_STREAM = "stream"; //$NON-NLS-1$
	public static final String SNAPSHOT_OWNER_TYPE_WORKSPACE = "workspace"; //$NON-NLS-1$	
	public static final String SNAPSHOT_OWNER_TYPE_NONE = "- none -"; //$NON-NLS-1$
	
	// constants for the keys that identify various fields in the snapshot context map
	public static final String SNAPSHOT_OWNER_TYPE_KEY = "snapshotOwnerType"; //$NON-NLS-1$
	public static final String PROCESS_AREA_OF_OWNING_STREAM_KEY = "processAreaOfOwningStream"; //$NON-NLS-1$
	public static final String OWNING_STREAM_KEY = "owningStream"; //$NON-NLS-1$
	public static final String OWNING_WORKSPACE_KEY = "owningWorkspace"; //$NON-NLS-1$
	
	/**
	 * Class defining the snapshot context configuration data.
	 * 
	 * Snapshot context is a complex datastructure. Since complex datastructures cannot be shared through a data model
	 * between hjplugin.jar and hjplugin-rtc.jar, the complex data structure is converted to a map of fields and values;
	 * this map, that is sent by hjplugin.jar, is read and converted to a object by hjplugin-rtc.jar. Though this is
	 * hacky, it is better than passing individual fields. This class has to be retained until we design a permanent
	 * solution to pass complex data structures between the two jars.
	 */
	public static class BuildSnapshotContext {
		public String snapshotOwnerType;
		public String processAreaOfOwningStream;
		public String owningStream;
		public String owningWorkspace;

		public BuildSnapshotContext(Map<String, String> buildSnapshotContextMap) {
			if (buildSnapshotContextMap != null) {
				if (LOGGER.isLoggable(Level.FINEST)) {
					LOGGER.finest("BuildSnapshotContext: Configured snapshotOwnerType: " + buildSnapshotContextMap.get(SNAPSHOT_OWNER_TYPE_KEY)); //$NON-NLS-1$
					LOGGER.finest("BuildSnapshotContext: Configured processAreaOfOwningStream: " + buildSnapshotContextMap.get(PROCESS_AREA_OF_OWNING_STREAM_KEY)); //$NON-NLS-1$
					LOGGER.finest("BuildSnapshotContext: Configured owningStream: " + buildSnapshotContextMap.get(OWNING_STREAM_KEY)); //$NON-NLS-1$
					LOGGER.finest("BuildSnapshotContext: Configured owningWorkspace: " + buildSnapshotContextMap.get(OWNING_WORKSPACE_KEY)); //$NON-NLS-1$
				}
				this.snapshotOwnerType = Utils.fixEmptyAndTrim(buildSnapshotContextMap.get(SNAPSHOT_OWNER_TYPE_KEY));
				this.processAreaOfOwningStream = Utils.fixEmptyAndTrim(buildSnapshotContextMap.get(PROCESS_AREA_OF_OWNING_STREAM_KEY));
				this.owningStream = Utils.fixEmptyAndTrim(buildSnapshotContextMap.get(OWNING_STREAM_KEY));
				this.owningWorkspace = Utils.fixEmptyAndTrim(buildSnapshotContextMap.get(OWNING_WORKSPACE_KEY));
			} else {
				// if the context map is null, set the owner type to "none"
				this.snapshotOwnerType = SNAPSHOT_OWNER_TYPE_NONE;
			}
		}

		private BuildSnapshotContext() {
			this.snapshotOwnerType = SNAPSHOT_OWNER_TYPE_NONE;
		}

		public static BuildSnapshotContext fixNullReference(BuildSnapshotContext buildSnapshotContext) {
			if (buildSnapshotContext != null) {
				return buildSnapshotContext;
			}
			// null reference is converted to an object with snapshot owner "none" for easy handling in the code
			return new BuildSnapshotContext();
		}

		public boolean isSnapshotOwnedByWorkspace() {
			return SNAPSHOT_OWNER_TYPE_WORKSPACE.equals(this.snapshotOwnerType);
		}

		public boolean isSnapshotOwnedByStream() {
			return SNAPSHOT_OWNER_TYPE_STREAM.equals(this.snapshotOwnerType);
		}
	}

	/**
	 * Given a snapshot name, return a {@link IBaselineSet} if there is a valid snapshot.
	 * 
	 * When a snaphot owner is provided, snapshot search is scoped depending upon the provided details.
	 * 
	 * The snapshot owner can be set to "none", "workspace", or "stream".
	 * 
	 * Note: Any change in the behavior of snaphot search has to be updated here as well in help-buildSnapshotContext.html
	 * 
	 * <pre>
	 * 	<ul>
	 * 		<li>
	 * 			When the snapshot owner is set to "none", the snapshot search is not scoped and happens across the repository.
	 * 		</li>
	 * 		<li>
	 * 			When the snapshot owner is selected to be a repository workspace but the name of the repository workspace is not
	 * 			specified, then the snapshot search is not scoped and happens across the repository.
	 * 		</li>
	 *  	<li>
	 *  		When the snapshot owner is selected to be a stream but neither the project or team area nor the owning stream 
	 *  		is specified, then the snapshot search is not scoped and happens across the repository.
	 * 		</li>
	 * 		<li>
	 * 			When the snapshot owner is selected to be a stream and only the project or team area name is specified, then the 
	 * 			snapshot search is scoped to the snapshots associated with all streams owned by the specified project or team area.
	 * 		</li> 
	 * 		<li>
	 * 			When the snapshot owner is selected to be a stream and only the stream name is specified, then the snapshot search 
	 * 		 	is scoped to the snapshots associated with all streams with the given name across the repository. 
	 * 		</li>
	 * 		<li>
	 * 			When the snapshot owner is selected to be a stream and both the project or team area and stream names are specified, 
	 * 			then the snapshot search is scoped to the snapshots associated with all streams with the given name in the specified project or team area.
	 * 		</li>
	 * </ul>
	 * </pre>
	 * 
	 * @param repository
	 * @param Object containing the snapshot owner details
	 * @param snapshotName
	 * @param progress
	 * @param clientLocale
	 * @return a {@link IBaselineSet} if there is a valid snapshot or <code>null</code>
	 * @throws TeamRepositoryException
	 * @throws RTCConfigurationException
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 */
	public static IBaselineSet getSnapshotByName(ITeamRepository repository, BuildSnapshotContext buildSnapshotContext, 
				String snapshotName, IProgressMonitor progress, Locale clientLocale) 
				throws TeamRepositoryException, RTCConfigurationException, 
				URISyntaxException, UnsupportedEncodingException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);

		IBaselineSetSearchCriteria criteria = IBaselineSetSearchCriteria.FACTORY.newInstance().setExactName(snapshotName);
		buildSnapshotContext = BuildSnapshotContext.fixNullReference(buildSnapshotContext);
		IProcessArea processArea = null;
		if (buildSnapshotContext.isSnapshotOwnedByWorkspace()) {
			// though the snapshot owner is set to "workspace", the workspace name can still be null or blank
			if (buildSnapshotContext.owningWorkspace != null) {
				monitor.setWorkRemaining(150);
				criteria = criteria.setOwnerWorkspaceOptional(RTCWorkspaceUtils.getInstance().getWorkspace(buildSnapshotContext.owningWorkspace,
						repository, monitor.newChild(50), clientLocale));
			}
		} else if (buildSnapshotContext.isSnapshotOwnedByStream()) {
			// see if a project or team area is provided
			if (buildSnapshotContext.processAreaOfOwningStream != null) {
				monitor.setWorkRemaining(200);
				processArea = RTCWorkspaceUtils.getInstance().getProcessAreaByName(buildSnapshotContext.processAreaOfOwningStream, repository,
						monitor.newChild(50), clientLocale);
				criteria = criteria.setProcessArea(processArea);
			}
			// though the snapshot owner is set to "stream", the stream name can still be null or blank
			if (buildSnapshotContext.owningStream != null) {
				monitor.setWorkRemaining(150);
				criteria = criteria.setOwnerWorkspaceOptional(RTCWorkspaceUtils.getInstance().getStream(
						buildSnapshotContext.processAreaOfOwningStream, processArea, buildSnapshotContext.owningStream, repository,
						monitor.newChild(50), clientLocale));
			}
		}
		monitor.setWorkRemaining(100);
		// Run the query
		@SuppressWarnings("unchecked")
		List<IBaselineSetHandle> baselineSetHandles = (List<IBaselineSetHandle>)SCMPlatform.getWorkspaceManager(repository).findBaselineSets(
				criteria, 2, monitor.newChild(100));

		if (baselineSetHandles.size() > 1) {
			handleSnapshotNameNotUnique(buildSnapshotContext, buildSnapshotContext.processAreaOfOwningStream, processArea, snapshotName, clientLocale);
		}

		if (baselineSetHandles.size() == 0) {
			handleSnapshotNotFound(buildSnapshotContext, buildSnapshotContext.processAreaOfOwningStream, processArea, snapshotName, clientLocale);
		}
		return getSnapshotByUUID(repository, baselineSetHandles.get(0).getItemId(), progress, clientLocale);
	}

	private static void handleSnapshotNameNotUnique(BuildSnapshotContext buildSnapshotContext, String processAreaPath, IProcessArea processArea,
			String snapshotName, Locale clientLocale) throws RTCConfigurationException {
		if (buildSnapshotContext.isSnapshotOwnedByWorkspace() && buildSnapshotContext.owningWorkspace != null) {
			// snapshot owned by workspace and workspace name is specified
			// more than one snapshot with the given name is associated with this workspace
			throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique_ws(snapshotName,
					buildSnapshotContext.owningWorkspace));
		} else if (buildSnapshotContext.isSnapshotOwnedByStream()
				&& (buildSnapshotContext.owningStream != null || buildSnapshotContext.processAreaOfOwningStream != null)) {
			// snapshot owned by stream and either or both the stream name and process area name is specified
			if (buildSnapshotContext.processAreaOfOwningStream != null) {
				// process area name is specified
				if (processArea instanceof IProjectArea) {
					// process area is a project area
					if (buildSnapshotContext.owningStream != null) {
						// project area and stream are specified
						// more than one snapshot with the given name is associated with this stream in this project
						// area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique_st_pa(snapshotName,
								buildSnapshotContext.owningStream, processAreaPath));
					} else {
						// only project area is specified and stream is not specified
						// more than one snapshot with the given name is associated with the streams in this project
						// area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique_pa(snapshotName,
								processAreaPath));
					}
				} else {
					// process area is a team area
					if (buildSnapshotContext.owningStream != null) {
						// team area and stream are specified
						// more than one snapshot with the given name is associated with this stream in this team area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique_st_ta(snapshotName,
								buildSnapshotContext.owningStream, processAreaPath));
					} else {
						// only team area is specified and stream is not specified
						// more than one snapshot with the given name is associated with the streams in this team area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique_ta(snapshotName,
								processAreaPath));
					}
				}
			}
			if (buildSnapshotContext.owningStream != null) {
				// stream is specified without the assoicated process area
				// more than one snapshot with the given name is associated with this stream
				throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique_st(snapshotName,
						buildSnapshotContext.owningStream));
			}
		} else {
			// no context is specified
			// more than one snapshot with the given name is found in the repository
			throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_name_not_unique(snapshotName));
		}
	}

	private static void handleSnapshotNotFound(BuildSnapshotContext buildSnapshotContext, String processAreaPath, IProcessArea processArea,
			String snapshotName, Locale clientLocale) throws RTCConfigurationException {
		if (buildSnapshotContext.isSnapshotOwnedByWorkspace() && buildSnapshotContext.owningWorkspace != null) {
			// snapshot owned by workspace and workspace name is specified
			// snapshot not associated with this workspace
			throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found_ws(snapshotName,
					buildSnapshotContext.owningWorkspace));
		} else if (buildSnapshotContext.isSnapshotOwnedByStream()
				&& (buildSnapshotContext.owningStream != null || buildSnapshotContext.processAreaOfOwningStream != null)) {
			// snapshot owned by stream and either or both the stream name and process area name is specified
			if (buildSnapshotContext.processAreaOfOwningStream != null) {
				// process area name is specified
				if (processArea instanceof IProjectArea) {
					// process area is a project area
					if (buildSnapshotContext.owningStream != null) {
						// project area and stream are specified
						// snapshot not associated with this stream in this project area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found_st_pa(snapshotName,
								buildSnapshotContext.owningStream, processAreaPath));
					} else {
						// only project area is specified and stream is not specified
						// snapshot not associated with any of the streams in this project area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found_pa(snapshotName,
								processAreaPath));
					}
				} else {
					// process area is a team area
					if (buildSnapshotContext.owningStream != null) {
						// team area and stream are specified
						// snapshot not associated with this stream in this team area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found_st_ta(snapshotName,
								buildSnapshotContext.owningStream, processAreaPath));
					} else {
						// only team area is specified and stream is not specified
						// snapshot not associated with any of the streams in this team area
						throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found_ta(snapshotName,
								processAreaPath));
					}
				}
			}
			if (buildSnapshotContext.owningStream != null) {
				// stream is specified without the assoicated process area
				// snapshot not associated with this stream
				throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found_st(snapshotName,
						buildSnapshotContext.owningStream));
			}
		} else {
			// no context is specified
			// snapshot not found in the repository
			throw new RTCConfigurationException(Messages.get(clientLocale).RTCSnapshotUtils_snapshot_not_found(snapshotName));
		}
	}

	/**
	 * Given a snapshotUUID as {@link String}, return a {@link IBaselineSet} if there is a valid snapshot
	 * 
	 * @param repository                  An instance of {@link ITeamRepository}
	 * @param snapshotUUID                UUID of the snapshot
	 * @param listener                    A stream to output messages to. These messages will be output 
	 *                                    to the user.
	 * @param clientLocale                Locale in which messages should be formatted
	 * @return                            a {@link IBaselineSet} if there is a valid snapshot.
	 * @throws TeamRepositoryException    If the snapshot does not exist or if there is any other issue communicating
	 *                                    with the EWM server.
	 */
	public static IBaselineSet getSnapshotByUUID(ITeamRepository repository, String snapshotUUID, 
									IProgressMonitor progress, Locale clientLocale)	throws TeamRepositoryException {
		UUID buildSnapshotUUID = UUID.valueOf(snapshotUUID);
		return getSnapshotByUUID(repository, buildSnapshotUUID, progress, clientLocale);
	}

	/**
	 * Given a snapshotUUID or name, get the {@link IBaselineSet} for a valid snapshot
	 * 
	 * @param repository
	 * @param buildSnapshotContext Object containing the snapshot owner details. Applicable only when the name of the
	 *            the snapshot is provided. For lookup by UUID owner details are not required.
	 * @param snapshotUUIDOrName
	 * @param progress
	 * @param clientLocale
	 * @return a {@link IBaselineSet} if there is a valid snapshot <code>null</code>
	 * @throws TeamRepositoryException
	 * @throws RTCConfigurationException
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 */
	public static IBaselineSet getSnapshot(ITeamRepository repository, BuildSnapshotContext buildSnapshotContext, 
			String snapshotUUIDOrName, IProgressMonitor progress, Locale clientLocale) 
					throws TeamRepositoryException, RTCConfigurationException, 
					       URISyntaxException, UnsupportedEncodingException {
		try {
			UUID buildSnapshotUUID = UUID.valueOf(snapshotUUIDOrName);
			return getSnapshotByUUID(repository, buildSnapshotUUID, progress, clientLocale);
		} catch (IllegalArgumentException exp) {
			// the argument is a name
			return getSnapshotByName(repository, buildSnapshotContext, snapshotUUIDOrName, progress, clientLocale);
		}
	}

	/**
	* Given a snapshot UUID as {@link UUID}, return a {@link IBaselineSet} if there is a valid snapshot
	* @param repository
	* @param snapshotUUID
	* @param progress
	* @param clientLocale
	* @return a {@link IBaselineSet} if there is a valid snapshot or <code>null</code>
	* @throws TeamRepositoryException
	*/
	private static IBaselineSet getSnapshotByUUID(ITeamRepository repository, 
					UUID snapshotUUID, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		try {
			IItemHandle itemHandle = IBaselineSet.ITEM_TYPE.createItemHandle(snapshotUUID, null);
			IBaselineSet baselineSet = (IBaselineSet) repository.itemManager().fetchCompleteItem(itemHandle, 
											ItemManager.REFRESH, monitor);
			return baselineSet;
		} finally {
			monitor.done();
		}
	}
}
