/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import static org.junit.Assert.assertEquals;
import java.util.Random;

import org.junit.Test;

import com.ibm.team.build.internal.hjplugin.rtc.Utils;

/**
 * 
 *Tests for Utils class
 *
 */
public class UtilsTest {
	/**
	 * Test whether a given string with 0x10 character is replaced with blank character
	 *  
	 * @throws Exception Throw all exceptions back to JUnit
	 */
	@Test
	public void testFixControlCharactersWith0x10() throws Exception {
		String baseString = "#XThis is #X test and there is a chara#Xcter in this string#X";
		String verificationString = baseString.replace("#X", " ");
		assertEquals(verificationString, Utils.fixControlCharacters(
				baseString.replace("#X", "\u0010")));
	}
	
	/**
	 * Test whether a given string with 0x00 character is replaced with blank character
     *
	 * @throws Exception Throw all exceptions back to JUnit
	 */
	@Test
	public void testFixControlCharactersWithNullByte() throws Exception {
		String baseString = "#XThis is #X test and there is a chara#Xcter in this string#X";
		String verificationString = baseString.replace("#X", " ");
		assertEquals(verificationString, Utils.fixControlCharacters(
				baseString.replace("#X", "\u0000")));
	}
	
	/**
	 * Test whether a given string with 0x00 character is replaced with blank character
     *
	 * @throws Exception Throw all exceptions back to JUnit
	 */
	@Test
	public void  testFixControlCharactersWithSingleRandomControlCharactersInString() throws Exception {
		int tries = 10;
		{
			String baseString1 = "#XThis is #X test and there is a chara#Xcter in this string#X";
			int [] testIndices = new int[tries];
			// First get an array of random integers. If any of them is the safe character integers, just skip
			Random r = new Random(System.currentTimeMillis());
			for (int i = 0 ; i < tries; i++) {
				testIndices[i] = getSafeIndex(r); 
			}
			String verificationString = baseString1.replace("#X", " ");
			for (int i = 0 ; i < tries; i++) {
				// Pick a random character from the unsafeCharacters list
				String ctrlCharacter = Utils.unsafeCharacters[testIndices[i]];
				assertEquals(verificationString, Utils.fixControlCharacters(baseString1.replace("#X", ctrlCharacter)));
			}
		}
	}
	
	/**
	 * 
	 * @throws Exception
	 */
	@Test
	public void testFixControlCharactersWithTwoRandomControlCharactersInString() throws Exception {
		int tries = 10;
		String baseString2 = "#XThis is #X#Y test and there is a chara#Xct#Yer in this string#Y";
		Pair<Integer, Integer> [] testIndices = new Pair[tries];
		// First get an array of random integers. If any of them is the safe character integers, just skip
		Random r = new Random(System.currentTimeMillis());
		for (int i = 0 ; i < tries; i++) {
			testIndices[i] = new Pair<>(getSafeIndex(r), getSafeIndex(r)); 
		}
		String verificationString = baseString2.replace("#X", " ").replace("#Y", " ");
		for (int i = 0 ; i < tries; i++) {
			String ctrlCharacter1 = Utils.unsafeCharacters[testIndices[i].getFirst()];
			String ctrlCharacter2 = Utils.unsafeCharacters[testIndices[i].getSecond()];
			assertEquals(verificationString, Utils.fixControlCharacters(
						baseString2.replace("#X", ctrlCharacter1).replace("#Y", ctrlCharacter2)));
		}
	}
	
	private int getSafeIndex (Random r) {
		int index = r.nextInt(32); // 0x20
		while (index != 9  && index != 10 && index != 13) {
			index = r.nextInt(32); // 0x20
		}
		return index;
	}

	/**
	 * @param <T1> 
	 * @param <T2> 
	 *
	 */
	static class Pair<T1,T2> {
	    private T1 first;
	    private T2 second;
	    
	    /**
	     * @param first
	     * @param second
	     */
	    public Pair(final T1 first, final T2 second) {
	        this.first = first;
	        this.second = second;
	    }
	    
	    /**
	     * @return the first element of the Pair
	     */
	    public T1 getFirst() {
	        return first;
	    }
	    
	    /**
	     * @return the second element of the Pair
	     */
	    public T2 getSecond() {
	        return second;
	    }
	}
}
