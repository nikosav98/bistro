package requests;

public class SeatingRequest {
	public enum SeatingRequestType{
		BY_CONFIRMATIONCODE,
		BY_RESERVATION,
	}
	private SeatingRequestType type;
	private int confirmationCode;
	private ReservationRequest reservation;
	
	public SeatingRequest() {};
	
	public SeatingRequest(SeatingRequestType type,int confirmationCode,ReservationRequest reservation) {

		this.confirmationCode = confirmationCode;
		this.reservation=reservation;
		this.type=type;
		
	}
	
	public ReservationRequest getReservation() {
		return reservation;
	}

	public void setReservation(ReservationRequest reservation) {
		this.reservation = reservation;
	}

	public int getConfirmationCode() {
		
		return confirmationCode;
	}
	public SeatingRequestType getType() {
		return this.type;
	}
	
	
}
