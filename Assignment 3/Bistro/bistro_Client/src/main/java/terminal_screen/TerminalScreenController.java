package terminal_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.EnumMap;
import java.util.Map;
/**
 * Terminal shell controller for walk-in
 * Manages toolbar navigation, view loading, and routing of server responses
 * to the currently active terminal view.
 */
public class TerminalScreenController {

    @FXML private StackPane contentHolder;

    // Toolbar buttons
    @FXML private Button checkInBtn;
    @FXML private Button joinWaitlistBtn;
    @FXML private Button lostCodeBtn;
    @FXML private Button payBillBtn;
    @FXML private Button cancelWaitlistBtn;

    private ClientController clientController;
    private boolean connected;

    // Back to main callback (set by the ui navigator)
    private Runnable onBackToMain;

    private enum View {
    	CHECK_IN, WAITING_LIST, CANCEL_WAITLIST, PAY_BILL, LOST_CODE
    }

    private final Map<View, Parent> cache = new EnumMap<>(View.class);
    private final Map<View, Object> controllerCache = new EnumMap<>(View.class);
    private Object currentContentController;
    private View currentView;

    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;

        // FXML fields might already be injected at this point
        applyConnectionState();
    }
    
    /**
     * Sets a callback that returns the UI to the main screen.
     * @param onBackToMain
     */
    public void setOnBackToMain(Runnable onBackToMain) {
        this.onBackToMain = onBackToMain;
    }

    @FXML
    private void initialize() {
        // At initialize time, connected is whatever default we have (false until setClientController)
        applyConnectionState();
    }

    // Toolbar actions
    @FXML private void onBackToMain()      { if (onBackToMain != null) onBackToMain.run(); }//Returns to the main screen.
    @FXML private void onCheckIn()         { show(View.CHECK_IN); }//Shows the check-in flow.
    @FXML private void onJoinWaitingList() { show(View.WAITING_LIST); }//Shows the waiting list join flow.
    @FXML private void onCancelWaitlist()  { show(View.CANCEL_WAITLIST); }//Shows the waiting list cancellation flow.
    @FXML private void onPayBill()         { show(View.PAY_BILL); }//Shows the pay bill flow.
    @FXML private void onLostCode()        { show(View.LOST_CODE); }//Shows the lost code flow

    /**
     * Enables/disables toolbar actions based on connection state and loads a default view.
     */
    private void applyConnectionState() {
        boolean online = connected && clientController != null;

        // Guard against early calls before FXML injection
        if (checkInBtn != null) {
            checkInBtn.setDisable(!online);
        }
        if (joinWaitlistBtn != null) {
            joinWaitlistBtn.setDisable(!online);
        }
        if (lostCodeBtn != null) {
            lostCodeBtn.setDisable(!online);
        }
        if (payBillBtn != null) {
            payBillBtn.setDisable(!online);
        }

        if (contentHolder == null) {
            return;
        }
        if (cancelWaitlistBtn != null) {
            cancelWaitlistBtn.setDisable(!online);
        }

        if (!online) {
            // Show offline message in the content area
            Label offlineLabel = new Label("""
                    Terminal is offline.

                    The server / database is currently not available.
                    Please contact staff or try again in a few moments.
                    """);
            offlineLabel.getStyleClass().add("muted"); // optional: you already have this in your CSS
            contentHolder.getChildren().setAll(offlineLabel);

            currentView = null;
            currentContentController = null;
        } else {
            // When we become online, show default view
            if (currentView == null) {
                show(View.CHECK_IN);
            } else {
                // keep current view, but re-inject controller with updated connection state
                reinjectActiveController();
            }
        }
    }
    
    /**
     *  Routes waiting list cancellation responses to the active view.
     * @param response
     */
    public void onWaitingListCancellation(responses.WaitingListResponse response) {
        javafx.application.Platform.runLater(() -> {
            if (currentContentController instanceof TerminalCancelWaitingListController cancelCtrl) {
                cancelCtrl.handleWaitingListResponse(response);
            }
        });
    }

    /**
     * Routes seating responses to the active view and returns to the main screen.
     * @param response
     */
    public void onSeatingResponse(responses.SeatingResponse response) {
        javafx.application.Platform.runLater(() -> {
            // Route server responses only to the currently displayed view
            if (currentContentController instanceof terminal_screen.TerminalCheckInController checkInCtrl) {
                checkInCtrl.handleSeatingResponse(response);
            }else if (currentContentController instanceof terminal_screen.TerminalWaitingListController waitingListController) {
                waitingListController.handleSeatingResponse(response);
            }
            returnToMain();
            
        });
    }
    
    /**
     * Routes bill totals to the pay bill view.
     * @param baseTotal
     */
    public void onBillTotal(double baseTotal) {
        javafx.application.Platform.runLater(() -> {
            if (currentContentController instanceof TerminalPayBillController payBillCtrl) {
                payBillCtrl.onBillTotalLoaded(baseTotal);
            }
        });
    }
    
    /**
     * Routes payment success and returns to the main screen.
     */
    public void onBillPaid() {
        javafx.application.Platform.runLater(() -> {
            if (currentContentController instanceof TerminalPayBillController payBillCtrl) {
                payBillCtrl.onBillPaid();
            }
            returnToMain();
        });
    }
    
    /**
     * Routes billing errors to the pay bill view.
     * @param message
     */
    public void onBillError(String message) {
        javafx.application.Platform.runLater(() -> {
            if (currentContentController instanceof TerminalPayBillController payBillCtrl) {
                payBillCtrl.onBillingError(message);
            }
        });
    }
    
    /**
     * Shows a specific terminal view, loading it if needed.
     * @param view
     */
    private void show(View view) {
        if (!isOnline()) {
            // safety - ignore navigation if offline
            return;
        }

        if (view == currentView && contentHolder != null && !contentHolder.getChildren().isEmpty()) {
            return;
        }

        Parent root = cache.computeIfAbsent(view, this::loadView);

        // keep current controller aligned with the shown view
        currentContentController = controllerCache.get(view);
        currentView = view;

        if (contentHolder != null) {
            contentHolder.getChildren().setAll(root);
        }
    }

    private boolean isOnline() {
        return connected && clientController != null;
    }
    
    private void returnToMain() {
        if (onBackToMain != null) {
            onBackToMain.run();
        }
    }
    
    /**
     * Re-injects the client controller into the current view after reconnect.
     */
    private void reinjectActiveController() {
        if (!isOnline()) return;
        if (currentView == null) return;

        Object ctrl = controllerCache.get(currentView);
        if (ctrl instanceof ClientControllerAware aware) {
            aware.setClientController(clientController, connected);
        }
    }

    /**
     * Loads an FXML view and caches its controller for response routing.
     * @param view
     * @return
     */
    private Parent loadView(View view) {
        try {
            String fxml = switch (view) {
                case CHECK_IN -> "/terminal_screen/TerminalCheckInView.fxml";
                case WAITING_LIST -> "/terminal_screen/TerminalWaitingListView.fxml";
                case CANCEL_WAITLIST -> "/terminal_screen/TerminalCancelWaitingList.fxml";
                case PAY_BILL -> "/terminal_screen/TerminalPayBillView.fxml";
                case LOST_CODE -> "/terminal_screen/TerminalLostCodeView.fxml";
            };

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();

            Object ctrl = loader.getController();

            // cache controller so we can route async server responses to the active page
            controllerCache.put(view, ctrl);

            if (ctrl instanceof ClientControllerAware aware) {
                aware.setClientController(clientController, connected);
            }

            return root;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + view, e);
        }
    }
}
