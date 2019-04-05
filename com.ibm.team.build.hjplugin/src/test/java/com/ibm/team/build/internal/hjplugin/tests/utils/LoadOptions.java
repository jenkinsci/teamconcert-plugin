/*******************************************************************************
 * Copyright © 2016, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.tests.utils;

public class LoadOptions {
	public boolean acceptBeforeLoad = true;
	public boolean createFoldersForComponents = false;
	public boolean isDeleteNeeded = false;
	public boolean addLinksToWorkItems = false;
	public String loadPolicy;
	public String componentLoadConfig;
	public String componentsToExclude;
	public String pathToLoadRuleFile;
	
	private static LoadOptions defaultOptions = new LoadOptions();
	
	public static LoadOptions getDefault() {
		return defaultOptions;
	}
}
