package it.unibo.arces.wot.sepa.apps.mqtt;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.pattern.JSAP;
import it.unibo.arces.wot.sepa.pattern.Producer;

public class ObservationSimulator extends Producer implements Runnable {
	private static final Logger logger = LogManager.getLogger();
	
	private int value = 15;
	private long timeout = 2000;
	
	public ObservationSimulator(JSAP appProfile,String observation) throws SEPAProtocolException, SEPASecurityException {
		super(appProfile, "UPDATE_OBSERVATION_VALUE");
		
		this.setUpdateBindingValue("observation", observation);
	}
	
	public void simulate() throws SEPASecurityException, IOException, SEPAPropertiesException {
		value += 5;
		if (value > 45) value = 15;
		
		this.setUpdateBindingValue("value", String.format("%d", value));
		update();
	}

	@Override
	public void run() {
		while(true) {
			try {
				Thread.sleep(timeout);
			} catch (InterruptedException e) {
				return;
			}
			try {
				simulate();
			} catch (SEPASecurityException | IOException | SEPAPropertiesException e) {
				logger.error(e.getMessage());
			}
		}
	}
	
	public static void main(String[] args) throws SEPAProtocolException, SEPAPropertiesException, InterruptedException, SEPASecurityException {
		if (args.length != 2) {
			logger.error("Usage: java -jar ObservationSimulator.jar <file.jsap> <observation URI>");
			System.exit(1);
		}

		ObservationSimulator sim = new ObservationSimulator(new JSAP(args[0]),args[1]);
		
		Thread th = new Thread(sim);
		th.start();
		th.join();
	}
}
