package controller_tests;

import static org.junit.Assert.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import controllers.SeatingControl;
import dao_stubs.ReservationDAOStub;
import dao_stubs.TableDAOStub;
import dao_stubs.SeatingDAOStub;
import dao_stubs.WaitingListDAOStub;

import entities.Reservation;
import entities.Table;
import responses.Response;

/**
 * Test suite for {@link SeatingControl}.
 * This class tests the check-in process, ensuring that the system 
 * correctly validates reservation details and manages table assignments.
 */
public class SeatingControlTest {

    private SeatingControl seatingControl;
    private ReservationDAOStub reservationStub;
    private SeatingDAOStub seatingStub;
    private WaitingListDAOStub waitingStub;
    
    /** Local list to simulate available tables for the anonymous stub */
    private List<Table> testTables;
    private TableDAOStub tableStub;

    /**
     * Sets up the test environment.
     * We create an anonymous subclass of TableDAOStub to implement findAvailableTable
     * without modifying the original stub file in the project.
     */
    @Before
    public void setup() {
        reservationStub = new ReservationDAOStub();
        seatingStub = new SeatingDAOStub();
        waitingStub = new WaitingListDAOStub();
        testTables = new ArrayList<>();

        // Anonymous extension to handle table searching logic locally
        tableStub = new TableDAOStub() {
            @Override
            public Table findAvailableTable(Connection conn, int capacity) throws SQLException {
                return testTables.stream()
                        .filter(t -> t.getCapacity() >= capacity)
                        .findFirst()
                        .orElse(null);
            }
        };

        seatingControl = new SeatingControl(reservationStub, tableStub, seatingStub, waitingStub);
    }

    /**
     * Tests a successful check-in scenario.
     * A guest with a valid confirmation code should be assigned an available table.
     */
    @Test
    public void checkIn_Success_SeatsCustomer() throws Exception {
        int code = 104;
        // Creating a reservation for today at current time
        Reservation r = new Reservation(1, LocalDate.now(), "CONFIRMED", 2, 2, code, "guest@test.com", null, LocalTime.now());
        reservationStub.addReservation(r);

        // Populate our local test list with one suitable table
        testTables.add(new Table(5, 12, 2, "Available")); 

        Response<Table> resp = seatingControl.checkInByConfirmationCode(code);

        assertTrue("Check-in should be successful when a table is available", resp.isSuccess());
        assertNotNull("Response should contain the assigned table", resp.getData());
        assertEquals("Should assign the correct table number", 12, resp.getData().getTableNumber());
        
        // Verify state change in the reservation
        Reservation seated = reservationStub.getReservationByConfirmationCode(code);
        assertEquals("Status should change to SEATED", "SEATED", seated.getStatus());
    }

    /**
     * Tests the scenario where no suitable tables are available.
     * The guest should be added to the waiting list automatically.
     */
    @Test
    public void checkIn_NoTableAvailable_MovesToWaitingList() throws Exception {
        int code = 105;
        Reservation r = new Reservation(1, LocalDate.now(), "CONFIRMED", 2, 2, code, "guest@test.com", null, LocalTime.now());
        reservationStub.addReservation(r);

        // testTables is left empty, forcing findAvailableTable to return null
        Response<Table> resp = seatingControl.checkInByConfirmationCode(code);

        assertFalse("Should return failure response when no tables are free", resp.isSuccess());
        assertEquals("No table right now - added to waiting list", resp.getMessage());
        
        // Verify status update
        assertEquals("WAITING", reservationStub.getReservationByConfirmationCode(code).getStatus());
    }

    /**
     * Verifies that check-in is rejected if the reservation is for a different date.
     */
    @Test
    public void checkIn_WrongDate_Fails() {
        int code = 106;
        Reservation r = new Reservation(1, LocalDate.now().plusDays(1), "CONFIRMED", 2, 2, code, "guest@test.com", null, LocalTime.now());
        reservationStub.addReservation(r);

        Response<Table> resp = seatingControl.checkInByConfirmationCode(code);
        assertFalse(resp.isSuccess());
        assertEquals("Not the date of the resevation", resp.getMessage());
    }
}
