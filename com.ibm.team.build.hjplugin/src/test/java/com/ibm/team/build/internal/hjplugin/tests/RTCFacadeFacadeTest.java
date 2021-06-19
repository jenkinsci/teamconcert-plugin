/*******************************************************************************
 * Copyright (c) 2020, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.client.protocol.HttpClientContext;
import org.junit.Test;

import com.ibm.team.build.internal.hjplugin.tests.utils.AbstractTestCase;
import com.ibm.team.build.internal.hjplugin.util.HttpUtils.GetResult;
import com.ibm.team.build.internal.hjplugin.util.RTCBuildConstants;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade;
import com.ibm.team.build.internal.hjplugin.util.RTCFacadeFacade.CompatibilityResult;
import com.ibm.team.build.internal.hjplugin.util.Tuple;

import hudson.Util;
import hudson.model.TaskListener;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class RTCFacadeFacadeTest extends AbstractTestCase {
	
	/**
	 * Test the server version comparison logic
	 * 
	 * @throws Exception
	 */
	@Test public void testServerVersionComparisonLogic() throws Exception {
		// Call isServerVersionEqualOrHigher with different string versions
		
		// Two digit server version vs two digit min server version
		// Equal
		// server version is greater
		// minimum server version is greater
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0", "6.0"));
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0", "6.0"));
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0", "7.0"));
		
		// Two digit server version vs three digit min server version
		// server version is greater than minimum server version and former is not a prefix
		// server version is lesser than minimum server version and latter is a prefix
		// server version is lesser than minimum server version and latter is not a prefix 
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0", "6.0.5"));
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0", "6.0.1"));
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0", "7.0"));
		
		// Two digit server version vs four digit min server version
		// server version is greater than minimum server version and the former is not a prefix
		// server version is lesser than minimum server version and is a prefix
		// server version is lesser than minimum server version and is not a prefix 
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0", "6.0.6.1"));
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0", "6.0.6.1"));
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("5.0", "6.0.6.1"));
		
		// Three digit server version vs two digit minimum server version
		// 3 digit server version is greater than 2 digit minimum server version and the latter is a prefix
		// 3 digit server version is greater than 2 digit minimum server version and the latter is not a prefix
		// NOT Possible 3 digit server version is lesser than 2 digit minimum server version and the former is a prefix
		// 3 digit server version is lesser than 2 digit minimum server version and the former is not a prefix 
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0.1", "7.0"));
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0.1", "6.0"));
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6", "7.0"));
		
		// 3 digit server version is equal to 3 digit minimum server version
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.4", "6.0.4")); // case 1

		// 3 digit server version is greater than 3 digit minimum server version and the latter is a prefix
		// 3 digit server version is greater than 3 digit minimum server version and the latter is not a prefix
		// 3 digit server version is lesser than 3 digit minimum server version and the former is a prefix
		// 3 digit server version is lesser than 3 digit minimum server version and the former is not a prefix
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.4", "6.0.3")); // case 1
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0.4", "6.0.5")); // case 2
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.4", "6.0.5")); // case 3
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.4", "7.0.1")); // case 4
		
		// Not possible - 3 digit server version is greater than 4 digit minimum server version and the latter is a prefix
		// 3 digit server version is greater than 4 digit minimum server version and the latter is not a prefix
		// 3 digit server version is lesser than 4 digit minimum server version and the former is a prefix
		// 3 digit server version is lesser than 4 digit minimum server version and the former is not a prefix
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0.2", "6.0.6.1")); // case 2
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6", "6.0.6.1")); // case 3
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("5.0.2", "6.0.6.1")); // case 3
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.3", "6.0.6.1")); // case 4

		// 4 digit server version is greater than 2 digit minimum server version and the latter is a prefix
		// 4 digit server version is greater than 2 digit minimum server version and the latter is not a prefix
		// Not possible - 4 digit server version is lesser than 2 digit minimum server version and the former is a prefix
		// 4 digit server version is lesser than 2 digit minimum server version and the former is not a prefix
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "6.0")); // case 1
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "5.0")); // case 2
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "7.0")); // case 4
		
		// 4 digit server version is greater than 3 digit minimum server version and the latter is a prefix
		// 4 digit server version is greater than 3 digit minimum server version and the latter is not a prefix
		// Not possible 4 digit server version is lesser than 3 digit minimum server version and the former is a prefix
		// 4 digit server version is lesser than 3 digit minimum server version and the former is not a prefix
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "6.0.6")); // case 1
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "6.0.2")); // case 2
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "5.0.2")); // case 2
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "7.0.2")); // case 4
		
		// 4 digit server version is equal to 4 digit minimum server version and the latter is a prefix
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "6.0.6.1")); // case 1

		// 4 digit server version is greater than 4 digit minimum server version and the latter is a prefix
		// 4 digit server version is greater than 4 digit minimum server version and the latter is not a prefix
		// 4 digit server version is lesser than 4 digit minimum server version and the former is a prefix
		// 4 digit server version is lesser than 4 digit minimum server version and the former is not a prefix
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.2", "6.0.6.1")); // case 2
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.2", "6.0.4.2")); // case 2
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.2", "5.0.2.1")); // case 3
		assertTrue(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("7.0.6.2", "6.0.2.1")); // case 3
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.6.1", "6.0.6.2")); // case 4
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.3.1", "6.0.6.2")); // case 4
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("5.0.3.1", "6.0.6.2")); // case 5
		assertFalse(RTCFacadeFacadeHelper.isServerVersionEqualOrHigher("6.0.3.1", "7.0.6.2")); // case 5

	}
	
	/**
	 * Test the logic of extracting server version from a string which has both 
	 * server version and milestone
	 * 
	 * @throws Exception
	 */
	@Test public void testServerVersionExtractionLogic() throws Exception {
		// Call extractServerVersionWithoutMilestone for different patterns
		
		assertEquals(null, RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("603fdwvM3"));
		
		// Two digits
		assertEquals("6.0", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0"));
		
		// Two digits with milestone (M1, RC1, Final)
		assertEquals("7.0", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("7.0M1"));
		assertEquals("7.0", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("7.0RC1"));
		assertEquals("7.0", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("7.0Final"));
		
		// Three digits
		assertEquals("7.0.1", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("7.0.1"));
		
		// Three digits with milestone (M2, RC3, Final)
		assertEquals("6.0.2", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.2M2"));
		assertEquals("6.0.2", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.2RC3"));
		assertEquals("6.0.2", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.2Final"));
		
		// Four digits
		assertEquals("6.0.6.1", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.6.1"));

		
		// Four digits with milestone (M3, RC4, Final)
		assertEquals("6.0.6.1", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.6.1M3"));
		assertEquals("6.0.6.1", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.6.1RC4"));
		assertEquals("6.0.6.1", RTCFacadeFacadeHelper.extractServerVersionWithoutMilestone("6.0.6.1Final"));
	}
	

	/**
	 * Validate that {@link RTCFacadeFacade#testHTTPConnectionHelper} handles 
	 * {@link InvalidCredentialsException} from validateCredentials and 
	 * throws the exception appropriately.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestHTTPConnectionHelperForInvalidCredentials() throws Exception {
		// Inspect that errorMessage is set
		// serverVersion is not set
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getInvalidCredentialsExceptionValidateCredsRunnable());

		Tuple<CompatibilityResult, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTPHelper("https://localhost:9443/ccm", "test", 
											"test", 100, RTCBuildConstants.URI_COMPATIBILITY_CHECK);
		CompatibilityResult r = t.getFirst();
		assertEquals("Invalid creds", r.getErrorMessage());
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Verify that {@link RTCFacadeFacade#testHTTPConnectionHelper} handles 
	 * {@link IOException} when validating credentials and 
	 * throws the exception appropriately.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestHTTPConnectionHelperHandlesIOException() throws Exception {
		// Inspect that errorMessage is set
		// serverVersion is not set
		// verify that the call counts is 
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getIOExceptionValidateCredsRunnable());

		Tuple<CompatibilityResult, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTPHelper("https://localhost:9443/ccm", "test", 
											"test", 100, RTCBuildConstants.URI_COMPATIBILITY_CHECK);
		CompatibilityResult r = t.getFirst();
		assertEquals("Throwing some io exception in validateCredentials", r.getErrorMessage());
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Verify that {@link RTCFacadeFacade#testHTTPConnectionHelper} does handles 
	 * {@link IOException} when validating credentials and throws 
	 * it back. 
	 * Catch the exception and verify the message
	 * 
	 * @throws Exception
	 */
	@Test public void testTestHTTPConnectionHelperDoesNotHandleGeneralSecurityException() throws Exception {
		// Make sure that an exception is received and not handled by the 
		// validate credentials method
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getGeneralSecurityExceptionValidateCredsRunnable());

		try {
			Tuple<CompatibilityResult, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTPHelper("https://localhost:9443/ccm", "test", 
												"test", 100, RTCBuildConstants.URI_COMPATIBILITY_CHECK);
			fail("Expecting a general security exception");
		} catch (Exception exp) {
			assertTrue(exp instanceof GeneralSecurityException);
			assertEquals(exp.getMessage(), exp.getMessage(), "General security exception is thrown in validateCredentials.");
		}
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTPHelper} handles compatibility error 
	 * from server and sets the message appropriately.
	 * 
	 * @throws Exception
	 */
	@Test public void testHTTPConnectionHelperHandlesCompatibilityError() throws Exception {
		// Return compatibility result data from performGet
		// Make sure that the return value contains the server version and error message
		// validateCredentials should not have been called.
		String serverVersion = "6.0.6.1";
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable(serverVersion));
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getGeneralSecurityExceptionValidateCredsRunnable());

		Tuple<CompatibilityResult, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTPHelper("https://localhost:9443/ccm", "test", 
											"test", 100, RTCBuildConstants.URI_COMPATIBILITY_CHECK);
		CompatibilityResult r = t.getFirst();
		assertEquals("The Team Concert server does not have the support required to avoid using the toolkit : "
									+ "Your server is version " + serverVersion , Util.fixEmptyAndTrim(r.getErrorMessage()));
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(0, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testHTTpConnectionHelper} validates credentials if there is 
	 * no compatibility error 
	 * 
	 * @throws Exception
	 */
	@Test public void testHTTPConnectionHelperValidatesCredsIfThereIsNoCompatibilityError() throws Exception {
		// Pass the compatibility check
		// Make sure that validate credentials method is called
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());

		Tuple<CompatibilityResult, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTPHelper("https://localhost:9443/ccm", "test", 
											"test", 100, RTCBuildConstants.URI_COMPATIBILITY_CHECK);
		CompatibilityResult r = t.getFirst();
		assertEquals(null, Util.fixEmptyAndTrim(r.getErrorMessage()));
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
		
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} handles invalid server version properly 
	 * and adds an additional message to the existing error message.
	 * 
	 * In this case, performGet is called only once and validateCredentials is not called.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPWithInvalidServerVersionInCompatibilityResult() throws Exception {
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerWithInvalidVersionStringGetRunnable());
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", 
				"test", 100, RTCBuildConstants.URI_COMPATIBILITY_CHECK, RTCBuildConstants.URI_COMPATIBILITY_CHECK);
		String errorMessage = t.getFirst();
		assertTrue(Util.fixEmptyAndTrim(errorMessage), 
				Util.fixEmptyAndTrim(errorMessage).contains("Unable to extract server information from version compatibility service response."));
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(0, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}

	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} does not retry compatibility check 
	 * if it receives invalid credentials error.
	 * 
	 * performGet is called only once and validate credentials is also called. But the latter throws an 
	 * exception.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPDoesNotRetryCompatibilityCheckIfInvalidCredentials() throws Exception {
		// Pass the compatibility check
		// Make sure that testHTTPConnectionHelper is called only once
		// Make sure that validateCredentials is called only once but it throws an exception
	
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getInvalidCredentialsExceptionValidateCredsRunnable());
		
		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
				 RTCBuildConstants.URI_COMPATIBILITY_CHECK, RTCBuildConstants.MINIMUM_SERVER_VERSION);
		assertTrue(t.getFirst(), t.getFirst().equals("Invalid creds"));
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} does not retry compatibility if there is an 
	 * IOException in the first performGet call.
	 * 
	 * performGet is called only once
	 * validateCredentials is not called at all.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPDoesNotRetryCompatibilityCheckIfIOExceptionInPerformGet() throws Exception {
		// Pass the compatibility check
		// Make sure that testHTTPConnectionHelper is called only once but throws io exception
		// Make sure that validateCredentials is not called at all
		RTCFacadeFacadeHelper.reset();
		
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getIOExceptionGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
				 RTCBuildConstants.URI_COMPATIBILITY_CHECK, RTCBuildConstants.MINIMUM_SERVER_VERSION);
		assertTrue(t.getFirst(), t.getFirst().equals("Throwing some io exception in performGet"));
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(0, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} does not retry compatibility if there is an 
	 * IOException in the second performGet call.
	 * 
	 * performGet is called twice.
	 * validateCredentials is not called at all.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPDoesNotRetryCompatibilityCheckIfIOExceptionInPerformGet2() throws Exception {
		// Pass the compatibility check
		// Make sure that testHTTPConnectionHelper is called twice. It should throw io exception in the second call
		// Make sure that validateCredentials is not called at all
		RTCFacadeFacadeHelper.reset();
		
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable("7.0.2"));
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getIOExceptionGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
				 RTCBuildConstants.URI_COMPATIBILITY_CHECK, RTCBuildConstants.MINIMUM_SERVER_VERSION);
		assertTrue(t.getFirst(), t.getFirst().equals("Throwing some io exception in performGet"));
		assertEquals(2, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(0, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} does not retry compatibility if there is an 
	 * IOException in the validateCredentials
	 * 
	 * performGet is called once.
	 * validateCredentials is also called once.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPDoesNotRetryCompatibilityCheckIfIOExceptionInValidateCreds() throws Exception {
		// Pass the compatibility check
		// Make sure that testHTTPConnectionHelper is called only once
		// Make sure that validateCredentials is called only once but it throws an IO exception
		RTCFacadeFacadeHelper.reset();
		
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getIOExceptionValidateCredsRunnable());
		
		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
				 RTCBuildConstants.URI_COMPATIBILITY_CHECK, RTCBuildConstants.MINIMUM_SERVER_VERSION);
		assertTrue(t.getFirst(), t.getFirst().equals("Throwing some io exception in validateCredentials"));
		assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	// Test for positive and negative cases for testConnectionHTTP - creds are valid in all cases
	// No retry - server is compatible with minimum server version 6.0
	// Retry - server is not compatible with 6.0 but is compatible with its own version and is greater 
	//        - than 6.0
	// No retry - server is compatible with minimum server version 6.0.4
	// Retry - server is not compatible with 6.0. but is compatible with its own version and is greater 
	//        - than 6.0.4
	// No retry - server is compatible with minimum server version 6.0.6.1
	// Retry - server is not compatible with 6.0 but is compatible with its own version and is greater 
	//        - than 6.0.6.1
	// No retry - server is compatible with minimum server version 7.0
	// Retry - server is not compatible with 6.0 but is compatible with its own version and is greater 
	//        - than 7.0
	// No retry - server is 5.0 and min compatibile version is 6.0
	// No retry - server is 6.0 and min compatibile version is 6.0.4
	// No retry - server is 6.0.6.1 and min compatibile version is 7.0
	
	/**
	 * If the server version is greater than the minimum server version and the server accepts the 
	 * minimum server version in the first go, then performGet and validateCredentials is called only once
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPDoesNotRetryCompatibilityCheckIfThereAreNoErrors() throws Exception {
		// Pass the compatibility check
		// Make sure that testHTTPConnectionHelper is called only once
		// Make sure that validateCredentials is called only once.
		RTCFacadeFacadeHelper.reset();

		// No retries - everything goes through
		for (String minServerVersion : new String [] {RTCBuildConstants.MINIMUM_SERVER_VERSION, 
		                                           RTCBuildConstants.MINIMUM_SERVER_VERSION_FOR_PBDELIVER, 
		                                           "7.0", "7.0.1"}) {
			RTCFacadeFacadeHelper.reset();
			RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getCompatibleServerGetRunnable());
			RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
	
			Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
					 RTCBuildConstants.URI_COMPATIBILITY_CHECK, minServerVersion);
			assertTrue(Util.fixEmptyAndTrim(t.getFirst()) == null);
			assertEquals(1, RTCFacadeFacadeHelper.getPerformGetCallCount());
			assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
		}
	}

	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} retries compatibility check if there is a 
	 * compatibility failure in the first attempt. In all these cases, the server version is 
	 * greater than the minimum server version. But the server rejects the first compatibility request 
	 * in the first attempt. 
	 * 
	 * We validate the following 
	 * 1. performGet is called twice
	 * 2. validateCredentials is called once
	 * 3. In the second call to performGet, we verify that the compatibility URI is called with 
	 *    the server version directly, since we know that it is greater than the minimum server version 
	 *    requirement.  
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPRetriesCompatibilityCheckIfCompatibilityFailure() throws Exception {
	    // Tests for higher server version and lower minimum server version 
	    // With retries - things will go through
		
		// Reset and try with 6.0 and 6.0.6
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable("6.0.6"));
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getCompatibleServerGetRunnableWithChecks(
				RTCBuildConstants.URI_COMPATIBILITY_CHECK_WITHOUT_VERSION + "6.0.6"));
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
									RTCBuildConstants.URI_COMPATIBILITY_CHECK, "6.0");
		assertTrue(Util.fixEmptyAndTrim(t.getFirst()) == null);
		assertEquals(2, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
		
		// Reset and try with 6.0.4 and 6.0.6.1 
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable("6.0.6.1"));
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getCompatibleServerGetRunnableWithChecks(
				RTCBuildConstants.URI_COMPATIBILITY_CHECK_WITHOUT_VERSION + "6.0.6.1"));
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
									RTCBuildConstants.URI_COMPATIBILITY_CHECK, "6.0.4");
		assertTrue(Util.fixEmptyAndTrim(t.getFirst()) == null);
		assertEquals(2, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
		
		// Reset and try with 6.0.6.1 and 7.0 
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable("7.0"));
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getCompatibleServerGetRunnableWithChecks(
				RTCBuildConstants.URI_COMPATIBILITY_CHECK_WITHOUT_VERSION + "7.0"));
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
									RTCBuildConstants.URI_COMPATIBILITY_CHECK, "6.0.6.1");
		assertTrue(Util.fixEmptyAndTrim(t.getFirst()) == null);
		assertEquals(2, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
		
		// Reset and try with 7.0 and 7.0.2 
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable("7.0.2"));
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getCompatibleServerGetRunnableWithChecks(
				RTCBuildConstants.URI_COMPATIBILITY_CHECK_WITHOUT_VERSION + "7.0.2"));
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());
		
		t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
				RTCBuildConstants.URI_COMPATIBILITY_CHECK, "7.0");
		assertTrue(Util.fixEmptyAndTrim(t.getFirst()) == null);
		assertEquals(2, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(1, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
	
	/**
	 * Validate that {@link RTCFacadeFacade#testConnectionHTTP} does not retry compatibility check 
	 * if there is a compatibility failure due to lower server version and higher server compatibility 
	 * version requirement. 
	 * 
	 * In all these cases, performGet is called only once and validate credentials not called at all.
	 * 
	 * @throws Exception
	 */
	@Test public void testTestConnectionHTTPDoesNotRetryCompatibilityCheckIfCompatibilityFailure() throws Exception {
		// Tests for lower server version and higher minimum server version
		// No retries - it fails
		testTestConnectionHTTPRetriesHelper("6.0", "7.0", 1, 0);
		testTestConnectionHTTPRetriesHelper("6.0", "6.0.4", 1, 0);
		testTestConnectionHTTPRetriesHelper("6.0.6.1", "7.0.2", 1, 0);
	}

	private void testTestConnectionHTTPRetriesHelper(String serverVersion, String minServerVersion,
				int expectedPerformGetCallCount, int expectedValidateCredentialsCallCount) throws Exception {
		RTCFacadeFacadeHelper.reset();
		RTCFacadeFacadeHelper.setPerformGetRunnable(RTCFacadeFacadeHelper.getInCompatibleServerGetRunnable(serverVersion));
		RTCFacadeFacadeHelper.setPerformGetRunnable2(RTCFacadeFacadeHelper.getIOExceptionGetRunnable());
		RTCFacadeFacadeHelper.setValidateCredentialsRunnable(RTCFacadeFacadeHelper.getValidValidateCredsRunnable());

		Tuple<String, GetResult> t = RTCFacadeFacadeHelper.testConnectionHTTP("https://localhost:9443/ccm", "test", "test", 100, 
				 RTCBuildConstants.URI_COMPATIBILITY_CHECK, minServerVersion);
		assertTrue(Util.fixEmptyAndTrim(t.getFirst()), 
				("The Team Concert server does not have the support required to avoid using the toolkit : "
						+ "Your server is version " + serverVersion).equals(Util.fixEmptyAndTrim(t.getFirst())));
		assertEquals(expectedPerformGetCallCount, RTCFacadeFacadeHelper.getPerformGetCallCount());
		assertEquals(expectedValidateCredentialsCallCount, RTCFacadeFacadeHelper.getValidateCredentialsCallCount());
	}
}

class RTCFacadeFacadeHelper extends RTCFacadeFacade {
	static int performGetCallCount = 0;
	static int validateCredentialsCallCount = 0;
	static Runnable<? extends GetResult> performGetRunnable = null;
	static Runnable<? extends GetResult> performGetRunnable2 = null;
	static Runnable<Void> validateCredentialsRunnable = null;
	
	static void reset() {
		performGetCallCount = 0;
		validateCredentialsCallCount = 0;
		performGetRunnable = null;
		performGetRunnable2 = null;
		validateCredentialsRunnable = null;
		RTCFacadeFacade.setHttpUtilsHelper(new MockHttpUtilsHelper());
	}

	static int getPerformGetCallCount() {
		return performGetCallCount;
	}
	
	static int getValidateCredentialsCallCount() {
		return validateCredentialsCallCount;
	}
	
	static void setPerformGetRunnable(Runnable<? extends GetResult> r) {
		performGetRunnable = r;
	}
	
	static void setPerformGetRunnable2(Runnable<? extends GetResult> r) {
		performGetRunnable2 = r;
	}
	
	static void setValidateCredentialsRunnable(Runnable<Void> r) {
		validateCredentialsRunnable = r;
	}
	
	public static class MockHttpUtilsHelper extends HttpUtilsHelper {
		@Override
		public GetResult performGet(String serverURI, String uri, String userId, String password, int timeout,
				HttpClientContext context, TaskListener listener) throws InvalidCredentialsException, IOException, GeneralSecurityException {
			performGetCallCount++;
			Runnable<? extends GetResult> runnable = null;
			if (performGetCallCount == 1) {
				runnable = performGetRunnable == null ? null : performGetRunnable;
			} else if (performGetCallCount == 2) {
				runnable = performGetRunnable2 == null ? null : performGetRunnable2;
			} else {
				runnable = null;
			}
			if (runnable != null) {
				return runnable instanceof HttpMatcherRunnable ? ((HttpMatcherRunnable<? extends GetResult>)runnable).run(uri): runnable.run(); 
			} else {
				return null;
			}
		}
		
		@Override
		public void validateCredentials(String serverURI, String userId, String password, int timeout)
				throws org.apache.http.auth.InvalidCredentialsException, IOException, GeneralSecurityException {
			validateCredentialsCallCount++;
			if (validateCredentialsRunnable == null) {
				return;
			}
			validateCredentialsRunnable.run();
		}
	}
	
	static Runnable<GetResult> getIOExceptionGetRunnable() {
		return new Runnable<GetResult>() {
			public GetResult run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				throw new IOException("Throwing some io exception in performGet");

			}
		};
	}
	
	static Runnable<GetResult> getCompatibleServerGetRunnable() {
		return new Runnable<GetResult>() {
			public GetResult run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				JSON j = JSONObject.fromObject("{\"compatible\":true,\"isJTS\":false}");
				GetResult gt = new GetResult(null, j);
				return gt;
			}
		};
	}
	
	static HttpMatcherRunnable<GetResult> getCompatibleServerGetRunnableWithChecks(String expectedUri) {
		return new HttpMatcherRunnable<GetResult>() {
			public GetResult run(String uri) throws InvalidCredentialsException, IOException, GeneralSecurityException {
				assertEquals(expectedUri, uri);
				return run();
			}
			
			public GetResult run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				JSON j = JSONObject.fromObject("{\"compatible\":true,\"isJTS\":false}");
				GetResult gt = new GetResult(null, j);
				return gt;
			}
		};
	}
	
	static Runnable<GetResult> getInCompatibleServerGetRunnable(String serverVersion) {
		return new Runnable<GetResult>() {
			public GetResult run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				JSON j = JSONObject.fromObject("{\"compatible\":false,\"serverVersion\":\""+ serverVersion +
						"\",\"isJTS\":false,\"message\":\"Your client is incompatible with " + serverVersion + " server." +
						"Please upgrade to" + serverVersion + "client.\",\"uri\":\"https:\\/\\/localhost:9443\\/ccm\"}"); 
				GetResult gt = new GetResult(null, j);
				return gt;
			}
		};
	}
	

	static Runnable<GetResult> getInCompatibleServerWithInvalidVersionStringGetRunnable() {
		return new Runnable<GetResult>() {
			String invalidServerVersion = "603fdwvM3";
			public GetResult run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				JSON j = JSONObject.fromObject("{\"compatible\":false,\"serverVersion\":\""+ invalidServerVersion +
						"\",\"isJTS\":false,\"message\":\"Your client is incompatible with " + invalidServerVersion + " server." +
						"Please upgrade to" + invalidServerVersion + "client.\",\"uri\":\"https:\\/\\/localhost:9443\\/ccm\"}"); 
				GetResult gt = new GetResult(null, j);
				return gt;
			}
		};
	}
	
	static Runnable<Void> getInvalidCredentialsExceptionValidateCredsRunnable() {
		return new Runnable<Void>() {
			public Void run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				throw new InvalidCredentialsException("Invalid creds");
			}
		};
	}
	
	static Runnable<Void> getGeneralSecurityExceptionValidateCredsRunnable() {
		return new Runnable<Void>() {
			public Void run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				throw new GeneralSecurityException("General security exception is thrown in validateCredentials.");
			}
		};
	}
	
	static Runnable<Void> getIOExceptionValidateCredsRunnable() {
		return new Runnable<Void>() {
			public Void run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				throw new IOException("Throwing some io exception in validateCredentials");
			}
		};
	}
	
	static Runnable<Void> getValidValidateCredsRunnable() {
		return new Runnable<Void>() {
			public Void run() throws InvalidCredentialsException, IOException, GeneralSecurityException {
				return null;
			}
		};
	}
	
	public static String extractServerVersionWithoutMilestone(String serverVersionWithMilestone) {
		return RTCFacadeFacade.extractServerVersionWithoutMilestone(serverVersionWithMilestone);
	}
	
	public static boolean isServerVersionEqualOrHigher(String serverVersionWithoutMilestone, 
							String minimumServerVersion) {
		return RTCFacadeFacade.isServerVersionEqualOrHigher(serverVersionWithoutMilestone, minimumServerVersion);
	}
	
	public static Tuple<String, GetResult> testConnectionHTTP(String serverURI, String userId, String password, int timeout,
			String compatibilityURI, String minimumServerVersion) throws Exception {
		return RTCFacadeFacade.testConnectionHTTP(serverURI, userId, password, timeout, compatibilityURI, minimumServerVersion);
	}
	
	public static Tuple<CompatibilityResult, GetResult> testConnectionHTTPHelper(String serverURI, String userId, String password, int timeout,
														String compatibilityURI) throws Exception {
		return RTCFacadeFacade.testConnectionHTTPHelper(serverURI, userId, password, timeout, compatibilityURI);
	}
	
	interface Runnable<T> {
		T run() throws InvalidCredentialsException, IOException, GeneralSecurityException;
	}

	interface HttpMatcherRunnable<T> extends Runnable<T> {
		T run(String matcherURI) throws InvalidCredentialsException, IOException, GeneralSecurityException;
	}
}

