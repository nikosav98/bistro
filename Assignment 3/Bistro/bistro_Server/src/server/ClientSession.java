package server;

/**
 * Represents a session associated with a connected client.
 * A ClientSession stores runtime information about a client connection,
 * including its IP address and authenticated user details after login.
 */

public class ClientSession {
	
	private String ip;
	private String userID;
	private String username;
    private String role;
	
	public ClientSession() {}

	public ClientSession(String ip) {
		this.ip = ip;
	}

	public String getIp() { return ip; }
    public String getUserId() { return userID; }
    public String getUsername() { return username; }
    public String getRole() { return role; }

    public void setUserId(String userId) { this.userID = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setRole(String role) { this.role = role; }
	
}
