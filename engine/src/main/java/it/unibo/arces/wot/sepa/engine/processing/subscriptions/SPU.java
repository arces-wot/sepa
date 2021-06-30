/* This class implements a Semantic Processing Unit (SPU) of the Semantic Event Processing Architecture (SEPA) Engine
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import it.unibo.arces.wot.sepa.engine.bean.SPUManagerBeans;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalSubscribeRequest;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProcessingException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.Notification;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;

/**
 * This class represents a Semantic Processing Unit (SPU)
 * 
 * 
 * @author Luca Roffia (luca.roffia@unibo.it)
 * @version 0.9.12
 */

public abstract class SPU extends Thread implements ISPU {
	public abstract Notification postUpdateInternalProcessing(UpdateResponse ret) throws SEPAProcessingException;

	public abstract void preUpdateInternalProcessing(InternalUpdateRequest update) throws SEPAProcessingException;

	protected final Logger logger = LogManager.getLogger();

	// SPU identifier
	protected String spuid;

	// Thread loop
	private final AtomicBoolean running = new AtomicBoolean(true);

	// Last bindings results
	protected BindingsResults lastBindings = null;

	// Request and response
	protected InternalUpdateRequest request;
	protected Response response;
	protected final InternalSubscribeRequest subscribe;

	private final AtomicBoolean preProcessing = new AtomicBoolean(false);
	private final AtomicBoolean postProcessing = new AtomicBoolean(false);

	protected final SPUManager manager;

	public SPU(InternalSubscribeRequest subscribe, SPUManager manager) {
		this.manager = manager;
		this.subscribe = subscribe;
		this.spuid = "sepa://spu/" + UUID.randomUUID();
	}

	public InternalSubscribeRequest getSubscribe() {
		return subscribe;
	}

	@Override
	public BindingsResults getLastBindings() {
		return lastBindings;
	}

	public void interrupt() {
		logger.log(Level.getLevel("spu"), "@interrupt");
		running.set(false);
		super.interrupt();
	}

	@Override
	public final boolean equals(Object obj) {
		return ((SPU) obj).subscribe.equals(subscribe);
	}

	@Override
	public final String getSPUID() {
		return spuid;
	}

	@Override
	public final int hashCode() {
		return subscribe.hashCode();
	}

	@Override
	public final void postUpdateProcessing(Response res) {
		synchronized (postProcessing) {
			logger.log(Level.getLevel("spu"), "@postUpdateProcessing");
			response = res;
			postProcessing.set(true);
			postProcessing.notify();
		}
	}

	@Override
	public final void preUpdateProcessing(InternalUpdateRequest req) {
		synchronized (preProcessing) {
			logger.log(Level.getLevel("spu"), "@preUpdateProcessing");
			request = req;
			preProcessing.set(true);
			preProcessing.notify();
		}
	}
	
	public void abortProcessing() {
		synchronized (postProcessing) {
			logger.log(Level.getLevel("spu"), "@postUpdateProcessing");
			response = new ErrorResponse(0, "abort", "@postUpdateProcessing");
			postProcessing.set(true);
			postProcessing.notify();
		}		
	}

	@Override
	public void run() {
		while (running.get()) {
			synchronized (preProcessing) {
				try {
					while (!preProcessing.get()) {
						logger.log(Level.getLevel("spu"), "Pre-processing wait...");
						preProcessing.wait();
					}
					preProcessing.set(false);
				} catch (InterruptedException e) {
					logger.log(Level.getLevel("spu"), "SPU interrupted. Send EOP and exist.");
					manager.endOfProcessing(this);
					return;
				}

				// PRE processing
				logger.log(Level.getLevel("spu"), "* PRE PROCESSING *");
				try {
					preUpdateInternalProcessing(request);
				} catch (SEPAProcessingException e) {
					SPUManagerBeans.preProcessingException();
					logger.error(e.getMessage());
					if (logger.isTraceEnabled())
						e.printStackTrace();
				}
			}
			
			// End of processing
			logger.log(Level.getLevel("spu"), "Send EOP");
			manager.endOfProcessing(this);

			synchronized (postProcessing) {
				try {
					while (!postProcessing.get()) {
						logger.log(Level.getLevel("spu"), "Post-processing wait...");
						postProcessing.wait();
					}
					postProcessing.set(false);
				} catch (InterruptedException e) {
					logger.log(Level.getLevel("spu"), "SPU interrupted. Send EOP and exist.");
					manager.endOfProcessing(this);
					return;
				}

				if (!response.isError()) {
					// POST processing
					logger.log(Level.getLevel("spu"), "* POST PROCESSING *");
					try {
						Notification notify = postUpdateInternalProcessing((UpdateResponse) response);
						if (notify != null) {
							logger.log(Level.getLevel("spu"), "notifyEvent");
							manager.notifyEvent(notify);
						}
					} catch (SEPAProcessingException e) {
						SPUManagerBeans.postProcessingException();
						logger.error(e.getMessage());
						if (logger.isTraceEnabled())
							e.printStackTrace();
					}
				}
			}

			// End of processing
			logger.log(Level.getLevel("spu"), "Send EOP");
			manager.endOfProcessing(this);
		}
	}
}
