/* Scheduling queues
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

package it.unibo.arces.wot.sepa.engine.scheduling;

//import java.util.Date;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.engine.bean.SchedulerBeans;
import it.unibo.arces.wot.sepa.engine.core.ResponseHandler;

class SchedulerQueue {
	private static final Logger logger = LogManager.getLogger();
	
	// Tokens
	private final static Vector<Integer> tokens = new Vector<Integer>();

	// Requests
	private final static LinkedBlockingQueue<ScheduledRequest> updates = new LinkedBlockingQueue<ScheduledRequest>();
	private final static LinkedBlockingQueue<ScheduledRequest> queries = new LinkedBlockingQueue<ScheduledRequest>();
	private final static LinkedBlockingQueue<ScheduledRequest> subscribes = new LinkedBlockingQueue<ScheduledRequest>();
	private final static LinkedBlockingQueue<ScheduledRequest> unsubscribes = new LinkedBlockingQueue<ScheduledRequest>();
	
	// Responses
	private final static LinkedBlockingQueue<ScheduledResponse> responses = new LinkedBlockingQueue<ScheduledResponse>();

	private int size = 0;
	
	public SchedulerQueue(int size) {
		// Initialize token jar
		for (int i = 0; i < size; i++)
			tokens.addElement(i);
		
		this.size = size;
	}
	
	public synchronized void setSize(int n) {
		if (n > size) {
			for (int i = size; i < size + (size-n);i++) {
				tokens.addElement(i);
			}
			size = n;
		}
		else if (n < size) {
			if (size == tokens.size()) {
				for (int i=0 ; i < (size-n);i++ ) tokens.removeElementAt(0);
			}
		}
	}

	/**
	 * Returns a new token if more tokens are available or -1 otherwise
	 * 
	 * @return an int representing the token
	 */
	private synchronized int getToken() {
		if (tokens.size() == 0) {
			logger.error("No tokens available");
			return -1;
		}

		Integer token = tokens.get(0);
		tokens.removeElementAt(0);

		logger.trace("Get token #" + token + " (Available: " + tokens.size() + ")");

		SchedulerBeans.tokenLeft(tokens.size());

		return token;
	}

	/**
	 * Release an used token
	 * 
	 * @return true if success, false if the token to be released has not been
	 *         acquired
	 */
	private synchronized boolean releaseToken(Integer token) {
		if (token == -1)
			return false;

		if (tokens.contains(token)) {
			logger.warn("Token #" + token + " is available (Available tokens: " + tokens.size() + ")");
			return false;
		} else {
			tokens.insertElementAt(token, tokens.size());
			logger.trace("Release token #" + token + " (Available: " + tokens.size() + ")");

			SchedulerBeans.tokenLeft(tokens.size());
		}
		
		return true;
	}
	
	public ScheduledRequest addRequest(InternalRequest req,ResponseHandler handler) {
		int token = getToken();
		if (token == -1)  return null;
		
		ScheduledRequest request = new ScheduledRequest(token,req,handler);
		
		if (req.isUpdateRequest()) updates.add(request);
		else if (req.isQueryRequest()) queries.add(request);
		else if (req.isSubscribeRequest()) subscribes.add(request);
		else if (req.isUnsubscribeRequest())unsubscribes.add(request);
		
		return request;
	}

	public ScheduledRequest waitUpdateRequest() throws InterruptedException {
		return updates.take();
	}
	
	public ScheduledRequest waitQueryRequest() throws InterruptedException {
		return queries.take();
	}
	
	public ScheduledRequest waitSubscribeRequest() throws InterruptedException {
		return subscribes.take();
	}
	
	public ScheduledRequest waitUnsubscribeRequest() throws InterruptedException {
		return unsubscribes.take();
	}
	
	public ScheduledResponse waitResponse() throws InterruptedException {
		return responses.take();
	}

	// Returns false if the corresponding token has not been released (e.g., a timeout has been triggered or the response received), true otherwise
	public boolean addResponse(int token,Response res) {
		if (!releaseToken(token)) return false;
		responses.offer(new ScheduledResponse(token,res));
		return true;
	}

	public long getPendingUpdates() {
		return updates.size();
	}

	public long getPendingQueries() {
		return queries.size();
	}

	public long getPendingSubscribes() {
		return subscribes.size();
	}
	
	public long getPendingUnsubscribes() {
		return unsubscribes.size();
	}
}
