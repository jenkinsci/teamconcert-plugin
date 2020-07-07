/*******************************************************************************
 * Copyright (c) 2017, 2020 IBM Corporation and others.
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
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.team.build.common.model.IBuildResultHandle;
import com.ibm.team.build.internal.publishing.WorkItemPublisher;
import com.ibm.team.build.internal.scm.SourceControlUtility;
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.scm.client.IWorkspaceConnection;

/**
 * This class contains utility methods to determine the version of build toolkit and RTC server.
 */
@SuppressWarnings("restriction")
public class VersionCheckerUtil {
	
    private static final String CLIENT_COMPATIBILITY_VERSION_ATTR = "clientCompatibilityVersion"; //$NON-NLS-1$
	private static final String COMPONENT_CONFIGURATION_XML_TAG = "componentConfiguration"; //$NON-NLS-1$
	private static final String PLUGIN_XML_FILENAME = "plugin.xml"; //$NON-NLS-1$
	private static final String CLASS_EXTN = ".class"; //$NON-NLS-1$
	private static final String COMPONENT_CONFIGURATION_CLAZZ = "com.ibm.team.rtc.common.configuration.IComponentConfiguration"; //$NON-NLS-1$
	private static final Logger LOGGER = Logger.getLogger(VersionCheckerUtil.class.getName());
    
    /**
	 * This method determines if the build toolkit in context is pre-603 or not, depending on the presence of
	 * SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, ILoadRule2, boolean,
	 * IProgressMonitor) method.
	 * 
	 * @return true if the method lookup fails otherwise return false.
	 */
	public static boolean isPre603BuildToolkit() {
		boolean isPre603BuildToolkit = true;
		try {
			SourceControlUtility.class.getMethod("updateFileCopyArea", IWorkspaceConnection.class, String.class, boolean.class, ILoadRule2.class, //$NON-NLS-1$
					boolean.class, IProgressMonitor.class);
			isPre603BuildToolkit = false;
		} catch (NoSuchMethodException e) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, ILoadRule2, boolean, IProgressMonitor) " //$NON-NLS-1$
						+ "method not found. Jenkins job should have been configured with a pre-603 build toolkit: " + e); //$NON-NLS-1$
			}
		} catch (SecurityException e) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("SourceControlUtility.updateFileCopyArea(IWorkspaceConnection, String, boolean, ILoadRule2, boolean, IProgressMonitor) " //$NON-NLS-1$
						+ "method not found. Jenkins job should have been configured with a pre-603 build toolkit: " + e); //$NON-NLS-1$
			}
		}
		return isPre603BuildToolkit;
	}
	
	/**
	 * This method determines if the build toolkit in context is pre-701 or not,
	 * depending on the presence of
	 * com.ibm.team.build.internal.scm.BuildScmLoadOptions class.
	 * 
	 * @return true if the class is not found otherwise return false.
	 */
	public static boolean isPre701BuildToolkit() {
		boolean isPre701BuildToolkit = true;
		try {
			 Class.forName("com.ibm.team.build.internal.scm.BuildScmLoadOptions");
			 isPre701BuildToolkit = false;
		} catch (ClassNotFoundException e) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("com.ibm.team.build.internal.scm.BuildScmLoadOptions class not found. " //$NON-NLS-1$
						+ "Jenkins job should have been configured with a pre-701 build toolkit: " //$NON-NLS-1$
						+ e.getMessage());
			}
		}
		return isPre701BuildToolkit;
	}
	
	public static String getBuildToolkitVersion(Locale clientLocale) throws RTCVersionCheckException {
		String buildtoolkitVersion = null;
		try {
	        Class<?> clazz = Class.forName(COMPONENT_CONFIGURATION_CLAZZ);
	        if (clazz != null) {
	            URL clazzURL = clazz.getResource(clazz.getSimpleName() + CLASS_EXTN);
	            String jarURL = clazzURL.toString();
	            // Construct the substring which has the package folders
	            String suffix = clazz.getCanonicalName().replace(".", "/") + CLASS_EXTN; //$NON-NLS-1$//$NON-NLS-2$
	            // Remove suffix from jarURL
	            jarURL = jarURL.substring(0, jarURL.indexOf(suffix));
	            URL pluginURL = new URL(new URL(jarURL), PLUGIN_XML_FILENAME);
	            InputStream fin = pluginURL.openStream();
	            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	            DocumentBuilder db = dbf.newDocumentBuilder();	            
	            Document d = db.parse(fin);
	            NodeList nl = d.getElementsByTagName(COMPONENT_CONFIGURATION_XML_TAG);
	            if (nl.getLength() > 0) {
	               Node n = nl.item(0);
	               NamedNodeMap attrMap = n.getAttributes();
	               Node attr = attrMap.getNamedItem(CLIENT_COMPATIBILITY_VERSION_ATTR);
	               if (attr != null) {
	            	   buildtoolkitVersion = attr.getNodeValue();
	               }
	            } else {
	            	throw new RTCConfigurationException(Messages.get(clientLocale).VersionCheckerUtil_missing_expected_content_in_plugin_xml());
	            }
	        }
	    } catch (ClassNotFoundException e) {
	       throw new RTCVersionCheckException(Messages.get(clientLocale).VersionCheckerUtil_class_not_found(e.getMessage()),
	    		   			e);
	    } catch (IOException e) {
	       throw new RTCVersionCheckException(
	    		   Messages.get(clientLocale).VersionCheckerUtil_io_error(e.getMessage()), e);
	    } catch (ParserConfigurationException e) {
	        throw new RTCVersionCheckException(
	        		Messages.get(clientLocale).VersionCheckerUtil_parser_error(e.getMessage()), e);
	    } catch (SAXException e) {
			throw new RTCVersionCheckException(Messages.get(clientLocale).VersionCheckerUtil_parser_error(e.getMessage()), e);
		} catch (RTCConfigurationException e) {
			throw new RTCVersionCheckException(Messages.get(clientLocale).VersionCheckerUtil_parser_error(e.getMessage()), e);
		}
		return buildtoolkitVersion;
	}
	
	public static boolean isPre70BuildToolkit() {
		boolean isPre70BuildToolkit = true; // Assume that we are dealing with a toolkit v 6.0.6.1 or below.
		try {
			WorkItemPublisher.class.getMethod("publish", IBuildResultHandle.class, Array.class, boolean.class, ITeamRepository.class);
			isPre70BuildToolkit = false;
		} catch (NoSuchMethodException | SecurityException exp) {
			if (LOGGER.isLoggable(Level.FINER)) {
				LOGGER.finer("WorkItemPublisher.publish(IBuildResult, IChangeSet[], boolean, ITeamRepository) not found"); //$NON-NLS-1$
			}
		} 
		return isPre70BuildToolkit;
	}
}