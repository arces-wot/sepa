package it.unibo.arces.wot.sepa.api.protocol.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import it.unibo.arces.wot.sepa.ConfigurationProvider;
import it.unibo.arces.wot.sepa.api.ISubscriptionHandler;
import it.unibo.arces.wot.sepa.api.protocols.websocket.WebsocketSubscriptionProtocol;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.Notification;

public class ITWebSocketSubscriptionProtocol implements ISubscriptionHandler {
	protected static final Logger logger = LogManager.getLogger();

	private static ConfigurationProvider provider;	
	
	private static Object mutex = new Object();
	private static String spuid = null;
	private static boolean error = false;
	private static boolean results = false;
	
	@BeforeAll
	public static void init() throws SEPAPropertiesException, SEPASecurityException, InterruptedException, SEPAProtocolException {
		provider = new ConfigurationProvider();
	}
	
	@AfterAll
	public static void end() throws SEPAPropertiesException, SEPASecurityException, InterruptedException, IOException {
	
	}

	@BeforeEach
	public void before() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException {		
		ITWebSocketSubscriptionProtocol.spuid = null;
		ITWebSocketSubscriptionProtocol.error = false;
		ITWebSocketSubscriptionProtocol.results = false;
	}

	@AfterEach
	public void after() throws IOException, SEPAProtocolException, SEPASecurityException, SEPAPropertiesException, InterruptedException {		
		
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(5)
	public void Subscribe() throws SEPAPropertiesException, SEPASecurityException, SEPAProtocolException, IOException, InterruptedException {
		WebsocketSubscriptionProtocol client = new WebsocketSubscriptionProtocol(provider.getJsap().getSubscribeHost(), provider.getJsap().getSubscribePort(), provider.getJsap().getSubscribePath(), this, provider.getClientSecurityManager());
		client.subscribe(provider.buildSubscribeRequest("ALL"));

		synchronized (mutex) {
			while (ITWebSocketSubscriptionProtocol.spuid == null)
				mutex.wait();
		}
		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void SubscribeAndResults() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException,
			IOException, InterruptedException {
		WebsocketSubscriptionProtocol client = new WebsocketSubscriptionProtocol(provider.getJsap().getSubscribeHost(), provider.getJsap().getSubscribePort(), provider.getJsap().getSubscribePath(), this, provider.getClientSecurityManager());
		client.subscribe(provider.buildSubscribeRequest("ALL"));

		synchronized (mutex) {
			while (ITWebSocketSubscriptionProtocol.spuid == null)
				mutex.wait();
		}
		synchronized (mutex) {
			while (!ITWebSocketSubscriptionProtocol.results)
				mutex.wait();
		}
		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(5)
	public void SubscribeAndUnsubscribe() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException,
			IOException, InterruptedException {
		WebsocketSubscriptionProtocol client = new WebsocketSubscriptionProtocol(provider.getJsap().getSubscribeHost(), provider.getJsap().getSubscribePort(), provider.getJsap().getSubscribePath(), this, provider.getClientSecurityManager());
		client.subscribe(provider.buildSubscribeRequest("ALL"));
		
		synchronized (mutex) {
			while (ITWebSocketSubscriptionProtocol.spuid == null)
				mutex.wait();
		}

		client.unsubscribe(provider.buildUnsubscribeRequest(ITWebSocketSubscriptionProtocol.spuid));
		synchronized (mutex) {
			while (ITWebSocketSubscriptionProtocol.spuid != null)
				mutex.wait();
		}

		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void WrongSubscribe() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException,
			IOException, InterruptedException {
		WebsocketSubscriptionProtocol client = new WebsocketSubscriptionProtocol(provider.getJsap().getSubscribeHost(), provider.getJsap().getSubscribePort(), provider.getJsap().getSubscribePath(), this, provider.getClientSecurityManager());
		
		client.subscribe(provider.buildSubscribeRequest("WRONG"));
		
		synchronized (mutex) {
			while (!ITWebSocketSubscriptionProtocol.error)
				mutex.wait();
		}
		client.close();
	}

	@Override
	public void onSemanticEvent(Notification notify) {
		synchronized (mutex) {
			ITWebSocketSubscriptionProtocol.results = true;
			mutex.notify();
		}
	}

	@Override
	public void onBrokenConnection(ErrorResponse errorResponse) {
		assertFalse(true, errorResponse.toString());
	}

	@Override
	public void onError(ErrorResponse errorResponse) {
		synchronized (mutex) {
			ITWebSocketSubscriptionProtocol.error = true;
			mutex.notify();
		}
	}

	@Override
	public void onSubscribe(String spuid, String alias) {
		synchronized (mutex) {
			ITWebSocketSubscriptionProtocol.spuid = spuid;
			mutex.notify();
		}
	}

	@Override
	public void onUnsubscribe(String spuid) {
		synchronized (mutex) {
			ITWebSocketSubscriptionProtocol.spuid = null;
			mutex.notify();
		}
	}
}
