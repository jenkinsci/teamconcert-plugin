/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin;

import java.io.Serializable;

public class BuildDefinitionInfo implements Serializable {
	
	/**
	 * default version
	 */
	private static final long serialVersionUID = 1L;
	
	private String triggerPolicy;
	private boolean configured = false;
	private boolean triggerPolicyUnknown = false;
	private String id = null;
	private boolean enabled = false;

	private BuildDefinitionInfo() {
		
	}
	
	private void setPBConfigured(boolean configured) {
		this.configured = configured;
	}

	private void setPBTriggerPolicy(String triggerPolicy) {
		this.triggerPolicy = triggerPolicy;
	}
	
	private void setPBTriggerPolicyUnknown(boolean unknown) {
		this.triggerPolicyUnknown = unknown;
	}
	
	public void setId(String id) {
		this.id = id;
		
	}

	private void setPBEnabled(boolean pbEnabled) {
		this.enabled = pbEnabled;
	}
	
	public String getPBTriggerPolicy() {
		return triggerPolicy;
	}

	public boolean isPBConfigured() {
		return configured;
	}
	
	public boolean isPBTriggerPolicyUnknown() {
		return triggerPolicyUnknown;
	}
	
	public boolean isPBEnabled() {
		return enabled;
	}
	
	public String getId() {
		return id;
	}

	public static class BuildDefinitionInfoBuilder {
		
		private BuildDefinitionInfo bdInfo = new BuildDefinitionInfo();

		public BuildDefinitionInfoBuilder setPBTriggerPolicy(String triggerPolicy) {
			this.bdInfo.setPBTriggerPolicy(triggerPolicy);
			return this;
		}
		
		public BuildDefinitionInfoBuilder setPBConfigured(boolean configured) {
			this.bdInfo.setPBConfigured(configured);
			return this;
		}
		
		public BuildDefinitionInfoBuilder setPBTriggerPolicyUnknown(boolean triggerPolicyUnknown) {
			this.bdInfo.setPBTriggerPolicyUnknown(triggerPolicyUnknown);
			return this;
		}

		public BuildDefinitionInfo build() {
			return bdInfo;
		}
		
		public BuildDefinitionInfoBuilder setId(String id) {
			this.bdInfo.setId(id);
			return this;
		}

		public BuildDefinitionInfoBuilder setPBEnabled(boolean pbEnabled) {
			this.bdInfo.setPBEnabled(pbEnabled);
			return this;
		}
	}
	
}


