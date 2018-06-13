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

package com.ibm.team.build.internal.hjplugin.rtc;

import java.nio.channels.ClosedByInterruptException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;

import com.ibm.team.filesystem.client.FileSystemStatusException;
import com.ibm.team.repository.common.TeamRepositoryException;

/**
 * Utilities
 *
 */
public class Utils {
	/**
	 * The " " character
	 */
	public final static String BLANK = " ";

	/**
	 * The list of ASCII control characters unsafe for XML
	 */
	public final static String [] unsafeCharacters = {
	"\u0000", "\u0001", "\u0002" ,"\u0003", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	"\u0004", "\u0005", "\u0006", "\u0007", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	"\u0008", /*tab*//*newline*/ "\u000b", //$NON-NLS-1$ //$NON-NLS-2$ 
	"\u000c", /*cr*/   "\u000e","\u000f", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	"\u0010", "\u0011", "\u0012", "\u0013", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	"\u0014", "\u0015", "\u0016", "\u0017", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	"\u0018", "\u0019", "\u001A", "\u001B", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	"\u001C", "\u001D", "\u001E", "\u001F"	//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$	
			};	
	// Create a Regex out of unsafe characters
	private final static String unsafeCharactersRegex = "[" + join("", unsafeCharacters).toString() + "]";   //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	
	private Utils() {}
	
	private static StringBuffer join(String delimiter, String[] strArr) {
		StringBuffer joinedStr = new StringBuffer(""); //$NON-NLS-1$
		if (strArr == null) {
			return joinedStr;
		}
		joinedStr.append(strArr[0]);
		{
			int index = 1;
			while (index < strArr.length) {
				joinedStr.append(delimiter).append(strArr[index]);
				index++;
			}
		}
		return joinedStr;
	}

	/**
	 * 
	 * @param e
	 * 
	 * @return
	 */
	public static Exception checkForCancellation(Exception e) {
		if (e instanceof FileSystemStatusException) {
			if (((FileSystemStatusException) e).getStatus().matches(IStatus.CANCEL)) {
				InterruptedException result = new InterruptedException(e.getMessage());
				result.initCause(e);
				return result;
			}
		}
		if (isInterruptedException(e)) {
			InterruptedException result = new InterruptedException(e.getMessage());
			result.initCause(e);
			return result;
		}
		return e;
	}
	
	private static boolean isInterruptedException(Exception e) {
		Throwable nested = e;
		// We are only digging 20 deep because I am paranoid and its unlikely that an interrupt
		// would be nested deeper than that.
		for (int i=0; i<20; i++) {
			if (nested == null) {
				return false;
			} else if ((nested instanceof OperationCanceledException)
					|| (nested instanceof InterruptedException) 
					|| (nested instanceof ClosedByInterruptException)) {
				return true;
			} else {
				nested = nested.getCause();
			}
		}
		return false;
	}

	/**
	 * Determine if the exception thrown is caused by a cancellation
	 * @param e The exception to investigate
	 * @return <code>true</code> if the exception represents a cancellation
	 * <code>false</code> otherwise.
	 */
	public static boolean isCancellation(TeamRepositoryException e) {
		if (e instanceof FileSystemStatusException) {
			if (((FileSystemStatusException) e).getStatus().matches(IStatus.CANCEL)) {
				return true;
			}
		}
		return isInterruptedException(e);
	}
	
	/**
	 * 
	 * Return null if the string is blank or null if not trim the string and return.
	 * 
	 * @param s The string to work with
	 * @return a string which has no whitespace characters at the beginning or end. 
	 * 			If the string has only whitespace characters, then <code>null</code> is 
	 * 		    returned. 
	 */
	public static String fixEmptyAndTrim(String s) {
		if (s == null || s.trim().length() == 0)
			return null;
		return s.trim();
	}
	
	/**
	 * Replace ASCII control characters from u+0000 to u+001f with u+0020 (blank character) 
	 * expect for the following
	 * u+0009 (tab)
	 * u+000a (newline)
	 * u+000d (carriage return)
	 * 
	 * @param s The string to work with
	 * @return a string in which all control characters, except the ones in safe list 
	 *       are replaced with the blank character.
	 *          
	 */
	public static String fixControlCharacters(String s) {
		if (s == null) return s;
		s = s.replaceAll(unsafeCharactersRegex, BLANK);	
		return s;
	}
}
