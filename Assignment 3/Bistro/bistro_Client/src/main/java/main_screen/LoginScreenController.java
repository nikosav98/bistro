package main_screen;

import controllers.ClientController;
import desktop_screen.DesktopScreenController;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public class LoginScreenController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField guestContactField;

    @FXML private ChoiceBox<DesktopScreenController.Role> roleChoiceBox;

    @FXML private Button loginButton;

    @FXML private Label statusLabel;
    @FXML private Button guestButton;
    @FXML private Button backButton;

    private ClientController clientController;
    private boolean connected;

    private Runnable onBackToMain;
    private Consumer<DesktopScreenController.Role> onLoginAsRole;

    private boolean guestMode;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?[0-9]{7,15}$");

    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
        updateConnectionStateUI();
    }

    public void setOnBackToMain(Runnable cb) {
        this.onBackToMain = cb;
    }

    public void setOnLoginAsRole(Consumer<DesktopScreenController.Role> cb) {
        this.onLoginAsRole = cb;
    }

    public String getUsernameForWelcome() {
        if (usernameField == null) return "";
        return usernameField.getText() == null ? "" : usernameField.getText().trim();
    }

    @FXML
    private void initialize() {
        if (statusLabel != null) statusLabel.setText("");

        if (passwordField != null) {
            passwordField.setOnAction(e -> onLoginClicked());
        }

        // start in member mode
        setGuestMode(false);
    }

    /**
     * Login button click
     * calls the client controller with a request
     */
    @FXML
    private void onLoginClicked() {

        if (guestMode) {
            String contact = guestContactField == null ? "" : guestContactField.getText();
            contact = contact == null ? "" : contact.trim();

            if (!isValidContact(contact)) {
                setStatus("Enter a valid email or phone number.", true);
                return;
            }

            // store contact in controller for this session
            if (clientController != null) {
                clientController.startGuestSession(contact);
            }

            setStatus("Continuing as guest...", false);

            if (onLoginAsRole != null) {
                onLoginAsRole.accept(DesktopScreenController.Role.GUEST);
            }
            return;
        }

        String username = usernameField == null ? "" : usernameField.getText();
        String password = passwordField == null ? "" : passwordField.getText();

        if (clientController == null) {
            setStatus("Client controller not ready.", true);
            return;
        }

        clientController.requestLogin(username, password);
        setStatus("Logging in...", false);
    }

    @FXML
    private void onContinueAsGuestClicked() {
        // toggle mode
        setGuestMode(!guestMode);
    }

    @FXML
    private void onBackClicked(ActionEvent event) {
        if (onBackToMain != null) {
            onBackToMain.run();
            return;
        }
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    private void updateConnectionStateUI() {
        if (loginButton != null) loginButton.setDisable(false);

        if (statusLabel != null && !connected) {
            setStatus("No server connection", true);
        }
    }

    private void setStatus(String message, boolean error) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusLabel.setStyle(error
                ? "-fx-text-fill: #EF4444; -fx-font-weight: 800;"
                : "-fx-text-fill: #2E9B5F; -fx-font-weight: 800;");
    }

    private void setGuestMode(boolean guest) {
        this.guestMode = guest;

        if (usernameField != null) {
            usernameField.setVisible(!guest);
            usernameField.setManaged(!guest);
        }
        if (passwordField != null) {
            passwordField.setVisible(!guest);
            passwordField.setManaged(!guest);
        }
        if (guestContactField != null) {
            guestContactField.setVisible(guest);
            guestContactField.setManaged(guest);
            if (!guest) {
                guestContactField.clear();
            }
        }

        if (loginButton != null) {
            loginButton.setText(guest ? "Continue as guest" : "Login");
        }
        if (guestButton != null) {
            guestButton.setText(guest ? "Back to member login" : "Continue as guest");
        }

        // clear status when switching modes
        setStatus("", false);
    }

    private boolean isValidContact(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        if (EMAIL_PATTERN.matcher(v).matches()) {
            return true;
        }
        return PHONE_PATTERN.matcher(v).matches();
    }
}
