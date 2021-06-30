package it.unibo.arces.wot.sepa.engine.dependability.authorization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map.Entry;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;

public class VirtuosoIsql implements IUsersAcl{
	protected static Logger logger = LogManager.getLogger();
	
	private final String endpointUsersPassword;
	private final ProcessBuilder ps;
	
	public VirtuosoIsql(IsqlProperties prop,String endpointUsersPassword) {
		this.endpointUsersPassword = endpointUsersPassword;
		
		ps = new ProcessBuilder(prop.getIsqlPath()+"isql",prop.getIsqlHost(),prop.getIsqlUser(),prop.getIsqlPass(),"command.sql");
		ps.redirectErrorStream(true);
	}
	
	public void createUser(String uid, JsonElement graphs) throws SEPASecurityException {
		logger.info("createUser "+uid+" "+graphs);
		new File("command.sql").delete();
		
		try {
			PrintWriter f = new PrintWriter(new BufferedWriter(new FileWriter("command.sql")));
			
			f.write("DB.DBA.USER_CREATE ('"+uid+"', '"+endpointUsersPassword+"');");
			f.println();
			
			f.write("DB.DBA.RDF_DEFAULT_USER_PERMS_SET ('nobody', 0);");
			f.println();
			
			f.write("DB.DBA.RDF_DEFAULT_USER_PERMS_SET ('"+uid+"', 0);");
			f.println();
			
			f.write("GRANT SPARQL_UPDATE TO \""+uid+"\";");
			f.println();
			
			for (Entry<String, JsonElement> graph : graphs.getAsJsonObject().entrySet()) {
				f.write("DB.DBA.RDF_GRAPH_USER_PERMS_SET ('"+graph.getKey()+"', '"+uid+"', "+graph.getValue().getAsInt()+");");
				f.println();
			}
			
			f.close();
			
			isql();
		} catch (IOException | InterruptedException e) {
			throw new SEPASecurityException(e.getMessage());
		}		
	}

	public void updateUser(String uid, JsonObject addGraphs, JsonArray removeGraphs) throws SEPASecurityException {
		logger.info("updateUser "+uid+" add:"+addGraphs+" remove:"+removeGraphs);
		
		if (new File("command.sql").exists()) new File("command.sql").delete();
		
		try {
			PrintWriter f = new PrintWriter(new BufferedWriter(new FileWriter("command.sql")));
			
			for (Entry<String, JsonElement> graph : addGraphs.entrySet()) {
				f.write("DB.DBA.RDF_GRAPH_USER_PERMS_SET ('"+graph.getKey()+"', '"+uid+"', "+graph.getValue().getAsInt()+");");
				f.println();
			}
			for (JsonElement graph : removeGraphs) {
				f.write("DB.DBA.RDF_GRAPH_USER_PERMS_SET ('"+graph.getAsString()+"', '"+uid+"', 0);");
				f.println();
			}
						
			f.close();
			
			isql();
		} catch (IOException | InterruptedException e) {
			throw new SEPASecurityException(e.getMessage());
		}		
	}

	public void removeUser(String uid) throws SEPASecurityException {
		logger.info("removeUser "+uid);
		
		if (new File("command.sql").exists()) new File("command.sql").delete();
		
		try {
			PrintWriter f = new PrintWriter(new BufferedWriter(new FileWriter("command.sql")));
			
			f.write("DB.DBA.USER_DROP ('"+uid+"', '"+endpointUsersPassword+"');");
					
			f.close();
			
			isql();
		} catch (IOException | InterruptedException e) {
			throw new SEPASecurityException(e.getMessage());
		}
		
	}
	
	private void isql() throws IOException, InterruptedException {
		logger.log(Level.getLevel("ldap"),"*** Execute isql *** ");

		Process pr = ps.start();

		BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));
		String line;
		while ((line = in.readLine()) != null) {
			logger.log(Level.getLevel("ldap"),line);
		}
		pr.waitFor();

		in.close();

		logger.log(Level.getLevel("ldap"),"*** Execute isql END ***");
	}
}
