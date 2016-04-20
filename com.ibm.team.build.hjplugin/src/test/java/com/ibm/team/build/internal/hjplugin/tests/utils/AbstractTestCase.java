package com.ibm.team.build.internal.hjplugin.tests.utils;

import org.junit.Ignore;
import org.jvnet.hudson.test.HudsonTestCase;

import junit.framework.TestCase;

@Ignore("Abstract class containing utility methods")
public class AbstractTestCase extends HudsonTestCase {
	/**
     * generate the name of the project based on the test case
     * 
     * @return Name of the project
     */
    protected String getTestName() {
        String name = this.getClass().getName();
        int posn = name.lastIndexOf('.');
        if (posn != -1 && posn < name.length()-1) {
            name = name.substring(posn + 1);
        }
        return name + "_" + this.getName();
    }
}
