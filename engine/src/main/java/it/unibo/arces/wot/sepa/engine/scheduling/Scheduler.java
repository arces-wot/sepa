/* This class implements the scheduler of the Semantic Event Processing Architecture (SEPA) Engine
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

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.engine.bean.SEPABeans;
import it.unibo.arces.wot.sepa.engine.bean.SchedulerBeans;
import it.unibo.arces.wot.sepa.engine.core.EngineProperties;
import it.unibo.arces.wot.sepa.engine.core.ResponseHandler;
import it.unibo.arces.wot.sepa.engine.timing.Timings;

/**
 * This class represents the scheduler of the SPARQL Event Processing Engine
 */

public class Scheduler extends Thread implements SchedulerMBean {
	private static final Logger logger = LogManager.getLogger();

	private final AtomicBoolean running = new AtomicBoolean(true);

	// Responders
	private final HashMap<Integer, ResponseHandler> responders = new HashMap<Integer, ResponseHandler>();

	// Synchronized queues
	private final SchedulerQueue queue;

	public Scheduler(EngineProperties properties) {
		if (properties == null) {
			logger.error("Properties are null");
			throw new IllegalArgumentException("Properties are null");
		}

		queue = new SchedulerQueue(properties.getSchedulingQueueSize());

		// JMX
		SEPABeans.registerMBean("SEPA:type=" + this.getClass().getSimpleName(), this);
		SchedulerBeans.setQueueSize(properties.getSchedulingQueueSize());
//		SchedulerBeans.setTimeout(properties.getSchedulerTimeout());

		setName("SEPA-Scheduler");
	}

	public ScheduledRequest schedule(InternalRequest request, ResponseHandler handler) {
		if (request == null || handler == null) {
			logger.error("Request handler or request are null");
			return null;
		}

		ScheduledRequest scheduled = null;		
		synchronized (responders) {
			// Add request to the scheduler queue (null means no more tokens)
			scheduled = queue.addRequest(request, handler);

			// No more tokens
			if (scheduled == null) {
				SchedulerBeans.newRequest(request, false);
				logger.error("Request refused: too many pending requests: " + request);
				return null;
			}

			logger.debug(">> " + scheduled);
			logger.trace(scheduled.getRequest());
			
			Timings.log(request);

			SchedulerBeans.newRequest(request, true);

			// Register response handlers
			logger.trace("Register handler: " + handler + " token: " + scheduled.getToken());
			responders.put(scheduled.getToken(), handler);
		}

		return scheduled;
	}

	public void finish() {
		running.set(false);
	}

	@Override
	public void run() {
		while (running.get()) {
			try {
				// Wait for response
				ScheduledResponse response = queue.waitResponse();
				logger.debug("<< " + response);
				logger.trace(response.getResponse());

				// The token
				int token = response.getToken();

				// Send response back
				synchronized (responders) {
					ResponseHandler handler = responders.get(token);
					if (handler == null) {
						logger.warn("Response handler is null (token #" + token + "). Timeout already expired?");

					} else {
						logger.trace("Handler: " + handler + " response: " + response);
						try {
							handler.sendResponse(response.getResponse());
						} catch (SEPAProtocolException e) {
							logger.warn(e.getMessage());
						}
					}

					// Remove handlers
					responders.remove(token);
				}

			} catch (InterruptedException e) {
				running.set(false);
			}
		}
	}

	public String getStatistics() {
		return SchedulerBeans.getStatistics();
	}

	public long getRequests_pending() {
		return SchedulerBeans.getQueue_Pending();
	}

	public long getRequests_max_pending() {
		return SchedulerBeans.getQueue_Max();
	}

	public long getRequests_rejected() {
		return SchedulerBeans.getQueue_OutOfToken();
	}

	@Override
	public void reset() {
		SchedulerBeans.reset();
	}

	@Override
	public long getRequests_scheduled() {
		return SchedulerBeans.getScheduledRequests();
	}

	@Override
	public int getQueueSize() {
		return SchedulerBeans.getQueueSize();
	}

	public ScheduledRequest waitQueryRequest() throws InterruptedException {
		return queue.waitQueryRequest();
	}

	public boolean addResponse(int token, Response ret) {
		return queue.addResponse(token, ret);
	}

	public ScheduledRequest waitSubscribeRequest() throws InterruptedException {
		return queue.waitSubscribeRequest();
	}
	
	public ScheduledRequest waitUnsubscribeRequest() throws InterruptedException {
		return queue.waitUnsubscribeRequest();
	}

	public ScheduledRequest waitUpdateRequest() throws InterruptedException {
		return queue.waitUpdateRequest();
	}

	@Override
	public long getPendingUpdates() {
		return queue.getPendingUpdates();
	}

	@Override
	public long getPendingQueries() {
		return queue.getPendingQueries();
	}

	@Override
	public long getPendingSubscribes() {
		return queue.getPendingSubscribes();
	}

	@Override
	public long getPendingUnsubscribes() {
		return queue.getPendingUnsubscribes();
	}

	@Override
	public void setQueueSize(int schedulingQueueSize) {
		SchedulerBeans.setQueueSize(schedulingQueueSize);
	}
}
