/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class BuildToolkitVersionTask extends RTCTask<String> {

	private static final String PRODUCT_VERSION_FULL_PROPERTY_NAME = "product_version"; //$NON-NLS-1$
	private static final String VERSION_FILE_NAME = "VERSION"; //$NON-NLS-1$
	private static final Logger LOGGER = Logger.getLogger(BuildToolkitVersionTask.class.getName());
	
	private static final long serialVersionUID = 1L;
	
	private Locale clientLocale;
	private String buildToolkitPath;
	private String nodeName;
	
	/**
	 * This provides the version of the build toolkit at the given path when executed 
	 * in a particular node
	 * @param buildtoolkit The path to the build toolkit on the node (could be master or slave)
	 * @param isDebug Is debug turned on?
	 * @param clientLocale The locale to use
	 * @param listener Listener for adding messages to the build log
	 */
	public BuildToolkitVersionTask(String buildtoolkit, String nodeName,
				boolean isDebug, Locale clientLocale, 
				TaskListener listener) {
		super(isDebug, listener);
		this.buildToolkitPath = buildtoolkit;
		this.nodeName = nodeName;
		this.clientLocale = clientLocale;
	}

	@Override
	public String invoke(File arg0, VirtualChannel arg1) throws IOException, InterruptedException {
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("BuildToolkitVersionTask: Fetching build toolkit version details"); //$NON-NLS-1$
		}
		if (getIsDebug()) {
			debug("buildtoolkit " + this.buildToolkitPath); //$NON-NLS-1$
			debug("listener is " + (getListener() == null ? "n/a" : getListener())); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return getBuildToolkitVersion(this.buildToolkitPath, this.nodeName, getIsDebug(), this.clientLocale, getListener());
	}

	public static String getBuildToolkitVersion(String buildToolkitPath, String nodeName, boolean isDebug, 
													Locale clientLocale, TaskListener listener) throws InterruptedException {
		try {
            String release = null;
			//  See if there is a VERSION file at buildtoolkitPath
			String versionFilePath = buildToolkitPath + File.separator + VERSION_FILE_NAME;
			File versionFile = new File(versionFilePath);
			if (versionFile.exists()) {
				FileInputStream ins = null;
				Scanner scanner = null;
				try {
					ins = new FileInputStream(versionFile); 
					scanner = new Scanner(ins);
		            List<String> lines = new ArrayList<String>();
		            while(scanner.hasNext()) {
		                lines.add(scanner.nextLine());
		            }
		            // Parse out the name value pairs into a map
		            Map<String, String> properties = parseProperties(lines);
		            if (properties.containsKey(PRODUCT_VERSION_FULL_PROPERTY_NAME)) { 
		                release = properties.get(PRODUCT_VERSION_FULL_PROPERTY_NAME);               
		            }
				} catch (IOException exp) {
					LOGGER.log(Level.WARNING, 
								String.format("Error reading from VERSION file at %s", versionFile), //$NON-NLS-1$
								exp);
					//Ignore and continue with the fall back.
				} finally {
					if (scanner != null) {
						scanner.close();
					}
					if (ins != null) {
						ins.close();
					}
				}
			}
			// If the version information is found, then return it
			// Otherwise try to fetch it from plugin.xml
			release = hudson.Util.fixEmptyAndTrim(release);
			if (release != null) {
				return release;
			}
			RTCFacadeWrapper facade = RTCFacadeFactory.getFacade(buildToolkitPath, (isDebug && listener != null) ? listener.getLogger() : null);
			return (String) facade.invoke("getBuildToolkitVersion", new Class[] { //$NON-NLS-1$
					String.class,
					Object.class,
					Locale.class
					}, buildToolkitPath, listener, clientLocale);
		} catch (Exception e)  {
			Throwable eToReport = e;
			if (eToReport instanceof InvocationTargetException && e.getCause() != null) {
				eToReport = e.getCause();
			}
			if (e.getCause() != null) { 
				eToReport = e.getCause();
			}
			// Log the exception
			if (LOGGER.isLoggable(Level.WARNING)) {
				LOGGER.log(Level.WARNING, Messages.BuildToolkitVersionTask_unable_to_fetch_version_details_for(buildToolkitPath, nodeName), e);
			}
			// We don't want to terminate the build if the version information could not be obtained
			// Log the exception in the build log too
			listener.getLogger().println(Messages.BuildToolkitVersionTask_unable_to_fetch_version_details_for2(buildToolkitPath, nodeName, e.getMessage()));
			if (isDebug) {
				e.printStackTrace(listener.getLogger());
			}
		}
		return null;
	}

	@Override
	protected Logger getLogger() {
		return LOGGER;
	}

	/**
     * Parse the lines which are of the form &it;propertyname&gt;=&lt;propertyvalue&gt;
     * 
     * @return Map containing name value pairs
     */
    private static Map<String, String> parseProperties(List<String> lines) {
        Map<String, String> properties = new HashMap<String, String>();
        for (String line : lines) {
            String [] parts = line.split("="); //$NON-NLS-1$
            if (parts == null) continue;
            if (parts.length != 2) continue;
            if (parts[0] != null && parts [1] != null) {
                properties.put(parts[0], parts[1]);
            }
        }
        return properties;
    }
}
