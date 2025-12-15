package database;

import entities.User;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;


/**
 * METHODS THIS CLASS HAS:
 * 1.getUserByUsername - fetching user with username from database
 */
public class UserDAO {

	//SELECT statements
	private static final String SELECT_LOGIN ="SELECT userID, role FROM `user` WHERE username=? AND password=?";

	
	/**
	 * @param username- username field
	 * @param password- password field
	 * @return the user with matching username and password,null if there are no matches.
	 * @throws SQLException if there is a database error
	 */
	public User getUserByUsernameAndPassword(String username, String password) throws SQLException
	{

	    try (Connection conn = DBManager.getConnection();
	         PreparedStatement ps = conn.prepareStatement(SELECT_LOGIN)) {

	        ps.setString(1, username);
	        ps.setString(2, password);

	        ResultSet rs = ps.executeQuery();

	        if (rs.next()) {
	            return new User(
	                rs.getString("userID"),
	                rs.getString("username"),
	                null,
	                rs.getString("role")
	            );
	        }

	    } catch (SQLException e) {
	        System.err.println("DB error during login");
	        e.printStackTrace();
	    }

	    return null; 
	}

}
