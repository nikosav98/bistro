package database;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import entities.Reservation;
import entities.Table;
import entities.WaitingList;
import entities.Seating;

public class WaitingListDAO {
	
	
	//INSERT
	private final String INSERT_NEW_WAIT = "INSERT INTO waiting_list (reservationID, status, priority, createdAt, assignedAt) " +
	        "VALUES (?, ?, ?, ?, NULL)";
	
	//SELECT
	private final String SELECT_NEXT_IN_LINE =
	        "SELECT w.waitID, w.reservationID, w.priority, w.status, w.createdAt " +
	        "FROM waiting_list w " +
	        "JOIN reservation r ON r.reservationID = w.reservationID " +
	        "WHERE w.status = 'WAITING' AND r.allocatedCapacity <= ? " +
	        "ORDER BY w.priority DESC, w.createdAt ASC " +
	        "LIMIT 1 FOR UPDATE";

	private static final String SELECT_RESERVATION_BY_WAIT_ID =
	        "SELECT r.* " +
	        "FROM waiting_list w " +
	        "JOIN reservation r ON w.reservationID = r.reservationID " +
	        "WHERE w.waitID = ?";
	private static final String SELECT_WAITINGLIST_BY_RESERVATION_ID =
	        "SELECT waitID, reservationID, status, priority, createdAt, assignedAt " +
	        "FROM waiting_list " +
	        "WHERE reservationID = ? " +
	        "ORDER BY waitID DESC " +
	        "LIMIT 1";
	//UPDATE
	private final String UPDATE_WAITLIST_STATUS = "UPDATE `waiting_list` SET status = ? WHERE reservationID = ?";
	private static final String UPDATE_WAITINGLIST_TO_ASSIGNED =
	        "UPDATE waiting_list SET status='ASSIGNED' " +
	        "WHERE reservationID = ? AND status='CALLED'";

	private final String UPDATE_STATUS_TO_CALLED = "UPDATE `waiting_list` SET status='CALLED', assignedAt=NOW() "+
			 "WHERE waitID = ? AND status = 'WAITING'";
	

	// SELECT (put near the top of WaitingListDAO)
	private static final String SELECT_WAITING_COUNTS_BY_DAY_BETWEEN =
	        "SELECT DAY(createdAt) AS dayOfMonth, COUNT(*) AS cnt " +
	        "FROM waiting_list " +
	        "WHERE createdAt >= ? AND createdAt < ? " +
	        "GROUP BY DAY(createdAt)";
	private static final String SELECT_WAITINGLIST_TODAY =
	        "SELECT * FROM waiting_list " +
	        "WHERE status = 'WAITING' " +
	        "AND assignedAt IS NULL " +
	        "AND DATE(createdAt) = CURRENT_DATE";
	private static final String SELECT_EXPIRED_CALLED_WAITINGLIST =
	        "SELECT waitID, reservationID, status, priority, createdAt, assignedAt " +
	        "FROM waiting_list " +
	        "WHERE status = 'CALLED' " +
	        "AND assignedAt IS NOT NULL " +
	        "AND assignedAt <= NOW() - INTERVAL 15 MINUTE";


	public Reservation getReservationByWaitingID(Connection conn,int waitID) throws SQLException {
		try(PreparedStatement ps=conn.prepareStatement(SELECT_RESERVATION_BY_WAIT_ID)){
			ps.setInt(1,waitID);
			try(ResultSet rs=ps.executeQuery()){
				if(!rs.next())return null;
				Reservation r= new Reservation(rs.getInt("reservationID"),
	                rs.getDate("reservationDate").toLocalDate(),
	                rs.getString("status"),
	                rs.getInt("partySize"),
	                rs.getInt("allocatedCapacity"),
	                rs.getInt("confirmationCode"),
	                rs.getString("guestContact"),  // may be null
	                rs.getString("userID"),        // may be null
	                rs.getTime("startTime") != null? rs.getTime("startTime").toLocalTime(): null,	                        	                        
	                rs.getTimestamp("timeOfCreation").toLocalDateTime());
				return r;
			}
			
		}
	}
	public WaitingList getNextWaitingThatFits(Connection conn, int tableCapacity) throws SQLException {
	    try (PreparedStatement ps = conn.prepareStatement(SELECT_NEXT_IN_LINE)) {
	        ps.setInt(1, tableCapacity);
	        try (ResultSet rs = ps.executeQuery()) {
	            if (!rs.next()) return null;

	            return new WaitingList(rs.getInt("waitID"),rs.getInt("reservationID"),rs.getString("status"),rs.getInt("priority"),rs.getTimestamp("createdAt").toLocalDateTime(),null);
	        }
	    }
	}

	

	
	//wrapper for insertNewWait for using multi DAO objects
	public boolean insertNewWait(int reservationID,String status,int priority) throws SQLException{
		try(Connection conn =DBManager.getConnection()){
			return insertNewWait(conn, reservationID, status, priority);
		}
	}
	/**
	 * method to inset a new row of waiting into database
	 * used when there is now available table to seat 
	 * @param conn 
	 * @param reservationID
	 * @param status
	 * @param priority
	 * @return
	 * @throws SQLException
	 */
	public boolean insertNewWait(Connection conn, int reservationId, String status, int priority) throws SQLException {

	    try (PreparedStatement ps = conn.prepareStatement(INSERT_NEW_WAIT)) {
	        ps.setInt(1, reservationId);
	        ps.setString(2, status);
	        ps.setInt(3, priority);
	        ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now())); 

	        return ps.executeUpdate() == 1;
	    }
	}
	
	public boolean updateWaitingStatus(Connection conn, int reservationID, String status) throws SQLException {
	    if (conn == null) {
	        throw new IllegalArgumentException("conn is null (updateWaitingStatus)");
	    }

	    try (PreparedStatement ps = conn.prepareStatement(UPDATE_WAITLIST_STATUS)) {
	        ps.setString(1, status);
	        ps.setInt(2, reservationID);

	        int affected = ps.executeUpdate();
	        return affected >= 1; // use == 1 if reservationID is guaranteed unique in waiting_list
	    }
	}
	public WaitingList getWaitingListByReservationId(Connection conn, int reservationID) throws SQLException {
	    if (conn == null) {
	        throw new IllegalArgumentException("conn is null (getWaitingListByReservationId)");
	    }

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_WAITINGLIST_BY_RESERVATION_ID)) {
	        ps.setInt(1, reservationID);

	        try (ResultSet rs = ps.executeQuery()) {
	            if (!rs.next()) return null;

	            return new WaitingList(
	                    rs.getInt("waitID"),
	                    rs.getInt("reservationID"),
	                    rs.getString("status"),
	                    rs.getInt("priority"),
	                    rs.getTimestamp("createdAt") != null
	                            ? rs.getTimestamp("createdAt").toLocalDateTime()
	                            : null,
	                    rs.getTimestamp("assignedAt") != null
	                            ? rs.getTimestamp("assignedAt").toLocalDateTime()
	                            : null
	            );
	        }
	    }
	}
	
	
	public boolean markAssignedIfCalled(Connection conn, int reservationId) throws SQLException {
	    try (PreparedStatement ps = conn.prepareStatement(UPDATE_WAITINGLIST_TO_ASSIGNED)) {
	        ps.setInt(1, reservationId);
	        return ps.executeUpdate() == 1;
	    }
	}
	
	public boolean markCalled(Connection conn, int waitId) throws SQLException {
	    try (PreparedStatement ps = conn.prepareStatement(UPDATE_STATUS_TO_CALLED)) {
	        ps.setInt(1, waitId);
	        return ps.executeUpdate() == 1;
	    }
	}
	
	public Integer[] getCountOfWaitingListBetween(Connection conn,LocalDateTime start,LocalDateTime end) throws SQLException {

	    Integer[] counts = new Integer[31];
	    for (int i = 0; i < counts.length; i++) counts[i] = 0;
	    if (start == null || end == null) {
	        return counts; 
	    }
	    try (PreparedStatement ps = conn.prepareStatement(SELECT_WAITING_COUNTS_BY_DAY_BETWEEN)) {
	        ps.setTimestamp(1, Timestamp.valueOf(start));
	        ps.setTimestamp(2, Timestamp.valueOf(end));

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
	
	/**
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	public List<WaitingList> fetchWaitingListByCurrentDate(Connection conn) throws SQLException {
	    List<WaitingList> waitingList = new ArrayList<>();

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_WAITINGLIST_TODAY);
	         ResultSet rs = ps.executeQuery()) {

	        while (rs.next()) {
	            WaitingList w = new WaitingList(
	                rs.getInt("waitID"),
	                rs.getInt("reservationID"),
	                rs.getString("status"),
	                rs.getInt("priority"),
	                rs.getTimestamp("createdAt").toLocalDateTime(),
	                null
	            );

	            waitingList.add(w);
	        }
	    }

	    return waitingList;
	}
	public List<WaitingList> fetchExpiredCalled(Connection conn) throws SQLException {
	    List<WaitingList> out = new ArrayList<>();

	    try (PreparedStatement ps = conn.prepareStatement(SELECT_EXPIRED_CALLED_WAITINGLIST);
	         ResultSet rs = ps.executeQuery()) {

	        while (rs.next()) {
	            WaitingList w = new WaitingList(
	                    rs.getInt("waitID"),
	                    rs.getInt("reservationID"),
	                    rs.getString("status"),
	                    rs.getInt("priority"),
	                    rs.getTimestamp("createdAt").toLocalDateTime(),
	                    rs.getTimestamp("assignedAt").toLocalDateTime() // assignedAt is your "called time"
	            );
	            out.add(w);
	        }
	    }
	    return out;
	}

}
