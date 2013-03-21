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

// CHECKSTYLE:OFF

package com.ibm.team.build.internal.hjplugin.rtc;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Messages {

	private static final String BUNDLE_NAME = "com.ibm.team.build.internal.hjplugin.rtc.messages"; //$NON-NLS-1$
	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private Messages() {
		// no instances
	}

    /**
     * Unable to retrieve name
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_component_name() {
        return getString("ChangeReportBuilder.unable_to_get_component_name");
    }

    /**
     * RTC Checkout : Failing the delete of root directory "{0}"
     * 
     */
    public static String RepositoryConnection_checkout_clean_root_disallowed(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_root_disallowed"), arg1);
    }

    /**
     * Unable to retrieve name
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_component_name2() {
        return getString("ChangeReportBuilder.unable_to_get_component_name2");
    }

    /**
     * Unknown
     * 
     */
    public static String ChangeReportBuilder_unknown_author() {
        return getString("ChangeReportBuilder.unknown_author");
    }

    /**
     * Unknown
     * 
     */
    public static String ChangeReportBuilder_unknown_component() {
        return getString("ChangeReportBuilder.unknown_component");
    }

    /**
     * RTC Checkout : Source control setup
     * 
     */
    public static String RepositoryConnection_checkout_setup() {
        return getString("RepositoryConnection.checkout_setup");
    }

    /**
     * &lt;unknown>
     * 
     */
    public static String ChangeReportBuilder_unknown_versionable() {
        return getString("ChangeReportBuilder_unknown_versionable");
    }

    /**
     * RTC Checkout : unable to expand change sets for change log {0}
     * 
     */
    public static String ChangeReportBuilder_unable_to_expand_change_sets(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_expand_change_sets"), arg1);
    }

    /**
     * CRRTC3533E: Corrupt metadata found in load directory "{0}".
     * 
     */
    public static String RepositoryConnection_corrupt_metadata_found(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.corrupt_metadata_found"), arg1);
    }

    /**
     * RTC Checkout : Termination error: {0}
     * 
     */
    public static String RepositoryConnection_checkout_termination_error(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_termination_error"), arg1);
    }

    /**
     * RTC Checkout : unable to expand components for change log {0}
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_component(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_component"), arg1);
    }

    /**
     * Unable to find a workspace with name : {0}
     * 
     */
    public static String RepositoryConnection_workspace_not_found(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.workspace_not_found"), arg1);
    }

    /**
     * &lt;unknown>
     * 
     */
    public static String ChangeReportBuilder_unknown_parent_folder() {
        return getString("ChangeReportBuilder.unknown_parent_folder");
    }

    /**
     * RTC Checkout : Unable to fill in the related work items in the Change log : 
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_work_items(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_work_items"), arg1);
    }

    /**
     * RTC Checkout : unable to determine authors of change sets for the change log : {0}
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_authors(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_authors"), arg1);
    }

    /**
     * Unable to obtain password from file {0}
     * 
     */
    public static String BuildClient_bad_password_file(Object arg1) {
        return MessageFormat.format(getString("BuildClient_bad_password_file"), arg1);
    }

    /**
     * No password provided
     * 
     */
	public static String BuildClient_no_password() {
		return getString("BuildClient_no_password");
	}

    /**
     * CRRTC3534E: While files were being loaded to the following location, metadata was corrupted: "{0}". For more details, open the help system and search for CRRTC3534E.
     * 
     */
    public static String RepositoryConnection_corrupt_metadata(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.corrupt_metadata"), arg1);
    }

    /**
     * RTC Checkout : Fetching files to fetch destination "{0}" ...
     * 
     */
    public static String RepositoryConnection_checkout_fetch_start(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_fetch_start"), arg1);
    }

    /**
     * RTC Checkout : Fetching Completed
     * 
     */
    public static String RepositoryConnection_checkout_fetch_complete() {
        return getString("RepositoryConnection.checkout_fetch_complete");
    }

    /**
     * Unable to retrieve change set details
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_change_set2() {
        return getString("ChangeReportBuilder.unable_to_get_change_set2");
    }

    /**
     * Unable to retrieve change set details
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_change_set() {
        return getString("ChangeReportBuilder.unable_to_get_change_set");
    }

    /**
     * RTC Checkout : unable to determine names of the versionables affected for the change log : {0}
     * 
     */
    public static String ChangeReportBuilder_unable_to_get_versionable_names(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_versionable_names"), arg1);
    }

    /**
     * More than 1 repository workspace has the name {0}
     * 
     */
    public static String RepositoryConnection_name_not_unique(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.name_not_unique"), arg1);
    }

    /**
     * CRRTC3505E: The following fetch destination cannot be deleted: "{0}". For more details, open the help system and search for CRRTC3505E.
     * 
     */
    public static String RepositoryConnection_checkout_clean_failed(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_failed"), arg1);
    }

    /**
     * CRRTC3531E: Unspecified IO error listing files for directory: "{0}"
     * 
     */
    public static String RepositoryConnection_checkout_clean_error(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_error"), arg1);
    }

    /**
     * RTC Checkout : Deleting fetch destination "{0}" before fetching ...
     * 
     */
    public static String RepositoryConnection_checkout_clean_sandbox(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_sandbox"), arg1);
    }

    /**
     * RTC Checkout : Accepting changes into workspace "{0}" ...
     * 
     */
    public static String RepositoryConnection_checkout_accept(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_accept"), arg1);
    }

    /**
     * Get the message from the bundle
     * 
     * @param key
     * @return The translated string.
     */
    private static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

}
