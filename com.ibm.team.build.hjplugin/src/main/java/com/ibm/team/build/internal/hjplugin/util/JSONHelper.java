/*******************************************************************************
 * Copyright (c) 2014, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.util;

import java.util.Iterator;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Contains generic methods that help us navigate through the JSON responses
 */
public class JSONHelper {
	
	/**
	 * gets the field identified as a Boolean from the JSON item.
	 * @param json The JSON object containing the Boolean field
	 * @param fieldName The name of the field
	 * @return The Boolean value of the field or <code>null</code> if the json is not
	 * a JSONObject, the field is not present or the field's value is not a boolean.
	 */
	public static Boolean getBoolean(JSON json, String fieldName) {
		if (json instanceof JSONObject && ((JSONObject) json).containsKey(fieldName)) {
			return ((JSONObject) json).getBoolean(fieldName);
    	}
    	return null;
	}
	
	/**
	 * gets the field identified as a String from the JSON item.
	 * @param json The JSON object containing the String field
	 * @param fieldName The name of the field
	 * @return The String value of the field or <code>null</code> if the json is not
	 * a JSONObject, the field is not present or the field's value is not a string.
	 */
	public static String getString(JSON json, String fieldName) {
		if (json instanceof JSONObject) {
			Object result = ((JSONObject) json).get(fieldName);
			if (result instanceof String) {
				return (String) result;
			}
		}
		return null;
	}
	
	/**
	 * gets the field identified as a JSONArray from the JSON item.
	 * @param json The JSON object containing the JSONArray field
	 * @param fieldName The name of the field
	 * @return The Boolean value of the field or <code>null</code> if the json is not
	 * a JSONObject, the field is not present or the field's value is not a JSONArray.
	 */
	public static JSONArray getJSONArray(JSON json, String fieldName) {
		if (json instanceof JSONObject) {
	        // validate the build definition has a supporting build engine
			Object result = ((JSONObject) json).get(fieldName);
			if (result instanceof JSONArray) {
				return (JSONArray) result;
			}
		}
		return null;
	}
	
	/**
	 * 
	 * @param json
	 * @param fieldName
	 * @return
	 */
	public static JSONObject getJSONObject(JSON json, String fieldName) {
		if (json instanceof JSONObject) {
			Object result = ((JSONObject)json).get(fieldName);
			if (result instanceof JSONObject) {
				return (JSONObject) result;
			}
		}
		return null;
	}

	/**
	 * Searches a JSONArray field in a JSONObject for an element with a field
	 * matching the value.
	 * @param jsonObject The object containing the array to search
	 * @param arrayField Name of the field containing the array
	 * @param arrayEntryField Name of the field in the array elements whose value is to be matched
	 * @param fieldValue The value we are searching for
	 * @return <code>true</code> if found. <code>false</code> if not found.
	 * It may not be found if the structure is unexpected.
	 */
	public static boolean searchJSONArray(JSONObject jsonObject,
			String arrayField, String arrayEntryField, String fieldValue) {
		JSONArray jsonArray = getJSONArray(jsonObject, arrayField);
		if (jsonArray != null) {
			for (Iterator<JSONObject> iterator=jsonArray.iterator(); iterator.hasNext();) {
				JSONObject element = iterator.next();
				Object field = element.get(arrayEntryField);
				if (fieldValue.equals(field)) {
					return true;
				}
			}
		}
		return false;
	}

}
