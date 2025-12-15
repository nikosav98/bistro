package responses;

public class LoginResponse {
	
	private String userID;
	private String role;
	private String employeeID;
	private String subscriberID;
	
	public LoginResponse() {}
	
	public LoginResponse(String userID, String role, String employeeID, String subscriberID) {
		this.userID = userID;
		this.role = role;
		this.employeeID = employeeID;
		this.subscriberID = subscriberID;
	}
	public String getUserID() {
		return userID;
	}

	public String getRole() {
		return role;
	}

	public String getEmployeeID() {
		return employeeID;
	}

	public String getSubscriberID() {
		return subscriberID;
	}
	
	
}
