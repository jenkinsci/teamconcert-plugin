/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.tests.utils;

import java.io.File;
import java.io.IOException;

import com.ibm.team.build.internal.hjplugin.tests.Config;

public class FileUtils {
	
	/**
	 * @return The password file as defined by the config or null if none.
	 */
	public static File getPasswordFile() {
		File passwordFileFile = null;
		if (Config.DEFAULT.getPasswordFile() != null && !Config.DEFAULT.getPasswordFile().isEmpty()
				&& !Config.DEFAULT.getPasswordFile().equalsIgnoreCase("none")) {
			passwordFileFile = new File(Config.DEFAULT.getPasswordFile());
		}
		return passwordFileFile;
	}

	/**
     * Recursively delete starting at the given file.
     */
    public static boolean delete(File file) throws Exception {
    	// paranoia check... Don't delete root directory because somehow the path was empty
    	if (file.getParent() == null) {
    		return false;
    	}
    	return deleteUsingJavaIO(file);
    }
    
    private static boolean deleteUsingJavaIO(File file) throws IOException {
        // take into account file or its children may be valid symbolic links.
        // we do not want to follow them.
        // The alternative to this is to use EFS to do the delete which does
        // essentially the same thing.
        // The other alternative is to use EFS to get the attributes and see
        // if it is a symbolic link and tailor the delete according to that.
        // This may have incorrect behaviour if the file is a link
        // and read-only.
        if (file.delete() || !file.exists()) {
            // Try to delete the file/link first without recursing to avoid
            // traversing into links
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                	deleteUsingJavaIO(child);
                }
            }
        }
        return file.delete();
    }

}
