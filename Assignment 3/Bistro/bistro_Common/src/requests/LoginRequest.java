package requests;

public class LoginRequest {
	
	
	private UserCommand userCommand;
	
	//for logging in
	private String username;//make it hold guest contact info
	private String password;
	
	//for editing
	private String phone;
	private String email;
	
	
	public enum UserCommand{
		LOGIN_REQUEST,
		SHOW_DETAILS_REQUEST,
		EDIT_DETAIL_REQUEST,
		HISTORY_REQUEST,
		UPCOMING_RESERVATIONS_REQUEST,
		
		
	}
	public LoginRequest() {
		
	}
	
	public LoginRequest(String phone,String email) {
		this.phone = phone;
		this.email = email;
	}

	public LoginRequest(String username, String password,UserCommand userCommand) {
		this.username = username;
		this.password = password;
		this.userCommand = userCommand;
	}

	public void setUserCommand(UserCommand userCommand) {
		this.userCommand = userCommand;
	}

	public String getUsername() {
		return username;
	}

	public UserCommand getUserCommand() {
		return userCommand;
	}

	public String getPassword() {
		return password;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}
	
	
}
