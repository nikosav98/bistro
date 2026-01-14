package database;

import entities.User;
import responses.UserHistoryResponse;

import java.sql.*;
import java.util.List;



import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalTime;


/**
 * METHODS THIS CLASS HAS:
 * 1.getUserByUsernameAndPassword - fetching user with username and password from database
 */
public class UserDAO {
	
	//INSERT 
		private final String INSERT_NEW_USER = "INSERT INTO user (userID, username, password, role, phone, email) VALUES(?, ?, ?, ?, ?, ?)";

	//SELECT statements
		private final String SELECT_USER_BY_USERNAME = "SELECT * FROM `user` WHERE username = ?";
		private final String SELECT_ALL_SUBSCRIBER = "SELECT * FROM `user` WHERE role = 'SUBSCRIBER'";
		private static final String SELECT_LOGIN ="SELECT userID, username, role, phone, email FROM `user` WHERE username= ? AND password= ?";
		private static final String SELECT_USER_BY_ID ="SELECT userID, username, role, phone, email FROM `user` WHERE userID = ?";
		private static final String SELECT_HISTORY ="SELECT r.reservationDate, r.startTime, r.partySize, s.checkInTime, s.checkOutTime, t.tableNumber, b.totalPrice "+
													"FROM reservation r " +
													"JOIN seating s ON s.reservationID = r.reservationID "+
													"JOIN restaurant_table t ON t.tableID = s.tableID "+
													"LEFT JOIN bill b ON b.seatingID = s.seatingID "+
													"WHERE r.userID = ? "+
													"ORDER BY r.reservationDate DESC, r.startTime";
		
	//UPDATE
		private final String UPDATE_USER_DETAILS_BY_ID = "UPDATE `user` SET phone = ?, email = ? WHERE userID = ?";
				
		public List<User> fetchAllUsers(Connection conn)throws SQLException{
			List<User> users = new ArrayList<>();
			try(PreparedStatement ps = conn.prepareStatement(SELECT_ALL_SUBSCRIBER)){
				ResultSet rs = ps.executeQuery();
				while(rs.next()) {
					User user = new User(
							rs.getString("userID"),
							rs.getString("username"),
							rs.getString("password"),
							rs.getString("role"),
							rs.getString("phone"),
							rs.getString("email"));
					users.add(user);
				}
				return users;
			}
		}
		
		public boolean insertNewUser(String userID,String username,String password,String role,String phone, String email)throws SQLException{
			try(Connection conn  = DBManager.getConnection();
					PreparedStatement ps = conn.prepareStatement(INSERT_NEW_USER)){
				ps.setString(1, userID);
				ps.setString(2, username);
				ps.setString(3, password);
				ps.setString(4, role);
				ps.setString(5, phone);
				ps.setString(6, email);
				
				int insert = ps.executeUpdate();
				return insert == 1;				
			}
		}
		
		
		/**
		 * @param username- username field
		 * @param password- password field
		 * @return the user with matching username and password,null if there are no matches.
		 * @throws SQLException if there is a database error
		 */
		public User getUserByUsernameAndPassword(Connection conn,String username, String password) throws SQLException {

		    try (PreparedStatement ps = conn.prepareStatement(SELECT_LOGIN)) {

		        ps.setString(1, username);
		        ps.setString(2, password);

		        ResultSet rs = ps.executeQuery();

		        if (rs.next()) {
		        	User user = new User(
		        		    rs.getString("userID"),
		        		    rs.getString("username"),
		        		    null,                    // password is not exposed
		        		    rs.getString("role"),
		        		    rs.getString("phone"),
		        		    rs.getString("email")
		        		);		        	
		        	return user;
		        }

		    } catch (SQLException e) {
		        System.err.println("DB error during login");
		           
		    }

		    return null;
		}

		
		
		/**
		 * Fetches a user by userID.
		 *
		 * @param userID the unique user identifier
		 * @return User object if found, null otherwise
		 * @throws SQLException if a database error occurs
		 */
		    public User getUserByUserID(Connection conn,String userID) throws SQLException {

		        try (PreparedStatement ps = conn.prepareStatement(SELECT_USER_BY_ID)) {
		            ps.setString(1, userID);
		            ResultSet rs = ps.executeQuery();

		            if (rs.next()) {
		            	return new User(
		            		    rs.getString("userID"),
		            		    rs.getString("username"),
		            		    null,                    // password is not exposed
		            		    rs.getString("role"),
		            		    rs.getString("phone"),
		            		    rs.getString("email")
		            		);
		            }
		        } catch (SQLException e) {
		            System.err.println("DB error fetching user by userID");
		            throw e;
		        }

		        return null;
		    }
		    
		    
		    public boolean updateUserDetailsByUserID(Connection conn,String userID,String email,String phone)throws SQLException{
		    	try(PreparedStatement ps = conn.prepareStatement(UPDATE_USER_DETAILS_BY_ID)){
		    		ps.setString(1, phone);
		    		ps.setString(2, email);
		    		ps.setString(3,userID);
		    		return ps.executeUpdate() == 1;		    		
		    	}		    			    	
		    }
		    
		    public List<UserHistoryResponse> getHistoryByUserID(Connection conn,String userID) throws SQLException{
		    	List<UserHistoryResponse> historyList = new ArrayList<>();
		    	
		    	try(PreparedStatement ps = conn.prepareStatement(SELECT_HISTORY)){
		    		ps.setString(1, userID);
		    		ResultSet rs = ps.executeQuery();
		    		
		    		while(rs.next()) {
		    			LocalDate resDate = rs.getDate("reservationDate").toLocalDate();
		                LocalTime reservedFor = rs.getTime("startTime").toLocalTime();
		                
		                Time inTime = rs.getTime("checkInTime");
		                LocalTime checkIn = (inTime == null) ? null : inTime.toLocalTime();
		                
		                Time outTime = rs.getTime("checkOutTime");
		                LocalTime checkOut = (outTime == null) ? null : outTime.toLocalTime();
		                
		                int tableNumber = rs.getInt("tableNumber");
		                
		                Double totalPrice = rs.getObject("totalPrice") == null ? null : rs.getDouble("totalPrice");
		                
		                int partySize = rs.getInt("partySize");
		                
		                historyList.add(new UserHistoryResponse(resDate,reservedFor,checkIn,checkOut,tableNumber,totalPrice,partySize));
		                
		    		}
		    	}
		    	return historyList;
		    }
		    
		    
		    public User fetchUserByUsername(Connection conn,String username)throws SQLException{
		    	try(PreparedStatement ps = conn.prepareStatement(SELECT_USER_BY_USERNAME)){
		    		ps.setString(1,username);
		    		ResultSet rs = ps.executeQuery();
		    		if(rs.next()) {
		    			return new User(
		            		    rs.getString("userID"),
		            		    rs.getString("username"),
		            		    null,                    
		            		    rs.getString("role"),
		            		    rs.getString("phone"),
		            		    rs.getString("email")
		            		);
		    		}
		    		return null;
		    	}
		    }
		    
}
