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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.map.LRUMap;

/**
 * Factory for the RTC Build client.
 */
public class RTCFacadeFactory {
    private static final Logger LOGGER = Logger.getLogger(RTCFacadeFactory.class.getName());

	private static final int DEFAULT_CACHE_SIZE = 10;
	private static final String CACHE_SIZE_PROPERTY = "com.ibm.team.build.classLoaderCacheSize"; //$NON-NLS-1$

    private static transient LRUMap fgRTCFacadeCache;
    
    /**
     * Returns a facade for interfacing with RTC, using the classes in the RTC build toolkit at the given path.
     * Current limitation: only one toolkit path at a time is supported.  
     * This will create a new facade if the path changes, but that's expensive.
     * Note that the return type is a wrapper and only supports the invoke reflection method. This is 
     * deliberate since RTCFacade must be loaded with the class loader that can see the RTC/Jazz/Eclipse types
     * and it will manage the class loader when executing methods.
     */
	public synchronized static RTCFacadeWrapper getFacade(String buildToolkitPath, PrintStream debugLog) throws Exception {
		if (fgRTCFacadeCache == null) {
			int mapSize = DEFAULT_CACHE_SIZE;
			String mapSizeProperty = System.getProperty(CACHE_SIZE_PROPERTY, String.valueOf(DEFAULT_CACHE_SIZE));
			try {
				mapSize = Integer.parseInt(mapSizeProperty);
			} catch (NumberFormatException e) {
				debug(debugLog, "Unable to parse system property " + CACHE_SIZE_PROPERTY + "=" + mapSizeProperty);   //$NON-NLS-1$//$NON-NLS-2$
			}
			debug(debugLog, "Class loader cache size is " + mapSize); //$NON-NLS-1$
			fgRTCFacadeCache = new LRUMap(mapSize);
		}
		
		if (buildToolkitPath == null) {
			throw new IllegalArgumentException(Messages.RTCFacadeFactory_missing_toolkit());
		}

		File buildToolkitFile = new File(buildToolkitPath);
		String stdBuildToolkitPath = buildToolkitFile.getAbsolutePath();
		RTCFacadeWrapper rtcFacade = (RTCFacadeWrapper) fgRTCFacadeCache.get(stdBuildToolkitPath);
		if (rtcFacade == null) {
			rtcFacade = RTCFacadeFactory.newFacade("com.ibm.team.build.internal.hjplugin.rtc.RTCFacade", //$NON-NLS-1$
					buildToolkitFile, debugLog);
			if (fgRTCFacadeCache.isFull()) {
				debug(debugLog, "Class loader cache(" + fgRTCFacadeCache.maxSize() + ") is full."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			fgRTCFacadeCache.put(stdBuildToolkitPath, rtcFacade);  // only cache if successful
		} else {
			debug(debugLog, "Reusing facade for " + stdBuildToolkitPath); //$NON-NLS-1$
		}
		return rtcFacade;
	}

	/**
	 * Wrapper on the facade to ensure the class loader is setup and restored
	 * between calls
	 */
	public static class RTCFacadeWrapper {
		private Object facade;
		private URLClassLoader newClassLoader;
		
		public Object invoke(String methodName, Class[] argumentTypes, Object... arguments) throws Exception {
			
			ClassLoader currentClassLoader = setContextClassLoader();
			try {
	    		Method m = facade.getClass().getMethod(methodName, argumentTypes);
	    		return m.invoke(facade, arguments);
			} catch (NoSuchMethodException e) {
				LOGGER.finer(e.getMessage());
				StringBuilder lookingFor = new StringBuilder("Looking for: ");
				lookingFor.append(methodName).append("(");
				boolean first = true;
				for (Class argument : argumentTypes) {
					if (!first) {
						lookingFor.append(",");
					} else {
						first = false;
					}
					lookingFor.append(argument.getSimpleName());
				}
				lookingFor.append(")");
				LOGGER.finer(lookingFor.toString());
				for (Method method : facade.getClass().getMethods()) {
					LOGGER.finer(method.toString());
				}
				throw e;
	    	} catch (InvocationTargetException e) {
	    		Throwable eToReport = e.getCause();
	    		if (eToReport instanceof Exception) {
	    			throw (Exception) eToReport;
	    		} else {
	    			throw e;
	    		}
			} finally {
				resetContextClassLoader(currentClassLoader);
			}
		}

		/**
		 * Sets the current thread's context class loader to be ours.  This is needed to ensure that
		 * the EMF package registry can find the previously registered packages.  
		 * Without this, you'll likely see EMF package not found errors. 
		 */
		protected ClassLoader setContextClassLoader() {
			ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(newClassLoader);
			return originalClassLoader;
		}
		
		protected void resetContextClassLoader(ClassLoader classLoader) {
			Thread.currentThread().setContextClassLoader(classLoader);
		}

	}
	
	public static RTCFacadeWrapper newTestingFacade(String toolkitPath) throws Exception {

		return newFacade("com.ibm.team.build.internal.hjplugin.rtc.tests.RTCTestingFacade", new File(toolkitPath), null); //$NON-NLS-1$
	}

	private static RTCFacadeWrapper newFacade(String fullClassName, File toolkitFile, PrintStream debugLog) throws Exception {

		if (!toolkitFile.exists()) {
			throw new IllegalArgumentException(Messages.RTCFacadeFactory_toolkit_not_found(toolkitFile.getAbsolutePath()));
		}
		if (!toolkitFile.isDirectory()) {
			throw new IllegalArgumentException(Messages.RTCFacadeFactory_toolkit_path_not_directory(toolkitFile.getAbsolutePath()));
		}
		
		RTCFacadeWrapper result = new RTCFacadeWrapper();
		
		URL[] jarURLs = getJarURLs(toolkitFile, debugLog);
		
		Class<?> originalClass = RTCFacadeFactory.class;
		URLClassLoader originalClassLoader = (URLClassLoader) originalClass.getClassLoader();
		debug(debugLog, "Original class loader: " + originalClassLoader); //$NON-NLS-1$
		
		URL[] originalURLs = originalClassLoader.getURLs();
		debug(debugLog, originalURLs.length + " Original class loader URLs:"); //$NON-NLS-1$
		for (URL url : originalURLs) {
			debug(debugLog, url.toString());
		}
		
		URL[] combinedURLs = new URL[jarURLs.length + originalURLs.length];
		System.arraycopy(originalURLs, 0, combinedURLs, 0, originalURLs.length);
		System.arraycopy(jarURLs, 0, combinedURLs, originalURLs.length, jarURLs.length);
		result.newClassLoader = new URLClassLoader(combinedURLs, originalClassLoader.getParent());

		debug(debugLog, "new classloader: " + result.newClassLoader); //$NON-NLS-1$
		Class<?> facadeClass = result.newClassLoader.loadClass(fullClassName);
		debug(debugLog, "facadeClass: " + facadeClass); //$NON-NLS-1$
		debug(debugLog, "facadeClass classloader: " + facadeClass.getClassLoader()); //$NON-NLS-1$
		
		// using the new class loader get the facade instance
		// then revert immediately back to the original class loader
		Thread.currentThread().setContextClassLoader(result.newClassLoader);
		try {
			result.facade = facadeClass.newInstance();
		} finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
		
		debug(debugLog, "facade: " + result.facade); //$NON-NLS-1$
		return result;	
	}
	
	private static void debug(PrintStream debugLog, String msg) {
		LOGGER.finer(msg);
		if (debugLog != null) {
			debugLog.println(msg);
		}
	}


	private static URL[] getJarURLs(File toolkitFile, PrintStream debugLog) throws IOException {
		File[] files = toolkitFile.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.getName().toLowerCase().endsWith(".jar") && !file.isDirectory(); //$NON-NLS-1$
			}
		});
		if (files == null) {
			throw new RuntimeException(Messages.RTCFacadeFactory_scan_error(toolkitFile.getAbsolutePath()));
		}
		
		// Log what we found 
		if (LOGGER.isLoggable(Level.FINER) || debugLog != null) {
			String eol = System.getProperty("line.separator");  //$NON-NLS-1$
			StringBuilder message = new StringBuilder("Found ").append(files.length) //$NON-NLS-1$
					.append(" jars in ").append(toolkitFile.getAbsolutePath()).append(eol); //$NON-NLS-1$
			for (File file : files) {
				message.append(file.getName()).append(eol);
			}
			debug(debugLog, message.toString());
		}

		URL[] urls = new URL[files.length];
		for (int i = 0; i < files.length; ++i) {
			urls[i] = files[i].toURI().toURL();
		}
		return urls;
	}

}
