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

package com.ibm.team.build.internal.hjplugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

/**
 * A sometimes Child First ClassLoader for use in RTCFacades. 
 * Only provide resources from parent and system classloaders, we do not search through the parent hierarchy.
 */
public class RTCFacadeClassLoader extends URLClassLoader {

	public RTCFacadeClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}
	
	private class RTCFacadeEnumeration<T> implements Enumeration<T> {
		
		private Enumeration<? extends T> e1, e2;
		
		private RTCFacadeEnumeration(Enumeration<? extends T> firstEnumeration, Enumeration<? extends T> secEnumeration) {
			this.e1 = firstEnumeration;
			this.e2 = secEnumeration;
		}
		
		@Override
		public boolean hasMoreElements() {
			boolean result = e1.hasMoreElements();
			if (!result && e2 != null)
				result = e2.hasMoreElements();
			return result;
		}

		@Override
		public T nextElement() {
			T result = null;
			if (e2 == null || e1.hasMoreElements())
				result = e1.nextElement();
			else
				result = e2.nextElement();	
			return result;
		}
		
	}
	
	@Override
	public Enumeration<URL> getResources(String resName) throws IOException {
		//cut out parent resources, since in general we don't really want them anyway.
		// including system resources just to be safe
		return new RTCFacadeEnumeration<URL>(findResources(resName), getSystemClassLoader() != null ? getSystemClassLoader().getResources(resName) : null);
	}
	
	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		Class<?> c = findLoadedClass(name);
		if (c == null) {	
			try {
				//this is to stop org.eclipse.core.runtime.RegistryFactory from within the depths of WAS
				//from being reused.
				if (name.startsWith("org.eclipse.")) {
					c = findClass(name);
				}
			} catch (ClassNotFoundException e) {
				//do nothing
			}
		}
		if (c == null) {
			//may eventually call findClass again, oh well
			c = super.loadClass(name, resolve);
		}
		if (resolve && c != null) resolveClass(c);
		return c;
	}
}
