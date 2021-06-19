/*******************************************************************************
 * Copyright © 2016, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests.utils;

import java.io.File;
import java.io.FileWriter;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Assert;
import org.junit.Ignore;

import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

@Ignore("Abstract class containing utility methods")
public class AbstractTestCase {
	private File sandboxDir;

	/**
     * generate the name of the project based on the test case
     * 
     * @return Name of the project
     */
    private String getTestClassName() {
        String name = this.getClass().getName();
        int posn = name.lastIndexOf('.');
        if (posn != -1 && posn < name.length()-1) {
            name = name.substring(posn + 1);
        }
        return name; 
    }
    
    protected String getUniqueName() {
    	return getUniqueName(getTestClassName()); 
    }
    
    protected String getFileUniqueName() {
    	return getUniqueName("File"); //$NON-NLS-1$
    }
    
    /**
     * This returns an unique name for use in a method of an object.
     * @return A name that is unique
     */
	protected String getUniqueName(String prefix) {
		String suffix1 = (SimpleDateFormat.getDateTimeInstance
				(DateFormat.LONG, DateFormat.FULL)).format(new Date()).replace(" ","-")
				.replace(",", "-").replace(":","-");
		SecureRandom random = new SecureRandom();
		Long suffix2 = random.nextLong();
		return prefix + "_" + suffix1 + "_" + Long.toHexString(suffix2);
	}
	
    
    /**
     * This returns an unique name for use in a method of an object.
     * This method is used by {@link RTCFacadeIT} to simulate a test failure.
     * Once the issue is fixed, this method can be used in place of the above method.
     * 
     * @return A name that is unique
     * @throws UnknownHostException 
     */
	protected String getUniqueName2(String prefix) {
		String suffix1 = (SimpleDateFormat.getDateTimeInstance
				(DateFormat.LONG, DateFormat.FULL)).format(new Date()).
				replace(" ","-");
		SecureRandom random = new SecureRandom();
		Long suffix2 = random.nextLong();
		return prefix + "_" + suffix1 + "_" + Long.toHexString(suffix2);
	}
	
	protected String getBuildDefinitionUniqueName() {
		return getUniqueName("BuildDefinition");
	}
	
	protected String getBuildDefinitionUniqueName2() {
		return getUniqueName2("BuildDefinition");
	}
	
	protected String getBuildDefinitionUniqueName(String prefix) {
		return getUniqueName(prefix);
	}
	
	protected String getRepositoryWorkspaceUniqueName() {
		return getUniqueName("RepositoryWorkspace");
	}
	
	protected String getComponentUniqueName() {
		return getUniqueName("Component");
	}
	
	protected String getStreamUniqueName() {
		return getUniqueName("Stream");
	}
	
	protected String getProjectAreaUniqueName() {
		return getUniqueName("ProjectArea");
	}
	
	protected String getTeamAreaUniqueName() {
		return getUniqueName("TeamArea");
	}
	
	protected String getSnapshotUniqueName() {
		return getUniqueName("Snapshot");
	}

	protected void createSandboxDirectory() {
		File tempDir = new File(System.getProperty("java.io.tmpdir"));
		File buildTestDir = new File(tempDir, "HJPluginTests");
		setSandboxDir(new File(buildTestDir, getFileUniqueName()));
		getSandboxDir().mkdirs();
		getSandboxDir().deleteOnExit();
		Assert.assertTrue(getSandboxDir().exists());
	}

	protected void tearDownSandboxDirectory() throws Exception {
		if (getSandboxDir() != null) {
			FileUtils.delete(getSandboxDir());
		}
	}

	protected TaskListener getTaskListener() {
		TaskListener listener = new StreamTaskListener(System.out, null);
		return listener;
	}

	protected File getSandboxDir() {
		return this.sandboxDir;
	}

	protected void setSandboxDir(File sandboxDir) {
		this.sandboxDir = sandboxDir;
	}
	
	/**
	 * Utility method to write the exception details into temporary file.
	 * 
	 * @param f
	 * @param exp
	 * @throws Exception
	 */
	protected void writeExpDetails(File f, Exception exp) throws Exception {
		try (FileWriter fw = new FileWriter(f)) {
			fw.write(exp.getClass().getName() + "\n");
			fw.write(exp.getMessage() != null ? exp.getMessage(): "No exception details");
			Throwable t = exp.getCause();
			while (t != null) {
				fw.write("\n");
				fw.write(t.getClass().getName() + "\n");
				fw.write(t.getMessage() != null ? t.getMessage() : "No inner exception details");
				t = t.getCause();
			}
		}
	}

}
