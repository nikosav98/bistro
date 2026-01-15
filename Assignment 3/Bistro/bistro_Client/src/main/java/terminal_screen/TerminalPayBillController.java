package terminal_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import requests.BillRequest.BillRequestType;

public class TerminalPayBillController implements ClientControllerAware {

	@FXML private TextField reservationOrTableField;
    @FXML private ListView<String> billItemsList;
    @FXML private Button fetchBillBtn;
    @FXML private Button payBtn;
    @FXML private Label statusLabel;

    private ClientController clientController;
    private boolean connected;

    private boolean billLoaded = false;
    private Double lastBaseTotal;
    private Integer lastConfirmationCode;

    @FXML
    private void initialize() {
    	if (reservationOrTableField != null) {
            reservationOrTableField.setTextFormatter(new TextFormatter<String>(change -> { 
                String newText = change.getControlNewText();
                if (!newText.matches("\\d*")) return null;
                if (newText.length() > 10) return null;
                return change;
            }));
        }
    	if (billItemsList != null)  billItemsList.getItems().setAll("Bill will appear here...");
                  
        if (payBtn != null) payBtn.setDisable(true);
                    
        setStatus("",false);
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onFetchBill() {
    	Integer code = parseConfirmationCode();
        if (code == null) return;
        if (!connected || clientController == null) {
        	setStatus("Terminal is offline.", true);
        	return;
        }
        billLoaded = false;
        lastBaseTotal = null;
        lastConfirmationCode = code;
        if (payBtn != null) {
            payBtn.setDisable(true);
        }
        if (billItemsList != null) {
            billItemsList.getItems().setAll("Fetching bill...");
        }
        setStatus("Fetching bill total...", false);

        clientController.requestBillAction( BillRequestType.REQUEST_TO_SEE_BILL,code,true );
                                                     
        
    }

    @FXML
    private void onPay() {
    	Integer code = lastConfirmationCode != null ? lastConfirmationCode : parseConfirmationCode();
        if (code == null) return;
        if (!billLoaded) {
            setStatus("Load the bill first.", true);
            return;
        }
        if (!connected || clientController == null) {
            setStatus("Terminal is offline.", true);
            return;
        }

        if (payBtn != null)  payBtn.setDisable(true);                  
        setStatus("Processing payment...", false);
        
        clientController.requestBillAction(BillRequestType.PAY_BILL,code, true);                                                      
    }

    @FXML
    private void onClear() {
    	if (reservationOrTableField != null) reservationOrTableField.clear();
        if (billItemsList != null) billItemsList.getItems().setAll("Bill will appear here...");
        billLoaded = false;
        lastBaseTotal = null;
        lastConfirmationCode = null;
        if (payBtn != null) {
            payBtn.setDisable(true);
        }
        setStatus("",false);
    }

    public void onBillTotalLoaded(double baseTotal) {
        lastBaseTotal = baseTotal;
        billLoaded = true;
        if (billItemsList != null) {
            billItemsList.getItems().setAll("Total due: " + formatMoney(baseTotal));
        }
        if (payBtn != null) {
            payBtn.setDisable(false);
        }
        setStatus("Bill ready.", false);
    }

    public void onBillPaid() {
        billLoaded = false;
        lastBaseTotal = null;
        if (billItemsList != null) {
            billItemsList.getItems().setAll("Payment completed. תודה! ✅");
        }
        if (payBtn != null) {
            payBtn.setDisable(true);
        }
        setStatus("Payment completed.", false);
    }

    public void onBillingError(String message) {
        billLoaded = false;
        if (payBtn != null) {
            payBtn.setDisable(true);
        }
        setStatus(message == null ? "Billing failed." : message, true);
    }

    private Integer parseConfirmationCode() {
        String txt = reservationOrTableField == null ? "" : reservationOrTableField.getText().trim();
        if (txt.isEmpty()) {
            setStatus("Enter confirmation code.", true);
            return null;
        }
        if (!txt.matches("\\d{1,10}")) {
            setStatus("Confirmation code must be numeric.", true);
            return null;
        }
        try {
            int code = Integer.parseInt(txt);
            if (code <= 0) {
                setStatus("Confirmation code must be positive.", true);
                return null;
            }
            return code;
        } catch (NumberFormatException ex) {
            setStatus("Confirmation code must be numeric.", true);
            return null;
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
                setStatus("Enter user ID.", true);
                return;
            }
            if (!connected || clientController == null) {
                setStatus("Terminal is offline.", true);
                return;
            }

            clientController.setLostCodeListener(code -> {
                clientController.clearLostCodeListener();
                if (code == null || code <= 0) {
                    setStatus("Failed to resolve confirmation code.", true);
                    return;
                }
                if (reservationOrTableField != null) {
                    reservationOrTableField.setText(String.valueOf(code));
                }
                onFetchBill();
            });
            setStatus("Resolving confirmation code...", false);
            clientController.requestLostCode(userId);
        });
    }

    private String formatMoney(double value) {
        return String.format("%.2f", value);
    }

    private void setStatus(String msg, boolean error) {
        if (statusLabel == null) return;
        statusLabel.setText(msg == null ? "" : msg);
        statusLabel.setStyle(error
                ? "-fx-text-fill: #EF4444; -fx-font-weight: 800;"
                : "-fx-text-fill: #2E9B5F; -fx-font-weight: 800;");
    }
}
