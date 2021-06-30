package it.unibo.arces.wot.sepa.engine.dependability.authorization;

public class IsqlProperties {
	private final String isqlPath;
	private final String isqlHost;
	private final String isqlUser;
	private final String isqlPass;
	private final int isqlPort;
	
	public IsqlProperties(String path,String host,int port, String uid,String pwd) {
		this.isqlHost = host;
		this.isqlPass = pwd;
		this.isqlPath = path;
		this.isqlUser = uid;
		this.isqlPort = port;
	}

	public String getIsqlPath() {
		return isqlPath;
	}

	public String getIsqlHost() {
		return isqlHost+":"+isqlPort;
	}

	public String getIsqlUser() {
		return isqlUser;
	}

	public String getIsqlPass() {
		return isqlPass;
	}
}
