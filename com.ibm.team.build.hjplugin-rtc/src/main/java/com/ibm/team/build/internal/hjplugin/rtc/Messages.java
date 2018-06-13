/*******************************************************************************
 * Copyright (c) 2013, 2018 IBM Corporation and others.
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
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("nls")
public class Messages {

	private static final String BUNDLE_NAME = "com.ibm.team.build.internal.hjplugin.rtc.messages"; //$NON-NLS-1$
	private static final Map<Locale, Messages> localeToMessagesMap = new ConcurrentHashMap<Locale, Messages>();
	
	public static Messages getDefault() {
		return get(Locale.getDefault());
	}
	
	public static Messages get(Locale locale) {
		Messages messages = localeToMessagesMap.get(locale);
		if (messages == null) {
			messages = new Messages(locale);
			localeToMessagesMap.put(locale, messages);
		}
		return messages;
	}

	private ResourceBundle resourceBundle;

	private Messages(Locale locale) {
		resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
	}

    /**
     * Unable to retrieve name
     * 
     */
    public String ChangeReportBuilder_unable_to_get_component_name() {
        return getString("ChangeReportBuilder.unable_to_get_component_name");
    }

    /**
     * RTC Checkout : Failing the delete of root directory "{0}"
     * 
     */
    public String RepositoryConnection_checkout_clean_root_disallowed(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_root_disallowed"), arg1);
    }

    /**
     * Unable to retrieve name
     * 
     */
    public String ChangeReportBuilder_unable_to_get_component_name2() {
        return getString("ChangeReportBuilder.unable_to_get_component_name2");
    }

    /**
     * Unknown
     * 
     */
    public String ChangeReportBuilder_unknown_author() {
        return getString("ChangeReportBuilder.unknown_author");
    }

    /**
     * Unknown
     * 
     */
    public String ChangeReportBuilder_unknown_component() {
        return getString("ChangeReportBuilder.unknown_component");
    }

    /**
     * RTC Checkout : Source control setup
     * 
     */
    public String RepositoryConnection_checkout_setup() {
        return getString("RepositoryConnection.checkout_setup");
    }

    /**
     * &lt;unknown>
     * 
     */
    public String ChangeReportBuilder_unknown_versionable() {
        return getString("ChangeReportBuilder_unknown_versionable");
    }

    /**
     * RTC Checkout : unable to expand change sets for change log {0}
     * 
     */
    public String ChangeReportBuilder_unable_to_expand_change_sets(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_expand_change_sets"), arg1);
    }

    /**
     * CRRTC3533E: Corrupt metadata found in load directory "{0}".
     * 
     */
    public String RepositoryConnection_corrupt_metadata_found(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.corrupt_metadata_found"), arg1);
    }

    /**
     * RTC Checkout : Termination error: {0}
     * 
     */
    public String RepositoryConnection_checkout_termination_error(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_termination_error"), arg1);
    }

    /**
     * RTC Checkout : unable to expand components for change log {0}
     * 
     */
    public String ChangeReportBuilder_unable_to_get_component(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_component"), arg1);
    }

    /**
     * Unable to find a workspace with name "{0}"
     * 
     */
    public String RepositoryConnection_workspace_not_found(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.workspace_not_found"), arg1);
    }

    public String RepositoryConnection_build_definition_not_found(String arg1) {
    	return MessageFormat.format(getString("RepositoryConnection_build_definition_not_found"), arg1);
    }

	public String RepositoryConnection_load_rules_pre_603_build_toolkit() {
		return getString("RepositoryConnection_load_rules_pre_603_build_toolkit");
	}

    public String RTCSnapshotUtils_snapshot_not_found(String arg1) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found"), arg1);
    }
    
    public String RTCSnapshotUtils_snapshot_not_found_ws(String arg1, String arg2) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found_ws"), arg1, arg2);
    }

    public String RTCSnapshotUtils_snapshot_not_found_st_pa(String arg1, String arg2, String arg3) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found_st_pa"), arg1, arg2, arg3);
    }

    public String RTCSnapshotUtils_snapshot_not_found_st_ta(String arg1, String arg2, String arg3) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found_st_ta"), arg1, arg2, arg3);
    }

    public String RTCSnapshotUtils_snapshot_not_found_pa(String arg1, String arg2) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found_pa"), arg1, arg2);
    }

    public String RTCSnapshotUtils_snapshot_not_found_ta(String arg1, String arg2) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found_ta"), arg1, arg2);
    }

    public String RTCSnapshotUtils_snapshot_not_found_st(String arg1, String arg2) {
    	return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_not_found_st"), arg1, arg2);
    }


    /**
     * &lt;unknown>
     * 
     */
    public String ChangeReportBuilder_unknown_parent_folder() {
        return getString("ChangeReportBuilder.unknown_parent_folder");
    }

    /**
     * RTC Checkout : Unable to fill in the related work items in the Change log : 
     * 
     */
    public String ChangeReportBuilder_unable_to_get_work_items(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_work_items"), arg1);
    }
    
    public String RTCWorkspaceUtils_cannot_delete_workspace(String arg1, String arg2) {
    	return MessageFormat.format(getString("RTCWorkspaceUtils_cannot_delete_workspace"), arg1, arg2);
    }
    
    public String RTCWorkspaceUtils_path_to_load_rule_file_invalid_format(String arg1) {
    	return MessageFormat.format(getString("RTCWorkspaceUtils_path_to_load_rule_file_invalid_format"), arg1);
    }

	public String RTCWorkspaceUtils_path_to_load_rule_file_no_component_name(String arg1) {
		return MessageFormat.format(getString("RTCWorkspaceUtils_path_to_load_rule_file_no_component_name"), arg1);
	}

	public String RTCWorkspaceUtils_path_to_load_rule_file_no_file_path(String arg1) {
		return MessageFormat.format(getString("RTCWorkspaceUtils_path_to_load_rule_file_no_file_path"), arg1);
	}
    
    /**
     * RTC Checkout : unable to determine authors of change sets for the change log : {0}
     * 
     */
    public String ChangeReportBuilder_unable_to_get_authors(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_authors"), arg1);
    }

    /**
     * Unable to obtain password from file {0}
     * 
     */
    public String BuildClient_bad_password_file(Object arg1) {
        return MessageFormat.format(getString("BuildClient_bad_password_file"), arg1);
    }

    /**
     * No password provided
     * 
     */
	public String BuildClient_no_password() {
		return getString("BuildClient_no_password");
	}

    /**
     * CRRTC3534E: While files were being loaded to the following location, metadata was corrupted: "{0}". For more details, open the help system and search for CRRTC3534E.
     * 
     */
    public String RepositoryConnection_corrupt_metadata(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.corrupt_metadata"), arg1);
    }

    /**
     * RTC Checkout : Fetching files to fetch destination "{0}" ...
     * 
     */
    public String RepositoryConnection_checkout_fetch_start(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_fetch_start"), arg1);
    }

    /**
     * RTC Checkout : Fetching Completed
     * 
     */
    public String RepositoryConnection_checkout_fetch_complete() {
        return getString("RepositoryConnection.checkout_fetch_complete");
    }

    /**
     * RTC Checkout : Failed to mark in the RTC build result that the checkout activity completed: {0}
     * 
     */
    public String RepositoryConnection_complete_checkout_activity_failed(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection_complete_checkout_activity_failed"), arg1);
    }

    /**
     * Unable to retrieve change set details
     * 
     */
    public String ChangeReportBuilder_unable_to_get_change_set2() {
        return getString("ChangeReportBuilder.unable_to_get_change_set2");
    }

    /**
     * Unable to retrieve change set details
     * 
     */
    public String ChangeReportBuilder_unable_to_get_change_set() {
        return getString("ChangeReportBuilder.unable_to_get_change_set");
    }

    /**
     * RTC Checkout : unable to determine names of the versionables affected for the change log : {0}
     * 
     */
    public String ChangeReportBuilder_unable_to_get_versionable_names(Object arg1) {
        return MessageFormat.format(getString("ChangeReportBuilder.unable_to_get_versionable_names"), arg1);
    }

    /**
     * More than one repository workspace has the name {0}
     * 
     */
    public String RepositoryConnection_name_not_unique(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.name_not_unique"), arg1);
    }
    
    /**
     * CRRTC3505E: The following fetch destination cannot be deleted: "{0}". For more details, open the help system and search for CRRTC3505E.
     * 
     */
    public String RepositoryConnection_checkout_clean_failed(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_failed"), arg1);
    }

    public String RepositoryConnection_stream_name_not_unique(Object arg1) {
    	return MessageFormat.format(getString("RepositoryConnection.stream_name_not_unique"), arg1);
    }
    
    public String RepositoryConnection_stream_not_found(Object arg1) {
    	return MessageFormat.format(getString("RepositoryConnection.stream_not_found"), arg1);
    }
    
	public String RTCSnapshotUtils_snapshot_name_not_unique(String arg1) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique"), arg1);
	}

	public String RTCSnapshotUtils_snapshot_name_not_unique_ws(String arg1, String arg2) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique_ws"), arg1, arg2);
	}

	public String RTCSnapshotUtils_snapshot_name_not_unique_st_pa(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique_st_pa"), arg1, arg2, arg3);
	}

	public String RTCSnapshotUtils_snapshot_name_not_unique_st_ta(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique_st_ta"), arg1, arg2, arg3);
	}

	public String RTCSnapshotUtils_snapshot_name_not_unique_pa(String arg1, String arg2) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique_pa"), arg1, arg2);
	}

	public String RTCSnapshotUtils_snapshot_name_not_unique_ta(String arg1, String arg2) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique_ta"), arg1, arg2);
	}

	public String RTCSnapshotUtils_snapshot_name_not_unique_st(String arg1, String arg2) {
		return MessageFormat.format(getString("RTCSnapshotUtils_snapshot_name_not_unique_st"), arg1, arg2);
	}
    
    /**
     * CRRTC3531E: Unspecified IO error listing files for directory: "{0}"
     * 
     */
    public String RepositoryConnection_checkout_clean_error(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_error"), arg1);
    }

    /**
     * RTC Checkout : Deleting fetch destination "{0}" before fetching ...
     * 
     */
    public String RepositoryConnection_checkout_clean_sandbox(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_clean_sandbox"), arg1);
    }

    /**
     * RTC Checkout : Accepting changes into workspace "{0}" ...
     * 
     */
    public String RepositoryConnection_checkout_accept(Object arg1) {
        return MessageFormat.format(getString("RepositoryConnection.checkout_accept"), arg1);
    }
    
    public String RepositoryConnection_accept_unable_to_start_call_connector() {
    	return getString("RepositoryConnection.accept_unable_to_start_call_connector");
    }
    
    /**
     * RTC Load : Unable to find WorkspaceConnection created by Accept
     * 
     */
    public String RepositoryConnection_load_no_workspace_connection_for_synched_load() {
    	return getString("RepositoryConnection.load_no_workspace_connection_for_synched_load");
    }
    
    /**
     * RTC Load : Invalid parameters, at least one of buildResultUUID, buildWorkspace or buildSnapshot
     * should be specified
     */
    public String RepositoryConnection_load_invalid_parameters_for_load() {
    	return getString("RepositoryConnection.load_invalid_parameters_for_load");
    }

    /**
     * The RTC Build definition {0} does not have the SCM build workspace defined.
     */
    public String RepositoryConnection_build_definition_no_workspace(Object arg1) {
    	return MessageFormat.format(getString("RepositoryConnection.build_definition_no_workspace"), arg1);
    }

    /**
     * Jazz Source Control setup
     */
	public String RepositoryConnection_pre_build_activity() {
		return getString("RepositoryConnection.pre_build_activity");
	}

    /**
     * Accepting changes
     */
	public String RepositoryConnection_activity_accepting_changes() {
		return getString("RepositoryConnection.activity_accepting_changes");
	}

	/**
	 * Fetching files
	 */
	public String RepositoryConnection_activity_fetching() {
		return getString("RepositoryConnection.activity_fetching");
	}
	
	/**
	 * Build workspace {0} has {1} components that are not visible.
	 */
	public String RepositoryConnection_hidden_components(Object arg1, Object arg2) {
		return MessageFormat.format(getString("RepositoryConnection_hidden_components"), arg1, arg2);
	}
	
	/**
	 * Fetching files from workspace {0}
	 */
	public String RepositoryConnection_fetching_files_from_workspace(Object arg1) {
		return MessageFormat.format(getString("RepositoryConnection.fetching_files_from_workspace"), arg1);
	}
	
	/**
	 * 
	 * @return
	 */
	public String RepositoryConnection_invalid_load_configuration() {
		return getString("RepositoryConnection.invalid_load_configuration");
	}
	
	/**
	 * 
	 */
	public String RepositoryConnection_stream_load_no_workspace_snapshot_uuid() {
		return getString("RepositoryConnection.stream_load_no_workspace_snapshot_uuid");
	}

	/**
	 * 
	 */
	public String RepositoryConnection_using_build_definition_configuration() {
		return getString("RepositoryConnection.using_build_definition_configuration");
	}
	
	/**
	 * 
	 */
	public String RepositoryConnection_using_build_workspace_configuration() {
		return getString("RepositoryConnection.using_build_workspace_configuration");
	}
	
	/**
	 * 
	 */
	public String RepositoryConnection_using_build_snapshot_configuration() {
		return getString("RepositoryConnection.using_build_snapshot_configuration");
	}
	
	/**
	 * 
	 */
	public String RepositoryConnection_using_build_stream_configuration() {
		return getString("RepositoryConnection.using_build_stream_configuration");
	}

	/**
	 * A component with item ID "{0}" cannot be found.
	 */
	public String RepositoryConnection_component_with_id_not_found(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_id_not_found"), arg1);
	}

	/**
	 * A component with item ID "{0}" cannot be found.
	 */
	public String RepositoryConnection_component_with_name_not_found(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_name_not_found"), arg1);
	}

	/**
	 * Could not find component with item id "{0}" in the workspace "{1}".
	 */
	public String RepositoryConnection_component_with_id_not_found_ws(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_id_not_found_ws"), arg1, arg2);
	}
	
	/**
	 * Could not find component with item id "{0}" in the snapshot "{1}".
	 */
	public String RepositoryConnection_component_with_id_not_found_snapshot(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_id_not_found_snapshot"), arg1, arg2);
	}
	
	/**
	 * Could not find component with item id "{0}" in the stream "{1}".
	 */
	public String RepositoryConnection_component_with_id_not_found_stream(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_id_not_found_stream"), arg1, arg2);
	}
	
	/**
	 * More than one component with name "{0}" found in the workspace "{1}".
	 */
	public String RepositoryConnection_multiple_components_with_name_in_ws(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.multiple_components_with_name_in_ws"), arg1, arg2);
	}
	
	/**
	 * More than one component with name "{0}" found in the snapshot "{1}".
	 */
	public String RepositoryConnection_multiple_components_with_name_in_snapshot(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.multiple_components_with_name_in_snapshot"), arg1, arg2);
	}
	
	/**
	 * More than one component with name "{0}" found in the stream "{1}".
	 */
	public String RepositoryConnection_multiple_components_with_name_in_stream(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.multiple_components_with_name_in_stream"), arg1, arg2);
	}

	/**
	 * Could not find component with name "{0}" in the workspace "{1}".
	 */
	public String RepositoryConnection_component_with_name_not_found_ws(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_name_not_found_ws"), arg1, arg2);
	}
	
	/**
	 * Could not find component with name "{0}" in the snapshot "{1}".
	 */
	public String RepositoryConnection_component_with_name_not_found_snapshot(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_name_not_found_snapshot"), arg1, arg2);
	}
	
	/**
	 * Could not find component with name "{0}" in the stream "{1}".
	 */
	public String RepositoryConnection_component_with_name_not_found_stream(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.component_with_name_not_found_stream"), arg1, arg2);
	}

	/**
	 * Either componentId or componentName should be specified.
	 */
	public String RepositoryConnection_component_id_or_name_required() {
		return getString("RepositoryConnection.component_id_or_name_required");
	}

	public String RepositoryConnection_load_rule_file_invalid_component_uuid(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_invalid_component_uuid"), arg1, arg2);
	}

	public String RepositoryConnection_load_rule_file_invalid_item_uuid(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_invalid_item_uuid"), arg1, arg2);
	}

	/**
	 * The file path specified in the component load rules mapping file for the
	 * component "{0}" is empty.
	 */
	public String RepositoryConnection_load_rule_file_path_empty(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_path_empty"), arg1);
	}

	/**
	 * The specified load rule file "{0}" is not found in the component "{1}" in
	 * the workspace {2}.
	 */
	public String RepositoryConnection_load_rule_file_not_found_ws(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_not_found_ws"), arg1, arg2, arg3);
	}
	
	/**
	 * The specified load rule file "{0}" is not found in the component "{1}" in
	 * the snapshot {2}.
	 */
	public String RepositoryConnection_load_rule_file_not_found_snapshot(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_not_found_snapshot"), arg1, arg2, arg3);
	}
	
	/**
	 * The specified load rule file "{0}" is not found in the component "{1}" in
	 * the stream {2}.
	 */
	public String RepositoryConnection_load_rule_file_not_found_stream(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_not_found_stream"), arg1, arg2, arg3);
	}

	/**
	 * The specified load rule file "{0}" does not resolve to a file in the
	 * component "{1}" in the workspace "{2}.
	 */
	public String RepositoryConnection_load_rule_not_a_file_ws(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_not_a_file_ws"), arg1, arg2, arg3);
	}
	
	/**
	 * The specified load rule file "{0}" does not resolve to a file in the
	 * component "{1}" in the snapshot "{2}.
	 */
	public String RepositoryConnection_load_rule_not_a_file_snapshot(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_not_a_file_snapshot"), arg1, arg2, arg3);
	}
	
	/**
	 * The specified load rule file "{0}" does not resolve to a file in the
	 * component "{1}" in the stream "{2}.
	 */
	public String RepositoryConnection_load_rule_not_a_file_stream(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_not_a_file_stream"), arg1, arg2, arg3);
	}

	/**
	 * The load rule file with item ID "{0}" cannot be found in the component "{1}" in the workspace "{2}".
	 */
	public String RepositoryConnection_load_rule_file_with_id_not_found_ws(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_with_id_not_found_ws"), arg1, arg2, arg3);
	}
	
	/**
	 * The load rule file with item ID "{0}" cannot be found in the component "{1}" in the snapshot "{2}".
	 */
	public String RepositoryConnection_load_rule_file_with_id_not_found_snapshot(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_with_id_not_found_snapshot"), arg1, arg2, arg3);
	}
	
	/**
	 * The load rule file with item ID "{0}" cannot be found in the component "{1}" in the stream "{2}".
	 */
	public String RepositoryConnection_load_rule_file_with_id_not_found_stream(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_file_with_id_not_found_stream"), arg1, arg2, arg3);
	}
	
	/**
	 * The load rule file with item ID "{0}" does not resolve to a file in the component "{1}" in the workspace "{2}".
	 */
	public String RepositoryConnection_load_rule_with_id_not_a_file_ws(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_with_id_not_a_file_ws"), arg1, arg2, arg3);
	}
	
	/**
	 * The load rule file with item ID "{0}" does not resolve to a file in the component "{1}" in the snapshot "{2}".
	 */
	public String RepositoryConnection_load_rule_with_id_not_a_file_snapshot(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_with_id_not_a_file_snapshot"), arg1, arg2, arg3);
	}
	
	/**
	 * The load rule file with item ID "{0}" does not resolve to a file in the component "{1}" in the stream "{2}".
	 */
	public String RepositoryConnection_load_rule_with_id_not_a_file_stream(String arg1, String arg2, String arg3) {
		return MessageFormat.format(getString("RepositoryConnection.load_rule_with_id_not_a_file_stream"), arg1, arg2, arg3);
	}
	
	/**
	 * Either fileItemId or filePath should be specified. 
	 */
	public String RepositoryConnection_file_item_id_or_name_required() {
		return getString("RepositoryConnection.file_item_id_or_name_required");
	}
	
    /**
     * Unable to find a build definition with name "{0}"
     */
    public String BuildConnection_build_definition_not_found(Object arg1) {
        return MessageFormat.format(getString("BuildConnection_build_definition_not_found"), arg1);
    }

    /**
     * Build definition is not a Hudson/Jenkins build definition
     */
    public String BuildConnection_build_definition_missing_hudson_config() {
        return getString("BuildConnection_build_definition_missing_hudson_config");
    }

    /**
     * Build definition has no supporting build engines
     */
    public String BuildConnection_build_definition_missing_build_engine() {
        return getString("BuildConnection_build_definition_missing_build_engine");
    }

    /**
     * Supporting build engine is not a Hudson/Jenkins build engine
     */
    public String BuildConnection_build_definition_missing_build_engine_hudson_config() {
        return getString("BuildConnection_build_definition_missing_build_engine_hudson_config");
    }
    
    /**
     * Build definition does not have a Jazz Source Control option specified
     */
    public String BuildConnection_build_definition_missing_jazz_scm_config() {
    	return getString("BuildConnection_build_definition_missing_jazz_scm_config");
    }

    /**
     * snapshot {0}
     */
    public String BuildConnection_snapshot_label(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_snapshot_label"), arg1);
    }
    
    /**
     * There are no RTC build engines associated with the RTC build definition {0}. The build definition must have a supported active build engine.
     */
    public String BuildConnection_no_build_engine_for_defn(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_no_build_engine_for_defn"),  arg1);
    }

    /**
     * Missing Hudson/Jenkins root url from configuation; unable to link Hudson/Jenkins build with the RTC build result
     */
    public String BuildConnection_missing_root_url() {
    	return getString("BuildConnection_missing_root_url");
    }

    /**
     * Hudson/Jenkins Job
     */
    public String BuildConnection_hj_job() {
    	return getString("BuildConnection_hj_job");
    }
    /**
     * Hudson/Jenkins Build
     */
    public String BuildConnection_hj_build() {
    	return getString("BuildConnection_hj_build");
    }

    /**
     * Unable to mark build as terminated. Requester information is missing
     */
    public String BuildConnection_terminate_missing_build_requester() {
    	return getString("BuildConnection_terminate_missing_build_requester");
    }

    /**
     * Unable to mark RTC build result as started. Requester information is missing
     */
    public String BuildConnection_start_missing_build_requester() {
    	return getString("BuildConnection_start_missing_build_requester");
    }

    /**
     * "Unable to update build label on RTC build result to {0}"
     */
    public String BuildConnection_set_label_failed(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_set_label_failed"), arg1);
    }

    /**
     * Unable to identify who started the build : {0}
     */
    public String BuildConnection_unknown_contributor(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_unknown_contributor"), arg1);
    }

    /**
     * Unable to identify how the build was started in RTC : {0}
     */
    public String BuildConnection_unknown_start_reason(Object arg1) {
    	return MessageFormat.format(getString("BuildConnection_unknown_start_reason"), arg1);
    }

    /**
     * The load directory at "{0}" could not be deleted because the directory is either the current working directory or an ancestor of it.
     */
    public String BuildConfiguration_deleting_working_directory(Object arg1) {
    	return MessageFormat.format(getString("BuildConfiguration_deleting_working_directory"), arg1);
    }

    /**
     * User {0} is unable to access the load rule for component {1}
     */
    public String BuildConfiguration_load_rule_access_failed(String user, String component, String origMsg) {
    	return MessageFormat.format(getString("BuildConfiguration_load_rule_access_failed"), user, component, origMsg);
    }
    
    /**
     * Load directory {0} is invalid : {1}
     */
    public String BuildConfiguration_invalid_fetch_destination(String arg1, String arg2) {
    	return MessageFormat.format(getString("BuildConfiguration_invalid_fetch_destination"), arg1, arg2);
    }

    /**
     * Substituted the following build property variables:
     */
    public String BuildConfiguration_substituted_build_variables() {
    	return getString("BuildConfiguration_substituted_build_variables");
    }

    /**
     * Substituted the following configuration element property variables:
     */
    public String BuildConfiguration_substituted_config_variables() {
    	return getString("BuildConfiguration_substituted_config_variables");
    }

    /**
     * Build definition {0} is not configured for Jazz SCM
     */
    public String BuildConfiguration_scm_not_configured(String arg1) {
    	return MessageFormat.format(getString("BuildConfiguration_scm_not_configured"), arg1);
    }
    
    /**
     * Retrying to create Repository Workspace
     */
    public String BuildConfiguration_repo_workspace_retry() {
    	return getString("BuildConfiguration_repo_workspace_retry");
    }
    
    /**
     * Failed to create a Repository Workspace after {0} retries
     */
    public String BuildConfiguration_repo_workspace_create_failed(String arg1) {
    	return MessageFormat.format(getString("BuildConfiguration_repo_workspace_create_failed"), arg1);
    }

    /**
     * Unable to determine load rule type due to {0}
     */
    public String NonValidatingLoadRuleFactory_load_rule_type_failure(Object arg1) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_load_rule_type_failure"),  arg1);
    }

    /**
     * Unable to retrieve load rule from {0}, unsupported encoding {1}
     */
    public String NonValidatingLoadRuleFactory_bad_encoding(Object arg1, Object arg2) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_bad_encoding"),  arg1, arg2);
    }

    /**
     * Error parsing load rule {0} at line {1}
     */
    public String NonValidatingLoadRuleFactory_parsing_failed_at_line(Object arg1, Object arg2) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_parsing_failed_at_line"),  arg1, arg2);
    }

    /**
     * Error parsing load rule: {0}
     */
    public String NonValidatingLoadRuleFactory_parsing_failure(Object arg1) {
    	return MessageFormat.format(getString("NonValidatingLoadRuleFactory_parsing_failure"),  arg1);
    }

    /**
     * Error reading load rule file
     */
    public String NonValidatingLoadRuleFactory_read_failure() {
    	return getString("NonValidatingLoadRuleFactory_read_failure");
    }

    /**
     * Unable to locate the load rule schema
     */
    public String NonValidatingLoadRuleFactory_missing_schema() {
    	return getString("NonValidatingLoadRuleFactory_missing_schema");
    }

    /**
     * {0} = {1}   -->   {2} = {3}
     */
    public String PropertyVariableHelper_substitution(String arg1, String arg2, String arg3, String arg4) {
    	return MessageFormat.format(getString("PropertyVariableHelper_substitution"), arg1, arg2, arg3, arg4);
    }

    /**
     * {0} : {1} = {2}   -->   {3} = {4}
     */
    public String PropertyVariableHelper_substitution_for_element(String arg1, String arg2, String arg3, String arg4, String arg5) {
    	return MessageFormat.format(getString("PropertyVariableHelper_substitution_for_element"), arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * A cycle was found while replacing property variable references. {0} = {1}
     */
    public String PropertyVariableHelper_cycle(String arg1, String arg2) {
    	return MessageFormat.format(getString("PropertyVariableHelper_cycle"), arg1, arg2);
    }

    /**
     * The following replacements were made:
     */
    public String PropertyVariableHelper_cycle_description() {
    	return getString("PropertyVariableHelper_cycle_description");
    }

	public String BuildConfiguration_load_rule_file_not_file(String arg1) {
		return MessageFormat.format(getString("BuildConfiguration_load_rule_file_not_file"), arg1);
	}

	public String BuildConfiguration_load_rule_file_does_not_exist(String arg1) {
		return MessageFormat.format(getString("BuildConfiguration_load_rule_file_does_not_exist"), arg1);
	}

	public String BuildConfiguration_loadrule2_class_not_found() {
		return getString("BuildConfiguration_loadrule2_class_not_found");
	}

	public String BuildConfiguration_multiple_load_rule_files_deprecated() {
		return getString("BuildConfiguration_multiple_load_rule_files_deprecated");
	}

	public String BuildConfiguration_multiple_load_rule_files_not_supported() {
		return getString("BuildConfiguration_multiple_load_rule_files_not_supported");
	}

	public String NonValidatingLoadRuleFactory_old_load_rules_format_deprecated() {
		return getString("NonValidatingLoadRuleFactory_old_load_rules_format_deprecated");
	}

	public String NonValidatingLoadRuleFactory_load_rules_not_in_XML_format() {
		return getString("NonValidatingLoadRuleFactory_load_rules_not_in_XML_format");
	}

	/**
	 * The user, {0} ({1}), does not have permissions to access the load rule file for component "{2}".
	 */
	public String RTCWorkspaceUtils_private_load_rule(String arg1, String arg2, String arg3, String arg4) {
		return MessageFormat.format(getString("RTCWorkspaceUtils_private_load_rule"), arg1, arg2, arg3, arg4);
	}
	
	
	/**
	 *  A project or team area with item ID "{0}" cannot be found.
	 */
	public String RepositoryConnection_process_area_not_found(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection_process_area_not_found"), arg1);
	}
			
	/**
	 * A project area with name "{0}" cannot be found.
	 */
	public String RepositoryConnection_project_area_not_found(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection_project_area_not_found"), arg1);
	}
	
	/**
	 * A team area at path "{0}" cannot be found. 
	 */
	public String RepositoryConnection_team_area_not_found(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection_team_area_not_found"), arg1);
	}
	
	/**
	 * The project area "{0}" is archived.
	 */
	public String RepositoryConnection_project_area_archived(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection_project_area_archived"), arg1);
	}
	
	/**
	 * The team area "{0}" is archived.
	 */
	public String RepositoryConnection_team_area_archived(String arg1) {
		return MessageFormat.format(getString("RepositoryConnection_team_area_archived"), arg1);
	}
	
	/**
	 * A stream with name "{0}" cannot be found in the project area "{1}".
	 */
	public String RepositoryConnection_stream_not_found_pa(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection_stream_not_found_pa"), arg1, arg2);
	}
	
	/**
	 * A stream with name "{0}" cannot be found in the team area "{1}".
	 */
	public String RepositoryConnection_stream_not_found_ta(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection_stream_not_found_ta"), arg1, arg2);
	}
	
	/**
	 * More than one stream with name "{0}" found in the project area "{1}".
	 */
	public String RepositoryConnection_stream_name_not_unique_pa(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection_stream_name_not_unique_pa"), arg1, arg2);
	}
	
	/**
	 * More than one stream with name "{0}" found in the team area "{1}".
	 */
	public String RepositoryConnection_stream_name_not_unique_ta(String arg1, String arg2) {
		return MessageFormat.format(getString("RepositoryConnection_stream_name_not_unique_ta"), arg1, arg2);
	}

	/**
	 * Build Result has no associated build request
	 */
	public String RTCBuildUtils_unexpected_zero_requests() {
		return getString("RTCBuildUtils_unexpected_zero_requests");
	}
	
	public String VersionCheckerUtil_missing_expected_content_in_plugin_xml() {
		return getString("VersionCheckerUtil_missing_expected_content_in_plugin_xml");
	}
	
	public String VersionCheckerUtil_class_not_found(String arg1) {
		return MessageFormat.format(getString("VersionCheckerUtil_class_not_found"), arg1);
	}

	public String VersionCheckerUtil_io_error(String arg1) {
		return MessageFormat.format(getString("VersionCheckerUtil_io_error"), arg1);
	}
	
	public String VersionCheckerUtil_parser_error(String arg1) {
		return MessageFormat.format(getString("VersionCheckerUtil_parser_error"), arg1);
	}
	/**
     * Get the message from the bundle
     * 
     * @param key
     * @return The translated string.
     */
    private String getString(String key) {
        try {
        	return resourceBundle.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }
}
