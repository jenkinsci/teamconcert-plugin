/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

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

    /**
     * Given a snapshot name, return a {@link IBaselineSet} if there is a valid snapshot
     * @param repository
     * @param snapshotName
     * @param progress
     * @param clientLocale
     * @return a {@link IBaselineSet} if there is a valid snapshot or <code>null</code>
     * @throws Exception
     */
    public static IBaselineSet getSnapshotByName(ITeamRepository repository, String snapshotName, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);

	 	IBaselineSetSearchCriteria criteria = IBaselineSetSearchCriteria.FACTORY.newInstance().setExactName(snapshotName);
        // Run the query
        @SuppressWarnings("unchecked")
		List<IBaselineSetHandle> baselineSetHandles = (List<IBaselineSetHandle>) SCMPlatform.getWorkspaceManager(repository).findBaselineSets(criteria, 2, monitor);
        
        if (baselineSetHandles.size() > 1) {
        	throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_snapshot_name_not_unique(snapshotName));
        }

        if (baselineSetHandles.size() == 0) {
        	throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_snapshot_not_found(snapshotName));
        }
        return getSnapshotByUUID(repository, baselineSetHandles.get(0).getItemId(), progress, clientLocale);
    }
    
    /**
     * Given a snapshotUUID as {@link String} , return a {@link IBaselineSet} if there is a valid snapshot
     * @param repository
     * @param snapshotUUID
     * @param progress
     * @param clientLocale
     * @return a {@link IBaselineSet} if there is a valid snapshot or <code>null</code>
     * @throws Exception
     */
	public static IBaselineSet getSnapshotByUUID(ITeamRepository repository, String snapshotUUID, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException {
		UUID buildSnapshotUUID = UUID.valueOf(snapshotUUID);
		return getSnapshotByUUID(repository, buildSnapshotUUID, progress, clientLocale);
	}
	
	/**
	 * Given a snapshotUUID or name, get  the {@link IBaselineSet} for a valid snapshot
	 * @param repository
	 * @param snapshotUUIDOrName
	 * @param progress
	 * @param clientLocale
	 * @return a {@link IBaselineSet} if there is a valid snapshot <code>null</code>
	 * @throws Exception
	 */
	public static IBaselineSet getSnapshot(ITeamRepository repository, String snapshotUUIDOrName, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException {
		try {
			UUID buildSnapshotUUID = UUID.valueOf(snapshotUUIDOrName);
			return getSnapshotByUUID(repository, buildSnapshotUUID, progress, clientLocale);
		} catch (IllegalArgumentException exp) {
			// the argument is a name
			return getSnapshotByName(repository, snapshotUUIDOrName, progress, clientLocale);
		}
	}
	
	/**
	* Given a snapshot UUID as {@link UUID}, return a {@link IBaselineSet} if there is a valid snapshot
	* @param repository
	* @param snapshotUUID
	* @param progress
	* @param clientLocale
	* @return a {@link IBaselineSet} if there is a valid snapshot or <code>null</code>
	* @throws Exception
	*/
	private static IBaselineSet getSnapshotByUUID(ITeamRepository repository, UUID snapshotUUID, IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IItemHandle itemHandle = IBaselineSet.ITEM_TYPE.createItemHandle(snapshotUUID, null);
		IBaselineSet baselineSet = (IBaselineSet) repository.itemManager().fetchCompleteItem(itemHandle, ItemManager.REFRESH, monitor);
		return baselineSet;
	}
}
