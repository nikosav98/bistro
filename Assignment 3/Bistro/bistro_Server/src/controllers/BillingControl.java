package controllers;

import java.sql.Connection;

import java.sql.SQLException;
import java.time.LocalDateTime;

import database.BillDAO;
import database.DBManager;
import database.ReservationDAO;
import database.UserDAO;
import entities.Bill;
import entities.Reservation;
import entities.User;
import requests.BillRequest;
import responses.BillResponse;
import responses.BillResponse.BillResponseType;
import responses.Response;
import database.SeatingDAO;

public class BillingControl {
	private final ReservationDAO reservationDAO;
	private final SeatingDAO seatingDAO;
	private final NotificationControl notificationControl;
	private final UserDAO userDAO;
	private final BillDAO billDAO;
	private final SeatingControl seatingControl;
	
	public BillingControl() {
		this(new ReservationDAO(),new SeatingDAO(),new NotificationControl(),new UserDAO(),new BillDAO(),new SeatingControl());
	}
	
	public BillingControl(ReservationDAO reservationDAO, SeatingDAO seatingDAO,NotificationControl notificationControl,UserDAO userDAO,BillDAO billDAO,SeatingControl seatingControl) {
		
		this.reservationDAO = reservationDAO;
		this.seatingDAO = seatingDAO;
		this.notificationControl=notificationControl;
		this.userDAO=userDAO;
		this.billDAO=billDAO;
		this.seatingControl = seatingControl;
	}
	
	public Response<BillResponse> handleBillRequest(BillRequest req) throws SQLException{
		if(req==null) return failResponse("request is null");
		if(req.getType()==null) return failResponse("type is null");
		return switch (req.getType()) {
		case REQUEST_TO_SEE_BILL ->handleRequestToSeeBill(req);
		case PAY_BILL -> handleRequestToPayBill(req);
		};
		
		}
	
	private Response<BillResponse> handleRequestToSeeBill(BillRequest req) {
	    
	    try (Connection conn = DBManager.getConnection()){
	    	if(conn==null) return failResponse("DB connection failed");
	        conn.setAutoCommit(false);
	    	try {
	    		Reservation r = reservationDAO.getReservationByConfirmationCode(conn, req.getConfirmationCode());
		        if (r == null) {
		        	conn.rollback();
		            return failResponse("Reservation not found");
		        }
		        Integer seatingId = seatingDAO.getSeatingIdByReservationId(conn, r.getReservationID());
		        if (seatingId == null) {
		        	conn.rollback();
		            return failResponse("No open seating found for this reservation");
		        }
		        Bill existingBill = billDAO.getOpenBillBySeatingId(conn, seatingId);
		        if(existingBill!=null) {
		        	BillResponse br = new BillResponse(BillResponseType.ANSWER_TO_REQUEST_TO_SEE_BILL,existingBill.getBillID(),true);
		        	return successResponse("Here is your bill", br);
		        }
		        
		        Double bill=generateRandomBillSum();
		        Double finalBill;
		        int billId;
		        if(r.getGuestContact()!=null && !r.getGuestContact().isBlank()) {
		        	 billId = billDAO.insertNewBill(conn, seatingId,bill, LocalDateTime.now());
		        	 finalBill=bill;
		        }
		        else {
		        	billId = billDAO.insertNewBill(conn, seatingId,bill*0.9, LocalDateTime.now());
		        	finalBill=0.9*bill;
		        }
		        if (billId == -1) {
		            conn.rollback();
		            return failResponse("Failed to create bill");
		        }
		        conn.commit();
		        conn.setAutoCommit(true);
		        boolean notificationSent = false;
		        try {
		            notificationSent = sendBillToCorrectContact(conn,r.getGuestContact(), r.getUserID(),buildBillMessage(),finalBill);
		        } catch (Exception e) {
		            System.err.println("Notification failed: " + e.getMessage());
		        }
		        BillResponse br = new BillResponse(BillResponseType.ANSWER_TO_REQUEST_TO_SEE_BILL,finalBill,notificationSent);
		        return successResponse("Bill Created", br);
	    	}
	    	catch (Exception e) {
                conn.rollback();
                return new Response<>(false, "failed to add bill to db" + e.getMessage(), null);
            }
	    	
        } catch (Exception e) {
            return new Response<>(false, "DB connection failed: " + e.getMessage(), null);
        }
	}
	private Response<BillResponse> handleRequestToPayBill(BillRequest req) {
	    try (Connection conn = DBManager.getConnection()) {
	        if (conn == null) return failResponse("DB connection failed");

	        conn.setAutoCommit(false);

	        try {
	            Reservation r = reservationDAO.getReservationByConfirmationCode(conn, req.getConfirmationCode());
	            if (r == null) { conn.rollback(); return failResponse("Reservation not found"); }

	            Integer seatingId = seatingDAO.getSeatingIdByReservationId(conn, r.getReservationID());
	            if (seatingId == null) { conn.rollback(); return failResponse("failed to find the Bill in the DB"); }

	            Bill bill = billDAO.getOpenBillBySeatingId(conn, seatingId);
	            if (bill == null) { conn.rollback(); return failResponse("failed to access bill"); }

	            if (!billDAO.markBillAsPaidBySeatingId(conn, seatingId)) {
	                conn.rollback(); return failResponse("failed to update Bill status in the DB");
	            }

	            if (!reservationDAO.updateStatus(conn, req.getConfirmationCode(), "COMPLETED")) {
	                conn.rollback(); return failResponse("Failed to update reservation status");
	            }

	            boolean notificationSent = sendConfirmationToCorrectContact(conn, r);
	            if (!notificationSent) {
	                conn.rollback(); return failResponse("Failed to send bill and therefore pay the bill");
	            }

	            if (!seatingDAO.updateBillSent(conn, seatingId, 1)) {
	                conn.rollback(); return failResponse("Failed to change billSent status");
	            }

	            Integer tableId = seatingDAO.getTableIDBySeatingID(conn, seatingId);
	            if (tableId == null || tableId <= 0) {
	                conn.rollback(); return failResponse("Failed to resolve table");
	            }
	            
	            if (!seatingControl.checkOutCurrentSeating(conn, tableId)) {
	                conn.rollback(); return failResponse("Failed to check out seating");
	            }
	            
	            conn.commit();          
	            conn.setAutoCommit(false);
	            boolean hasCalledNext=true;
	            try {
	                boolean ok=seatingControl.tryAssignNextFromWaitingList(conn, tableId);
	                if(ok)
	                conn.commit();
	                else {
	                	hasCalledNext=false;
	                	conn.rollback();
	                	
	                }
	            } catch (Exception e) {
	            	hasCalledNext=false;
	                try { conn.rollback(); } catch (Exception ignore) {}
	                System.out.println("Assign-next failed (payment already committed): " + e.getMessage());
	            }
	            
	            conn.setAutoCommit(true);
	            
	            BillResponse br = new BillResponse(BillResponse.BillResponseType.ANSWER_TO_PAY_BILL,bill.getTotalPrice(),notificationSent,hasCalledNext);
	            return successResponse(hasCalledNext?"Payment Fulfilled":"Payment fulfilled but failed to call next customer in waiting list", br);

	        } catch (Exception e) {
	            try { conn.rollback(); } catch (Exception ignore) {}
	            return new Response<>(false, "failed to change bill's status to PAID: " + e.getMessage(), null);
	        }

	    } catch (Exception e) {
	        return new Response<>(false, "DB connection failed: " + e.getMessage(), null);
	    }
	}

	
	private boolean sendConfirmationToCorrectContact(Connection conn, Reservation r) throws SQLException {
		if(r.getGuestContact()==null ||r.getGuestContact().isBlank()) {
			User user= userDAO.getUserByUserID(conn, r.getUserID());
			return notificationControl.sendBillConfirmationToUser(user,"Sending bill to user ...");
		}
		else {
			return notificationControl.sendBillConfirmationToGuest(r.getGuestContact(),"Sending bill to guest...");
		}
	}

	private Response<BillResponse> successResponse(String msg, BillResponse bR) {
        return new Response<>(true, msg, bR);
    }

    private Response<BillResponse> failResponse(String msg) {
        return new Response<>(false, msg, null);
    }
	/**
     * 
     * 
     *
     * @return TRUE only if bill was sent and marked as sent.
     *         FALSE otherwise (including if claim failed / not due / missing contact / notification failed).
     */
    public boolean sendBillAutomatically(Connection conn,int seatingId) {
        if (seatingId <= 0) return false;
        if (conn == null) return false; 
            try {
                boolean claimed = seatingDAO.claimAutoBillSend(conn, seatingId);
                if (!claimed) {
                    conn.rollback();
                    return false;
                }
                Integer reservationId = seatingDAO.getReservationIdBySeatingId(conn, seatingId);
                if (reservationId == null) {
                    seatingDAO.updateBillSent(conn, seatingId,0); 
                    conn.commit();
                    return false;
                }
                Reservation res = reservationDAO.getReservationByReservationID(conn, reservationId);
                if (res == null) {
                    seatingDAO.updateBillSent(conn, seatingId,0);
                    conn.commit();
                    return false;
                }

                String guestContact = res.getGuestContact();
                String userId = res.getUserID();
                String billMessage = buildBillMessage();
                double bill=generateRandomBillSum();
                boolean sentOk = sendBillToCorrectContact(conn, guestContact, userId, billMessage,bill);
                if (!sentOk) {
                    seatingDAO.updateBillSent(conn, seatingId,0); // 2->0 so it can retry
                    conn.rollback();
                    return false;
                }
                boolean marked = seatingDAO.updateBillSent(conn, seatingId,1);
                if (!marked) {
                    seatingDAO.updateBillSent(conn, seatingId,0);
                    conn.rollback();
                    return false;
                }
                conn.commit();
                return true;
            } 
         catch (SQLException e) {
            return false;
        }
    }
    private String buildBillMessage() {
        return "Your bill is ready.Seating.Thank you!";
    }
    private boolean sendBillToCorrectContact(Connection conn,String guestContact,String userId,String billMessage,double bill) throws SQLException {
    	if (guestContact != null && !guestContact.isBlank()) {
    		return notificationControl.sendBillToGuest(guestContact, billMessage,bill);
    	}
    	if (userId == null || userId.isBlank()) {
    		return false;
    	}
    	User user = userDAO.getUserByUserID(conn, userId);
    	if (user == null) {
    		return false;
    	}
    	return notificationControl.sendBillToUser(user, billMessage,bill);
    }
    
    private double generateRandomBillSum() {
	    double bill;
	    bill = 50 + (double)(Math.random()*(1000-50+1));
	    return bill;
	}
    
    
}
