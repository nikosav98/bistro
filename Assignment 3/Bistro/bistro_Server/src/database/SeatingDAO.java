package database;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import requests.TableInfo;
import responses.CurrentSeatingResponse;

public class SeatingDAO {
    // INSERT
    private static final String INSERT_SEATING =
            "INSERT INTO seating (tableID, reservationID, checkInTime, checkOutTime,billSent) " +
            "VALUES (?, ?, NOW(), NULL,0)";
    private static final String INSERT_HELD_SEATING =
    	    "INSERT INTO seating (tableID, reservationID, checkInTime, checkOutTime) " +
    	    "VALUES (?, ?, NULL, NULL)";

    // UPDATE
    private static final String UPDATE_CHEKOUT_BY_SEATING_ID ="UPDATE `seating` SET checkOutTime = NOW() WHERE seatingID = ?";
    private static final String CLAIM_AUTO_BILL_SEND ="UPDATE seating SET billSent = 2 " +"WHERE seatingID = ? AND billSent = 0 AND checkOutTime IS NULL " +
    	    "AND checkInTime <= DATE_SUB(NOW(), INTERVAL 2 HOUR)";
    private static final String UPDATE_BILL_SENT =
    	    "UPDATE seating SET billSent = ? WHERE seatingID = ?";    
        
    private static final String UPDATE_CHECKIN_TIME_NOW =
            "UPDATE seating " +
            "SET checkInTime = NOW() " +
            "WHERE seatingID = ? AND checkOutTime IS NULL AND checkInTime IS NULL";
    
    //SELECT
    private static final String SELECT_TABLE_ID_BY_SEATING_ID ="SELECT tableID FROM seating WHERE seatingID = ?";        
    private static final String SELECT_VISIT_TIMES_BETWEEN ="SELECT checkInTime, checkOutTime " +"FROM seating " +"WHERE checkInTime >= ? " +
    														"AND checkInTime < ? " +
    														"AND checkOutTime IS NOT NULL " +
    														"ORDER BY checkInTime";
    private final String SELECT_CURRENT_SEATINGS ="SELECT s.seatingID,t.tableNumber, t.capacity, s.checkInTime, s.checkOutTime, "+
    											  "DATE_ADD(s.checkInTime, INTERVAL 2 HOUR) AS estimatedCheckOutTime, r.reservationID, r.confirmationCode, r.partySize, "+
    											  "r.userID, r.guestContact, u.username "+
    											  "FROM seating s JOIN restaurant_table t ON t.tableID = s.tableID "+
    											  "JOIN reservation r ON r.reservationID = s.reservationID "+
    											  "LEFT JOIN user u ON u.userID = r.userID "+
    											  "WHERE s.checkOutTime IS NULL "+
    											  "ORDER BY t.tableNumber ASC";
    private final String SELECT_WHERE_CHECKIN_NULL= "SELECT checkInTime FROM seating WHERE seatingID = ?";
    
    private final String SELECT_OPEN_SEATING_TO_UPDATE = "SELECT seatingID, reservationID "+
    													 "FROM seating "+
    													 "WHERE tableID = ? AND checkOutTime IS NULL "+
    													 "ORDER BY checkInTime DESC "+
    													 "LIMIT 1 FOR UPDATE";
    private static final String SELECT_SEATINGS_DUE_FOR_BILL ="SELECT seatingID " +"FROM seating " +"WHERE checkOutTime IS NULL " +"AND billSent = 0 " +"AND checkInTime <= DATE_SUB(NOW(), INTERVAL 2 HOUR)";
    private static final String SELECT_RESERVATION_ID_BY_SEATING_ID = "SELECT reservationID FROM seating WHERE seatingID = ?";
    private static final String SELECT_SEATING_ID_BY_RESERVATION_ID ="SELECT seatingID " +"FROM seating " +"WHERE reservationID = ? " +"AND checkOutTime IS NULL " +
    																 "ORDER BY checkInTime DESC " +
    																 "LIMIT 1";
    
    
    public int insertHeldSeating(Connection conn, int tableId, int reservationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_HELD_SEATING, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, tableId);
            ps.setInt(2, reservationId);

            int affected = ps.executeUpdate();
            if (affected != 1) return -1;

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }
    public boolean isCheckInNull(Connection conn, int seatingId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_WHERE_CHECKIN_NULL)) {
            ps.setInt(1, seatingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getTimestamp(1) == null;
            }
        }
    }
    public boolean claimAutoBillSend(Connection conn, int seatingId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(CLAIM_AUTO_BILL_SEND)) {
            ps.setInt(1, seatingId);
            return ps.executeUpdate() == 1;
        }
    }
    public boolean updateBillSent(Connection conn, int seatingId, int billSent) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_BILL_SENT)) {
            ps.setInt(1, billSent);
            ps.setInt(2, seatingId);
            return ps.executeUpdate() == 1;
        }
    }
    
    public Integer getReservationIdBySeatingId(Connection conn, int seatingId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_RESERVATION_ID_BY_SEATING_ID)) {
            ps.setInt(1, seatingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("reservationID") : null;
            }
        }
    }
    public Integer getSeatingIdByReservationId(Connection conn, int reservationId) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("conn is null");
        }

        try (PreparedStatement ps = conn.prepareStatement(SELECT_SEATING_ID_BY_RESERVATION_ID)) {
            ps.setInt(1, reservationId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getInt("seatingID");
            }
        }
    }
    
    
    /**
     * Inserts a check-in row (opens its own connection).
     * @return seatingID (generated key) or -1 if failed.
     */
    public int checkIn(int tableID, int reservationID) throws SQLException {
        try (Connection conn = DBManager.getConnection()) {
            return checkIn(conn, tableID, reservationID);
        }
    }
    
    /**
     * Inserts a check-in row using an existing connection (for transactions).
     * @return seatingID (generated key) or -1 if failed.
     */
    public int checkIn(Connection conn, int tableID, int reservationID) throws SQLException {
        try (PreparedStatement ps =conn.prepareStatement(INSERT_SEATING, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, tableID);
            ps.setInt(2, reservationID); // if you want to allow walk-in: use ps.setNull(2, Types.INTEGER)

            int affected = ps.executeUpdate();
            if (affected != 1) return -1;

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }
    
    public boolean markCheckInNow(Connection conn, int seatingId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_CHECKIN_TIME_NOW)) {
            ps.setInt(1, seatingId);
            return ps.executeUpdate() == 1;
        }
    }
    public Integer fetchOpenSeatingID(Connection conn,int tableID)throws SQLException {
    	try(PreparedStatement ps = conn.prepareStatement(SELECT_OPEN_SEATING_TO_UPDATE)){
    		ps.setInt(1, tableID);
    		ResultSet rs = ps.executeQuery();
    		if(!rs.next()) return null;
    		return rs.getInt("seatingID");
    	}
    }
    
    public Integer findOpenSeatingReservationId(Connection conn, int tableId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_OPEN_SEATING_TO_UPDATE)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getInt("reservationID");
            }
        }
    }
    
    public boolean checkOutBySeatingId(Connection conn, int seatingId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_CHEKOUT_BY_SEATING_ID)) {
            ps.setInt(1, seatingId);
            return ps.executeUpdate() == 1;
        }
    }
    public List<Integer> getSeatingsDueForBill(Connection conn) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("Connection is null");
        }
        List<Integer> seatingIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_SEATINGS_DUE_FOR_BILL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                seatingIds.add(rs.getInt("seatingID"));
            }
        }

        return seatingIds;
    }
    
    public List<CurrentSeatingResponse> fetchCurrentSeating(Connection conn) throws SQLException {
    	List<CurrentSeatingResponse> list = new ArrayList<>();
    	try( PreparedStatement ps = conn.prepareStatement(SELECT_CURRENT_SEATINGS);
    		ResultSet rs = ps.executeQuery();){
    		  		
    		
    		while(rs.next()) {
    			LocalDateTime checkIn = rs.getTimestamp("checkInTime").toLocalDateTime();
    			LocalDateTime estimated = rs.getTimestamp("estimatedCheckOutTime").toLocalDateTime();
    			
    			TableInfo ti = new TableInfo(rs.getInt("tableNumber"), rs.getInt("capacity"));
    			
    			CurrentSeatingResponse cs = new CurrentSeatingResponse(
    					rs.getString("userID"),
    					rs.getString("username"),
    					ti,
    					rs.getString("guestContact"),
    					rs.getInt("partySize"),
    					rs.getInt("seatingID"),
    					estimated,
    					rs.getInt("confirmationCode"),
    					checkIn);
    			list.add(cs);
    		}
    				
    	}
    	return list;
    }
    
    public List<LocalDateTime[]> getVisitTimesBetween(Connection conn,LocalDateTime startInclusive,LocalDateTime endExclusive) throws SQLException {
        List<LocalDateTime[]> visits = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(SELECT_VISIT_TIMES_BETWEEN)) {
            ps.setTimestamp(1, Timestamp.valueOf(startInclusive));
            ps.setTimestamp(2, Timestamp.valueOf(endExclusive));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime checkIn =rs.getTimestamp("checkInTime").toLocalDateTime();
                    LocalDateTime checkOut =rs.getTimestamp("checkOutTime").toLocalDateTime();

                    // index 0 = check-in, index 1 = check-out
                    visits.add(new LocalDateTime[] { checkIn, checkOut });
                }
            }
        }
        return visits;
    }
    
    public Integer getTableIDBySeatingID(Connection conn, int seatingID) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_TABLE_ID_BY_SEATING_ID)) {

            ps.setInt(1, seatingID);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; 
                }
                return rs.getInt("tableID");
            }
        }
    }

}
