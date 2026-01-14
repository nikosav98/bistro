package controllers;

import java.time.LocalDate;
import java.time.LocalTime;

import client.BistroEchoClient;
import desktop_screen.DesktopScreenController;
import kryo.KryoUtil;
import java.util.HashMap;
import java.util.Map;
//requests
import requests.ReportRequest;
import requests.ManagerRequest;
import requests.BillRequest;
import requests.BillRequest.BillRequestType;
import requests.LoginRequest;
import requests.LoginRequest.UserCommand;
import requests.Request;
import requests.ReservationRequest;
import requests.ReservationRequest.ReservationRequestType;
import requests.SeatingRequest;
import requests.SeatingRequest.SeatingRequestType;
//responses
import responses.ManagerResponse;
import responses.ReportResponse;
import responses.BillResponse;
import responses.SeatingResponse;
import responses.LoginResponse;
import responses.ReservationResponse;
import responses.Response;
import responses.UserHistoryResponse;

/**
 * Central application controller for the Bistro Echo Client.
 * All JavaFX screen controllers call THIS class (UI -> Controller)
 * Only THIS class sends requests to server (Controller -> Network)
 * Only THIS class routes responses from server (Network -> Controller)
 *
 * Important: OCSF calls back on a non JavaFX thread
 * This controller must NOT directly touch JavaFX
 */
public class ClientController {

    private final BistroEchoClient client;
    private ClientUIHandler ui;
    private boolean connected;
    
    private java.util.function.Consumer<Integer> lostCodeListener;


    // Session identity state
    private boolean guestSession;
    private String guestContact;
    private String currentUserId;
    private String currentUsername;
    private String lastLoginUsername;
    private String currentEmail;
    private String currentPhone;
    private UserCommand lastUserCommand;

    public boolean getGuestSession(){
    	return this.guestSession;
    }
    
    public ClientController(BistroEchoClient client) {
        this.client = client;
    }

    /** Called once during UI startup */
    public void setUIHandler(ClientUIHandler ui) {
        this.ui = ui;
    }
    
    //connected boolean
    public boolean isConnected() {
        return connected;
    }

    // UI - Controller API
    /**
     * Generic send method. Screens can call this with any request object
     * (e.g., LoginRequest, ReservationRequest, etc.) from your common package.
     */
    public void sendRequest(Object request) {
        if (request == null) {
            safeUiError("Client Error", "Tried to send a null request.");
            return;
        }
        if (!connected) {
            safeUiWarning("Offline Mode", "Not connected to server. This action is unavailable.");
            return;
        }

        try {
            // Convert to bytes using Kryo
            byte[] payload = KryoUtil.serialize(request);

            // Send the byte array using OCSF
            client.sendToServer(payload); 
        	}
         catch (Exception e) {
            safeUiError("Connection Error", "Failed to send request to server.\n" + e.getMessage());
        }
    }


    // Network -> Controller
    /** Called ONLY by BistroEchoClient.handleMessageFromServer(msg) */
    public void handleServerResponse(Object msg) {
        try {
            Object decoded = msg;
            if (msg instanceof byte[] bytes) {
                decoded = KryoUtil.deserialize(bytes);
            }

            if (!(decoded instanceof Response<?> response)) {
                safeUiInfo("Failed", "failed to deserialize response");
                return;
            }

            if (!response.isSuccess()) {
                safeUiError("Failed operation", response.getMessage());
                return;
            }

            Object responseData = response.getData();

            // user history payload: Response<List<UserHistoryResponse>>
            if (responseData instanceof java.util.List<?> list ) {
            	if (list.isEmpty()) {
                    if (lastUserCommand == UserCommand.UPCOMING_RESERVATIONS_REQUEST) {
                        if (ui != null) {
                            ui.onUpcomingReservationsResponse(java.util.List.of());
                        } else {
                            safeUiInfo("Upcoming Reservations", "Upcoming reservations received. Rows: 0");
                        }
                        return;
                    }
                    if (lastUserCommand == UserCommand.HISTORY_REQUEST) {
                        if (ui != null) {
                            ui.onUserHistoryResponse(java.util.List.of());
                        } else {
                            safeUiInfo("History", "History response received. Rows: 0");
                        }
                        return;
                    }
                    if (list.get(0) instanceof ReservationResponse) {
                        @SuppressWarnings("unchecked")
                        java.util.List<ReservationResponse> rows = (java.util.List<ReservationResponse>) list;
                        if (ui != null) {
                            ui.onUpcomingReservationsResponse(rows);
                        } else {
                            safeUiInfo("Upcoming Reservations", "Upcoming reservations received. Rows: " + rows.size());
                        }
                        return;
                    }
                }
                if (list.get(0) instanceof UserHistoryResponse) {
                    @SuppressWarnings("unchecked")
                    java.util.List<UserHistoryResponse> rows = (java.util.List<UserHistoryResponse>) list;

                    if (ui != null) {
                        ui.onUserHistoryResponse(rows);
                    } else {
                        safeUiInfo("History", "History response received. Rows: " + rows.size());
                    }
                    return;
                }
            }

            else if (responseData instanceof LoginResponse loginResponse) {
                switch (loginResponse.getResponseCommand()) {
                    case LOGIN_RESPONSE -> {
                        DesktopScreenController.Role uiRole =
                                mapRoleFromServer(loginResponse.getRole());

                        // update session state for member login
                        this.guestSession = false;
                        this.guestContact = null;
                        this.currentUserId = loginResponse.getUserID();
                        this.currentUsername = loginResponse.getUsername();
                        this.currentEmail = loginResponse.getEmail();
                        this.currentPhone = loginResponse.getPhone();
  

                        // choose username for server history lookup
                        String serverUsername = loginResponse.getUsername();
                        serverUsername = serverUsername == null ? null : serverUsername.trim();

                        // reuse the username typed in the login screen if server did not return it
                        if (serverUsername != null && !serverUsername.isEmpty()) {
                            this.currentUsername = serverUsername;
                        } else {
                            this.currentUsername = lastLoginUsername == null ? null : lastLoginUsername.trim();
                        }
                        
                        // THIS is where we move to the next screen
                        if (ui != null) {
                        	//move on with the respected role, and username
                            ui.routeToDesktop(uiRole, loginResponse.getUsername());
                        } else {
                            System.out.println("[INFO] Login success for " +
                                    loginResponse.getUsername() + " as " + uiRole);
                        }
                    }
                    case EDIT_RESPONSE -> {
                        //update local cache if server returns updated values
                        this.currentEmail = loginResponse.getEmail();
                        this.currentPhone = loginResponse.getPhone();
                        safeUiInfo("Subscriber Details", "Details updated successfully");
                    }
                    case HISTORY_RESPONSE -> {
                        java.util.List<UserHistoryResponse> rows = loginResponse.getUserHistory();
                        if (ui != null) {
                            ui.onUserHistoryResponse(rows == null ? java.util.List.of() : rows);
                        } else {
                            safeUiInfo("History", "History response received. Rows: " + (rows == null ? 0 : rows.size()));
                        }
                    }
                    case UPCOMING_RESERVATIONS_RESPONSE -> {
                        java.util.List<ReservationResponse> rows = loginResponse.getUpcomingReservations();
                        if (ui != null) {
                            ui.onUpcomingReservationsResponse(rows == null ? java.util.List.of() : rows);
                        } else {
                            safeUiInfo("Upcoming Reservations", "Upcoming reservations received. Rows: " + (rows == null ? 0 : rows.size()));
                        }
                    }
                }
            }

            else if (responseData instanceof ReservationResponse reservationResponse) {

                // if this is the final confirmation, show a global info message + confirmation code
                if (reservationResponse.getType() == ReservationResponse.ReservationResponseType.SECOND_PHASE_CONFIRMED) {
                    String messege = "Reservation confirmed successfully. "
                               + "Confirmation code: " + reservationResponse.getConfirmationCode();
                    safeUiInfo("Reservation", messege);
                }

                //push to UI handler
                if (ui != null) {
                    ui.onReservationResponse(reservationResponse);
                } else {
                    uiPayload(reservationResponse);
                }
            }
            
            else if (responseData instanceof responses.SeatingResponse seatingResponse) {

                if (seatingResponse.getType() == responses.SeatingResponse.SeatingResponseType.CUSTOMER_CHECKED_IN) {
                    Integer tn = seatingResponse.getTableNumberl();
                    String seatingMessage = "Checked-in successfully."
                            + (tn != null ? (" Table: " + tn) : "");
                    safeUiInfo("Check-in", seatingMessage);
                } else if (seatingResponse.getType() == responses.SeatingResponse.SeatingResponseType.CUSTOMER_IN_WAITINGLIST) {
                    safeUiInfo("Check-in", "No table available. You were added to the waiting list.");
                }

                // push to UI handler
                if (ui != null) {
                    ui.onSeatingResponse(seatingResponse);
                } else {
                    uiPayload(seatingResponse);
                }
            }
            else if (responseData instanceof ReportResponse reportResponse) {
                if (ui != null) {
                    ui.onReportResponse(reportResponse);
                } else {
                    safeUiInfo("Reports", "Report received for " + reportResponse.getMonth());
                }
                return;
            }
            
            else if (responseData instanceof ManagerResponse managerResponse) {
                if (ui != null) {
                    ui.onManagerResponse(managerResponse);
                } else {
                    uiPayload(managerResponse);
                }
            }
            
            else if (responseData instanceof Integer confirmationCode) {
            	if (lostCodeListener != null) {
                    lostCodeListener.accept(confirmationCode);
                    return;
                }
            }
            // billing payloads are handled using reflection to avoid tight coupling
            if (responseData instanceof BillResponse billResponse) {
                handleBillResponse(billResponse);
            }

            // handle other response types here (Reservations, etc)

        } catch (Exception e) {
            safeUiError("Client Error", "Error handling server response:\n" + e.getMessage());
            e.printStackTrace();
        }
    }
    public void requestWalkInSeating(String userId, String guestContact, int partySize) {
        if (!connected) {
            safeUiWarning("Take a seat", "Not connected to server.");
            return;
        }

        String trimmedUserId = userId == null ? "" : userId.trim();
        String trimmedGuestContact = guestContact == null ? "" : guestContact.trim();

        if (trimmedUserId.isEmpty() && trimmedGuestContact.isEmpty()) {
            safeUiWarning("Take a seat", "Subscriber ID or guest contact is required.");
            return;
        }

        ReservationRequest reservationRequest = new ReservationRequest(
                null,
                LocalDate.now(),
                LocalTime.now(),
                partySize,
                trimmedUserId.isEmpty() ? null : trimmedUserId,
                trimmedGuestContact.isEmpty() ? null : trimmedGuestContact,
                0
        );

        SeatingRequest payload = new SeatingRequest(SeatingRequestType.BY_RESERVATION,0,reservationRequest);

        Request<SeatingRequest> req =new Request<>(Request.Command.SEATING_REQUEST, payload);
                

        sendRequest(req);
    }
    public void requestReports(ReportRequest reportRequest) {
        if (!connected) {
            safeUiWarning("Reports", "Not connected to server.");
            return;
        }
        if (reportRequest == null) {
            safeUiWarning("Reports", "Missing report request.");
            return;
        }

        Request<ReportRequest> req = new Request<>(Request.Command.REPORT_REQUEST, reportRequest);
        sendRequest(req);
    }
    
    
    public void setLostCodeListener(java.util.function.Consumer<Integer> listener) {
        this.lostCodeListener = listener;
    }

    public void clearLostCodeListener() {
        this.lostCodeListener = null;
    }

    public void requestLostCode(String contactRaw) {
        if (!connected) {
            safeUiWarning("Retrieve Code", "Not connected to server.");
            return;
        }

        String contact = contactRaw == null ? null : contactRaw.trim();
        if (contact == null || contact.isEmpty()) {
            safeUiWarning("Retrieve Code", "Missing contact or user ID.");
            return;
        }

        Request<String> req = new Request<>(Request.Command.LOST_CODE, contact);
        sendRequest(req);
    }
    
    // Login and information verification
    public void requestLogin(String usernameRaw, String passwordRaw) {
        String username = usernameRaw == null ? "" : usernameRaw.trim();
        String password = passwordRaw == null ? "" : passwordRaw.trim();
        this.lastLoginUsername = username == null ? null : username.trim();

        String err = validateUsername(username);
        if (err != null) { safeUiWarning("Login", err); return; }

        err = validatePassword(password);
        if (err != null) { safeUiWarning("Login", err); return; }

        this.guestSession = false;
        this.guestContact = null;
        LoginRequest loginRequest = new LoginRequest(username,password,UserCommand.LOGIN_REQUEST);       
        Request<LoginRequest> req = new Request<LoginRequest>(Request.Command.USER_REQUEST,loginRequest);
        sendRequest(req);
    }
    // User history request
    public void requestUserHistory() {
        if (!connected) {
            safeUiWarning("History", "Not connected to server.");
            return;
        }

        String username = guestSession ? guestContact : currentUsername;
        username = username == null ? null : username.trim();

        System.out.println("[HISTORY_REQUEST] sending username='" + username + "'");

        if (username == null || username.isEmpty()) {
            safeUiWarning("History", "No active user session.");
            return;
        }

        LoginRequest payload = new LoginRequest(username, null, UserCommand.HISTORY_REQUEST);
        Request<LoginRequest> req = new Request<>(Request.Command.USER_REQUEST, payload);
        lastUserCommand = UserCommand.HISTORY_REQUEST;
        sendRequest(req);
    }
    public void requestUpcomingReservations() {
        if (!connected) {
            safeUiWarning("Upcoming Reservations", "Not connected to server.");
            return;
        }

        String username = currentUsername == null ? null : currentUsername.trim();
        if (username == null || username.isEmpty()) {
            safeUiWarning("Upcoming Reservations", "No active user session.");
            return;
        }

        LoginRequest payload = new LoginRequest(username, null, UserCommand.UPCOMING_RESERVATIONS_REQUEST);
        Request<LoginRequest> req = new Request<>(Request.Command.USER_REQUEST, payload);
        lastUserCommand = UserCommand.UPCOMING_RESERVATIONS_REQUEST;
        sendRequest(req);
    }
    
    //for manager use
    public void requestUserHistoryForSubscriber(String usernameRaw) {
        if (!connected) {
            safeUiWarning("History", "Not connected to server.");
            return;
        }

        String username = usernameRaw == null ? null : usernameRaw.trim();

        System.out.println("[HISTORY_REQUEST] sending username='" + username + "'");

        if (username == null || username.isEmpty()) {
            safeUiWarning("History", "No active user session.");
            return;
        }

        LoginRequest payload = new LoginRequest(username, null, UserCommand.HISTORY_REQUEST);
        Request<LoginRequest> req = new Request<>(Request.Command.USER_REQUEST, payload);
        lastUserCommand = UserCommand.HISTORY_REQUEST;
        sendRequest(req);
    }
    // Manager request
    public void requestManagerAction(ManagerRequest request) {
        if (!connected) {
            safeUiWarning("Manager", "Not connected to server.");
            return;
        }
        if (request == null) {
            safeUiWarning("Manager", "Missing manager request.");
            return;
        }

        Request<ManagerRequest> req =
                new Request<>(Request.Command.MANAGER_REQUEST, request);
        sendRequest(req);
    }
    // Billing helpers
    /**
     * Request billing actions (See total or Pay)
     */
    public void requestBillAction(BillRequestType type, int confirmationCode, boolean isCash) {
        if (!connected) {
            safeUiWarning("Billing", "Not connected to server.");
            return;
        }

        // Wrap the request in the generic Request envelope
        BillRequest payload = new BillRequest(type,confirmationCode);
        Request<BillRequest> req = new Request<>(Request.Command.BILLING_REQUEST, payload);
        
        sendRequest(req);
    }
    
    private void handleBillResponse(BillResponse response) {
        if (ui == null) return;

        switch (response.getType()) {
            case ANSWER_TO_REQUEST_TO_SEE_BILL -> {
                // BillResponse uses getBill() to return the double value [cite: 1]
                ui.onBillTotal(response.getBill(), false); 
            }
            case ANSWER_TO_PAY_BILL -> {
                // Signals the UI that payment was successful 
                ui.onBillPaid(null); 
            }
        }
    }
    
    public void logout() {
        try {
            if (connected && client != null) {
                client.closeConnection();   // OCSF AbstractClient.closeConnection()
            }
        } catch (Exception e) {
            safeUiError("Logout", "Error while closing connection:\n" + e.getMessage());
        } finally {
            connected = false;
            

            // reset session identity
            guestSession = false;
            guestContact = null;
            currentUsername = null;
            currentEmail = null;
            currentPhone = null;
            currentUserId = null;

            // Optionally notify UI to go back to login screen
            if (ui != null) {
                ui.showInfo("Logout", "You have been logged out.");
                //ui.routeToLogin(); // <-- add this to your ClientUIHandler if you do not have it yet
            }
        }
    }


    public void requestSeatingCheckInByConfirmationCode(int confirmationCode) {
        if (!connected) {
            safeUiWarning("Check-in", "Not connected to server.");
            return;
        }

        SeatingRequest payload = new SeatingRequest(SeatingRequestType.BY_CONFIRMATIONCODE,confirmationCode,null);

        Request<SeatingRequest> req =new Request<>(Request.Command.SEATING_REQUEST, payload);
        
        sendRequest(req);
    }
    
    // helper function for requestLogin
    private String validateUsername(String u) {
        if (u.isEmpty()) return "Username is required.";
        if (u.length() < 3 || u.length() > 20) return "Username must be 3-20 characters.";
        if (u.contains(" ")) return "Username cannot contain spaces.";
        if (!u.matches("[A-Za-z0-9._-]+")) return "Username can only contain letters, numbers, . _ -";
        return null;
    }
    
    // helper function for requestLogin
    private String validatePassword(String p) {
        if (p.isEmpty()) return "Password is required.";
        if (p.length() < 6 || p.length() > 64) return "Password must be 6-64 characters.";
        //allow symbols/spaces on password
        return null;
    }
    // END
    
    public void requestNewReservation(
            ReservationRequestType type,
            LocalDate reservationDate,
            LocalTime startTime,
            int partySize,
            String userID,
            String guestContact,
            int confirmationCode
    ) {
        //client side validation
    	
        if (!connected) {
            safeUiWarning("Reservations", "Not connected to server.");
            return;
        }

        ReservationRequest payload = new ReservationRequest(
                type,
                reservationDate,
                startTime,
                partySize,
                userID,
                guestContact,
                confirmationCode
        );

        Request<ReservationRequest> req = new Request<>(Request.Command.RESERVATION_REQUEST, payload);
        sendRequest(req);
    }
    //role mapper
    private DesktopScreenController.Role mapRoleFromServer(String rawRole) {
        if (rawRole == null) {
            return DesktopScreenController.Role.GUEST;
        }

        String normalized = rawRole.trim().toUpperCase();

        // server sends exact enum names
        try {
            return DesktopScreenController.Role.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // fallback - dont crash UI if server sends unexpected role
            System.err.println("[WARN] Unknown role from server: " + rawRole);
            return DesktopScreenController.Role.GUEST;
        }
    }

    public void handleConnectionLost(String message) {
        if (ui != null) {
            ui.showError("Connection Lost", message);
        }
    }
    
    // safe UI calls

    private void safeUiInfo(String title, String message) {
        if (ui != null) ui.showInfo(title, message);
        else System.out.println("[INFO] " + title + ": " + message);
    }

    private void safeUiWarning(String title, String message) {
        if (ui != null) ui.showWarning(title, message);
        else System.out.println("[WARN] " + title + ": " + message);
    }

    private void safeUiError(String title, String message) {
        if (ui != null) ui.showError(title, message);
        else System.err.println("[ERROR] " + title + ": " + message);
    }
 
    private void uiPayload(Object payload) {
        if (ui != null) ui.showPayload(payload);
        else System.out.println("[PAYLOAD] " + payload);
    } 
    
    // Setters
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    // session identity API (guest)
    public void startGuestSession(String contact) {
        this.guestSession = true;
        this.guestContact = contact;
        this.currentUsername = "guest";
        this.currentUserId = null;

    }
    

    public boolean isGuestSession() {
        return guestSession;
    }
    
    //Getters
    public String getCurrentUserId() {
        return currentUserId;
    }
    
    public String getGuestContact() {
        return guestContact;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }
    
    public String getCurrentEmail() {
        return currentEmail;
    }

    public String getCurrentPhone() {
        return currentPhone;
    }

}
