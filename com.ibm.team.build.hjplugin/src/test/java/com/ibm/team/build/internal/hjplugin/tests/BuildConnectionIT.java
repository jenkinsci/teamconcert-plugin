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

import java.io.File;

import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.rtc.BuildConnection;
import com.ibm.team.build.internal.hjplugin.tests.utils.FileUtils;

public class BuildConnectionIT extends HudsonTestCase {

	private RTCFacadeWrapper testingFacade;

	@Override
	public void setUp() throws Exception {

		if (Config.DEFAULT.isConfigured()) {
			// DO NOT initialize Hudson/Jenkins because its slow and we don't need it for the tests
			
			testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
		}
	}

	@Override
	public void tearDown() throws Exception {
		// Nothing to do including no need to shutdown Hudson/Jenkins
	}
	
	/**
	 * {@link BuildConnection#addSnapshotContribution()} is tested by {@link RepositoryConnectionIT#testBuildResultContributions()}
	 * {@link BuildConnection#addWorkspaceContribution()} is tested by {@link RepositoryConnectionIT#testBuildResultContributions()}
	 * {@link BuildConnection#startBuildActivity()} is tested by {@link RepositoryConnectionIT#testBuildResultContributions()}
	 * {@link BuildConnection#completeBuildActivity() is tested by RepositoryConnectionIT#testBuildResultContributions()}
	 * @throws Exception 
	 */

	public void testCreateBuildResult() throws Exception {
		// test creation of build results
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			testingFacade.invoke("testCreateBuildResult",
					new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					File.class, // passwordFile,
					int.class, // timeout,
					String.class}, // testName
			Config.DEFAULT.getServerURI(),
			Config.DEFAULT.getUserID(),
			Config.DEFAULT.getPassword(), passwordFileFile,
			Config.DEFAULT.getTimeout(), getTestName() + System.currentTimeMillis());
		}
	}

	public void testCreateBuildResultFail() throws Exception {
		// test creation of build results
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			testingFacade.invoke("testCreateBuildResultFail",
					new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					File.class, // passwordFile,
					int.class, // timeout,
					String.class}, // testName
			Config.DEFAULT.getServerURI(),
			Config.DEFAULT.getUserID(),
			Config.DEFAULT.getPassword(), passwordFileFile,
			Config.DEFAULT.getTimeout(), getTestName() + System.currentTimeMillis());
		}
	}

	public void testLinksToJenkins() throws Exception {
		// test Jenkins external links added
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			testingFacade.invoke("testExternalLinks",
					new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					File.class, // passwordFile,
					int.class, // timeout,
					String.class}, // testName
			Config.DEFAULT.getServerURI(),
			Config.DEFAULT.getUserID(),
			Config.DEFAULT.getPassword(), passwordFileFile,
			Config.DEFAULT.getTimeout(), getTestName() + System.currentTimeMillis());
		}
	}
	
	public void testBuildTermination() throws Exception {
		// test termination of build 
		if (Config.DEFAULT.isConfigured()) {
			File passwordFileFile = FileUtils.getPasswordFile();
			
			testingFacade.invoke("testBuildTermination",
					new Class[] { String.class, // serverURL,
					String.class, // userId,
					String.class, // password,
					File.class, // passwordFile,
					int.class, // timeout,
					String.class}, // testName
			Config.DEFAULT.getServerURI(),
			Config.DEFAULT.getUserID(),
			Config.DEFAULT.getPassword(), passwordFileFile,
			Config.DEFAULT.getTimeout(), getTestName() + System.currentTimeMillis());
		}
	}
	
    /**
     * generate the name of the project based on the test case
     * 
     * @return Name of the project
     */
    protected String getTestName() {
        String name = this.getClass().getName();
        int posn = name.lastIndexOf('.');
        if (posn != -1 && posn < name.length()-1) {
            name = name.substring(posn + 1);
        }
        return name + "_" + this.getName();
    }


}
