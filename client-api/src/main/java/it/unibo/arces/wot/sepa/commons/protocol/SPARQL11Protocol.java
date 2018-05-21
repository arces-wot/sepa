/* This class implements the SPARQL 1.1 Protocol (https://www.w3.org/TR/sparql11-protocol/)
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

package it.unibo.arces.wot.sepa.commons.protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.protocol.SPARQL11Properties.HTTPMethod;
import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.pattern.ApplicationProfile;
import it.unibo.arces.wot.sepa.timing.Timings;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonParser;

import org.apache.logging.log4j.LogManager;

/**
 * This class implements the SPARQL 1.1 Protocol
 */

public class SPARQL11Protocol implements java.io.Closeable {

	/** The log4j2 logger. */
	private static final Logger logger = LogManager.getLogger();

	/** The Java bean name. */
	protected static String mBeanName = "arces.unibo.SEPA.server:type=SPARQL11Protocol";

	/** The http client. */
	final CloseableHttpClient httpClient = HttpClients.createDefault();

	/** The url components. */
	private String scheme = "http";
	private String host = "localhost";
	private int port = -1;
	private String updatePath = "/update";
	private String queryPath = "/query";

	/** Endpoint authentication */
	private boolean authentication = false;
	private String authorizationHeader = null;
	
	public SPARQL11Protocol(SPARQL11Properties properties) throws SEPAProtocolException {
		if (properties == null) {
			logger.fatal("Properties are null");
			throw new SEPAProtocolException(new IllegalArgumentException("Properties are null"));
		}

		this.scheme = properties.getProtocolScheme();
		this.host = properties.getHost();
		this.port = properties.getHttpPort();
		this.updatePath = properties.getUpdatePath();
		this.queryPath = properties.getQueryPath();
		
		this.authentication = properties.isAuthenticationRequired();
		this.authorizationHeader = properties.getAuthorizationHeader();
	}

	public SPARQL11Protocol(ApplicationProfile appProfile, String id, boolean update) throws SEPAProtocolException {
		if (scheme == null | host == null | updatePath == null | queryPath == null) {
			logger.fatal("Properties are null");
			throw new SEPAProtocolException(new IllegalArgumentException("Properties are null"));
		}
		if (update) {
			this.scheme = appProfile.getUpdateProtocol(id);
			this.host = appProfile.getUpdateHost(id);
			this.port = appProfile.getUpdatePort(id);
			this.updatePath = appProfile.getUpdatePath(id);
			this.queryPath = appProfile.getQueryPath();
			
			this.authentication = appProfile.isAuthenticationRequiredForUpdate(id);
			this.authorizationHeader = appProfile.getUpdateAuthorizationHeader(id);
		} else {
			this.scheme = appProfile.getQueryProtocol(id);
			this.host = appProfile.getQueryHost(id);
			this.port = appProfile.getQueryPort(id);
			this.updatePath = appProfile.getUpdatePath();
			this.queryPath = appProfile.getQueryPath(id);
			
			this.authentication = appProfile.isAuthenticationRequiredForQuery(id);
			this.authorizationHeader = appProfile.getQueryAuthorizationHeader(id);
		}
	}

	private Response executeRequest(HttpUriRequest req, int timeout, boolean update, int token) {
		CloseableHttpResponse httpResponse = null;
		HttpEntity responseEntity = null;
		int responseCode = 0;
		String responseBody = null;

		try {
			// Add "Authorization" header if required
			if (authentication) {
				req.setHeader("Authorization", authorizationHeader);
			}
			
			// Execute HTTP request
			logger.debug("Execute HTTP request (timeout: " + timeout + " ms) " + req.toString(), timeout);
			long start = Timings.getTime();
			httpResponse = httpClient.execute(req);
			long stop = Timings.getTime();
			if (update)
				Timings.log("ENDPOINT_UPDATE_TIME", start, stop);
			else
				Timings.log("ENDPOINT_QUERY_TIME", start, stop);

			// Status code
			responseCode = httpResponse.getStatusLine().getStatusCode();

			// Body
			responseEntity = httpResponse.getEntity();
			responseBody = EntityUtils.toString(responseEntity, Charset.forName("UTF-8"));
			logger.debug(String.format("Response (%d): %s", responseCode, responseBody));
			EntityUtils.consume(responseEntity);
		} catch (IOException e) {
			return new ErrorResponse(token, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			try {
				if (httpResponse != null)
					httpResponse.close();
			} catch (IOException e) {
				return new ErrorResponse(token, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
			}

			responseEntity = null;
		}
		if (responseCode >= 400) {
			return new ErrorResponse(token, responseCode, responseBody);
//			try {
////				return new ErrorResponse(token, new JsonParser().parse(responseBody).getAsJsonObject());
////			} catch (Exception e) {
////				return new ErrorResponse(token, responseCode, responseBody);	
////			}
		}

		if (update)
			return new UpdateResponse(token, responseBody);
		try {
			return new QueryResponse(token, new JsonParser().parse(responseBody).getAsJsonObject());
		} catch (Exception e) {
			return new ErrorResponse(token, HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/**
	 * Implements a SPARQL 1.1 update operation
	 * (https://www.w3.org/TR/sparql11-protocol/)
	 * 
	 * <pre>
	 * update via URL-encoded POST 
	 * - Method: <b>POST</b>
	 * - Query Parameters: None
	 * - Content Type: <b>application/x-www-form-urlencoded</b>
	 * - Body: URL-encoded, ampersand-separated query parameters. 
	 * 	<b>update</b> (exactly 1). 
	 * 	<b>using-graph-uri</b> (0 or more). 
	 * 	<b>using-named-graph-uri</b> (0 or more)
	 * 
	 * update via POST directly
	 * - Method: <b>POST</b>
	 * - Query Parameters: 
	 * 	<b>using-graph-uri</b> (0 or more); 
	 * 	<b>using-named-graph-uri</b> (0 or more)
	 * - Content Type: <b>application/sparql-update</b>
	 * - Body: Unencoded SPARQL update request string
	 * </pre>
	 * 
	 * 2.2.3 Specifying an RDF Dataset
	 * 
	 * <pre>
	 * SPARQL Update requests are executed against a Graph Store, a mutable
	 * container of RDF graphs managed by a SPARQL service. The WHERE clause of a
	 * SPARQL update DELETE/INSERT operation [UPDATE] matches against data in an RDF
	 * Dataset, which is a subset of the Graph Store. The RDF Dataset for an update
	 * operation may be specified either in the operation string itself using the
	 * USING, USING NAMED, and/or WITH keywords, or it may be specified via the
	 * using-graph-uri and using-named-graph-uri parameters.
	 * 
	 * It is an error to supply the using-graph-uri or using-named-graph-uri
	 * parameters when using this protocol to convey a SPARQL 1.1 Update request
	 * that contains an operation that uses the USING, USING NAMED, or WITH clause.
	 * 
	 * A SPARQL Update processor should treat each occurrence of the
	 * using-graph-uri=g parameter in an update protocol operation as if a USING <g>
	 * clause were included for every operation in the SPARQL 1.1 Update request.
	 * Similarly, a SPARQL Update processor should treat each occurrence of the
	 * using-named-graph-uri=g parameter in an update protocol operation as if a
	 * USING NAMED <g> clause were included for every operation in the SPARQL 1.1
	 * Update request.
	 * 
	 * UPDATE 2.2 update operation The response to an update request indicates
	 * success or failure of the request via HTTP response status code.
	 * </pre>
	 */
	// public Response update(UpdateRequest req, int timeout) {
	// return post(req, timeout, false);
	// }

	public Response update(UpdateRequest req, int timeout, HTTPMethod method) {
		switch (method) {
		case GET:
			// ***********************
			// OpenLink VIRTUOSO PATCH (not supported by SPARQL 1.1 Protocol)
			// ***********************
			return patchVirtuoso(req, timeout);
		case POST:
			return post(req, timeout, false);
		case URL_ENCODED_POST:
			return post(req, timeout, true);
		}

		return post(req, timeout, false);
	}

	private Response post(UpdateRequest req, int timeout, boolean urlEncoded) {
		StringEntity requestEntity = null;
		HttpPost post;
		String graphs = "";

		try {
			if (req.getUsingGraphUri() != null) {

				graphs += "using-graph-uri=" + URLEncoder.encode(req.getUsingGraphUri(), "UTF-8");

				if (req.getUsingNamedGraphUri() != null) {
					graphs += "&using-named-graph-uri=" + URLEncoder.encode(req.getUsingNamedGraphUri(), "UTF-8");
				}
			} else if (req.getUsingNamedGraphUri() != null) {
				graphs += "using-named-graph-uri=" + URLEncoder.encode(req.getUsingNamedGraphUri(), "UTF-8");
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		try {
			if (!urlEncoded)
				post = new HttpPost(new URI(scheme, null, host, port, updatePath, graphs, null));
			else
				post = new HttpPost(new URI(scheme, null, host, port, updatePath, null, null));
		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		post.setHeader("Accept", req.getAcceptHeader());
		if (!urlEncoded)
			post.setHeader("Content-Type", "application/sparql-update");
		else
			post.setHeader("Content-Type", "application/x-www-form-urlencoded");

		requestEntity = new StringEntity(req.getSPARQL(), Consts.UTF_8);
		post.setEntity(requestEntity);

		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
				.build();
		post.setConfig(requestConfig);

		return executeRequest(post, timeout, true, req.getToken());
	}

	private Response patchVirtuoso(UpdateRequest req, int timeout) {
		// 1) "INSERT DATA" is not supported. Only INSERT (also if the WHERE is not
		// present).
		String fixedSparql = req.getSPARQL();
		Pattern p = null;
		try {
			p = Pattern.compile(
					"(?<update>.*)(delete)([^{]*)(?<udtriples>.*)(insert)([^{]*)(?<uitriples>.*)|(?<delete>.*)(delete)([^{]*)(?<dtriples>.*)|(?<insert>.*)(insert)([^{]*)(?<itriples>.*)",
					Pattern.CASE_INSENSITIVE);

			Matcher m = p.matcher(req.getSPARQL());
			if (m.matches()) {
				if (m.group("update") != null) {
					fixedSparql = m.group("update") + " delete " + m.group("udtriples") + " insert "
							+ m.group("uitriples");
				} else if (m.group("insert") != null) {
					fixedSparql = m.group("insert") + " insert " + m.group("itriples");
				} else {
					fixedSparql = m.group("delete") + " delete " + m.group("dtriples");
				}
			}
		} catch (Exception e) {
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		// 2) SPARQL 1.1 Update are issued as GET request (like for a SPARQL 1.1 Query)
		String query;
		try {
			// custom "format" parameter
			query = "query=" + URLEncoder.encode(fixedSparql, "UTF-8") + "&format="
					+ URLEncoder.encode(req.getAcceptHeader(), "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			logger.error(e1.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e1.getMessage());
		}

		// 3) Named-graphs specified like a query
		String graphs = "";
		try {
			if (req.getUsingGraphUri() != null) {

				graphs += "default-graph-uri=" + URLEncoder.encode(req.getUsingGraphUri(), "UTF-8");

				if (req.getUsingNamedGraphUri() != null) {
					graphs += "&named-graph-uri=" + URLEncoder.encode(req.getUsingNamedGraphUri(), "UTF-8");
				}
			} else if (req.getUsingNamedGraphUri() != null) {
				graphs += "named-graph-uri=" + URLEncoder.encode(req.getUsingNamedGraphUri(), "UTF-8");
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		if (!graphs.equals(""))
			query += "&" + graphs;

		String url;
		if (port != -1)
			url = "http://" + host + ":" + port + queryPath + "?" + query;
		else
			url = "http://" + host + queryPath + "?" + query;

		HttpGet get;
		get = new HttpGet(url);

		get.setHeader("Accept", req.getAcceptHeader());

		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
				.build();
		get.setConfig(requestConfig);

		return executeRequest(get, timeout, false, req.getToken());
	}

	/**
	 * Implements a SPARQL 1.1 query operation
	 * (https://www.w3.org/TR/sparql11-protocol/)
	 * 
	 * <pre>
	 * query via GET
	 * - HTTP Method: GET
	 * - Query String Parameters: <b>query</b> (exactly 1). default-graph-uri (0 or more). named-graph-uri (0 or more)
	 * - Request Content Type: None
	 * - Request Message Body: None
	 * 
	 * query via URL-encoded POST 
	 * - HTTP Method: POST
	 * - Query String Parameters: None
	 * - Request Content Type: <b>application/x-www-form-urlencoded</b>
	 * - Request Message Body: URL-encoded, ampersand-separated query parameters. <b>query</b> (exactly 1). default-graph-uri (0 or more). named-graph-uri (0 or more)
	 * 
	 * query via POST directly
	 * - HTTP Method: POST
	 * - Query String parameters: default-graph-uri (0 or more). named-graph-uri (0 or more)
	 * - Request Content Type: <b>application/sparql-query</b>
	 * - Request Message Body: Unencoded SPARQL update request string
	 *
	 * 2.1.4 Specifying an RDF Dataset
	 * 
	 * A SPARQL query is executed against an RDF Dataset. The RDF Dataset for a query may be specified either 
	 * via the default-graph-uri and named-graph-uri parameters in the SPARQL Protocol or in the SPARQL query 
	 * string using the FROM and FROM NAMED keywords. If different RDF Datasets are specified in both the protocol 
	 * request and the SPARQL query string, then the SPARQL service must execute the query using the RDF Dataset 
	 * given in the protocol request.
	 * 
	 * Note that a service may reject a query with HTTP response code 400 if the service does not allow protocol 
	 * clients to specify the RDF Dataset. If an RDF Dataset is not specified in either the protocol request or 
	 * the SPARQL query string, then implementations may execute the query against an implementation-defined default RDF dataset.
	 * 
	 * QUERY 2.1.5 Accepted Response Formats
	 * 
	 * Protocol clients should use HTTP content negotiation [RFC2616] to request
	 * response formats that the client can consume. See below for more on
	 * potential response formats.
	 * 
	 * 2.1.6 Success Responses
	 * 
	 * The SPARQL Protocol uses the response status codes defined in HTTP to
	 * indicate the success or failure of an operation. Consult the HTTP
	 * specification [RFC2616] for detailed definitions of each status code.
	 * While a protocol service should use a 2XX HTTP response code for a
	 * successful query, it may choose instead to use a 3XX response code as per
	 * HTTP.
	 * 
	 * The response body of a successful query operation with a 2XX response is
	 * either:
	 * 
	 * a SPARQL Results Document in XML, JSON, or CSV/TSV format (for SPARQL
	 * Query forms SELECT and ASK); or, an RDF graph [RDF-CONCEPTS] serialized,
	 * for example, in the RDF/XML syntax [RDF-XML], or an equivalent RDF graph
	 * serialization, for SPARQL Query forms DESCRIBE and CONSTRUCT). The
	 * content type of the response to a successful query operation must be the
	 * media type defined for the format of the response body.
	 * 
	 * 2.1.7 Failure Responses
	 * 
	 * The HTTP response codes applicable to an unsuccessful query operation
	 * include:
	 * 
	 * 400 if the SPARQL query supplied in the request is not a legal sequence
	 * of characters in the language defined by the SPARQL grammar; or, 500 if
	 * the service fails to execute the query. SPARQL Protocol services may also
	 * return a 500 response code if they refuse to execute a query. This
	 * response does not indicate whether the server may or may not process a
	 * subsequent, identical request or requests. The response body of a failed
	 * query request is implementation defined. Implementations may use HTTP
	 * content negotiation to provide human-readable or machine-processable (or
	 * both) information about the failed query request.
	 * 
	 * A protocol service may use other 4XX or 5XX HTTP response codes for other
	 * failure conditions, as per HTTP.
	 *
	 * </pre>
	 */
	public Response query(QueryRequest req, int timeout) {
		return post(req, timeout, false);
	}

	public Response query(QueryRequest req, int timeout, HTTPMethod method) {
		switch (method) {
		case GET:
			return get(req, timeout);
		case POST:
			return post(req, timeout, false);
		case URL_ENCODED_POST:
			return post(req, timeout, true);
		}
		return post(req, timeout, false);
	}

	private Response post(QueryRequest req, int timeout, boolean urlEncoded) {
		StringEntity requestEntity = null;
		HttpPost post;
		String graphs = "";

		try {
			if (req.getDefaultGraphUri() != null) {

				graphs += "default-graph-uri=" + URLEncoder.encode(req.getDefaultGraphUri(), "UTF-8");

				if (req.getNamedGraphUri() != null) {
					graphs += "&using-named-graph-uri=" + URLEncoder.encode(req.getNamedGraphUri(), "UTF-8");
				}
			} else if (req.getNamedGraphUri() != null) {
				graphs += "named-graph-uri=" + URLEncoder.encode(req.getNamedGraphUri(), "UTF-8");
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		try {
			if (!urlEncoded)
				post = new HttpPost(new URI(scheme, null, host, port, queryPath, graphs, null));
			else
				post = new HttpPost(new URI(scheme, null, host, port, queryPath, null, null));
		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		post.setHeader("Accept", req.getAcceptHeader());
		if (!urlEncoded)
			post.setHeader("Content-Type", "application/sparql-query");
		else
			post.setHeader("Content-Type", "application/x-www-form-urlencoded");

		requestEntity = new StringEntity(req.getSPARQL(), Consts.UTF_8);
		post.setEntity(requestEntity);

		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
				.build();
		post.setConfig(requestConfig);

		return executeRequest(post, timeout, false, req.getToken());
	}

	private Response get(QueryRequest req, int timeout) {
		String query;
		try {
			query = "query=" + URLEncoder.encode(req.getSPARQL(), "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			logger.error(e1.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e1.getMessage());
		}

		String graphs = "";
		try {
			if (req.getDefaultGraphUri() != null) {

				graphs += "default-graph-uri=" + URLEncoder.encode(req.getDefaultGraphUri(), "UTF-8");

				if (req.getNamedGraphUri() != null) {
					graphs += "&named-graph-uri=" + URLEncoder.encode(req.getNamedGraphUri(), "UTF-8");
				}
			} else if (req.getNamedGraphUri() != null) {
				graphs += "named-graph-uri=" + URLEncoder.encode(req.getNamedGraphUri(), "UTF-8");
			}
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getMessage());
			return new ErrorResponse(req.getToken(), HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		}

		if (!graphs.equals(""))
			query += "&" + graphs;

		String url;
		if (port != -1)
			url = "http://" + host + ":" + port + queryPath + "?" + query;
		else
			url = "http://" + host + queryPath + "?" + query;

		HttpGet get;
		get = new HttpGet(url);

		get.setHeader("Accept", req.getAcceptHeader());

		RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout)
				.build();
		get.setConfig(requestConfig);
		
		return executeRequest(get, timeout, false, req.getToken());
	}

	@Override
	public void close() throws IOException {
		httpClient.close();
	}
}