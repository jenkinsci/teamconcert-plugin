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
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.digester3.Digester;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

public class RTCChangeLogParser extends ChangeLogParser {
    private static final Logger LOGGER = Logger.getLogger(RTCChangeLogParser.class.getName());

	@Override
	public ChangeLogSet<? extends Entry> parse(Run build, RepositoryBrowser<?> browser,
			File changelogFile) throws IOException, SAXException {
		LOGGER.finest("RTCChangeLogParser.parse with changelogFile: Begin"); //$NON-NLS$1
		if (LOGGER.isLoggable(Level.FINER)) {
			try {
				if (changelogFile != null) {
					LOGGER.finer("Parsing changelog for file " +  changelogFile.getAbsolutePath()); //$NON-NLS$1
				}
			}
			catch (SecurityException exp) {
				LOGGER.finer("Error getting changelog file path for filename" +  changelogFile.getName()); //$NON-NLS$1
			}
		}
		FileInputStream inputStream = new FileInputStream(changelogFile);
        CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT); //$NON-NLS-1$
		Reader reader = new InputStreamReader(inputStream, decoder);
		return parse(build, browser, reader);
	}
	
	public ChangeLogSet<? extends Entry> parse(Run build, RepositoryBrowser<?> browser,
			Reader changelogReader) throws IOException, SAXException {
		LOGGER.finest("RTCChangeLogParser.parse with Reader : Begin"); //$NON-NLS$1
		try {
			RTCChangeLogSet result = new RTCChangeLogSet(build, browser);
			Digester digester = getDigester();
			digester.push(result);

			digester.addSetProperties("changelog"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/baselineSetItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/baselineSetName"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/workspaceItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/workspaceName"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/buildDefinitionName"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/buildDefinitionItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/streamName"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/streamItemId"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/isPersonalBuild"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/previousBuildUrl"); //$NON-NLS-1$
			digester.addBeanPropertySetter("*/previousBaselineSetItemId");
			digester.addBeanPropertySetter("*/previousBaselineSetName");
			
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
	
	private Digester getDigester() throws SAXException {
		LOGGER.finest("RTCChangeLogParser.getDigester : Begin");
		Digester digester;
		try {
			digester = new Digester();
		} catch (Error e) {
			LOGGER.log(Level.FINER, "Failed to get Digest2 error: " + e.getMessage(), e);
			dumpClassLoader("getDigester()");
			// Switch out the context class loader because in some configurations, the 
			// LogFactory class used by Log used by the Digester can't be loaded.
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
				digester = new Digester();
			} finally {
				Thread.currentThread().setContextClassLoader(classLoader);
			}
		}
		digester.setXIncludeAware(false);

		if (!Boolean.getBoolean(RTCChangeLogParser.class.getName() + ".UNSAFE")) {
			try {
				digester.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				digester.setFeature("http://xml.org/sax/features/external-general-entities", false);
				digester.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				digester.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			}
			catch ( ParserConfigurationException ex) {
				throw new SAXException("Failed to securely configure RTC changelog parser", ex);
			}
		}
		return digester;
	}

	private void dumpClassLoader(String string) {
		LOGGER.finest("RTCChangeLogParser.dumpClassLoader : Begin");
		String message = string + "\n";
		String indent = "  ";
		ClassLoader classLoader = this.getClass().getClassLoader();
		message += indent + "ClassLoader: " + classLoader.getClass().getName() + "\n";
		message += appendClassLoaderParents(classLoader.getParent(), indent);
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		message += indent + "ContextClassLoader: " + contextClassLoader.getClass().getName() + "\n";
		message += appendClassLoaderParents(contextClassLoader.getParent(), indent);
		LOGGER.log(Level.FINER, message);
		LOGGER.finest("RTCChangeLogParser.dumpClassLoader : End");
	}

	private static String appendClassLoaderParents(ClassLoader parent,
			String indent) {
		if (parent == null) {
			return "\n";
		} else {
			indent += "  ";
			String result = indent + parent.getClass().getName() + "\n";
			result += appendClassLoaderParents(parent.getParent(), indent);
			return result;
		}
	}
}
