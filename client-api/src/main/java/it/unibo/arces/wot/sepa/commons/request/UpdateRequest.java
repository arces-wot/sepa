/* This class represents an update request
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

package it.unibo.arces.wot.sepa.commons.request;

import java.util.Set;

import it.unibo.arces.wot.sepa.commons.protocol.SPARQL11Properties.UpdateHTTPMethod;

/**
 * This class represents the request to perform a SPARQL 1.1 Update
* */

public class UpdateRequest extends SPARQL11Request {
	 /* It is an error to supply the using-graph-uri or using-named-graph-uri
	 * parameters when using this protocol to convey a SPARQL 1.1 Update request
	 * that contains an operation that uses the USING, USING NAMED, or WITH clause.
	 */
	
	private boolean urlEncodedGraphsPatch = false;
	
	private UpdateHTTPMethod method;
	
	public UpdateRequest(UpdateHTTPMethod method,String scheme,String host, int port, String path,String sparql,Set<String> default_graph_uri,Set<String> named_graph_uri,String authorization,long timeout,long nRetry) {
		super(sparql,authorization,default_graph_uri,named_graph_uri,timeout,nRetry);
		
		this.method = method;
		this.host = host;
		this.port = port;
		this.path = path;
		this.scheme = scheme;
	}
	
	public UpdateRequest(UpdateHTTPMethod method,String scheme,String host, int port, String path,String sparql,Set<String> default_graph_uri,Set<String> named_graph_uri,String authorization) {
		super(sparql,authorization,default_graph_uri,named_graph_uri);
		
		this.method = method;
		this.host = host;
		this.port = port;
		this.path = path;
		this.scheme = scheme;
	}

	/* SPARQL Update requests are executed against a Graph Store, a mutable
	 * container of RDF graphs managed by a SPARQL service. The WHERE clause of a
	 * SPARQL update DELETE/INSERT operation [UPDATE] matches against data in an RDF
	 * Dataset, which is a subset of the Graph Store. The RDF Dataset for an update
	 * operation may be specified either in the operation string itself using the
	 * USING, USING NAMED, and/or WITH keywords, or it may be specified via the
	 * using-graph-uri and using-named-graph-uri parameters.
	 */

	public String getAcceptHeader() {
		return "application/json";
	}
	
	/**
	 * Default implementation. Two requests are equal if they belong to the same class and their SPARQL strings are equals. SPARQL matching should be based on SPARQL algebra
	 * and SPARQL semantics. The default implementation provides a syntax based matching. 
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof UpdateRequest)) return false;
		return sparql.equals(((UpdateRequest)obj).sparql);
	}
	
	public UpdateHTTPMethod getHttpMethod() {
		return method;
	}

	public boolean urlEncodedGraphsPatch() {
		return urlEncodedGraphsPatch;
	}
}
