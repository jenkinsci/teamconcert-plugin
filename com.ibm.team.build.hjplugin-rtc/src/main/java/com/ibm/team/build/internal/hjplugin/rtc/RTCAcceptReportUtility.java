/*******************************************************************************
 * Copyright (c) 2015, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.HashSet;
import java.util.Set;

import com.ibm.team.build.internal.scm.AcceptReport;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponentHandle;

/**
 * Utility class over AcceptReport, that offers: 
 * - hash computation on an AcceptReport, for easy comparison with other AcceptReports.
 *
 */
public class RTCAcceptReportUtility {
	
	/**
	 * This method reverses a given string
	 * @param original string to be reversed
	 * @return a reversed string
	 */
	private static String reverse(String original) {
		return new StringBuffer(original).reverse().toString();
	}
	
	/**
	 * Use (added / deleted) components, and (accepted / discarded) changesets to
	 * compute hash value.
	 * In order to differentiate between a component (or changeset) getting 
	 * added (accepted) v/s deleted (discarded), reverse the uuid value if an item is
	 * deleted (discarded). 
	 */
	public static int hashCode(AcceptReport acceptReport, boolean ignoreOutgoingFromBuildWorkspace) {
		Set<String> uuidSet = new HashSet<String>();
		for (IComponentHandle addedComponents : acceptReport.getComponentAdds()) {
			uuidSet.add(addedComponents.getItemId().getUuidValue());
		}
		
		for (IComponentHandle removedComponents : acceptReport.getComponentRemovals()) {
			uuidSet.add(reverse(removedComponents.getItemId().getUuidValue()));
		}
		
		for (IChangeSetHandle acceptChangeSet : acceptReport.getAcceptChangeSets()) {
			uuidSet.add(acceptChangeSet.getItemId().getUuidValue());
		}
		
		if (!ignoreOutgoingFromBuildWorkspace) {
			for (IChangeSetHandle discardChangeSet : acceptReport.getDiscardChangeSets()) {
				uuidSet.add(reverse(discardChangeSet.getItemId().getUuidValue()));
			}
		}
		return uuidSet.hashCode(); 
	}	
}
