/* JMX interface for the engine core
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
package it.unibo.arces.wot.sepa.engine.core;

public interface EngineMBean {
	public String getVersion();
	
	public String getUpTime();
	
	public String getQueryPath();
	
	public String getUpdatePath();
	
	public String getSubscribePath();

	public String getSecurePath();
	
	public String getRegisterPath();
	
	public String getTokenRequestPath();
	
	public int getHttpPort();
	
	public int getHttpsPort();
	
	public int getWsPort();
	
	public int getWssPort();
	
	public boolean getSecure();
	
	public void resetAll();
	
	public String getSSLCertificate();
	
	public void refreshSSLCertificate();
}
