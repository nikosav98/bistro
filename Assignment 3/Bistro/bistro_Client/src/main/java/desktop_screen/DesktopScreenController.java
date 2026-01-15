package desktop_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import controllers.ClientUIHandler;
import desktop_views.EditReservationViewController;
import desktop_views.HistoryViewController;
import desktop_views.PayViewController;
import desktop_views.ReservationsViewController;
import desktop_views.TablesViewController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import manager_screen.AddSubscriberScreenController;
import manager_screen.EditTableScreenController;
import manager_screen.ManagerReservationsScreenController;
import manager_screen.ShowDataScreenController;
import manager_screen.UpdateOpeningHoursScreenController;
import manager_screen.SubscribersScreenController;
import manager_screen.ManagerReservationStartScreenController;
import subscriber_screen.*;
import responses.ManagerResponse;
import responses.ReservationResponse;
import responses.SeatingResponse;
import responses.UserHistoryResponse;
import responses.WaitingListResponse;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import desktop_views.ReportsViewController;
import responses.ReportResponse;
/**
 *Desktop shell controller (top bar + left navigation + center content host).
 *Handles role-based navigation visibility, single selection across navigation buttons,
 * and content swapping into the content host
 */
public class DesktopScreenController implements ClientUIHandler {

    public enum Role {
        GUEST,
        SUBSCRIBER,
        REP,
        MANAGER
    }

    @FXML private StackPane contentHost;

    // Role groups (just layout containers)
    @FXML private VBox repGroup;
    @FXML private VBox subscriberGroup;
    @FXML private VBox managerGroup;

    // Top bar
    @FXML private Label welcomeNameLabel;

    // Nav buttons
    @FXML private ToggleButton reservationsBtn;   // "New Reservations"
    @FXML private ToggleButton waitlistBtn;
    @FXML private ToggleButton tablesBtn;

    @FXML private ToggleButton historyBtn;
    @FXML private ToggleButton editReservationBtn;
    @FXML private ToggleButton subscriberHomeBtn;

    @FXML private ToggleButton reportsBtn;
    @FXML private ToggleButton analyticsBtn;
    @FXML private ToggleButton payBtn;

    @FXML private ToggleButton editTablesBtn;
    @FXML private ToggleButton openingHoursBtn;
    @FXML private ToggleButton managerReservationsBtn;
    @FXML private ToggleButton managerDataBtn;
    @FXML private ToggleButton addSubscriberBtn;
    @FXML private ToggleButton viewSubscribersBtn;

    private boolean connected;

    private final ToggleGroup navToggleGroup = new ToggleGroup();
    private final Map<ToggleButton, ScreenKey> navMap = new HashMap<>();

    private ClientController clientController;

    // cached controllers for response routing
    private ReservationsViewController reservationsVC;
    private EditReservationViewController editReservationVC;
    private HistoryViewController historyVC;
    private PayViewController payVC;

    private TablesViewController tablesVC;
    private EditTableScreenController editTableVC;
    private UpdateOpeningHoursScreenController openingHoursVC;
    private ManagerReservationsScreenController managerReservationsVC;
    private ShowDataScreenController showDataVC;
    private AddSubscriberScreenController addSubscriberVC;
    private SubscribersScreenController subscribersVC;
    private SubscriberMainScreenController subscriberMainVC;
    private ReportsViewController reportsVC;
    private Role role = Role.GUEST;
    private Runnable onLogout;

    // Screens
    private enum ScreenKey {
    	SUBSCRIBER_HOME,
        RESERVATIONS,
        WAITLIST,
        TABLES,
        HISTORY,
        EDIT_DETAILS,
        ADD_SUBSCRIBER,
        SUBSCRIBERS,
        REPORTS,
        ANALYTICS,
        PAY,
        EDIT_TABLES,
        MANAGER_RESERVATIONS,
        OPENING_HOURS,
        MANAGER_DATA
    }

    // Role == allowed screens
    private static final Map<Role, Set<ScreenKey>> ROLE_SCREENS = new EnumMap<>(Role.class);
    static {
        // GUEST -> edit details, new reservations, pay
        ROLE_SCREENS.put(Role.GUEST, EnumSet.of(
                ScreenKey.EDIT_DETAILS,
                ScreenKey.RESERVATIONS,
                ScreenKey.PAY
        ));

        // SUBSCRIBER -> edit details, new reservations, pay, history
        ROLE_SCREENS.put(Role.SUBSCRIBER, EnumSet.of(
        		 ScreenKey.SUBSCRIBER_HOME,
                ScreenKey.EDIT_DETAILS,
                ScreenKey.RESERVATIONS,
                ScreenKey.PAY,
                ScreenKey.HISTORY
        ));

        // REP -> edit details, new reservations, pay, history, tables, waitlist
        ROLE_SCREENS.put(Role.REP, EnumSet.of(
                ScreenKey.EDIT_DETAILS,
                ScreenKey.RESERVATIONS,                                              
                ScreenKey.EDIT_TABLES,
                ScreenKey.MANAGER_DATA,
                ScreenKey.MANAGER_RESERVATIONS,
                ScreenKey.ADD_SUBSCRIBER,
                ScreenKey.SUBSCRIBERS,
                ScreenKey.OPENING_HOURS,
                ScreenKey.PAY
                
        ));

        // MANAGER -> manager screens
        ROLE_SCREENS.put(Role.MANAGER, EnumSet.of(
                ScreenKey.EDIT_DETAILS,
                ScreenKey.RESERVATIONS,                
                ScreenKey.ADD_SUBSCRIBER,
                ScreenKey.SUBSCRIBERS,                
                ScreenKey.EDIT_TABLES,
                ScreenKey.MANAGER_DATA,                
                ScreenKey.MANAGER_RESERVATIONS,
                ScreenKey.REPORTS,
                ScreenKey.OPENING_HOURS,
                ScreenKey.PAY
                
        ));
    }

    // Public API
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }
    
    /**
     * Sets the current user role for this desktop shell.
     * @param role the new role; if {@code null}, defaults to {@link Role#GUEST}.
     */
    public void setRole(Role role) {
        this.role = role != null ? role : Role.GUEST;
        applyRoleVisibility();
        navToggleGroup.selectToggle(null); // clear selection
        selectDefaultForRole(); // pick default screen for this role
        
        if (this.role == Role.SUBSCRIBER && clientController != null)  clientController.requestUpcomingReservations();                   
        if (historyVC != null) historyVC.setUserContext(this.role, buildDiscountInfo());
        if (addSubscriberVC != null) addSubscriberVC.setRequesterRole(this.role);
    }

    public void setWelcomeName(String name) {
        if (welcomeNameLabel != null) {
            String trimmed = name == null ? "" : name.trim();
            
            if (role == Role.GUEST || trimmed.equalsIgnoreCase("guest"))  welcomeNameLabel.setText("Hello, guest.");               
            else if (trimmed.isEmpty())  welcomeNameLabel.setText("Hello.");               
            else welcomeNameLabel.setText("Hello, " + trimmed + ".");                            
        }
    }
    
    /**
     * Sets a logout callback for returning to the login screen or parent shell.
     * @param onLogout
     */
    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

   
    @FXML
    /**
     * Initializes navigation button mappings and listeners after FXML injection.
     */
    private void initialize() {
        registerNavButton(reservationsBtn, ScreenKey.RESERVATIONS);
        registerNavButton(waitlistBtn,     ScreenKey.WAITLIST);
        registerNavButton(tablesBtn,       ScreenKey.TABLES);
        registerNavButton(historyBtn,      ScreenKey.HISTORY);
        registerNavButton(editReservationBtn,  ScreenKey.EDIT_DETAILS);
        registerNavButton(subscriberHomeBtn, ScreenKey.SUBSCRIBER_HOME);
        registerNavButton(reportsBtn,      ScreenKey.REPORTS);
        registerNavButton(analyticsBtn,    ScreenKey.ANALYTICS);
        registerNavButton(payBtn,          ScreenKey.PAY);
        registerNavButton(editTablesBtn,   ScreenKey.EDIT_TABLES);
        registerNavButton(openingHoursBtn, ScreenKey.OPENING_HOURS);
        registerNavButton(managerReservationsBtn, ScreenKey.MANAGER_RESERVATIONS);
        registerNavButton(managerDataBtn,  ScreenKey.MANAGER_DATA);
        registerNavButton(addSubscriberBtn, ScreenKey.ADD_SUBSCRIBER);
        registerNavButton(viewSubscribersBtn, ScreenKey.SUBSCRIBERS);


        applyRoleVisibility();

        navToggleGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            // Allow no selection so welcome screen can remain visible.
            if (newT instanceof ToggleButton btn) {
                ScreenKey key = navMap.get(btn);
                if (key != null) navigate(key);
            }
        });
    }
    
    /**
     * Registers a navigation toggle button and maps it to a {@link ScreenKey}.
     * @param btn
     * @param key
     */
    private void registerNavButton(ToggleButton btn, ScreenKey key) {
        if (btn == null) return;
        btn.setToggleGroup(navToggleGroup);
        navMap.put(btn, key);
    }

    /**
     * Applies role-based visibility rules to all registered navigation buttons.
     */
    private void applyRoleVisibility() {
        Set<ScreenKey> allowed = ROLE_SCREENS.getOrDefault(role, Set.of());

        // Show/hide each button individually
        for (Map.Entry<ToggleButton, ScreenKey> entry : navMap.entrySet()) {
            ToggleButton btn = entry.getKey();
            ScreenKey key = entry.getValue();
            if (btn == null) continue;

            boolean visible = allowed.contains(key);
            btn.setVisible(visible);
            btn.setManaged(visible);
        }

        // Update groups visibility based on whether they have any visible children
        updateGroupVisibility(repGroup);
        updateGroupVisibility(subscriberGroup);
        updateGroupVisibility(managerGroup);
    }

    private void updateGroupVisibility(VBox group) {
        if (group == null) return;

        boolean hasVisibleChild = group.getChildren().stream()
                .filter(n -> n instanceof ToggleButton)
                .map(n -> (ToggleButton) n)
                .anyMatch(btn -> btn.isVisible() && btn.isManaged());

        group.setVisible(hasVisibleChild);
        group.setManaged(hasVisibleChild);
    }

    /**
     * choosing which button each role will see
     */
    private void selectDefaultForRole() {
        switch (role) {
            case MANAGER -> selectIfVisible(reportsBtn);       // manager default
            case REP     -> selectIfVisible(reservationsBtn);  // rep default
            case SUBSCRIBER -> selectIfVisible(subscriberHomeBtn);    // subscriber default
            case GUEST   -> selectIfVisible(reservationsBtn);  // guest default
            default      -> selectFirstAvailable();
        }
    }

    private void selectFirstAvailable() {
        for (Map.Entry<ToggleButton, ScreenKey> entry : navMap.entrySet()) {
            ToggleButton btn = entry.getKey();
            if (btn != null && btn.isVisible() && btn.isManaged() && !btn.isDisable()) {
                btn.setSelected(true);
                navigate(entry.getValue());
                return;
            }
        }
        if (contentHost != null) contentHost.getChildren().clear();
    }

    private void selectIfVisible(ToggleButton btn) {
        if (btn != null && btn.isVisible() && btn.isManaged() && !btn.isDisable()) {
            btn.setSelected(true);
            navigate(navMap.get(btn));
        } else {
            selectFirstAvailable();
        }
    }

    // 
    @FXML
    /**
     * Handles navigation button click events from the sidebar.
     * @param event
     */
    private void onNavClicked(javafx.event.ActionEvent event) {
        if (!(event.getSource() instanceof ToggleButton btn)) return;
        ScreenKey key = navMap.get(btn);
        if (key != null) {
            btn.setSelected(true);
            navigate(key);
        }
    }

    @FXML
    /**
     *  Handles logout click events and routes back to the login flow.
     */
    private void onLogoutClicked() {
       
        if (clientController != null) clientController.logout();                            
        if (onLogout != null)  onLogout.run();           
        else {            
            setRole(Role.GUEST);
            setWelcomeName("");
            if (contentHost != null) contentHost.getChildren().clear();
        }
    }

    /**
     * Navigates to a specific screen by swapping the content host.
     * @param key
     */
    private void navigate(ScreenKey key) {
        switch (key) {
        	case SUBSCRIBER_HOME ->loadIntoContentHost("/subscriber_screen/SubscriberMainScreen.fxml");        			
            case RESERVATIONS ->loadIntoContentHost("/desktop_views/ReservationsView.fxml");                    
            case WAITLIST ->loadIntoContentHost("/desktop_views/WaitlistView.fxml");                    
            case TABLES ->loadIntoContentHost("/desktop_views/TablesView.fxml");                    
            case EDIT_TABLES ->  loadIntoContentHost("/manager_screen/EditTableScreen.fxml");                  
            case MANAGER_DATA ->loadIntoContentHost("/manager_screen/ShowDataScreen.fxml");                    
            case ADD_SUBSCRIBER -> loadIntoContentHost("/manager_screen/AddSubscriberScreen.fxml");                   
            case SUBSCRIBERS ->loadIntoContentHost("/manager_screen/SubscribersScreen.fxml");            		
            case HISTORY ->loadIntoContentHost("/desktop_views/HistoryView.fxml");
            case EDIT_DETAILS ->loadIntoContentHost("/desktop_views/EditReservationView.fxml");
            case REPORTS ->loadIntoContentHost("/desktop_views/ReportsView.fxml");
            case ANALYTICS ->loadIntoContentHost("/desktop_views/AnalyticsView.fxml");
            case OPENING_HOURS ->loadIntoContentHost("/manager_screen/UpdateOpeningHoursScreen.fxml");
            case MANAGER_RESERVATIONS ->loadIntoContentHost("/manager_screen/ManagerReservationsScreen.fxml");
            case PAY ->loadIntoContentHost("/desktop_views/PayView.fxml");
        }
    }
    
    /**
     * Loads an FXML view into the content host and wires its controller.
     * @param fxmlPath
     */
    private void loadIntoContentHost(String fxmlPath) {
        try {
        	//incase of manager reserve for customer clear reset identity back to manager 
        	if (!"/desktop_views/ReservationsView.fxml".equals(fxmlPath) && reservationsVC != null) 
                reservationsVC.clearReservationIdentity();
            
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();

            Object ctrl = loader.getController();

            // cache known controllers so we can route responses later
            if (ctrl instanceof ReservationsViewController rvc) reservationsVC = rvc;
            if (ctrl instanceof EditReservationViewController edvc) editReservationVC = edvc;
            if (ctrl instanceof HistoryViewController hvc) historyVC = hvc;
            if (ctrl instanceof ReportsViewController rvc) reportsVC = rvc;
            if (ctrl instanceof HistoryViewController hvc) {
                historyVC = hvc;
                historyVC.setUserContext(role, buildDiscountInfo());
            }
            if (ctrl instanceof TablesViewController tvc) tablesVC = tvc;
            if (ctrl instanceof EditTableScreenController etc) editTableVC = etc;
            if (ctrl instanceof UpdateOpeningHoursScreenController uohc) openingHoursVC = uohc;
            if (ctrl instanceof ManagerReservationsScreenController mrc) {
                managerReservationsVC = mrc;
                managerReservationsVC.setOnStartReservationFlow(this::openManagerReservationStart);
            }
            if (ctrl instanceof ManagerReservationStartScreenController startCtrl) {
                startCtrl.setOnContinue(this::openReservationForIdentity);
                startCtrl.setOnCancel(this::openManagerReservations);
            }
            if (ctrl instanceof ShowDataScreenController sdc) showDataVC = sdc;
            if (ctrl instanceof AddSubscriberScreenController asc) {
                addSubscriberVC = asc;
                addSubscriberVC.setRequesterRole(role);
            }
            if (ctrl instanceof SubscribersScreenController ssc) subscribersVC = ssc;
            if (ctrl instanceof SubscriberMainScreenController smc) {
                subscriberMainVC = smc;
                subscriberMainVC.setOnEditReservation(this::openEditReservation);
                subscriberMainVC.setOnPayReservation(this::openPayReservation);
            }
            if (ctrl instanceof PayViewController pvc) payVC = pvc;

            // inject controller reference into screens that need it
            if (ctrl instanceof ClientControllerAware aware) aware.setClientController(clientController, connected);
                            
            // manager screens can optionally load initial data when opened
            if (ctrl instanceof EditTableScreenController etc) etc.requestInitialData();
                            
            if (ctrl instanceof ShowDataScreenController sdc) sdc.requestInitialData();
                            
            if (contentHost != null) contentHost.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
            Label error = new Label("Failed to load screen:\n" + fxmlPath + "\n" + e.getMessage());
            error.getStyleClass().add("muted");
            if (contentHost != null) contentHost.getChildren().setAll(error);
        }
    }
    @Override
    public void onReportResponse(ReportResponse reportResponse) {
        javafx.application.Platform.runLater(() -> {
            if (reportsVC != null) {
                reportsVC.onReportResponse(reportResponse);
                return;
            }
            showInfo("Reports", "Report received. Open Reports screen to view it.");
        });
    }

    
    @Override
    public void onReservationResponse(ReservationResponse response) {
        javafx.application.Platform.runLater(() -> {
            if (reservationsVC != null) reservationsVC.handleServerResponse(response);                                           
            if (editReservationVC != null) editReservationVC.onReservationResponse(response);                                            
        });
    }

    @Override
    public void onSeatingResponse(SeatingResponse response) {
        // desktop currently does not handle seating responses
    }
    
    
    private HistoryViewController.DiscountInfo buildDiscountInfo() {
        if (role == Role.SUBSCRIBER) return HistoryViewController.DiscountInfo.active("10% subscriber discount", 0.10);                    
        return HistoryViewController.DiscountInfo.none();
    }

    @Override
    public void onUserHistoryResponse(java.util.List<responses.UserHistoryResponse> rows) {
        javafx.application.Platform.runLater(() -> {
            if (historyVC != null) historyVC.renderHistory(rows);                            
            if (subscribersVC != null)  subscribersVC.renderHistory(rows);                          
        });
    }

    @Override
    public void onUserHistoryError(String message) {
    	
        javafx.application.Platform.runLater(() -> {
        	 boolean handled = false;
            if (historyVC != null) {
                historyVC.showHistoryError(message);
                handled = true;
            }
            if (subscribersVC != null) {
                subscribersVC.showHistoryError(message);
                handled = true;
            }
            if(!handled) showError("History", message);            	           
        });
    }
    
    @Override
    public void onUpcomingReservationsResponse(java.util.List<ReservationResponse> rows) {
    	javafx.application.Platform.runLater(() -> {
            if (subscriberMainVC != null) subscriberMainVC.onUpcomingReservationsResponse(rows);                            
        });
    }

    @Override
    public void onUpcomingReservationsError(String message) {
    	javafx.application.Platform.runLater(() -> {
            if (subscriberMainVC != null) subscriberMainVC.onUpcomingReservationsError(message);                           
        });
    }
    
    @Override
    public void onUserDetailsResponse(String email, String phone) {
        javafx.application.Platform.runLater(() -> {
            if (subscriberMainVC != null) subscriberMainVC.onUserDetailsResponse(email, phone);                
            else showInfo("Subscriber Details", "Email: " + email + "\nPhone: " + phone);                            
        });
    }
    
    /**
     * Routes manager responses to whichever manager view is currently loaded.
     */
    @Override
    public void onManagerResponse(ManagerResponse response) {
        javafx.application.Platform.runLater(() -> {
            if (tablesVC != null) tablesVC.handleManagerResponse(response);                            
            if (editTableVC != null) editTableVC.handleManagerResponse(response);                            
            if (openingHoursVC != null) openingHoursVC.handleManagerResponse(response);                           
            if (managerReservationsVC != null) managerReservationsVC.handleManagerResponse(response);                            
            if (showDataVC != null) showDataVC.handleManagerResponse(response);                           
            if (subscribersVC != null) subscribersVC.handleManagerResponse(response);                         
            if (addSubscriberVC != null) addSubscriberVC.handleManagerResponse(response);
                
            
        });
    }
    
    /**
     * Displays waiting list cancellation results.
     */
    @Override
    public void onWaitingListCancellation(WaitingListResponse response) {
        javafx.application.Platform.runLater(() -> {
            boolean cancelled = response != null && response.getHasBeenCancelled();
            if (cancelled) {
                showInfo("Waiting List", "Waiting list entry cancelled.");
            } else {
                showWarning("Waiting List", "Unable to cancel waiting list entry.");
            }
        });
    }

   /**
    * Displays bill totals and applies subscriber discounts in the pay view.
     *
    */
    @Override
    public void onBillTotal(double baseTotal, boolean isCash) {
    	javafx.application.Platform.runLater(() -> {
            if (payVC != null) {
                boolean isSubscriber = role == Role.SUBSCRIBER;
                payVC.onBillTotalLoaded(baseTotal, isCash, isSubscriber);
                return;
            }
            showInfo("Billing", "Bill total received: " + String.format("%.2f", baseTotal));
        });
    }
    private void openManagerReservations() {
        selectIfVisible(managerReservationsBtn);
    }

    private void openManagerReservationStart() {
        loadIntoContentHost("/manager_screen/ManagerReservationStartScreen.fxml");
    }

    private void openReservationForIdentity(String userId, String guestContact) {
        selectIfVisible(reservationsBtn);
        if (reservationsVC != null) {
            reservationsVC.setReservationIdentity(userId, guestContact);
        }
    }
    
    /**
     * Displays bill-paid confirmation in the pay view or via alert fallback.
     */
    @Override
    public void onBillPaid(Integer tableNumber) {
    	javafx.application.Platform.runLater(() -> {
            if (payVC != null) {
                payVC.onBillPaid(tableNumber);
                return;
            }
            showInfo("Billing", "Payment completed.");
        });
    }

    @Override
    public void onBillError(String message) {
    	javafx.application.Platform.runLater(() -> {
            if (payVC != null) {
                payVC.onBillingError(message);
                return;
            }
            showError("Billing", message == null ? "Billing failed." : message);
        });
    }
    private void openPayReservation(ReservationResponse reservation) {
        if (reservation == null) return;
        Integer code = reservation.getConfirmationCode();
        if (code == null || code <= 0) {
            showWarning("Billing", "Missing confirmation code.");
            return;
        }
        selectIfVisible(payBtn);
        if (payVC != null) {
            payVC.loadBillForConfirmationCode(code);
        }
    }

    @Override
    public void showInfo(String title, String message) {
        showAlert(title, message, Alert.AlertType.INFORMATION);
    }

    @Override
    public void showWarning(String title, String message) {
        showAlert(title, message, Alert.AlertType.WARNING);
    }

    @Override
    public void showError(String title, String message) {
        showAlert(title, message, Alert.AlertType.ERROR);
    }

    @Override
    public void showPayload(Object payload) {
        showInfo("Server Message", String.valueOf(payload));
    }

    @Override
    public void routeToDesktop(Role role, String username) {
        // already in desktop
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        javafx.application.Platform.runLater(() -> {
            Alert a = new Alert(type);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(msg == null ? "" : msg);
            a.showAndWait();
        });
    }
    private void openEditReservation(ReservationResponse reservation) {
        if (reservation == null) return;
        Integer code = reservation.getConfirmationCode();
        if (code == null || code <= 0) {
            showWarning("Reservation", "Missing confirmation code.");
            return;
        }
        selectIfVisible(editReservationBtn);
        if (editReservationVC != null)  editReservationVC.loadReservationByCode(code);                  
    }
}
