/* The SEPA core class for subscriptions management
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

import it.unibo.arces.wot.sepa.commons.exceptions.SEPANotExistsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProcessingException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.SubscribeResponse;
import it.unibo.arces.wot.sepa.commons.response.UnsubscribeResponse;
import it.unibo.arces.wot.sepa.engine.bean.SEPABeans;
import it.unibo.arces.wot.sepa.engine.bean.SPUManagerBeans;
import it.unibo.arces.wot.sepa.engine.bean.UpdateProcessorBeans;
import it.unibo.arces.wot.sepa.engine.core.EventHandler;
import it.unibo.arces.wot.sepa.engine.dependability.Dependability;
import it.unibo.arces.wot.sepa.engine.processing.Processor;
import it.unibo.arces.wot.sepa.engine.processing.QueryProcessor;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalSubscribeRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;
import it.unibo.arces.wot.sepa.timing.Timings;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * SpuManager is a monitor class. It takes care of the SPU collection and it
 * encapsulates filtering algorithms based on the internal structure.
 */
public class SPUManager implements SPUManagerMBean, EventHandler {
	private final Logger logger = LogManager.getLogger();

	// SPUs processing pool
	private final HashSet<SPU> processingPool = new HashSet<SPU>();
	private Collection<SPU> activeSpus;
	// SPUID ==> SPU
	private final HashMap<String, SPU> spus = new HashMap<String, SPU>();

	private final Processor processor;

	public SPUManager(Processor processor) {
		this.processor = processor;

		SEPABeans.registerMBean("SEPA:type=" + this.getClass().getSimpleName(), this);
	}

	// TODO: choose different kinds of SPU based on subscribe request
	protected SPU createSPU(InternalSubscribeRequest req, SPUManager manager) {
		try {
			return new SPUNaive(req, this);
		} catch (SEPAProtocolException e) {
			return null;
		}
	}

	// TODO: filtering SPUs to be activated
	protected Collection<SPU> filter(InternalUpdateRequest update) {
//		Collection<SPU> ret = new ArrayList<SPU>();
//
//		synchronized (spus) {
//			Collection<SPU> toBeRemoved = new ArrayList<SPU>();
//			for (SPU spu : spus.values()) {
//				if (Subscriptions.isZombieSpu(spu.getSPUID())) {
//					spu.finish();
//					spu.interrupt();
//					toBeRemoved.add(spu);
//				} else {
//					ret.add(spu);
//				}
//			}
//			for (SPU spu : toBeRemoved) {
//				spus.remove(spu.getSPUID());
//			}	
//		}
//
//		return ret;
		return spus.values();
	}

	public synchronized Response update(InternalUpdateRequest update) {
		// PRE-processing update request
		InternalUpdateRequest preRequest;
		try {
			preRequest = processor.preProcessUpdate(update);
		} catch (SEPAProcessingException e) {
			logger.error("*** PRE-UPDATE PROCESSING FAILED *** " + e.getMessage());
			return new ErrorResponse(500, "pre_update_processing_failed",
					"Update: " + update + " Message: " + e.getMessage());
		}

		// PRE-processing subscriptions (ENDPOINT not yet updated)
		try {
			preProcessing(preRequest);
		} catch (SEPAProcessingException e) {
			logger.error("*** PRE-SUBSCRIPTIONS PROCESSING FAILED *** " + e.getMessage());
			return new ErrorResponse(500, "timeout",
					"Update: " + update + " Message: " + e.getMessage());
		}

		// Update the ENDPOINT
		Response ret;
		try {
			ret = processor.processUpdate(preRequest, UpdateProcessorBeans.getTimeoutNRetry());
		} catch (SEPASecurityException e1) {
			logger.error(e1.getMessage());
			if (logger.isTraceEnabled())
				e1.printStackTrace();
			ret = new ErrorResponse(401, "SEPASecurityException", e1.getMessage());
		}

		if (ret.isError()) {
			logger.error("*** UPDATE PROCESSING FAILED *** " + ret);
//			return ret;
		}

		// Subscription processing (post update)
		try {
			postProcessing(ret);
		} catch (SEPAProcessingException e) {
			logger.error("*** POST-SUBSCRIPTIONS PROCESSING FAILED *** " + e.getMessage());
			ret = new ErrorResponse(500, "timeout",
					"Update: " + update + " Message: " + e.getMessage());
		}

		return ret;
	}

	private void preProcessing(InternalUpdateRequest update) throws SEPAProcessingException {
		logger.debug("*** PRE PROCESSING SUBSCRIPTIONS BEGIN *** ");

		// Get active SPUs (e.g., LUTT filtering)
		long start = Timings.getTime();
		activeSpus = filter(update);
		long stop = Timings.getTime();
		SPUManagerBeans.filteringTimings(start, stop);

		// Start processing
		start = Timings.getTime();

		// Copy active SPU pool
		processingPool.clear();

		for (SPU spu : activeSpus) {
			processingPool.add(spu);
			spu.preUpdateProcessing(update);
		}

		logger.debug("@preUpdateProcessing SPU processing pool size: " + processingPool.size());

		// Wait all SPUs to complete processing
		if (!processingPool.isEmpty()) {
			logger.debug(String.format("@preUpdateProcessing wait (%d ms) for %d SPUs to complete processing...",
					SPUManagerBeans.getSPUProcessingTimeout()*processingPool.size(), processingPool.size()));

			try {
				wait(SPUManagerBeans.getSPUProcessingTimeout()*processingPool.size());
			} catch (InterruptedException e) {
				logger.error(
						"@preUpdateProcessing TIMEOUT on SPU processing. SPUs still running: " + processingPool.size());
				for (SPU spu : processingPool) {
					logger.error("@preUpdateProcessing spuid timed out: " + spu.getSPUID());
				}
				if (logger.isTraceEnabled()) e.printStackTrace();
				
				stop = Timings.getTime();

				SPUManagerBeans.timings(start, stop);
				
				throw new SEPAProcessingException("@preUpdateProcessing TIMEOUT on SPU processing. SPUs still running: " + processingPool.size());
			}
		}

		stop = Timings.getTime();

		SPUManagerBeans.timings(start, stop);

		logger.debug("*** PRE PROCESSING SUBSCRIPTIONS END *** ");
	}

	private void postProcessing(Response ret) throws SEPAProcessingException {
		logger.debug("*** POST PROCESSING SUBSCRIPTIONS BEGIN *** ");

		long start = Timings.getTime();

		processingPool.clear();

		for (SPU spu : activeSpus) {
			processingPool.add(spu);
			spu.postUpdateProcessing(ret);
		}

		logger.debug("@postUpdateProcessing SPU processing pool size: " + processingPool.size());

		if (!processingPool.isEmpty()) {
			logger.debug(String.format("@postUpdateProcessing wait (%d ms) for %d SPUs to complete processing...",
					SPUManagerBeans.getSPUProcessingTimeout(), processingPool.size()));

			try {
				wait(SPUManagerBeans.getSPUProcessingTimeout());
			} catch (InterruptedException e) {
				logger.error(
						"@postUpdateProcessing timeout on SPU processing. SPUs still running: " + processingPool.size());
				for (SPU spu : processingPool) {
					logger.error("@postUpdateProcessing spuid timed out: " + spu.getSPUID());
				}
				if (logger.isTraceEnabled()) e.printStackTrace();
				
				long stop = Timings.getTime();

				SPUManagerBeans.timings(start, stop);
				
				throw new SEPAProcessingException("@postUpdateProcessing timeout on SPU processing. SPUs still running: " + processingPool.size());
			}
		}

		long stop = Timings.getTime();

		SPUManagerBeans.timings(start, stop);

		logger.debug("*** POST PROCESSING SUBSCRIPTIONS END *** ");
	}

	public synchronized void endOfProcessing(SPU s) {
		logger.debug("@endOfProcessing  SPUID: " + s.getSPUID());

		processingPool.remove(s);
		if (processingPool.isEmpty()) notify();
	}

	public synchronized void exceptionOnProcessing(SPU s) {
		logger.error("@exceptionOnProcessing  SPUID: " + s.getSPUID());

		activeSpus.remove(s);

		processingPool.remove(s);
		if (processingPool.isEmpty()) notify();
	}

	public synchronized Response subscribe(InternalSubscribeRequest req) throws InterruptedException {

		SPUManagerBeans.subscribeRequest();

		// Create or link to an existing SPU
		SPU spu;
		if (Subscriptions.contains(req)) {
			spu = Subscriptions.getSPU(req);
		} else {
			spu = createSPU(req, this);

			// Initialize SPU
			Response init;
			try {
				init = spu.init();
			} catch (SEPASecurityException e) {
				logger.error(e.getMessage());
				if (logger.isTraceEnabled())
					e.printStackTrace();
				init = new ErrorResponse(401, "SEPASecurityException", e.getMessage());
			}

			if (init.isError()) {
				logger.error("@subscribe SPU initialization failed: " + init);
				if (req.getAlias() != null) {
					((ErrorResponse) init).setAlias(req.getAlias());
				}

				return init;
			}

			// Register request
			Subscriptions.register(req, spu);

			// Create new entry for handler
			spus.put(spu.getSPUID(), spu);

			// Start the SPU thread
			spu.setName(spu.getSPUID());
			spu.start();
		}

		Subscriber sub = Subscriptions.addSubscriber(req, spu);

		return new SubscribeResponse(sub.getSID(), req.getAlias(), sub.getSPU().getLastBindings());
	}

	public synchronized Response unsubscribe(String sid, String gid) throws InterruptedException {
		return internalUnsubscribe(sid, gid, true);
	}

	public synchronized void killSubscription(String sid, String gid) throws InterruptedException {
		internalUnsubscribe(sid, gid, false);
	}

	private Response internalUnsubscribe(String sid, String gid, boolean dep) throws InterruptedException {

		try {
			Subscriber sub = Subscriptions.getSubscriber(sid);
			String spuid = sub.getSPU().getSPUID();
			
			if (Subscriptions.removeSubscriber(sub)) {
				// If it is the last handler: kill SPU
				spus.get(spuid).finish();
				spus.get(spuid).interrupt();

				// Clear
				spus.remove(spuid);

				logger.info("@internalUnsubscribe active SPUs: " + spus.size());

				SPUManagerBeans.setActiveSPUs(spus.size());
			}
		} catch (SEPANotExistsException e) {
			logger.warn("@internalUnsubscribe SID not found: " + sid);

			return new ErrorResponse(500, "sid_not_found", "Unregistering a not existing subscriber: " + sid);
		}

		if (dep)
			Dependability.onUnsubscribe(gid, sid);

		return new UnsubscribeResponse(sid);
	}

	@Override
	public void notifyEvent(Notification notify) throws SEPAProtocolException {
		logger.debug("@notifyEvent " + notify);

		String spuid = notify.getSpuid();

		if (spus.containsKey(spuid)) {
			Subscriptions.notifySubscribers(spuid, notify);
		}
	}

	public QueryProcessor getQueryProcessor() {
		return processor.getQueryProcessor();
	}

	@Override
	public long getUpdateRequests() {
		return SPUManagerBeans.getUpdateRequests();
	}

	@Override
	public long getSPUs_current() {
		return SPUManagerBeans.getSPUs_current();
	}

	@Override
	public long getSPUs_max() {
		return SPUManagerBeans.getSPUs_max();
	}

	@Override
	public float getSPUs_time() {
		return SPUManagerBeans.getSPUs_time();
	}

	@Override
	public void reset() {
		SPUManagerBeans.reset();
	}

	@Override
	public float getSPUs_time_min() {
		return SPUManagerBeans.getSPUs_time_min();
	}

	@Override
	public float getSPUs_time_max() {
		return SPUManagerBeans.getSPUs_time_max();
	}

	@Override
	public float getSPUs_time_average() {
		return SPUManagerBeans.getSPUs_time_average();
	}

	@Override
	public long getSubscribeRequests() {
		return SPUManagerBeans.getSubscribeRequests();
	}

	@Override
	public long getUnsubscribeRequests() {
		return SPUManagerBeans.getUnsubscribeRequests();
	}

	@Override
	public long getSPUProcessingTimeout() {
		return SPUManagerBeans.getSPUProcessingTimeout();
	}

	@Override
	public void setSPUProcessingTimeout(long t) {
		SPUManagerBeans.setActiveSPUs(t);
	}

	@Override
	public void scale_ms() {
		SPUManagerBeans.scale_ms();
	}

	@Override
	public void scale_us() {
		SPUManagerBeans.scale_us();
	}

	@Override
	public void scale_ns() {
		SPUManagerBeans.scale_ns();
	}

	@Override
	public String getUnitScale() {
		return SPUManagerBeans.getUnitScale();
	}

	@Override
	public long getSubscribers() {
		return SPUManagerBeans.getSubscribers();
	}

	@Override
	public long getSubscribers_max() {
		return SPUManagerBeans.getSubscribersMax();
	}

	@Override
	public float getFiltering_time() {
		return SPUManagerBeans.getFiltering_time();
	}

	@Override
	public float getFiltering_time_min() {
		return SPUManagerBeans.getFiltering_time_min();
	}

	@Override
	public float getFiltering_time_max() {
		return SPUManagerBeans.getFiltering_time_max();
	}

	@Override
	public float getFiltering_time_average() {
		return SPUManagerBeans.getFiltering_time_average();
	}
}
