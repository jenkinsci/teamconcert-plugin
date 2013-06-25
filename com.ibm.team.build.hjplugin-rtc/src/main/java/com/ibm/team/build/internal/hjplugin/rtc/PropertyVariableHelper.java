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

package com.ibm.team.build.internal.hjplugin.rtc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.ibm.team.build.common.model.IBuildConfigurationElement;
import com.ibm.team.build.common.model.IConfigurationProperty;

/**
 * Helper for processing build properties and performing variable substitution.
 */
public class PropertyVariableHelper {
    private static final String VARIABLE_START = "${"; //$NON-NLS-1$
    private static final String VARIABLE_END = "}"; //$NON-NLS-1$
    
    /**
     * Substitute any property references within the build property values (one
     * property value references another property's name). Substitution
     * continues until there are no more references to substitute or no
     * substitutions are made. For example, before expansion:
     * <ul>
     * <li>a = hello</li>
     * <li>b = ${a} there</li>
     * </ul>
     * After expansion:
     * <ul>
     * <li>a = hello</li>
     * <li>b = hello there</li>
     * </ul>
     * 
     * @param buildProperties
     *            The build properties.
     * @return A list of substitution descriptions. Each entry looks like
     *         <code>b = ${a} there   -->   b = hello there</code>.
     * @throws IllegalArgumentException
     *             If there are any direct or indirect cycles in the property
     *             references.
     */
    public static List<String> substituteBuildPropertyVariables(Map<String, String> buildProperties) {
        boolean substitutionsMade = true;
        List<String> substitutions = new ArrayList<String>();

        while (containsAnyVariables(buildProperties.values()) && substitutionsMade) {
            substitutionsMade = false;

            // For each value.
            for (Map.Entry<String, String> valueEntry : buildProperties.entrySet()) {
                String originalValue = valueEntry.getValue();

                if (originalValue.contains(VARIABLE_START)) {
                    
                    String newValue = originalValue;
                    
                    // For each name.
                    for (Map.Entry<String, String> nameEntry : buildProperties.entrySet()) {
                        // Check for a cycle, such as a = ${a}
                        if (newValue.contains(variableReference(valueEntry.getKey()))) {
                            handleCycle(substitutions, valueEntry.getKey(), newValue);
                        }

                        // Substitute any variable occurrences of the name in the
                        // original value.
                        newValue = newValue.replace(variableReference(nameEntry.getKey()),
                                nameEntry.getValue());
                    }

                    if (!newValue.equals(originalValue)) {
                        substitutions.add(Messages.getDefault().PropertyVariableHelper_substitution(
                                valueEntry.getKey(), originalValue, valueEntry.getKey(), newValue ));
                        substitutionsMade = true;
                        valueEntry.setValue(newValue);
                    }

                }
            }
        }

        return substitutions;
    }

    /**
     * Substitute any build property references within the configuration
     * property values (a configuration property value references a build
     * property name). Configuration properties cannot reference other
     * configuration properties.
     * 
     * @param configElements
     *            The request containing the configuration properties.
     * @param buildProperties
     *            The build properties to use as a source.
     * @return A list of substitution descriptions. Each entry looks like
     *         <code>destination = ${root}/fetched   -->   destination = /releng/fetched</code>.
     */
    public static List<String> substituteConfigurationElementPropertyVariables(
            List<IBuildConfigurationElement> configElements, Map<String, String> buildProperties) {

        List<String> substitutions = new ArrayList<String>();

        for (IBuildConfigurationElement element : configElements) {

            if (element.isVariableSubstitutionAllowed()) {

                // For each configuration property
                for (Object object : element.getConfigurationProperties()) {
                    IConfigurationProperty configProperty = (IConfigurationProperty) object;
                    String originalValue = configProperty.getValue();

                    if (originalValue.contains(VARIABLE_START)) {

                        // For each build property.
                        for (Map.Entry<String, String> buildProperty : buildProperties.entrySet()) {

                            // Replace any variable occurences of the name in
                            // the config element property value.
                            String newValue = originalValue.replace(variableReference(buildProperty.getKey()),
                                    buildProperty.getValue());

                            if (!newValue.equals(originalValue)) {
                                substitutions.add(Messages.getDefault().PropertyVariableHelper_substitution_for_element(
                                        element.getElementId(), configProperty.getName(), originalValue,
                                                configProperty.getName(), newValue ));
                                originalValue = newValue;
                            }

                            configProperty.setValue(newValue);
                        }
                    }
                }
            }
        }

        return substitutions;
    }

    private static void handleCycle(List<String> substitutions, String propertyName, String propertyValue) {

        StringBuffer message = new StringBuffer();

        message.append(Messages.getDefault().PropertyVariableHelper_cycle(propertyName, propertyValue));

        if (!substitutions.isEmpty()) {
            String newline = System.getProperty("line.separator"); //$NON-NLS-1$
            message.append(newline);

            message.append(Messages.getDefault().PropertyVariableHelper_cycle_description());

            message.append(newline);

            for (String substitution : substitutions) {
                message.append(substitution);
                message.append(newline);
            }
        }

        throw new IllegalArgumentException(message.toString());
    }

    private static String variableReference(String propertyName) {
        return VARIABLE_START + propertyName + VARIABLE_END;
    }

    private static boolean containsAnyVariables(Collection<String> propertyValues) {
        for (String propertyValue : propertyValues) {
            if (propertyValue.contains(VARIABLE_START)) {
                return true;
            }
        }
        return false;
    }

}
