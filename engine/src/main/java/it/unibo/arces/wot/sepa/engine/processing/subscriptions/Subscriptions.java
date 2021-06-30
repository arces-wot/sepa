/* A monitor class for subscription management
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

package it.unibo.arces.wot.sepa.engine.processing.subscriptions;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPANotExistsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.engine.bean.SPUManagerBeans;
import it.unibo.arces.wot.sepa.engine.dependability.Dependability;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalSubscribeRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequestWithQuads;
import it.unibo.arces.wot.sepa.timing.Timings;

/**
 * A monitor class for subscription management
 <pre>
 Terms
  
 - SPU (SPARQL Processing Unit): the thread in charge of processing a SPARQL query (InternalSubscribeRequest). Each SPU is identified by a unique SPUID 
 - Subscriber: a client subscribed to SEPA. Each subscriber is identified by a unique SID. A subscriber knows the SPU and the InternalSubscribeRequest.
 - An SPU can be associated to multiple subscribers (e.g., they share the same SPARQL query)
  
 HashMaps
 - subscribers: SID --> subscriber
 - handlers: SPUID --> Set of subscribers
 - requests:  InternalSubscribeRequest --> SPU
 - spus: SPUID --> SPU
 </pre>
  
 * @author Luca Roffia (luca.roffia@unibo.it)
 * @version 0.9.12
 */
public class Subscriptions {
	private static final Logger logger = LogManager.getLogger();

	// SID ==> Subscriber
	private static final HashMap<String, Subscriber> subscribers = new HashMap<String, Subscriber>();

	// SPUID ==> Subscribers
	private static final HashMap<String, HashSet<Subscriber>> handlers = new HashMap<String, HashSet<Subscriber>>();

	// Request ==> SPU
	private static final HashMap<InternalSubscribeRequest, SPU> requests = new HashMap<InternalSubscribeRequest, SPU>();

	// SPUID ==> SPU
	private final static HashMap<String, SPU> spus = new HashMap<String, SPU>();
	
	//TODO: a different SPU can be created based on the InternalSubscribeRequest
	public static SPU createSPU(InternalSubscribeRequest req, SPUManager manager) {
		logger.log(Level.getLevel("subscriptions"),"@createSPU");
		
		try {
			return new SPUNaive(req, manager);
		} catch (SEPAProtocolException e) {
			return null;
		}
	}

	// First level filtering on RDF data set (graph uris)
	public synchronized static Collection<SPU> filterOnGraphs(InternalUpdateRequest update) {
		long start = Timings.getTime();
		
		Collection<SPU> ret = new HashSet<>();
		Set<String> target = update.getRdfDataSet();

		for (InternalSubscribeRequest sub : requests.keySet()) {
			Set<String> context = sub.getRdfDataSet();

			// All graphs: NO FILTER
			// TODO: default graph?
			if (context.isEmpty() && target.isEmpty()) {
				// DEFAULT GRAPHs
				ret.add(requests.get(sub));
			}
			else if (context.contains("*") || target.contains("*")) {
				ret.add(requests.get(sub));
			} else
				for (String graph : target) {
					if (context.contains(graph)) {
						ret.add(requests.get(sub));
						break;
					}
				}
		}
		long stop = Timings.getTime();
		
		SPUManagerBeans.filteringTimings(start, stop);
		
		logger.log(Level.getLevel("subscriptions"),"Filtered spus: " + ret.size());
		
		return ret;
	}

	// Second level filtering (on quads)
	public static Collection<SPU> filterOnQuads(Collection<SPU> activeSpus, InternalUpdateRequestWithQuads update) {
		// TODO implement LUTT filtering
		return activeSpus;
	}
	
	public synchronized static boolean containsSubscribe(InternalSubscribeRequest req) {
		logger.log(Level.getLevel("subscriptions"),"@containsSubscribe");
		return requests.containsKey(req);
	}

	public synchronized static void registerSubscribe(InternalSubscribeRequest req, SPU spu) {
		logger.log(Level.getLevel("subscriptions"),"@registerSubscribe");
		
		if (requests.containsKey(req)) return;
		
		// Link the request with the SPU
		requests.put(req, spu);
		
		// New entry for subscribers
		handlers.put(spu.getSPUID(), new HashSet<Subscriber>());
		
		// Add the SPU to the collection
		spus.put(spu.getSPUID(), spu);
				
		SPUManagerBeans.setActiveSPUs(handlers.size());
				
		logger.log(Level.getLevel("subscriptions"),"@registerSubscribe SPU activated: " + spu.getSPUID() + " total (" + handlers.size() + ")");
	}

	public synchronized static SPU getSPU(InternalSubscribeRequest req) {
		return requests.get(req);
	}

	public synchronized static Subscriber addSubscriber(InternalSubscribeRequest req, SPU spu) {
		logger.log(Level.getLevel("subscriptions"),"@addSubscriber");
		
		// Create a new subscriber
		Subscriber sub = new Subscriber(spu, req);
		
		// Add subscriber to the SPU
		handlers.get(spu.getSPUID()).add(sub);
		
		// Add subscriber to the subscribers map
		subscribers.put(sub.getSID(), sub);

		SPUManagerBeans.addSubscriber();
		
		// Link the subscriber with the gate
		Dependability.onSubscribe(req.getGID(), sub.getSID());

		return sub;
	}

	public synchronized static Subscriber getSubscriber(String sid) throws SEPANotExistsException {
		logger.log(Level.getLevel("subscriptions"),"@getSubscriber "+sid);
		
		Subscriber sub = subscribers.get(sid);

		if (sub == null)
			throw new SEPANotExistsException("Subscriber " + sid + " does not exists");
		return sub;
	}

	/**
	 * Remove the subscriber and return true if it is the last of the SPU managed ones 
	 * */
	public synchronized static boolean removeSubscriber(Subscriber sub) throws SEPANotExistsException {
		String sid = sub.getSID();
		String spuid = sub.getSPU().getSPUID();

		logger.log(Level.getLevel("subscriptions"),"@removeSubscriber "+sid+" "+spuid);
		
		if (!subscribers.containsKey(sid)) {
			logger.warn("@internalUnsubscribe SID not found: " + sid);
			throw new SEPANotExistsException("SID not found: " + sid);
		}

		logger.log(Level.getLevel("subscriptions"),"@internalUnsubscribe SID: " + sid + " from SPU: " + spuid + " with active subscriptions: "
				+ subscribers.size());

		if (handlers.get(spuid) == null) return false;
		
		// Remove from maps
		subscribers.remove(sid);
		
		handlers.get(spuid).remove(sub);
	
		// No more handlers: return true
		if (handlers.get(spuid).isEmpty()) {
			logger.log(Level.getLevel("subscriptions"),"@internalUnsubscribe no more subscribers. Kill SPU: " + sub.getSPU().getSPUID());

			requests.remove(sub.getSPU().getSubscribe());
			handlers.remove(spuid);

			// *** Kill SPU ***
			logger.log(Level.getLevel("subscriptions"), "Interrupt SPU " + spuid);
			spus.get(spuid).interrupt();

			// Clear
			logger.log(Level.getLevel("subscriptions"), "remove " + spuid);
			spus.remove(spuid);

			logger.log(Level.getLevel("subscriptions"), "@internalUnsubscribe active SPUs: " + spus.size());

			SPUManagerBeans.setActiveSPUs(spus.size());
			SPUManagerBeans.removeSubscriber();
			
			return true;
		}

		// More handlers
		return false;
	}

	public synchronized static void notifySubscribers(Notification notify) {
		logger.log(Level.getLevel("subscriptions"),"@notifySubscribers");
		
		String spuid = notify.getSpuid();

		if (!spus.containsKey(spuid)) return;		
		
		for (Subscriber client : handlers.get(spuid)) {
			// Dispatching events
			Notification event = new Notification(client.getSID(), notify.getARBindingsResults(),
					client.nextSequence());
			try {
				client.notifyEvent(event);
			} catch (SEPAProtocolException e) {
				logger.error(e.getMessage());
				if (logger.isTraceEnabled())
					e.printStackTrace();
			}
		}
	}

	public synchronized static long size() {
		return spus.size();
	}
}
