package manager_screen;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

import java.util.function.BiConsumer;

public class ManagerReservationStartScreenController {

    @FXML private RadioButton subscriberRadio;
    @FXML private RadioButton guestRadio;
    @FXML private ToggleGroup reservationTargetGroup;
    @FXML private TextField userIdField;
    @FXML private TextField guestContactField;
    @FXML private Label infoLabel;

    private BiConsumer<String, String> onContinue;
    private Runnable onCancel;

    @FXML
    private void initialize() {
        if (reservationTargetGroup != null && subscriberRadio != null) {
            subscriberRadio.setSelected(true);
            reservationTargetGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> updateTargetFields());
            updateTargetFields();
        }
    }

    public void setOnContinue(BiConsumer<String, String> onContinue) {
        this.onContinue = onContinue;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @FXML
    private void onContinue() {
        boolean isSubscriber = subscriberRadio != null && subscriberRadio.isSelected();
        boolean isGuest = guestRadio != null && guestRadio.isSelected();

        if (!isSubscriber && !isGuest) {
            setInfo("Select subscriber or guest.");
            return;
        }

        String userId = userIdField == null ? null : userIdField.getText();
        String guestContact = guestContactField == null ? null : guestContactField.getText();
        userId = userId == null ? null : userId.trim();
        guestContact = guestContact == null ? null : guestContact.trim();

        if (isSubscriber && (userId == null || userId.isBlank())) {
            setInfo("Subscriber ID is required.");
            return;
        }
        if (isGuest && (guestContact == null || guestContact.isBlank())) {
            setInfo("Guest contact is required.");
            return;
        }

        if (onContinue != null) {
            onContinue.accept(isSubscriber ? userId : null, isGuest ? guestContact : null);
        }
    }

    @FXML
    private void onCancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void updateTargetFields() {
        boolean isSubscriber = subscriberRadio != null && subscriberRadio.isSelected();
        if (userIdField != null) {
            userIdField.setDisable(!isSubscriber);
            userIdField.setManaged(isSubscriber);
            userIdField.setVisible(isSubscriber);
            if (!isSubscriber) {
                userIdField.clear();
            }
        }
        if (guestContactField != null) {
            guestContactField.setDisable(isSubscriber);
            guestContactField.setManaged(!isSubscriber);
            guestContactField.setVisible(!isSubscriber);
            if (isSubscriber) {
                guestContactField.clear();
            }
        }
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }
}