package requests;

public class Request<T> {
	private Command command;
	private T data;
	
	public enum Command{
		GUEST_REQUEST,
		USER_REQUEST,
		RESERVATION_REQUEST,	
		SEATING_REQUEST,
		MANAGER_REQUEST,
		BILLING_REQUEST,
		REPORT_REQUEST,
		LOST_CODE
	}
	
	public Request() {}

	public Request(Command command, T data) {
		super();
		this.command = command;
		this.data = data;
	}

	public Command getCommand() {
		return command;
	}

	public T getData() {
		return data;
	}	
		 	
}
