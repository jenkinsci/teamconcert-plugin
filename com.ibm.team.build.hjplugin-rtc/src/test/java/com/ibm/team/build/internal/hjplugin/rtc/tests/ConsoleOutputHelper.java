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

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import org.eclipse.core.runtime.AssertionFailedException;

import com.ibm.team.build.internal.hjplugin.rtc.IConsoleOutput;

public class ConsoleOutputHelper implements IConsoleOutput {
	private final Exception[] failure = new Exception[] {null};

	@Override
	public void log(String message, Exception e) {
		failure[0] = e;
	}
	
	@Override
	public void log(String message) {
		// not good
		throw new AssertionFailedException(message);
	}
	
	public Exception getFailure() {
		return failure[0];
	}

	public boolean hasFailure() {
		return this.getFailure() != null;
	}
}
