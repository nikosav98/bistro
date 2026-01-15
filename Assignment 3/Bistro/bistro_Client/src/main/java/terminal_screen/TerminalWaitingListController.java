package terminal_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;

public class TerminalWaitingListController implements ClientControllerAware {

	@FXML private TextField userIdField;
    @FXML private TextField guestContactField;
    @FXML private Spinner<Integer> partySizeField; // spinner
    @FXML private Label statusLabel;

    private ClientController clientController;
    private boolean connected;

    @FXML
    private void initialize() {
        if (partySizeField != null) {
            partySizeField.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 2)
            );
            partySizeField.setEditable(true); // optional: allow typing
        }
        setStatus("");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onJoin() {
    	String userId = userIdField == null ? "" : userIdField.getText().trim();
        String guestContact = guestContactField == null ? "" : guestContactField.getText().trim();

        if (userId.isEmpty() && guestContact.isEmpty()) {
            setStatus("Enter a subscriber ID or guest contact.");
            return;
        }

        Integer sizeObj = (partySizeField == null) ? null : partySizeField.getValue();
        int size = (sizeObj == null) ? -1 : sizeObj;

        if (size < 1 || size > 20) { setStatus("Party size must be 1-20."); return; }

        if (!connected || clientController == null) {
        	String contactLabel = userId.isEmpty() ? guestContact : userId;
            setStatus("Demo: added " + contactLabel + " (party of " + size + ") to waiting list.");
        }

        // Later:
        // clientController.sendRequest(new JoinWaitingListRequest(name, phone, size));
        clientController.requestWalkInSeating(userId, guestContact, size);
        setStatus("Requesting a table...");
    }

    @FXML
    private void onClear() {
    	if (userIdField != null) userIdField.clear();
        if (guestContactField != null) guestContactField.clear();
        if (partySizeField != null) partySizeField.getValueFactory().setValue(2);
        setStatus("");
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg == null ? "" : msg);
    }
    
    public void handleSeatingResponse(responses.SeatingResponse response) {
        if (response == null) return;
        switch (response.getType()) {
            case CUSTOMER_CHECKED_IN -> {
                Integer table = response.getTableNumberl();
                setStatus("Table ready! You're seated" + (table != null ? " at table " + table + "." : "."));
            }
            case CUSTOMER_IN_WAITINGLIST -> setStatus("You're on the take-a-seat list. We'll notify you soon.");
        }
    }
}
