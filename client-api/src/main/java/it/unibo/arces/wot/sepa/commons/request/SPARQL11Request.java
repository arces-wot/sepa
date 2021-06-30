/* An abstract SPARQL 1.1 request 
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

package it.unibo.arces.wot.sepa.commons.request;

import java.util.HashSet;
import java.util.Set;

public abstract class SPARQL11Request extends Request {
	protected String scheme = null;
	protected String host = null;
	protected int port = -1;
	protected String path = null;
	
	protected Set<String> default_graph_uri = new HashSet<>();
	protected Set<String> named_graph_uri = new HashSet<>();
	
	public SPARQL11Request(String sparql, String auth,Set<String> defaultGraphUri,Set<String> namedGraphUri,long timeout,long nRetry) {
		super(sparql, auth,timeout,nRetry);
		
		if (defaultGraphUri != null) this.default_graph_uri = defaultGraphUri;
		if (namedGraphUri != null) this.named_graph_uri = namedGraphUri;
	}
	
	public SPARQL11Request(String sparql, String auth,Set<String> defaultGraphUri,Set<String> namedGraphUri) {
		super(sparql, auth);
		
		if (defaultGraphUri != null) this.default_graph_uri = defaultGraphUri;
		if (namedGraphUri != null) this.named_graph_uri = namedGraphUri;
	}

	@Override
	public String toString() {
		return sparql;
		
	}

	public String getScheme() {
		return scheme;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getPath() {
		return path;
	}
	
	public Set<String> getDefaultGraphUri() {
		return default_graph_uri;
	}

	public Set<String> getNamedGraphUri() {
		return named_graph_uri;
	}
}
