package manager_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.collections.FXCollections;
import requests.ManagerRequest;
import requests.ManagerRequest.ManagerCommand;
import responses.ManagerResponse;
import responses.ManagerResponse.ManagerResponseCommand;
import desktop_screen.DesktopScreenController;

public class AddSubscriberScreenController implements ClientControllerAware {

    @FXML private TextField usernameField;
    @FXML private TextField passwordField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private Label infoLabel;
    @FXML private ComboBox<String> roleComboBox;

    private ClientController clientController;
    private DesktopScreenController.Role requesterRole;
    private boolean connected;
    
    @FXML
    private void initialize() {   	
    	 configureRoleOptions();
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }
    
    public void setRequesterRole(DesktopScreenController.Role requesterRole) {
        this.requesterRole = requesterRole;
        configureRoleOptions();
    }

    @FXML
    private void onSave() {
        if (!readyForServer()) return;

        String username = getValue(usernameField);
        String password = getValue(passwordField);
        String phone = getValue(phoneField);
        String email = getValue(emailField);
        String role = roleComboBox == null ? "" : String.valueOf(roleComboBox.getValue());
        
        if(password.length()<6 || password.length()>10) {
        	setInfo("password musn be 6-10 chars long.");
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            setInfo("Username and password are required.");
            return;
        }
        if (role == null || role.isBlank()) {
            setInfo("Role is required.");
            return;
        }

        ManagerRequest request = new ManagerRequest(
                ManagerCommand.ADD_NEW_USER,
                username,
                password,
                phone,
                email,
                role
        );

        clientController.requestManagerAction(request);
        setInfo("Submitting new subscriber...");
    }

    @FXML
    private void onClear() {
        clearFields();
        setInfo("");
    }

    public void handleManagerResponse(ManagerResponse response) {
        if (response == null || response.getResponseCommand() == null) return;

        if (response.getResponseCommand() == ManagerResponseCommand.NEW_USER_RESPONSE) {
            String newId = response.getUserID();
            setInfo(newId == null ? "Subscriber added." : "Subscriber added. ID: " + newId);

            // clear input fields but keep success message visible
            clearFields();
        }
    }

    private void clearFields() {
        if (usernameField != null) usernameField.clear();
        if (passwordField != null) passwordField.clear();
        if (phoneField != null) phoneField.clear();
        if (emailField != null) emailField.clear();
        if (roleComboBox != null) roleComboBox.getSelectionModel().select("SUBSCRIBER");
    }
    
    private void configureRoleOptions() {
        if (roleComboBox == null) return;

        boolean subscriberOnly = requesterRole == DesktopScreenController.Role.REP;
        if (subscriberOnly) {
            roleComboBox.setItems(FXCollections.observableArrayList("SUBSCRIBER"));
            roleComboBox.getSelectionModel().select("SUBSCRIBER");
            roleComboBox.setDisable(true);
        } else {
            roleComboBox.setItems(FXCollections.observableArrayList("SUBSCRIBER", "REPRESENTATIVE", "MANAGER"));
            roleComboBox.getSelectionModel().select("SUBSCRIBER");
            roleComboBox.setDisable(false);
        }
    }



    private String getValue(TextField field) {
        if (field == null || field.getText() == null) return "";
        return field.getText().trim();
    }

    private boolean readyForServer() {
        if (!connected || clientController == null) {
            setInfo("Not connected to server.");
            return false;
        }
        return true;
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }
}
