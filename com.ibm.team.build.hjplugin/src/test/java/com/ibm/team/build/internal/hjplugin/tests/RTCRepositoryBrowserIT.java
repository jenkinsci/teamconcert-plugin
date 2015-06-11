/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tests;

import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.ChangeDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogComponentEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCRepositoryBrowser;

public class RTCRepositoryBrowserIT  extends HudsonTestCase {
	
	private static final String WORK_ITEM_NUMBER = "2";
	private static final String BASELINE_SET_ITEMID = "_ds8LYMpYEeOwhrfCswB9SQ";
	private static final String CHANGE_SET_ITEMID = "_gDvyEMpVEeOwhrfCswB9SQ";
	private static final String VERSIONABLE_ITEMID = "_nsM4UF8sEeKKTc3S5sCXGg"; 
	private static final String VERSIONABLE_STATEID = "_gHzLpMpVEeOwhrfCswB9SQ";
	private static final String FILE_ITEM_TYPE = "FileItem";
	private static final String WORKSPACE_ITEMID = "_wMN34EfnEeOMnuakiRSrLA";
	private static final String COMPONENT_ITEMID = "_BouZwEVxEeOheafumOIpCA";
	private static final String COMPONENT2_ITEMID = "_gHzLq8pVEeOwhrfCswB9SQ";
	private static final String SERVER_URI = "https://localhost:9443/ccm";
	
	@Override
	public void setUp() throws Exception {
		// don't start Jenkins for faster tests
	}

	@Override
	protected void tearDown() throws Exception {
		// We didn't start Jenkins for faster tests
	}
	
	public void testTrailingSlash() throws Exception {
		RTCRepositoryBrowser browser = new RTCRepositoryBrowser(SERVER_URI + "/");
		exerciseBrowser(SERVER_URI, browser);
	}
	
	public void testNoTrailingSlash() throws Exception {
		RTCRepositoryBrowser browser = new RTCRepositoryBrowser(SERVER_URI);
		exerciseBrowser(SERVER_URI, browser);
	}
	
	public void testNullURI() throws Exception {
		RTCRepositoryBrowser browser = new RTCRepositoryBrowser(null);
		exerciseBrowser(browser);
	}
	
	public void testEmptyURI() throws Exception {
		RTCRepositoryBrowser browser = new RTCRepositoryBrowser("");
		exerciseBrowser(browser);
	}

	/**
	 * Tests that the proper links are generated for the browser
	 * @param serverURI Server URI browser should generate links for
	 * @param browser The browser to test
	 * @throws Exception If the test fails
	 */
	private void exerciseBrowser(String serverURI, RTCRepositoryBrowser browser) throws Exception {
		RTCChangeLogSet changeLogSet = setupChangeLogSet();
		
		assertEquals(serverURI + "/resource/itemOid/com.ibm.team.scm.BaselineSet/" + BASELINE_SET_ITEMID, browser.getBaselineSetLink(changeLogSet).toString());
		
		changeLogSet.setBaselineSetItemId(null);
		assertEquals(null, browser.getBaselineSetLink(changeLogSet));
		
		RTCChangeLogComponentEntry componentEntry = changeLogSet.getComponentChanges().get(0);
		RTCChangeLogChangeSetEntry changeSetEntry = changeLogSet.getChangeSetsAccepted(COMPONENT_ITEMID).get(0);
		WorkItemDesc workItem = changeSetEntry.getWorkItem();
		ChangeDesc change = changeSetEntry.getAffectedVersionables().get(0);
		
		assertEquals(serverURI + "/resource/itemName/com.ibm.team.workitem.WorkItem/" + WORK_ITEM_NUMBER , browser.getWorkItemLink(workItem).toString());
		assertEquals(serverURI + "/resource/itemOid/com.ibm.team.scm.ChangeSet/" + CHANGE_SET_ITEMID, browser.getChangeSetLink(changeSetEntry).toString());

		changeLogSet.setWorkspaceItemId(WORKSPACE_ITEMID);
		assertEquals(serverURI + "/resource/itemOid/com.ibm.team.scm.ChangeSet/" + CHANGE_SET_ITEMID + "?workspace=" + WORKSPACE_ITEMID, browser.getChangeSetLink(changeSetEntry).toString());

		assertEquals(null, browser.getChangeSetLink(componentEntry));
		
		assertEquals(serverURI + "/resource/itemOid/com.ibm.team.scm.Versionable/" + VERSIONABLE_ITEMID + "/" + VERSIONABLE_STATEID
			+ "?workspace=" + WORKSPACE_ITEMID + "&component=" + COMPONENT_ITEMID, browser.getVersionableStateLink(changeSetEntry, change).toString());
		
		change.setItemType("Folder");
		assertEquals(null, browser.getVersionableStateLink(changeSetEntry, change));
	}

	/**
	 * Tests that null is the value of the links when the browser has no server uri
	 * @param browser The browser to test
	 * @throws Exception If the tests fails
	 */
	private void exerciseBrowser(RTCRepositoryBrowser browser) throws Exception {
		RTCChangeLogSet changeLogSet = setupChangeLogSet();
		
		assertEquals(null, browser.getBaselineSetLink(changeLogSet));
		
		RTCChangeLogComponentEntry componentEntry = changeLogSet.getComponentChanges().get(0);
		RTCChangeLogChangeSetEntry changeSetEntry = changeLogSet.getChangeSetsAccepted(COMPONENT_ITEMID).get(0);
		WorkItemDesc workItem = changeSetEntry.getWorkItem();
		ChangeDesc change = changeSetEntry.getAffectedVersionables().get(0);
		
		assertEquals(null , browser.getWorkItemLink(workItem));
		assertEquals(null, browser.getChangeSetLink(changeSetEntry));
		assertEquals(null, browser.getChangeSetLink(componentEntry));
		assertEquals(null, browser.getVersionableStateLink(changeSetEntry, change));
	}

	/**
	 * @return A simple ChangeLog structure with a discarded change set (with a file change and a work item),
	 *  a component change and a baseline set.
	 */
	private RTCChangeLogSet setupChangeLogSet() {
		RTCChangeLogSet changeLogSet = new RTCChangeLogSet(null, null);
		changeLogSet.setBaselineSetItemId(BASELINE_SET_ITEMID);
		
		RTCChangeLogChangeSetEntry.WorkItemDesc workItem = new WorkItemDesc();
		workItem.setNumber(WORK_ITEM_NUMBER);

		RTCChangeLogChangeSetEntry changeSetEntry = new RTCChangeLogChangeSetEntry();
		changeSetEntry.setChangeSetItemId(CHANGE_SET_ITEMID);
		changeSetEntry.setComponentItemId(COMPONENT_ITEMID);
		changeSetEntry.addWorkItem(workItem);
		changeSetEntry.setAction("Added");
		changeSetEntry.setParent(changeLogSet);
		changeLogSet.add(changeSetEntry);
		
		ChangeDesc change = new ChangeDesc();
		change.setItemId(VERSIONABLE_ITEMID);
		change.setItemType(FILE_ITEM_TYPE);
		change.setStateId(VERSIONABLE_STATEID);
		changeSetEntry.addChange(change);

		RTCChangeLogComponentEntry componentEntry = new RTCChangeLogComponentEntry();
		componentEntry.setItemId(COMPONENT2_ITEMID);
		componentEntry.setParent(changeLogSet);
		changeLogSet.add(componentEntry);
		
		return changeLogSet;
	}
}
