package terminal_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import responses.WaitingListResponse;
import javafx.scene.control.TextInputDialog;

public class TerminalCancelWaitingListController implements ClientControllerAware {

    @FXML private TextField confirmationCodeField;
    @FXML private Label statusLabel;

    private ClientController clientController;
    private boolean connected;

    @FXML
    private void initialize() {
        setStatus("");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onCancel() {
        String rawCode = confirmationCodeField == null ? "" : confirmationCodeField.getText().trim();
        if (rawCode.isEmpty()) {
            setStatus("Enter your confirmation code.");
            return;
        }

        int code;
        try {
            code = Integer.parseInt(rawCode);
        } catch (NumberFormatException ex) {
            setStatus("Confirmation code must be a number.");
            return;
        }

        if (code <= 0) {
            setStatus("Confirmation code must be a positive number.");
            return;
        }

        if (!connected || clientController == null) {
            setStatus("Demo: waiting list entry cancelled for code " + code + ".");
            return;
        }

        clientController.requestWaitingListCancellation(code);
        setStatus("Submitting cancel request...");
    }

    public void handleWaitingListResponse(WaitingListResponse response) {
        if (response == null) {
            setStatus("No response received. Please try again.");
            return;
        }
        if (response.getHasBeenCancelled()) {
            setStatus("Waiting list entry cancelled.");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Waiting List");
            alert.setHeaderText(null);
            alert.setContentText("Waiting list entry cancelled.");
            alert.showAndWait();
        } else {
            setStatus("Unable to cancel waiting list entry.");
        }
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }
    
    @FXML
    private void onUseUserId() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Use user ID");
        dialog.setHeaderText("Enter subscriber user ID");
        dialog.setContentText("User ID:");

        dialog.showAndWait().ifPresent(input -> {
            String userId = input == null ? "" : input.trim();
            if (userId.isEmpty()) {
                setStatus("Enter user ID.");
                return;
            }
            if (!connected || clientController == null) {
                setStatus("Offline Mode: cannot cancel without server connection.");
                return;
            }

            clientController.setLostCodeListener(code -> {
                clientController.clearLostCodeListener();
                if (code == null || code <= 0) {
                    setStatus("Failed to resolve confirmation code.");
                    return;
                }
                if (confirmationCodeField != null) {
                    confirmationCodeField.setText(String.valueOf(code));
                }
                onCancel();
            });
            setStatus("Resolving confirmation code...");
            clientController.requestLostCode(userId);
        });
    }
}