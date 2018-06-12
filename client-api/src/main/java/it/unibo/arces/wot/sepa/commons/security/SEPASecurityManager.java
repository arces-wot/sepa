/* This class implements the TLS 1.0 security mechanism 
 * 
 * Author: Luca Roffia (luca.roffia@unibo.it)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package it.unibo.arces.wot.sepa.commons.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.request.RegistrationRequest;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.JWTResponse;
import it.unibo.arces.wot.sepa.commons.response.RegistrationResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.timing.Timings;

/**
 * The Class SecurityManager.
 * 
 * * ### Key and Certificate Storage ###
 * 
 * The Java platform provides for long-term persistent storage of cryptographic
 * keys and certificates via key and certificate stores. Specifically, the
 * java.security.KeyStore class represents a key store, a secure repository of
 * cryptographic keys and/or trusted certificates (to be used, for example,
 * during certification path validation), and the java.security.cert.CertStore
 * class represents a certificate store, a public and potentially vast
 * repository of unrelated and typically untrusted certificates. A CertStore may
 * also store CRLs.
 * 
 * KeyStore and CertStore implementations are distinguished by types. The Java
 * platform includes the standard PKCS11 and PKCS12 key store types (whose
 * implementations are compliant with the corresponding PKCS specifications from
 * RSA Security). It also contains a proprietary file-based key store type
 * called JKS (which stands for "Java Key Store"), and a type called DKS
 * ("Domain Key Store") which is a collection of keystores that are presented as
 * a single logical keystore.
 * 
 * ### PKI Tools ###
 * 
 * There are two built-in tools for working with keys, certificates, and key
 * stores:
 * 
 * keytool is used to create and manage key stores. It can
 * 
 * - Create public/private key pairs
 * 
 * - Display, import, and export X.509 v1, v2, and v3 certificates stored as
 * files
 * 
 * - Create self-signed certificates
 * 
 * ### Secure Communication ###
 * 
 * The data that travels across a network can be accessed by someone who is not
 * the intended recipient. When the data includes private information, such as
 * passwords and credit card numbers, steps must be taken to make the data
 * unintelligible to unauthorized parties. It is also important to ensure that
 * you are sending the data to the appropriate party, and that the data has not
 * been modified, either intentionally or unintentionally, during transport.
 * 
 * Cryptography forms the basis required for secure communication, and that is
 * described in Section 4. The Java platform also provides API support and
 * provider implementations for a number of standard secure communication
 * protocols.
 * 
 * ### SSL/TLS ###
 * 
 * The Java platform provides APIs and an implementation of the SSL and TLS
 * protocols that includes functionality for data encryption, message integrity,
 * server authentication, and optional client authentication. Applications can
 * use SSL/TLS to provide for the secure passage of data between two peers over
 * any application protocol, such as HTTP on top of TCP/IP.
 * 
 * The javax.net.ssl.SSLSocket class represents a network socket that
 * encapsulates SSL/TLS support on top of a normal stream socket
 * (java.net.Socket). Some applications might want to use alternate data
 * transport abstractions (e.g., New-I/O); the javax.net.ssl.SSLEngine class is
 * available to produce and consume SSL/TLS packets.
 * 
 * The Java platform also includes APIs that support the notion of pluggable
 * (provider-based) key managers and trust managers. A key manager is
 * encapsulated by the javax.net.ssl.KeyManager class, and manages the keys used
 * to perform authentication. A trust manager is encapsulated by the
 * TrustManager class (in the same package), and makes decisions about who to
 * trust based on certificates in the key store it manages.
 * 
 * The Java platform includes a built-in provider that implements the SSL/TLS
 * protocols:
 * 
 * SSLv3 TLSv1 TLSv1.1 TLSv1.2
 */
public class SEPASecurityManager implements HostnameVerifier {

	/** The JAVA key store. */
	KeyStore keystore;

	/** The ssl context. */
	SSLContext sslContext;

	/** The protocol. */
	String protocol;

	/** The storename. */
	private String storename;

	/** The password. */
	private String password;

	/** The log4j2 logger. */
	private static final Logger logger = LogManager.getLogger();

	/**
	 * Instantiates a new Security Manager.
	 *
	 * @param protocol
	 *            the protocol
	 * @param jksName
	 *            the jks name
	 * @param jksPassword
	 *            the jks password
	 * @param keyPassword
	 *            the key password
	 * @throws SEPASecurityException 
	 */
	public SEPASecurityManager(String protocol, String jksName, String jksPassword, String keyPassword) throws SEPASecurityException {
		// Arguments check
		if (jksName == null || jksPassword == null)
			throw new IllegalArgumentException("JKS name or password are null");

		// Initialize SSL context
		File f = new File(jksName);
		if (!f.exists() || f.isDirectory())
			throw new SEPASecurityException(new KeyStoreException(jksName + " not found"));

		try {
			keystore = KeyStore.getInstance("JKS");
			keystore.load(new FileInputStream(jksName), jksPassword.toCharArray());
			KeyManagerFactory kmfactory = KeyManagerFactory.getInstance("SunX509");
			kmfactory.init(keystore, keyPassword.toCharArray());
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(keystore);

			sslContext = SSLContext.getInstance(protocol);
			sslContext.init(kmfactory.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e) {
			throw new SEPASecurityException(e);
		}

		this.protocol = protocol;
		this.storename = jksName;
		this.password = jksPassword;
		this.protocol = protocol;
	}

	public SEPASecurityManager() throws SEPASecurityException {
		this("http","sepa.jks","sepa2017","sepa2017");
	}
	/**
	 * Gets the SSL context.
	 *
	 * @return the SSL context
	 * @throws NoSuchAlgorithmException
	 *             the no such algorithm exception
	 * @throws KeyManagementException
	 *             the key management exception
	 */
	public SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
		return sslContext;
	}

	/**
	 * Gets the key store.
	 *
	 * @return the key store
	 */
	public KeyStore getKeyStore() {
		return keystore;
	}

	/**
	 * Gets the SSL http client.
	 *
	 * @return the SSL http client
	 * @throws KeyManagementException
	 *             the key management exception
	 * @throws NoSuchAlgorithmException
	 *             the no such algorithm exception
	 * @throws KeyStoreException
	 *             the key store exception
	 * @throws CertificateException
	 *             the certificate exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public CloseableHttpClient getSSLHttpClient() throws KeyManagementException, NoSuchAlgorithmException,
			KeyStoreException, CertificateException, IOException {
		// Trust own CA and all self-signed certificates
		SSLContext sslcontext = null;

		sslcontext = SSLContexts.custom()
				.loadTrustMaterial(new File(storename), password.toCharArray(), new TrustSelfSignedStrategy()).build();

		// Allow TLSv1 protocol only
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[] { protocol }, null,
				this);

		return HttpClients.custom().setSSLSocketFactory(sslsf).build();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see javax.net.ssl.HostnameVerifier#verify(java.lang.String,
	 * javax.net.ssl.SSLSession)
	 */
	@Override
	public boolean verify(String hostname, SSLSession session) {
		// TODO IMPORTANT Verify X.509 certificate

		return true;
	}

	// Registration to the Authorization Server (AS)
	public Response register(String url, String identity) {
		logger.debug("REGISTER " + identity);

		CloseableHttpResponse response = null;
		long start = Timings.getTime();	
		
		try {
			URI uri = new URI(url);
			ByteArrayEntity body = new ByteArrayEntity(new RegistrationRequest(identity).toString().getBytes("UTF-8"));
			HttpPost httpRequest = new HttpPost(uri);
			httpRequest.setHeader("Content-Type", "application/json");
			httpRequest.setHeader("Accept",  "application/json");
			httpRequest.setEntity(body);
			
			response = getSSLHttpClient().execute(httpRequest);
			
			logger.debug("Response: " + response);
			HttpEntity entity = response.getEntity();
			String jsonResponse = EntityUtils.toString(entity, Charset.forName("UTF-8"));
			EntityUtils.consume(entity);
			JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();
			
			if (json.has("error")) {
				Timings.log("REGISTER_ERROR", start, Timings.getTime());
				ErrorResponse error = new ErrorResponse(0, json.get("error").getAsJsonObject().get("code").getAsInt(),
						json.get("error").getAsJsonObject().get("body").getAsString());
				logger.error(error);
				return error;
			}
			
			String id = json.get("credentials").getAsJsonObject().get("client_id").getAsString();
			String secret = json.get("credentials").getAsJsonObject().get("client_secret").getAsString();
			JsonElement signature = json.get("credentials").getAsJsonObject().get("signature");
			Timings.log("REGISTER", start, Timings.getTime());
			return new RegistrationResponse(id, secret, signature);
			
		}
		catch(Exception e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());	
		}
		finally {
			try {
				if (response != null)
					response.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				Timings.log("REGISTER_ERROR", start, Timings.getTime());
				return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}
		}
	}
	
	// Token request to the Authorization Server (AS)
	public Response requestToken(String url,String authorization) {
		logger.debug("TOKEN_REQUEST");

		CloseableHttpResponse response = null;
		long start = Timings.getTime();	
		
		try {
			URI uri = new URI(url);
			
			HttpPost httpRequest = new HttpPost(uri);
			httpRequest.setHeader("Content-Type", "application/json");
			httpRequest.setHeader("Accept",  "application/json");
			httpRequest.setHeader("Authorization", authorization);
			
			response = getSSLHttpClient().execute(httpRequest);
			
			logger.debug("Response: " + response);
			HttpEntity entity = response.getEntity();
			String jsonResponse = EntityUtils.toString(entity, Charset.forName("UTF-8"));
			EntityUtils.consume(entity);
			JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();
			
			if (json.has("error")) {
				Timings.log("TOKEN_REQUEST_ERROR", start, Timings.getTime());
				ErrorResponse error = new ErrorResponse(0, json.get("error").getAsJsonObject().get("code").getAsInt(),
						json.get("error").getAsJsonObject().get("body").getAsString());
				logger.error(error);
				return error;
			}
			
			int seconds = json.get("token").getAsJsonObject().get("expires_in").getAsInt();
			String jwt = json.get("token").getAsJsonObject().get("access_token").getAsString();
			String type = json.get("token").getAsJsonObject().get("token_type").getAsString();
			
			return new JWTResponse(jwt, type, seconds);	
		}
		catch(Exception e) {
			logger.error(e.getMessage());
			Timings.log("TOKEN_REQUEST_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());	
		}
		finally {
			try {
				if (response != null)
					response.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				Timings.log("TOKEN_REQUEST_ERROR", start, Timings.getTime());
				return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}
		}
	}
}
