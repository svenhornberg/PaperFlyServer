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
package de.fhb.paperfly.server.rest.v1.service.resources.auth;

import com.qmino.miredot.annotations.ReturnType;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.oauth.server.spi.OAuthProvider;
import de.fhb.paperfly.server.account.entity.Account;
import de.fhb.paperfly.server.account.entity.Status;
import de.fhb.paperfly.server.account.service.AccountServiceLocal;
import de.fhb.paperfly.server.chat.ChatController;
import de.fhb.paperfly.server.logging.interceptor.WebServiceLoggerInterceptor;
import de.fhb.paperfly.server.logging.service.LoggingServiceLocal;
import de.fhb.paperfly.server.rest.v1.dto.output.TokenDTO;
import de.fhb.paperfly.server.rest.v1.service.PaperFlyRestService;
import de.fhb.paperfly.server.rest.v1.service.provider.DefaultOAuthProvider;
import java.security.spec.KeySpec;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.ServletException;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import sun.misc.BASE64Decoder;

/**
 *
 * @author Michael Koppen <michael.koppen@googlemail.com>
 */
@Stateless
//@Path("auth/")
@Interceptors({WebServiceLoggerInterceptor.class})
public class AuthResource {

	@EJB
	public LoggingServiceLocal LOG;
	@EJB
	private AccountServiceLocal accountService;
	@EJB
	private ChatController chatController;

	/**
	 * Method to login a user.
	 *
	 * @title Login
	 * @summary Log into the service an retrieve your OAuth-Token.
	 * @param request
	 * @param provider
	 * @return Returns the OAuth-Token of the logged in user.
	 */
	@GET
	@Path("login")
	@Produces(PaperFlyRestService.JSON_MEDIA_TYPE)
	@ReturnType("de.fhb.paperfly.server.rest.v1.dto.output.TokenDTO")
	public Response login(@Context HttpServletRequest request, @Context OAuthProvider provider, @Context SecurityContext sc) {
		LOG.log(this.getClass().getName(), Level.INFO, "LOGIN...");
		Response resp;
		try {
//			request.logout();
			HttpSession session = request.getSession(true);
			System.out.println("isSession new?: " + session.isNew());
			System.out.println("SessionID: " + session.getId());
			System.out.println("trackingmodes");
			for (SessionTrackingMode object : session.getServletContext().getEffectiveSessionTrackingModes()) {
				System.out.println("trackingmode: " + object);
			}
			String cred = request.getHeader("cred");
			cred = decrypt(cred);
			String username = cred.split(":")[0];
			String password = cred.split(":")[1];

			System.out.println("Username: " + username);
			System.out.println("Password: " + password);

			request.login(username, password);


			session = request.getSession(true);
			System.out.println("isSession new?: " + session.isNew());
			System.out.println("SessionID: " + session.getId());
			session.setAttribute("mail", request.getUserPrincipal().toString());

			MultivaluedMap<String, String> roles = new MultivaluedMapImpl();

			if (request.isUserInRole("ADMINISTRATOR")) {
				LOG.log(this.getClass().getName(), Level.INFO, "User is in role ADMINISTRATOR");
				roles.add("roles", "ADMINISTRATOR");
			}
			if (request.isUserInRole("USER")) {
				LOG.log(this.getClass().getName(), Level.INFO, "User is in role USER");
				roles.add("roles", "USER");
			}
			if (request.isUserInRole("ANONYMOUS")) {
				LOG.log(this.getClass().getName(), Level.INFO, "User is in role ANONYMOUS");
				roles.add("roles", "ANONYMOUS");
			}
			DefaultOAuthProvider.Consumer c = ((DefaultOAuthProvider) provider).registerConsumer(request.getUserPrincipal().toString(), request.getUserPrincipal(), roles);
			LOG.log(this.getClass().getName(), Level.INFO, "Consumer Owner: " + c.getOwner());
			LOG.log(this.getClass().getName(), Level.INFO, "Consumer Secret: " + c.getSecret());
			LOG.log(this.getClass().getName(), Level.INFO, "Consumer Key: " + c.getKey());
			LOG.log(this.getClass().getName(), Level.INFO, "Consumer Principal: " + c.getPrincipal());


			String consumerKey = "";
			String callbackURL = "";
			Map<String, List<String>> attributes = null;
//			OAuthToken oauthtoken = provider.newRequestToken(consumerKey, callbackURL, attributes);

			LOG.log(this.getClass().getName(), Level.INFO, "Successfully logged in!");
			LOG.log(this.getClass().getName(), Level.INFO, "User: " + request.getUserPrincipal());

			Account myAccount = accountService.getAccountByMail(sc.getUserPrincipal().getName());
			myAccount.setStatus(Status.ONLINE);
			accountService.editAccount(myAccount);

			System.out.println("SessionID: " + session.getId());
			resp = Response.ok(new TokenDTO(c.getKey(), c.getSecret())).build();
		} catch (Exception e) {
			LOG.log(this.getClass().getName(), Level.SEVERE, "Exception: {0}", e.getMessage());
			resp = Response.status(500).build();
		}
		return resp;
	}

	/**
	 * Method to logout a user.
	 *
	 * @title Logout
	 * @summary Log's out the actual user.
	 * @param request
	 * @return Nothing to return.
	 */
	@GET
	@Path("logout")
	@Produces(PaperFlyRestService.JSON_MEDIA_TYPE)
	public Response logout(@Context HttpServletRequest request, @Context SecurityContext sc) throws ServletException {
		LOG.log(this.getClass().getName(), Level.INFO, "LOGOUT...");
		Response resp;

//		chatController.removeUserFromAllChats(sc.getUserPrincipal().getName());
//
//		Account myAccount = accountService.getAccountByMail(sc.getUserPrincipal().getName());
//		myAccount.setStatus(Status.OFFLINE);
//		accountService.editAccount(myAccount);

		if (request.getSession(false) != null) {
			request.getSession(false).invalidate();
		} else {
			LOG.log(this.getClass().getName(), Level.INFO, "Session was Null!");
		}
		request.logout();


		resp = Response.ok().build();
		return resp;
	}

	/**
	 * decrypt password and username for login.
	 *
	 * http://sanjaal.com/java/186/java-encryption/tutorial-java-des-encryption-and-decryption/
	 *
	 * @param crypted
	 * @return decrypted string
	 */
	private String decrypt(String crypted) {
		String decryptedText = null;
		try {
			String UNICODE_FORMAT = "UTF8";
			String DES_ENCRYPTION_SCHEME = "DES";

			String myEncryptionKey = "HansPeter";
			byte[] keyAsBytes = myEncryptionKey.getBytes(UNICODE_FORMAT);
			KeySpec myKeySpec = new DESKeySpec(keyAsBytes);


			Cipher cipher = Cipher.getInstance(DES_ENCRYPTION_SCHEME);
			SecretKeyFactory mySecretKeyFactory = SecretKeyFactory.getInstance(DES_ENCRYPTION_SCHEME);
			SecretKey key = mySecretKeyFactory.generateSecret(myKeySpec);


			cipher.init(Cipher.DECRYPT_MODE, key);
			BASE64Decoder base64decoder = new BASE64Decoder();
			byte[] encryptedText = base64decoder.decodeBuffer(crypted);
			byte[] plainText = cipher.doFinal(encryptedText);
			decryptedText = bytes2String(plainText);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return decryptedText;
	}

	/**
	 * converts bytes to a string.
	 *
	 * http://sanjaal.com/java/186/java-encryption/tutorial-java-des-encryption-and-decryption/
	 *
	 * @param bytes
	 * @return the string
	 */
	private String bytes2String(byte[] bytes) {
		StringBuffer stringBuffer = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			stringBuffer.append((char) bytes[i]);
		}
		return stringBuffer.toString();
	}
}
