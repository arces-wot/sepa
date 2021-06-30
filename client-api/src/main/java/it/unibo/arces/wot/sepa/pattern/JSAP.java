/* This class implements a JSON parser of an JSAP (JSON Application Profile) file
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

package it.unibo.arces.wot.sepa.pattern;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import it.unibo.arces.wot.sepa.api.SPARQL11SEProperties;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.Credentials;
import it.unibo.arces.wot.sepa.commons.security.OAuthProperties;
import it.unibo.arces.wot.sepa.commons.sparql.Bindings;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTerm;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermBNode;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermLiteral;
import it.unibo.arces.wot.sepa.commons.sparql.RDFTermURI;

/**
 * JSAP file example
 * 
 * <pre>
 * {
 "#include" : ["file:///test1.jsap","file://localhost/test2.jsap","file:/test3.jsap",...]
 "host" : "...",
 "sparql11protocol": {
 		"host":"...", 	(optional)
		"protocol": "http | https",
		"port": ...,					(optional)
		"query": {
			"path": "...",
			"method": "GET | POST | URL_ENCODED_POST",
			"format": "JSON | XML | CSV"
		},
		"update": {
			"path": "...",
			"method": "POST | URL_ENCODED_POST",
			"format": "JSON | HTML"
		}
	},
  	"sparql11seprotocol": {
  	    "host":"...", 	(optional)
		"protocol": "ws",
		"reconnect" : false, (optional)
		"availableProtocols": {
			"ws": {
			    "host":"...", 	(optional)
				"port": ...,
				"path": "..."
			},
			"wss": {
			     "host":"...", 	(optional)
				"port": ...,
				"path": "..."
			}
		}
	},
	"oauth": {
		"enable" : false,
		"register": "https://localhost:8443/oauth/register",
		"tokenRequest": "https://localhost:8443/oauth/token"
	},
	"graphs": {
		"default-graph-uri ": ["..."],
		"named-graph-uri": ["..."],
		"using-graph-uri": ["..."],
		"using-named-graph-uri": ["..."]
	},	
	"extended" :{<Application specific extended data>},
	"namespaces" : {
			"chat" : "http://wot.arces.unibo.it/chat#" ,
			"rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#"},
	"updates" : {
			"UPDATE_1" : {
				"sparql" : "..." ,
				"forcedBindings" : {
					"variable_1" : {
						"type" : "literal" ,
						"datatype": "xsd datatype" (optional. Default: xsd:string)
						"value" : "..."} (optional)
					 ,
					"variable_2" : {
						"type" : "uri",
						"value" : "..."}
					 ,
					"variable_N" : {
						"type" : "bNode",
						"value" : "..."
					}
				},
				"sparql11protocol" :{...} (optional)
				"authentication" : {...} (optional)
				"graphs": { (optional)
					"using-graph-uri": "...", (optional)
					"using-named-graph-uri": ..." (optional)
				},
			}
			 ,
			"UPDATE_N" : {
				"sparql" : "..."
			}
		}
		 ,
		"queries" : {
			"QUERY_1" : {
				"sparql" : "..." ,
				"forcedBindings" : {
					"variable_1" : {
						"type" : "literal" ,
						"datatype": "xsd datatype" (optional, default: xsd:string)
						"value" : "..."}	(optional)
					 ,
					"variable_2" : {
						"type" : "uri",
						"value" : "..."} (optional)
					 ,
					"variable_N" : {
						"type" : "bNode",
						"value" : "..."} (optional)
				},
				"sparql11protocol" :{...} (optional),
				"sparql11seprotocol" :{...} (optional)
				"authentication" : {...} (optional)
				"graphs": { (optional)
					"default-graph-uri ": "...", (optional)
					"named-graph-uri": "...", (optional)
				},
			}
			 ,
			"QUERY_N" : {
				"sparql" : "..."
			}
		}
		}}
 * </pre>
 */
public class JSAP extends SPARQL11SEProperties {
	protected static Set<String> numbersOrBoolean = new HashSet<String>();

	protected String prefixes = "";

	protected HashMap<String, String> namespaces = new HashMap<String, String>();

	protected OAuthProperties oauth = new OAuthProperties();

	public JSAP() {
		super();

		buildSPARQLPrefixes();
		
		jsap.add("extended", new JsonObject());
	}

	public JSAP(String propertiesFile) throws SEPAPropertiesException, SEPASecurityException {
		this(propertiesFile,false);
	}
	
	public JSAP(String propertiesFile,byte[] aes128) throws SEPAPropertiesException, SEPASecurityException {
		this(propertiesFile,false,aes128);
	}

	public JSAP(String propertiesFile, boolean validate,byte[] aes128) throws SEPAPropertiesException, SEPASecurityException {
		super(propertiesFile, validate);

		if (jsap.has("oauth"))
			oauth = new OAuthProperties(propertiesFile,aes128);

		if (jsap.has("#include"))
			loadIncluded(jsap, validate, propertiesFile);

		buildSPARQLPrefixes();
	}
	
	public JSAP(String propertiesFile, boolean validate) throws SEPAPropertiesException, SEPASecurityException {
		super(propertiesFile, validate);

		if (jsap.has("oauth"))
			oauth = new OAuthProperties(propertiesFile);

		if (jsap.has("#include"))
			loadIncluded(jsap, validate, propertiesFile);

		buildSPARQLPrefixes();
	}

	public void setClientCredentials(Credentials cred) throws SEPAPropertiesException, SEPASecurityException {
		if (cred == null) throw new SEPASecurityException("Credentials are null");
		oauth.setCredentials(cred.user(), cred.password());
	}
	
	private void loadIncluded(JsonObject jsap, boolean validate, String parentFile) throws SEPAPropertiesException, SEPASecurityException {
		File path = new File(parentFile);

		HashSet<String> files = new HashSet<>();
		for (JsonElement element : jsap.get("#include").getAsJsonArray())
			files.add(element.getAsString());
		jsap.remove("#include");

		for (String uri : files) includeJsap(uri, path.getParent(),validate);
		
		if (jsap.has("#include")) loadIncluded(jsap, validate, parentFile);		
	}

	private void includeJsap(String uri, String dir,boolean validate )
			throws SEPAPropertiesException, SEPASecurityException {
		if (uri.startsWith("file:/")) {
			loadFromFile(uri, dir,validate);
		} else {
			logger.warn("URI not supported: " + uri);
		}
	}

	private void loadFromFile(String uri,  String dir,boolean validate)
			throws SEPAPropertiesException, SEPASecurityException {
		String path;
		// String hostName;
		if (uri.charAt(6) != '/') {
			// file:/path
			path = uri.substring(6);
		} else if (uri.charAt(8) == '/') {
			// file:///path
			path = uri.substring(9);
		} else {
			// file://hostname/path
			// hostName = uri.substring(7, uri.indexOf('/', 7)+1);
			path = uri.substring(uri.indexOf('/', 7) + 1);
		}

		File file = new File(path);
		if (file.getParent() == null && dir != null)
			path = dir + File.separator + path;

		read(path, true, validate);
	}

	public void read(InputStream input,boolean replace,boolean validate) throws SEPAPropertiesException, SEPASecurityException {
		InputStreamReader in  = new InputStreamReader(input);
		
		JsonObject temp = new JsonParser().parse(in).getAsJsonObject();

		merge(temp, jsap, replace);

		// Validate the JSON elements
		if (validate)
			validate();

		// OAuth
		if (temp.has("oauth")) {
			oauth = new OAuthProperties(input);
		}

		buildSPARQLPrefixes();
	}
	
	public void read(InputStream filename)
			throws SEPAPropertiesException, SEPASecurityException {
		read(filename, true, false);
	}
	
	/**
	 * Parse the file and merge the content with the actual JSAP object. Primitive
	 * values are replaced if replace = true.
	 * 
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws SEPAPropertiesException
	 * @throws SEPASecurityException
	 */
	public void read(String filename, boolean replace, boolean validate)
			throws SEPAPropertiesException, SEPASecurityException {
		FileReader in;
		try {
			in = new FileReader(filename);
		} catch (IOException e) {
			throw new SEPAPropertiesException(e.getMessage());
		}
		
		JsonObject temp = new JsonParser().parse(in).getAsJsonObject();
		
		try {
			in.close();
		} catch (IOException e) {
			throw new SEPAPropertiesException(e.getMessage());
		}

		merge(temp, jsap, replace);

		// Validate the JSON elements
		if (validate)
			validate();

		// OAuth
		if (temp.has("oauth")) {
			oauth = new OAuthProperties(filename);
		}

		buildSPARQLPrefixes();
	}

	public void read(String filename, boolean replace)
			throws SEPAPropertiesException, SEPASecurityException {
		read(filename, replace, false);
	}

	public void read(String filename)
			throws SEPAPropertiesException, SEPASecurityException {
		read(filename, true, false);
	}

	public void write() throws SEPAPropertiesException {
		write(getFileName());
	}
	
	public void write(String fileName) throws SEPAPropertiesException {
		FileWriter out;
		try {
			out = new FileWriter(fileName);
			out.write(jsap.toString());
			out.close();
		} catch (IOException e) {
			if (logger.isTraceEnabled()) e.printStackTrace();
			throw new SEPAPropertiesException(e.getMessage());
		}	
	}
	
	private JsonObject merge(JsonObject temp, JsonObject jsap, boolean replace) {
		for (Entry<String, JsonElement> entry : temp.entrySet()) {
			JsonElement value = entry.getValue();
			String key = entry.getKey();

			if (!jsap.has(key)) {
				jsap.add(key, value);
				continue;
			}

			if (value.isJsonPrimitive()) {
				if (!replace)
					continue;
				jsap.add(key, value);
			} else if (value.isJsonObject()) {
				JsonObject obj = merge(value.getAsJsonObject(), jsap.getAsJsonObject(key), replace);
				jsap.add(key, obj);
			} else if (value.isJsonArray()) {
				for (JsonElement arr : value.getAsJsonArray()) {
					jsap.getAsJsonArray(key).add(arr);
				}
			}
		}

		return jsap;
	}

	private void defaultNamespaces() {
		// Numbers or boolean
		numbersOrBoolean.add("xsd:integer");
		numbersOrBoolean.add("xsd:decimal");
		numbersOrBoolean.add("xsd:double");
		numbersOrBoolean.add("xsd:boolean");

		numbersOrBoolean.add("http://www.w3.org/2001/XMLSchema#integer");
		numbersOrBoolean.add("http://www.w3.org/2001/XMLSchema#decimal");
		numbersOrBoolean.add("http://www.w3.org/2001/XMLSchema#double");
		numbersOrBoolean.add("http://www.w3.org/2001/XMLSchema#boolean");

		// Default namespaces
		namespaces.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		namespaces.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
		namespaces.put("owl", "http://www.w3.org/2002/07/owl#");
		namespaces.put("xsd", "http://www.w3.org/2001/XMLSchema#");
	}

	// TODO: add the query as parameter and get just the namespaces required
	public String getPrefixes() {
		return prefixes;
	}

	public HashMap<String, String> getNamespaces() {
		return namespaces;
	}

	private void buildSPARQLPrefixes() {
		defaultNamespaces();
		readNamespaces();

		prefixes = "";
		for (String prefix : namespaces.keySet()) {
			prefixes += "PREFIX " + prefix + ":<" + namespaces.get(prefix) + "> ";
		}
	}

	private void readNamespaces() {
		if (!jsap.has("namespaces"))
			return;

		try {
			for (Entry<String, JsonElement> ns : jsap.getAsJsonObject("namespaces").entrySet())
				namespaces.put(ns.getKey(), ns.getValue().getAsString());
		} catch (Exception e) {
			logger.error("getPrefixes exception: " + e.getMessage());
		}
	}

	protected static Logger logger = LogManager.getLogger();

	public OAuthProperties getAuthenticationProperties() {
		return oauth;
	}

	public boolean isSecure() {
		return oauth.isEnabled();
	}

	public boolean reconnect() {
		try {
			return jsap.getAsJsonObject("sparql11seprotocol").get("reconnect").getAsBoolean();
		} catch (Exception e) {
			logger.warn("sparql11seprotocol-reconnect not found. Default: false");
		}

		return false;
	}

	public JsonObject getExtendedData() {
		try {
			if (jsap.has("extended")) return jsap.getAsJsonObject("extended");
			
		} catch (Exception e) {
			logger.error("Extended data section not found");
		}
		
		return new JsonObject();
	}

	private JsonObject checkAndCreate(String id, boolean update) {
		if (update) {
			if (!jsap.has("updates"))
				jsap.add("updates", new JsonObject());
			if (!jsap.getAsJsonObject("updates").has(id))
				jsap.getAsJsonObject("updates").add(id, new JsonObject());
			if (!jsap.getAsJsonObject("updates").getAsJsonObject(id).has("sparql11protocol"))
				jsap.getAsJsonObject("updates").getAsJsonObject(id).add("sparql11protocol", new JsonObject());
			return jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol");
		} else {
			if (!jsap.has("queries"))
				jsap.add("queries", new JsonObject());
			if (!jsap.getAsJsonObject("queries").has(id))
				jsap.getAsJsonObject("queries").add(id, new JsonObject());
			if (!jsap.getAsJsonObject("queries").getAsJsonObject(id).has("sparql11protocol"))
				jsap.getAsJsonObject("queries").getAsJsonObject(id).add("sparql11protocol", new JsonObject());

			return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11protocol");
		}
	}

	/*
	 * UPDATE
	 */
	public String getSPARQLUpdate(String id) {
		try {
			return jsap.getAsJsonObject("updates").getAsJsonObject(id).get("sparql").getAsString();
		} catch (Exception e) {
			logger.error("SPARQL Update " + id + "  not found");
		}
		return null;
	}

	public String getUpdateHost(String id) {
		try {
			return jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol").get("host")
					.getAsString();
		} catch (Exception e) {
			try {
				return jsap.get("updates").getAsJsonObject().get(id).getAsJsonObject().get("host").getAsString();
			} catch (Exception e1) {

			}
		}

		return super.getSubscribeHost();
	}

	public String getUpdateAcceptHeader(String id) {
		try {
			if (jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.getAsJsonObject("update").get("format").getAsString().equals("JSON"))
				return "application/json";
			else
				return "application/html";
		} catch (Exception e) {

		}

		return super.getUpdateAcceptHeader();
	}

	public UpdateHTTPMethod getUpdateMethod(String id) {
		try {
			switch (jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.getAsJsonObject("update").get("method").getAsString()) {
			case "URL_ENCODED_POST":
				return UpdateHTTPMethod.URL_ENCODED_POST;
			case "POST":
				return UpdateHTTPMethod.POST;
//			case "GET":
//				// Virtuoso PATCH
//				return HTTPMethod.GET;
			}
		} catch (Exception e) {
		}

		return super.getUpdateMethod();
	}

	public String getUpdateProtocolScheme(String id) {
		try {
			return jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.get("protocol").getAsString();
		} catch (Exception e) {
		}

		return super.getProtocolScheme();
	}

	public String getUpdatePath(String id) {
		try {
			return jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.getAsJsonObject("update").get("path").getAsString();
		} catch (Exception e) {
		}

		return super.getUpdatePath();
	}

	public int getUpdatePort(String id) {
		try {
			return jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("sparql11protocol").get("port")
					.getAsInt();
		} catch (Exception e) {
		}

		return super.getPort();
	}

	public Set<String> getUsingGraphURI(String id) {
		try {
			JsonArray array = jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("graphs")
					.get("using-graph-uri").getAsJsonArray();
			HashSet<String> ret = new HashSet<>();
			for (JsonElement e : array) ret.add(e.getAsString());
			return ret;
		} catch (Exception e) {
		}

		return super.getUsingGraphURI();
	}

	public Set<String> getUsingNamedGraphURI(String id) {
		try {
			JsonArray array = jsap.getAsJsonObject("updates").getAsJsonObject(id).getAsJsonObject("graphs")
					.get("using-named-graph-uri").getAsJsonArray();
			HashSet<String> ret = new HashSet<>();
			for (JsonElement e : array) ret.add(e.getAsString());
			return ret;
		} catch (Exception e) {
		}

		return super.getUsingNamedGraphURI();
	}

	public void setSPARQLUpdate(String id, String sparql) {
		if (!jsap.has("updates"))
			jsap.add("updates", new JsonObject());
		jsap.getAsJsonObject("updates").add(id, new JsonObject());
		jsap.getAsJsonObject("updates").getAsJsonObject(id).add("sparql", new JsonPrimitive(sparql));
	}

	public void setUpdateHost(String id, String host) {
		JsonObject prop = checkAndCreate(id, true);
		prop.add("host", new JsonPrimitive(host));
	}

	public void setUpdateAcceptHeader(String id, UpdateResultsFormat format) {
		JsonObject prop = checkAndCreate(id, true);

		if (!prop.has("update"))
			prop.add("update", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("update");

		switch (format) {
		case JSON:
			temp.add("format", new JsonPrimitive("application/json"));
			break;
		case HTML:
			temp.add("format", new JsonPrimitive("application/html"));
			break;
		}
	}

	public void setUpdateMethod(String id, UpdateHTTPMethod method) {
		JsonObject prop = checkAndCreate(id, true);

		if (!prop.has("update"))
			prop.add("update", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("update");

		switch (method) {
//		case GET:
//			temp.add("method", new JsonPrimitive("GET"));
//			break;
		case POST:
			temp.add("method", new JsonPrimitive("POST"));
			break;
		case URL_ENCODED_POST:
			temp.add("method", new JsonPrimitive("URL_ENCODED_POST"));
			break;
		}
	}

	public void setUpdateProtocolScheme(String id, ProtocolScheme scheme) {
		JsonObject prop = checkAndCreate(id, true);

		switch (scheme) {
		case HTTP:
			prop.add("protocol", new JsonPrimitive("http"));
			break;
		case HTTPS:
			prop.add("protocol", new JsonPrimitive("https"));
			break;
		}
	}

	public void setUpdatePath(String id, String path) {
		JsonObject prop = checkAndCreate(id, true);

		if (!prop.has("update"))
			prop.add("update", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("update");

		temp.add("path", new JsonPrimitive(path));
	}

	public void setUpdatePort(String id, int port) {
		JsonObject prop = checkAndCreate(id, true);

		if (!prop.has("update"))
			prop.add("update", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("update");

		temp.add("port", new JsonPrimitive(port));
	}

	public void setUsingNamedGraphUri(String id, Set<String> graph) {
		if (!jsap.has("updates"))
			jsap.add("updates", new JsonObject());
		if (!jsap.getAsJsonObject("updates").has("graphs"))
			jsap.getAsJsonObject("updates").add("graphs", new JsonObject());
		
		JsonArray array = new JsonArray();
		for(String s: graph) array.add(s);
		jsap.getAsJsonObject("updates").getAsJsonObject("graphs").add("using-named-graph-uri",array);
	}

	public void setUsingGraphURI(String id, Set<String> graph) {
		if (!jsap.has("updates"))
			jsap.add("updates", new JsonObject());
		if (!jsap.getAsJsonObject("updates").has("graphs"))
			jsap.getAsJsonObject("updates").add("graphs", new JsonObject());
		
		JsonArray array = new JsonArray();
		for(String s: graph) array.add(s);
		jsap.getAsJsonObject("updates").getAsJsonObject("graphs").add("using-graph-uri", array);
	}

	/*
	 * QUERY
	 */
	public String getSPARQLQuery(String id) {
		try {
			return jsap.getAsJsonObject("queries").getAsJsonObject(id).get("sparql").getAsString();
		} catch (Exception e) {
			logger.fatal("SPARQL query " + id + " not found");
		}
		return null;
	}

	public String getQueryHost(String id) {
		try {
			return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11protocol").get("host")
					.getAsString();
		} catch (Exception e) {
			try {
				return jsap.getAsJsonObject("queries").getAsJsonObject(id).get("host").getAsString();
			} catch (Exception e1) {

			}
		}

		return super.getSubscribeHost();
	}

	public String getQueryProtocolScheme(String id) {
		try {
			return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.get("protocol").getAsString();
		} catch (Exception e) {
		}

		return super.getProtocolScheme();
	}

	public int getQueryPort(String id) {
		try {
			return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11protocol").get("port")
					.getAsInt();
		} catch (Exception e) {
		}

		return super.getPort();
	}

	public String getQueryPath(String id) {
		try {
			return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.getAsJsonObject("query").get("path").getAsString();
		} catch (Exception e) {
		}

		return super.getQueryPath();
	}

	public QueryHTTPMethod getQueryMethod(String id) {
		try {
			switch (jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11protocol")
					.getAsJsonObject("query").get("method").getAsString()) {
			case "URL_ENCODED_POST":
				return QueryHTTPMethod.URL_ENCODED_POST;
			case "POST":
				return QueryHTTPMethod.POST;
			case "GET":
				return QueryHTTPMethod.GET;
			}
		} catch (Exception e) {
			
		}

		return super.getQueryMethod();
	}

	public String getQueryAcceptHeader(String id) {
		try {
			switch (jsap.getAsJsonObject("queries").getAsJsonObject(id).get("format").getAsString()) {
			case "JSON":
				return "application/sparql-results+json";
			case "XML":
				return "application/sparql-results+xml";
			case "CSV":
				return "text/csv";
			default:
				return "application/sparql-results+json";
			}
		} catch (Exception e) {

		}

		return super.getQueryAcceptHeader();
	}

	public Set<String> getNamedGraphURI(String id) {
		try {
			JsonArray array = jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("graphs")
					.get("named-graph-uri").getAsJsonArray();
			HashSet<String> ret = new HashSet<>();
			for (JsonElement e : array) ret.add(e.getAsString());
			return ret;
		} catch (Exception e) {
		}

		return super.getNamedGraphURI();
	}

	public Set<String> getDefaultGraphURI(String id) {
		try {
			JsonArray array = jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("graphs")
					.get("default-graph-uri").getAsJsonArray();
			HashSet<String> ret = new HashSet<>();
			for (JsonElement e : array) ret.add(e.getAsString());
			return ret;
		} catch (Exception e) {
		}

		return super.getDefaultGraphURI();
	}

	public void setSPARQLQuery(String id, String sparql) {
		if (!jsap.has("queries"))
			jsap.add("queries", new JsonObject());
		jsap.getAsJsonObject("queries").add(id, new JsonObject());
		jsap.getAsJsonObject("queries").getAsJsonObject(id).add("sparql", new JsonPrimitive(sparql));
	}

	public void setQueryHost(String id, String host) {
		JsonObject prop = checkAndCreate(id, false);
		prop.add("host", new JsonPrimitive(host));
	}

	public void setQueryAcceptHeader(String id, QueryResultsFormat format) {
		JsonObject prop = checkAndCreate(id, true);

		if (!prop.has("query"))
			prop.add("query", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("query");

		switch (format) {
		case JSON:
			temp.add("format", new JsonPrimitive("application/sparql-results+json"));
			break;
		case XML:
			temp.add("format", new JsonPrimitive("application/sparql-results+xml"));
		case CSV:
			temp.add("format", new JsonPrimitive("text/csv"));
			break;
		}
	}

	public void setQueryMethod(String id, QueryHTTPMethod method) {
		JsonObject prop = checkAndCreate(id, false);

		if (!prop.has("query"))
			prop.add("query", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("query");

		switch (method) {
		case GET:
			temp.add("method", new JsonPrimitive("GET"));
			break;
		case POST:
			temp.add("method", new JsonPrimitive("POST"));
			break;
		case URL_ENCODED_POST:
			temp.add("method", new JsonPrimitive("URL_ENCODED_POST"));
			break;
		}
	}

	public void setQueryProtocolScheme(String id, ProtocolScheme scheme) {
		JsonObject prop = checkAndCreate(id, false);

		switch (scheme) {
		case HTTP:
			prop.add("protocol", new JsonPrimitive("http"));
			break;
		case HTTPS:
			prop.add("protocol", new JsonPrimitive("https"));
			break;
		}
	}

	public void setQueryPath(String id, String path) {
		JsonObject prop = checkAndCreate(id, false);

		if (!prop.has("query"))
			prop.add("query", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("query");

		temp.add("path", new JsonPrimitive(path));
	}

	public void setQueryPort(String id, int port) {
		JsonObject prop = checkAndCreate(id, false);

		if (!prop.has("query"))
			prop.add("query", new JsonObject());
		JsonObject temp = prop.getAsJsonObject("query");

		temp.add("port", new JsonPrimitive(port));
	}

	public void setNamedGraphUri(String id, Set<String> graph) {
		if (!jsap.has("queries"))
			jsap.add("queries", new JsonObject());
		if (!jsap.getAsJsonObject("queries").has("graphs"))
			jsap.getAsJsonObject("queries").add("graphs", new JsonObject());
		
		JsonArray array = new JsonArray();
		for(String s: graph) array.add(s);
		jsap.getAsJsonObject("queries").getAsJsonObject("graphs").add("named-graph-uri", array);
	}

	public void setDefaultGraphURI(String id, Set<String> graph) {
		if (!jsap.has("queries"))
			jsap.add("queries", new JsonObject());
		if (!jsap.getAsJsonObject("queries").has("graphs"))
			jsap.getAsJsonObject("queries").add("graphs", new JsonObject());
		
		JsonArray array = new JsonArray();
		for(String s: graph) array.add(s);
		jsap.getAsJsonObject("queries").getAsJsonObject("graphs").add("default-graph-uri", array);
	}

	/*
	 * SUBSCRIBE
	 */

	public JsonObject getSubscriptionProperties(String id) {
		SubscriptionProtocol sp = getSubscribeProtocol(id);
		String protocol = "ws";
		switch (sp) {
		case WS:
			protocol = "ws";
			break;
		case WSS:
			protocol = "wss";
			break;
		}

		if (!jsap.has("queries")) {
			jsap.add("queries", new JsonObject());
			jsap.getAsJsonObject("queries").add(id, new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).add("sparql11seprotocol", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.add("availableProtocols", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.getAsJsonObject("availableProtocols").add(protocol, new JsonObject());
		} else if (!jsap.getAsJsonObject("queries").has(id)) {
			jsap.getAsJsonObject("queries").add(id, new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).add("sparql11seprotocol", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.add("availableProtocols", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.getAsJsonObject("availableProtocols").add(protocol, new JsonObject());
		} else if (!jsap.getAsJsonObject("queries").getAsJsonObject(id).has("sparql11seprotocol")) {
			jsap.getAsJsonObject("queries").getAsJsonObject(id).add("sparql11seprotocol", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.add("availableProtocols", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.getAsJsonObject("availableProtocols").add(protocol, new JsonObject());
		} else if (!jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
				.has("availableProtocols")) {
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.add("availableProtocols", new JsonObject());
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.getAsJsonObject("availableProtocols").add(protocol, new JsonObject());
		} else if (!jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
				.getAsJsonObject("availableProtocols").has(protocol)) {
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.getAsJsonObject("availableProtocols").add(protocol, new JsonObject());
		}

		return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
				.getAsJsonObject("availableProtocols").getAsJsonObject(protocol);
	}

	public String getSubscribeHost(String id) {
		String protocol = null;
		try {
			protocol = jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.get("protocol").getAsString();
		} catch (Exception e) {
			try {
				return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
						.get("host").getAsString();
			} catch (Exception e1) {
				return super.getSubscribeHost();
			}
		}

		try {
			return jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.getAsJsonObject("availableProtocols").getAsJsonObject(protocol).get("host").getAsString();
		} catch (Exception e) {

		}

		return super.getSubscribeHost();
	}

	public void setSubscribeHost(String id, String host) {
		JsonObject prop = getSubscriptionProperties(id);

		prop.add("host", new JsonPrimitive(host));
	}

	public int getSubscribePort(String id) {
		try {
			return jsap.get("queries").getAsJsonObject().get(id).getAsJsonObject().get("sparql11seprotocol")
					.getAsJsonObject().get("availableProtocols").getAsJsonObject()
					.get(jsap.get("queries").getAsJsonObject().get(id).getAsJsonObject().get("sparql11seprotocol")
							.getAsJsonObject().get("protocol").getAsString())
					.getAsJsonObject().get("port").getAsInt();
		} catch (Exception e) {
		}

		return super.getSubscribePort();
	}

	public void setSubscribePort(String id, int port) {
		JsonObject prop = getSubscriptionProperties(id);

		prop.add("port", new JsonPrimitive(port));
	}

	public String getSubscribePath(String id) {
		try {
			return jsap.get("queries").getAsJsonObject().get(id).getAsJsonObject().get("sparql11seprotocol")
					.getAsJsonObject().get("availableProtocols").getAsJsonObject()
					.get(jsap.get("queries").getAsJsonObject().get(id).getAsJsonObject().get("sparql11seprotocol")
							.getAsJsonObject().get("protocol").getAsString())
					.getAsJsonObject().get("path").getAsString();
		} catch (Exception e) {
		}

		return super.getSubscribePath();
	}

	public void setSubscribePath(String id, String path) {
		JsonObject prop = getSubscriptionProperties(id);

		prop.add("path", new JsonPrimitive(path));
	}

	public SubscriptionProtocol getSubscribeProtocol(String id) {
		try {
			if (jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.get("protocol").getAsString().equals("ws"))
				return SubscriptionProtocol.WS;

			if (jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol")
					.get("protocol").getAsString().equals("wss"))
				return SubscriptionProtocol.WSS;
		} catch (Exception e1) {
		}

		return super.getSubscriptionProtocol();
	}

	public void setSubscribeProtocol(String id, SubscriptionProtocol sp) {
		switch (sp) {
		case WS:
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol").add("protocol",
					new JsonPrimitive("ws"));
			break;
		case WSS:
			jsap.getAsJsonObject("queries").getAsJsonObject(id).getAsJsonObject("sparql11seprotocol").add("protocol",
					new JsonPrimitive("wss"));
			break;
		}
	}

	public Set<String> getUpdateIds() {
		HashSet<String> ret = new HashSet<String>();

		if (!jsap.has("updates"))
			return ret;

		try {
			for (Entry<String, JsonElement> key : jsap.getAsJsonObject("updates").entrySet()) {
				ret.add(key.getKey());
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}

		return ret;
	}

	public Set<String> getQueryIds() {
		HashSet<String> ret = new HashSet<String>();

		if (!jsap.has("queries"))
			return ret;

		try {
			for (Entry<String, JsonElement> key : jsap.getAsJsonObject("queries").entrySet()) {
				ret.add(key.getKey());
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}

		return ret;
	}

	/**
	 * <pre>
	 * "forcedBindings" : {
					"variable_1" : {
						"type" : "literal",
						"datatype" : "xsd:short"}
					 ,
					"variable_2" : {
						"type" : "uri"}
					 ,
					 "variable_3" : {
					 	"type": "literal",
					 	"datatype" : "xsd:string",
					 	"language" : "it"},
					 ...
					"variable_N" : {
						"type" : "literal",
						"datatype" : "xsd:dateTime" ,
						"value" : "1985-08-03T01:02:03Z"}
				}
	 * </pre>
	 */
	public ForcedBindings getUpdateBindings(String id) throws IllegalArgumentException {
		if (!jsap.get("updates").getAsJsonObject().has(id))
			throw new IllegalArgumentException("Update ID not found: " + id);

		ForcedBindings ret = new ForcedBindings();

		if (!jsap.getAsJsonObject("updates").getAsJsonObject(id).has("forcedBindings"))
			return ret;

		try {
			for (Entry<String, JsonElement> binding : jsap.getAsJsonObject("updates").getAsJsonObject(id)
					.getAsJsonObject("forcedBindings").entrySet()) {

				if (!binding.getValue().getAsJsonObject().has("type")) {
					logger.error("JSAP missing binding type: " + binding);
					continue;
				}

				RDFTerm bindingValue = null;
				String value = null;
				if (binding.getValue().getAsJsonObject().has("value"))
					value = binding.getValue().getAsJsonObject().get("value").getAsString();

				switch (binding.getValue().getAsJsonObject().get("type").getAsString()) {
				case "literal":
					String datatype = null;
					if (binding.getValue().getAsJsonObject().has("datatype"))
						datatype = binding.getValue().getAsJsonObject().get("datatype").getAsString();

					String language = null;
					if (binding.getValue().getAsJsonObject().has("language"))
						language = binding.getValue().getAsJsonObject().get("language").getAsString();

					bindingValue = new RDFTermLiteral(value, datatype, language);
					break;
				case "uri":
					bindingValue = new RDFTermURI(value);
					break;
				case "bnode":
					bindingValue = new RDFTermBNode(value);
					break;
				default:
					logger.error("JSAP unknown type: " + binding);
					continue;
				}

				ret.addBinding(binding.getKey(), bindingValue);
			}
		} catch (Exception e) {
			logger.error("getUpdateBindings " + id + " exception: " + e.getMessage());
		}

		return ret;
	}

	public ForcedBindings getQueryBindings(String id) throws IllegalArgumentException {
		if (!jsap.get("queries").getAsJsonObject().has(id))
			throw new IllegalArgumentException("Query ID not found: "+id);

		ForcedBindings ret = new ForcedBindings();

		if (!jsap.getAsJsonObject("queries").getAsJsonObject(id).has("forcedBindings"))
			return ret;

		try {
			for (Entry<String, JsonElement> binding : jsap.getAsJsonObject("queries").getAsJsonObject(id)
					.getAsJsonObject("forcedBindings").entrySet()) {

				RDFTerm bindingValue = null;
				String value = null;
				if (binding.getValue().getAsJsonObject().has("value"))
					value = binding.getValue().getAsJsonObject().get("value").getAsString();

				switch (binding.getValue().getAsJsonObject().get("type").getAsString()) {
				case "literal":
					String datatype = null;
					if (binding.getValue().getAsJsonObject().has("datatype"))
						datatype = binding.getValue().getAsJsonObject().get("datatype").getAsString();

					String language = null;
					if (binding.getValue().getAsJsonObject().has("language"))
						language = binding.getValue().getAsJsonObject().get("language").getAsString();

					bindingValue = new RDFTermLiteral(value, datatype, language);
					break;
				case "uri":
					bindingValue = new RDFTermURI(value);
					break;
				case "bnode":
					bindingValue = new RDFTermBNode(value);
					break;
				default:
					logger.error("JSAP unknown type: " + binding);
					continue;
				}

				ret.addBinding(binding.getKey(), bindingValue);
			}
		} catch (Exception e) {
			logger.error("getQueryBindings " + id + " exception: " + e.getMessage());
		}

		return ret;
	}

	public String getFileName() {
		return propertiesFile;
	}

	public String printParameters() {
		return jsap.toString();
	}

	public String getUpdateUrl(String id) {
		String port = "";
		if (getUpdatePort(id) != -1)
			port = ":" + getUpdatePort(id);
		return getUpdateProtocolScheme(id) + "://" + getUpdateHost(id) + port + getUpdatePath(id);
	}

	public String getQueryUrl(String id) {
		String port = "";
		if (getQueryPort(id) != -1)
			port = ":" + getQueryPort(id);
		return getQueryProtocolScheme(id) + "://" + getQueryHost(id) + port + getQueryPath(id);
	}

	public String getSubscribeUrl(String id) {
		String scheme = "";
		String port = "";

		SubscriptionProtocol prot = getSubscribeProtocol(id);

		if (prot.equals(SubscriptionProtocol.WS))
			scheme = "ws";
		else if (prot.equals(SubscriptionProtocol.WSS))
			scheme = "wss";

		if (getSubscribePort(id) != -1)
			port = ":" + getSubscribePort(id);

		return scheme + "://" + getSubscribeHost(id) + port + getSubscribePath();
	}

	/*
	 * The grammar of SPARQL 1.1 Update is specified here:
	 * https://www.w3.org/TR/sparql11-query/#rUpdate
	 * 
	 * A request MAY include multiple primitives separated by ";" (see Update [29])
	 * 
	 * 
	 * [29] Update ::= Prologue ( Update1 ( ';' Update )? )?
	 */

	public String addPrefixesAndReplaceBindings(String sparql, Bindings bindings) throws SEPABindingsException {
		return prefixes + replaceBindings(sparql, bindings);
	}

	// TODO: use Jena?
//	private final String replaceBindings(String sparql, Bindings bindings) throws SEPABindingsException {
//		QuerySolutionMap initialBinding = new QuerySolutionMap();
//		for (String var : bindings.getVariables()) {
//			if (bindings.getRDFTerm(var).isLiteral()) {
//				RDFTermLiteral literal = (RDFTermLiteral) bindings.getRDFTerm(var);
//				String dataType = literal.getDatatype();
//				String lan = literal.getLanguageTag();
//				String value = literal.getValue();
//				if (dataType == null && lan == null) initialBinding.add(var, ResourceFactory.createPlainLiteral(value));
//				else if (dataType != null) initialBinding.add(var, ResourceFactory.createTypedLiteral(value, new XSDPlainType(dataType) ));
//				else if (lan != null) initialBinding.add(var, ResourceFactory.createLangLiteral(value, lan));
//			}
//			else initialBinding.add(var, ResourceFactory.createResource(bindings.getValue(var)));
//
//		}
//		QueryExecution qe = QueryExecutionFactory.create(sparql, initialBinding);
//		return qe.getQuery().toString();
//		
//	}

	public static final String replaceBindings(String sparql, Bindings bindings) throws SEPABindingsException {
		if (bindings == null || sparql == null)
			return sparql;

		String replacedSparql = String.format("%s", sparql);
//		String selectPattern = "";
//
//		if (sparql.toUpperCase().contains("SELECT")) {
//			selectPattern = replacedSparql.substring(0, sparql.indexOf('{'));
//			replacedSparql = replacedSparql.substring(sparql.indexOf('{'), replacedSparql.length());
//		}

		for (String var : bindings.getVariables()) {
			String value = bindings.getValue(var);
			if (value == null)
				continue;

			/*
			 * 4.1.2 Syntax for Literals
			 * 
			 * The general syntax for literals is a string (enclosed in either double
			 * quotes, "...", or single quotes, '...'), with either an optional language tag
			 * (introduced by @) or an optional datatype IRI or prefixed name (introduced by
			 * ^^).
			 * 
			 * As a convenience, integers can be written directly (without quotation marks
			 * and an explicit datatype IRI) and are interpreted as typed literals of
			 * datatype xsd:integer; decimal numbers for which there is '.' in the number
			 * but no exponent are interpreted as xsd:decimal; and numbers with exponents
			 * are interpreted as xsd:double. Values of type xsd:boolean can also be written
			 * as true or false.
			 * 
			 * To facilitate writing literal values which themselves contain quotation marks
			 * or which are long and contain newline characters, SPARQL provides an
			 * additional quoting construct in which literals are enclosed in three single-
			 * or double-quotation marks.
			 * 
			 * Examples of literal syntax in SPARQL include:
			 * 
			 * - "chat" 
			 * - 'chat'@fr with language tag "fr" 
			 * - "xyz"^^<http://example.org/ns/userDatatype> 
			 * - "abc"^^appNS:appDataType 
			 * - '''The librarian said, "Perhaps you would enjoy 'War and Peace'."''' 
			 * - 1, which is the same as "1"^^xsd:integer 
			 * - 1.3, which is the same as "1.3"^^xsd:decimal 
			 * - 1.300, which is the same as "1.300"^^xsd:decimal 
			 * - 1.0e6, which is the same as "1.0e6"^^xsd:double 
			 * - true, which is the same as "true"^^xsd:boolean 
			 * - false, which is the same as "false"^^xsd:boolean
			 * 
			 * Tokens matching the productions INTEGER, DECIMAL, DOUBLE and BooleanLiteral
			 * are equivalent to a typed literal with the lexical value of the token and the
			 * corresponding datatype (xsd:integer, xsd:decimal, xsd:double, xsd:boolean).
			 */

			if (bindings.isLiteral(var)) {
				String datatype = bindings.getDatatype(var);
				String lang = bindings.getLanguage(var);

				if (datatype == null) {
					if (lang != null)
						value += "@" + bindings.getLanguage(var);
					else {
						value = "'''" + StringEscapeUtils.escapeJava(value) + "'''";
					}
				} else if (!numbersOrBoolean.contains(datatype)) {
					// Check if datatype is a qname or not
					URI uri = null;
					try {
						uri = new URI(datatype);
					} catch (URISyntaxException e) {
						logger.error(e.getMessage());
					}

					if (uri != null) {
						if (uri.getSchemeSpecificPart().startsWith("/"))
							datatype = "<" + datatype + ">";
					}

					value = "'''" + StringEscapeUtils.escapeJava(value) + "'''";
					value += "^^" + datatype;
				}
			} else if (bindings.isURI(var)) {
				// See https://www.w3.org/TR/rdf-sparql-query/#QSynIRI
				// https://docs.oracle.com/javase/7/docs/api/java/net/URI.html

				// [scheme:]scheme-specific-part[#fragment]
				// An absolute URI specifies a scheme; a URI that is not absolute is said to be
				// relative.
				// URIs are also classified according to whether they are opaque or
				// hierarchical.

				// An opaque URI is an absolute URI whose scheme-specific part does not begin
				// with a slash character ('/').
				// Opaque URIs are not subject to further parsing.

				// A hierarchical URI is either an absolute URI whose scheme-specific part
				// begins with a slash character,
				// or a relative URI, that is, a URI that does not specify a scheme.
				// A hierarchical URI is subject to further parsing according to the syntax
				// [scheme:][//authority][path][?query][#fragment]

				URI uri = null;
				try {
					uri = new URI(value);
				} catch (URISyntaxException e) {
					logger.error(e.getMessage());
				}

				if (uri != null) {
					if (uri.getSchemeSpecificPart().startsWith("/") || uri.getScheme().equals("urn"))
						value = "<" + value + ">";
				}
			} else {
				// A blank node
				logger.trace("Blank node: " + value);
				
				// Not a BLANK_NODE_LABEL
				// [142]  	BLANK_NODE_LABEL	  ::=  	'_:' ( PN_CHARS_U | [0-9] ) ((PN_CHARS|'.')* PN_CHARS)?
				if (!value.startsWith("_:")) value = "<" + value + ">";
			}
			// Matching variables
			/*
			 * [108] Var ::= VAR1 | VAR2 [143] VAR1 ::= '?' VARNAME [144] VAR2 ::= '$'
			 * VARNAME [164] PN_CHARS_BASE ::= [A-Z] | [a-z] | [#x00C0-#x00D6] |
			 * [#x00D8-#x00F6] | [#x00F8-#x02FF] | [#x0370-#x037D] | [#x037F-#x1FFF] |
			 * [#x200C-#x200D] | [#x2070-#x218F] | [#x2C00-#x2FEF] | [#x3001-#xD7FF] |
			 * [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF] [165] PN_CHARS_U ::=
			 * PN_CHARS_BASE | '_' [166] VARNAME ::= ( PN_CHARS_U | [0-9] ) ( PN_CHARS_U |
			 * [0-9] | #x00B7 | [#x0300-#x036F] | [#x203F-#x2040] )*
			 */
			int start = 0;
			while (start != -1) {
				int index = replacedSparql.indexOf("?" + var, start);
				if (index == -1)
					index = replacedSparql.indexOf("$" + var, start);
				if (index != -1) {
					start = index + 1;
					if (index + var.length() + 1 <= replacedSparql.length() - 1) {
						int unicode = replacedSparql.codePointAt(index + var.length() + 1);
						if (!isValidVarChar(unicode)) {
							replacedSparql = replacedSparql.substring(0, index) + value
									+ replacedSparql.substring(index + var.length() + 1);
						}
					}
					// END OF STRING
					else {
						replacedSparql = replacedSparql.substring(0, index) + value;
					}

				} else
					start = index;
			}

//			selectPattern = selectPattern.replace(" ?" + var + " ", "");
		}

		return replacedSparql;
//		return selectPattern + replacedSparql;
	}

	private static boolean isValidVarChar(int c) {
		return ((c == '_') || (c == 0x00B7) || (0x0300 <= c && c <= 0x036F) || (0x203F <= c && c <= 0x2040)
				|| ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9')
				|| (0x00C0 <= c && c <= 0x00D6) || (0x00D8 <= c && c <= 0x00F6) || (0x00F8 <= c && c <= 0x02FF)
				|| (0x0370 <= c && c <= 0x037D) || (0x037F <= c && c <= 0x1FFF) || (0x200C <= c && c <= 0x200D)
				|| (0x2070 <= c && c <= 0x218F) || (0x2C00 <= c && c <= 0x2FEF) || (0x3001 <= c && c <= 0xD7FF)
				|| (0xF900 <= c && c <= 0xFDCF) || (0xFDF0 <= c && c <= 0xFFFD) || (0x10000 <= c && c <= 0xEFFFF));
	}
	
	public void setAutoReconnect(boolean b) {
		if (jsap.has("sparql11seprotocol")) {
			jsap.getAsJsonObject("sparql11seprotocol").add("reconnect", new JsonPrimitive(b));
		}
	}
	
}
