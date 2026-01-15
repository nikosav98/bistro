package controllers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

import client.BistroEchoClient;
import desktop_screen.DesktopScreenController;
import kryo.KryoUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
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
import responses.WaitingListResponse;

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
    private ClientUIHandler ui = new NullClientUIHandler();
    private boolean connected;
    private final SessionState session = new SessionState();
    
    private java.util.function.Consumer<Integer> lostCodeListener;
    private final Map<Class<?>, Consumer<Object>> responseHandlers = new HashMap<>();


    
    private UserCommand lastUserCommand;

    
    
    public ClientController(BistroEchoClient client) {
        this.client = client;
        registerResponseHandlers();
    }

    /** Called once during UI startup */
    public void setUIHandler(ClientUIHandler ui) {
    	 this.ui = ui == null ? new NullClientUIHandler() : ui;
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


    /**
     *  Handles a raw response from the server, decoding and routing by payload type.
     *  Called only by {@code BistroEchoClient.handleMessageFromServer(msg)}
     * @param msg
     */
    public void handleServerResponse(Object msg) {
        try {
            Object decoded = msg;
            if (msg instanceof byte[] bytes) {
                decoded = KryoUtil.deserialize(bytes);
            }

            Response<?> response = decodeResponse(decoded);
            if(response ==null) {
                return;
            }

            if (!response.isSuccess()) {
                safeUiError("Failed operation", response.getMessage());
                return;
            }

            Object responseData = response.getData();
            if (responseData == null) {
                //safeUiInfo("Empty Response", "Server returned no payload.");
                return;
            }

            // user history payload: Response<List<UserHistoryResponse>>
            if (responseData instanceof java.util.List<?> list ) {
            	handleListResponse(list);
                return;
            }

            Consumer<Object> handler = responseHandlers.get(responseData.getClass());
            if (handler != null) {
                handler.accept(responseData);
                return;
            }
            
            safeUiInfo("Unhandled Response", "No handler for " + responseData.getClass().getSimpleName());
        } catch (Exception e) {
            safeUiError("Client Error", "Error handling server response:\n" + e.getMessage());
            e.printStackTrace();
        }
    }
            
            
    
    private void registerResponseHandlers() {
        responseHandlers.put(LoginResponse.class, payload -> handleLoginResponse((LoginResponse) payload));
        responseHandlers.put(ReservationResponse.class, payload -> handleReservationResponse((ReservationResponse) payload));
        responseHandlers.put(SeatingResponse.class, payload -> handleSeatingResponse((SeatingResponse) payload));
        responseHandlers.put(ReportResponse.class, payload -> handleReportResponse((ReportResponse) payload));
        responseHandlers.put(ManagerResponse.class, payload -> handleManagerResponse((ManagerResponse) payload));
        responseHandlers.put(BillResponse.class, payload -> handleBillResponse((BillResponse) payload));
        responseHandlers.put(Integer.class, payload -> handleLostCode((Integer) payload));
        responseHandlers.put(WaitingListResponse.class, payload -> handleWaitingListResponse((WaitingListResponse) payload));
    }

    private Response<?> decodeResponse(Object decoded) {
        if (!(decoded instanceof Response<?> response)) {
            safeUiInfo("Failed", "failed to deserialize response");
            return null;
        }
        return response;
    }
    
    private void handleWaitingListResponse(WaitingListResponse waitingListResponse) {
    	ui.onWaitingListCancellation(waitingListResponse);
    }

    private void handleListResponse(java.util.List<?> list) {
        if (list.isEmpty()) {
            if (lastUserCommand == UserCommand.UPCOMING_RESERVATIONS_REQUEST) {
                ui.onUpcomingReservationsResponse(java.util.List.of());
                return;
            }
            
            if (lastUserCommand == UserCommand.HISTORY_REQUEST) {
                ui.onUserHistoryResponse(java.util.List.of());
                return;
            }
            return;
        }
        
        Object first = list.get(0);
        if (first instanceof ReservationResponse) {
            @SuppressWarnings("unchecked")
            java.util.List<ReservationResponse> rows = (java.util.List<ReservationResponse>) list;
            ui.onUpcomingReservationsResponse(rows);
            
        } else if (first instanceof UserHistoryResponse) {
            @SuppressWarnings("unchecked")
            java.util.List<UserHistoryResponse> rows = (java.util.List<UserHistoryResponse>) list;
            ui.onUserHistoryResponse(rows);
            
        } else {
            safeUiInfo("Unhandled Response", "Unhandled list payload");
        }
    }
    
    
    private void handleLoginResponse(LoginResponse loginResponse) {
        switch (loginResponse.getResponseCommand()) {
            case LOGIN_RESPONSE -> {
                DesktopScreenController.Role uiRole =
                        mapRoleFromServer(loginResponse.getRole());

                session.setGuestSession(false);
                session.setGuestContact(null);
                session.setCurrentUserId(loginResponse.getUserID());
                session.setCurrentUsername(loginResponse.getUsername());
                session.setCurrentEmail(loginResponse.getEmail());
                session.setCurrentPhone(loginResponse.getPhone());

                String serverUsername = loginResponse.getUsername();
                serverUsername = serverUsername == null ? null : serverUsername.trim();

                if (serverUsername != null && !serverUsername.isEmpty())  session.setCurrentUsername(serverUsername);                   
                else session.setCurrentUsername(session.getLastLoginUsername() == null? null : session.getLastLoginUsername().trim());
                	                                                                     
                ui.routeToDesktop(uiRole, loginResponse.getUsername());
            }
            case EDIT_RESPONSE -> {
                if (loginResponse.getEmail() != null) session.setCurrentEmail(loginResponse.getEmail());
                                    
                if (loginResponse.getPhone() != null)  session.setCurrentPhone(loginResponse.getPhone());
                                   
                safeUiInfo("Subscriber Details", "Details updated successfully");
            }
            case SHOW_DETAIL_RESPONSE -> {
                session.setCurrentEmail(loginResponse.getEmail());
                session.setCurrentPhone(loginResponse.getPhone());
                ui.onUserDetailsResponse(loginResponse.getEmail(), loginResponse.getPhone());
            }
            case HISTORY_RESPONSE -> {
                java.util.List<UserHistoryResponse> rows = loginResponse.getUserHistory();
                ui.onUserHistoryResponse(rows == null ? java.util.List.of() : rows);
            }
            case UPCOMING_RESERVATIONS_RESPONSE -> {
                java.util.List<ReservationResponse> rows = loginResponse.getUpcomingReservations();
                ui.onUpcomingReservationsResponse(rows == null ? java.util.List.of() : rows);
            }
        }
    }
    
    
    private void handleReservationResponse(ReservationResponse reservationResponse) {
        if (reservationResponse.getType() == ReservationResponse.ReservationResponseType.SECOND_PHASE_CONFIRMED) {
            String messege = "Reservation confirmed successfully. "
                    + "Confirmation code: " + reservationResponse.getConfirmationCode();
            safeUiInfo("Reservation", messege);
        }
        ui.onReservationResponse(reservationResponse);
    }
    
    private void handleSeatingResponse(SeatingResponse seatingResponse) {
        if (seatingResponse.getType() == SeatingResponse.SeatingResponseType.CUSTOMER_CHECKED_IN) {
            Integer tn = seatingResponse.getTableNumberl();
            String seatingMessage = "Checked-in successfully."
                    + (tn != null ? (" Table: " + tn) : "");
            safeUiInfo("Check-in", seatingMessage);
        } else if (seatingResponse.getType() == SeatingResponse.SeatingResponseType.CUSTOMER_IN_WAITINGLIST) {
            safeUiInfo("Check-in", "No table available. You were added to the waiting list.");
        }

        ui.onSeatingResponse(seatingResponse);
    }

    private void handleReportResponse(ReportResponse reportResponse) {
        ui.onReportResponse(reportResponse);
    }

    private void handleManagerResponse(ManagerResponse managerResponse) {
        ui.onManagerResponse(managerResponse);
    }
    
    private void handleLostCode(Integer confirmationCode) {
        if (lostCodeListener != null) lostCodeListener.accept(confirmationCode);                    
    }
    
    /**
     * Requests a waiting list confirmation code lookup for a user or guest contact.
     * @param contactRaw
     */
    public void requestWaitingListLostCode(String contactRaw) {
        if (!connected) {
            safeUiWarning("Retrieve Code", "Not connected to server.");
            return;
        }

        String contact = contactRaw == null ? null : contactRaw.trim();
        if (contact == null || contact.isEmpty()) {
            safeUiWarning("Retrieve Code", "Missing contact or user ID.");
            return;
        }

        Request<String> req = new Request<>(Request.Command.LOST_CODE, "WAITLIST:" + contact);
        sendRequest(req);
    }
    
    
    
    /**
     * Requests a walk-in seating assignment based on user or guest contact.
     * @param userId
     * @param guestContact
     * @param partySize
     */
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

        ReservationRequest reservationRequest = new ReservationRequest(null,LocalDate.now(),LocalTime.now(),
                partySize,trimmedUserId.isEmpty() ? null : trimmedUserId,trimmedGuestContact.isEmpty() ? null : trimmedGuestContact,0);

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
    
    /**
     * Registers a listener to receive a lost confirmation code.
     * @param listener
     */
    public void setLostCodeListener(java.util.function.Consumer<Integer> listener) {
        this.lostCodeListener = listener;
    }
    
    /**
     * Clears the lost code listener.
     */
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
    
    /**
     * Submits a login request for a subscriber.
     * @param usernameRaw
     * @param passwordRaw
     */
    public void requestLogin(String usernameRaw, String passwordRaw) {
        String username = usernameRaw == null ? "" : usernameRaw.trim();
        String password = passwordRaw == null ? "" : passwordRaw.trim();
        session.setLastLoginUsername(username == null ? null : username.trim());

        String err = validateUsername(username);
        if (err != null) { safeUiWarning("Login", err); return; }

        err = validatePassword(password);
        if (err != null) { safeUiWarning("Login", err); return; }

        session.setGuestSession(false);
        session.setGuestContact(null);
        LoginRequest loginRequest = new LoginRequest(username,password,UserCommand.LOGIN_REQUEST);       
        Request<LoginRequest> req = new Request<LoginRequest>(Request.Command.USER_REQUEST,loginRequest);
        sendRequest(req);
    }
    
    /**
     * Requests cancellation for a waiting list reservation.
     *
     * @param confirmationCode
     */
    public void requestWaitingListCancellation(int confirmationCode) {
        if (!connected) {
            safeUiWarning("Cancel waitlist", "Not connected to server.");
            return;
        }
        if (confirmationCode <= 0) {
            safeUiWarning("Cancel waitlist", "Confirmation code is required.");
            return;
        }

        requests.WaitingListRequest payload = new requests.WaitingListRequest(confirmationCode);
        Request<requests.WaitingListRequest> req =
                new Request<>(Request.Command.WAITING_LIST_REQUEST, payload);
        sendRequest(req);
    }
    
    
    /**
     * Requests the current user's reservation history.
     */
    public void requestUserHistory() {
        if (!connected) {
            safeUiWarning("History", "Not connected to server.");
            return;
        }

        String username = session.isGuestSession() ? session.getGuestContact() : session.getCurrentUsername();
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
    
    /**
     * Requests upcoming reservations for the current user.
     */
    public void requestUpcomingReservations() {
        if (!connected) {
            safeUiWarning("Upcoming Reservations", "Not connected to server.");
            return;
        }

        String username = session.getCurrentUsername();
        username = username == null ? null : username.trim();
        if (username == null || username.isEmpty()) {
            safeUiWarning("Upcoming Reservations", "No active user session.");
            return;
        }

        LoginRequest payload = new LoginRequest(username, null, UserCommand.UPCOMING_RESERVATIONS_REQUEST);
        Request<LoginRequest> req = new Request<>(Request.Command.USER_REQUEST, payload);
        lastUserCommand = UserCommand.UPCOMING_RESERVATIONS_REQUEST;
        sendRequest(req);
    }
    
    /**
     * Requests reservation history for a specific subscriber.
     * @param usernameRaw
     */
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
    
    /**
     * Requests current subscriber detail values.
     */
    public void requestUserDetails() {
        if (!connected) {
            safeUiWarning("Subscriber Details", "Not connected to server.");
            return;
        }

        String username = session.resolveUsername();
        if (username == null || username.isEmpty()) {
            safeUiWarning("Subscriber Details", "No active user session.");
            return;
        }

        LoginRequest payload = new LoginRequest(username, null, UserCommand.SHOW_DETAILS_REQUEST);
        Request<LoginRequest> req = new Request<>(Request.Command.USER_REQUEST, payload);
        lastUserCommand = UserCommand.SHOW_DETAILS_REQUEST;
        sendRequest(req);
    }

    public void requestEditDetails(String emailRaw, String phoneRaw) {
        if (!connected) {
            safeUiWarning("Subscriber Details", "Not connected to server.");
            return;
        }

        String username = session.resolveUsername();
        if (username == null || username.isEmpty()) {
            safeUiWarning("Subscriber Details", "No active user session.");
            return;
        }

        String email = emailRaw == null ? "" : emailRaw.trim();
        String phone = phoneRaw == null ? "" : phoneRaw.trim();
        if (email.isEmpty() && phone.isEmpty()) {
            safeUiWarning("Subscriber Details", "Provide an email or phone number to update.");
            return;
        }

        LoginRequest payload = new LoginRequest(username, phone, email, UserCommand.EDIT_DETAIL_REQUEST);
        Request<LoginRequest> req = new Request<>(Request.Command.USER_REQUEST, payload);
        lastUserCommand = UserCommand.EDIT_DETAIL_REQUEST;
        sendRequest(req);
    }
    
    
    
    /**
     * Submits a manager workflow request.
     * @param request
     */
    public void requestManagerAction(ManagerRequest request) {
        if (!connected) {
            safeUiWarning("Manager", "Not connected to server.");
            return;
        }
        if (request == null) {
            safeUiWarning("Manager", "Missing manager request.");
            return;
        }

        Request<ManagerRequest> req = new Request<>(Request.Command.MANAGER_REQUEST, request);               
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
    
    /**
     * Logs the user out and clears session state.
     */
    public void logout() {
    	 if (connected && client != null) {
             Request<Void> req = new Request<>(Request.Command.LOGOUT_REQUEST, null);
             sendRequest(req);
         }
    	
    	 session.reset();
         ui.showInfo("Logout", "You have been logged out.");
         
    }
    
    public void closeConnectionForExit() {
    	 if (client != null) {
             try {
				client.closeConnection();
			} catch (IOException e) {
				 safeUiError("Exit", "Error while closing connection:\n" + e.getMessage());				
			} finally {
				connected = false;
			}
         }
    }


    public void requestSeatingCheckInByConfirmationCode(int confirmationCode) {
        if (!connected) {
            safeUiWarning("Check-in", "Not connected to server.");
            return;
        }

        SeatingRequest payload = new SeatingRequest(SeatingRequestType.BY_CONFIRMATIONCODE,confirmationCode, null);

        Request<SeatingRequest> req = new Request<>(Request.Command.SEATING_REQUEST, payload);

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
   
    /**
     * Requests creation, edit, cancel, or query of a reservation.
     * @param type
     * @param reservationDate
     * @param startTime
     * @param partySize
     * @param userID
     * @param guestContact
     * @param confirmationCode
     */
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
        if ("REPRESENTATIVE".equals(normalized)) {
            normalized = "REP";
        }


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
    	 ui.showInfo(title, message);
    }

    private void safeUiWarning(String title, String message) {
    	 ui.showWarning(title, message);
    }

    private void safeUiError(String title, String message) {
    	 ui.showError(title, message);
    }
 
    private void uiPayload(Object payload) {
    	ui.showPayload(payload);
    } 
    
    // Setters
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    /**
     * Starts a guest session and stores the guest contact.
     * @param contact
     */
    public void startGuestSession(String contact) {
    	 session.setGuestSession(true);
         session.setGuestContact(contact);
         session.setCurrentUsername("guest");
         session.setCurrentUserId(null);

    }
    

    public boolean isGuestSession() {
    	 return session.isGuestSession();
    }
    
    //Getters
    public String getCurrentUserId() {
    	return session.getCurrentUserId();
    }
    
    public String getGuestContact() {
    	 return session.getGuestContact();
    }

    public String getCurrentUsername() {
    	return session.getCurrentUsername();
    }
    
    public String getCurrentEmail() {
    	return session.getCurrentEmail();
    }

    public String getCurrentPhone() {
    	return session.getCurrentPhone();
    }

}
