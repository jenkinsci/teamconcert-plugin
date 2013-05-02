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

@SuppressWarnings("nls")
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
     * Unable to find a workspace with name "{0}"
     * 
     */
    public static String RepositoryConnection_workspace_not_found(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.workspace_not_found"), arg1);
    }

    public static String RepositoryConnection_build_definition_not_found(String arg1) {
    	return MessageFormat.format(getString("RepositoryConnection_build_definition_not_found"), arg1);
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
     * The RTC Build definition {0} does not have the SCM build workspace defined.
     */
    public static String RepositoryConnection_build_definition_no_workspace(Object arg1) {
    	return MessageFormat.format(getString("RepositoryConnection.build_definition_no_workspace"), arg1);
    }

    /**
     * Jazz Source Control setup
     */
	public static String RepositoryConnection_pre_build_activity() {
		return getString("RepositoryConnection.pre_build_activity");
	}

    /**
     * Accepting changes
     */
	public static String RepositoryConnection_activity_accepting_changes() {
		return getString("RepositoryConnection.activity_accepting_changes");
	}

	/**
	 * Fetching files
	 */
	public static String RepositoryConnection_activity_fetching() {
		return getString("RepositoryConnection.activity_fetching");
	}

    /**
     * Unable to find a build definition with name "{0}"
     */
    public static String BuildConnection_build_definition_not_found(Object arg1) {
        return MessageFormat.format(getString("BuildConnection_build_definition_not_found"), arg1);
    }

    /**
     * Build definition is not a Hudson/Jenkins build definition
     */
    public static String BuildConnection_build_definition_missing_hudson_config() {
        return getString("BuildConnection_build_definition_missing_hudson_config");
    }

    /**
     * Build definition has no supporting build engines
     */
    public static String BuildConnection_build_definition_missing_build_engine() {
        return getString("BuildConnection_build_definition_missing_build_engine");
    }

    /**
     * Supporting build engine is not a Hudson/Jenkins build engine
     */
    public static String BuildConnection_build_definition_missing_build_engine_hudson_config() {
        return getString("BuildConnection_build_definition_missing_build_engine_hudson_config");
    }
    
    /**
     * Build definition has no Jazz Source Control option
     */
    public static String BuildConnection_build_definition_missing_jazz_scm_config() {
    	return getString("BuildConnection_build_definition_missing_jazz_scm_config");
    }

    /**
     * snapshot {0}
     */
    public static String BuildConnection_snapshot_label(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_snapshot_label"), arg1);
    }
    
    /**
     * There are no RTC build engines associated with the RTC build definition {0}. The build definition must have a supported active build engine.
     */
    public static String BuildConnection_no_build_engine_for_defn(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_no_build_engine_for_defn"),  arg1);
    }
    /**
     * Missing Hudson/Jenkins root url from configuation; unable to link Hudson/Jenkins build with the RTC build result
     */
    public static String BuildConnection_missing_root_url() {
    	return getString("BuildConnection_missing_root_url");
    }

    /**
     * Hudson/Jenkins Job
     */
    public static String BuildConnection_hj_job() {
    	return getString("BuildConnection_hj_job");
    }
    /**
     * Hudson/Jenkins Build
     */
    public static String BuildConnection_hj_build() {
    	return getString("BuildConnection_hj_build");
    }

    /**
     * The load directory at "{0}" could not be deleted because the directory is either the current working directory or an ancestor of it.
     */
    public static String BuildConfiguration_deleting_working_directory(Object arg1) {
    	return MessageFormat.format(getString("BuildConfiguration_deleting_working_directory"), arg1);
    }

    /**
     * User {0} is unable to access the load rule for component {1}
     */
    public static String BuildConfiguration_load_rule_access_failed(String arg1, String arg2) {
    	return MessageFormat.format(getString("BuildConfiguration_load_rule_access_failed"), arg1, arg2);
    }
    
    /**
     * Load directory {0} is invalid : {1}
     */
    public static String BuildConfiguration_invalid_fetch_destination(String arg1, String arg2) {
    	return MessageFormat.format(getString("BuildConfiguration_invalid_fetch_destination"), arg1, arg2);
    }

    /**
     * Substituted the following build property variables:
     */
    public static String BuildConfiguration_substituted_build_variables() {
    	return getString("BuildConfiguration_substituted_build_variables");
    }

    /**
     * Substituted the following configuration element property variables:
     */
    public static String BuildConfiguration_substituted_config_variables() {
    	return getString("BuildConfiguration_substituted_config_variables");
    }

    /**
     * Build definition {0} is not configured for Jazz SCM
     */
    public static String BuildConfiguration_scm_not_configured(String arg1) {
    	return MessageFormat.format(getString("BuildConfiguration_scm_not_configured"), arg1);
    }

    /**
     * Unable to determine load rule type due to {0}
     */
    public static String NonValidatingLoadRuleFactory_load_rule_type_failure(Object arg1) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_load_rule_type_failure"),  arg1);
    }

    /**
     * Unable to retrieve load rule from {0}, unsupported encoding {1}
     */
    public static String NonValidatingLoadRuleFactory_bad_encoding(Object arg1, Object arg2) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_bad_encoding"),  arg1, arg2);
    }

    /**
     * Error parsing load rule {0} at line {1}
     */
    public static String NonValidatingLoadRuleFactory_parsing_failed_at_line(Object arg1, Object arg2) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_parsing_failed_at_line"),  arg1, arg2);
    }

    /**
     * Error parsing load rule: {0}
     */
    public static String NonValidatingLoadRuleFactory_parsing_failure(Object arg1) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_parsing_failure"),  arg1);
    }

    /**
     * Error reading load rule file
     */
    public static String NonValidatingLoadRuleFactory_read_failure() {
    	return getString("NonValidatingLoadRuleFactory_read_failure");
    }

    /**
     * Unable to locate the load rule schema
     */
    public static String NonValidatingLoadRuleFactory_missing_schema() {
    	return getString("NonValidatingLoadRuleFactory_missing_schema");
    }

    /**
     * {0} = {1}   -->   {2} = {3}
     */
    public static String PropertyVariableHelper_substitution(String arg1, String arg2, String arg3, String arg4) {
    	return MessageFormat.format(getString("PropertyVariableHelper_substitution"), arg1, arg2, arg3, arg4);
    }

    /**
     * {0} : {1} = {2}   -->   {3} = {4}
     */
    public static String PropertyVariableHelper_substitution_for_element(String arg1, String arg2, String arg3, String arg4, String arg5) {
    	return MessageFormat.format(getString("PropertyVariableHelper_substitution_for_element"), arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * A cycle was found while replacing property variable references. {0} = {1}
     */
    public static String PropertyVariableHelper_cycle(String arg1, String arg2) {
    	return MessageFormat.format(getString("PropertyVariableHelper_cycle"), arg1, arg2);
    }

    /**
     * The following replacements were made:
     */
    public static String PropertyVariableHelper_cycle_description() {
    	return getString("PropertyVariableHelper_cycle_description");
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
