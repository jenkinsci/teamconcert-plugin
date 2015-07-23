/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ibm.team.build.internal.hjplugin.rtc.RTCAcceptReportUtility;
import com.ibm.team.build.internal.scm.AcceptReport;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;

/**
 * Test cases to ensure our usage of HashSet remains valid
 * in RTCAcceptReportUtility
 *
 */
public class RTCAcceptReportUtilityTests {
	
	private IChangeSetHandle createChangeSetHandle(UUID uuid) {
		return (IChangeSetHandle) IChangeSet.ITEM_TYPE.createItemHandle(uuid, null);
	}
	
	private List createChangeSetHandles(int nCount) {
		List res = new ArrayList();
		for (int i = 0; i < nCount; i++) {
			res.add(createChangeSetHandle(UUID.generate()));
		}
		return res;
	}
	
	private AcceptReport setListField(AcceptReport acceptReport, String fieldName, Object value) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field field = acceptReport.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(acceptReport, value);
		
		return acceptReport;
	}
	
	private IComponentHandle createComponentHandle(UUID uuid) {
		return (IComponentHandle) IComponent.ITEM_TYPE.createItemHandle(uuid, null);
	}
	
	private List createComponentHandles(int nCount) {
		List res = new ArrayList();
		for (int i = 0; i < nCount; i++) {
			res.add(createComponentHandle(UUID.generate()));
		}
		return res;
	}
	
	private AcceptReport createAcceptReport(List acceptedChangeSets, List discardedChangeSets, Collection componentsAdded, Collection componentsRemoved) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		AcceptReport acceptReport = new AcceptReport();
		acceptReport = setListField(acceptReport, "fAcceptChangeSets", acceptedChangeSets);
		acceptReport = setListField(acceptReport, "fDiscardChangeSets", discardedChangeSets);
		acceptReport = setListField(acceptReport, "fComponentAdds", componentsAdded);
		acceptReport = setListField(acceptReport, "fComponentRemovals", componentsRemoved);
		
		return acceptReport;
	}
	
	public void testMatchingAcceptReports() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles1 = createChangeSetHandles(4); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles1 = createComponentHandles(4);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertEquals(hash1, hash2);
	}

	public void testAcceptReportsWithDifferentAcceptedChangesets() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles1 = createChangeSetHandles(4); 
		List acceptedChangeSetHandles2 = createChangeSetHandles(3); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles1 = createComponentHandles(4);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						acceptedChangeSetHandles2,
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertFalse(hash1 == hash2, "Hash codes are equal where they are expected to be unequal");
	}

	public void testAcceptReportsWithDifferentDiscardedChangesets() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles1 = createChangeSetHandles(4); 
		List discardedChangeSetHandles2 = createChangeSetHandles(3); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles1 = createComponentHandles(4);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						acceptedChangeSetHandles1,
						discardedChangeSetHandles2, 
						addedComponentHandles1,
						discardedComponentHandles1);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertFalse(hash1 == hash2, "Hash codes are equal where they are expected to be unequal");
	}

	public void testAcceptReportsWithDifferentAcceptedComponents() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles1 = createChangeSetHandles(3); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection addedComponentHandles2 = createComponentHandles(4);
		Collection discardedComponentHandles1 = createComponentHandles(3);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles2,
						discardedComponentHandles1);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertFalse(hash1 == hash2, "Hash codes are equal where they are expected to be unequal");
	}

	public void testAcceptReportsWithDifferentRemovedComponents() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles1 = createChangeSetHandles(3); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles2 = createComponentHandles(4);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles2);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertFalse(hash1 == hash2, "Hash codes are equal where they are expected to be unequal");
	}

	public void testAcceptReportsWithOppositeChangesets() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles1 = createChangeSetHandles(4); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles1 = createComponentHandles(4);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						discardedChangeSetHandles1,
						acceptedChangeSetHandles1,
						addedComponentHandles1,
						discardedComponentHandles1);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertFalse(hash1 == hash2, "Hash codes are equal where they are expected to be unequal");
	}

	public void testNonMatchingAcceptReports() throws Exception {
		List acceptedChangeSetHandles1 = createChangeSetHandles(3); 
		List acceptedChangeSetHandles2 = createChangeSetHandles(4); 
		List discardedChangeSetHandles1 = createChangeSetHandles(3); 
		List discardedChangeSetHandles2 = createChangeSetHandles(4); 
		Collection addedComponentHandles1 = createComponentHandles(3);
		Collection addedComponentHandles2 = createComponentHandles(4);
		Collection discardedComponentHandles1 = createComponentHandles(3);
		Collection discardedComponentHandles2 = createComponentHandles(4);
		AcceptReport acceptReport1 =
				createAcceptReport(
						acceptedChangeSetHandles1, 
						discardedChangeSetHandles1, 
						addedComponentHandles1,
						discardedComponentHandles1);
		AcceptReport acceptReport2 =
				createAcceptReport(
						acceptedChangeSetHandles2, 
						discardedChangeSetHandles2, 
						addedComponentHandles2,
						discardedComponentHandles2);

		Integer hash1 = RTCAcceptReportUtility.hashCode(acceptReport1);
		Integer hash2 = RTCAcceptReportUtility.hashCode(acceptReport2);

		AssertUtil.assertFalse(hash1 == hash2, "Hash codes are equal where they are expected to be unequal");
	}
}
