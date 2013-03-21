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
import java.util.Map;

import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;

import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;

public class RTCFacadeIT extends HudsonTestCase {

	RTCFacadeWrapper facade = null;

	@Override
	public void setUp() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			super.setUp();
			facade = RTCFacadeFactory.getFacade(Config.DEFAULT.getToolkit(), null);
		}
	}

	@Override
	protected void tearDown() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			super.tearDown();
		}
	}

	public void testTestConnectionWithPassword() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			String errorMessage = (String) facade.invoke(
					"testConnection",
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class}, // timeout
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(), null,
					Config.DEFAULT.getTimeout());
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
		}
	}

	public void testTestConnectionWithInvalidPassword() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			String errorMessage = (String) facade.invoke(
					"testConnection",
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class}, // timeout
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					"invalid password", null,
					Config.DEFAULT.getTimeout());
			assertTrue("Successful testConnection with invalid password", errorMessage != null && errorMessage.length() != 0);
		}
	}

	public void testTestConnectionWithPasswordFile() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			String errorMessage = (String) facade.invoke(
					"testConnection",
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class}, // timeout
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					null, new File(Config.DEFAULT.getPasswordFile()),
					Config.DEFAULT.getTimeout());
			assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);
		}
	}

	public void testTestConnectionWithInvalidPasswordFile() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			String errorMessage = (String) facade.invoke(
					"testConnection",
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class}, // timeout
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					null, new File("/invalidPasswordFile"),
					Config.DEFAULT.getTimeout());
			assertTrue("Successful testConnection with invalid password file", errorMessage != null && errorMessage.length() != 0);
		}
	}

	public void testTestBuildWorkspace() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			RTCFacadeWrapper testingFacade = RTCFacadeFactory.newTestingFacade(Config.DEFAULT.getToolkit());
			
			@SuppressWarnings("unchecked")
			Map<String, String> setupArtifacts = (Map<String, String>) testingFacade.invoke(
					"setupTestBuildWorkspace", new Class[] {
							String.class, // serverURL,
							String.class, // userId,
							String.class, // password,
							File.class, // passwordFile,
							int.class, // timeout,
							String.class, // singleWorkspaceName,
							String.class}, // multipleWorkspaceName
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(), null,
					Config.DEFAULT.getTimeout(),
					"SinglyOccuringWS", 
					"MultipleOccurrenceWS");

			try {
				
				String errorMessage = (String) facade.invoke(
					"testBuildWorkspace",
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class, // timeout
							String.class}, // buildWorkspace
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(), null,
					Config.DEFAULT.getTimeout(),
					"SinglyOccuringWS");
				assertTrue(errorMessage, errorMessage == null || errorMessage.length() == 0);

				try {
					errorMessage = (String) facade.invoke(
							"testBuildWorkspace",
							new Class[] { String.class, // serverURI
									String.class, // userId
									String.class, // password
									File.class, // passwordFile
									int.class, // timeout
									String.class}, // buildWorkspace
							Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
							Config.DEFAULT.getPassword(), null,
							Config.DEFAULT.getTimeout(),
							"MultipleOccurrenceWS");
					assertTrue("There should be more than 1 workspace with the name", errorMessage != null && errorMessage.contains("More than 1"));
				} catch (Exception e) {
					e.printStackTrace(System.out);
					Assert.fail(e.getMessage());
				}
			} finally {
				testingFacade.invoke(
						"tearDownTestBuildWorkspace",
						new Class[] { String.class, // serverURI
								String.class, // userId
								String.class, // password
								File.class, // passwordFile
								int.class, // timeout
								Map.class}, // setupArtifacts
								Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
								Config.DEFAULT.getPassword(), null,
								Config.DEFAULT.getTimeout(),
								setupArtifacts);
			}
		}
	}

	public void testTestMissingBuildWorkspace() throws Exception {
		if (Config.DEFAULT.isConfigured()) {
			try {
				String errorMessage = (String) facade.invoke(
					"testBuildWorkspace",
					new Class[] { String.class, // serverURI
							String.class, // userId
							String.class, // password
							File.class, // passwordFile
							int.class, // timeout
							String.class}, // buildWorkspace
					Config.DEFAULT.getServerURI(), Config.DEFAULT.getUserID(),
					Config.DEFAULT.getPassword(), null,
					Config.DEFAULT.getTimeout(),
					"MissingWorkspace" + System.currentTimeMillis());
				assertTrue(errorMessage != null && errorMessage.contains("Unable to find"));
			} catch (Exception e) {
				e.printStackTrace(System.out);
				Assert.fail(e.getMessage());
			}
		}
	}
}
