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
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.util.Digester2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import org.xml.sax.SAXException;

public class RTCChangeLogParser extends ChangeLogParser {

	@Override
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build,
			File changelogFile) throws IOException, SAXException {
		FileInputStream inputStream = new FileInputStream(changelogFile);
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT); //$NON-NLS-1$
		Reader reader = new InputStreamReader(inputStream, decoder);
		return parse(build, reader);
	}
	
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build,
			Reader changelogReader) throws IOException, SAXException {
		try {
			RTCChangeLogSet result = new RTCChangeLogSet(build);
			Digester2 digester = new Digester2();
			digester.push(result);
	
			digester.addSetProperties("changelog"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/baselineSetItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/baselineSetName"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/workspaceItemId"); //$NON-NLS-1$
			
			// When digester reads a {{<changeset>}} node it will create a {{RTCChangeLogChangeSetEntry}} object
			digester.addObjectCreate("*/changeset", RTCChangeLogChangeSetEntry.class); //$NON-NLS-1$
			// Reads all attributes in the {{<changeset>}} node and uses setter method in class to set the values
			digester.addSetProperties("*/changeset"); //$NON-NLS-1$
			// Reads the child node {{<action>}} and uses {{RTCChangeLogChangeSetEntry.setAction()}} to set the value
			digester.addBeanPropertySetter("*/changeset/action"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/changeSetItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/componentItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/componentName"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/owner"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/comment"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/additionalChanges"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/date"); //$NON-NLS-1$
			// The digested node/changeset is added to the change log through {{RTCChangeLogSet.add()}}
			digester.addSetNext("*/changeset", "add"); //$NON-NLS-1$ //$NON-NLS-2$
	
			// When digester reads a {{<changes>}} child node of {{<changeset}} it will create a {{RTCChangeLogChangeSetEntry.ChangeDesc}} object
			digester.addObjectCreate("*/changeset/changes/change", RTCChangeLogChangeSetEntry.ChangeDesc.class); //$NON-NLS-1$
			digester.addSetProperties("*/changeset/changes/change"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/changes/change/kind"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/changes/change/name"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/changes/change/itemType"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/changes/change/itemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/changes/change/stateId"); //$NON-NLS-1$
			// The digested node/change is added to the change set through {{RTCChangeLogChangeSetEntry.addChange()}}
			digester.addSetNext("*/changeset/changes/change", "addChange"); //$NON-NLS-1$ //$NON-NLS-2$
	
			// When digester reads a {{<workitems>}} child node of {{<changeset}} it will create a {{RTCChangeLogChangeSetEntry.WorkItemDesc}} object
			digester.addObjectCreate("*/changeset/workItems/workItem", RTCChangeLogChangeSetEntry.WorkItemDesc.class); //$NON-NLS-1$
			digester.addSetProperties("*/changeset/workItems/workItem"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/workItems/workItem/number"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/changeset/workItems/workItem/summary"); //$NON-NLS-1$
			// The digested node/workItem is added to the change set through {{RTCChangeLogChangeSetEntry.addWorkItem()}}
			digester.addSetNext("*/changeset/workItems/workItem", "addWorkItem"); //$NON-NLS-1$ //$NON-NLS-2$
	
			// components that were added/dropped
			// When digester reads a {{<component>}} node it will create a {{RTCChangeLogComponentEntry}} object
			digester.addObjectCreate("*/component", RTCChangeLogComponentEntry.class); //$NON-NLS-1$
			// Reads all attributes in the {{<component>}} node and uses setter method in class to set the values
			digester.addSetProperties("*/component"); //$NON-NLS-1$
			// Reads the child node {{<action>}} and uses {{RTCChangeLogComponentEntry.setAction()}} to set the value
			digester.addBeanPropertySetter("*/component/action"); //$NON-NLS-1$
			// Reads the child node {{<name>}} and uses {{RTCChangeLogComponentEntry.setName()}} to set the value
			digester.addBeanPropertySetter("*/component/name"); //$NON-NLS-1$
			// Reads the child node {{<uuid>}} and uses {{RTCChangeLogComponentEntry.setUuid()}} to set the value
			digester.addBeanPropertySetter("*/component/uuid"); //$NON-NLS-1$
			// The digested node/change set is added to the list through {{RTCChangeLogSet.add()}}
			digester.addSetNext("*/component", "add"); //$NON-NLS-1$ //$NON-NLS-2$
	
	
			// Do the actual parsing
			digester.parse(changelogReader);
			return result;

		} finally {
			changelogReader.close();
		}
	}
}
