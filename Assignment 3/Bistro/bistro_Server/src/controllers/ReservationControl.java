package controllers;

import database.*;
import entities.OpeningHours;
import entities.Reservation;
import requests.ReservationRequest;
import responses.ReservationResponse;
import responses.ReservationResponse.ReservationResponseType;
import responses.Response;
import responses.UserHistoryResponse;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * ReservationControl (Controller owns the Connection and passes it to DAOs)
 *
 * This version matches YOUR ReservationResponse:
 * - For SECOND_PHASE / EDIT / SHOW / CANCEL:
 *   ReservationResponse(LocalDate date, int partySize, LocalTime time,
 *                       int confirmationCode, String userID, String guestContact, ReservationResponseType type)
 *   and sets userID OR guestContact (one is null)
 *
 * - For FIRST_PHASE:
 *   ReservationResponse(type, availableTimes, suggestedDates, confirmationCode)
 */
public class ReservationControl {

    private static final int RESERVATION_DURATION_MIN = 120;
    private static final int TIME_SLOT_STEP_MIN = 30;

    private final ReservationDAO reservationDAO;
    private final TableDAO tableDAO;
    private final OpeningHoursDAO openingHoursDAO;
    private final UserDAO userDAO; 
    private final NotificationControl notificationControl;

    public ReservationControl() {
        this(new ReservationDAO(), new TableDAO(), new OpeningHoursDAO(),
                new UserDAO(), new NotificationControl());
    }

    public ReservationControl(ReservationDAO reservationDAO, TableDAO tableDAO,OpeningHoursDAO openingHoursDAO,
                             UserDAO userDAO,  NotificationControl notificationControl) {                                                                                      
        this.reservationDAO = reservationDAO;
        this.tableDAO = tableDAO;
        this.openingHoursDAO = openingHoursDAO;
        this.userDAO = userDAO;
        this.notificationControl = notificationControl;
    }

    public Response<ReservationResponse> handleReservationRequest(ReservationRequest req) {
        if (req == null) return failResponse("Request is missing");
        if (req.getType() == null) return failResponse("Phase is missing");

        return switch (req.getType()) {
            case FIRST_PHASE -> handleFirstPhase(req);
            case SECOND_PHASE -> handleSecondPhase(req);
            case EDIT_RESERVATION -> handleEdit(req);
            case CANCEL_RESERVATION -> handleCancel(req);
            case SHOW_RESERVATION -> showReservation(req.getConfirmationCode());
        };
    }

    // ---------------- FIRST PHASE ----------------
    private Response<ReservationResponse> handleFirstPhase(ReservationRequest req) {
        if (req.getReservationDate() == null) return failResponse("Missing reservation date");
        if (req.getPartySize() <= 0) return failResponse("Invalid party size");

        try (Connection conn = DBManager.getConnection()) {
        	
        	System.out.println(req.getReservationDate()+" received");
        	
            List<LocalTime> availableTimes =getAvailableTimes(conn, req.getReservationDate(), req.getPartySize());            

            if (availableTimes != null && !availableTimes.isEmpty()) {
                ReservationResponse rr = new ReservationResponse(ReservationResponseType.FIRST_PHASE_SHOW_AVAILABILITY,availableTimes,
                        null,null);                                                                                     
                return successResponse("Available times found", rr);
            }

            Map<LocalDate, List<LocalTime>> suggestions =
                    getSuggestionsForNextDays(conn, req.getReservationDate(), req.getPartySize());

            boolean hasAnySuggestion = suggestions != null && suggestions.values().stream().anyMatch(list -> list != null && !list.isEmpty());
                    
            if (hasAnySuggestion) {
                ReservationResponse rr = new ReservationResponse(ReservationResponseType.FIRST_PHASE_SHOW_SUGGESTIONS,null,suggestions,null);  
                                        
                return successResponse("No availability on requested date, showing suggestions", rr);
            }

            // IMPORTANT: your failResponse returns rr=null. If UI expects a type, return rr as SUCCESS.
            ReservationResponse rr = new ReservationResponse(ReservationResponseType.FIRST_PHASE_NO_AVAILABILITY_OR_SUGGESTIONS,null,null, null);
                                                                                           
            return successResponse("No availability or suggestions found", rr);

        } catch (IllegalArgumentException e) {
            return failResponse("Party too large");
        } catch (SQLException e) {
            return failResponse("Failed to interact with DB");
        }
    }

    // ---------------- SECOND PHASE ----------------
    private static String q(String s) { return s == null ? "null" : ("'" + s + "'"); }

    private Response<ReservationResponse> handleSecondPhase(ReservationRequest req) {
    	System.out.println("[SERVER SECOND_PHASE] userID=" + q(req.getUserID())
        + " guestContact=" + q(req.getGuestContact())
        + " date=" + req.getReservationDate()
        + " time=" + req.getStartTime()
        + " party=" + req.getPartySize());
        if (req.getReservationDate() == null || req.getStartTime() == null)
            return failResponse("Missing reservation date/time");
        if (req.getPartySize() <= 0) return failResponse("Invalid party size");

        boolean hasUser = req.getUserID() != null && !req.getUserID().isBlank();
        if (!hasUser && (req.getGuestContact() == null || req.getGuestContact().isBlank()))
            return failResponse("Missing identity: both userID and guestContact are empty");

        try {
            return createReservation(req, ReservationResponseType.SECOND_PHASE_CONFIRMED);
        } catch (IllegalArgumentException e) {
            return failResponse("Party too large");
        } catch (SQLException e) {
            return failResponse("Failed to interact with DB");
        }
    }

    /**
     * Transaction:
     * - availability check + generate code + insert are done in ONE transaction
     * - commit first, then notify
     */
    private Response<ReservationResponse> createReservation(ReservationRequest req,
                                                           ReservationResponseType type) throws SQLException {

        boolean hasUser = req.getUserID() != null && !req.getUserID().isBlank();
        String userID = hasUser ? req.getUserID() : null;
        String guestContact = hasUser ? null : req.getGuestContact();

        int partySize = req.getPartySize();

        int confirmationCode;

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int allocatedCapacity = roundToCapacity(conn, partySize);

                if (!isStillAvailable(conn, req.getReservationDate(), req.getStartTime(), allocatedCapacity)) {
                    conn.rollback();
                    return failResponse("Selected time is no longer available");
                }

                confirmationCode = reservationDAO.generateConfirmationCode(conn);

                int inserted = reservationDAO.insertNewReservation(conn,req.getReservationDate(),partySize,allocatedCapacity,confirmationCode,userID,
                        req.getStartTime(),
                        "CONFIRMED",
                        guestContact
                );

                if (inserted == -1) {
                    conn.rollback();
                    return failResponse("Failed to create reservation");
                }

                conn.commit();

            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        // notify AFTER commit (don’t hold DB transaction while sending)
        sendConfirmationNotification(userID, guestContact, confirmationCode);

        ReservationResponse rr = new ReservationResponse(req.getReservationDate(),partySize,req.getStartTime(),confirmationCode,userID,guestContact,type);       
        return successResponse("Reservation created", rr);
    }

    // ---------------- EDIT ----------------
    private Response<ReservationResponse> handleEdit(ReservationRequest req) {
        if (req.getConfirmationCode() <= 0) return failResponse("Missing confirmation code");
        if (req.getReservationDate() == null || req.getStartTime() == null)
            return failResponse("Missing reservation date/time");
        if (req.getPartySize() <= 0) return failResponse("Invalid party size");

        try {
            return editReservation(req.getReservationDate(),req.getStartTime(),req.getPartySize(),req.getGuestContact(), req.getConfirmationCode()
            );
        } catch (IllegalArgumentException e) {
            return failResponse(e.getMessage() != null ? e.getMessage() : "Invalid edit request");
        } catch (SQLException e) {
            return failResponse("Failed to interact with DB(edit)");
        }
    }

    /**
     * IMPORTANT NOTE:
     * Availability check during edit SHOULD exclude the existing reservation itself,
     * otherwise keeping the same time may fail.
     *
     * Best fix: add DAO method:
     *   reservationDAO.getBookedTablesByCapacityExcluding(conn, date, start, end, confirmationCode)
     * and use it here.
     *
     * In this controller, I show BOTH options:
     * - default uses normal check (works but may be too strict)
     * - if you implement the excluding method, switch the call in one line below.
     */
    public Response<ReservationResponse> editReservation(LocalDate reservationDate,LocalTime startTime,int partySize,String newGuestContact,int confirmationCode) throws SQLException {

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Reservation existing = reservationDAO.getReservationByConfirmationCode(conn, confirmationCode);
                
                if (existing == null) {
                    conn.rollback();
                    return new Response<>(false, "Reservation not found", null);
                }
                if (reservationDate.isBefore(LocalDate.now())) return new Response<>(false, "Requested date already passed", null);
                

                String userID = existing.getUserID();     // preserved
                String status = existing.getStatus();     // preserved

                boolean isGuestReservation = (userID == null || userID.isBlank());

                String guestContactToSave = existing.getGuestContact();
                if (isGuestReservation) {
                    // guest can update contact
                    guestContactToSave = newGuestContact;
                }

                int newAllocatedCapacity = roundToCapacity(conn, partySize);

                
                boolean available = isStillAvailable(conn, reservationDate, startTime, newAllocatedCapacity);


                if (!available) {
                    conn.rollback();
                    return new Response<>(false, "Requested time is not available", null);
                }

                boolean updated = reservationDAO.updateReservation(conn,reservationDate,status, partySize,confirmationCode,
                        guestContactToSave,userID,startTime,newAllocatedCapacity);
                        
                                       
                if (!updated) {
                    conn.rollback();
                    return new Response<>(false, "Reservation was not updated", null);
                }

                conn.commit();

                // build response: userID OR guestContact (one is null)
                ReservationResponse rr = new ReservationResponse(reservationDate,partySize,startTime,confirmationCode,userID,
                        isGuestReservation ? guestContactToSave : null,
                        ReservationResponseType.EDIT_RESERVATION
                );
                return new Response<>(true, "Reservation updated successfully", rr);

            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // OPTIONAL helper if you implement exclude DAO:
    /*
    private boolean isStillAvailableExcluding(Connection conn,
                                              LocalDate date,
                                              LocalTime startTime,
                                              int allocatedCapacity,
                                              int excludeConfirmationCode) throws SQLException {

        LocalTime end = startTime.plusMinutes(RESERVATION_DURATION_MIN);

        Map<Integer, Integer> totals = tableDAO.getTotalTablesByCapacity(conn);
        int totalForCap = totals.getOrDefault(allocatedCapacity, 0);
        if (totalForCap <= 0) return false;

        Map<Integer, Integer> booked =
                reservationDAO.getBookedTablesByCapacityExcluding(conn, date, startTime, end, excludeConfirmationCode);

        int bookedForCap = booked.getOrDefault(allocatedCapacity, 0);
        return bookedForCap < totalForCap;
    }
    */

    // ---------------- CANCEL ----------------
    private Response<ReservationResponse> handleCancel(ReservationRequest req) {
        if (req.getConfirmationCode() <= 0) return failResponse("Missing confirmation code");
        try {
            return cancelReservation(req.getConfirmationCode());
        } catch (SQLException e) {
            return failResponse("Failed to interact with DB");
        }
    }

    public Response<ReservationResponse> cancelReservation(int confirmationCode) throws SQLException {
        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Reservation reservation = reservationDAO.getReservationByConfirmationCode(conn, confirmationCode);
                if (reservation == null) {
                    conn.rollback();
                    return new Response<>(false, "Reservation not found", null);
                }

                boolean ok = reservationDAO.updateStatus(conn, confirmationCode, "CANCELLED");
                if (!ok) {
                    conn.rollback();
                    return new Response<>(false, "Failed to cancel reservation", null);
                }

                conn.commit();

                String userID = reservation.getUserID();
                String guestContact = (userID == null || userID.isBlank()) ? reservation.getGuestContact() : null;

                ReservationResponse rr = new ReservationResponse(reservation.getReservationDate(),reservation.getPartySize(),reservation.getStartTime(),
                		reservation.getConfirmationCode(),
                        userID,
                        guestContact,
                        ReservationResponseType.CANCEL_RESERVATION
                );
                return new Response<>(true, "Reservation cancelled successfully", rr);

            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ---------------- SHOW ----------------
    public Response<ReservationResponse> showReservation(int confirmationCode) {
        if (confirmationCode <= 0) return failResponse("Missing confirmation code");

        try (Connection conn = DBManager.getConnection()) {
            Reservation existing = reservationDAO.getReservationByConfirmationCode(conn, confirmationCode);
                 
            if (existing == null) return new Response<>(false, "Reservation not found", null);
            if(existing.getStatus().equals("CANCELLED")) return new Response<>(false, "Reservation status is cancelled", null);
            if(existing.getReservationDate().isBefore(LocalDate.now())) return new Response<>(false, "cant edit past reservations", null);

            String userID = existing.getUserID();
            String guestContact = (userID == null || userID.isBlank()) ? existing.getGuestContact() : null;

            ReservationResponse rr = new ReservationResponse(
                    existing.getReservationDate(),
                    existing.getPartySize(),
                    existing.getStartTime(),
                    existing.getConfirmationCode(),
                    userID,
                    guestContact,
                    ReservationResponseType.SHOW_RESERVATION
            );
            return new Response<>(true, "Here is your reservation", rr);

        } catch (SQLException e) {
            return new Response<>(false, "Failed to fetch reservation", null);
        }
    }

    // ---------------- Helpers ----------------
    private Response<ReservationResponse> successResponse(String msg, ReservationResponse rr) {
        return new Response<>(true, msg, rr);
    }

    private Response<ReservationResponse> failResponse(String msg) {
        return new Response<>(false, msg, null);
    }

    private boolean isStillAvailable(Connection conn,LocalDate date,LocalTime startTime,int allocatedCapacity) throws SQLException {

        LocalTime end = startTime.plusMinutes(RESERVATION_DURATION_MIN);

        Map<Integer, Integer> totals = tableDAO.getTotalTablesByCapacity(conn);
        int totalForCap = getTotalTablesForParty(totals, allocatedCapacity);
        if (totalForCap <= 0) return false;

        Map<Integer, Integer> booked = reservationDAO.getBookedTablesByCapacity(conn, date, startTime, end);
        int bookedForCap = getBookedTablesForParty(booked, allocatedCapacity);


        return bookedForCap < totalForCap;
    }

    public List<LocalTime> getAvailableTimes(Connection conn, LocalDate date, int partySize) throws SQLException {
        if (date == null) return new ArrayList<>();

        OpeningHours openHour = openingHoursDAO.getOpeningHour(conn, date);
        if (openHour == null) return new ArrayList<>();

        LocalTime open = openHour.getOpenTime();
        LocalTime close = openHour.getCloseTime();
        if (open == null || close == null) return new ArrayList<>();
        
        
        if (LocalDate.now().equals(date) && open.isBefore(LocalTime.now())) {
        	LocalTime minStart = LocalTime.now().plusHours(1);
        	minStart = ceilToStep(minStart, TIME_SLOT_STEP_MIN);
        	 open = open.isAfter(minStart) ? open : minStart;
        }
        
        open = ceilToStep(open, TIME_SLOT_STEP_MIN);
        int cap = roundToCapacity(conn, partySize);

        List<LocalTime> available = new ArrayList<>();

        Map<Integer, Integer> totalTablesByCapacity = tableDAO.getTotalTablesByCapacity(conn);
        int total = getTotalTablesForParty(totalTablesByCapacity, cap);
        if (total <= 0) {
            return available;
        }


        for (LocalTime start = open;
             !start.plusMinutes(RESERVATION_DURATION_MIN).isAfter(close);
             start = start.plusMinutes(TIME_SLOT_STEP_MIN)) {

            LocalTime end = start.plusMinutes(RESERVATION_DURATION_MIN);

            Map<Integer, Integer> alreadyBooked =
                    reservationDAO.getBookedTablesByCapacity(conn, date, start, end);

            int booked = getBookedTablesForParty(alreadyBooked, cap);

            if (booked < total) available.add(start);
        }

        return available;
    }
    
    private int getTotalTablesForParty(Map<Integer, Integer> totals, int minCapacity) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : totals.entrySet()) {
            if (entry.getKey() >= minCapacity) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private int getBookedTablesForParty(Map<Integer, Integer> booked, int minCapacity) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : booked.entrySet()) {
            if (entry.getKey() >= minCapacity) {
                total += entry.getValue();
            }
        }
        return total;
    }


    public Map<LocalDate, List<LocalTime>> getSuggestionsForNextDays(Connection conn,
                                                                     LocalDate requestedDate,
                                                                     int partySize) throws SQLException {
        Map<LocalDate, List<LocalTime>> suggestions = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) {
            LocalDate dateToCheck = requestedDate.plusDays(i);
            suggestions.put(dateToCheck, getAvailableTimes(conn, dateToCheck, partySize));
        }
        return suggestions;
    }

    private int roundToCapacity(Connection conn, int partySize) throws SQLException {
        // Controller owns the connection -> pass conn
        return tableDAO.getMinimalTableSize(conn, partySize);
    }
    /*
    /**
     * Notification AFTER commit.
     * Here we send based on userID/guestContact fields (your design).
     * If your NotificationControl requires full User object, you can fetch it using userDAO + new connection,
     * but do NOT do it inside the DB transaction.
     */
    private void sendConfirmationNotification(String userID, String guestContact, int confirmationCode) {
        try {
            if (userID != null && !userID.isBlank()) {
                // If your NotificationControl needs a User object:
                try (Connection conn = DBManager.getConnection()) {
                    var user = userDAO.getUserByUserID(conn, userID);
                    if (user == null) {
                        System.err.println("User not found for userID=" + userID);
                        return;
                    }
                    notificationControl.sendConfirmationToUser(user, confirmationCode);
                }
            } else {
                notificationControl.sendConfirmationToGuest(guestContact, confirmationCode);
            }
        } catch (SQLException e) {
            System.err.println("[NOTIFY] Failed while notifying: " + e.getMessage());
        }
    }
    
    
    public Response<Integer> retrieveConfirmationCode(String contact) {
    	try(Connection conn = DBManager.getConnection()){
    		int code= reservationDAO.fetchConfirmationCodeByGuestContact(conn, contact);
    		if(code==-1) {
    			List<UserHistoryResponse> upcoming = reservationDAO.fetchUpcomingReservationsByUser(conn, contact);
    			if(upcoming !=null)code = upcoming.get(0).getConfirmationCode();
    		}
    		sendConfirmationNotification(null, contact, code);
    		return new Response<>(true,"here is your code",code);
    	}catch(Exception e) {
    		return new Response<>(false, "Failed to fetch code by contact", null);
    	}
    }
    
    /**
     * helper method to round up time for searching available times when doing a reservation
     * @param t the now time 
     * @param stepMinutes the 30 min step
     * @return round up to hh:00 or hh:30 format
     */
    private static LocalTime ceilToStep(LocalTime t, int stepMinutes) {
        int stepSeconds = stepMinutes * 60;

        
        int totalSeconds = t.toSecondOfDay() + (t.getNano() > 0 ? 1 : 0);

        int remainder = totalSeconds % stepSeconds;
        if (remainder != 0) {
            totalSeconds += (stepSeconds - remainder);
        }

        
        int maxSeconds = 24 * 60 * 60 - 1;
        totalSeconds = Math.min(totalSeconds, maxSeconds);

        return LocalTime.ofSecondOfDay(totalSeconds);
    }
}
