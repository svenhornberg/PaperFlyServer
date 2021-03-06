/*
 * Copyright (C) 2013 Michael Koppen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fhb.paperfly.server.rest.v1.service.filter;

import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.oauth.server.NonceManager;
import com.sun.jersey.oauth.server.OAuthException;
//import com.sun.jersey.oauth.server.OAuthSecurityContext;
import javax.ws.rs.WebApplicationException;

import com.sun.jersey.oauth.server.OAuthServerRequest;
import com.sun.jersey.oauth.server.api.providers.DefaultOAuthProvider;
import com.sun.jersey.oauth.server.spi.OAuthConsumer;
import com.sun.jersey.oauth.server.spi.OAuthProvider;
import com.sun.jersey.oauth.server.spi.OAuthToken;
import com.sun.jersey.oauth.signature.OAuthParameters;
import com.sun.jersey.oauth.signature.OAuthSecrets;
import com.sun.jersey.oauth.signature.OAuthSignature;
import com.sun.jersey.oauth.signature.OAuthSignatureException;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import de.fhb.paperfly.server.rest.v1.service.context.OAuthSecurityContext;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * https://svn.java.net/svn/jersey~svn/trunk/jersey/contribs/jersey-oauth/
 *
 * @author Michael Koppen <michael.koppen@googlemail.com>
 */
public class OAuthServerFilter implements ContainerRequestFilter {

	/**
	 * OAuth realm. Default is set to "default".
	 */
	public static final String PROPERTY_REALM = "com.sun.jersey.config.property.oauth.realm";
	/**
	 * Property that can be set to a regular expression used to match the path
	 * (relative to the base URI) this filter should not be applied to.
	 */
	public static final String PROPERTY_IGNORE_PATH_PATTERN = "com.sun.jersey.config.property.oauth.ignorePathPattern";
	/**
	 * Can be set to max. age (in milliseconds) of nonces that should be tracked
	 * (default = 300000 ms = 5 min).
	 */
	public static final String PROPERTY_MAX_AGE = "com.sun.jersey.config.property.oauth.maxAge";
	/**
	 * Property that can be set to frequency of collecting nonces exceeding max.
	 * age (default = 100 = every 100 requests).
	 */
	public static final String PROPERTY_GC_PERIOD = "com.sun.jersey.config.property.oauth.gcPeriod";
	/**
	 * If set to true makes the correct OAuth authentication optional - i.e.
	 * instead of returning the appropriate status code
	 * ({@link Response.Status#BAD_REQUEST} or
	 * {@link Response.Status#UNAUTHORIZED}) the filter will ignore this request
	 * (as if it was not authenticated) and let the web application deal with
	 * it.
	 */
	public static final String FEATURE_NO_FAIL = "com.sun.jersey.config.feature.oauth.noFail";
	/**
	 * OAuth Server
	 */
	private final OAuthProvider provider;
	/**
	 * Manages and validates incoming nonces.
	 */
	private final NonceManager nonces;
	/**
	 * Maximum age (in milliseconds) of timestamp to accept in incoming
	 * messages.
	 */
	private final int maxAge;
	/**
	 * Average requests to process between nonce garbage collection passes.
	 */
	private final int gcPeriod;
	/**
	 * Value to return in www-authenticate header when 401 response returned.
	 */
	private final String wwwAuthenticateHeader;
	/**
	 * OAuth protocol versions that are supported.
	 */
	private final Set<String> versions;
	/**
	 * Regular expression pattern for path to ignore.
	 */
	private final Pattern ignorePathPattern;
	private final boolean optional;

	public OAuthServerFilter(@Context ResourceConfig rc, @Context OAuthProvider provider) {
		this.provider = provider;

		// establish supported OAuth protocol versions
		HashSet<String> v = new HashSet<String>();
		v.add(null);
		v.add("1.0");
		versions = Collections.unmodifiableSet(v);

		// optional initialization parameters (defaulted)
		String realm = defaultInitParam(rc, PROPERTY_REALM, "PaperFlyRealm");//"default");
		maxAge = intValue(defaultInitParam(rc, PROPERTY_MAX_AGE, "300000")); // 5 minutes
		gcPeriod = intValue(defaultInitParam(rc, PROPERTY_GC_PERIOD, "100")); // every 100 on average
		ignorePathPattern = pattern(defaultInitParam(rc, PROPERTY_IGNORE_PATH_PATTERN, null)); // no pattern
//		optional = rc.getFeature(FEATURE_NO_FAIL);
		optional = false;

		nonces = new NonceManager(maxAge, gcPeriod);

		// www-authenticate header for the life of the object
		wwwAuthenticateHeader = "OAuth realm=\"" + realm + "\"";
	}

	@Override
	public ContainerRequest filter(ContainerRequest request) {
		StringBuilder log = new StringBuilder("");
		log.append("Calling OAuthFilter").append("\n");


		log.append("path: ").append(request.getPath()).append("\n");

		// do not filter if the request path matches pattern to ignore
		if (match(ignorePathPattern, request.getPath())) {
			log.append("...Requested path is irgnored because of configured ignorePattern").append("\n");
			System.out.println(log.toString());
			return request;
		}

		String authHeader = request.getHeaderValue(OAuthParameters.AUTHORIZATION_HEADER);
		if (authHeader == null || !authHeader.toUpperCase().startsWith(OAuthParameters.SCHEME.toUpperCase())) {
			log.append("...No OAuth Header found").append("\n");
			System.out.println(log.toString());
			throw new WebApplicationException(newUnauthorizedException().toResponse());
//			return request;
		}



		OAuthSecurityContext sc = null;

		try {
			sc = getSecurityContext(request);
		} catch (OAuthException e) {
			if (optional) {
				System.out.println(log.toString());
				return request;
			} else {
				log.append("...OAuth failed").append("\n");
				System.out.println(log.toString());
				throw new WebApplicationException(e.toResponse());
			}
		}
		log.append("...setting OAuth securityContext").append("\n");
		request.setSecurityContext(sc);

		System.out.println(log.toString());
		return request;
	}

	private OAuthSecurityContext getSecurityContext(ContainerRequest request) throws OAuthException {
		StringBuilder log = new StringBuilder("");
		log.append("...getSecurityContext").append("\n");
		OAuthServerRequest osr = new OAuthServerRequest(request);
		OAuthParameters params = new OAuthParameters().readRequest(osr);

		// apparently not signed with any OAuth parameters; unauthorized
		if (params.size() == 0) {
			log.append("...no OAuth parameters").append("\n");
			System.out.println(log.toString());
			throw newUnauthorizedException();
		}

		// get required OAuth parameters
		String consumerKey = requiredOAuthParam(params.getConsumerKey());
		String token = params.getToken();
		String timestamp = requiredOAuthParam(params.getTimestamp());
		String nonce = requiredOAuthParam(params.getNonce());

		// enforce other supported and required OAuth parameters
		requiredOAuthParam(params.getSignature());
		supportedOAuthParam(params.getVersion(), versions);

		// retrieve secret for consumer key
		OAuthConsumer consumer = provider.getConsumer(consumerKey);
		if (consumer == null) {
			log.append("...no OAuth consumer found").append("\n");
			System.out.println(log.toString());
			throw newUnauthorizedException();
		}

		OAuthSecrets secrets = new OAuthSecrets().consumerSecret(consumer.getSecret());
		OAuthSecurityContext sc;
		String nonceKey;

		if (token == null) {

			if (consumer.getPrincipal() == null) {
				System.out.println(log.toString());
				throw newUnauthorizedException();
			}
			nonceKey = "c:" + consumerKey;
			sc = new OAuthSecurityContext(consumer, request.isSecure());

		} else {

			OAuthToken accessToken = provider.getAccessToken(token);
			if (accessToken == null) {
				System.out.println(log.toString());
				throw newUnauthorizedException();
			}

			OAuthConsumer atConsumer = accessToken.getConsumer();
			if (atConsumer == null || !consumerKey.equals(atConsumer.getKey())) {
				System.out.println(log.toString());
				throw newUnauthorizedException();
			}

			nonceKey = "t:" + token;
			secrets.tokenSecret(accessToken.getSecret());
			sc = new OAuthSecurityContext(accessToken, request.isSecure());
		}
		if (!verifySignature(osr, params, secrets)) {
			System.out.println(log.toString());
			throw newUnauthorizedException();
		}
		if (!nonces.verify(nonceKey, timestamp, nonce)) {
			System.out.println(log.toString());
			throw newUnauthorizedException();
		}

		return sc;
	}

	private static String defaultInitParam(ResourceConfig config, String name, String value) {
		String v = (String) config.getProperty(name);
		if (v == null || v.length() == 0) {
			v = value;
		}
		return v;
	}

	private static int intValue(String value) {
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException nfe) {
			return -1;
		}
	}

	private static String requiredOAuthParam(String value) throws OAuthException {
		if (value == null) {
			throw newBadRequestException();
		}
		return value;
	}

	private static String supportedOAuthParam(String value, Set<String> set) throws OAuthException {
		if (!set.contains(value)) {
			throw newBadRequestException();
		}
		return value;
	}

	private static Pattern pattern(String p) {
		if (p == null) {
			return null;
		}
		return Pattern.compile(p);
	}

	private static boolean match(Pattern pattern, String value) {
		return (pattern != null && value != null && pattern.matcher(value).matches());
	}

	private static boolean verifySignature(OAuthServerRequest osr,
			OAuthParameters params, OAuthSecrets secrets) {
		try {
			return OAuthSignature.verify(osr, params, secrets);
		} catch (OAuthSignatureException ose) {
			throw newBadRequestException();
		}
	}

	private static OAuthException newBadRequestException() throws OAuthException {
		return new OAuthException(Response.Status.BAD_REQUEST, null);
	}

	private OAuthException newUnauthorizedException() throws OAuthException {
		return new OAuthException(Response.Status.UNAUTHORIZED, wwwAuthenticateHeader);
	}
}
