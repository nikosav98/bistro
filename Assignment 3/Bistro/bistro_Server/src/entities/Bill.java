package entities;

import java.time.LocalDateTime;

public class Bill {

	private int billID;
	private int seatingID;
	private double totalPrice;
	private String status; // OPEN,PAID
	private LocalDateTime createdAt;
	private LocalDateTime paidAt;
	
	public Bill(int billID, int seatingID, double totalPrice, String status, LocalDateTime createdAt,LocalDateTime paidAt) {
		this.billID = billID;
		this.seatingID = seatingID;
		this.totalPrice = totalPrice;
		this.status = status;
		this.createdAt = createdAt;
		this.paidAt = paidAt;
	}
	public int getBillID() {
		return billID;
	}
	public int getSeatingID() {
		return seatingID;
	}
	public double getTotalPrice() {
		return totalPrice;
	}
	public String getStatus() {
		return status;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public LocalDateTime getPaidAt() {
		return paidAt;
	}
	
}