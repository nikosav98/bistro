package responses;

import java.time.LocalDate;
import java.time.LocalTime;

public class UserHistoryResponse {

	private LocalDate reservationDate;
	private LocalTime reservedForTime;
	private LocalTime checkInTime;
	private LocalTime checkOutTime;
	private int tableNumber;
	private Double totalPrice;
	private int partySize;
	
	private int confirmationCode;
	
	public UserHistoryResponse() {}
	
	public UserHistoryResponse(LocalDate resDate, int partySize,LocalTime startTime,int confirmationCode){
		this.reservationDate =resDate;
		this.partySize = partySize;
		this.checkInTime = startTime;
		this.confirmationCode=confirmationCode;
	}
	
	public UserHistoryResponse(LocalDate reservationDate, LocalTime reservedForTime, LocalTime checkInTime,
			LocalTime checkOutTime, int tableNumber, double totalPrice,int partySize) {
		this.reservationDate = reservationDate;
		this.reservedForTime = reservedForTime;
		this.checkInTime = checkInTime;
		this.checkOutTime = checkOutTime;
		this.tableNumber = tableNumber;
		this.totalPrice = totalPrice;
		this.partySize = partySize;
	}
	public LocalDate getReservationDate() {
		return reservationDate;
	}
	public LocalTime getReservedForTime() {
		return reservedForTime;
	}
	public LocalTime getCheckInTime() {
		return checkInTime;
	}
	public LocalTime getCheckOutTime() {
		return checkOutTime;
	}
	public int getTableNumber() {
		return tableNumber;
	}
	public Double getTotalPrice() {
		return totalPrice;
	}

	public int getPartySize() {
		return partySize;
	}

	public int getConfirmationCode() {
		return confirmationCode;
	}
	
					
}
