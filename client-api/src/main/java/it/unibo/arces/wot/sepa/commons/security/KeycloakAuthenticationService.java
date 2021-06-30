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
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.JWTResponse;
import it.unibo.arces.wot.sepa.commons.response.RegistrationResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.timing.Timings;

public class KeycloakAuthenticationService extends AuthenticationService {
	String registrationAccessToken;

	public KeycloakAuthenticationService(OAuthProperties oauthProp)
			throws SEPASecurityException {
		super(oauthProp);
	}

	/**
	 * Client Registration Request
	 * 
curl --location --request POST 'https://sepa.vaimee.it:8443/auth/realms/MONAS/clients-registrations/default' \
--header 'Content-Type: application/json' \
--header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI4Y2E2ZGNiNC1jZmY5LTQzNGUtODNhNi05NTk4MzQ1NjUxZGMifQ.eyJleHAiOjAsImlhdCI6MTU5OTgwNTYzMywianRpIjoiMzNkZjRjZDYtMjJkZC00M2UxLWFmMzItYWE3NTMwMmJmZGUzIiwiaXNzIjoiaHR0cHM6Ly9zZXBhLnZhaW1lZS5pdDo4NDQzL2F1dGgvcmVhbG1zL01PTkFTIiwiYXVkIjoiaHR0cHM6Ly9zZXBhLnZhaW1lZS5pdDo4NDQzL2F1dGgvcmVhbG1zL01PTkFTIiwidHlwIjoiSW5pdGlhbEFjY2Vzc1Rva2VuIn0.edceIxjn2Fdc3NzXYIu--lWbDVBF0YXQfrUJ1R94myc' \
--data-raw '{"clientId":"sepatest_client","standardFlowEnabled" : false, "implicitFlowEnabled" : false, "authorizationServicesEnabled":true,"directAccessGrantsEnabled" : false, "serviceAccountsEnabled" : true, "publicClient":false, "protocol":"openid-connect","protocolMappers":[{"name":"hardcoded_username","protocol":"openid-connect","protocolMapper" : "oidc-hardcoded-claim-mapper","config" : {"claim.value":"sepatest","userinfo.token.claim":"false","id.token.claim":"false","access.token.claim":"true","claim.name":"preferred_username","jsonType.label":"String"}}]}'
	 */

	@Override
	public Response registerClient(String client_id, String username, String initialAccessToken,int timeout) throws SEPASecurityException {
		if (client_id == null)
			throw new SEPASecurityException("client_id is null");

		logger.log(Level.getLevel("oauth"),"REGISTER " + client_id);
		
		CloseableHttpResponse response = null;
		long start = Timings.getTime();

		try {
			URI uri = new URI(oauthProperties.getRegisterUrl());

			// 1) Register client
			HttpPost httpRequest = new HttpPost(uri);

			httpRequest.setHeader("Content-Type", "application/json");
			httpRequest.setHeader("Authorization", "bearer "+initialAccessToken);

			// oidc_hardcoded_claim_mapper for username link
			JsonObject usernameClaim = new JsonObject();
			usernameClaim.add("claim.value", new JsonPrimitive(username));
			usernameClaim.add("claim.name", new JsonPrimitive("username"));
			usernameClaim.add("userinfo.token.claim", new JsonPrimitive(false));
			usernameClaim.add("id.token.claim", new JsonPrimitive(false));
			usernameClaim.add("access.token.claim", new JsonPrimitive(true));
			usernameClaim.add("jsonType.label", new JsonPrimitive("String"));

			JsonArray protocolMappers = new JsonArray();
			JsonObject oidc_hardcoded_claim_mapper = new JsonObject();
			oidc_hardcoded_claim_mapper.add("name", new JsonPrimitive("hardcoded_username"));
			oidc_hardcoded_claim_mapper.add("protocol", new JsonPrimitive("openid-connect"));
			oidc_hardcoded_claim_mapper.add("protocolMapper", new JsonPrimitive("oidc-hardcoded-claim-mapper"));			
			oidc_hardcoded_claim_mapper.add("config", usernameClaim);
			protocolMappers.add(oidc_hardcoded_claim_mapper);
			
			JsonObject jsonBody = new JsonObject();
			jsonBody.add("clientId", new JsonPrimitive(client_id));
			
			jsonBody.add("standardFlowEnabled", new JsonPrimitive(false));
			jsonBody.add("implicitFlowEnabled", new JsonPrimitive(false));
			jsonBody.add("directAccessGrantsEnabled", new JsonPrimitive(false));
			jsonBody.add("serviceAccountsEnabled", new JsonPrimitive(true));
			jsonBody.add("authorizationServicesEnabled", new JsonPrimitive(false));			
			jsonBody.add("publicClient", new JsonPrimitive(false));
			jsonBody.add("protocol", new JsonPrimitive("openid-connect"));
			
			jsonBody.add("protocolMappers", protocolMappers);
						
			StringEntity body = new StringEntity(jsonBody.toString());
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
				return new ErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "HttpExecute", e.getMessage());
			}

			logger.log(Level.getLevel("oauth"),"Response: " + response);
			HttpEntity entity = response.getEntity();
			String jsonResponse = EntityUtils.toString(entity, Charset.forName("UTF-8"));

			EntityUtils.consume(entity);

			JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();

			if (json.has("error")) {
				// int code = json.get("status_code").getAsInt();
				String error = json.get("error").getAsString();
				String description = json.get("error_description").getAsString();

				ErrorResponse ret = new ErrorResponse(response.getStatusLine().getStatusCode(), error, description);
				logger.error(ret);

				return ret;
			}
			return new RegistrationResponse(client_id, json.get("secret").getAsString(), json);

		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_BAD_REQUEST, "URISyntaxException", e.getMessage());
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_BAD_REQUEST, "UnsupportedEncodingException",
					e.getMessage());
		} catch (ParseException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_BAD_REQUEST, "ParseException", e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
			Timings.log("REGISTER_ERROR", start, Timings.getTime());
			return new ErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "IOException", e.getMessage());
		} finally {
			try {
				if (response != null)
					response.close();
			} catch (IOException e) {
				logger.error(e.getMessage());
				Timings.log("REGISTER_ERROR", start, Timings.getTime());
				return new ErrorResponse(HttpStatus.SC_SERVICE_UNAVAILABLE, "IOException", e.getMessage());
			}
		}
	}

	@Override
	public Response requestToken(String authorization, int timeout) {
		/*
		 * POST /auth/realms/demo/protocol/openid-connect/token Authorization: Basic
		 * cHJvZHVjdC1zYS1jbGllbnQ6cGFzc3dvcmQ= Content-Type:
		 * application/x-www-form-urlencoded
		 * 
		 * grant_type=client_credentials
		 **/
		logger.log(Level.getLevel("oauth"),"TOKEN_REQUEST: " + authorization);

		CloseableHttpResponse response = null;
		long start = Timings.getTime();

		try {
			URI uri = new URI(oauthProperties.getTokenRequestUrl());

			HttpPost httpRequest = new HttpPost(uri);
			StringEntity body = new StringEntity("grant_type=client_credentials");
			httpRequest.setEntity(body);
			httpRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
			httpRequest.setHeader("Authorization", authorization);

			// Set timeout
			RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
					.build();
			httpRequest.setConfig(requestConfig);

			try {
				response = httpClient.execute(httpRequest);
				// break;
			} catch (Exception e) {
				ErrorResponse err = new ErrorResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getClass().getName(), e.getMessage());
				logger.error(err);
				return err;
			}

			logger.log(Level.getLevel("oauth"),"Response: " + response);
			HttpEntity entity = response.getEntity();
			String jsonResponse = EntityUtils.toString(entity, Charset.forName("UTF-8"));
			EntityUtils.consume(entity);

			// Parse response
			JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();

			if (json.has("error")) {
				Timings.log("TOKEN_REQUEST", start, Timings.getTime());
				ErrorResponse error = new ErrorResponse(response.getStatusLine().getStatusCode(),"token_request",
						json.get("error").getAsString());
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
}
