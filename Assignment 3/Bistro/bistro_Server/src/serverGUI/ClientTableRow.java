package serverGUI;

import javafx.beans.property.*;

/**
 * Represents a single row in the server GUI client table.
 * This class acts as a JavaFX view-model and wraps client connection
 * and authentication details using observable properties.
 * 
 * 
 */
public class ClientTableRow {
	
	private final SimpleStringProperty userID = new SimpleStringProperty("-");
	private final SimpleStringProperty username = new SimpleStringProperty("-");	
	private final SimpleStringProperty role = new SimpleStringProperty("-");
	private final SimpleStringProperty ip = new SimpleStringProperty("-");
	
	public ClientTableRow(String ip) {
		this.ip.set(ip);
	}
	
	public StringProperty userIdProperty() {return userID;}
	public StringProperty usernameProperty() {return username;}
	public StringProperty roleProperty() {return role;}
	public StringProperty ipProperty() {return ip;}
	
	
	public String getIp() { return ipProperty().get(); }
	public String getUserId() { return userID.get(); }
	public String getUsername() { return username.get(); }
	public String getRole() { return role.get(); }

	
	public void setUserId(String id) {this.userID.set(id);};
	public void setUsername(String username) {this.username.set(username);}
	public void setRole(String role) {this.role.set(role);}
	public void setIp(String ip) { this.ip.set(ip); }
	
	
}
