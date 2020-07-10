/* LDAP based authorization
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

package it.unibo.arces.wot.sepa.engine.dependability.authorization;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import org.apache.directory.api.ldap.model.constants.LdapSecurityConstants;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.password.PasswordUtil;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.nimbusds.jwt.SignedJWT;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.security.Credentials;
import it.unibo.arces.wot.sepa.engine.dependability.authorization.identities.ApplicationIdentity;
import it.unibo.arces.wot.sepa.engine.dependability.authorization.identities.DeviceIdentity;
import it.unibo.arces.wot.sepa.engine.dependability.authorization.identities.DigitalIdentity;
import it.unibo.arces.wot.sepa.engine.dependability.authorization.identities.UserIdentity;

/**
 * The directory structure follows. Possible options are evidenced as "***"
 * 
 * <pre>
 *
 ### ROOT ###
dn: dc=vaimee,dc=com
|- objectclass: top
|- objectclass: domain
|- dc: vaimee

dn: o=vaimee
|- objectclass: extensibleObject
|- objectclass: top
|- objectclass: domain
|- dc: vaimee
|- o: vaimee


### Authorized identities (for applications and devices) ###
dn: ou=authorizedIdentities,o=vaimee
|- objectClass: organizationalUnit
|- objectClass: top
|- ou: authorizedIdentities

dn: uid=SEPATest,ou=authorizedIdentities,o=vaimee
|- *** objectClass: applicationProcess | device
|- objectClass: uidObject
|- objectClass: top
|- cn: TEST
|- uid: SEPATest

### Credentials (include users) ###
dn: ou=credentials,o=vaimee
|- objectClass: organizationalUnit
|- objectClass: top
|- ou: credentials

dn: uid=SEPATest,ou=credentials,o=vaimee
|- *** objectClass: applicationProcess | device
|- uid: SEPATest
|- userPassword: {SSHA}/hybr9FEohB6Y6sxn2K+/V8HTzbpKxGa8RxxSQ==
|- ObjectClass: uidObject
|- ObjectClass: javaSerializedObject
|- ObjectClass: simpleSecurityObject
|- ObjectClass: top
|- ObjectClass: javaObject
|- cn: Authorized Digital Identity SEPATest
|- javaClassName: it.unibo.arces.wot.sepa.engine.dependability.authorization.Credentials
|- javaSerializedData:: 77+977+9AAVzcgBGaXQudW5pYm8uYXJjZXMud290LnNlcGEuZW5naW5
 						lLmRlcGVuZGFiaWxpdHkuYXV0aG9yaXphdGlvbi5DcmVkZW50aWFsc2dPZ++/vRo9160CAAJMAA
 						hwYXNzd29yZHQAEkxqYXZhL2xhbmcvU3RyaW5nO0wABHVzZXJxAH4AAXhwdAAIU0VQQVRlc3RxA
 						H4AAw==

dn: uid=luca.roffia@vaimee.it,ou=credentials,o=vaimee
|- *** objectClass: inetOrgPerson
|- *** cn: Luca
|- *** sn: Roffia
|- uid: luca.roffia@vaimee.it
|- userPassword: {SSHA}/abvbr8FEohB6Y6sxn2K+/V8HTzbpKxGa8RxxXT==
|- ObjectClass: uidObject
|- ObjectClass: javaSerializedObject
|- ObjectClass: simpleSecurityObject
|- ObjectClass: top
|- ObjectClass: javaObject
|- javaClassName: it.unibo.arces.wot.sepa.engine.dependability.authorization.Credentials
|- javaSerializedData:: 66+966+9BBVzcgBGBXQudp5pYm8uYXJjZXMud290LnNlcGEuZp5nBp5
 						lLmRlcGVuZGFiBpxpdHkuYXV0BG9yBXphdGlvbi5DcmVkZp50BpFsc2dPZ++/vRo9160CBBJMBB
 						hpYXNzd29yZHQBEkxqYXZhL2xhbmcvU3RyBp5nO0pBBHVzZXJxBH4BBXhpdBBIU0VQQVRlc3RxB
 						H4BBp==


### Tokens ###
|- dn: ou=tokens,o=vaimee
|- objectClass: organizationalUnit
|- objectClass: top
|- ou: tokens

dn: uid=SEPATest,ou=tokens,o=vaimee
|- uid: SEPATest
|- objectclass: account
|- objectclass: javaSerializedObject
|- objectclass: top
|- objectclass: javaObject
|- javaclassname: com.nimbusds.jwt.SignedJWT
|- javaSerializedData: eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJodHRwOlwvXC9zdWJqZWN0Iiw
 						iYXVkIjpbImh0dHBzOlwvXC9hdWRpZW5jZSIsImF1ZGllbmNlIl0sImlzcyI6Imh0dHA6XC9cL2
 						lzc3VlciIsImV4cCI6MTU3MTY3MzQxNSwiaWF0IjoxNTcxNjczNDEwLCJqdGkiOiJTRVBBVGVzd
 						CJ9.fPpCRUle3g49HMJIHWgzqEUDUEQ5ukg4ffp7y6yRT3hSMpp95MMgNRcOLPJFhlVvWAHxoag
 						DW6g6hI85Ks7XFfL4yqIwogOt5cgprVYSCCvOKXk4j7VNrb2aaNcAq8Y3oD2LgX6BDzMG37VlJ3
 						TXwKNZSsm6WlDMRIiiWooFeb_Ystmtt2x38ksIWO3oZ3K0se3sUiSqUD6M0BArOJXydX7TyfgIU
 						Vy4zWRsqLUwTOl0f_ReWHi1lwMi8ex-gucj-byy7QCXHLYLYx6KfUP72XhFObpf2Asc0cUd2O_C
 						3DvtWlcHCbK0MV4YoEf0cQYyqmf6oDRpOlepijodi9tTDQ

### JWT ###
dn: ou=jwt,o=vaimee
|- objectClass: organizationalUnit
|- objectClass: top
|- ou: jwt

dn: uid=expiring,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- uid: expiring

dn: uid=SEPATest,uid=expiring,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- objectClass: pwdPolicy
|- pwdAttribute: userPassword
|- uid: SEPATest
|- pwdGraceExpire: 5

dn: uid=device,uid=expiring,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- objectClass: pwdPolicy
|- pwdAttribute: userPassword
|- uid: device
|- description: Default expiring time for devices (i.e., 1h)
|- pwdGraceExpire: 3600

dn: uid=application,uid=expiring,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- objectClass: pwdPolicy
|- pwdAttribute: userPassword
|- uid: application
|- description: Default expiring time for applications (i.e., 24h)
|- pwdGraceExpire: 43200

dn: uid=default,uid=expiring,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- objectClass: pwdPolicy
|- pwdAttribute: userPassword
|- uid: default
|- pwdGraceExpire: 5

dn: uid=user,uid=expiring,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- objectClass: pwdPolicy
|- pwdAttribute: userPassword
|- uid: user
|- description: Default expiring time for users (i.e., 5 minutes)
|- pwdGraceExpire: 300

dn: uid=subject,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- uid: subject
|- host: http://subject

dn: uid=httpsAudience,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- uid: httpsAudience
|- host: https://audience

dn: uid=wssAudience,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- uid: wssAudience
|- host: audience

dn: uid=issuer,ou=jwt,o=vaimee
|- objectClass: account
|- objectClass: top
|- uid: issuer
|- host: http://issuer
 * </pre>
 */
public class LdapAuthorization implements IAuthorization {
	protected static Logger logger = LogManager.getLogger();

	private final LdapConnection ldap;
	private final String ldapRoot;
	private final String pwd;
	private final String user;

	EntryCursor cursor = null;

	public LdapAuthorization(String host, int port, String base, String user, String pwd) throws LdapException {
		if (user != null && pwd == null)
			throw new LdapException("Password is null for user: " + user);
		ldap = new LdapNetworkConnection(host, port);
		this.user = user;
		this.pwd = pwd;
		ldapRoot = base;
	}

	public LdapAuthorization() {
		ldap = new LdapNetworkConnection("localhost", 10389);
		this.user = null;
		this.pwd = null;
		ldapRoot = "dc=example,dc=com";
	}

	private void bind() throws SEPASecurityException {
		if (user != null)
			try {
				ldap.bind(user, pwd);
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

	@Override
	public Credentials getEndpointCredentials(String uid) throws SEPASecurityException {
		bind();

		try {
			logger.debug("[LDAP] getEndpointCredentials Base DN: " + "uid=" + uid + ",ou=credentials," + ldapRoot);

			cursor = ldap.search("uid=" + uid + ",ou=credentials," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT,
					"*");

			if (cursor.next()) {
				Attribute attr = cursor.get().get("javaSerializedData");

				if (attr != null) {
					byte[] cred = attr.getBytes();
					Credentials auth = Credentials.deserialize(cred);
					// TODO: WARNING. PRINTING CREDENTIALS just for debugging purposes
					logger.debug("[LDAP] " + auth);
					return auth;
				}
			}
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] LdapException|CursorException : " + e.getMessage());
			throw new SEPASecurityException(e.getMessage());
		} finally {
			unbind();
		}

		return null;
	}

	@Override
	public void addAuthorizedIdentity(DigitalIdentity identity) throws SEPASecurityException {
		logger.debug("[LDAP] addIdentity uid=" + identity.getUid() + " class: " + identity.getObjectClass());

		bind();

		try {
			Entry entry = new DefaultEntry("uid=" + identity.getUid() + ",ou=authorizedIdentities," + ldapRoot);
			entry.add("ObjectClass", "uidObject");
			entry.add("ObjectClass", "top");
			entry.add("ObjectClass", "javaSerializedObject");
			entry.add("ObjectClass", identity.getObjectClass());

			entry.add("cn", "Authorized Digital Identity");
			entry.add("uid", identity.getUid());

			entry.add("javaClassName", identity.getEndpointCredentials().getClass().getName());
			entry.add("javaSerializedData", identity.getEndpointCredentials().serialize());

			ldap.add(entry);
		} catch (LdapException e) {
			logger.error("[LDAP] addAuthorizedIdentity exception: " + e.getMessage());
			throw new SEPASecurityException("addIdentity exception: " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void removeAuthorizedIdentity(String id) throws SEPASecurityException {
		logger.debug("[LDAP] removeIdentity " + "uid=" + id + ",ou=authorizedIdentities," + ldapRoot);

		bind();

		try {
			ldap.delete("uid=" + id + ",ou=authorizedIdentities," + ldapRoot);
		} catch (LdapException e) {
			logger.error("[LDAP] Exception on removing identity: " + "uid=" + id + ",ou=authorizedIdentities,"
					+ ldapRoot + " " + e.getMessage());
			if (logger.isTraceEnabled())
				e.printStackTrace();
			throw new SEPASecurityException(
					"Exception on removing identity: " + "uid=" + id + ",ou=authorizedIdentities," + ldapRoot);
		} finally {
			unbind();
		}
	}

	@Override
	public boolean storeCredentials(DigitalIdentity identity, String client_secret) throws SEPASecurityException {
		logger.debug("[LDAP] storeCredentials " + identity + " secret: " + client_secret);

		byte[] password = PasswordUtil.createStoragePassword(client_secret.getBytes(),
				LdapSecurityConstants.HASH_METHOD_SSHA);

		bind();

		try {
			cursor = ldap.search("uid=" + identity.getUid() + ",ou=credentials," + ldapRoot, "(objectclass=*)",
					SearchScope.OBJECT, "*");

			if (cursor.next())
				removeCredentials(identity);

			Entry entry = new DefaultEntry("uid=" + identity.getUid() + ",ou=credentials," + ldapRoot);
			entry.add("ObjectClass", "top");
			entry.add("ObjectClass", identity.getObjectClass());
			entry.add("ObjectClass", "uidObject");
			entry.add("ObjectClass", "simpleSecurityObject");
			entry.add("ObjectClass", "javaSerializedObject");

			if (identity.getObjectClass().equals("inetOrgPerson")) {
				entry.add("cn", ((UserIdentity) identity).getCommonName());
				entry.add("sn", ((UserIdentity) identity).getSurname());
			} else
				entry.add("cn", "Authorized Digital Identity " + identity.getUid());

			entry.add("uid", identity.getUid());
			entry.add("userPassword", password);

			entry.add("javaClassName", identity.getEndpointCredentials().getClass().getName());
			entry.add("javaSerializedData", identity.getEndpointCredentials().serialize());

			ldap.add(entry);

		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] storeCredentials exception " + e.getMessage());
			throw new SEPASecurityException("storeCredentials exception " + e.getMessage());
		} finally {
			unbind();
		}

		return true;
	}

	@Override
	public boolean checkCredentials(String uid, String secret) throws SEPASecurityException {
		logger.debug(
				"[LDAP] checkCredentials " + uid + " secret: " + secret + " uid=" + uid + ",ou=credentials," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=credentials," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT,
					"*");
			if (cursor.next()) {
				return PasswordUtil.compareCredentials(secret.getBytes(),
						cursor.get().get("userPassword").get().getBytes());
			} else
				return false;
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] checkCredentials exception " + e.getMessage());
			throw new SEPASecurityException("checkCredentials exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void removeCredentials(DigitalIdentity identity) throws SEPASecurityException {
		logger.debug("[LDAP] removeCredentials " + "uid=" + identity.getUid() + ",ou=credentials," + ldapRoot);

		bind();

		try {
			ldap.delete("uid=" + identity.getUid() + ",ou=credentials," + ldapRoot);
		} catch (LdapException e) {
			logger.error("[LDAP] checkCredentials exception " + e.getMessage());
			throw new SEPASecurityException("checkCredentials exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void removeToken(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] removeToken " + "uid=" + uid + ",ou=tokens," + ldapRoot);

		bind();

		try {
			ldap.delete("uid=" + uid + ",ou=tokens," + ldapRoot);
		} catch (LdapException e) {
			logger.error("[LDAP] removeToken exception " + e.getMessage());
			throw new SEPASecurityException("removeToken exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public boolean containsCredentials(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] containsCredentials " + "uid=" + uid + ",ou=credentials," + ldapRoot, "(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=credentials," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT,
					"*");
			return cursor.next();
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] checkCredentials exception " + e.getMessage());
			throw new SEPASecurityException("checkCredentials exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public long getTokenExpiringPeriod(String id) throws SEPASecurityException {
		bind();

		try {
			if (id == null) {
				logger.debug("[LDAP] getTokenExpiringPeriod " + "uid=default,uid=expiring,ou=jwt," + ldapRoot,
						"(objectclass=*)");

				cursor = ldap.search("uid=default,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)",
						SearchScope.OBJECT, "*");
				if (!cursor.next())
					throw new SEPASecurityException("uid=default,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");

			} else {
				if (id.equals("SEPATest")) {
					logger.debug("[LDAP] getTokenExpiringPeriod " + "uid=" + id + ",uid=expiring,ou=jwt," + ldapRoot,
							"(objectclass=*)");

					cursor = ldap.search("uid=" + id + ",uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)",
							SearchScope.OBJECT, "*");
				} else {
					logger.debug("[LDAP] getTokenExpiringPeriod " + "uid=" + id + ",ou=credentials," + ldapRoot,
							"(objectclass=*)");

					cursor = ldap.search("uid=" + id + ",ou=credentials," + ldapRoot, "(objectclass=*)",
							SearchScope.OBJECT, "*");
				}
				if (!cursor.next())
					throw new LdapException("uid=" + id + ",ou=credentials," + ldapRoot + " NOT FOUND");

				Entry entry = cursor.get();

				if (!entry.containsAttribute("pwdGraceExpire")) {
					cursor.close();

					// APPLICATION
					if (entry.hasObjectClass("applicationProcess")) {
						cursor = ldap.search("uid=application,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)",
								SearchScope.OBJECT, "*");

						if (!cursor.next())
							throw new LdapException("uid=application,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
					}

					// DEVICE
					else if (entry.hasObjectClass("device")) {
						cursor = ldap.search("uid=device,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)",
								SearchScope.OBJECT, "*");

						if (!cursor.next())
							throw new LdapException("uid=device,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
					}

					// USER
					else if (entry.hasObjectClass("inetOrgPerson")) {
						cursor = ldap.search("uid=user,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)",
								SearchScope.OBJECT, "*");

						if (!cursor.next())
							throw new LdapException("uid=user,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
					} else
						throw new LdapException("ClassObject for " + id
								+ " MUST BE one of the following: device, applicationProcess, inetOrgPerson");
				}
			}

			Entry entry = cursor.get();
			Attribute attr = entry.get("pwdGraceExpire");
			return Long.parseLong(attr.get().getString());
		} catch (LdapException | CursorException | IOException e) {
			logger.error("[LDAP] getTokenExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("getTokenExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}

	}

	@Override
	public void setTokenExpiringPeriod(String uid, long period) throws SEPASecurityException {
		logger.debug("[LDAP] setTokenExpiringPeriod " + uid + " period: " + period + " uid=" + uid + ",ou=credentials,"
				+ ldapRoot);

		bind();

		try {
			Modification pwdGraceExpire = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE,
					"pwdGraceExpire", String.format("%d", period));
			Modification pwdPolicy = new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "objectClass",
					"pwdPolicy");
			Modification pwdAttribute = new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, "pwdAttribute",
					"userPassword");

			ldap.modify("uid=" + uid + ",ou=credentials," + ldapRoot, pwdGraceExpire, pwdPolicy, pwdAttribute);

		} catch (LdapException e) {
			logger.error("[LDAP] setTokenExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("setTokenExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public String getIssuer() throws SEPASecurityException {
		logger.debug("[LDAP] getIssuer " + "uid=issuer,ou=jwt," + ldapRoot, "(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=issuer,ou=jwt," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT, "*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=issuer,ou=jwt," + ldapRoot + " NOT FOUND");
			if (cursor.get().get("host") == null)
				throw new SEPASecurityException("issuer host not found");

			return cursor.get().get("host").getString();
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] getIssuer exception " + e.getMessage());
			throw new SEPASecurityException("getIssuer exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void setIssuer(String issuer) throws SEPASecurityException {
		logger.debug("[LDAP] setIssuer " + issuer + " uid=issuer,ou=jwt," + ldapRoot);

		bind();

		try {
			Modification replaceGn = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "host", issuer);
			ldap.modify("uid=issuer,ou=jwt," + ldapRoot, replaceGn);
		} catch (LdapException e) {
			logger.error("[LDAP] setIssuer exception " + e.getMessage());
			throw new SEPASecurityException("setIssuer exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public boolean isAuthorized(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] isAuthorized " + uid + " uid=" + uid + ",ou=authorizedIdentities," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=authorizedIdentities," + ldapRoot, "(objectclass=*)",
					SearchScope.OBJECT, "*");
			return cursor.next();
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] isAuthorized exception " + e.getMessage());
			throw new SEPASecurityException("isAuthorized exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	/*
	 * An identity is defined for testing if cn = TEST This entity should be used
	 * only for debugging. A testing identity is not deleted after registration:
	 * avoid in production!
	 * 
	 * uid: uid ou: authorizedIdentities cn: SEPATest
	 */
	@Override
	public boolean isForTesting(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] isForTesting " + uid + " uid=" + uid + ",ou=authorizedIdentities," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=authorizedIdentities," + ldapRoot, "(objectclass=*)",
					SearchScope.OBJECT, "*");
			if (!cursor.next())
				return false;
			return cursor.get().get("cn").getString().equals("SEPATest");
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] isAuthorized exception " + e.getMessage());
			throw new SEPASecurityException("isAuthorized exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void addToken(String uid, SignedJWT token) throws SEPASecurityException {
		logger.debug("[LDAP] addToken " + uid + " uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT, "*");
			if (!cursor.next()) {
				ldap.add(new DefaultEntry("uid=" + uid + ",ou=tokens," + ldapRoot, "ObjectClass: top",
						"ObjectClass: account", "ObjectClass: javaSerializedObject",
						"javaClassName: " + token.getClass().getName(), "javaSerializedData: " + token.serialize()));
			} else {
				Modification replaceGn = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE,
						"javaSerializedData", token.serialize());

				ldap.modify("uid=" + uid + ",ou=tokens," + ldapRoot, replaceGn);
			}
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] addToken exception " + e.getMessage());
			throw new SEPASecurityException("addToken exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public boolean containsToken(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] containsToken " + uid + " uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT, "*");
			return cursor.next();
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] containsToken exception " + e.getMessage());
			throw new SEPASecurityException("containsToken exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public Date getTokenExpiringDate(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] getTokenExpiringDate " + uid + " uid=" + uid + ",ou=tokens," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT, "*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=" + uid + ",ou=tokens," + ldapRoot + " NOT FOUND");
			SignedJWT jwt = SignedJWT.parse(cursor.get().get("javaSerializedData").getString());
			return jwt.getJWTClaimsSet().getExpirationTime();
		} catch (LdapException | CursorException | ParseException e) {
			logger.error("[LDAP] getTokenExpiringDate exception " + e.getMessage());
			throw new SEPASecurityException("getTokenExpiringDate exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public SignedJWT getToken(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] getToken " + uid + " uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=tokens," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT, "*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=" + uid + ",ou=tokens," + ldapRoot + " NOT FOUND");
			return SignedJWT.parse(cursor.get().get("javaSerializedData").getString());
		} catch (LdapException | CursorException | ParseException e) {
			logger.error("[LDAP] getToken exception " + e.getMessage());
			throw new SEPASecurityException("getToken exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public DigitalIdentity getIdentity(String uid) throws SEPASecurityException {
		logger.debug("[LDAP] getIdentity " + uid + " uid=" + uid + ",ou=authorizedIdentities," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=" + uid + ",ou=authorizedIdentities," + ldapRoot, "(objectclass=*)",
					SearchScope.OBJECT, "*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=" + uid + ",ou=authorizedIndentities," + ldapRoot + " NOT FOUND");

			// SPARQL endpoint credentials are stored as Java Serialized Object
			Credentials credentials = null;
			if (cursor.get().contains("objectClass", "javaSerializedObject")) {
				credentials = Credentials.deserialize(cursor.get().get("javaSerializedData").getBytes());
			}

			if (cursor.get().contains("objectClass", "device"))
				return new DeviceIdentity(uid, credentials);
			else if (cursor.get().contains("objectClass", "applicationProcess"))
				return new ApplicationIdentity(uid, credentials);
			else
				throw new SEPASecurityException("Digital identity class NOT FOUND");
		} catch (LdapException | CursorException e) {
			logger.error("[LDAP] getIdentity exception " + e.getMessage());
			throw new SEPASecurityException("getIdentity exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void setDeviceExpiringPeriod(long period) throws SEPASecurityException {
		logger.debug("[LDAP] setDeviceExpiringPeriod " + period + " uid=device,uid=expiring,ou=jwt," + ldapRoot);

		bind();

		try {
			Modification expiring = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "pwdGraceExpire");
			ldap.modify("uid=device,uid=expiring,ou=jwt," + ldapRoot, expiring);

		} catch (LdapException e) {
			logger.error("setDeviceExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("setDeviceExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public long getDeviceExpiringPeriod() throws SEPASecurityException {
		logger.debug("[LDAP] getDeviceExpiringPeriod " + "uid=device,uid=expiring,ou=jwt," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=device,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT,
					"*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=device,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
			if (cursor.get().get("pwdGraceExpire") == null)
				throw new SEPASecurityException(
						"uid=device,uid=expiring,ou=jwt," + ldapRoot + " pwdGraceExpire NOT FOUND");

			return Long.parseLong(cursor.get().get("pwdGraceExpire").getString());
		} catch (LdapException | CursorException e) {
			logger.error("getDeviceExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("getDeviceExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void setApplicationExpiringPeriod(long period) throws SEPASecurityException {
		logger.debug(
				"[LDAP] setApplicationExpiringPeriod " + period + " uid=application,uid=expiring,ou=jwt," + ldapRoot);

		bind();

		try {
			Modification expiring = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "pwdGraceExpire");
			ldap.modify("uid=application,uid=expiring,ou=jwt," + ldapRoot, expiring);
		} catch (LdapException e) {
			logger.error("setApplicationExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("setApplicationExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public long getApplicationExpiringPeriod() throws SEPASecurityException {
		logger.debug("[LDAP] getApplicationExpiringPeriod " + "uid=application,uid=expiring,ou=jwt," + ldapRoot);

		bind();

		try {
			cursor = ldap.search("uid=application,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)",
					SearchScope.OBJECT, "*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=application,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
			if (cursor.get().get("pwdGraceExpire") == null)
				throw new SEPASecurityException(
						"uid=application,uid=expiring,ou=jwt," + ldapRoot + " pwdGraceExpire NOT FOUND");

			return Long.parseLong(cursor.get().get("pwdGraceExpire").getString());
		} catch (LdapException | CursorException e) {
			logger.error("getApplicationExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("getApplicationExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void setUserExpiringPeriod(long period) throws SEPASecurityException {
		logger.debug("[LDAP] setUserExpiringPeriod " + period + " uid=user,uid=expiring,ou=jwt," + ldapRoot);

		bind();

		try {
			Modification expiring = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "pwdGraceExpire");
			ldap.modify("uid=user,uid=expiring,ou=jwt," + ldapRoot, expiring);
		} catch (LdapException e) {
			logger.error("setUserExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("setUserExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}

	}

	@Override
	public long getUserExpiringPeriod() throws SEPASecurityException {
		logger.debug("[LDAP] getUserExpiringPeriod " + "uid=user,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=user,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT,
					"*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=user,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
			if (cursor.get().get("pwdGraceExpire") == null)
				throw new SEPASecurityException(
						"uid=user,uid=expiring,ou=jwt," + ldapRoot + " pwdGraceExpire NOT FOUND");

			return Long.parseLong(cursor.get().get("pwdGraceExpire").getString());
		} catch (LdapException | CursorException e) {
			logger.error("getUserExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("getUserExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public void setDefaultExpiringPeriod(long period) throws SEPASecurityException {
		logger.debug("[LDAP] setDefaultExpiringPeriod " + period + " uid=default,uid=expiring,ou=jwt," + ldapRoot);

		bind();

		try {
			Modification expiring = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, "pwdGraceExpire");
			ldap.modify("uid=default,uid=expiring,ou=jwt," + ldapRoot, expiring);
		} catch (LdapException e) {
			logger.error("setDefaultExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("setDefaultExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}

	@Override
	public long getDefaultExpiringPeriod() throws SEPASecurityException {
		logger.debug("[LDAP] getDefaultExpiringPeriod " + "uid=default,uid=expiring,ou=jwt," + ldapRoot,
				"(objectclass=*)");

		bind();

		try {
			cursor = ldap.search("uid=default,uid=expiring,ou=jwt," + ldapRoot, "(objectclass=*)", SearchScope.OBJECT,
					"*");
			if (!cursor.next())
				throw new SEPASecurityException("uid=default,uid=expiring,ou=jwt," + ldapRoot + " NOT FOUND");
			if (cursor.get().get("pwdGraceExpire") == null)
				throw new SEPASecurityException(
						"uid=default,uid=expiring,ou=jwt," + ldapRoot + " pwdGraceExpire NOT FOUND");

			Attribute attr = cursor.get().get("pwdGraceExpire");
			return Long.parseLong(attr.getString());
		} catch (LdapException | CursorException e) {
			logger.error("getDefaultExpiringPeriod exception " + e.getMessage());
			throw new SEPASecurityException("getDefaultExpiringPeriod exception " + e.getMessage());
		} finally {
			unbind();
		}
	}
}
