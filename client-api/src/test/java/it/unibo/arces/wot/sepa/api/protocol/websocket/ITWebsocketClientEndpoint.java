package it.unibo.arces.wot.sepa.api.protocol.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import it.unibo.arces.wot.sepa.ConfigurationProvider;
import it.unibo.arces.wot.sepa.api.ISubscriptionHandler;
import it.unibo.arces.wot.sepa.api.protocols.websocket.WebsocketClientEndpoint;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.response.ErrorResponse;
import it.unibo.arces.wot.sepa.commons.response.Notification;

public class ITWebsocketClientEndpoint implements ISubscriptionHandler {
	protected static final Logger logger = LogManager.getLogger();

	private static ConfigurationProvider provider;
	private static URI url;

	private static Object mutex = new Object();
	private static String spuid = null;
	private static boolean error = false;
	private static boolean results = false;

	@BeforeAll
	public static void init()
			throws SEPAPropertiesException, SEPASecurityException, InterruptedException, SEPAProtocolException {
		provider = new ConfigurationProvider();

		// Connect
		String scheme = "ws://";
		if (provider.getClientSecurityManager() != null)
			scheme = "wss://";
		if (provider.getJsap().getSubscribePort() == -1)
			try {
				url = new URI(scheme + provider.getJsap().getSubscribeHost() + provider.getJsap().getSubscribePath());
			} catch (URISyntaxException e) {
				logger.error(e.getMessage());
				throw new SEPAProtocolException(e);
			}
		else
			try {
				url = new URI(scheme + provider.getJsap().getSubscribeHost() + ":"
						+ provider.getJsap().getSubscribePort() + provider.getJsap().getSubscribePath());
			} catch (URISyntaxException e) {
				logger.error(e.getMessage());
				throw new SEPAProtocolException(e);
			}
	}

	@AfterAll
	public static void end() throws IOException {

	}

	@BeforeEach
	public void before() throws IOException {
		ITWebsocketClientEndpoint.spuid = null;
		ITWebsocketClientEndpoint.error = false;
		ITWebsocketClientEndpoint.results = false;
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void Connect() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException, IOException {
		WebsocketClientEndpoint client = new WebsocketClientEndpoint(provider.getClientSecurityManager(), this);
		client.connect(url);
		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void Subscribe() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException, IOException,
			InterruptedException {
		WebsocketClientEndpoint client = new WebsocketClientEndpoint(provider.getClientSecurityManager(), this);
		client.connect(url);
		client.send(provider.buildSubscribeRequest("ALL").toString());
		synchronized (mutex) {
			while (ITWebsocketClientEndpoint.spuid == null)
				mutex.wait();
		}
		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void SubscribeAndResults() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException,
			IOException, InterruptedException {
		WebsocketClientEndpoint client = new WebsocketClientEndpoint(provider.getClientSecurityManager(), this);
		client.connect(url);

		client.send(provider.buildSubscribeRequest("ALL").toString());
		synchronized (mutex) {
			while (ITWebsocketClientEndpoint.spuid == null)
				mutex.wait();
		}
		synchronized (mutex) {
			while (!ITWebsocketClientEndpoint.results)
				mutex.wait();
		}
		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void SubscribeAndUnsubscribe() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException,
			IOException, InterruptedException {
		WebsocketClientEndpoint client = new WebsocketClientEndpoint(provider.getClientSecurityManager(), this);
		client.connect(url);

		client.send(provider.buildSubscribeRequest("ALL").toString());
		synchronized (mutex) {
			while (ITWebsocketClientEndpoint.spuid == null)
				mutex.wait();
		}

		client.send(provider.buildUnsubscribeRequest(ITWebsocketClientEndpoint.spuid).toString());
		synchronized (mutex) {
			while (ITWebsocketClientEndpoint.spuid != null)
				mutex.wait();
		}

		client.close();
	}

	@RepeatedTest(ConfigurationProvider.REPEATED_TEST)
	@Timeout(10)
	public void WrongSubscribe() throws SEPASecurityException, SEPAPropertiesException, SEPAProtocolException,
			IOException, InterruptedException {
		WebsocketClientEndpoint client = new WebsocketClientEndpoint(provider.getClientSecurityManager(), this);
		client.connect(url);
		client.send(provider.buildSubscribeRequest("WRONG").toString());
		synchronized (mutex) {
			while (!ITWebsocketClientEndpoint.error)
				mutex.wait();
		}
		client.close();
	}

	@Override
	public void onSemanticEvent(Notification notify) {
		synchronized (mutex) {
			ITWebsocketClientEndpoint.results = true;
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
			ITWebsocketClientEndpoint.error = true;
			mutex.notify();
		}
	}

	@Override
	public void onSubscribe(String spuid, String alias) {
		synchronized (mutex) {
			ITWebsocketClientEndpoint.spuid = spuid;
			mutex.notify();
		}
	}

	@Override
	public void onUnsubscribe(String spuid) {
		synchronized (mutex) {
			ITWebsocketClientEndpoint.spuid = null;
			mutex.notify();
		}
	}
}
