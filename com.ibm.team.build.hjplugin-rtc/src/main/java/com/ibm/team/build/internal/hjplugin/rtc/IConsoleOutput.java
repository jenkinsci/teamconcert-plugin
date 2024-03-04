/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2013, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

/**
 * Facade in front of the Hudson/Jenkins listener
 * Used so we can provide messages to the listener within the RTC specific functionality
 */
public interface IConsoleOutput {
	
	/**
	 * Log an information message to any listeners
	 * @param message The message to log
	 */
	public void log(String message);
	
	/**
	 * Log an error message to any listeners
	 * @param message The message to log
	 * @param e The error exception to log
	 */
	public void log(String message, Exception e);
	
	/**
	 * Print out a message if debug messages can be 
	 * printed out. Otherwise ignored.
	 */
	public void debug(String message); 
}
