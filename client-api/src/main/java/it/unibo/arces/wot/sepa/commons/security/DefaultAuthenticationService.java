package it.unibo.arces.wot.sepa.commons.security;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;

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

public class DefaultAuthenticationService extends AuthenticationService {
	
	public DefaultAuthenticationService(OAuthProperties oauthProperties)
			throws SEPASecurityException {
		super(oauthProperties);
	}
	
	public Response registerClient(String client_id, String username, String initialAccessToken, int timeout) throws SEPASecurityException {
		if (client_id == null) throw new SEPASecurityException("Identity is null");
			
		logger.log(Level.getLevel("oauth"),"REGISTER " + client_id);

		CloseableHttpResponse response = null;
		long start = Timings.getTime();

		try {
			URI uri = new URI(oauthProperties.getRegisterUrl());
			ByteArrayEntity body = new ByteArrayEntity(new RegistrationRequest(client_id).toString().getBytes("UTF-8"));

			HttpPost httpRequest = new HttpPost(uri);
			httpRequest.setHeader("Content-Type", "application/json");
			httpRequest.setHeader("Accept", "application/json");
			httpRequest.setEntity(body);

			// Set timeout
			RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
					.build();
			httpRequest.setConfig(requestConfig);

			logger.log(Level.getLevel("oauth"),"Request: "+httpRequest);

			try {
				response = httpClient.execute(httpRequest);
			} catch (IOException e) {
				logger.error("HTTP EXECUTE: " + e.getMessage());
				return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "HttpExecute", e.getMessage());
			}

			logger.log(Level.getLevel("oauth"),"Response: " + response);
			
			HttpEntity entity = response.getEntity();
			String jsonResponse = EntityUtils.toString(entity, Charset.forName("UTF-8"));

			EntityUtils.consume(entity);

			JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();

			if (json.has("error")) {
				int code = json.get("status_code").getAsInt();
				String error = json.get("error").getAsString();
				String description = json.get("error_description").getAsString();

				ErrorResponse ret = new ErrorResponse(code, error, description);
				logger.error(ret);

				return ret;
			}

			String id = json.get("credentials").getAsJsonObject().get("client_id").getAsString();
			String secret = json.get("credentials").getAsJsonObject().get("client_secret").getAsString();
			JsonElement signature = json.get("credentials").getAsJsonObject().get("signature");

			Timings.log("REGISTER", start, Timings.getTime());

			return new RegistrationResponse(id, secret, signature);

		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "URISyntaxException", e.getMessage());
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "UnsupportedEncodingException",
					e.getMessage());
		} catch (ParseException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "ParseException", e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "IOException", e.getMessage());
		} finally {
			try {
				if (response != null)
					response.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				Timings.log("REGISTER_ERROR", start, Timings.getTime());
				return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "IOException", e.getMessage());
			}
		}
	}

	public Response requestToken(String authorization,int timeout) {
		logger.log(Level.getLevel("oauth"),"TOKEN_REQUEST: " + authorization);

		CloseableHttpResponse response = null;
		long start = Timings.getTime();

		try {
			URI uri = new URI(oauthProperties.getTokenRequestUrl());

			HttpPost httpRequest = new HttpPost(uri);
			httpRequest.setHeader("Content-Type", "application/json");
			httpRequest.setHeader("Accept", "application/json");
			httpRequest.setHeader("Authorization", authorization);

			// Set timeout
			RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
					.build();
			httpRequest.setConfig(requestConfig);

			try {
				response = httpClient.execute(httpRequest);
				// break;
			} catch (IOException e) {
				logger.error("HTTP EXECUTE: " + e.getMessage());
				return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "HttpExecute", e.getMessage());
			}

			logger.log(Level.getLevel("oauth"),"Response: " + response);
			HttpEntity entity = response.getEntity();
			String jsonResponse = EntityUtils.toString(entity, Charset.forName("UTF-8"));
			EntityUtils.consume(entity);

			// Parse response
			JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();

			if (json.has("error")) {
				Timings.log("TOKEN_REQUEST", start, Timings.getTime());
				ErrorResponse error = new ErrorResponse(json.get("status_code").getAsInt(),
						json.get("error").getAsString(), json.get("error_description").getAsString());
				return error;
			}

			return new JWTResponse(json);
		} catch (Exception e) {
			logger.error(e.getMessage());
			Timings.log("TOKEN_REQUEST", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Exception", e.getMessage());
		} finally {
			try {
				if (response != null)
					response.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				Timings.log("TOKEN_REQUEST", start, Timings.getTime());
				return new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "IOException", e.getMessage());
			}
		}
	}

	@Override
	public void close() throws IOException {
		httpClient.close();
	}
}
