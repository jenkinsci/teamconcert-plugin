/*******************************************************************************
 * Copyright (c) 2017, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;

public class ConsoleOutputHelper implements IConsoleOutput {
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
}
