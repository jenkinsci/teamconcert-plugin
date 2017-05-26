/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.util.PostBuildDeliverTriggerPolicy;

import hudson.model.Result;

public class PostBuildDeliverTriggerPolicyTest extends AbstractTestCase {

	@Test
	public void testMatchNoErrorsWithBuildResultSuccess() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_ERRORS;
		Result resultSuccess = Result.SUCCESS;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchNoErrorsWithBuildResultUnstable() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_ERRORS;
		Result resultSuccess = Result.UNSTABLE;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchNoErrorsWithBuildResultFailure() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_ERRORS;
		Result resultSuccess = Result.FAILURE;
		assertFalse(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchNoErrorsWithBuildResultAborted() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_ERRORS;
		Result resultSuccess = Result.ABORTED;
		assertFalse(triggerPolicy.matches(resultSuccess));
	} 
	
	@Test
	public void testMatchNoWarningsWithBuildResultSuccess() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_WARNINGS;
		Result resultSuccess = Result.SUCCESS;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchNoWarningsWithBuildResultUnstable() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_WARNINGS;
		Result resultSuccess = Result.UNSTABLE;
		assertFalse(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchNoWarningsWithBuildResultFailure() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_WARNINGS;
		Result resultSuccess = Result.FAILURE;
		assertFalse(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchNoWarningsWithBuildResultAborted() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.NO_WARNINGS;
		Result resultSuccess = Result.ABORTED;
		assertFalse(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchAlwaysWithBuildResultSuccess() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.ALWAYS;
		Result resultSuccess = Result.SUCCESS;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchAlwaysWithBuildResultUnstable() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.ALWAYS;
		Result resultSuccess = Result.UNSTABLE;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchAlwaysWithBuildResultFailure() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.ALWAYS;
		Result resultSuccess = Result.FAILURE;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
	
	@Test
	public void testMatchAlwaysWithBuildResultAborted() {
		PostBuildDeliverTriggerPolicy triggerPolicy = PostBuildDeliverTriggerPolicy.ALWAYS;
		Result resultSuccess = Result.ABORTED;
		assertTrue(triggerPolicy.matches(resultSuccess));
	}
}
