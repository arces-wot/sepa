/* Authentication properties which includes the token for OAuth 2.0 authorization
 * 
 * Author: Luca Roffia (luca.roffia@unibo.it)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package it.unibo.arces.wot.sepa.commons.security;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.JWTResponse;

/**
 * The set of properties used for client authentication
 * 
 * 
 * <pre>
	"oauth": {
		"enable": true,
		"ssl": "TLSv1.2",
		"loadTrustMaterial": {
			"jks" : "store.jks",
			"secret" : "XYZ"
		},
		"registration": {
			"endpoint": "https://localhost:8443/auth/realms/MONAS/clients-registrations/default",
			"initialAccessToken": "eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI4Y2E2ZGNiNC1jZmY5LTQzNGUtODNhNi05NTk4MzQ1NjUxZGMifQ.eyJleHAiOjAsImlhdCI6MTYwMjA3ODc3NiwianRpIjoiNjAxYzc3YWQtMDc3Yy00YjNlLTg2NWEtYzI3M2QwNTBmYzk2IiwiaXNzIjoiaHR0cHM6Ly9zZXBhLnZhaW1lZS5pdDo4NDQzL2F1dGgvcmVhbG1zL01PTkFTIiwiYXVkIjoiaHR0cHM6Ly9zZXBhLnZhaW1lZS5pdDo4NDQzL2F1dGgvcmVhbG1zL01PTkFTIiwidHlwIjoiSW5pdGlhbEFjY2Vzc1Rva2VuIn0.Whm-sQrv8c72It2aZkQ60S-885pNh0pOcmei65CyzlI",
			"username": "sepatest"
			"clientid": "sepatest"
		},
		"authentication": {
			"endpoint": "https://locahost:8443/auth/realms/MONAS/protocol/openid-connect/token"
		},
		"provider": "keycloak"
	},
}
 * </pre>
 */

public class OAuthProperties {
	/** The log4j2 logger. */
	private static final Logger logger = LogManager.getLogger();

	private Encryption encryption = new Encryption();

	private boolean enabled = false;

	private String registrationURL = null;
	private String tokenRequestURL = null;

	private String initialAccessToken;
	private String username = null;
	private String clientRegistrationId = null;
	
	private String clientId = null;
	private String clientSecret = null;

	private String jwt = null;
	private long expires = -1;
	private String type = null;

	private String ssl = "TLS";
	private String jks = null;
	private String jksSecret = null;
	
	private JsonObject jsap;
	private File propertiesFile;
	private JsonObject oauthJsonObject;
	
	public enum OAUTH_PROVIDER{SEPA,KEYCLOAK};
	private OAUTH_PROVIDER provider = OAUTH_PROVIDER.SEPA;
	
	public OAUTH_PROVIDER getProvider() {
		return provider;
	}
	
	public OAuthProperties(InputStream input,byte[] secret) throws SEPAPropertiesException {
		InputStreamReader in = new InputStreamReader(input);
		
		jsap = new JsonParser().parse(in).getAsJsonObject();

		try {
		
		if (secret != null) encryption = new Encryption(secret);

		if (jsap.has("oauth")) {
			oauthJsonObject = jsap.getAsJsonObject("oauth");

			if (oauthJsonObject.has("enable")) enabled = oauthJsonObject.get("enable").getAsBoolean();
			if (oauthJsonObject.has("loadTrustMaterial")) {
				jks = oauthJsonObject.get("loadTrustMaterial").getAsJsonObject().get("jks").getAsString();
				jksSecret = oauthJsonObject.get("loadTrustMaterial").getAsJsonObject().get("secret").getAsString();
			}
			if (oauthJsonObject.has("ssl")) ssl = oauthJsonObject.get("ssl").getAsString();

			if (enabled) {
				if (oauthJsonObject.has("provider")) {
					String p = oauthJsonObject.get("provider").getAsString();
					if (p.equals("keycloak")) provider = OAUTH_PROVIDER.KEYCLOAK;
					else if (p.equals("sepa")) provider = OAUTH_PROVIDER.SEPA;
					else throw new SEPASecurityException("Provider must have one of the following values: [sepa|keycloak]");	
				}
				
				if (oauthJsonObject.has("authentication")) {
					JsonObject auth = oauthJsonObject.getAsJsonObject("authentication");
					
					if (auth.has("endpoint"))
						tokenRequestURL = auth.get("endpoint").getAsString();
					if (auth.has("client_id"))
						clientId = encryption.decrypt(auth.get("client_id").getAsString());
					if (auth.has("client_secret"))
						clientSecret = encryption.decrypt(auth.get("client_secret").getAsString());
					if (auth.has("jwt"))
						jwt = encryption.decrypt(auth.get("jwt").getAsString());
					if (auth.has("expires"))
						expires = Long.decode(encryption.decrypt(auth.get("expires").getAsString()));
					if (auth.has("type"))
						type = encryption.decrypt(auth.get("type").getAsString());	
				}
											
				// Initial access token registration
				if (oauthJsonObject.has("registration")) {
					JsonObject reg = oauthJsonObject.getAsJsonObject("registration");
					registrationURL = reg.get("endpoint").getAsString();
					
					initialAccessToken = (reg.has("initialAccessToken") ? reg.get("initialAccessToken").getAsString() : null);					
					username = (reg.has("username") ? reg.get("username").getAsString() : null);
					clientRegistrationId = (reg.has("client_id") ? reg.get("client_id").getAsString() : null);
				}								
			} 
		} 
		} catch(Exception e) {
			logger.error(e.getMessage());
			throw new SEPAPropertiesException(e.getMessage());
		}
	}
	
	public OAuthProperties(String jsapFileName, byte[] secret)
			throws SEPAPropertiesException, SEPASecurityException {
		propertiesFile = new File(jsapFileName);

		FileReader in;
		try {
			in = new FileReader(propertiesFile);
		} catch (FileNotFoundException e) {
			throw new SEPAPropertiesException("FileNotFoundException. " + e.getMessage());
		}

		jsap = new JsonParser().parse(in).getAsJsonObject();

		try {
			in.close();
		} catch (IOException e) {
			throw new SEPAPropertiesException("IOException. " + e.getMessage());
		}

		try {
		
		if (secret != null) encryption = new Encryption(secret);

		if (jsap.has("oauth")) {
			oauthJsonObject = jsap.getAsJsonObject("oauth");

			if (oauthJsonObject.has("enable")) enabled = oauthJsonObject.get("enable").getAsBoolean();
			if (oauthJsonObject.has("loadTrustMaterial")) {
				jks = oauthJsonObject.get("loadTrustMaterial").getAsJsonObject().get("jks").getAsString();
				jksSecret = oauthJsonObject.get("loadTrustMaterial").getAsJsonObject().get("secret").getAsString();
			}
			if (oauthJsonObject.has("ssl")) ssl = oauthJsonObject.get("ssl").getAsString();

			if (enabled) {
				if (oauthJsonObject.has("provider")) {
					String p = oauthJsonObject.get("provider").getAsString();
					if (p.equals("keycloak")) provider = OAUTH_PROVIDER.KEYCLOAK;
					else if (p.equals("sepa")) provider = OAUTH_PROVIDER.SEPA;
					else throw new SEPASecurityException("Provider must have one of the following values: [sepa|keycloak]");	
				}
				
				if (oauthJsonObject.has("authentication")) {
					JsonObject auth = oauthJsonObject.getAsJsonObject("authentication");
					
					if (auth.has("endpoint"))
						tokenRequestURL = auth.get("endpoint").getAsString();
					if (auth.has("client_id"))
						clientId = encryption.decrypt(auth.get("client_id").getAsString());
					if (auth.has("client_secret"))
						clientSecret = encryption.decrypt(auth.get("client_secret").getAsString());
					if (auth.has("jwt"))
						jwt = encryption.decrypt(auth.get("jwt").getAsString());
					if (auth.has("expires"))
						expires = Long.decode(encryption.decrypt(auth.get("expires").getAsString()));
					if (auth.has("type"))
						type = encryption.decrypt(auth.get("type").getAsString());	
				}
											
				// Initial access token registration
				if (oauthJsonObject.has("registration")) {
					JsonObject reg = oauthJsonObject.getAsJsonObject("registration");
					registrationURL = reg.get("endpoint").getAsString();
					
					initialAccessToken = (reg.has("initialAccessToken") ? reg.get("initialAccessToken").getAsString() : null);					
					username = (reg.has("username") ? reg.get("username").getAsString() : null);
					clientRegistrationId = (reg.has("client_id") ? reg.get("client_id").getAsString() : null);
				}								
			} 
		} 
		} catch(Exception e) {
			logger.error(e.getMessage());
			throw new SEPAPropertiesException(e.getMessage());
		}

	}

	public OAuthProperties(String jsap) throws SEPAPropertiesException, SEPASecurityException {
		this(jsap, null);
	}
	
	public OAuthProperties(InputStream jsap) throws SEPAPropertiesException, SEPASecurityException {
		this(jsap, null);
	}

	public OAuthProperties() {}


	public boolean isEnabled() {
		return enabled;
	}

	public String getRegisterUrl() {
		return registrationURL;
	}

	public String getTokenRequestUrl() {
		return tokenRequestURL;
	}

	/**
	 * Gets the access token.
	 *
	 * @return the access token
	 */
	public String getBearerAuthorizationHeader() {
		if (jwt != null)
			return type + " "+ jwt;
		else
			return null;
	}

	public String getToken() {
		return jwt;
	}

	/**
	 * Gets the token type.
	 *
	 * @return the token type
	 */
	public String getTokenType() {
		return type;
	}

	/**
	 * Gets the basic authorization.
	 *
	 * @return the basic authorization
	 * @throws SEPASecurityException
	 */
	public String getBasicAuthorizationHeader() throws SEPASecurityException {
		if (clientId != null && clientSecret != null) {
			String plainString = clientId + ":" + clientSecret;
			try {
				return "Basic " + new String(Base64.getEncoder().encode(plainString.getBytes("UTF-8")), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new SEPASecurityException(e);
			}
		} else
			return null;
	}

	/**
	 * Sets the credentials.
	 *
	 * @param id     the username
	 * @param secret the password
	 */
	public void setCredentials(String id, String secret) throws SEPAPropertiesException, SEPASecurityException {
		// logger.debug("@setCredentials Id: " + id + " Secret:" + secret);

		clientId = id;
		clientSecret = secret;
	}

	public boolean isClientRegistered() {
		return clientId != null && clientSecret != null;
	}

	public boolean isTokenExpired() {
		if (expires < 0 || jwt == null)
			return true;
		long now = new Date().getTime();
		return expires < now;
	}

	/**
	 * Sets the JWT.
	 *
	 * @param jwt the JSON Web Token
	 * @throws SEPAPropertiesException
	 * @throws SEPASecurityException
	 *
	 */
	public void setJWT(JWTResponse jwt) throws SEPASecurityException, SEPAPropertiesException {
		logger.debug("@setJWT: " + jwt);

		long now = new Date().getTime();
		
		this.expires = now + 1000 * jwt.getExpiresIn();
		this.jwt = jwt.getAccessToken();
		this.type = jwt.getTokenType();
	}

	/**
	 * Store properties.
	 *
	 * @param propertiesFile the properties file
	 * @throws SEPAPropertiesException
	 * @throws SEPASecurityException
	 * @throws IOException             Signals that an I/O exception has occurred.
	 */
	public synchronized void storeProperties() throws SEPAPropertiesException, SEPASecurityException {
		jsap.add("oauth", new JsonObject());
		jsap.getAsJsonObject("oauth").add("enable", new JsonPrimitive(enabled));

		if (ssl != null) {
			jsap.getAsJsonObject("oauth").add("ssl", new JsonPrimitive(ssl));
		}

		if (jks != null && jksSecret != null) {
			JsonObject obj = new JsonObject();
			obj.add("jks", new JsonPrimitive(jks));
			obj.add("secret", new JsonPrimitive(jksSecret));
			jsap.getAsJsonObject("oauth").add("loadTrustMaterial", obj);	
		}
		

		if (registrationURL != null) {
			JsonObject reg = new JsonObject();
			reg.add("endpoint", new JsonPrimitive(registrationURL));
			
			if (initialAccessToken != null) reg.add("initialAccessToken", new JsonPrimitive(initialAccessToken));
			if (username != null) reg.add("username", new JsonPrimitive(username));
			if (clientRegistrationId != null) reg.add("client_id", new JsonPrimitive(clientRegistrationId));
			
			jsap.getAsJsonObject("oauth").add("registration", reg);
		}
		
		if (tokenRequestURL != null) {
			JsonObject auth = new JsonObject();
			
			auth.add("endpoint", new JsonPrimitive(tokenRequestURL));
			if (clientId != null)
				auth.add("client_id", new JsonPrimitive(encryption.encrypt(clientId)));
			
			if (clientSecret != null)
				auth.add("client_secret", new JsonPrimitive(encryption.encrypt(clientSecret)));

			if (jwt != null)
				auth.add("jwt", new JsonPrimitive(encryption.encrypt(jwt)));
			if (expires != -1)
				auth.add("expires",
						new JsonPrimitive(encryption.encrypt(String.format("%d", expires))));
			if (type != null)
				auth.add("type", new JsonPrimitive(encryption.encrypt(type)));
			jsap.getAsJsonObject("oauth").add("authentication", auth);
		}
		
		if (provider.equals(OAUTH_PROVIDER.SEPA)) {
			jsap.getAsJsonObject("oauth").add("provider", new JsonPrimitive("sepa"));
		} else if (provider.equals(OAUTH_PROVIDER.KEYCLOAK)) {
			jsap.getAsJsonObject("oauth").add("provider", new JsonPrimitive("keycloak"));
		}
			
		FileWriter out;
		try {
			out = new FileWriter(propertiesFile);
			out.write(jsap.toString());
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new SEPAPropertiesException("IOException: " + propertiesFile.getPath() + " " + e.getMessage());
		}
	}

	public String getClientId() {
		return clientId;
	}
	
	public String getClientRegistrationId() {
		return clientRegistrationId;
	}

	public String getSSLProtocol() {
		return ssl;
	}

	public String getInitialAccessToken() {
		return initialAccessToken;
	}

	public String getUsername() {
		return username;
	}

	public boolean useJks() {
		return jks != null && jksSecret != null;
	}

	public String getJks() {
		return jks;
	}

	public String getJksSecret() {
		return jksSecret;
	}

	public String getClientSecret() {
		return clientSecret;
	}
}
