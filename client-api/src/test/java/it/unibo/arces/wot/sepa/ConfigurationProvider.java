package it.unibo.arces.wot.sepa;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.SubscribeRequest;
import it.unibo.arces.wot.sepa.commons.request.UnsubscribeRequest;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.security.SEPASecurityManager;
import it.unibo.arces.wot.sepa.pattern.JSAP;

import java.io.File;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConfigurationProvider {
	protected final Logger logger = LogManager.getLogger();

	private final JSAP appProfile;
	private String prefixes = "";
	
	public ConfigurationProvider() throws SEPAPropertiesException, SEPASecurityException {
		String jsapFileName = "sepatest.jsap";

		if (System.getProperty("testConfiguration") != null) {
			jsapFileName = System.getProperty("testConfiguration");
			logger.info("JSAP from property testConfiguration: " + jsapFileName);
		} else if (System.getProperty("secure") != null) {
			jsapFileName = "sepatest-secure.jsap";
			logger.info("JSAP secure default: " + jsapFileName);
		}

		String path = getClass().getClassLoader().getResource(jsapFileName).getPath();
		File f = new File(path);
		if (!f.exists()) {
			logger.error("File not found: " + path);
			throw new SEPAPropertiesException("File not found: "+path);
		}
		
		appProfile = new JSAP(path);
		
		Set<String> appPrefixes = appProfile.getPrefixes();
		for (String prefix : appPrefixes) {
			prefixes += "PREFIX " + prefix + ":<" + appProfile.getNamespaceURI(prefix) + "> ";
		}
	}

	private String getSPARQLUpdate(String id) {
		return prefixes + " " +appProfile.getSPARQLUpdate(id);
	}
	
	private String getSPARQLQuery(String id) {
		return prefixes + " " +appProfile.getSPARQLQuery(id);
	}
	
	public UpdateRequest buildUpdateRequest(String id, long timeout,SEPASecurityManager sm) {
		String authorization = null;

		if (sm != null)
			try {
				authorization = sm.getAuthorizationHeader();
			} catch (SEPASecurityException | SEPAPropertiesException e) {
				logger.error(e.getMessage());
			}

		return new UpdateRequest(appProfile.getUpdateMethod(id), appProfile.getUpdateProtocolScheme(id),
				appProfile.getUpdateHost(id), appProfile.getUpdatePort(id), appProfile.getUpdatePath(id),
				getSPARQLUpdate(id), appProfile.getUsingGraphURI(id), appProfile.getUsingNamedGraphURI(id),
				authorization, timeout);
	}

	public QueryRequest buildQueryRequest(String id, long timeout,SEPASecurityManager sm) {
		String authorization = null;

		if (sm != null)
			try {
				authorization = sm.getAuthorizationHeader();
			} catch (SEPASecurityException | SEPAPropertiesException e) {
				logger.error(e.getMessage());
			}

		return new QueryRequest(appProfile.getQueryMethod(id), appProfile.getQueryProtocolScheme(id),
				appProfile.getQueryHost(id), appProfile.getQueryPort(id), appProfile.getQueryPath(id),
				getSPARQLQuery(id), appProfile.getDefaultGraphURI(id), appProfile.getNamedGraphURI(id),
				authorization, timeout);
	}

	public SubscribeRequest buildSubscribeRequest(String id, long timeout,SEPASecurityManager sm) {
		String authorization = null;		
		if (sm != null)
			try {
				authorization = sm.getAuthorizationHeader();
			} catch (SEPASecurityException | SEPAPropertiesException e) {
				logger.error(e.getMessage());
			}
		
		return new SubscribeRequest(getSPARQLQuery(id), id, appProfile.getDefaultGraphURI(id),
				appProfile.getNamedGraphURI(id), authorization, timeout);
	}

	public UnsubscribeRequest buildUnsubscribeRequest(String spuid, long timeout,SEPASecurityManager sm) {
		String authorization = null;		
		if (sm != null)
			try {
				authorization = sm.getAuthorizationHeader();
			} catch (SEPASecurityException | SEPAPropertiesException e) {
				logger.error(e.getMessage());
			}
		
		return new UnsubscribeRequest(spuid, authorization, timeout);
	}
	
	public JSAP getJsap() {
		return appProfile;
	}
}