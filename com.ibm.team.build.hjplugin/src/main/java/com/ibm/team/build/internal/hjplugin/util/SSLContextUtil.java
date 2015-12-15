/*******************************************************************************
 * Copyright (c) 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.util;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class SSLContextUtil {

	private final static Logger LOGGER = Logger.getLogger(SSLContextUtil.class.getName());
	
    public static final String SSL_TLS = "SSL_TLS"; //$NON-NLS-1$

    public static final String TLS = "TLS"; //$NON-NLS-1$

    public static final String SSL = "SSL"; //$NON-NLS-1$
	
	/**
	 * Creates an SSL context factory.
	 * The returned SSLContext will be created so that it is compatible with the
	 * current security environment.  If a FIPS environment is detected then a
	 * FIPS 140-2 complaint context will be returned. 
	 * 
	 * @return a {@link SSLContext}
	 */
	public static SSLContext createSSLContext(TrustManager trustManager) {
       return createSSLContext(null, trustManager);
    }
	/**
	 * Creates an SSL context factory.
	 * The returned SSLContext will be created so that it is compatible with the
	 * current security environment.  If a FIPS environment is detected then a
	 * FIPS 140-2 complaint context will be returned. 
	 * 
	 * @return a {@link SSLContext}
	 */
	public static SSLContext createSSLContext(KeyManager[] keyManagers, TrustManager trustManager) {
	    
	    String overrideProtocol = System.getProperty("com.ibm.team.repository.transport.client.protocol"); //$NON-NLS-1$
	    SSLContext context = null;
	    if (overrideProtocol != null)  {
	        LOGGER.finer("Attempting to create protocol context using system property: " + overrideProtocol);  //$NON-NLS-1$
	        context = createSSLContext(overrideProtocol, keyManagers, trustManager);
	    }

	    if (context == null) {
	        LOGGER.finer("Attempting to create SSL_TLS context");  //$NON-NLS-1$
	        context = createSSLContext(SSL_TLS, keyManagers, trustManager);
	    }

        if (context == null) {
            LOGGER.finer("Unable to create SSL_TLS context, trying TLS");  //$NON-NLS-1$
              // When SSL_TLS doesn't work (e.g. under FIPS), try TLS
              context = createSSLContext(TLS, keyManagers, trustManager);
          }

        if (context == null) {
          LOGGER.finer("Unable to create TLS context, trying SSL"); //$NON-NLS-1$
        	// Fall back to just SSL when the above two are not available
        	context = createSSLContext(SSL, keyManagers, trustManager);
        }

        if (context == null) {
        	/* No encryption algorithm worked.  Give up.  This should never happen
        	 * in any of our supported configurations. */
        	throw new RuntimeException("No acceptable encryption algorithm found"); //$NON-NLS-1$
        }

        return context;
	}
	
	// Returns null when the given algorithm fails
	private static SSLContext createSSLContext(String algorithm, KeyManager[] keyManagers, TrustManager trustManager) {
		SSLContext context;
		try {
			context = SSLContext.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			LOGGER.log(Level.FINER, "No such algorithm creating SSL Context", e); //$NON-NLS-1$
			return null;
		}
		try {
			context.init(keyManagers, new TrustManager[] { trustManager }, null);
		} catch (KeyManagementException e) {
			LOGGER.log(Level.FINER, "Key management issue creating SSL Context", e); //$NON-NLS-1$
			return null;
		}

		/* Create a socket to ensure this algorithm is acceptable.  This will
		 * correctly disallow certain configurations (such as SSL_TLS under FIPS) */
		try {
			Socket s = context.getSocketFactory().createSocket();
			s.close();
		} catch (IOException e) {
		  LOGGER.log(Level.FINER, "error creating socket ensuring algorithm is acceptable", e); //$NON-NLS-1$
			return null;
		} catch (IllegalArgumentException e) {
			  LOGGER.log(Level.FINER, "Illegal argument creating socket ensuring algorithm is acceptable", e); //$NON-NLS-1$
			return null;
		}
		return context;
	}	
}
