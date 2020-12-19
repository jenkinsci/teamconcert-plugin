/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.util;

import java.io.IOException;

@SuppressWarnings("serial")
public class ItemNotFoundException extends IOException {
	public ItemNotFoundException(String errorMessage) {
		super(errorMessage);
	}
	
	public ItemNotFoundException(String errorMessage, Throwable e) {
		super(errorMessage, e);
	}
}
