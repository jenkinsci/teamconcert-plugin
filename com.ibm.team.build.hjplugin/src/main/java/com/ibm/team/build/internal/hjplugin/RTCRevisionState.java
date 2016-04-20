/*******************************************************************************
 * Copyright (c) 2015, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin;

import hudson.scm.SCMRevisionState;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * This class implements RTC specific revision state management.
 *
 */
public class RTCRevisionState extends SCMRevisionState implements Serializable {
	private static final long serialVersionUID = 1L;
	private BigInteger lastRevisionHash;

	public RTCRevisionState(BigInteger lastRevisionHash) {
		this.lastRevisionHash = lastRevisionHash;
	}
	
	public BigInteger getLastRevisionHash() {
		return lastRevisionHash;
	}
}