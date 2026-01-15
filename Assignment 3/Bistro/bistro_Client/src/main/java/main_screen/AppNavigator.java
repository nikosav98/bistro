package main_screen;

import controllers.ClientController;
import controllers.ClientUIHandler;
import desktop_screen.DesktopScreenController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import responses.ManagerResponse;
import responses.UserHistoryResponse;
import terminal_screen.TerminalScreenController;
import subscriber_screen.SubscriberMainScreenController;

/**
 * Central UI router that swaps between the two client applications:
 * desktop app and terminal app
 * Keeps MainScreenController lightweight
 */
public class AppNavigator {
	private static final double APP_W = 1280;
	private static final double APP_H = 800;
	
    private final Stage stage;
    private final ClientController clientController;
    private final boolean connected;

    private Parent mainRoot;

    private DesktopScreenController desktopController;
    private TerminalScreenController terminalController;
    private SubscriberMainScreenController subscriberController;


    private final ClientUIHandler navigationHandler = new NavigationUIHandler();

    public AppNavigator(Stage stage, ClientController clientController, boolean connected) {
        this.stage = stage;
        this.clientController = clientController;
        this.connected = connected;
    }

    public ClientUIHandler getNavigationHandler() {
        return navigationHandler;
    }

    public void setMainRoot(Parent mainRoot) {
        this.mainRoot = mainRoot;
    }

    public void showMain() {
        runOnFx(() -> {
            if (mainRoot == null) return;

            stage.getScene().setRoot(mainRoot);
            stage.setTitle("Bistro Client");
            stage.sizeToScene();
            stage.centerOnScreen();

            // main screen does not handle server callbacks
            clientController.setUIHandler(navigationHandler);
        });
    }


    public void showLogin() {
        runOnFx(() -> {
        	
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/main_screen/LoginScreen.fxml"));
                Parent loginRoot = loader.load();

                LoginScreenController loginCtrl = loader.getController();
                loginCtrl.setClientController(clientController, connected);

                loginCtrl.setOnBackToMain(this::showMain);

                loginCtrl.setOnLoginAsRole(role -> {
                    String welcome = (role == DesktopScreenController.Role.GUEST) ? "guest" : loginCtrl.getUsernameForWelcome();
                    showDesktop(role, welcome);
                });

                stage.getScene().setRoot(loginRoot);
                stage.setTitle("Login");
                stage.sizeToScene();
                stage.centerOnScreen();

                // while in login, handle popups and routeToDesktop
                clientController.setUIHandler(navigationHandler);

            } catch (Exception e) {
                e.printStackTrace();
                navigationHandler.showError("Navigation Error", "Failed to open LoginScreen.\n" + e.getMessage());
            }
        });
    }
    

    public void showDesktop(DesktopScreenController.Role role, String welcomeName) {
        runOnFx(() -> {
            try {
            	
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/desktop_screen/DesktopScreen.fxml"));
                Parent desktopRoot = loader.load();

                desktopController = loader.getController();
                desktopController.setClientController(clientController, connected);
                desktopController.setRole(role);
                desktopController.setWelcomeName(welcomeName);

                desktopController.setOnLogout(() -> {
                    desktopController = null;
                    showLogin();
                });

                stage.getScene().setRoot(desktopRoot);
                stage.setTitle("Desktop");
                stage.sizeToScene();
                stage.centerOnScreen();

                // desktop becomes the active UI handler for all server callbacks
                clientController.setUIHandler(desktopController);

            } catch (Exception e) {
                e.printStackTrace();
                navigationHandler.showError("Navigation Error", "Failed to open DesktopScreen.\n" + e.getMessage());
            }
        });
    }
    
   
    
    private void runOnFx(Runnable r) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            r.run();
        } else {
            javafx.application.Platform.runLater(r);
        }
    }
    
    public void showTerminal() {
        try {
            String fxml = "/terminal_screen/TerminalScreen.fxml";

            var url = getClass().getResource(fxml);
            if (url == null) {
                throw new IllegalArgumentException("FXML not found on classpath: " + fxml);
            }

            FXMLLoader loader = new FXMLLoader(url);
            Parent terminalRoot = loader.load();

            terminalController = loader.getController();
            terminalController.setClientController(clientController, connected);

            terminalController.setOnBackToMain(() -> {
                terminalController = null;
                showMain();
            });

            stage.getScene().setRoot(terminalRoot);
            stage.setTitle("Terminal");
            stage.sizeToScene();
            stage.centerOnScreen();

            // terminal becomes the active UI handler for server callbacks
            clientController.setUIHandler(new TerminalUIBridge(terminalController));

        } catch (Exception e) {
            e.printStackTrace();
            navigationHandler.showError("Navigation Error", "Failed to open Terminal.\n" + e.getMessage());
        }
    }

    private final class NavigationUIHandler implements ClientUIHandler {

        @Override
        public void showInfo(String title, String message) {
            MainScreenController.showAlert(title, message, javafx.scene.control.Alert.AlertType.INFORMATION);
        }

        @Override
        public void showWarning(String title, String message) {
            MainScreenController.showAlert(title, message, javafx.scene.control.Alert.AlertType.WARNING);
        }

        @Override
        public void showError(String title, String message) {
            MainScreenController.showAlert(title, message, javafx.scene.control.Alert.AlertType.ERROR);
        }

        @Override
        public void showPayload(Object payload) {
            showInfo("Server Message", String.valueOf(payload));
        }

        @Override
        public void routeToDesktop(DesktopScreenController.Role role, String username) {
            showDesktop(role, username);
        }

        @Override
        public void onReservationResponse(responses.ReservationResponse response) {
            // ignore here, desktop will be the handler when reservations are relevant
        }
        @Override
        public void onReportResponse(responses.ReportResponse reportResponse) {
            // Not handled here (desktop will handle when active)
        }

        @Override
        public void onSeatingResponse(responses.SeatingResponse response) {
            // ignore here, terminal will be the handler when seating is relevant
        }

        @Override
        public void onUserHistoryResponse(java.util.List<responses.UserHistoryResponse> rows) {
            // ignore here
        }
        

        @Override
        public void onUserHistoryError(String message) {
            showError("History", message);
        }
        @Override
        public void onUpcomingReservationsResponse(java.util.List<responses.ReservationResponse> rows) {
            // ignore here
        }

        @Override
        public void onUpcomingReservationsError(String message) {
            showError("Upcoming Reservations", message);
        }

		@Override
		public void onManagerResponse(ManagerResponse response) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onBillTotal(double baseTotal, boolean isCash) {
			terminalController.onBillTotal(baseTotal);
			
		}

		@Override
		public void onBillPaid(Integer tableNumber) {
			  terminalController.onBillPaid();
			
		}

		@Override
		public void onBillError(String message) {
			terminalController.onBillError(message);
			
		}
		@Override
        public void onUserDetailsResponse(String email, String phone) {
            showInfo("Subscriber Details", "Email: " + email + "\nPhone: " + phone);
        }
		@Override
        public void onWaitingListCancellation(responses.WaitingListResponse response) {
            boolean cancelled = response != null && response.getHasBeenCancelled();
            showInfo("Waiting List", cancelled
                    ? "Waiting list entry cancelled."
                    : "Unable to cancel waiting list entry.");
        }
    }
    

    private static final class TerminalUIBridge implements ClientUIHandler {

        private final TerminalScreenController terminalController;

        private TerminalUIBridge(TerminalScreenController terminalController) {
            this.terminalController = terminalController;
        }

        @Override
        public void showInfo(String title, String message) {
            MainScreenController.showAlert(title, message, javafx.scene.control.Alert.AlertType.INFORMATION);
        }

        @Override
        public void showWarning(String title, String message) {
            MainScreenController.showAlert(title, message, javafx.scene.control.Alert.AlertType.WARNING);
        }

        @Override
        public void showError(String title, String message) {
            MainScreenController.showAlert(title, message, javafx.scene.control.Alert.AlertType.ERROR);
        }

        @Override
        public void showPayload(Object payload) {
            showInfo("Server Message", String.valueOf(payload));
        }

        @Override
        public void routeToDesktop(DesktopScreenController.Role role, String username) {
            // terminal does not navigate to desktop
        }
        @Override
        public void onReportResponse(responses.ReportResponse reportResponse) {
            // Terminal app doesn't display reports
        }

        @Override
        public void onReservationResponse(responses.ReservationResponse response) {
            // terminal does not handle reservations
        }

        @Override
        public void onSeatingResponse(responses.SeatingResponse response) {
            javafx.application.Platform.runLater(() -> terminalController.onSeatingResponse(response));
        }

        @Override
        public void onUserHistoryResponse(java.util.List<responses.UserHistoryResponse> rows) {
            // terminal does not handle history
        }
        @Override
        public void onUpcomingReservationsResponse(java.util.List<responses.ReservationResponse> rows) {
            // terminal does not handle upcoming reservations
        }

        @Override
        public void onUpcomingReservationsError(String message) {
            showError("Upcoming Reservations", message);
        }

        @Override
        public void onUserHistoryError(String message) {
            showError("History", message);
        }

		@Override
		public void onManagerResponse(ManagerResponse response) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onBillTotal(double baseTotal, boolean isCash) {
			if (terminalController == null) {
                return;
            }
            javafx.application.Platform.runLater(() -> terminalController.onBillTotal(baseTotal));
			
		}

		@Override
		public void onBillPaid(Integer tableNumber) {
			if (terminalController == null) {
                return;
            }
            javafx.application.Platform.runLater(() -> terminalController.onBillPaid());
			
			
		}

		@Override
		public void onBillError(String message) {
			if (terminalController == null) {
                return;
            }
            javafx.application.Platform.runLater(() -> terminalController.onBillError(message));
			
		}
		
		@Override
        public void onUserDetailsResponse(String email, String phone) {
            showInfo("Subscriber Details", "Email: " + email + "\nPhone: " + phone);
        }
		@Override
        public void onWaitingListCancellation(responses.WaitingListResponse response) {
            if (terminalController == null) {
                return;
            }
            javafx.application.Platform.runLater(() -> terminalController.onWaitingListCancellation(response));
        }
    }
}
