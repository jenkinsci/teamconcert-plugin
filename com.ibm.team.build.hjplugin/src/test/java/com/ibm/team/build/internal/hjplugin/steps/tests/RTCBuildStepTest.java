/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.steps.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.ibm.team.build.internal.hjplugin.Messages;
import com.ibm.team.build.internal.hjplugin.steps.RTCBuildStep;
import com.ibm.team.build.internal.hjplugin.steps.RTCBuildStep.DescriptorImpl;
import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.util.Helper;

import hudson.util.FormValidation;

/**
 * 
 * Unit tests for {@link RTCBuildStep}
 * 
 */
public class RTCBuildStepTest extends AbstractTestCase {

	@Rule public JenkinsRule r = new JenkinsRule();

	@Test
	public void testBuildStateValidationValidStates() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
							getDescriptor(RTCBuildStep.class);
		
		// Valid states
		String buildStatesStr = "INCOMPLETE, NOT_STARTED, COMPLETED, ";
		FormValidation fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.OK, fv.kind);
	}
	
	@Test
	public void testBuildStateValidationNoStateString() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
							getDescriptor(RTCBuildStep.class);

		// Valid states
		String buildStatesStr = "";
		FormValidation fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_buildStates_required(), fv.getMessage());
	}
	
	@Test
	public void testBuildStateValidationValidStatesEndsInComma() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
							getDescriptor(RTCBuildStep.class);
		// Valid states
		String buildStatesStr = "IN_PROGRESS,COMPLETED,";
		FormValidation fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.OK, fv.kind);
	}
	
	@Test
	public void testBuildStateValidationInvalidStates() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
							getDescriptor(RTCBuildStep.class);

		// One invalid state
		String buildStatesStr = "INCOMPLETE, NOT_STARTED, ABC, COMPLETED, ";
		FormValidation fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(
				Messages.RTCBuildStep_invalid_build_states_1(
					String.join(",", "ABC"), String.join(",", Helper.getAllBuildStates())).replace("\"", "&quot;")
				, fv.getMessage());
		
		// Two invalid states
		buildStatesStr = "INCOMPLETE, EFG, NOT_STARTED, ABC, COMPLETED, ";
		fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_invalid_build_states_1(
						String.join(",", "EFG", "ABC"),
						String.join(",", Helper.getAllBuildStates())).replace("\"", "&quot;")
				, fv.getMessage());
	}
	
	@Test
	public void testBuildStateValidationInvalidStatesWithDuplicate() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
					getDescriptor(RTCBuildStep.class);

		// One or more invalid states with duplicate valid state
		// Only the error is sent but not info about duplicate states
		String buildStatesStr = "INCOMPLETE, NOT_STARTED, ABC, COMPLETED, INCOMPLETE ";
		FormValidation fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(
				Messages.RTCBuildStep_invalid_build_states_1(
						String.join(",", "ABC"),
						String.join(",", Helper.getAllBuildStates())).replace("\"", "&quot;")
				, fv.getMessage());

		// One or more invalid states with duplicate invalid state
		// Only the error is sent but not info about duplicate states
		buildStatesStr = "INCOMPLETE, NOT_STARTED, ABC, COMPLETED, ABC ";
		fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_invalid_build_states_1(
							String.join(",", "ABC"),
							String.join(",", Helper.getAllBuildStates())).replace("\"", "&quot;")
				, fv.getMessage());
	}
	
	@Test
	public void testBuildStateValidationValidStatesWithDuplicate() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
							getDescriptor(RTCBuildStep.class);

		// Valid states with duplicates.
		String buildStatesStr = "INCOMPLETE, NOT_STARTED, INCOMPLETE, "
				+ "COMPLETED, NOT_STARTED ";
		FormValidation fv = descriptor.doCheckBuildStates(buildStatesStr);
		assertEquals(FormValidation.Kind.WARNING, fv.kind);
		assertEquals(Messages.RTCBuildStep_build_states_repeated(
				String.join(",","INCOMPLETE", "NOT_STARTED"))
				, fv.getMessage());
	}
	
	@Test
	public void testDoCheckDestinationFileNameValidation() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
				getDescriptor(RTCBuildStep.class);

		// Provide a file name with relative separator characters
		String fileName = "abc/efgh";
		FormValidation fv = descriptor.doCheckDestinationFileName(fileName);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_destination_file_name_ispath(fileName).
				replace("\"", "&quot;"),
				fv.getMessage());
		
		// Provide a parameter and it should be OK
		fileName = "${abcdefgh}";
		fv = descriptor.doCheckDestinationFileName(fileName);
		assertEquals(FormValidation.Kind.OK, fv.kind);
	}
	
	@Test
	public void testDoCheckFileNameOrPattern() throws Exception {
		
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
				getDescriptor(RTCBuildStep.class);
		
		String invalidPattern = "abc[12][12[";
		FormValidation fv = descriptor.doCheckFileNameOrPattern(invalidPattern);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_file_name_pattern_invalid(invalidPattern).
				replace("\"", "&quot;"),
				fv.getMessage());
		
		// Provide a parameter and it should be OK
		String validPattern = "${abcdefgh}";
		fv = descriptor.doCheckFileNameOrPattern(validPattern);
		assertEquals(FormValidation.Kind.OK, fv.kind);
	}
	
	@Test
	public void testDoCheckMaxResults() throws Exception {
		DescriptorImpl descriptor = (DescriptorImpl) r.getInstance().
				getDescriptor(RTCBuildStep.class);
		
		// Empty max results
		String maxResults = null;
		FormValidation fv = descriptor.doCheckMaxResults(maxResults);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_maxResults_empty(),
				fv.getMessage());
		
		// Negative max results, 0 max results
		for (int i : new int [] {0 , -12}) {
			maxResults = Integer.toString(i);
			fv = descriptor.doCheckMaxResults(maxResults);
			assertEquals(FormValidation.Kind.ERROR, fv.kind);
			assertEquals(Messages.RTCBuildStep_maxResults_invalid_value(maxResults).
					replace("\"", "&quot;"),
					fv.getMessage());
		}
		
		// Value greater than 2048
		maxResults = "2049";
		fv = descriptor.doCheckMaxResults(maxResults);
		assertEquals(FormValidation.Kind.ERROR, fv.kind);
		assertEquals(Messages.RTCBuildStep_maxResults_invalid_value_greater_than_2048().
				replace("\"", "&quot;"),
				fv.getMessage());
	}
	
}
