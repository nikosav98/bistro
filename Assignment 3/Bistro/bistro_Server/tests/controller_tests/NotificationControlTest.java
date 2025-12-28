package controller_tests;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import controllers.NotificationControl;
import entities.User;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Test suite for {@link NotificationControl}.
 * This class verifies the routing logic for notifications (Email vs SMS)
 * by intercepting console output.
 */
public class NotificationControlTest {

    private NotificationControl control;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    /**
     * Sets up the test environment by initializing the controller 
     * and redirecting System.out to capture console logs.
     */
    @Before
    public void setup() {
        control = new NotificationControl();
        System.setOut(new PrintStream(outContent));
    }

    /**
     * Restores the original System.out stream after each test execution.
     */
    @After
    public void restoreStream() {
        System.setOut(originalOut);
    }

    // -------------------- User Notification Tests --------------------

    /**
     * Verifies that if a User has both email and phone number, 
     * the system triggers both notification methods.
     */
    @Test
    public void sendConfirmationToUser_bothEmailAndPhone_sendsBoth() {
        User user = new User("U1", "John", "pass", "CUSTOMER", "123456789", "john@mail.com");
        int code = 111;
        
        control.sendConfirmationToUser(user, code);

        String output = outContent.toString();
        assertTrue("Output should contain Email log", output.contains("[EMAIL] To: john@mail.com"));
        assertTrue("Output should contain SMS log", output.contains("[SMS] To: 123456789"));
        assertTrue("Output should contain the confirmation code", output.contains(String.valueOf(code)));
    }

    /**
     * Verifies that if a User only has an email address, 
     * only the email notification is sent.
     */
    @Test
    public void sendConfirmationToUser_onlyEmail_sendsOnlyEmail() {
        User user = new User("U2", "Jane", "pass", "CUSTOMER", null, "jane@mail.com");
        control.sendConfirmationToUser(user, 222);

        String output = outContent.toString();
        assertTrue("Email should be sent", output.contains("[EMAIL]"));
        assertFalse("SMS should not be sent for user without phone", output.contains("[SMS]"));
    }

    // -------------------- Guest Notification Tests --------------------

    /**
     * Tests the routing logic for guests. 
     * A contact string containing '@' and '.' should be treated as an email.
     */
    @Test
    public void sendConfirmationToGuest_looksLikeEmail_sendsEmail() {
        String contact = "guest@example.com";
        control.sendConfirmationToGuest(contact, 333);
        
        String output = outContent.toString();
        assertTrue("Should detect contact as Email", output.contains("[EMAIL] To: " + contact));
        assertFalse("Should not send SMS when Email is detected", output.contains("[SMS]"));
    }

    /**
     * Tests the routing logic for guests.
     * A contact string without typical email characters should default to SMS.
     */
    @Test
    public void sendConfirmationToGuest_looksLikePhone_sendsSms() {
        String contact = "987654321";
        control.sendConfirmationToGuest(contact, 444);
        
        String output = outContent.toString();
        assertTrue("Should detect contact as SMS provider", output.contains("[SMS] To: " + contact));
        assertFalse("Should not send Email when it looks like a phone number", output.contains("[EMAIL]"));
    }

    // -------------------- Edge Case Tests --------------------

    /**
     * Ensures that providing an empty string as guest contact 
     * does not trigger any notification and does not crash.
     */
    @Test
    public void sendConfirmationToGuest_emptyContact_doesNothing() {
        control.sendConfirmationToGuest("", 555);
        assertEquals("Output should be empty for empty contact", "", outContent.toString().trim());
    }

    /**
     * Ensures that the system handles a null User object gracefully 
     * without throwing a NullPointerException.
     */
    @Test
    public void sendConfirmationToUser_nullUser_doesNothing() {
        control.sendConfirmationToUser(null, 0);
        assertEquals("Output should be empty for null user", "", outContent.toString().trim());
    }
}