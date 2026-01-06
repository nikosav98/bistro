package controller_tests;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import java.sql.SQLException;

import controllers.UserControl;
import dao_stubs.UserDAOStub;
import entities.User;
import requests.LoginRequest;
import responses.LoginResponse;
import responses.Response;

/**
 * Test suite for {@link UserControl}.
 * Verifies the authentication logic for users, including successful logins
 * and handling of invalid credentials.
 */
public class UserControlTest {

    private UserControl userControl;
    private UserDAOStub userStub;
    
    // A test user that we will "store" in our stub memory
    private User validUser;

    /**
     * Sets up the test environment.
     * We create an anonymous subclass of UserDAOStub to override the specific 
     * login method used by the controller.
     */
    @Before
    public void setup() {
        validUser = new User("U100", "testUser", "password123", "CUSTOMER", "123456", "test@mail.com");

        // We extend the stub to handle the specific login method
        userStub = new UserDAOStub() {
            @Override
            public User getUserByUsernameAndPassword(String username, String password) throws SQLException {
                if (validUser.getUsername().equals(username) && validUser.getPassword().equals(password)) {
                    return validUser;
                }
                return null;
            }
        };

        userControl = new UserControl(userStub);
    }

    /**
     * Tests a successful login scenario with valid credentials.
     * Checks if the response contains the correct user ID and role.
     */
    @Test
    public void login_ValidCredentials_ReturnsSuccess() throws SQLException {
        LoginRequest req = new LoginRequest("testUser", "password123");
        
        Response<LoginResponse> resp = userControl.login(req);

        assertTrue("Login should be successful", resp.isSuccess());
        assertEquals("Hello" + validUser.getUsername(), resp.getMessage());
        assertNotNull("Response data should not be null", resp.getData());
        assertEquals("U100", resp.getData().getUserID());
        assertEquals("CUSTOMER", resp.getData().getRole());
    }

    /**
     * Tests login failure when providing an incorrect password.
     */
    @Test
    public void login_InvalidPassword_ReturnsFailure() throws SQLException {
        LoginRequest req = new LoginRequest("testUser", "wrong_password");
        
        Response<LoginResponse> resp = userControl.login(req);

        assertFalse("Login should fail for wrong password", resp.isSuccess());
        assertEquals("Invalid username or password", resp.getMessage());
        assertNull("Data should be null on failure", resp.getData());
    }

    /**
     * Tests login failure when the username does not exist in the system.
     */
    @Test
    public void login_NonExistentUser_ReturnsFailure() throws SQLException {
        LoginRequest req = new LoginRequest("unknown_user", "password123");
        
        Response<LoginResponse> resp = userControl.login(req);

        assertFalse("Login should fail for non-existent user", resp.isSuccess());
        assertNull(resp.getData());
    }
}