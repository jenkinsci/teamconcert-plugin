/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017, 2024. All Rights Reserved.
 * 
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule 
 * Contract with IBM Corp.
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;

public class ConsoleOutputHelper implements IConsoleOutput {
	List<String> debugMessages = new ArrayList<String>();
	List<String> infoMessages = new ArrayList<String>();
	Map<String, Exception> exceptionMessages = 
				new HashMap<String, Exception>();
	private final Exception[] failure = new Exception[] {null};

	@Override
	public void log(String message, Exception e) {
		failure[0] = e;
		exceptionMessages.put(message, e);
	}
	
	@Override
	public void log(String message) {
		infoMessages.add(message);
	}
	
	public Exception getFailure() {
		return failure[0];
	}
	
	public Collection<Exception> getFailures() {
		return exceptionMessages.values();
	}

	public boolean hasFailure() {
		return this.getFailure() != null;
	}

	@Override
	public void debug(String message) {
		debugMessages.add(message);
	}
	
	public Collection<String> getDebugMessages() {
		return debugMessages;
	}
}
