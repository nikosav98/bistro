package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import requests.BillRequest.BillRequestType;

/**
 * Pay bill flow:
 * Step 1 enter confirmation code
 * Step 2 show summary (total, discount, final amount) and pay
 * Note: bill items are planned for later!!!
 */
public class PayViewController implements ClientControllerAware {

    @FXML private TextField confirmationCodeField;
    @FXML private RadioButton cardRadio;
    @FXML private RadioButton cashRadio;
    @FXML private ToggleGroup paymentToggleGroup;

    @FXML private Button showBillButton;
    @FXML private Button payBillButton;

    @FXML private Label statusLabel;

    // Summary labels (for now we only show totals)
    @FXML private Label paymentMethodValueLabel;
    @FXML private Label baseTotalValueLabel;
    @FXML private Label discountValueLabel;
    @FXML private Label totalToPayValueLabel;

    @FXML private javafx.scene.layout.VBox billPlaceholderContainer;

    private ClientController clientController;
    private boolean connected;

    private Double lastBaseTotal;

    @FXML
    private void initialize() {
        setStatus("", false);
        clearSummary();

        if (payBillButton != null) {
            payBillButton.setDisable(true);
        }

        if (billPlaceholderContainer != null) {
            billPlaceholderContainer.setManaged(true);
            billPlaceholderContainer.setVisible(true);
        }
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onShowBillClicked() {
        Integer code = parseConfirmationCode();
        if (code == null) {
            return;
        }
        if (clientController == null || !connected) {
            setStatus("Not connected to server.", true);
            return;
        }

        clearSummary();
        lastBaseTotal = null;

        if (payBillButton != null) {
            payBillButton.setDisable(true);
        }

        boolean isCash = isCashSelected();
        setStatus("Fetching bill total...", false);

        // we keep REQUEST_TO_SEE_BILL for now, later it can return full bill items
        clientController.requestBillAction(
                BillRequestType.REQUEST_TO_SEE_BILL,
                code,
                isCash
        );
    }

    @FXML
    private void onPayBillClicked() {
        Integer code = parseConfirmationCode();
        if (code == null) {
            return;
        }
        if (clientController == null || !connected) {
            setStatus("Not connected to server.", true);
            return;
        }

        boolean isCash = isCashSelected();
        setStatus("Processing payment...", false);

        if (payBillButton != null) {
            payBillButton.setDisable(true);
        }

        clientController.requestBillAction(
                BillRequestType.PAY_BILL,
                code,
                isCash
        );
    }

    /**
     * called by DesktopScreenController when a bill total is returned
     * client computes discount: guests no discount, subscribers 10%
     */
    public void onBillTotalLoaded(double baseTotal, boolean isCashPayment, boolean isSubscriber) {
        lastBaseTotal = baseTotal;

        double discountRate = isSubscriber ? 0.10 : 0.0;
        double discountAmount = roundMoney(baseTotal * discountRate);
        double finalTotal = roundMoney(baseTotal - discountAmount);

        setSummary(isCashPayment, baseTotal, discountAmount, finalTotal);
        setStatus("Bill ready.", false);

        if (payBillButton != null) {
            payBillButton.setDisable(false);
        }
    }

    /**
     * called by DesktopScreenController when payment is confirmed
     */
    public void onBillPaid(Integer tableNumber) {
        String msg = "Payment completed.";
        if (tableNumber != null) {
            msg += " Table " + tableNumber + " is now available.";
        }
        setStatus(msg, false);

        lastBaseTotal = null;

        if (payBillButton != null) {
            payBillButton.setDisable(true);
        }
    }

    /**
     * called by DesktopScreenController when billing fails
     */
    public void onBillingError(String message) {
        setStatus(message == null ? "Billing failed." : message, true);

        if (payBillButton != null) {
            // keep disabled unless we already have totals loaded
            payBillButton.setDisable(lastBaseTotal == null);
        }
    }
    
    public void loadBillForConfirmationCode(int confirmationCode) {
        if (confirmationCodeField == null) {
            setStatus("Confirmation code field is not available.", true);
            return;
        }
        confirmationCodeField.setText(String.valueOf(confirmationCode));
        onShowBillClicked();
    }

    private Integer parseConfirmationCode() {
        if (confirmationCodeField == null) {
            setStatus("Confirmation code field is not available.", true);
            return null;
        }
        String raw = confirmationCodeField.getText();
        if (raw == null || raw.trim().isEmpty()) {
            setStatus("Please enter a confirmation code.", true);
            return null;
        }
        try {
            int code = Integer.parseInt(raw.trim());
            if (code <= 0) {
                setStatus("Confirmation code must be a positive number.", true);
                return null;
            }
            return code;
        } catch (NumberFormatException ex) {
            setStatus("Confirmation code must be numeric.", true);
            return null;
        }
    }

    private boolean isCashSelected() {
        if (cashRadio != null && cashRadio.isSelected()) {
            return true;
        }
        return false;
    }

    private void clearSummary() {
        if (paymentMethodValueLabel != null) paymentMethodValueLabel.setText("-");
        if (baseTotalValueLabel != null) baseTotalValueLabel.setText("-");
        if (discountValueLabel != null) discountValueLabel.setText("-");
        if (totalToPayValueLabel != null) totalToPayValueLabel.setText("-");
    }

    private void setSummary(boolean isCashPayment, double baseTotal, double discountAmount, double finalTotal) {
        if (paymentMethodValueLabel != null) {
            paymentMethodValueLabel.setText(isCashPayment ? "cash" : "card");
        }
        if (baseTotalValueLabel != null) {
            baseTotalValueLabel.setText(formatMoney(baseTotal));
        }
        if (discountValueLabel != null) {
            discountValueLabel.setText(discountAmount <= 0 ? "-" : ("-" + formatMoney(discountAmount)));
        }
        if (totalToPayValueLabel != null) {
            totalToPayValueLabel.setText(formatMoney(finalTotal));
        }
    }

    private String formatMoney(double value) {
        return String.format("%.2f", value);
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void setStatus(String message, boolean error) {
        if (statusLabel == null) {
            return;
        }
        statusLabel.setText(message == null ? "" : message);
        statusLabel.setStyle(error
                ? "-fx-text-fill: #EF4444; -fx-font-weight: 800;"
                : "-fx-text-fill: #2E9B5F; -fx-font-weight: 800;");
    }
}
