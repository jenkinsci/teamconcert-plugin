/*******************************************************************************
 * Copyright © 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.google.common.io.Files;
import com.ibm.team.build.internal.hjplugin.RTCFacadeFactory.RTCFacadeWrapper;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.tests.utils.Utils;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;

/**
 * Integration tests for getting metronome logs in a freestyle 
 * build
 *
 */
public class MetronomeLogIT extends AbstractTestCase {
	@Rule
	public JenkinsRule r = new JenkinsRule();

	
	@Before
	public void setUp() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
	}
	
	
	@After
	public void tearDown() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		Utils.deleteTemporaryWorkspaces();
	}
	
	/**
	 * Build result has two logs but the Jenkins build  does not have any log files 
	 * @throws Exception
	 */
	@Test
	public void testMetronomeLogPropertyTurnedOnInBuildDefinition_BuildResultUpdatedLogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinitionAndJenkinsJob(true, false);
	}

	@Test
	public void testMetronomeLogPropertyTurnedOffInBuildDefinitionTurnedOnInJenkinsJob_LogsCreated() 
								throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinitionAndJenkinsJob(false, true);
	}

	@Test
	public void testMetronomeLogPropertyAbsentInBuildDefinitionPresentInJenkinsJob_LogsCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinition(true, true, false);
	}
	
	@Test
	public void testMetronomeLogPropertyTurnedOnBuildWorkspace_LogsCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildWorkspace(true, true, false);
	}

	@Test
	public void testMetronomeLogPropertyTurnedOnBuildSnapshot_LogsCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildSnapshot(true, true, false);
	}
	
	@Test
	public void testMetronomeLogPropertyTurnedOnBuildStream_LogsCreated() throws Exception {
		// Check if the buildToolkitVersion environment property is set.
		// If it's value is 407, do not run the test
		if (Utils.is407BuildToolkit()) {
			return;
		}
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildStream(true, true, false);
	}
	
	@Test
	public void testMetronomeLogPropertyTurnedOffBuildInDefinitionTurnedOffInJenkinsJob_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinitionAndJenkinsJob_LogsNotCreated(true);
	}
	
	@Test
	public void testMetronomeLogPropertyAbsentInDefinitionTurnedOffInJenkinsJob_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinitionAndJenkinsJob_LogsNotCreated(false);
	}

	@Test
	public void testMetronomeLogPropertyAbsentInBuildInDefinitionTurnedOffInJenkinsJob_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinition(true, false, false);
	}
	
	@Test
	public void testMetronomeLogPropertyAbsentInBuildInDefinitionEmptyInJenkinsJob_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinition(true, false, true);
	}
	
	@Test
	public void testMetronomeLogPropertyAbsentInBuildInDefinitionAbsentInJenkinsJob_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildDefinition(false, false, false);		
	}
	
	@Test
	public void testMetronomeLogPropertyTurnedOffBuildWorkspace_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildWorkspace(true, false, false);
	}
	
	@Test
	public void testMetronomeLogPropertyEmptyBuildWorkspace_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildWorkspace(true, false, true);
	}
	
	@Test
	public void testMetronomeLogPropertyAbsentBuildWorkspace_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildWorkspace(false, false, false);
	}

	@Test
	public void testMetronomeLogPropertyTurnedOffBuildSnapshot_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildSnapshot(true, false, false);
	}
	
	@Test
	public void testMetronomeLogPropertyEmptyBuildSnapshot_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildSnapshot(true, false, true);
	}
	
	@Test
	public void testMetronomeLogPropertyAbsentBuildSnapshot_LogsNotCreated() throws Exception {
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildSnapshot(false, false, false);
	}
	
	@Test
	public void testMetronomeLogPropertyTurnedOffBuildStream_LogsNotCreated() throws Exception {
		if (Utils.is407BuildToolkit()) {
			return;
		}
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildStream(true, false, false);
	}
	
	@Test
	public void testMetronomeLogPropertyEmptyBuildStream_LogsNotCreated() throws Exception {
		if (Utils.is407BuildToolkit()) {
			return;
		}
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildStream(true, false, true);
	}

	@Test
	public void testMetronomeLogPropertyAbsentBuildStream_LogsNotCreated() throws Exception {
		if (Utils.is407BuildToolkit()) {
			return;
		}
		if (!Config.DEFAULT.isConfigured()) {
			return;
		}
		helperTestMetronomeLogPropertyInBuildStream(false, false, false);
	}

	private void helperTestMetronomeLogPropertyInBuildDefinition(
			boolean shouldCreateProperty, 
			boolean shouldEnableMetronome, boolean shouldLeaveEmpty) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map <String, String> setupArtifacts = setupBuildDefinition(testingFacade, 
								defaultC, buildDefinitionId, workspaceName, componentName);
		try {
			// Setup a freestyle job with build definition configuration
			FreeStyleProject prj = setupFreeStyleJobWithBuildDefinitionConfiguration(defaultC, 
									buildDefinitionId);
			
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					setReportStatisticsTrue(prj);
				} else if (shouldLeaveEmpty) {
					setReportStatisticsEmpty(prj);
				} else {
					setReportStatisticsFalse(prj);
				}
			}

			// Run the freestyle job
			Map<String, String> additonalArtifacts = new HashMap<>();
			List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
			checkAndAddMetronomeAction(pActions, shouldCreateProperty, shouldEnableMetronome,
										shouldLeaveEmpty);
			FreeStyleBuild build = Utils.runBuild(prj, pActions, 
									true,
									additonalArtifacts);
			// Add additional artifacts into setup artifacts, mainly build result UUID
			setupArtifacts.putAll(additonalArtifacts);
			
			Utils.verifyRTCScmInBuild(build, true);

			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					// Validate that the metronome files do not exist in the build root directory
					validateMetronomeContentAbsentInBuildRootDir(build);
					
					// Validate that the build result has two log files, starting with specific names
					// Ensure that the content has some strings we usually expect
					validateMetronomeContentInBuildResult(additonalArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID));
				} else {
					validateMetronomeContentAbsentInBuildRootDir(build);
					
					// Validate that the build result has two log files, starting with specific names
					// Ensure that the content has some strings we usually expect
					validateMetronomeContentAbsentInBuildResult(additonalArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID));
				}
			} else {
				validateMetronomeContentAbsentInBuildRootDir(build);
				
				// Validate that the build result has two log files, starting with specific names
				// Ensure that the content has some strings we usually expect
				validateMetronomeContentAbsentInBuildResult(additonalArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID));
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	private void helperTestMetronomeLogPropertyInBuildDefinitionAndJenkinsJob(
			boolean enablePropertyInBuildDefinition, 
			boolean enablePropertyInJenkinsJob) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> configOrGenericProperties = new HashMap<>();
		configOrGenericProperties.put("team.build.reportStatistics", 
							Boolean.toString(enablePropertyInBuildDefinition));
		Map <String, String> setupArtifacts = Utils.setupBuildDefinitionWithPBDeliver(
								defaultC.getLoginInfo(), 
								componentName, workspaceName, 
								buildDefinitionId, configOrGenericProperties);
		try {
			// Setup a freestyle job with build definition configuration
			FreeStyleProject prj = setupFreeStyleJobWithBuildDefinitionConfiguration(defaultC,
									buildDefinitionId);
			if (enablePropertyInJenkinsJob) {
				setReportStatisticsTrue(prj);
			}
			
			List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
			if (enablePropertyInJenkinsJob) {
				checkAndAddMetronomeAction(pActions, true, true, false);
			}
			Map<String, String> additonalArtifacts = new HashMap<>();
			FreeStyleBuild build = Utils.runBuild(prj, pActions, 
												true, additonalArtifacts);
			// Add additional artifacts into setup artifacts, mainly build result UUID
			setupArtifacts.putAll(additonalArtifacts);
			Utils.verifyRTCScmInBuild(build, true);
			
			
			// Validate that the metronome files with those names exist in the build root dir
			validateMetronomeContentAbsentInBuildRootDir(build);
			
			// Validate that the build result has two log files, starting with specific names
			// Ensure that the content has some strings we usually expect
			validateMetronomeContentInBuildResult(additonalArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID));
		
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	private void helperTestMetronomeLogPropertyInBuildDefinitionAndJenkinsJob_LogsNotCreated(
			boolean createPropertyInBuildDefinition) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String buildDefinitionId = getBuildDefinitionUniqueName();
		Map<String, String> configOrGenericProperties = new HashMap<>();
		if (createPropertyInBuildDefinition) {
			configOrGenericProperties.put("team.build.reportStatistics", 
										"false");
		}
		Map <String, String> setupArtifacts = Utils.setupBuildDefinitionWithPBDeliver(
							defaultC.getLoginInfo(), 
							componentName, workspaceName, 
							buildDefinitionId, configOrGenericProperties);
		try {
			// Setup a freestyle job with build definition configuration
			FreeStyleProject prj = setupFreeStyleJobWithBuildDefinitionConfiguration(defaultC,
									buildDefinitionId);
			setReportStatisticsFalse(prj);
			
			List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
			checkAndAddMetronomeAction(pActions, true, false, false);
			
			Map<String, String> additonalArtifacts = new HashMap<>();
			FreeStyleBuild build = Utils.runBuild(prj, pActions, 
												true, additonalArtifacts);
			// Add additional artifacts into setup artifacts, mainly build result UUID
			setupArtifacts.putAll(additonalArtifacts);
			Utils.verifyRTCScmInBuild(build, true);
			
			// Validate that  metronome directory does not exist in the 
			// jenkins root build directory
			validateMetronomeContentAbsentInBuildRootDir(build);
			
			// Validate that metronome log files are not present in the build result
			validateMetronomeContentAbsentInBuildResult(
					additonalArtifacts.get(Utils.ARTIFACT_BUILDRESULT_ITEM_1_ID));
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
}


	private void helperTestMetronomeLogPropertyInBuildWorkspace(boolean shouldCreateProperty,
					boolean shouldEnableMetronome, boolean shouldLeaveEmpty) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		Map<String, String> setupArtifacts = Utils.setupRepositoryWorkspace(testingFacade, 
						defaultC, workspaceName, componentName);
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForWorkspace(getJenkinsRule(),
										workspaceName);
			
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					setReportStatisticsTrue(prj);
				} else if (shouldLeaveEmpty) {
					setReportStatisticsEmpty(prj);
				} else {
					setReportStatisticsFalse(prj);
				}
			}
			// Run the freestyle job
			List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
			checkAndAddMetronomeAction(pActions, shouldCreateProperty, shouldEnableMetronome, 
						shouldLeaveEmpty);
			FreeStyleBuild build = runFreeStyleJob(prj, pActions);
			Utils.verifyRTCScmInBuild(build, false);
			
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					// Validate that the metronome files with those names exist in the build root dir
					validateMetronomeContentInBuildRootDir(build);				
				} else {
					validateMetronomeContentAbsentInBuildRootDir(build);
				}
			} else {
				validateMetronomeContentAbsentInBuildRootDir(build);
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}
	
	private void helperTestMetronomeLogPropertyInBuildSnapshot(boolean shouldCreateProperty,
				boolean shouldEnableMetronome, boolean shouldLeaveEmpty) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String workspaceName = getRepositoryWorkspaceUniqueName();
		String componentName = getComponentUniqueName();
		String snapshotName = getSnapshotUniqueName();
		Map<String, String> setupArtifacts = Utils.setupBuildSnapshot(defaultC.getLoginInfo(), 
				workspaceName, snapshotName, componentName, testingFacade);
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForSnapshot(getJenkinsRule(), snapshotName);
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					setReportStatisticsTrue(prj);
				} else if (shouldLeaveEmpty) {
					setReportStatisticsEmpty(prj);
				} else {
					setReportStatisticsFalse(prj);
				}
			}
			
			// Run the freestyle job
			List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
			checkAndAddMetronomeAction(pActions, shouldCreateProperty, shouldEnableMetronome, 
					shouldLeaveEmpty);
			FreeStyleBuild build = runFreeStyleJob(prj, pActions);
			Utils.verifyRTCScmInBuild(build, false);
			
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					// Validate that the metronome files with those names exist in the build root dir
					validateMetronomeContentInBuildRootDir(build);				
				} else {
					validateMetronomeContentAbsentInBuildRootDir(build);
				}
			} else {
				validateMetronomeContentAbsentInBuildRootDir(build);
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	private void helperTestMetronomeLogPropertyInBuildStream(boolean shouldCreateProperty, 
					boolean shouldEnableMetronome, boolean shouldLeaveEmpty) throws Exception {
		Config defaultC = Config.DEFAULT;
		RTCFacadeWrapper testingFacade = Utils.getTestingFacade();
		String streamName = getStreamUniqueName();
		Map<String, String> setupArtifacts = Utils.setUpBuildStream(testingFacade, defaultC, streamName);
		try {
			FreeStyleProject prj = Utils.setupFreeStyleJobForStream(getJenkinsRule(), defaultC, streamName);
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					setReportStatisticsTrue(prj);
				} else if (shouldLeaveEmpty) {
					setReportStatisticsEmpty(prj);
				} else {
					setReportStatisticsFalse(prj);
				}
			}
			
			// Run the freestyle job
			List<ParametersAction> pActions = Utils.getPactionsWithEmptyBuildResultUUID();
			checkAndAddMetronomeAction(pActions, shouldCreateProperty, shouldEnableMetronome, 
					shouldLeaveEmpty);
			FreeStyleBuild build = runFreeStyleJob(prj, pActions);
			Utils.verifyRTCScmInBuild(build, false);
			
			if (shouldCreateProperty) {
				if (shouldEnableMetronome) {
					// Validate that the metronome files with those names exist in the build root dir
					validateMetronomeContentInBuildRootDir(build);				
				} else {
					validateMetronomeContentAbsentInBuildRootDir(build);
				}
			} else {
				validateMetronomeContentAbsentInBuildRootDir(build);
			}
		} finally {
			Utils.tearDown(testingFacade, defaultC, setupArtifacts);
		}
	}

	private void checkAndAddMetronomeAction(List<ParametersAction> pActions, boolean shouldCreateProperty,
					boolean shouldTurnOnMetronome, boolean shouldLeaveEmptyMetronome) {
		if (shouldCreateProperty) {
			if (shouldTurnOnMetronome) {
				pActions.add(new ParametersAction(
						new StringParameterValue("team.build.reportStatistics", "true")));
			} else if (shouldLeaveEmptyMetronome){
				pActions.add(new ParametersAction(
						new StringParameterValue("team.build.reportStatistics", "")));
			} else {
				pActions.add(new ParametersAction(
						new StringParameterValue("team.build.reportStatistics", "false")));
			}
		}
	}

	private void validateMetronomeContentInBuildResult(String buildResultUUID) throws Exception {
		// TODO make sure that the log files look like metronome log files
		Utils.validateMetronomeLogsInBuildresult(buildResultUUID);
	}

	private void validateMetronomeContentInBuildRootDir(FreeStyleBuild build) throws IOException {
		File rootDir = build.getRootDir();
		File diagnosticsDir = new File(new File(rootDir, "teamconcert"), "diagnostics"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("metronome directory does not exist", diagnosticsDir.exists());
		assertTrue("metronome directory is not a directory!", diagnosticsDir.isDirectory());

		// Check for two files 
		File [] files = diagnosticsDir.listFiles();
		assertTrue(String.format("Expecting files of size 2, but got %s",  //$NON-NLS-1$
							Integer.toString(files.length)),  
							files.length == 2);
		
		// Ensure that  the file names start with statistics or statistics-report
		File  statisticsDataFile = null, statisticsReportFile = null;
		for (File f : files) {
			if (f.getName().startsWith("statisticsData-")) { //$NON-NLS-1$
				statisticsDataFile = f;
				continue;
			}
			if (f.getName().startsWith("statistics-")) { //$NON-NLS-1$
				statisticsReportFile = f;
			}
		}
		
		assertNotNull("Statistics data file not found", statisticsDataFile);
		assertNotNull("Statistics report file not found", statisticsReportFile);
		
		// assert the files end with proper suffixes
		assertTrue(statisticsDataFile.getName().endsWith(".csv"));
		assertTrue(statisticsReportFile.getName().endsWith(".log"));

		Files.copy(statisticsDataFile, new File("/tmp", statisticsDataFile.getName()));
		Files.copy(statisticsReportFile, new File("/tmp", statisticsReportFile.getName()));

		// Check the contents
        String [] statisticsDataPatterns = {"\"Interface/method\".*",
                            "\"IFilesystemService.*",
                            "\"IScmService.*",
                            "\"IVersionedContentService.*",
                            "\"ITeamBuildService.*"
        			};
        
        String [] statisticsReportPatterns = {"statistics: Service Trip Statistics.*",
                ".*Interface/method.*",
                "-- Total time in service calls.*",
                "-- Total elapsed time.*",
                ".*ITeamBuildService.*",
                ".*IScmService.*",
                ".*IVersionedContentService.*"};



		for (String pattern : statisticsDataPatterns) {
			assertNotNull(String.format("Pattern %s not found in statistics report file %s", 
					pattern, statisticsDataFile.getAbsolutePath()),  
					Utils.getMatch(statisticsDataFile, pattern));
		}
		
		for (String pattern : statisticsReportPatterns) {
			assertNotNull(String.format("Pattern %s not found in statistics data file %s", 
					pattern, statisticsReportFile.getAbsolutePath()),  
						Utils.getMatch(statisticsReportFile, pattern));
		}
	}

	private void validateMetronomeContentAbsentInBuildResult(String buildResultUUID) throws Exception {
		Utils.validateNoMetronomeLogsInBuildResult(buildResultUUID);
	}


	private void validateMetronomeContentAbsentInBuildRootDir(FreeStyleBuild build) {
		File rootDir = build.getRootDir();
		File teamconcertDir = new File(rootDir, "teamconcert");
		File diagnosticsDir = new File(teamconcertDir, "diagnostics"); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(!teamconcertDir.exists());
		assertTrue(!diagnosticsDir.exists());
	}
	
	private FreeStyleBuild runFreeStyleJob(FreeStyleProject prj, List<ParametersAction> pActions) 
										throws InterruptedException, ExecutionException {
		return Utils.runBuild(prj, pActions); 
	}

	private void setReportStatisticsTrue(FreeStyleProject prj) throws IOException {
		prj.addProperty(new ParametersDefinitionProperty(
				new StringParameterDefinition("team.build.reportStatistics", "true"))); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void setReportStatisticsFalse(FreeStyleProject prj) throws IOException {
		prj.addProperty(new ParametersDefinitionProperty(
				new StringParameterDefinition("team.build.reportStatistics", "false"))); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void setReportStatisticsEmpty(FreeStyleProject prj) throws IOException {
		prj.addProperty(new ParametersDefinitionProperty(
				new StringParameterDefinition("team.build.reportStatistics", ""))); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private FreeStyleProject setupFreeStyleJobWithBuildDefinitionConfiguration(Config defaultC, 
									String buildDefinitionId) throws Exception {
		return Utils.setupFreeStyleJobForBuildDefinition(getJenkinsRule(), buildDefinitionId);
	}

	private Map<String,String> setupBuildDefinition(RTCFacadeWrapper testingFacade, Config c, String buildDefinitionId, 
							String workspaceName, String componentName) throws Exception {
		return Utils.setupBuildDefinition(testingFacade, c, buildDefinitionId, workspaceName, componentName);
	}

	public JenkinsRule getJenkinsRule() {
		return this.r;
	}
}
