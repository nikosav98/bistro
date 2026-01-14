package entities;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class WaitingList {
	
	private int waitID;
	private int reservationID;
	private String status; //ENUM('WAITING', 'ASSIGNED', 'CANCELLED','CALLED')
	private int priority; //1-high priority (reserved place) , 0-low priority (walk in)
	private LocalDateTime createdAt;
	private LocalDateTime assignedAt; //or cancelled at
	
	
	public WaitingList(int waitID, int reservationID, String status, int priority, LocalDateTime createdAt,LocalDateTime assignedAt) {
		this.waitID = waitID;
		this.reservationID = reservationID;
		this.status = status;
		this.priority = priority;
		this.createdAt = createdAt;
		this.assignedAt = assignedAt;
	}
	public int getWaitID() {
		return waitID;
	}
	public int getReservationID() {
		return reservationID;
	}
	public void setWaitID(int waitID) {
		this.waitID = waitID;
	}
	public void setReservationID(int reservationID) {
		this.reservationID = reservationID;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public void setPriority(int priority) {
		this.priority = priority;
	}
	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
	public void setAssignedAt(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}
	public String getStatus() {
		return status;
	}
	public int getPriority() {
		return priority;
	}
	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}
	
	
}
