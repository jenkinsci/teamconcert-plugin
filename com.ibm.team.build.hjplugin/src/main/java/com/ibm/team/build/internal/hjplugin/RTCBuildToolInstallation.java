/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolProperty;
import hudson.tools.ToolInstallation;
import hudson.util.FormValidation;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class RTCBuildToolInstallation extends ToolInstallation implements NodeSpecific<RTCBuildToolInstallation>, EnvironmentSpecific<RTCBuildToolInstallation> {
	
	public static final String BUILD_TOOLKIT_TASK_DEFS_XML = "BuildToolkitTaskDefs.xml"; //$NON-NLS-1$
	
	private static final Logger LOGGER = Logger.getLogger(RTCBuildToolInstallation.class.getName());

    /**
     * Default constructor.
     * 
     * @param name Installation name
     * @param home Path to RTC build toolkit
     * @param properties Additional tool installation properties
     */
    @DataBoundConstructor
	public RTCBuildToolInstallation(String name, String home,
			List<? extends ToolProperty<?>> properties) {
		super(name, home, properties);
	}

    /**
     * @return The build toolkit path
     */
    public String getBuildToolkit() {
    	return getHome();
    }
    
	private static final long serialVersionUID = 1L;

	public RTCBuildToolInstallation forNode(Node node, TaskListener log)
			throws IOException, InterruptedException {
        return new RTCBuildToolInstallation(getName(), translateFor(node, log), Collections.<ToolProperty<?>>emptyList());
	}

    public RTCBuildToolInstallation forEnvironment(EnvVars environment) {
       return new RTCBuildToolInstallation(getName(), environment.expand(getHome()), Collections.<ToolProperty<?>>emptyList());
    }

    public static RTCBuildToolInstallation[] allInstallations() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class)
                .getInstallations();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Hudson.getInstance().getDescriptor(RTCBuildToolInstallation.class);
    }

    /**
     * Validate that the path supplied points to a directory that looks like
     * a build toolkit (i.e. contains the BuildToolkitTasksDef.xml file)
     * @param warnOnly Consider any problems found to be a warning as opposed to 
     * an error.
     * @param path Path to the toolkit to validate
	 * @return The result of the validation (will be ok if valid)
     */
	public static FormValidation validateBuildToolkit(boolean warnOnly, String path) {
		path = Util.fixEmptyAndTrim(path);
		if (path == null) {
			LOGGER.finer("BuildToolKit value not supplied"); //$NON-NLS-1$
			if (warnOnly) {
				return FormValidation.warning(Messages.RTCBuildToolInstallation_missing_toolkit());
			} else {
				return FormValidation.error(Messages.RTCBuildToolInstallation_missing_toolkit());
			}
		}
		File toolkitFile = new File(path);
		return validateBuildToolkit(warnOnly, toolkitFile);
	}
	
	/**
	 * Validate that the File provided looks like the toolkit directory
	 * i.e. contains the BuildToolkitTaskDefs.xml file
     * @param warnOnly Consider any problems found to be a warning as opposed to 
     * an error.
	 * @param toolkitFile File that represents the toolkit path. Not <code>null</code>
	 * @return The result of the validation (will be ok if valid)
	 */
	public static FormValidation validateBuildToolkit(boolean warnOnly, File toolkitFile) {
		String message = null;
		if (!toolkitFile.exists()) {
			LOGGER.finer("BuildToolKit folder not found : " + toolkitFile.getAbsolutePath()); //$NON-NLS-1$
			message = Messages.RTCBuildToolInstallation_toolkit_not_found();
		}
		if (message == null && !toolkitFile.isDirectory()) {
			LOGGER.finer("BuildToolKit is not a folder : " + toolkitFile.getAbsolutePath()); //$NON-NLS-1$
			message = Messages.RTCBuildToolInstallation_toolkit_not_directory();
		}
		if (message == null) {
			File[] files = toolkitFile.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.getName().equals(BUILD_TOOLKIT_TASK_DEFS_XML) && !file.isDirectory();
				}
			});
			if (files == null) {
				LOGGER.finer("BuildToolKit contents can not be determined : " + toolkitFile.getAbsolutePath()); //$NON-NLS-1$
				message = Messages.RTCBuildToolInstallation_unable_to_read();
			}
			if (message == null && files.length == 0) {
				LOGGER.finer("BuildToolKit folder doesn't look like a tool kit : " + toolkitFile.getAbsolutePath()); //$NON-NLS-1$
				message = Messages.RTCBuildToolInstallation_not_tookit_directory();
			}
		}
		if (message == null) {
			return FormValidation.ok();
		} else if (warnOnly) {
			return FormValidation.warning(message);
		} else {
			return FormValidation.error(message);
		}
	}

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<RTCBuildToolInstallation> {
        @CopyOnWrite
        private volatile RTCBuildToolInstallation[] installations = new RTCBuildToolInstallation[0];

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.RTCBuildToolInstallation_RTC_display_name();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            super.configure(req, json);
            save();
            return true;
        }

        @Override
        public RTCBuildToolInstallation[] getInstallations() {
            return installations;
        }

        @Override
        public void setInstallations(RTCBuildToolInstallation... installations) {
            this.installations = installations;
            save();
        }
        
        /**
         * Checks if the path to the build toolkit is valid
         */
        public FormValidation doCheckHome(@QueryParameter File value)  {
            return validateBuildToolkit(false, value);
        }
        
    }

}
