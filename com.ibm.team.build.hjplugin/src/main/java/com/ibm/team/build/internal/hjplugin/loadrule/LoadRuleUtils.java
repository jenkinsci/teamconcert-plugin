package com.ibm.team.build.internal.hjplugin.loadrule;

import java.util.List;

/**
 * Utilities to handle the format and validation of Load Rules.
 * 
 */
public class LoadRuleUtils {

    /**
     * Generates a load rule JSON string from a list of load rule entries.
     * The return format is as follow:
     * 
     * {
     *   "loadRules":[
     *       {
     *          "componentName":"JUnit",
     * 		"filePath":"JUnit Project/junit-project.loadrule"
     *       },
     *       {
     *          "componentName":"Releng",
     *          "filePath":"Releng Project/releng-project.loadrule"
     *       }
     *   ]
     * }
     * 
     * @param loadRuleEntries List of LoadRuleEntry
     * @return Load Rule in JSON format
     */
    public static String generateLoadRulesJson(
	    List<LoadRuleEntry> loadRuleEntries) {

	final String JSON_LOADRULES = "{\"loadRules\":[%s]}";
	final String JSON_LOADRULE_ENTRY = "{\"componentName\":\"%s\", \"filePath\":\"%s\"},";

	// There are specific libraries to create JSON. But since our Load Rules
	// JSON is very simple, it can be handle with StringBuilder and a simple
	// logic.

	StringBuilder loadRuleEntriesBuilder = new StringBuilder();
	for (LoadRuleEntry loadRuleEntry : loadRuleEntries) {
	  
	  String componentUUID = loadRuleEntry.componentUUID;
      String loadRulesFilepath = loadRuleEntry.loadRulesFilepath.replace("\\", "/");
      
      loadRuleEntriesBuilder.append(String.format(JSON_LOADRULE_ENTRY,
		    componentUUID,
		    loadRulesFilepath));
	}
	
	// Cheapest way to remove last comma without relying on additional libs 
	loadRuleEntriesBuilder.deleteCharAt(loadRuleEntriesBuilder.length()-1);

	return String.format(JSON_LOADRULES, loadRuleEntriesBuilder.toString());
    }

}
