package server;

import java.net.InetAddress;


/**
 * Represents runtime information about the server instance.
 * A ServerSession stores metadata such as the server's IP address,
 * host name, and startup time.
 */
public class ServerSession {
	
	private String hostIP;
	private String hostName;
	private long startTime;
	
	public ServerSession() {
		this.startTime = System.currentTimeMillis();
		try {
			InetAddress host = InetAddress.getLocalHost();
			this.hostIP = host.getHostAddress();
			this.hostName = host.getHostName();
		}catch(Exception e) {
			this.hostIP = "unknown";
			this.hostName = "unknown";
		}
	}

	public String getHostIP() {
		return hostIP;
	}

	public String getHostName() {
		return hostName;
	}

	public long getStartTime() {
		return startTime;
	}
	
	
}