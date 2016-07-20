package com.ibm.team.build.internal.hjplugin.loadrule;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import jenkins.model.Jenkins;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * Represents an entry of a Load Rule file. Each entry is a pair of Component
 * name or UUID and the filepath of the Load Rule file within.
 */
public class LoadRuleEntry extends AbstractDescribableImpl<LoadRuleEntry> {

    /**
     * Component Name or UUID
     */
    protected String componentUUID = null;

    /**
     * Path of the Load Rule file within the Component
     */
    protected String loadRulesFilepath = null;
    
    /**
     * @param componentUUID Component Name or UUID
     * @param loadRulesFilepath Path of the Load Rule file within the Component
     */
    @DataBoundConstructor
    public LoadRuleEntry(String componentUUID, String loadRulesFilepath) {
	super();
	this.componentUUID = componentUUID;
	this.loadRulesFilepath = loadRulesFilepath;
    }

    /**
     * @return the componentUUID
     */
    public String getComponentUUID() {
        return componentUUID;
    }

    /**
     * @param componentUUID the componentUUID to set
     */
    public void setComponentUUID(String componentUUID) {
        this.componentUUID = componentUUID;
    }

    /**
     * @return the loadRulesFilepath
     */
    public String getLoadRulesFilepath() {
        return loadRulesFilepath;
    }

    /**
     * @param loadRulesFilepath the loadRulesFilepath to set
     */
    public void setLoadRulesFilepath(String loadRulesFilepath) {
        this.loadRulesFilepath = loadRulesFilepath;
    }
    
    /**
     * Load Rule Entry descriptor. Inner class of a descriptor class. Specifies and
     * validate the content exhibited in the Jenkins interface.
     */
    @Extension
    public static class LoadRuleEntryDescriptor extends Descriptor<LoadRuleEntry> {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
    		// TODO Auto-generated method stub
    		return "Load Rule Entry";
        }

        /**
         * @return List of all available descriptors for LoadRuleEntry.
         */
        public static DescriptorExtensionList<LoadRuleEntry, LoadRuleEntryDescriptor> all() {
    		return Jenkins.getInstance().getDescriptorList(LoadRuleEntry.class);
        }
        
        /**
         * Invoked from Jenkins to validate field.
         * @param componentUUID Component name or UUID
         * @return Validation message.
         */
        public FormValidation doCheckComponentUUID(@QueryParameter String componentUUID) {
    		return FormValidation.validateRequired(componentUUID);
        }
        
        /**
         * Invoked from Jenkins to validate field.
         * @param loadRulesFilepath Path of the Load Rule file within the Component
         * @return Validation message.
         */
        public FormValidation doCheckLoadRulesFilepath(@QueryParameter String loadRulesFilepath) {
    		return FormValidation.validateRequired(loadRulesFilepath);
        }
    }

}
