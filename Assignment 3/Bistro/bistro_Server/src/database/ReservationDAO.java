package database;

import entities.Reservation;
import responses.ReservationResponse;
import responses.UserHistoryResponse;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * This class encapsulates all SQL operations related to the @code reservation
 * Inserting new reservations
 * Updating reservation fields and status
 * Fetching reservations by identifiers (confirmation code / reservation ID)
 * Finding reservations due for reminder / no-show processing
 * Computing booked tables per capacity for a time window
 * Detecting overbooking and selecting reservations to cancel
 * 
 */
public class ReservationDAO {
	
	
	//INSERT statement
	private static final String INSERT_newReservation = "INSERT INTO `reservation` (reservationDate, status, partySize, allocatedCapacity, confirmationCode, guestContact, userID, startTime) "+
															"VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
															
	
	//SELECT statements
	
	private static final String SELECT_UPCOMING_RESERVATIONS_BY_USER =
	        "SELECT reservationDate, startTime, partySize, confirmationCode " +
	        "FROM reservation " +
	        "WHERE userID = ? " +
	        "AND status IN ('NEW','CONFIRMED') " +
	        "AND reservationDate >= CURDATE() " +
	        "ORDER BY reservationDate ASC, startTime ASC";
	
	private final String SELECT_RESERVATIONS_OVERLAPING_WITH_CLOSE_HOURS="SELECT r.reservationID FROM reservation r "+
																		 "WHERE r.reservationDate = ? "+
																		 "AND (r.startTime < ? OR ADDTIME(r.startTime, '02:00:00') > ?) "+
																		 "ORDER BY r.startTime";
	private static final String SELECT_OVERLAPPING_RESERVATIONS_TO_CANCEL =
            "SELECT guestContact, userID, status, reservationID " +
            "FROM reservation " +
            "WHERE reservationDate = ? " +
            "  AND allocatedCapacity = ? " +
            "  AND status IN ('NEW','CONFIRMED') " +
            "  AND (? < ADDTIME(startTime, '02:00:00') AND ? > startTime) " +
            "ORDER BY (status='NEW') DESC, reservationID DESC " +
            "LIMIT ?";
	private static final String SELECT_OVERBOOKED_SLOTS =
	        "SELECT slots.reservationDate, slots.startTime AS slotStart, COUNT(r2.reservationID) AS booked " +
	        "FROM ( " +
	        "SELECT DISTINCT reservationDate, startTime " +
	        "FROM reservation " +
	        "WHERE reservationDate >= CURDATE() " +
	        "AND allocatedCapacity = ? " +
	        "AND status IN ('NEW','CONFIRMED') " +
	        ") slots " +
	        "JOIN reservation r2 ON r2.reservationDate = slots.reservationDate " +
	        "AND r2.allocatedCapacity = ? " +
	        "AND r2.status IN ('NEW','CONFIRMED') " +
	        "AND (slots.startTime < ADDTIME(r2.startTime, '02:00:00') " +
	        "AND ADDTIME(slots.startTime, '02:00:00') > r2.startTime) " +
	        "GROUP BY slots.reservationDate, slots.startTime " +
	        "HAVING booked > ? " +
	        "ORDER BY slots.reservationDate ASC, slots.startTime ASC";
	private final String SELECT_CODE_BY_CONTACT ="SELECT confirmationCode FROM reservation WHERE guestContact = ?";
	private static final String SELECT_RESERVATIONS_DUE_FOR_NO_SHOW_PARAM ="SELECT reservationID FROM reservation WHERE reservationDate = ? AND status IN ('NEW','CONFIRMED') AND startTime <= ?";
	private static final String SELECT_reservationByConfirmationCode = "SELECT * FROM `reservation` WHERE confirmationCode = ?";
	private static final String SELECT_reservationByReservationId = "SELECT * FROM `reservation` WHERE reservationID = ?";
	private static final String SELECT_amountOfUsedSeats ="""
	        SELECT allocatedCapacity, COUNT(*) AS booked
	        FROM reservation
	        WHERE reservationDate = ?
	          AND status IN ('NEW','CONFIRMED','SEATED')
	          AND (? < ADDTIME(startTime, '02:00:00') AND ? > startTime)
	        GROUP BY allocatedCapacity
			""";
	private static final String SELECT_RESERVATIONS_DUE_FOR_REMINDER =
	        "SELECT reservationID, reservationDate, status, partySize, allocatedCapacity, " +
	        "confirmationCode, guestContact, userID, startTime, timeOfCreation " +
	        "FROM reservation " +
	        "WHERE TIMESTAMP(reservationDate, startTime) >= (NOW() + INTERVAL 2 HOUR) " +
	        "AND TIMESTAMP(reservationDate, startTime) <  (NOW() + INTERVAL 150 MINUTE) " +
	        "AND status = 'APPROVED'";
	private static final String SELECT_CONFIRMATION_CODE_EXISTS = "SELECT 1 FROM reservation WHERE confirmationCode = ? LIMIT 1";
	// Put this SQL near the top of ReservationDAO
	private static final String SELECT_RESERVATION_COUNTS_BY_DAY_BETWEEN =
	        "SELECT DAY(r.reservationDate) AS dayOfMonth, COUNT(*) AS cnt " +
	        "FROM reservation r " +
	        "WHERE r.reservationDate >= ? AND r.reservationDate < ? " +
	        "AND NOT EXISTS ( " +
	        "   SELECT 1 FROM seating s " +
	        "   WHERE s.reservationID = r.reservationID " +
	        "     AND s.checkInTime IS NOT NULL " +
	        "     AND ABS(TIMESTAMPDIFF(MINUTE, r.timeOfCreation, s.checkInTime)) <= 60 " +
	        ") " +
	        "GROUP BY DAY(r.reservationDate)";


	private static final String SELECT_RESERVATIONS_BY_DATE =
	        "SELECT * FROM reservation WHERE reservationDate = ?";
	// UPDATE statement
	private static final String UPDATE_CANCEL_BY_RESERVAIO_ID ="UPDATE reservation SET status = 'CANCELLED' WHERE reservationID = ? AND status IN ('NEW','CONFIRMED')";                     
	private static final String UPDATE_STATUS_RESERVATION_SQL_BY_RESERVATION_ID ="UPDATE `reservation` SET status = ? WHERE reservationID = ?";
	private static final String UPDATE_STATUS_RESERVATION_SQL ="UPDATE `reservation` " +"SET status = ? " +"WHERE confirmationCode = ?";
	private static final String UPDATE_RESERVATION_BY_CONFIRMATION_CODE =
	        "UPDATE `reservation` " +
	        "SET reservationDate = ?, status = ?, partySize = ?, allocatedCapacity = ?, guestContact = ?, userID = ?, startTime = ? " +
	        "WHERE confirmationCode = ?";
	
	
	/**
	 * Represents an overbooked slot (a date + slot start time) and how many reservations overlap that slot.
     * Used when capacity (number of active tables) is reduced and you need to identify problem slots.
	 */
	public static class SlotOverbook {
        private final LocalDate date;
        private final LocalTime slotStart;
        private final int booked;

        public SlotOverbook(LocalDate date, LocalTime slotStart, int booked) {
            this.date = date;
            this.slotStart = slotStart;
            this.booked = booked;
        }

        public LocalDate getDate() { return date; }
        public LocalTime getSlotStart() { return slotStart; }
        public int getBooked() { return booked; }
    }

	
	/**
	 * Finds all overbooked slots for a given allocated capacity after reducing the number of active tables
	 * flow:
	 * Find distinct future slots (date + startTime) for the given capacity
	 * Count how many reservations overlap each slot (2-hour window)
	 * Return those where @code booked > newTotalTables
	 * @param conn
	 * @param allocatedCapacity
	 * @param newTotalTables
	 * @return
	 * @throws SQLException
	 */
    public List<SlotOverbook> findOverbookedSlots(Connection conn, int allocatedCapacity, int newTotalTables) throws SQLException {
        List<SlotOverbook> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_OVERBOOKED_SLOTS)) {
            ps.setInt(1, allocatedCapacity);
            ps.setInt(2, allocatedCapacity);
            ps.setInt(3, newTotalTables);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate d = rs.getDate("reservationDate").toLocalDate();
                    LocalTime s = rs.getTime("slotStart").toLocalTime();
                    int booked = rs.getInt("booked");
                    list.add(new SlotOverbook(d, s, booked));
                }
            }
        }
        return list;
    }
	
    /**
     * Updates an existing reservation (identified by confirmationCode).
     * @param conn
     * @param reservationDate
     * @param status
     * @param partySize
     * @param confirmationCode
     * @param guestContact
     * @param userID
     * @param startTime
     * @return
     * @throws SQLException
     */
	public boolean updateReservation(Connection conn,LocalDate reservationDate,String status,int partySize,int confirmationCode,
	        String guestContact,String userID, LocalTime startTime,int allocatedCapacity) throws SQLException {
		if (reservationDate == null) {
            System.err.println("DB error: reservationDate is null for confirmationCode=" + confirmationCode);
            return false;
        }	       
	    try (
	         PreparedStatement ps = conn.prepareStatement(UPDATE_RESERVATION_BY_CONFIRMATION_CODE)) {

	        ps.setDate(1, java.sql.Date.valueOf(reservationDate));
	        ps.setString(2, status);
	        ps.setInt(3, partySize);
	        ps.setInt(4, allocatedCapacity);
	        ps.setString(5, guestContact); // can be null
	        ps.setString(6, userID);       // can be null
	        ps.setTime(7, startTime != null ? java.sql.Time.valueOf(startTime) : null);
	        ps.setInt(8, confirmationCode);

	        int affected = ps.executeUpdate();
	        return affected == 1;

	    } catch (SQLException e) {
	        System.err.println("DB error updating reservation by confirmationCode=" + confirmationCode);
	        throw e;
	    }
	}
	
	/**
	 * Fetches reservations that are due for a reminder notification based on the time window defined
     * in {@link #SELECT_RESERVATIONS_DUE_FOR_REMINDER}.
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public List<Reservation> getReservationsDueForReminder(Connection conn) throws SQLException {

	    List<Reservation> reservations = new ArrayList<>();

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_RESERVATIONS_DUE_FOR_REMINDER);
	         ResultSet rs = ps.executeQuery()) {

	        while (rs.next()) {
	            Reservation r = new Reservation(
	                    rs.getInt("reservationID"),
	                    rs.getDate("reservationDate").toLocalDate(),
	                    rs.getString("status"),
	                    rs.getInt("partySize"),
	                    rs.getInt("allocatedCapacity"),
	                    rs.getInt("confirmationCode"),
	                    rs.getString("guestContact"),
	                    rs.getString("userID"),
	                    rs.getTime("startTime").toLocalTime(),
	                    readTimeOfCreation(rs));
	                   
	            
	            reservations.add(r);
	        }
	    }

	    return reservations;
	}


	/**
	 * Returns reservation IDs that should be processed as "no-show candidates" for a specific date
	 * @code reservationDate = date
	 * @code status IN ('NEW','CONFIRMED')
	 * @code startTime <= lateCutoffTime
	 * @param conn
	 * @param date
	 * @param lateCutoffTime
	 * @return
	 * @throws SQLException
	 */
	public List<Integer> getReservationsDueForNoShow(Connection conn, LocalDate date, LocalTime lateCutoffTime)
	        throws SQLException {

	    if (conn == null) throw new IllegalArgumentException("conn is null");

	    List<Integer> ids = new ArrayList<>();

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_RESERVATIONS_DUE_FOR_NO_SHOW_PARAM)) {
	        ps.setDate(1, java.sql.Date.valueOf(date));
	        ps.setTime(2, java.sql.Time.valueOf(lateCutoffTime));

	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                ids.add(rs.getInt("reservationID"));
	            }
	        }
	    }
	    return ids;
	}

	
	/**
	 * Updates the status of a reservation identified by @code reservationID
	 * @param conn
	 * @param reservationID
	 * @param status
	 * @return
	 * @throws SQLException
	 */
	public boolean updateStatusByReservationID(Connection conn, int reservationID,String status) throws SQLException {
	    if (conn == null) throw new IllegalArgumentException("conn is null(updateStatus)");
	    

	    try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_RESERVATION_SQL_BY_RESERVATION_ID)) {
	        ps.setString(1, status);
	        ps.setInt(2, reservationID);
	        return ps.executeUpdate() == 1;
	    }
	}
	
	/**
	 * Cancels a reservation by setting its status to 'CANCELLED' (transaction-friendly).
	 *
	 * @param conn active JDBC connection (can be part of a larger transaction)
	 * @param confirmationCode reservation confirmation code
	 * @return true if exactly one row was updated, false otherwise
	 * @throws SQLException if a DB error occurs
	 */
	public boolean updateStatus(Connection conn, int confirmationCode,String status) throws SQLException {
	    if (conn == null) throw new IllegalArgumentException("conn is null(updateStatus)");
	    if (confirmationCode <= 0) return false;

	    try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_RESERVATION_SQL)) {
	        ps.setString(1, status);
	        ps.setInt(2, confirmationCode);
	        return ps.executeUpdate() == 1;
	    }
	}

	
	/**
	 * Cancels a reservation by setting its status to 'CANCELLED'.
	 * Convenience wrapper that opens/closes its own connection.
	 */
	public boolean updateStatus(int confirmationCode,String status) throws SQLException {
	    try (Connection conn = DBManager.getConnection()) {
	        return updateStatus(conn, confirmationCode,status);
	    }
	}

	
	

	
	/**
	 * method to add a new reservation
	 * @param reservationID
	 * @param reservationDate
	 * @param numberOfGuests
	 * @param confirmationCode
	 * @param subscriberId
	 * @param dateOfPlacingOrder
	 * @return reservationID  
	 * @throws SQLException
	 */
	public int insertNewReservation(Connection conn,LocalDate reservationDate,int numberOfGuests,int allocatedCapacity,
			int confirmationCode,String userID,LocalTime startTime,String status,String guest) throws SQLException {
		if (reservationDate == null) {
            System.err.println("DB error: reservationDate is null for confirmationCode=" + confirmationCode);
            return -1;
        }
	    java.sql.Date sqlReservationDate = java.sql.Date.valueOf(reservationDate);
	    if (conn == null) throw new IllegalArgumentException("conn is null");
	    try (PreparedStatement pstmt = conn.prepareStatement(INSERT_newReservation,Statement.RETURN_GENERATED_KEYS)) {
	         

	        pstmt.setDate(1, sqlReservationDate);
	        pstmt.setString(2, status);
	        pstmt.setInt(3, numberOfGuests);
	        pstmt.setInt(4, allocatedCapacity);
	        pstmt.setInt(5, confirmationCode);
	        pstmt.setString(6, guest);
	        pstmt.setString(7, userID);
	        pstmt.setTime(8, java.sql.Time.valueOf(startTime));
	        pstmt.setTime(8, startTime != null ? java.sql.Time.valueOf(startTime) : null);

	        int isInserted = pstmt.executeUpdate();
	        if( isInserted != 1) return -1;
	        
	        ResultSet rs = pstmt.getGeneratedKeys();
	        return rs.next() ? rs.getInt(1) : -1;

	    } catch (SQLException e) {
	        System.err.println("Database error: could not insert new reservation");
	        throw e;
	    }
	}
	
	
	public Reservation getReservationByConfirmationCode(int code) throws SQLException {
        try (Connection conn = DBManager.getConnection()) {
            return getReservationByConfirmationCode(conn, code); // delegate
        }
    }
	
	/**
	 * Fetches a reservation by confirmation code using an existing connection.
	 * @param conn
	 * @param confirmationCode
	 * @return
	 * @throws SQLException
	 */
	public Reservation getReservationByConfirmationCode(Connection conn,int confirmationCode) throws SQLException {

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_reservationByConfirmationCode)) {
	         
	        ps.setInt(1, confirmationCode);

	        try (ResultSet rs = ps.executeQuery()) {

	            if (!rs.next()) {
	                return null; // No reservation with this confirmation code
	            }

	            return new Reservation(
	                rs.getInt("reservationID"),
	                rs.getDate("reservationDate").toLocalDate(),
	                rs.getString("status"),
	                rs.getInt("partySize"),
	                rs.getInt("allocatedCapacity"),
	                rs.getInt("confirmationCode"),
	                rs.getString("guestContact"),  // may be null
	                rs.getString("userID"),        // may be null
	                rs.getTime("startTime") != null? rs.getTime("startTime").toLocalTime(): null,	                        	                        
	                readTimeOfCreation(rs));
	        }

	    } catch (SQLException e) {
	        System.err.println("DB error fetching reservation by confirmationCode=" + confirmationCode);	            	        
	        throw e;
	    }
	}
	
	/**
	 * Fetches a reservation by reservation ID (primary key).
	 * @param conn
	 * @param reservationID
	 * @return
	 * @throws SQLException
	 */
	public Reservation getReservationByReservationID(Connection conn,int reservationID) throws SQLException {

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_reservationByReservationId)) {
	         
	        ps.setInt(1, reservationID);

	        try (ResultSet rs = ps.executeQuery()) {

	            if (!rs.next()) {
	                return null; 
	            }

	            return new Reservation(
	                rs.getInt("reservationID"),
	                rs.getDate("reservationDate").toLocalDate(),
	                rs.getString("status"),
	                rs.getInt("partySize"),
	                rs.getInt("allocatedCapacity"),
	                rs.getInt("confirmationCode"),
	                rs.getString("guestContact"),  // may be null
	                rs.getString("userID"),        // may be null
	                rs.getTime("startTime") != null ? rs.getTime("startTime").toLocalTime(): null,
	                readTimeOfCreation(rs));      
	            	
	        }

	    } catch (SQLException e) {
	        System.err.println("DB error fetching reservation by reservationID=" + reservationID);	            	        
	        throw e;
	    }
	}
	
	/**
	 * Returns a mapping of {@code allocatedCapacity -> bookedCount} for reservations overlapping the
     * requested time window (start/end) on a given date.
	 * @param conn
	 * @param date
	 * @param start
	 * @param end
	 * @return
	 * @throws SQLException
	 */
	public Map<Integer, Integer> getBookedTablesByCapacity(Connection conn,LocalDate date, LocalTime start, LocalTime end)throws SQLException {
		
	    Map<Integer, Integer> booked = new HashMap<>();
	    try (PreparedStatement ps = conn.prepareStatement(SELECT_amountOfUsedSeats)) {

	        ps.setDate(1, java.sql.Date.valueOf(date));
	        ps.setTime(2, java.sql.Time.valueOf(start));
	        ps.setTime(3, java.sql.Time.valueOf(end));

	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                booked.put(rs.getInt("allocatedCapacity"), rs.getInt("booked"));
	            }
	        }
	    }
	    return booked;
	}
	public boolean isConfirmationCodeUsed(Connection conn, int code) throws SQLException {
	    try (PreparedStatement ps = conn.prepareStatement(SELECT_CONFIRMATION_CODE_EXISTS)) {
	        ps.setInt(1, code);
	        try (ResultSet rs = ps.executeQuery()) {
	            return rs.next();
	        }
	    }
	}

	
	/**
	 * Checks if a confirmation code already exists in the database
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public int generateConfirmationCode(Connection conn) throws SQLException {
	    if (conn == null) {
	        throw new IllegalArgumentException("Connection is null");
	    }

	    int code;
	    do {
	        // 6-digit code
	        code = 100000 + (int) (Math.random() * 900000);
	    } while (isConfirmationCodeUsed(conn, code));

	    return code;
	}
	
	
	/**
	 * Cancels multiple reservations by confirmation code using a batch update
	 * @param conn
	 * @param confirmationCodes
	 * @return
	 * @throws SQLException
	 */
	public int cancelReservationsByReservationID(Connection conn, List<Reservation> reservations) throws SQLException {
		if (conn == null) throw new IllegalArgumentException("conn is null");
	    if (reservations == null || reservations.isEmpty()) return 0;

        try (PreparedStatement ps = conn.prepareStatement(UPDATE_CANCEL_BY_RESERVAIO_ID)) {
            for (Reservation r : reservations) {
                ps.setInt(1, r.getReservationID());
                ps.addBatch();
            }
            int[] res = ps.executeBatch();

            int updated = 0;
            for (int x : res) {
                if (x > 0) updated += x;
            }
            return updated;
        }
    }
	
	/**
	 * Selects up to @code limit confirmation codes for reservations that overlap a 2-hour window
     * starting at @code timeStart, for a given date and allocated capacity.
     *
     * Ordering prefers cancelling NEW reservations first, then the newest ones, based on:
     *@code ORDER BY (status='NEW') DESC, reservationID DESC
	 * @param conn
	 * @param date
	 * @param timeStart
	 * @param allocatedCapacity
	 * @param limit
	 * @return
	 * @throws SQLException
	 */
	public List<Reservation> pickReservationToCancelDueToTable(Connection conn,LocalDate date,LocalTime timeStart,int allocatedCapacity,int limit)throws SQLException{
		List<Reservation> idList = new ArrayList<>();
        LocalTime timeEnd = timeStart.plusHours(2);
        
        try (PreparedStatement ps = conn.prepareStatement(SELECT_OVERLAPPING_RESERVATIONS_TO_CANCEL)){
        	ps.setDate(1, Date.valueOf(date));
            ps.setInt(2, allocatedCapacity);
            ps.setTime(3, Time.valueOf(timeStart));
            ps.setTime(4, Time.valueOf(timeEnd));
            ps.setInt(5, limit);
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
            	Reservation r = new Reservation(rs.getInt("reservationID"),rs.getString("guestContact"),rs.getString("userID"),rs.getString("status"));				
				idList.add(r);
            }
        }
        return idList;
	}
	public List<Reservation> pickReservationToCancelDueToOpenHours(Connection conn,LocalDate date,LocalTime openTime,LocalTime closeTime)throws SQLException{
		List<Reservation> idList = new ArrayList<>();
		try(PreparedStatement ps = conn.prepareStatement(SELECT_RESERVATIONS_OVERLAPING_WITH_CLOSE_HOURS)){
			
			ps.setDate(1,Date.valueOf(date));
			ps.setTime(2,Time.valueOf(openTime));
			ps.setTime(3, Time.valueOf(closeTime));
			ResultSet rs = ps.executeQuery();
			
			while(rs.next()) {
				Reservation r = new Reservation(rs.getInt("reservationID"),rs.getString("guestContact"),rs.getString("userID"),rs.getString("status"));				
				idList.add(r);
			}
		}
		return idList;
	}
	
	public Integer[] getCountOfReservationsBetween(Connection conn, LocalDateTime start, LocalDateTime end) throws SQLException {
	    Integer[] counts = new Integer[31];
	    for (int i = 0; i < counts.length; i++) counts[i] = 0;

	    if (start == null || end == null) {
	        return counts;
	    }

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_RESERVATION_COUNTS_BY_DAY_BETWEEN)) {
	        ps.setDate(1, java.sql.Date.valueOf(start.toLocalDate()));
	        ps.setDate(2, java.sql.Date.valueOf(end.toLocalDate()));

	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                int day = rs.getInt("dayOfMonth"); // 1..31
	                int cnt = rs.getInt("cnt");
	                if (day >= 1 && day <= 31) {
	                    counts[day - 1] = cnt;
	                }
	            }
	        }
	    }
	    return counts;
	}

	public List<Reservation> fetchReservationsByDate(Connection conn, LocalDate date) throws SQLException {

	    List<Reservation> reservations = new ArrayList<>();

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_RESERVATIONS_BY_DATE)) {
	        ps.setDate(1, java.sql.Date.valueOf(date));
	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                Reservation r = new Reservation(
	                        rs.getInt("reservationID"),
	                        rs.getString("guestContact"),
	                        rs.getString("userID"),
	                        rs.getString("status")
	                );
	                reservations.add(r);
	            }
	        }
	    }

	    return reservations;
	}
	
	private LocalDateTime readTimeOfCreation(ResultSet rs) throws SQLException {
	    if (!hasColumn(rs, "timeOfCreation")) {
	        return null;
	    }
	    Timestamp timestamp = rs.getTimestamp("timeOfCreation");
	    return timestamp != null ? timestamp.toLocalDateTime() : null;
	}

	private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
	    ResultSetMetaData metaData = rs.getMetaData();
	    int count = metaData.getColumnCount();
	    for (int i = 1; i <= count; i++) {
	        if (columnName.equalsIgnoreCase(metaData.getColumnLabel(i))) {
	            return true;
	        }
	    }
	    return false;
	}
	
	/**
	 * fetching future reservations for a specific user 
	 * @param conn
	 * @param userId
	 * @return
	 * @throws SQLException
	 */
	public List<UserHistoryResponse> fetchUpcomingReservationsByUser(Connection conn, String userId) throws SQLException {
	    List<UserHistoryResponse> reservations = new ArrayList<>();
	    if (userId == null || userId.isBlank()) {
	        return reservations;
	    }

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_UPCOMING_RESERVATIONS_BY_USER)) {
	        ps.setString(1, userId);
	        try (ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                reservations.add(new UserHistoryResponse(
	                        rs.getDate("reservationDate").toLocalDate(),
	                        rs.getInt("partySize"),
	                        rs.getTime("startTime").toLocalTime(),
	                        rs.getInt("confirmationCode")));	                        	                
	            }
	        }
	    }

	    return reservations;
	}


	
	public int fetchConfirmationCodeByGuestContact(Connection conn,String contact)throws SQLException{
		try(PreparedStatement ps = conn.prepareStatement(SELECT_CODE_BY_CONTACT)){
			ps.setString(1, contact);
			ResultSet rs = ps.executeQuery();
			if(rs.next()) return rs.getInt("confirmationCode");
			return -1;
		}
	}
	}
	
	
		
	
