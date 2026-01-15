package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import requests.ReservationRequest.ReservationRequestType;
import responses.ReservationResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Edit reservation flow:
 * Step 1 confirmation code
 * Step 2 choose a new time slot (different from current), then send EDIT_RESERVATION
 */
public class EditReservationViewController implements ClientControllerAware {

    // Step panes
    @FXML private VBox step1Pane;
    @FXML private VBox step2Pane;

    // Step 1
    @FXML private TextField confirmationCodeField;
    @FXML private Label codeErrorLabel;
    @FXML private Label statusLabel;
    @FXML private Button loadButton;

    // Step 2 (choose date + party + time, then confirm)
    @FXML private DatePicker datePicker;
    @FXML private Spinner<Integer> partySizeSpinner;
    @FXML private Label slotInfoLabel;
    @FXML private TilePane slotsTile;
    @FXML private Label actionStatusLabel;

    @FXML private Label originalSummaryLabel;
    @FXML private Label newSummaryLabel;
    @FXML private Button confirmNewDetailsButton;

    private ReservationSlotsUI slotsUI;

    private ClientController clientController;
    private boolean connected;

    private boolean guestMode;
    private String guestContact;
    private String userId;

    private int currentConfirmationCode;

    private LocalDate currentReservationDate;
    private LocalTime currentReservationTime;
    private int currentReservationParty;

    private LocalDate selectedDate;
    private Integer selectedParty;

    private boolean cancelledLock;

    private static final int MIN_PARTY = 1;
    private static final int MAX_PARTY = 20;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final double SLOT_WIDTH = 130.0;
    private static final double SLOT_HEIGHT = 55.0;

    @FXML
    private void initialize() {
        initPartySizeSpinner();
        initDatePickerLimit();

        slotsUI = new ReservationSlotsUI(slotsTile, slotInfoLabel, SLOT_WIDTH, SLOT_HEIGHT);

        resetStep2State();
        switchToStep1();
        setStatus("");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;

        if (controller != null) {
            this.guestMode = controller.isGuestSession();
            if (guestMode) {
                this.userId = null;
                this.guestContact = controller.getGuestContact();
            } else {
                this.userId = controller.getCurrentUsername();
                this.guestContact = null;
            }
        }
    }

    private void initPartySizeSpinner() {
        if (partySizeSpinner == null) return;

        partySizeSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_PARTY, MAX_PARTY, 2)
        );
        partySizeSpinner.setEditable(true);

        partySizeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            selectedParty = newV;
            if (slotsUI != null) slotsUI.clearSelection();
            updateNewSummary();
            updateConfirmEnabled();
        });
    }

    private void initDatePickerLimit() {
        if (datePicker == null) return;

        LocalDate today = LocalDate.now();
        LocalDate max = today.plusDays(6);

        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) return;

                boolean disabled = item.isBefore(today) || item.isAfter(max);
                setDisable(disabled);
                setStyle(disabled ? "-fx-opacity: 0.45;" : "");
            }
        });

        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;

            LocalDate clamped = newV;
            if (clamped.isBefore(today)) clamped = today;
            if (clamped.isAfter(max)) clamped = max;

            if (!clamped.equals(newV)) {
                datePicker.setValue(clamped);
                return;
            }

            selectedDate = clamped;
            if (slotsUI != null) slotsUI.clearSelection();
            if (slotsUI != null) slotsUI.clear();
            updateNewSummary();
            updateConfirmEnabled();
        });
    }
    @FXML
    private void onLoadReservation() {
        loadReservationByCode(parseConfirmationCode());
    }

    
    public void loadReservationByCode(Integer confirmationCode) {
        clearMessages();

        if (cancelledLock) {
            setStatus("Reservation was cancelled. Loading by confirmation code is disabled.", true);
            return;
        }
        if (clientController == null) {
            setStatus("ClientController not set.", true);
            return;
        }
        if (!connected) {
            setStatus("Not connected to server.", true);
            return;
        }
        if (confirmationCode == null) return;

        currentConfirmationCode = confirmationCode;
        setStatus("Loading reservation...", false);
        if (confirmationCodeField != null) {
            confirmationCodeField.setText(String.valueOf(confirmationCode));
        }

        clientController.requestNewReservation(
                ReservationRequestType.SHOW_RESERVATION,
                null,
                null,
                0,
                userId,
                guestContact,
                currentConfirmationCode
        );
    }

    @FXML
    private void onFindTimes() {
        clearMessages();

        if (slotsUI != null) slotsUI.clear();
        if (slotsUI != null) slotsUI.clearSelection();

        if (clientController == null) {
            setSlotInfo("ClientController not set.");
            return;
        }
        if (!connected) {
            setSlotInfo("Not connected to server.");
            return;
        }
        if (currentConfirmationCode <= 0) {
            setSlotInfo("Missing confirmation code.");
            return;
        }

        LocalDate d = datePicker == null ? null : datePicker.getValue();
        Integer p = partySizeSpinner == null ? null : partySizeSpinner.getValue();

        if (d == null) {
            setSlotInfo("Please choose a date.");
            return;
        }
        if (p == null || p < MIN_PARTY || p > MAX_PARTY) {
            setSlotInfo("Party size must be 1-20.");
            return;
        }

        selectedDate = d;
        selectedParty = p;

        updateNewSummary();
        updateConfirmEnabled();

        setSlotInfo("Fetching available times...");

        clientController.requestNewReservation(
                ReservationRequestType.FIRST_PHASE,
                d,
                null,
                p,
                guestMode ? null : userId,
                guestMode ? guestContact : null,
                currentConfirmationCode
        );
    }

    @FXML
    private void onConfirmChange() {
        clearMessages();

        if (clientController == null) {
            setActionStatus("ClientController not set.", true);
            return;
        }
        if (!connected) {
            setActionStatus("Not connected to server.", true);
            return;
        }
        if (currentConfirmationCode <= 0) {
            setActionStatus("Missing confirmation code.", true);
            return;
        }
        if (selectedDate == null) {
            setActionStatus("Please choose a date.", true);
            return;
        }
        if (selectedParty == null || selectedParty < MIN_PARTY || selectedParty > MAX_PARTY) {
            setActionStatus("Party size must be 1-20.", true);
            return;
        }
        if (slotsUI == null || !slotsUI.hasSelection()) {
            setActionStatus("Please select a time.", true);
            return;
        }

        LocalDate pickedDate = slotsUI.getSelectedDate();
        LocalTime pickedTime = slotsUI.getSelectedTime();

        if (pickedDate == null || pickedTime == null) {
            setActionStatus("Please select a time.", true);
            return;
        }

        if (!pickedDate.equals(selectedDate)) {
            setActionStatus("Please select a time for the chosen date.", true);
            return;
        }

        if (isSameAsCurrent(pickedDate, pickedTime, selectedParty)) {
            setActionStatus("New details must be different from the original.", true);
            return;
        }

        setActionStatus("Submitting edit request...", false);

        clientController.requestNewReservation(
                ReservationRequestType.EDIT_RESERVATION,
                pickedDate,
                pickedTime,
                selectedParty,
                guestMode ? null : userId,
                guestMode ? guestContact : null,
                currentConfirmationCode
        );
    }

    @FXML
    private void onCancelReservation() {
        clearMessages();

        if (clientController == null) {
            setActionStatus("ClientController not set.", true);
            return;
        }
        if (!connected) {
            setActionStatus("Not connected to server.", true);
            return;
        }
        if (currentConfirmationCode <= 0) {
            setActionStatus("Missing confirmation code.", true);
            return;
        }

        setActionStatus("Submitting cancel request...", false);

        clientController.requestNewReservation(
                ReservationRequestType.CANCEL_RESERVATION,
                null,
                null,
                0,
                guestMode ? null : userId,
                guestMode ? guestContact : null,
                currentConfirmationCode
        );
    }

    @FXML
    private void onBackToCode() {
        switchToStep1();
        if (!cancelledLock) setStatus("", false);
    }

    @FXML
    private void onClearCode() {
        if (cancelledLock) {
            setStatus("Reservation was cancelled. Loading by confirmation code is disabled.", true);
            return;
        }
        if (confirmationCodeField != null) confirmationCodeField.setText("");
        clearMessages();
        setStatus("", false);
    }

    private Integer parseConfirmationCode() {
        String raw = confirmationCodeField == null ? "" : confirmationCodeField.getText();
        raw = raw == null ? "" : raw.trim();

        if (raw.isEmpty()) {
            setCodeError("Confirmation code is required.");
            return null;
        }
        if (!raw.matches("\\d{4,10}")) {
            setCodeError("Confirmation code must be 4-10 digits.");
            return null;
        }

        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            setCodeError("Invalid confirmation code.");
            return null;
        }
    }

    private void resetStep2State() {
        selectedDate = null;
        selectedParty = null;

        if (originalSummaryLabel != null) originalSummaryLabel.setText("-");
        if (newSummaryLabel != null) newSummaryLabel.setText("Select date, party, and time");

        if (confirmNewDetailsButton != null) confirmNewDetailsButton.setDisable(true);

        if (slotsUI != null) slotsUI.clear();
        if (slotInfoLabel != null) slotInfoLabel.setText("");
        if (actionStatusLabel != null) actionStatusLabel.setText("");
    }

    private void updateOriginalSummary() {
        if (originalSummaryLabel == null) return;

        if (currentReservationDate == null || currentReservationTime == null || currentReservationParty <= 0) {
            originalSummaryLabel.setText("-");
            return;
        }

        originalSummaryLabel.setText(
                currentReservationDate + " " + currentReservationTime.format(TIME_FMT) + " | party " + currentReservationParty
        );
    }

    private void updateNewSummary() {
        if (newSummaryLabel == null) return;

        LocalDate d = selectedDate != null ? selectedDate : (datePicker == null ? null : datePicker.getValue());
        Integer p = selectedParty != null ? selectedParty : (partySizeSpinner == null ? null : partySizeSpinner.getValue());

        if (slotsUI != null && slotsUI.hasSelection() && slotsUI.getSelectedTime() != null && slotsUI.getSelectedDate() != null) {
            LocalDate sd = slotsUI.getSelectedDate();
            LocalTime st = slotsUI.getSelectedTime();
            newSummaryLabel.setText(sd + " " + st.format(TIME_FMT) + " | party " + (p == null ? "-" : p));
            return;
        }

        if (d == null && p == null) {
            newSummaryLabel.setText("Select date, party, and time");
            return;
        }

        if (d != null && p != null) {
            newSummaryLabel.setText(d + " | party " + p + " | select time");
            return;
        }

        if (d != null) {
            newSummaryLabel.setText(d + " | select party and time");
            return;
        }

        newSummaryLabel.setText("party " + p + " | select date and time");
    }

    private void updateConfirmEnabled() {
        if (confirmNewDetailsButton == null) return;

        LocalDate d = selectedDate != null ? selectedDate : (datePicker == null ? null : datePicker.getValue());
        Integer p = selectedParty != null ? selectedParty : (partySizeSpinner == null ? null : partySizeSpinner.getValue());

        if (d == null || p == null) {
            confirmNewDetailsButton.setDisable(true);
            return;
        }

        if (slotsUI == null || !slotsUI.hasSelection()) {
            confirmNewDetailsButton.setDisable(true);
            return;
        }

        LocalDate sd = slotsUI.getSelectedDate();
        LocalTime st = slotsUI.getSelectedTime();

        if (sd == null || st == null) {
            confirmNewDetailsButton.setDisable(true);
            return;
        }

        boolean different = !isSameAsCurrent(sd, st, p);
        confirmNewDetailsButton.setDisable(!different);
    }

    private boolean isSameAsCurrent(LocalDate d, LocalTime t, int party) {
        if (d == null || t == null) return false;
        if (currentReservationDate == null || currentReservationTime == null || currentReservationParty <= 0) return false;
        return currentReservationDate.equals(d) && currentReservationTime.equals(t) && currentReservationParty == party;
    }

    private boolean excludeCurrentSlotOnly(LocalDate d, LocalTime t) {
        return isSameAsCurrent(d, t, selectedParty == null ? currentReservationParty : selectedParty);
    }

    private void onSlotPicked(LocalDate d, LocalTime t) {
        updateNewSummary();
        updateConfirmEnabled();
        setActionStatus("", false);
    }

    private void clearMessages() {
        if (codeErrorLabel != null) codeErrorLabel.setText("");
        if (statusLabel != null) statusLabel.setText("");
        if (actionStatusLabel != null) actionStatusLabel.setText("");
        if (slotInfoLabel != null) slotInfoLabel.setText("");
    }

    private void switchToStep1() {
        if (step1Pane != null) { step1Pane.setVisible(true); step1Pane.setManaged(true); }
        if (step2Pane != null) { step2Pane.setVisible(false); step2Pane.setManaged(false); }
    }

    private void switchToStep2() {
        if (step1Pane != null) { step1Pane.setVisible(false); step1Pane.setManaged(false); }
        if (step2Pane != null) { step2Pane.setVisible(true); step2Pane.setManaged(true); }
    }

    private void setCodeError(String msg) {
        if (codeErrorLabel != null) codeErrorLabel.setText(msg == null ? "" : msg);
    }

    private void setStatus(String msg, boolean error) {
        if (statusLabel == null) return;
        statusLabel.setText(msg == null ? "" : msg);
        statusLabel.setStyle(error
                ? "-fx-text-fill: #EF4444; -fx-font-weight: 800;"
                : "-fx-text-fill: #2E9B5F; -fx-font-weight: 800;");
    }

    private void setStatus(String msg) {
        setStatus(msg, false);
    }

    private void setActionStatus(String msg, boolean error) {
        if (actionStatusLabel == null) return;
        actionStatusLabel.setText(msg == null ? "" : msg);
        actionStatusLabel.setStyle(error
                ? "-fx-text-fill: #EF4444; -fx-font-weight: 800;"
                : "-fx-text-fill: #2E9B5F; -fx-font-weight: 800;");
    }

    private void setSlotInfo(String msg) {
        if (slotInfoLabel != null) slotInfoLabel.setText(msg == null ? "" : msg);
    }

    private void lockLoadingAfterCancel() {
        cancelledLock = true;

        if (confirmationCodeField != null) confirmationCodeField.setDisable(true);
        if (loadButton != null) loadButton.setDisable(true);

        setStatus("Reservation was cancelled. Loading by confirmation code is disabled.", true);
    }

    /**
     * DesktopScreenController forwards ReservationResponse here.
     */
    public void onReservationResponse(ReservationResponse resp) {
        if (resp == null) return;

        switch (resp.getType()) {
            case SHOW_RESERVATION -> {
                currentReservationDate = resp.getNewDate();
                currentReservationTime = resp.getNewTime();
                currentReservationParty = resp.getNewPartySize();

                Integer code = resp.getConfirmationCode();
                if (code != null) currentConfirmationCode = code;

                resetStep2State();
                updateOriginalSummary();

                try {
                    if (datePicker != null && currentReservationDate != null) datePicker.setValue(currentReservationDate);
                    if (partySizeSpinner != null && currentReservationParty > 0) {
                        partySizeSpinner.getValueFactory().setValue(currentReservationParty);
                    }
                } catch (Exception ignored) { }
                

                selectedDate = datePicker == null ? currentReservationDate : datePicker.getValue();
                selectedParty = partySizeSpinner == null ? currentReservationParty : partySizeSpinner.getValue();

                updateNewSummary();
                updateConfirmEnabled();

                switchToStep2();
                setSlotInfo("Pick a time, then confirm.");

                onFindTimes();
            }

            case FIRST_PHASE_SHOW_AVAILABILITY -> {
                if (slotsUI != null) slotsUI.clear();

                if (slotsUI != null) {
                    slotsUI.info("Select a time.");
                    slotsUI.renderAvailabilitySelectable(
                            selectedDate,
                            resp.getAvailableTimes(),
                            (d, t) -> excludeCurrentSlotOnly(d, t),
                            (d, t) -> onSlotPicked(d, t)
                    );
                }

                Map<LocalDate, java.util.List<LocalTime>> sug = resp.getSuggestedDates();
                if (sug != null && !sug.isEmpty() && slotsUI != null) {
                    slotsUI.renderSuggestionsSelectable(
                            sug,
                            (d, t) -> excludeCurrentSlotOnly(d, t),
                            (d, t) -> onSlotPicked(d, t)
                    );
                }
            }

            case FIRST_PHASE_SHOW_SUGGESTIONS -> {
                if (slotsUI != null) slotsUI.clear();

                if (slotsUI != null) {
                    slotsUI.info("No exact match found. Showing alternatives.");
                    slotsUI.renderSuggestionsSelectable(
                            resp.getSuggestedDates(),
                            (d, t) -> excludeCurrentSlotOnly(d, t),
                            (d, t) -> onSlotPicked(d, t)
                    );
                }
            }

            case FIRST_PHASE_NO_AVAILABILITY_OR_SUGGESTIONS -> {
                if (slotsUI != null) slotsUI.clear();
                setSlotInfo("No available times for the selected date.");
            }

            case EDIT_RESERVATION -> {
                currentReservationDate = resp.getNewDate() != null ? resp.getNewDate() : currentReservationDate;
                currentReservationTime = resp.getNewTime() != null ? resp.getNewTime() : currentReservationTime;
                currentReservationParty = resp.getNewPartySize() > 0 ? resp.getNewPartySize() : currentReservationParty;

                switchToStep1();
                if (confirmationCodeField != null) confirmationCodeField.setText("");
                setStatus("Reservation updated successfully.", false);

                resetStep2State();
                currentConfirmationCode = 0;
            }

            case CANCEL_RESERVATION -> {
                setActionStatus("Reservation cancelled successfully.", false);
                switchToStep1();
                lockLoadingAfterCancel();
            }

            default -> { }
        }
    }
}
