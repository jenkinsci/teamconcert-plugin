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

package com.ibm.team.build.internal.hjplugin.tests.utils;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import org.junit.Ignore;

@Ignore("Abstract class containing utility methods")
public class AbstractTestCase {
	/**
     * generate the name of the project based on the test case
     * 
     * @return Name of the project
     */
    private String getTestClassName() {
        String name = this.getClass().getName();
        int posn = name.lastIndexOf('.');
        if (posn != -1 && posn < name.length()-1) {
            name = name.substring(posn + 1);
        }
        return name; 
    }
    
    protected String getUniqueName() {
    	return getUniqueName(getTestClassName()); 
    }
    
    protected String getFileUniqueName() {
    	return getUniqueName("File");
    }
    
    /**
     * This returns an unique name for use in a method of an object.
     * @return A name that is unique
     * @throws UnknownHostException 
     */
	protected String getUniqueName(String prefix) {
		Long suffix1 = System.nanoTime();
		SecureRandom random = new SecureRandom();
		Long suffix2 = random.nextLong();
		return prefix + "_" + Long.toHexString(suffix1) + "_" + Long.toHexString(suffix2);
	}
	
	protected String getBuildDefinitionUniqueName() {
		return getUniqueName("BuildDefinition");
	}
	
	protected String getRepositoryWorkspaceUniqueName() {
		return getUniqueName("RepositoryWorkspace");
	}
	
	protected String getComponentUniqueName() {
		return getUniqueName("Component");
	}
	
	protected String getStreamUniqueName() {
		return getUniqueName("Stream");
	}
	
	protected String getProjectAreaUniqueName() {
		return getUniqueName("ProjectArea");
	}
	
	protected String getTeamAreaUniqueName() {
		return getUniqueName("TeamArea");
	}
	
	protected String getSnapshotUniqueName() {
		return getUniqueName("Snapshot");
	}
}
