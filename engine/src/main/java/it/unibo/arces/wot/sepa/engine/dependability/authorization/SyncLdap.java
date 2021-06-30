package it.unibo.arces.wot.sepa.engine.dependability.authorization;

import java.io.IOException;

import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;

public class SyncLdap implements IUsersSync {
	protected static Logger logger = LogManager.getLogger();

	private final LdapNetworkConnection ldap;
	private final LdapProperties prop;
	
	private EntryCursor cursor = null;
	private final String endpointPassword;
	private final String endpointPasswordUid;
	private final String usersUid;
	
	public SyncLdap(LdapProperties prop) throws SEPASecurityException {
		this.prop = prop;	
		this.ldap = new LdapNetworkConnection(prop.getHost(), prop.getPort());
		
		endpointPasswordUid = "uid=endpointUsersPassword," +prop.getUsersDN()+","+prop.getBase();
		usersUid = "ou=users," + (prop.getUsersDN() == null ? "" : prop.getUsersDN()+",") + prop.getBase();
		
		logger.log(Level.getLevel("ldap"),"endpointPasswordUid: "+endpointPasswordUid);
		logger.log(Level.getLevel("ldap"),"usersUid: "+usersUid);
		
		endpointPassword = retrievePassword();
		
		new Thread() {
			public void run() {
				try {
					sync();
				} catch (SEPASecurityException e) {
					logger.error(e.getMessage());
				}
			}
		}.start();
	}
	
	public String getEndpointUsersPassword() {
		return endpointPassword;
	}
	
	private String retrievePassword() throws SEPASecurityException {
		String ret = null;
		
		try {
			bind();
			
			logger.log(Level.getLevel("ldap"),"[LDAP] Sync LDAP "+ldap.getConfig().getLdapHost()+":"+ldap.getConfig().getLdapPort()+" Base DN: " + endpointPasswordUid);

			EntryCursor cursor = ldap.search(endpointPasswordUid, "(objectclass=simpleSecurityObject)", SearchScope.OBJECT);
			
			if (cursor.next()) {
				// Password has to be store as "plain text"
				ret = new String(cursor.get().get("userPassword").get().getBytes());
				logger.log(Level.getLevel("ldap"),"userPassword: "+ret);				
			}
			else throw new SEPASecurityException(endpointPasswordUid+" not found in LDAP");
		} catch (LdapException | CursorException  e) {
			throw new SEPASecurityException(e.getMessage());
		} finally {
			unbind();				
		}
		
		return ret;	
	}

	public JsonObject sync() throws SEPASecurityException {
		JsonObject ret = new JsonObject();
		
		try {
			bind();
			
			logger.log(Level.getLevel("ldap"),"[LDAP] Sync LDAP "+ldap.getConfig().getLdapHost()+":"+ldap.getConfig().getLdapPort()+" Base DN: " + usersUid);

			EntryCursor cursor = ldap.search(usersUid, "(objectclass=inetOrgPerson)", SearchScope.ONELEVEL);
			
			for (org.apache.directory.api.ldap.model.entry.Entry entry: cursor) {
				logger.log(Level.getLevel("ldap"),entry.toString("--"));
				
				if (entry.get("uid") == null) {
					logger.log(Level.getLevel("ldap"),"Missing *uid*");
					continue;
				}
				if (entry.get("description") == null) {
					logger.log(Level.getLevel("ldap"),"Missing *description* "+entry.get("uid"));
					continue;
				}
				String uid = entry.get("uid").getString();
				String description = entry.get("description").getString();
			
				ret.add(uid,new JsonParser().parse(description).getAsJsonObject());
			}
		} catch (LdapException | SEPASecurityException  e) {
			logger.error("[LDAP] LdapException|CursorException : " + e.getMessage());
		} finally {
			unbind();
				
		}
		
		return ret;
	}

	private void bind() throws SEPASecurityException {
		try {
			if (prop.isTls()) ldap.startTls();
		} catch (LdapException e1) {
			logger.error(e1.getMessage());
		}
		
		if (prop.getUser() != null)
			try {
				ldap.bind(prop.getUser(), prop.getPass());
			} catch (LdapException e) {
				logger.error("[LDAP] Exception on binding: " + e.getMessage());
				throw new SEPASecurityException("Exception on LDAP binding: " + e.getMessage());
			}
		else
			try {
				ldap.bind();
			} catch (LdapException e) {
				logger.error("[LDAP] Exception on binding: " + e.getMessage());
				throw new SEPASecurityException("Exception on LDAP binding: " + e.getMessage());
			}
	}

	private void unbind() throws SEPASecurityException {
		try {
			if (cursor != null)
				cursor.close();
			ldap.unBind();
		} catch (IOException e) {
			logger.error("[LDAP] IOException: " + e.getMessage());
			throw new SEPASecurityException(e.getMessage());
		} catch (LdapException e) {
			logger.error("[LDAP] LdapException: " + e.getMessage());
			throw new SEPASecurityException(e.getMessage());
		} finally {
			cursor = null;
		}
	}
}
