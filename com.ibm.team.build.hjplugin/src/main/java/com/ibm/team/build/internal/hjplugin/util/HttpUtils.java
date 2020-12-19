/*******************************************************************************
 * Copyright (c) 2014, 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.team.build.internal.hjplugin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.auth.AuthSchemeBase;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.CharArrayBuffer;

import com.ibm.team.build.internal.hjplugin.Messages;

import hudson.model.TaskListener;
import hudson.util.IOUtils;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

/**
 * Collection of methods to handle authentication and providing the response back.
 * For a GET, the caller supplies uri and gets JSON back.
 * For a PUT, the caller supplies uri & any stream data.
 */
public class HttpUtils {

	//As of now we are not explicitly supporting KERBEROSSPNEGO, CLIENTCERTS, WINDOWSINTEGRATED
	public enum AuthenticationType {
	    USERIDPASSWD, KERBEROSSPNEGO, CLIENTCERTS, WINDOWSINTEGRATED
	}
	
	private static final String SLASH = "/";  //$NON-NLS-1$

	private static final String TEXT_JSON = "text/json"; //$NON-NLS-1$

	private static final String UTF_8 = "UTF-8"; //$NON-NLS-1$

	private static final Logger LOGGER = Logger.getLogger(HttpUtils.class.getName());

	private static final String NEW_LINE = System.getProperty("line.separator"); //$NON-NLS-1$

	// aka "http.conn-manager.timeout"
	// Its how long we should wait to get a connection from the connection manager
	private static final int CONNECTION_REQUEST_TIMEOUT_MILLIS = 480000;
	
	private static final String FORM_AUTHREQUIRED_HEADER = "X-com-ibm-team-repository-web-auth-msg"; //$NON-NLS-1$
	private static final String FORM_AUTHREQUIRED_HEADER_VALUE = "authrequired"; //$NON-NLS-1$
	private static final String AUTHFAILED_HEADER_VALUE = "authfailed"; //$NON-NLS-1$
	private static final String BASIC_AUTHREQUIRED_HEADER = "WWW-Authenticate"; //$NON-NLS-1$
	public static final String SCHEME_HTTP = "http"; //$NON-NLS-1$
	public static final String SCHEME_HTTPS = "https"; //$NON-NLS-1$
	public static final String X_SERVICE_VERSION = "X-com-ibm-team-service-version";
	public static final int HTTP_RETRY_REQUEST_COUNT = 3;
    private static Pattern JAUTH_PATTERN = Pattern.compile("^[Jj][Aa][Uu][Tt][Hh]\\s+.*"); //$NON-NLS-1$
    private static Pattern BASIC_PATTERN = Pattern.compile("^[Bb][Aa][Ss][Ii][Cc]\\s+.*"); //$NON-NLS-1$
    private static final String LOCATION = "Location"; //$NON-NLS-1$
	
	private static CloseableHttpClient HTTP_CLIENT = null;

	private static SSLConnectionSocketFactory SSL_CONNECTION_SOCKET_FACTORY;
	
	public static final String APP_PASSWORD_ENABLED = " app-password-enabled"; //$NON-NLS-1$
    private static final String JAZZ_NATIVE_CLIENT = "Jazz Native Client" + APP_PASSWORD_ENABLED;
    private final static String RETRYING_AUTH_SERVER_CHALLENGE = "RetryingAuthServerChallenge"; //$NON-NLS-1$
    private final static String AuthSchemeBearer = "Bearer"; //$NON-NLS-1$
    private final static Collection<String> authSchemePreferences = Arrays
            .asList(new String[] { AuthSchemeBearer, AuthSchemes.SPNEGO, AuthSchemes.BASIC });
 // the default is USERIDPASSWD. This is mostly to cater for JUNITS
    private static volatile AuthenticationType _authenticationType = AuthenticationType.USERIDPASSWD;
//	static {
//		LOGGER.setLevel(Level.FINER);
//		ConsoleHandler handler = new ConsoleHandler();
//		handler.setLevel(Level.FINER);
//		LOGGER.addHandler(handler);
//	}

    public static class AdaptedSPNegoSchemeFactory extends SPNegoSchemeFactory {

        /**
         * 
         */
        public AdaptedSPNegoSchemeFactory() {
            super();
        }

        /**
         * @param stripPort
         */
        public AdaptedSPNegoSchemeFactory(boolean stripPort) {
            super(stripPort);
        }

    }
    
    public static class BearerSchemeProvider implements AuthSchemeProvider {

        @Override
        public AuthScheme create(HttpContext context) {
            return new BearerScheme();
        }
    }
    
    public static class BearerScheme extends AuthSchemeBase {
        private final static String BEARER = "bearer"; //$NON-NLS-1$
        private final static String JSA_REALM = "jsa"; //$NON-NLS-1$

        public BearerScheme() {
        }

        @Override
        public String getSchemeName() {
            return BEARER;
        }

        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public String getRealm() {
            return JSA_REALM;
        }

        @Override
        public boolean isConnectionBased() {
            return false;
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public Header authenticate(Credentials credentials, HttpRequest request) throws AuthenticationException {
            // return null means the Authorization header is not set to begin with. This will lead to a 401 which is reported back to the 
            // RemoteTeamServer as no other AuthScheme's will be attempted. Therefore, this avoid that credentials are sent to the RP
            return null;
        }

        @Override
        protected void parseChallenge(CharArrayBuffer buffer, int beginIndex, int endIndex) throws MalformedChallengeException {
            // nothing to parse
        }

    }
    
    /**
     * A custom redirect strategy that allows redirects on the following methods. The assumption here is that a redirect on a non-idempotent method is for the
     * purposes of authentication redirect, and will not cause the original method to replay. E.G. when a POST request is redirected by the team server, that is
     * because: a) the container is redirected from http to https; b) the caller is being redirected to an URL that requires authentication. The subsequent
     * return to the original request in that case will be a GET, which will get replayed in subsequent authentication logic in this class.
     */
    private static class AuthRedirectStrategy extends DefaultRedirectStrategy {

        static private String[] METHODS = new String[] {
                HttpDelete.METHOD_NAME,
                HttpGet.METHOD_NAME,
                HttpHead.METHOD_NAME,
                HttpOptions.METHOD_NAME,
                HttpPost.METHOD_NAME,
                HttpPut.METHOD_NAME
        };

        @Override
        protected boolean isRedirectable(String method) {
        	LOGGER.finer("Entering isRedirectable: "+ method);
        	for (String name : METHODS)
                if (name.equalsIgnoreCase(method))
                    return true;

            return false;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.apache.http.impl.client.DefaultRedirectStrategy#isRedirected(org.apache.http.HttpRequest, org.apache.http.HttpResponse,
         * org.apache.http.protocol.HttpContext)
         *
         * Overridden here to not redirect on 301. The previous version of this rich client library did not redirect on 301s, and we're following that to
         * maintain compatibility (which at this time, appears to be only tests). Note also the the location header returned via the ContentMovedException on
         * the server is typically not one amenable to a GET redirect.
         *
         * For example, in the test that I observed, a DELETE was sent to the a URL that mapped to the ItemRestService on a fronting-app, which failed with a
         * ContentMovedException, containing a location to the itemName/Contributor/ADMIN URI, which will map on the server to the ItemRenderService. The
         * ItemRenderService doesn't expect to be called with the rich client and service versioning information, and fails that versioning check.
         */
        @Override
        public boolean isRedirected(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws ProtocolException
        {
            final int statusCode = response.getStatusLine().getStatusCode();
            final String method = request.getRequestLine().getMethod();
            final Header locationHeader = response.getFirstHeader("location"); //$NON-NLS-1$

            LOGGER.finer("Entering isRedirected: statusCode="+statusCode+";"+ "method="+method+";"+"locationHeader="+locationHeader);
            switch (statusCode) {
            case HttpStatus.SC_MOVED_TEMPORARILY:
                /*
                 * If the original request used a non-secure URL (i.e. http instead of https), and the new Location does use a secure URL, we do not allow the
                 * redirect. This allows executeMethod(), below, to see the redirect and throw InsecureProtocolException, which is expected by callers when the
                 * server is configured to require secure connections. In 5.0.2 and earlier, redirects were followed manually, and this same logic was applied
                 * to each 302 response. Note that in 5.0.2 we did not check if the redirect target URI was secure. This is different now because we may have
                 * redirections for other reasons and if a server is set up in non-secure mode, this would cause problems with the other authentication
                 * protocols
                 */
                HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
                if (targetHost != null && SCHEME_HTTP.equalsIgnoreCase(targetHost.getSchemeName())) {
                    if (locationHeader != null) {
                        URI redirectTarget = URI.create(locationHeader.getValue());
                        if (SCHEME_HTTPS.equalsIgnoreCase(redirectTarget.getScheme())) {
                            return false;
                        }
                    }
                }

                return isRedirectable(method) && locationHeader != null;
            case HttpStatus.SC_TEMPORARY_REDIRECT:
                return isRedirectable(method);
            case HttpStatus.SC_SEE_OTHER:
                return true;
            default:
                return false;
            }
        }

        /**
         * This method is overridden so that any service version header can be stripped from the request when following redirects. It doesn't make sense to send
         * the version for the originally requested server to redirect targets, because they will not be the same service, in general.
         */
        @Override
        public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
            LOGGER.finer("Entering getRedirect: locationHeader="+ response.getFirstHeader("location")); //$NON-NLS-1$ //$NON-NLS-2$
            /*
             * The base DefaultRedirectStrategy class will generate the redirect request without any headers. Then, by default, the redirect executor class will
             * copy all the headers from the original request to the redirect request, but only if the redirect request doesn't have any headers. So we do the
             * copy ourselves, without the service version header.
             *
             * The request should be wrapped, so we want the original headers, without the request-specific headers that would be added by protocol
             * interceptors.
             */
            Header[] originalHeaders;
            if (request instanceof HttpRequestWrapper) {
                originalHeaders = ((HttpRequestWrapper) request).getOriginal().getAllHeaders();
            } else {
                originalHeaders = request.getAllHeaders();
            }

            HttpUriRequest redirectRequest = super.getRedirect(request, response, context);
            redirectRequest.setHeaders(originalHeaders);

            Header serviceVersionHeader = redirectRequest.getFirstHeader(X_SERVICE_VERSION);
            if (serviceVersionHeader != null) {
                redirectRequest.removeHeader(serviceVersionHeader);
            }

            /*
             * If this redirect is happening as a result of retrying authentication with an OIDC identity provider,
             * and the authentication has succeeded,
             * then clear any cached credentials so that they are not sent to the target host.
             * Otherwise, if both the identity provider (auth server) and target are on the same host, then
             * doing both OIDC authentication and Basic authentication with the target is an error.
             */
            Object retryingAuthServerChallenge = context.getAttribute(RETRYING_AUTH_SERVER_CHALLENGE);
            Object authState = context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);
            LOGGER.finer("retryingAuthServerChallenge: "+retryingAuthServerChallenge+", authState: "+authState); //$NON-NLS-1$
            if (retryingAuthServerChallenge != null && ((Boolean) retryingAuthServerChallenge).booleanValue()) {
                AuthProtocolState state = authState == null ? AuthProtocolState.UNCHALLENGED : ((AuthState) authState).getState();
                if (state == AuthProtocolState.UNCHALLENGED || state == AuthProtocolState.SUCCESS) {
                	LOGGER.finer("OIDC clearing credentials before redirecting to "+redirectRequest.getURI()); //$NON-NLS-1$
                    clearCredentialsInContext(context);
                }
            }
            
            return redirectRequest;
        }
    }
    
    /**
     * Clear any credentials that have been cached in the HTTP execution context.
     */
    private static void clearCredentialsInContext(HttpContext context) {
        context.removeAttribute(HttpClientContext.AUTH_CACHE);
        context.removeAttribute(HttpClientContext.TARGET_AUTH_STATE);
        context.removeAttribute(HttpClientContext.CREDS_PROVIDER);
    }
    
	public static class GetResult {
		private HttpClientContext httpContext;
		private JSON json;
		
		public GetResult(HttpClientContext httpContext, JSON json) {
			this.httpContext = httpContext;
			this.json = json;
		}

		/**
		 * @return The context used in the request. May be used for subsequent
		 * rest requests relating to the same user
		 */
		public HttpClientContext getHttpContext() {
			return httpContext;
		}

		/**
		 * @return JSON response from the get request
		 */
		public JSON getJson() {
			return json;
		}
	}

	/**
	 * Perform GET request against an RTC server
	 * 
	 * @param serverURI The RTC server
	 * @param uri The relative URI for the GET. It is expected it is already encoded if necessary.
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param httpContext The context from the login if cycle is being managed by the caller
	 * Otherwise <code>null</code> and this call will handle the login.
	 * @param listener The listener to report errors to. May be 
	 * <code>null</code>
	 * @return Result of the GET (JSON response)
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException 
	 */
	public static GetResult performGet(String serverURI, String uri, String userId,
			String password, int timeout, HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException, GeneralSecurityException {
		return performGetWithItemNotFound(serverURI, uri, userId, password, timeout, false, httpContext, listener);
	}
	

	/**
	 * Perform GET request against an RTC server. This method captures 404 separately and 
	 * throws an {@link ItemNotFoundException} while {@link #performGet} handles 404 
	 * like any other HTTP error code.
	 * 
	 * @param serverURI The RTC server
	 * @param uri The relative URI for the GET. It is expected it is already encoded if necessary.
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param catpure404 In this 
	 * @param httpContext The context from the login if cycle is being managed by the caller
	 * Otherwise <code>null</code> and this call will handle the login.
	 * @param listener The listener to report errors to. May be 
	 * <code>null</code>
	 * @return Result of the GET (JSON response)
	 * @throws IOException Thrown if things go wrong
	 * @throws ItemNotFoundException - Thrown if the item is not found
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException 
	 */
	public static GetResult performGetWithItemNotFound(String serverURI, String uri, String userId,
			String password, int timeout, boolean capture404, HttpClientContext httpContext, TaskListener listener)
			throws IOException, ItemNotFoundException, InvalidCredentialsException, GeneralSecurityException {
		CloseableHttpClient httpClient = getClient();
		String fullURI = getFullURI(serverURI, uri);
		HttpGet request = getGET(fullURI, timeout);
		if (httpContext == null) {
			httpContext = createHttpContext();
		}
		LOGGER.finer("GET: " + request.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(request, httpContext);
		try {
			// based on the response do any authentication. If authentication requires
			// the request to be performed again (i.e. Basic auth) re-issue request
			response = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);
			if (response == null) {
				// retry get
				request = getGET(fullURI, timeout);
				response = httpClient.execute(request, httpContext);
			}
			int statusCode = response.getStatusLine().getStatusCode();
			
			if (statusCode == 200) {
				InputStreamReader inputStream = new InputStreamReader(response.getEntity().getContent(), UTF_8);
				try {
					String responseContent = IOUtils.toString(inputStream);
					GetResult result = new GetResult(httpContext, JSONSerializer.toJSON(responseContent));
					return result;
				} finally {
					try {
						inputStream.close();
					} catch (IOException e) {
						LOGGER.finer("Failed to close the result input stream for request: " + uri); //$NON-NLS-1$
					}
				}
			} else if (statusCode == 401) {
				// if still un-authorized, then there is a good chance the basic credentials are bad.
				throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else if (statusCode == 404) {
				if (capture404) {
					throw new ItemNotFoundException(Messages.HttpUtils_item_not_found(fullURI));
				} else {
					// capture details about the error, reverting to old behavior
					LOGGER.finer(Messages.HttpUtils_GET_failed(fullURI, statusCode));
					if (listener != null) {
						listener.fatalError(Messages.HttpUtils_GET_failed(fullURI, statusCode));
					}
					throw logError(fullURI, response, Messages.HttpUtils_GET_failed(fullURI, statusCode));
				}
			} else {
					// capture details about the error
					LOGGER.finer(Messages.HttpUtils_GET_failed(fullURI, statusCode));
					if (listener != null) {
						listener.fatalError(Messages.HttpUtils_GET_failed(fullURI, statusCode));
					}
					throw logError(fullURI, response, Messages.HttpUtils_GET_failed(fullURI, statusCode));
			}
		} finally {
			closeResponse(response);
		}
	}

	/**
	 * Perform GET request against an RTC server.
	 * Extracts a string from the response stream and returns it.
	 * 
	 * @param serverURI The RTC server
	 * @param uri The relative URI for the GET. It is expected it is already encoded if necessary.
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param httpContext The context from the login if cycle is being managed by the caller
	 * Otherwise <code>null</code> and this call will handle the login.
	 * @param listener The listener to report errors to. May be 
	 * <code>null</code>
	 * @return a tuple of context and a string
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException 
	 */
	public static Tuple<HttpClientContext, String> performRawGet(String serverURI, String uri, String userId,
			String password, int timeout, HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException, GeneralSecurityException {

		CloseableHttpClient httpClient = getClient();
		String fullURI = getFullURI(serverURI, uri);
		HttpGet request = getGET(fullURI, timeout);
		if (httpContext == null) {
			httpContext = createHttpContext();
		}

		LOGGER.finer("GET: " + request.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(request, httpContext);
		try {
			// based on the response do any authentication. If authentication requires
			// the request to be performed again (i.e. Basic auth) re-issue request
			response = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);
			if (response == null) {
				// retry get
				request = getGET(fullURI, timeout);
				response = httpClient.execute(request, httpContext);
			}
			int statusCode = response.getStatusLine().getStatusCode();
			
			if (statusCode == 200) {
				InputStreamReader inputStream = new InputStreamReader(response.getEntity().getContent(), UTF_8);
				try {
					return new Tuple<HttpClientContext, String>(httpContext, IOUtils.toString(inputStream));
				} finally {
					try {
						inputStream.close();
					} catch (IOException e) {
						LOGGER.finer("Failed to close the result input stream for request: " + fullURI); //$NON-NLS-1$
					}
				}
			} else if (statusCode == 401) {
				// if still un-authorized, then there is a good chance the basic credentials are bad.
				throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else {
				// capture details about the error
				LOGGER.finer(Messages.HttpUtils_GET_failed(fullURI, statusCode));
				if (listener != null) {
					listener.fatalError(Messages.HttpUtils_GET_failed(fullURI, statusCode));
				}
				throw logError(fullURI, response, Messages.HttpUtils_GET_failed(fullURI, statusCode));
			}
		} finally {
			closeResponse(response);
		}
	}
					
	/**
	 * Perform PUT request against an RTC server
	 * @param serverURI The RTC server
	 * @param uri The relative URI for the PUT. It is expected that it is already encoded if necessary.
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param json The JSON object to put to the RTC server
	 * @param httpContext The context from the login if cycle is being managed by the caller
	 * Otherwise <code>null</code> and this call will handle the login.
	 * @param listener The listener to report errors to.
	 * May be <code>null</code> if there is no listener.
	 * @return The HttpContext for the request. May be reused in subsequent requests
	 * for the same user
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException 
	 */
	public static HttpClientContext performPut(String serverURI, String uri, String userId,
			String password, int timeout, final JSONObject json, HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException, GeneralSecurityException {

		CloseableHttpClient httpClient = getClient();
		// How to fill the request body (Clone doesn't work)
		ContentProducer cp = new ContentProducer() {
			public void writeTo(OutputStream outstream) throws IOException {
				Writer writer = new OutputStreamWriter(outstream, UTF_8);
				json.write(writer);
				writer.flush();
			}
		};
		HttpEntity entity = new EntityTemplate(cp);
		String fullURI = getFullURI(serverURI, uri);
		HttpPut put = getPUT(fullURI, timeout);
		put.setEntity(entity);
		
		if (httpContext == null) {
			httpContext = createHttpContext();
		}
		
		LOGGER.finer("PUT: " + put.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(put, httpContext);
		try {
			/**
			 * TODO this reauth flow does not work
			 * So mostly we end up doing a get/reauth and then put
			 */

			// based on the response do any authentication. If authentication requires
			// the request to be performed again (i.e. Basic auth) re-issue request
			response = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);

			// retry put request if we have to do authentication
			if (response == null) {
				put = getPUT(fullURI, timeout);
				put.setEntity(entity);
				response = httpClient.execute(put, httpContext);
			}

			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 401) {
				// It is an unusual case to get here (in our current work flow) because it means
				// the user has become unauthenticated since the previous request 
				// (i.e. the get of the value about to put back)
				throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else {
				int responseClass = statusCode / 100;
				if (responseClass != 2) {
					if (listener != null) {
						listener.fatalError(Messages.HttpUtils_PUT_failed(fullURI, statusCode));
					}
	
					throw logError(fullURI, response, Messages.HttpUtils_PUT_failed(fullURI, statusCode));
				}
				return httpContext;
			}
		} finally {
			closeResponse(response);
		}
	}
	
	/**
	 * Perform Post request against an RTC server
	 * NOTE: Make sure to perform a GET on a resource to complete the authentication sequence 
	 *       before calling this method.
	 *       
	 * @param serverURI The RTC server
	 * @param uri The relative URI for the PUT. It is expected that it is already encoded if necessary.
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param json The JSON object to put to the RTC server
	 * @param httpContext The context from the login cycle. You can perform a GET on a protected resource
	 *        Do not pass <code>null</code> context.
	 * @param listener The listener to report errors to.
	 * May be <code>null</code> if there is no listener.
	 * @return The HttpContext for the request. May be reused in subsequent requests
	 * for the same user
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException 
	 */
	public static HttpClientContext performPost(String serverURI, String uri, String userId,
			String password, int timeout, final JSONObject json, HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException, GeneralSecurityException {

		CloseableHttpClient httpClient = getClient();
		// Fill the request body
		ContentProducer cp = new ContentProducer() {
			public void writeTo(OutputStream outstream) throws IOException {
				Writer writer = new OutputStreamWriter(outstream, UTF_8);
				json.write(writer);
				writer.flush();
			}
		};
		HttpEntity entity = new EntityTemplate(cp);
		String fullURI = getFullURI(serverURI, uri);
		HttpPost postRequest = getPost(fullURI, timeout);
		postRequest.setEntity(entity);
		
		// A <code>null</code> httpContext will most likely not work due to 
		// issues with redirect strategy and authentication. 
		// It is recommended to add a == null check on httpContext and throw 
		// a invalid argument exception. See the TODO below.
		if (httpContext == null) {
			httpContext = createHttpContext();
		}
	
		LOGGER.finer("POST: " + postRequest.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(postRequest, httpContext);
		try {
			/**
			 * TODO this reauth flow does not work inside POST
			 * We end up doing a get/reauth first outside this method 
			 * and then call  POST. We could mandate a non null httpContext
			 * as an argument to this method.
			 */
			
			// Based on the response do any authentication. If authentication requires
			// the request to be performed again (i.e. Basic auth) re-issue request
			response = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);
			
			// retry put request if we have to do authentication
			if (response == null) {
				postRequest = getPost(fullURI, timeout);
				postRequest.setEntity(entity);
				response = httpClient.execute(postRequest, httpContext);
			}

			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 401) {
				// It is an unusual case to get here (in our current work flow) because it means
				// the user has become unauthenticated since the previous request 
				// (i.e. the get of the value about to put back)
				throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else {
				int responseClass = statusCode / 100;
				if (responseClass != 2) {
					LOGGER.warning(Messages.HttpUtils_POST_failed(fullURI, statusCode));
					if (listener != null) {
						listener.fatalError(Messages.HttpUtils_POST_failed(fullURI, statusCode));
					}
	
					throw logError(fullURI, response, Messages.HttpUtils_POST_failed(fullURI, statusCode));
				}
				return httpContext;
			}
		} finally {
			closeResponse(response);
		}
	}

	/**
	 * Perform DELETE request against an RTC server.
	 * NOTE: Make sure to perform a GET on a resource to complete the authentication sequence 
	 *       before calling this method.
	 * 
	 * @param serverURI The RTC server
	 * @param uri The relative URI for the DELETE. It is expected that it is already encoded if necessary.
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param httpContext The context from the login cycle. You can perform a GET on a protected resource 
	 *        Do not pass <code>null</code> context.
	 * @param listener The listener to report errors to.
	 * May be <code>null</code> if there is no listener.
	 * @return The HttpContext for the request. May be reused in subsequent requests
	 * for the same user
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException 
	 */
	public static HttpClientContext performDelete(String serverURI, String uri, String userId,
			String password, int timeout, HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException,
			GeneralSecurityException {
		LOGGER.finest("performDelete: onEnter"); //$NON-NLS-1$

		CloseableHttpClient httpClient = getClient();
		String fullURI = getFullURI(serverURI, uri);
		
		// A <code>null</code> httpContext will most likely not work due to 
		// issues with redirect strategy (httpClient 4.5.6) and authentication. 
		// It is recommended to add a == null check on httpContext and throw 
		// a invalid argument exception. A similar approach should be applied to  
		// performPOST method.
		HttpDelete delete = getDELETE(fullURI, timeout);
		if (httpContext == null) {
			httpContext = createHttpContext();
		}
		
		LOGGER.finer("performDelete: " + delete.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(delete, httpContext);
		try {
			int statusCode = response.getStatusLine().getStatusCode();
			Header locationHeader = response.getFirstHeader(LOCATION);
			boolean redirectsFollowed = false;
			int paranoia = 100;
			while (statusCode == 302 && locationHeader != null && paranoia > 0) {
				redirectsFollowed = true;
				// follow the redirects. Eventually we will get to a point where we can authenticate
				closeResponse(response);
				String redirectURI = locationHeader.getValue();
				HttpGet request = getGET(redirectURI, timeout);
				LOGGER.finer("performDelete: Following redirect before auth: " + request.getURI()); //$NON-NLS-1$
				response = httpClient.execute(request, httpContext);
				statusCode = response.getStatusLine().getStatusCode();
				locationHeader = response.getFirstHeader(LOCATION);
				paranoia--;
			}

			// based on the response do any authentication. If authentication requires
			// the request to be performed again (i.e. Basic auth) re-issue request
			LOGGER.finer("performDelete: Authenticating if required"); //$NON-NLS-1$
			response = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);

			if (response != null) {
				checkDeleteResponse(response, fullURI, serverURI, userId, listener);
			}
			
			// retry delete request if we have to do authentication or we followed a redirect to a Get
			if (redirectsFollowed || response == null) {
				// Do the actual delete
				paranoia = 100;
				do {
					// follow the redirects. Eventually we will get to a point where we can authenticate
					closeResponse(response);
					HttpDelete request = getDELETE(fullURI, timeout);
					LOGGER.finer("performDelete: Following redirect after auth: " + request.getURI()); //$NON-NLS-1$
					response = httpClient.execute(request, httpContext);
					statusCode = response.getStatusLine().getStatusCode();
					locationHeader = response.getFirstHeader(LOCATION);
					if (locationHeader != null) {
						fullURI = locationHeader.getValue();
					}
					paranoia--;
				} while (statusCode == 302 && locationHeader != null && paranoia > 0);
				checkDeleteResponse(response, fullURI, serverURI, userId, listener);
			}

			return httpContext;
		} finally {
			closeResponse(response);
			LOGGER.finest("performDelete: end"); //$NON-NLS-1$
		}
	}

	/**
	 * Check the response for a delete (or it could be a get if we were following
	 * redirects & posted the form). Idea is to see if its an auth failure or
	 * something really serious (not found isn't serious, just means already deleted).
	 * @param response The response
	 * @param fullURI The full uri that was used for the request.
	 * @param serverURI The RTC Server portion of the uri
	 * @param userId The user id on behalf of whom the request was made
	 * @param listener A listener to notify of issues.
	 * @throws InvalidCredentialsException Thrown if the authentication failed.
	 * @throws IOException Thrown if a serious error occurred.
	 */
	private static void checkDeleteResponse(CloseableHttpResponse response,
			String fullURI, String serverURI, String userId,
			TaskListener listener) throws InvalidCredentialsException,
			IOException {
		int statusCode;
		statusCode = response.getStatusLine().getStatusCode();
		if (statusCode == 401) {
			// the user is unauthenticated
			throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

		} else if (statusCode == 404) {
			// this is ok, build result already deleted

		} else {
			int responseClass = statusCode / 100;
			if (responseClass != 2) {
				if (listener != null) {
					listener.fatalError(Messages.HttpUtils_DELETE_failed(fullURI, statusCode));
				}

				throw logError(fullURI, response, Messages.HttpUtils_DELETE_failed(fullURI, statusCode));
			}
		}
	}

	/**
	 * Only used to test connection.
	 * @param serverURI The RTC server
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @return The HttpContext to be used in a series of requests (so we only need to
	 * login once).
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException if authentication fails
	 * @throws GeneralSecurityException 
	 */
	public static void validateCredentials(String serverURI, String userId,
			String password, int timeout) throws IOException, GeneralSecurityException, InvalidCredentialsException {
		// We can't directly do a post because we need a JSession id cookie.
		// Instead attempt to do a get and then verify the credentials when we need to do
		// the form based auth. Don't bother to re-issue the get though. We just want
		// to know if the Login works
	
		CloseableHttpClient httpClient = getClient();
		HttpGet request = getGET(serverURI, timeout);
		HttpClientContext httpContext = createHttpContext();
		
		LOGGER.finer("GET: " + request.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(request, httpContext);
		try {
			response = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, null);
			if (response == null) {
				// retry get - if doing form based auth, not required
				// but if its basic auth then we do need to re-issue since basic just updates the context
				request = getGET(serverURI, timeout);
				response = httpClient.execute(request, httpContext);
				if (response.getStatusLine().getStatusCode() == 401) {
					// still not authorized
					throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));
				}
			}
			
		} finally {
			closeResponse(response);
		}
	}

	/**
	 * Log the error that occurred and provide an exception that encapsulates the failure as best as
	 * possible. This means parsing the output and if its from RTC extract the stack trace from 
	 * there.
	 * @param fullURI The URI requested
	 * @param httpResponse The response from the request
	 * @param message A message for the failure if nothing can be detected from the response
	 * @return An exception representing the failure
	 */
	@SuppressWarnings("rawtypes")
	private static IOException logError(String fullURI, CloseableHttpResponse httpResponse, String message) {
		printMessageHeaders(httpResponse);

		IOException error = new IOException(message);
		try {
			InputStreamReader inputStream = new InputStreamReader(httpResponse.getEntity().getContent(), UTF_8);
			try {
				String response = IOUtils.toString(inputStream);
				// this is one lonnnng string if its a stack trace.
				// try to get it as JSON so we can output it in a more friendly way.
				try {
					JSON json = JSONSerializer.toJSON(response);
					response = json.toString(4);
					if (json instanceof JSONObject) {
						// see if we have a stack trace
						JSONObject jsonObject = (JSONObject) json;
						String errorMessage = jsonObject.getString("errorMessage"); //$NON-NLS-1$
						error = new IOException(errorMessage);
						JSONArray trace = jsonObject.getJSONArray("errorTraceMarshall"); //$NON-NLS-1$
						List<StackTraceElement> stackElements = new ArrayList<StackTraceElement>(trace.size());
						for (Iterator iterator = trace.iterator(); iterator.hasNext(); ) {
							Object element = iterator.next();
							if (element instanceof JSONObject) {
								JSONObject jsonElement = (JSONObject) element;
								String cls = jsonElement.getString("errorTraceClassName"); //$NON-NLS-1$
								String method = jsonElement.getString("errorTraceMethodName");  //$NON-NLS-1$
								String file = jsonElement.getString("errorTraceFileName"); //$NON-NLS-1$
								int line = jsonElement.getInt("errorTraceLineNumber"); //$NON-NLS-1$
								StackTraceElement stackElement = new StackTraceElement(cls, method, file, line);
								stackElements.add(stackElement);
							}
						}
						error.setStackTrace(stackElements.toArray(new StackTraceElement[stackElements.size()]));
						
						// our RTC responses have the stack trace in there twice. Remove 1 copy of it.
						jsonObject.remove("errorTraceMarshall"); //$NON-NLS-1$
						response = jsonObject.toString(4);
					}
				} catch (JSONException e) {
					// not JSON or not a RTC stack trace in the JSONObject so just log what we have
				}
				LOGGER.finer(response);
			} finally {
				try {
					inputStream.close();
				} catch (IOException e) {
					LOGGER.finer("Failed to close the result input stream for request: " + fullURI); //$NON-NLS-1$
				}
			}
		} catch (IOException e) {
			LOGGER.finer("Unable to capture details of the failure"); //$NON-NLS-1$
		}
		return error;
	}

	/**
	 * Because the creation of the SSLConnectionSocketFactory is expensive (reads from disk
	 * the certs file) we will cache the client. The client is re-used so doesn't have any
	 * target RTC server info associated with it.
	 * @return an HttpClient
	 * @throws GeneralSecurityException
	 */
	private synchronized static CloseableHttpClient getClient() throws GeneralSecurityException {
		if (HTTP_CLIENT == null) {
			HttpClientBuilder clientBuilder = HttpClientBuilder.create();

			clientBuilder.setSSLSocketFactory(getSSLConnectionSocketFactory());

			// TODO to find out if this is sufficient, we can periodically dump
			// the PoolStats
			clientBuilder.setMaxConnPerRoute(10);
			clientBuilder.setMaxConnTotal(100);

			RegistryBuilder<AuthSchemeProvider> authSchemeRegistryBuilder = RegistryBuilder.<AuthSchemeProvider> create();

	        // we are always at least supporting bearer
	        authSchemeRegistryBuilder.register(AuthSchemeBearer, new BearerSchemeProvider());
	        
	        switch (_authenticationType) {
	        case USERIDPASSWD:
	            // if we use user creds, we do not want to handle spnego, but deal with it in the main body, but we do want to support basic
	            authSchemeRegistryBuilder.register(AuthSchemes.BASIC, new BasicSchemeFactory(Charset.forName("UTF-8"))); //$NON-NLS-1$
	            break;
	        default:
	            // in all other cases, we don't want to deal with basic or spnego, notably clien creds
	            break;
	        }
	        
			
	        Lookup<AuthSchemeProvider> authSchemeRegistry = authSchemeRegistryBuilder.build();
	        clientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
			
			// allow redirects on POST
			clientBuilder.setRedirectStrategy(new AuthRedirectStrategy());

			// How long to wait to get a connection from our connection manager.
			// The default
			// timeouts are forever which is probably not good.
			// TODO If we set it when creating the GET, PUT & POST maybe its not
			// really needed here
			clientBuilder.setDefaultRequestConfig(getRequestConfig(CONNECTION_REQUEST_TIMEOUT_MILLIS).build());

			clientBuilder.setUserAgent(JAZZ_NATIVE_CLIENT);
			
			 /* We occasionally see NoHttpResponseException.  Evidently this can
		         * happen when the server shuts down a connection but the client has not
		         * noticed yet.  One suggested workaround is to retry. */
		        HttpRequestRetryHandler retryHandler = new HttpRequestRetryHandler() {
		            @Override
		            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
		                if (executionCount > HTTP_RETRY_REQUEST_COUNT) {
		                    LOGGER.finer("httpClient retry overflow"); //$NON-NLS-1$
		                    return false;
		                } else if (exception instanceof NoHttpResponseException) {
		                    LOGGER.finer("httpClient retry on NoHttpResponseException"); //$NON-NLS-1$
		                    return true;
		                } else
		                    return false;
		            }
		        };
		        clientBuilder.setRetryHandler(retryHandler);
			
			HTTP_CLIENT = clientBuilder.build();
		}

		return HTTP_CLIENT;
	}
	
	private static synchronized SSLConnectionSocketFactory getSSLConnectionSocketFactory() throws GeneralSecurityException {
		if (SSL_CONNECTION_SOCKET_FACTORY == null) { 

			/** Create a trust manager that does not validate certificate chains */
			TrustManager trustManager = new X509TrustManager() {
				public void checkClientTrusted(
						java.security.cert.X509Certificate[] certs, String authType) {
					/** Ignore Method Call */
				}
	
				public void checkServerTrusted(
						java.security.cert.X509Certificate[] certs, String authType) {
					/** Ignore Method Call */
				}
	
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};
			SSLContext sslContext = SSLContextUtil.createSSLContext(trustManager);
			SSL_CONNECTION_SOCKET_FACTORY = new SSLConnectionSocketFactory(
		        sslContext, new X509HostnameVerifier() {
		            @Override
		            public void verify(String host, SSLSocket ssl)
		                    throws IOException {
		            }

		            @Override
		            public void verify(String host, X509Certificate cert)
		                    throws SSLException {
		            }

		            @Override
		            public void verify(String host, String[] cns,
		                    String[] subjectAlts) throws SSLException {
		            }

		            @Override
		            public boolean verify(String s, SSLSession sslSession) {
		                return true;
		            }
		        });
		}
		return SSL_CONNECTION_SOCKET_FACTORY;
	}
	
	/**
	 * Obtain a GET request to execute
	 * @param fullURI The full uri
	 * @param timeout The time out period in seconds
	 * @return a GET http request
	 */
	private static HttpGet getGET(String fullURI, int timeout) {
		HttpGet get = new HttpGet(fullURI);
		get.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		get.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		get.setConfig(getRequestConfig(timeout).build());
		return get;
	}

	/**
	 * Obtain a PUT request to execute
	 * @param fullURI The full uri
	 * @param timeout The time out period in seconds
	 * @return a PUT http request
	 */
	private static HttpPut getPUT(String fullURI, int timeout) {
		HttpPut put = new HttpPut(fullURI);
		put.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		put.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		put.addHeader("Content-type", TEXT_JSON); //$NON-NLS-1$
		put.setConfig(getRequestConfig(timeout).build());
		return put;
	}

	/**
	 * Obtain a POST request to execute
	 * @param fullURI The full uri
	 * @param timeout The time out period in seconds
	 * @return a POST http request
	 */
	private static HttpPost getPost(String fullURI, int timeout) {
		HttpPost post = new HttpPost(fullURI);
		post.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		post.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		post.addHeader("Content-type", TEXT_JSON); //$NON-NLS-1$
		post.setConfig(getRequestConfig(timeout).build());
		return post;
	}
	
	/**
	 * Obtain a DELETE request to execute
	 * @param fullURI The full uri
	 * @param timeout The time out period in seconds
	 * @return a DELETE http request
	 */
	private static HttpDelete getDELETE(String fullURI, int timeout) {
		HttpDelete delete = new HttpDelete(fullURI);
		delete.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		delete.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		delete.setConfig(getRequestConfig(timeout).build());
		return delete;
	}

	/**
	 * Obtain a POST request to execute
	 * @param fullURI The full uri
	 * @param timeout The time out period in seconds
	 * @return a POST http request
	 */
	private static HttpPost getPOST(String fullURI, int timeout) {
		HttpPost post = new HttpPost(fullURI);
		post.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		post.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		post.setConfig(getRequestConfig(timeout).build());
		return post;
	}

	/**
	 * Get the request configuration tailored to the timeout period for
	 * accessing the RTC server.
	 * @param timeout The timout period in seconds
	 * @return The request configuration
	 */
	private static RequestConfig.Builder getRequestConfig(int timeout) {
		return RequestConfig.custom()
                .setConnectionRequestTimeout(timeout * 1000)
                .setConnectTimeout(timeout * 1000) // changed form timeouts[1]
                .setCookieSpec(CookieSpecs.STANDARD)
                .setSocketTimeout(timeout * 1000)
                .setStaleConnectionCheckEnabled(true)
                .setAuthenticationEnabled(true)
                .setCircularRedirectsAllowed(true)
                .setTargetPreferredAuthSchemes(authSchemePreferences)
                .setRedirectsEnabled(true);
	}

	public static String getFullURI(String serverURI, String relativeURI) {
		String fullURI = serverURI;
		if (!serverURI.endsWith(SLASH) && !relativeURI.startsWith(SLASH)) {
			fullURI = serverURI + SLASH + relativeURI;
		} else {
			fullURI = serverURI + relativeURI;
		}
		return fullURI;
	}
	
	/**
	 * Creates and returns a new HttpContext with a new cookie store, for use with the singleton HTTP_CLIENT.
	 * 
	 * @return a new HttpContext
	 */
	private static HttpClientContext createHttpContext() {
		CookieStore cookieStore = new BasicCookieStore();
		HttpClientContext httpContext = new HttpClientContext();
		httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
		return httpContext;
	}

	/**
	 * Perform any authentication required (Form or Basic) if the previous request did not
	 * succeed.
	 * @param response The response from the previous request. It will be consumed if
	 * we are going to do authentication (i.e. not returned by this call).
	 * @param httpClient The httpClient to use for authentication
	 * @param httpContext The current context for use in request. Required.
	 * It may be updated with authentication info if necessary.
	 * @param serverURI The URI for the server
	 * @param userId The user that is performing the request
	 * @param password The user's password
	 * @param timeout The timeout for the password
	 * @param listener The listener should any messages need to be logged. May be
	 * <code>null</code>
	 * @return The response from the form based auth, or the original request if no auth is required
	 * <code>null</code> if the request needs to be repeated (now that the context has been updated).
	 * @throws InvalidCredentialsException Thrown if the user's userid or password are invalid.
	 * @throws IOException Thrown if anything else goes wrong.
	 * @throws GeneralSecurityException 
	 */
	private static CloseableHttpResponse authenticateIfRequired(
			CloseableHttpResponse response, CloseableHttpClient httpClient,
			HttpClientContext httpContext, String serverURI, String userId,
			String password, int timeout, TaskListener listener) throws InvalidCredentialsException, IOException, GeneralSecurityException {
	
		// decide what kind of Auth is required if any
		int statusCode = response.getStatusLine().getStatusCode();
		Header formHeader = response.getFirstHeader(FORM_AUTHREQUIRED_HEADER);
		Header basicHeader = response.getFirstHeader(BASIC_AUTHREQUIRED_HEADER);
		
		//collect all the WWW-Authenticate headers
				Header bearer[] = response.getHeaders(BASIC_AUTHREQUIRED_HEADER);
				
				//store the headers value in string format
				String bearerHeader = "";
				for(Header b:bearer)
					bearerHeader += b.getValue();
				
		if (formHeader!=null && FORM_AUTHREQUIRED_HEADER_VALUE.equals(formHeader.getValue())) {
			closeResponse(response);
			
			// login using Form based auth
			return handleFormBasedChallenge(httpClient, httpContext, serverURI, userId, password, timeout, listener);
		}else if(statusCode == 401 && bearerHeader.toLowerCase().matches("(.*)bearer(.*)")){
			return handleBearerChallenge(response, httpClient, httpContext, serverURI, userId, password);
		}else if (statusCode == 401 && basicHeader != null) {
	        if (JAUTH_PATTERN.matcher(basicHeader.getValue()).matches()) {
	        	throw new UnsupportedOperationException();
	
	        } else if (BASIC_PATTERN.matcher(basicHeader.getValue()).matches()) {
				closeResponse(response);
				
				// setup the context to use Basic auth
				handleBasicAuthChallenge(httpContext, serverURI, userId, password, listener);
				return null;
	        }
		}
		return response;
	}

	
	private static CloseableHttpResponse handleBearerChallenge(CloseableHttpResponse response, CloseableHttpClient httpClient,
			HttpClientContext httpContext, String serverURI, String userId, String password) throws IOException, GeneralSecurityException, InvalidCredentialsException {
		
		Header opAuthorizationRequestHeader = response.getFirstHeader("X-JSA-AUTHORIZATION-REDIRECT");
		
		CloseableHttpResponse httpResponse = null;

		// if the header were to be null because target application is not developed with the OIDC SDK, we return and let the 401 reach the client
        if (opAuthorizationRequestHeader == null) {
           LOGGER.info("Bearer challenge received but target not developed with OIDC SDK"); //$NON-NLS-1$
           int statusCode = response.getStatusLine().getStatusCode();
           throw new IOException(Messages.HttpUtils_LOGIN_failed(userId, serverURI, statusCode));
        }
        try {
        	// if server supports application passwords, get the redirect URL from the response
            Header appPasswordRequestHeader = response.getFirstHeader("X-JSA-APP-PASSWORD-REDIRECT");
            
			URIBuilder authorizationRequestUriBuilder = new URIBuilder(opAuthorizationRequestHeader.getValue());
			closeResponse(response);
			
			 URI authorizationRequestUri = authorizationRequestUriBuilder.build();
	         // auth code flow uri with prompt=none
	         URIBuilder authorizationRequestUriBuilderWithNoPrompt = authorizationRequestUriBuilder.addParameter("prompt", "none"); //$NON-NLS-1$ //$NON-NLS-2$
	         // get the OP host
	         HttpHost opHost = new HttpHost(authorizationRequestUriBuilder.getHost(), authorizationRequestUriBuilder.getPort(),
	                    authorizationRequestUriBuilder.getScheme());

	         LOGGER.finer("OIDC OP Host: "+opHost);
	         HttpGet noPromptAuthorizationRequest = new HttpGet(authorizationRequestUriBuilderWithNoPrompt.build());
	         String newUserAgent = JAZZ_NATIVE_CLIENT;
	         noPromptAuthorizationRequest.setHeader(HTTP.USER_AGENT, newUserAgent);
	         noPromptAuthorizationRequest.setHeader("Accept", TEXT_JSON);
	         noPromptAuthorizationRequest.setHeader("Accept-Charset", UTF_8);
	         
	         // This request will fail if this client is connecting for the first time. It may succeed
	         // if an OP session is already in place in which case some previous level-setting had already been done
	          CloseableHttpResponse closeableResponse = executePrimitiveRequest(noPromptAuthorizationRequest, opHost, httpContext);
	          int status = closeableResponse.getStatusLine().getStatusCode();
	          
	          if (status == 200) {
	        	  LOGGER.finer("OIDC Authorization request with prompt is none returned 200"); //$NON-NLS-1$
	                
	                // check if we have to deal with challenge
	                Header login_required_header = closeableResponse.getFirstHeader("X-JSA-LOGIN-REQUIRED");
	                if (login_required_header != null) {
	                	LOGGER.finer("OIDC login required header returned"); //$NON-NLS-1$
	                    // close the response
	                    HttpClientUtils.closeQuietly(closeableResponse);

	                    // (1) this means we haven't yet signed into the authorization server, so deal with this challenge
	                    HttpGet authorizationRequest = new HttpGet(authorizationRequestUri);
	                    authorizationRequest.setHeader("Accept", TEXT_JSON);
	                    authorizationRequest.setHeader("Accept-Charset", UTF_8);
	                    httpResponse = handleAuthorizationServerChallenge(opHost, authorizationRequest, httpContext, userId, password, serverURI);
					} else if (appPasswordRequestHeader != null) {
	                    // application passwords are supported and either application password usage is forced on by a system property,
	                    // or the response is a recognized delegated authentication challenge;
	                    // assume we have an application password and try to authenticate with it
						HttpClientUtils.closeQuietly(closeableResponse);
						
						LOGGER.finer("Using password as an application password for a delegated authentication challenge"); //$NON-NLS-1$
						httpResponse = handleAppPasswordChallenge(httpContext, appPasswordRequestHeader.getValue(), serverURI
	                    		, userId, password);
	                }
	            }else {
	            	LOGGER.finer("OIDC Authorization request with prompt is none returned with status "+ closeableResponse.getStatusLine().getStatusCode());
	            }
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
        return httpResponse;
        
	}
	
	
	private static CloseableHttpResponse handleAppPasswordChallenge(HttpClientContext httpContext, String appPasswordRequestUri, String serverURI, String userId, String password) throws IOException, URISyntaxException, GeneralSecurityException, InvalidCredentialsException {

        URIBuilder appAuthorizationRequestUriBuilder = new URIBuilder(appPasswordRequestUri);
		URI appAuthorizationRequestUri = appAuthorizationRequestUriBuilder.build();
        HttpHost _targetHost = new HttpHost(appAuthorizationRequestUriBuilder.getHost(), appAuthorizationRequestUriBuilder.getPort(), 
        		appAuthorizationRequestUriBuilder.getScheme());
        URL url = new URL(serverURI);
        // setup Basic credentials for the request, assuming the user's password is really an application password
        AuthCache authCache = new BasicAuthCache();
        authCache.put(_targetHost, new BasicScheme(Charset.forName("UTF-8"))); //$NON-NLS-1$
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
	    credsProvider.setCredentials(
	            new AuthScope(_targetHost.getHostName(), _targetHost.getPort(), _targetHost.getSchemeName()),
	            new UsernamePasswordCredentials(userId, password));
        httpContext.setAuthCache(authCache);
        httpContext.setCredentialsProvider(credsProvider);
        httpContext.setAttribute(HttpClientContext.TARGET_AUTH_STATE, new AuthState());

        HttpGet appPasswordRequest = new HttpGet(appAuthorizationRequestUri);
        appPasswordRequest.setHeader("Accept", TEXT_JSON);
        appPasswordRequest.setHeader("Accept-Charset", UTF_8);
        // flag that an auth challenge is being retried so that Basic credentials are cleared when following redirects
        httpContext.setAttribute(RETRYING_AUTH_SERVER_CHALLENGE, Boolean.TRUE);
        CloseableHttpResponse appPasswordResponse;
        try {
            appPasswordResponse = executePrimitiveRequest(appPasswordRequest, _targetHost, httpContext);
        } finally {
            httpContext.removeAttribute(RETRYING_AUTH_SERVER_CHALLENGE);
        }
        if (appPasswordResponse.getStatusLine().getStatusCode() == 200) {
        	LOGGER.finer("Application password auth sequence reached protected resource"); //$NON-NLS-1$
        } else if(appPasswordResponse.getStatusLine().getStatusCode() == 401) {
        	LOGGER.finer("Application password auth request returned " + appPasswordResponse.getStatusLine().getStatusCode()); //$NON-NLS-1$
        	throw new InvalidCredentialsException(
						Messages.HttpUtils_authentication_failed(userId, serverURI));
        }else {
        	LOGGER.finer("Application password auth request returned " + appPasswordResponse.getStatusLine().getStatusCode());
        }
        	
        return appPasswordResponse;	
    }
 
 private static CloseableHttpResponse handleAuthorizationServerChallenge(HttpHost opHost, HttpGet authorizationRequest,
		 HttpClientContext httpContext, String userId, String password, String serverURI) throws IOException, GeneralSecurityException, InvalidCredentialsException {
        // we set the idpHost to be the OP for now so we can react to challenges from it
       HttpHost _idpHost = opHost;

       LOGGER.finer("OIDC resending authorization request without prompt = none"); //$NON-NLS-1$
        CloseableHttpResponse closeableResponse = executePrimitiveRequest(authorizationRequest, opHost, httpContext);
     // so if we see a 200 at this stage, we are dealing with a FORM produced by the OP or some other IDP
        // in which case we will simply set http client to send Basic Creds to the last request preemptively
        if (closeableResponse.getStatusLine().getStatusCode() == 200) {
        	LOGGER.finer("OIDC resending authorization request with Basic credentials cached"); //$NON-NLS-1$
             
             HttpClientUtils.closeQuietly(closeableResponse);

             // fetch the last request from the context
             _idpHost = httpContext.getTargetHost();
             // on top of that, for the duration of this context, we will send the basic credentials to any request towards the IdP
             AuthCache authCache = new BasicAuthCache();
             authCache.put(_idpHost, new BasicScheme(Charset.forName("UTF-8"))); //$NON-NLS-1$
             CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
             AuthScope idpScope = new AuthScope(_idpHost.getHostName(), AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthScope.ANY_SCHEME);
             credentialsProvider.setCredentials(idpScope, new UsernamePasswordCredentials(userId, password));
             httpContext.setAuthCache(authCache);
             httpContext.setCredentialsProvider(credentialsProvider);
             httpContext.setAttribute(HttpClientContext.TARGET_AUTH_STATE, new AuthState());
             
             // Set a flag indicating that auth server authentication is being retried.
             // This is used by the getRedirect method to clear the cached credentials after auth server authentication succeeds,
             // to avoid sending credentials to the target server when it has the same hostname as the auth server.
             httpContext.setAttribute(RETRYING_AUTH_SERVER_CHALLENGE, Boolean.TRUE);
             closeableResponse = executePrimitiveRequest(authorizationRequest, opHost, httpContext);
             httpContext.removeAttribute(RETRYING_AUTH_SERVER_CHALLENGE);
             
             // In case the redirect interceptor didn't clear the cached credentials, do it now
             LOGGER.finer("OIDC clearing credentials for host "+ _idpHost.getHostName()); //$NON-NLS-1$
             clearCredentialsInContext(httpContext);
             if(closeableResponse.getStatusLine().getStatusCode() == 401)
             {
            	 throw new InvalidCredentialsException(
 						Messages.HttpUtils_authentication_failed(userId, serverURI));
             }
             return closeableResponse;
        }
        return null;
 }

private static CloseableHttpResponse executePrimitiveRequest(HttpRequest request, HttpHost targetHost, HttpClientContext context) throws IOException, GeneralSecurityException {
	LOGGER.finer("Entering executePrimitiveRequest"); //$NON-NLS-1$
    try {
    	LOGGER.finer("PRIOR REQUEST EXECUTION CONTEXT "+context); //$NON-NLS-1$

        // replace user-agent with one which indicates it can handle spnego and application passwords
        Header userAgent = request.getFirstHeader(HTTP.USER_AGENT);
        if (userAgent != null) {
            String newUserAgent = userAgent.getValue();
            LOGGER.finer("Adjusted user agent to: " + newUserAgent); //$NON-NLS-1$
            request.setHeader(HTTP.USER_AGENT, newUserAgent);
       }
        CloseableHttpResponse response = getClient().execute(targetHost, request, context);
        LOGGER.finer("AFTER REQUEST EXECUTION CONTEXT " + context); //$NON-NLS-1$
        return response;
    }

    catch(IOException ex)
    {
    	throw ex;
    }
}
	
	/**
	 * Post a login form to the server when authentication was required by the previous request
	 * @param httpClient The httpClient to use for the requests
	 * @param httpContext httpContext with it's own cookie store for use with the singleton HTTP_CLIENT
	 * Not <code>null</code>
	 * @param serverURI The RTC server
	 * @param userId The userId to authenticate as
	 * @param password The password to authenticate with
	 * @param timeout The timeout period for the connection (in seconds)
	 * @param listener The listener to report errors to. May be 
	 * <code>null</code>
	 * @throws IOException Thrown if things go wrong
	 * @throws InvalidCredentialsException if authentication fails
	 */
	private static CloseableHttpResponse handleFormBasedChallenge(CloseableHttpClient httpClient, HttpClientContext httpContext, String serverURI,
			String userId, String password, int timeout, TaskListener listener)
			throws IOException,
			InvalidCredentialsException {

		// The server requires an authentication: Create the login form
		String fullURI = getFullURI(serverURI, "j_security_check");
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("j_username", userId)); //$NON-NLS-1$
		nvps.add(new BasicNameValuePair("j_password", password)); //$NON-NLS-1$

		HttpPost formPost = getPOST(fullURI, timeout); //$NON-NLS-1$
		formPost.setEntity(new UrlEncodedFormEntity(nvps, UTF_8));

		// The client submits the login form
		LOGGER.finer("POST: " + formPost.getURI()); //$NON-NLS-1$
		CloseableHttpResponse formResponse = httpClient.execute(formPost, httpContext);
		int statusCode = formResponse.getStatusLine().getStatusCode();
		Header header = formResponse.getFirstHeader(FORM_AUTHREQUIRED_HEADER);
		
		// check to see if the authentication was successful
		if (statusCode/100 == 2 && (header!=null) && (AUTHFAILED_HEADER_VALUE.equals(header.getValue()))) {
			closeResponse(formResponse);
			throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));
		} 
		return formResponse;
	}

	/**
	 * For Basic auth, configure the context to do pre-emptive auth with the
	 * credentials given.
	 * @param httpContext The context in use for the requests.
	 * @param serverURI The RTC server we will be authenticating against
	 * @param userId The userId to login with
	 * @param password The password for the User
	 * @param listener A listen to log messages to, Not required
	 * @throws IOException Thrown if anything goes wrong
	 */
	private static void handleBasicAuthChallenge(
			HttpClientContext httpContext, String serverURI, String userId,
			String password, TaskListener listener) throws IOException {
		
		URI uri;
		try {
			uri = new URI(serverURI);
		} catch (URISyntaxException e) {
			throw new IOException(Messages.HttpUtils_invalid_server(serverURI), e);
		} 
	    HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
	    CredentialsProvider credsProvider = new BasicCredentialsProvider();
	    credsProvider.setCredentials(
	            new AuthScope(target.getHostName(), target.getPort()),
	            new UsernamePasswordCredentials(userId, password));
	    httpContext.setAttribute(HttpClientContext.CREDS_PROVIDER, credsProvider);
	
	    // Create AuthCache instance
	    AuthCache authCache = new BasicAuthCache();
	    // Generate BASIC scheme object and add it to the local auth cache
	    BasicScheme basicAuth = new BasicScheme();
	    authCache.put(target, basicAuth);
	
	    // Add AuthCache to the execution context
	    httpContext.setAuthCache(authCache);
	}

	private static void closeResponse(CloseableHttpResponse formResponse) {
		if (formResponse != null) {
			try {
				formResponse.close();
			} catch (IOException e) {
				// we don't care we are logged in or are throwing an exception for a different problem
				LOGGER.log(Level.FINER, "Failed to close response", e);
			}
		}
	}

	/**
	 * Print the HTTP request - for debugging purposes
	 */
	@SuppressWarnings("unused")
	private static void printRequest(HttpRequestBase request) {
		if (LOGGER.isLoggable(Level.FINER)) {
			StringBuffer logMessage = new StringBuffer();
			logMessage.append(NEW_LINE).append("\t- Method: ").append(request.getMethod()); //$NON-NLS-1$ //
			logMessage.append(NEW_LINE).append("\t- URL: ").append(request.getURI()); //$NON-NLS-1$
			logMessage.append(NEW_LINE).append("\t- Headers: "); //$NON-NLS-1$
			Header[] headers = request.getAllHeaders();
			for (int i = 0; i < headers.length; i++) {
				logMessage.append(NEW_LINE).append("\t\t- ").append(headers[i].getName()).append(": ").append(headers[i].getValue()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			LOGGER.finer(logMessage.toString());
		}
	}

	/**
	 * Print out the HTTPMessage headers - for debugging purposes
	 */
	private static void printMessageHeaders(HttpMessage message) {
		if (LOGGER.isLoggable(Level.FINER)) {
			StringBuffer logMessage = new StringBuffer("Message Headers:"); //$NON-NLS-1$
			Header[] headers = message.getAllHeaders();
			for (int i = 0; i < headers.length; i++) {
				logMessage.append(NEW_LINE).append("\t- ").append(headers[i].getName()).append(": ").append(headers[i].getValue()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			LOGGER.finer(logMessage.toString());
		}
	}

	/**
	 * Print out the HTTP Response body - for debugging purposes
	 */
	@SuppressWarnings("unused")
	private static void printResponseBody(CloseableHttpResponse response) {
		if (LOGGER.isLoggable(Level.FINER)) {
			HttpEntity entity = response.getEntity();
			if (entity == null) return;
			
			StringBuffer logMessage = new StringBuffer("Response Body:");
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new InputStreamReader(entity.getContent()));
				String line = reader.readLine();
				while (line != null) {
					logMessage.append(NEW_LINE).append(line);
					line = reader.readLine();
				}
				LOGGER.finer(logMessage.toString());
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						// ignore
					}
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private static void printCookies(HttpClientContext httpContext) {
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Cookies:"); //$NON-NLS-1$
			CookieStore cookieStore = (CookieStore) httpContext.getAttribute(HttpClientContext.COOKIE_STORE);
			List<Cookie> cookies = cookieStore.getCookies();
	        if (cookies.isEmpty()) {
	            System.out.println("\tNone"); //$NON-NLS-1$
	        } else {
	            for (int i = 0; i < cookies.size(); i++) {
	            	LOGGER.finer("\t- " + cookies.get(i).toString()); //$NON-NLS-1$
	            }
	        }
		}
	}
}
