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

package com.ibm.team.build.internal.hjplugin.tests;

import hudson.Util;
import hudson.scm.EditType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogComponentEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogParser;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSet.ComponentDescriptor;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogSetEntry;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.ChangeDesc;
import com.ibm.team.build.internal.hjplugin.RTCChangeLogChangeSetEntry.WorkItemDesc;

public class RTCChangeLogParserTest extends HudsonTestCase {

	private static final String EOL = System.getProperty("line.separator", "\n"); 
	
    @Test
	public void testBasic() throws Exception {
		Reader changeLog =  new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + EOL +
				"<changelog version=\"1\" baselineSetItemId=\"_u1CB8V-JEeKvo5cWqp-wYg\" baselineSetName=\"Jenkins Happy Build#44\" workspaceItemId=\"_cc-LYUhlEeK5tewUzhIIVw\">" + EOL +
				"    <component action=\"Added\" itemId=\"_9O6uoqjcEeGldOeoQAxwFA\" name=\"My favourite component\" />" + EOL +
				"    <changeset action=\"Added\" owner=\"heatherf\" date=\"1359389120942\" comment=\"my comment is really long from a UI perspective&#x0A;And spans 3 lines containing incredibly awesome essential&#x0A;information about the myriad of changes in the change set.\" changeSetItemId=\"_ojFTsVwQEeKvo5cWqp-wYg\" componentItemId=\"_K7ukIGPuEeKs7d1U683ZJg\" componentName=\"TestComponent1\">" + EOL +
				"        <changes>" + EOL +
				"            <change kind=\"2\" name=\"RTCFacade.java\" />" + EOL +
				"            <change kind=\"1\" name=\"RTCChangeLogParser.java\" />" + EOL +
				"            <change kind=\"0\" name=\"RTCBuildClient.java\" />" + EOL +
				"            <change kind=\"4\" name=\"RTCChangeLogSet.java\" />" + EOL +
				"            <change kind=\"8\" name=\"RTCChangeLogComponentEntry.java\" />" + EOL +
				"            <change kind=\"16\" name=\"RTCAlternateParser.java\" />" + EOL +
				"        </changes>" + EOL +
				"        <workItems>" + EOL +
				"            <workItem number=\"246737\" summary=\"HPI: Simple Hudson/Jenkins\" />" + EOL +
				"        </workItems>" + EOL +
				"    </changeset>" + EOL +
				"    <changeset action=\"Added\" owner=\"nedgar\" date=\"1359389181332\" comment=\"Add RTC project sources to Launch and it is magnificent\" changeSetItemId=\"_NIoxMVuWEeKvo5cWqp-wYg\" componentItemId=\"_K7ukIGPuEeKs7d1U683ZJg\" componentName=\"TestComponent1\">" + EOL +
				"        <changes>" + EOL +
				"            <change kind=\"2\" name=\"RTCFacade.java\" />" + EOL +
				"            <change kind=\"12\" name=\"RTCChangeLogSet2.java\" />" + EOL +
				"            <change kind=\"14\" name=\"RTCChangeLogComponentEntry2.java\" />" + EOL +
				"        </changes>" + EOL +
				"        <workItems>" + EOL +
				"            <workItem number=\"246335\" summary=\"As a developer of the Hudson/Jenkins plugin for RTC, I can compile and and run against the RTC client libraries\" />" + EOL +
				"            <workItem number=\"185990\" summary=\"Test Failure (RTC-Buildsystem-C20111122-1359): com.ibm.team.build.engine.tests.JazzScmPreBuildParticipantTests.testDeleteWithSymlinkExternalTarget\" />" + EOL +
				"            <workItem number=\"246737\" summary=\"HPI: Simple Hudson/Jenkins\" />" + EOL +
				"        </workItems>" + EOL +
				"    </changeset>" + EOL +
				"    <changeset action=\"Added\" owner=\"ADMIN\" date=\"1359131227571\" comment=\"\" changeSetItemId=\"_W8i8kUuCEeK5tewUzhIIVw\" componentItemId=\"_K7ukIGPuEeKs7d1U683ZJg\" componentName=\"TestComponent1\">" + EOL +
				"        <changes>" + EOL +
				"            <change kind=\"2\" name=\"pom.xml\" />" + EOL +
				"        </changes>" + EOL +
				"        <workItems>" + EOL +
				"            <workItem number=\"246737\" summary=\"HPI: Simple Hudson/Jenkins\"/>" + EOL +
				"        </workItems>" + EOL +
				"    </changeset>" + EOL +
				"    <changeset action=\"Dropped\" owner=\"ADMIN\" date=\"1359132327571\" comment=\"\" changeSetItemId=\"_xZS89ltAEeKvo5cWqp-wYg\" componentItemId=\"_K7ukIGPuEeKs7d1U683ZJg\" componentName=\"TestComponent1\">" + EOL +
				"        <changes>" + EOL +
				"            <change kind=\"2\" name=\"pom.xml\" />" + EOL +
				"        </changes>" + EOL +
				"    </changeset>" + EOL +
				"    <changeset action=\"Added\" owner=\"scowan\" date=\"1359129257571\" comment=\"share\" additionalChanges=\"2034\" changeSetItemId=\"_H7VJsUuOEeK5tewUzhIIVw\" componentItemId=\"_K7ukIGPuEeKs7d1U683ZJg\" componentName=\"TestComponent1\"/>" + EOL +
				"    <changeset action=\"Added\" owner=\"scowan\" date=\"1359131887571\" additionalChanges=\"129\" changeSetItemId=\"_AJnUgVpuEeKvo5cWqp-wYg\" componentItemId=\"_K7ukIGPuEeKs7d1U683ZJg\" componentName=\"TestComponent1\">" + EOL +
				"        <workItems>" + EOL +
				"            <workItem number=\"246335\" summary=\"As a developer of the Hudson/Jenkins plugin for RTC, I can compile and and run against the RTC client libraries\"/>" + EOL +
				"            <workItem number=\"185990\" summary=\"Test Failure (RTC-Buildsystem-C20111122-1359): com.ibm.team.build.engine.tests.JazzScmPreBuildParticipantTests.testDeleteWithSymlinkExternalTarget\" />" + EOL +
				"            <workItem number=\"246737\" summary=\"HPI: Simple Hudson/Jenkins\" />" + EOL +
				"        </workItems>" + EOL +
				"    </changeset>" + EOL +
				"    <component action=\"Dropped\" itemId=\"_jbCkEKjgEeGldOeoQAxwFA\" name=\"Bad bad component\" />" + EOL +
				"    <changeset action=\"Dropped\" owner=\"ADMIN\" date=\"1359123427571\" comment=\"\" changeSetItemId=\"_9PGm0WGiEeKvo5cWqp-wYg\" componentItemId=\"_yBCJIYXDEeKO4ogEo4hN1g\" componentName=\"TestComponent2\">" + EOL +
				"    </changeset>" + EOL +
				"</changelog>");
		
		RTCChangeLogParser parser = new RTCChangeLogParser();
		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLog);
		
		// use the iterator to collect all the items
		// compare against the other methods that return the categorized items
		// make sure we have paths, workitems, first work items, etc.
		Assert.assertEquals("baselineSetItemId", "_u1CB8V-JEeKvo5cWqp-wYg", result.getBaselineSetItemId());
		Assert.assertEquals("baselineSetName", "Jenkins Happy Build#44", result.getBaselineSetName());
		Set<RTCChangeLogComponentEntry> componentEntries = new HashSet<RTCChangeLogComponentEntry>();
		Set<RTCChangeLogChangeSetEntry> acceptedEntries = new HashSet<RTCChangeLogChangeSetEntry>();
		Set<RTCChangeLogChangeSetEntry> discardedEntries = new HashSet<RTCChangeLogChangeSetEntry>();
		
		Iterator<RTCChangeLogSetEntry> i = result.iterator();
		int count = 0;
		for (;i.hasNext();) {
			RTCChangeLogSetEntry change=i.next(); 
			count++;
			if (change instanceof RTCChangeLogComponentEntry) {
				RTCChangeLogComponentEntry componentEntry = (RTCChangeLogComponentEntry) change;
				Assert.assertEquals(0, componentEntry.getAffectedPaths().size());
				componentEntries.add(componentEntry);
				String message = componentEntry.getMsg();
				if (message.contains("Added")) {
					Assert.assertTrue(message, message.contains("My favourite component"));
				} else if (message.contains("Dropped")) {
					Assert.assertTrue(message, message.contains("Bad bad component"));
				} else {
					Assert.fail(message);
				}
			} else if (change instanceof RTCChangeLogChangeSetEntry) {
				RTCChangeLogChangeSetEntry changeSetEntry = (RTCChangeLogChangeSetEntry) change;
				if (changeSetEntry.isAccept()) {
					acceptedEntries.add(changeSetEntry);
				} else {
					discardedEntries.add(changeSetEntry);
				}
			} else {
				Assert.fail("unexpected change entry " + change.getClass().getName());
			}
				
			if (count > 256) break; // paranoia check
		}
		Assert.assertEquals("Number of changes in change log", 9, count);
		Assert.assertEquals("Number of changes to components", 2, componentEntries.size());
		Assert.assertEquals("Number of discarded change sets", 2, discardedEntries.size());
		Assert.assertEquals(discardedEntries.size(), result.getChangeSetsDiscardedCount());
		Assert.assertEquals("Number of accepted change sets", 5, acceptedEntries.size());
		Assert.assertEquals(acceptedEntries.size(), result.getChangeSetsAcceptedCount());
		Assert.assertEquals("Affected components",  2, result.getAffectedComponents().size());
		
		// affected components are sorted by name so this should always work out
		Iterator<ComponentDescriptor> iter = result.getAffectedComponents().iterator();
		ComponentDescriptor affectedComponent1 = iter.next();
		ComponentDescriptor affectedComponent2 = iter.next();
		Assert.assertTrue(affectedComponent1.getName() + " " + affectedComponent2.getName(), affectedComponent2.getName().compareTo(affectedComponent1.getName()) > 0);
		assertEquals(5, result.getChangeSetsAccepted(affectedComponent1.getItemId()).size());
		assertEquals(0, result.getChangeSetsAccepted(affectedComponent2.getItemId()).size());
		assertEquals(1, result.getChangeSetsDiscarded(affectedComponent1.getItemId()).size());
		assertEquals(1, result.getChangeSetsDiscarded(affectedComponent2.getItemId()).size());
		
		for (RTCChangeLogChangeSetEntry changeSetEntry : acceptedEntries) {
			String changeSetItemId = changeSetEntry.getChangeSetItemId();
			if (changeSetItemId.equals("_ojFTsVwQEeKvo5cWqp-wYg")) {
				Assert.assertEquals("author", "heatherf", changeSetEntry.getOwner());
				Assert.assertEquals("comment", "my comment is really long from a UI perspective\nAnd spans 3 lines containing incredibly awesome essential\ninformation about the myriad of changes in the change set.", changeSetEntry.getComment());
				Assert.assertEquals("Change count for " + changeSetItemId, 6, changeSetEntry.getAffectedPaths().size());
				Assert.assertTrue("change set date missmatch expected 2013-01-28 was " + changeSetEntry.getChangeSetModDate(), changeSetEntry.getChangeSetModDate().startsWith("2013-01-28"));
				Assert.assertEquals("Versionable change count for " + changeSetItemId, 6, changeSetEntry.getAffectedVersionables().size());
				Assert.assertEquals("Work item count " + changeSetItemId, 0, changeSetEntry.getAdditionalWorkItems().size());
				WorkItemDesc workItem = changeSetEntry.getWorkItem();
				Assert.assertEquals("Work item Number", "246737", workItem.getNumber());
				Assert.assertEquals("Work item summary", "HPI: Simple Hudson/Jenkins", workItem.getSummary());
				Assert.assertEquals("Additional work items",  0, changeSetEntry.getAdditionalWorkItems().size());
			} else if (changeSetItemId.equals("_NIoxMVuWEeKvo5cWqp-wYg")) {
				Assert.assertEquals("author", "nedgar", changeSetEntry.getOwner());
				Assert.assertEquals("Component item id", "_K7ukIGPuEeKs7d1U683ZJg", changeSetEntry.getComponentItemId());
				Assert.assertEquals("Component name", "TestComponent1", changeSetEntry.getComponentName());
				Assert.assertEquals("workspace item id", "_cc-LYUhlEeK5tewUzhIIVw", changeSetEntry.getWorkspaceItemId());
				Assert.assertEquals("Change count for " + changeSetItemId, 3, changeSetEntry.getAffectedPaths().size());
				Assert.assertNotNull("Null primary work item", changeSetEntry.getWorkItem());
				Assert.assertEquals("Work item count " + changeSetItemId, 2, changeSetEntry.getAdditionalWorkItems().size());
				WorkItemDesc workItem = changeSetEntry.getWorkItem();
				Assert.assertEquals("Work item Number", "246335", workItem.getNumber());
			} else if (changeSetItemId.equals("_W8i8kUuCEeK5tewUzhIIVw")) {
				Assert.assertEquals("author", "ADMIN", changeSetEntry.getOwner());
				Assert.assertEquals("Change count for " + changeSetItemId, 1, changeSetEntry.getAffectedPaths().size());
				Assert.assertEquals("Work item count " + changeSetItemId, 0, changeSetEntry.getAdditionalWorkItems().size());
				ChangeDesc change = changeSetEntry.getAffectedVersionables().iterator().next();
				Assert.assertEquals("Versionable name", "pom.xml", change.getName());
				Assert.assertEquals("Change Kind",  EditType.EDIT, change.getType());
				WorkItemDesc workItem = changeSetEntry.getWorkItem();
				Assert.assertEquals("Work item Number", "246737", workItem.getNumber());
			} else if (changeSetItemId.equals("_H7VJsUuOEeK5tewUzhIIVw")) {
				Assert.assertEquals("Change count for " + changeSetItemId, 0, changeSetEntry.getAffectedVersionables().size());
				String path = changeSetEntry.getAffectedPaths().iterator().next();
				Assert.assertTrue(path, path.contains("2034") || path.contains("2,034"));
				Assert.assertTrue("Doesn't report back too many changes", changeSetEntry.isTooManyChanges());
				Assert.assertTrue(changeSetEntry.getTooManyChangesMsg(),
						changeSetEntry.getTooManyChangesMsg().contains("2034") || path.contains("2,034"));
				Assert.assertNull("Primary work item", changeSetEntry.getWorkItem());
				Assert.assertEquals("Work item count " + changeSetItemId, 0, changeSetEntry.getAdditionalWorkItems().size());
				WorkItemDesc workItem = changeSetEntry.getWorkItem();
				Assert.assertNull("primary work item", workItem);
			} else if (changeSetItemId.equals("_AJnUgVpuEeKvo5cWqp-wYg")) {
				Assert.assertEquals("Change count for " + changeSetItemId, 0, changeSetEntry.getAffectedVersionables().size());
				String path = changeSetEntry.getAffectedPaths().iterator().next();
				Assert.assertTrue(path, path.contains("129"));
				Assert.assertTrue("Doesn't report back too many changes", changeSetEntry.isTooManyChanges());
				Assert.assertTrue(changeSetEntry.getTooManyChangesMsg(), changeSetEntry.getTooManyChangesMsg().contains("129"));
				Assert.assertEquals("Work item count " + changeSetItemId, 2, changeSetEntry.getAdditionalWorkItems().size());
			} else {
				Assert.fail("Unexpected change set " + changeSetItemId + " id=" + changeSetEntry.getOwner());
			}
		}
	}

    protected BufferedReader getReader(String fileName) {
        InputStream in = getClass().getResourceAsStream(fileName);
        return new BufferedReader(new InputStreamReader(in));
    }

    @Test
    public void testMissingComponentName() throws Exception {
    	
		Reader changeLog =  getReader("MissingComponentName.xml");    	
		RTCChangeLogParser parser = new RTCChangeLogParser();
		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLog);
		
		Assert.assertEquals(1, result.getAffectedComponents().size());
		Assert.assertEquals(3, result.getChangeSetsAcceptedCount());
		String componentItemId = result.getAffectedComponents().iterator().next().getItemId();
		String componentName = result.getAffectedComponents().iterator().next().getName();
		Assert.assertNull(componentName);
		Assert.assertEquals(3, result.getChangeSetsAccepted(componentItemId).size());
    }

    @Test
    public void testDuplicateComponentName() throws Exception {
    	
		Reader changeLog =  getReader("DuplicateComponentName.xml");    	
		RTCChangeLogParser parser = new RTCChangeLogParser();
		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLog);
		
		Assert.assertEquals(2, result.getAffectedComponents().size());
		Assert.assertEquals(2, result.getChangeSetsAcceptedCount());
		Iterator<ComponentDescriptor> iterator = result.getAffectedComponents().iterator();
		ComponentDescriptor descriptor1 = iterator.next();
		ComponentDescriptor descriptor2 = iterator.next();
		Assert.assertNotNull(descriptor1.getName());
		Assert.assertEquals(descriptor1.getName(), descriptor2.getName());
		Assert.assertNotSame(descriptor1.getItemId(), descriptor2.getItemId());
		Assert.assertEquals(1, result.getChangeSetsAccepted(descriptor1.getItemId()).size());
		Assert.assertEquals(1, result.getChangeSetsAccepted(descriptor2.getItemId()).size());
    }

    @Test
	public void testEmpty() throws Exception {
		Reader changeLog =  new StringReader("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + EOL +
				"<changelog version=\"1\" >" + EOL +
				"</changelog>");
		
		RTCChangeLogParser parser = new RTCChangeLogParser();
		RTCChangeLogSet result = (RTCChangeLogSet) parser.parse(null, changeLog);
		Assert.assertFalse("Expected no change entries", result.iterator().hasNext());
		Assert.assertTrue("Expected it to be an empty set", result.isEmptySet());
		Assert.assertEquals(0, result.getAffectedComponents().size());
	}
}
