/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that connects to sequential calls on slave side to share some data.
 * Suppose call to Method1 on Slave is to be followed by call to Method2 on Slave, and some data
 * of type T is to be shared between these 2 calls, without letting this data flow
 * back to master. Then the usage should be as follows.
 * 
 * From within Method1, CallConnector should be instantiated, and started.
 * Method1 should make sure that its caller gets the id of CallConnector object instantiated.
 * This id should be made available to Method2. Method2 should then call static method getValue
 * to retrieve value of type T.
 *
 */
public class CallConnector<T> extends Thread {
	public static final long DEFAULT_TIMEOUT = 3*60*1000; // 3 minutes
	private T value;
	List<T> container;
	long timeout;
	
	public CallConnector(T value, long timeout) {
		this.value = value;
		this.timeout = timeout;
	}

	public CallConnector(T value) {
		this(value, DEFAULT_TIMEOUT);
	}

	/**
	 * Start but wait, till a caller resumes by setting container.
	 * If no caller does that, then time out.
	 */
	@Override
	public synchronized void run() {
		try {
			wait(timeout);
			
			if (container != null) {
				container.add(value);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public synchronized void resumeCall(List<T> container) {
		this.container = container;
		notify();
	}
	
	public static Object getValue(long threadId) {
		for (Thread t : Thread.getAllStackTraces().keySet()) {
			if (t.getId() == threadId) {
				if (t instanceof CallConnector) {
					CallConnector c = (CallConnector)t;
					List valueContainer = new ArrayList();
					c.resumeCall(valueContainer);
					try {
						c.join();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (valueContainer.size() > 0) {
						return valueContainer.get(0);
					}
				}
			}
		}
		return null;
	}
}
