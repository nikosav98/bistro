package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import requests.ReservationRequest.ReservationRequestType;
import responses.ReservationResponse;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.control.DateCell;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReservationsViewController implements ClientControllerAware {

    // Step 1
    @FXML private Spinner<Integer> partySizeSpinner;
    @FXML private DatePicker datePicker;
    @FXML private Label infoLabel;

    // Step 2
    @FXML private VBox step1Pane;
    @FXML private VBox step2Pane;
    @FXML private TilePane slotsTile;
    @FXML private Label slotHeaderLabel;
    @FXML private Label slotInfoLabel;
    @FXML private VBox slotsContainer;

    private ClientController clientController;

    private LocalDate currentDate;
    private int currentPartySize;
    private String userID;

    private boolean guestMode;
    private String guestContact;
    private boolean connected;
    private String overrideUserId;
    private String overrideGuestContact;
    private boolean overrideIdentity;
    private static final int MIN_PARTY = 1;
    private static final int MAX_PARTY = 20;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static final double SLOT_WIDTH = 130.0;
    private static final double SLOT_HEIGHT = 55.0;

    @FXML
    private void initialize() {    	
        initPartySizeSpinner();
        initDatePickerLimit();
        switchToStep1();
        setInfo("Pick party size + date, then choose a time block.");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
        
        refreshIdentityFromController();
    }

    private void initDatePickerLimit() {
        if (datePicker == null) return;

        LocalDate today = LocalDate.now();
        LocalDate max = today.plusDays(6);

        datePicker.setValue(today);

        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) return;

                boolean disabled = item.isBefore(today) || item.isAfter(max);
                setDisable(disabled);

                if (disabled) {
                    setStyle("-fx-opacity: 0.45;");
                } else {
                    setStyle("");
                }
            }
        });

        datePicker.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (newV.isBefore(today)) datePicker.setValue(today);
            else if (newV.isAfter(max)) datePicker.setValue(max);
        });
    }

    private void initPartySizeSpinner() {
        if (partySizeSpinner == null) return;

        partySizeSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(MIN_PARTY, MAX_PARTY, 2)
        );
        partySizeSpinner.setEditable(true);

        TextFormatter<String> formatter = new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) return change;
            return newText.matches("\\d{0,2}") ? change : null;
        });

        partySizeSpinner.getEditor().setTextFormatter(formatter);
        partySizeSpinner.getEditor().setOnAction(e -> clampPartySize());
        partySizeSpinner.getEditor().focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) clampPartySize();
        });
    }
    /*
     * helper function to limit the party size using constants
     */
    private void clampPartySize() {
        if (partySizeSpinner == null || partySizeSpinner.getValueFactory() == null) return;

        String txt = partySizeSpinner.getEditor() == null ? "" : partySizeSpinner.getEditor().getText();
        int v;
        try { v = Integer.parseInt(txt); }
        catch (Exception e) { v = MIN_PARTY; }

        if (v < MIN_PARTY) v = MIN_PARTY;
        if (v > MAX_PARTY) v = MAX_PARTY;

        partySizeSpinner.getValueFactory().setValue(v);
    }

    /*
     * click "Show times" button initializes the first phase
     */
    @FXML
    private void onFindTimes() {
        clampPartySize();

        Integer partySize = partySizeSpinner == null ? null : partySizeSpinner.getValue();
        LocalDate date = datePicker == null ? null : datePicker.getValue();

        if (partySize == null || partySize < MIN_PARTY) { setInfo("Party size must be at least 1."); return; }
        if (partySize > MAX_PARTY) { setInfo("Party size cannot exceed 20."); return; }
        if (date == null) { setInfo("Please choose a date."); return; }
        if (date.isBefore(LocalDate.now())) { setInfo("Date cannot be in the past."); return; }

        currentPartySize = partySize;
        currentDate = date;

        if (clientController == null) { setInfo("ClientController not set."); return; }
        refreshIdentityFromController();
        setInfo("Fetching available times...");

        String userId = guestMode ? null : this.userID;
        String guestContactLocal = guestMode ? this.guestContact : null;
        if (guestMode && (guestContactLocal == null || guestContactLocal.isBlank())) {
            setInfo("Guest contact is required to create a reservation.");
            return;
        }
        if (!guestMode && (userId == null || userId.isBlank())) {
            setInfo("Subscriber ID is missing. Please log in again.");
            return;
        }
        
        clientController.requestNewReservation(
                ReservationRequestType.FIRST_PHASE,
                date,
                null,
                Integer.valueOf(partySize),
                userId,
                guestContactLocal,
                0
        );
        System.out.println("Sent FIRST_PHASE request to server");
        System.out.println("Date: " + date.toString() + ", Party size: " + partySize);
        System.out.println("Guest mode: " + guestMode + ", UserID: " + userId + ", Guest contact: " + guestContactLocal);

    }

    @FXML
    private void onBackToForm() {
        switchToStep1();
        setInfo("Pick party size + date, then choose a time block.");
    }

    /**
     * Called by DesktopScreenController
     * when the server replies to FIRST_PHASE
     */
    public void handleServerResponse(ReservationResponse resp) {
        if (resp == null) return;

        if (slotsContainer != null) slotsContainer.getChildren().clear();

        switch (resp.getType()) {
        case FIRST_PHASE_SHOW_AVAILABILITY -> {

            LocalDate effectiveDate = resp.getNewDate() != null ? resp.getNewDate() : currentDate;

            if (effectiveDate == null) {
                setInfo("No date provided for availability. Please select a date and try again.");
                switchToStep1();
                return;
            }

            // keep client state aligned with server, prevents stale/null currentDate later
            currentDate = effectiveDate;

            setInfo("Select a time for " + effectiveDate);

            addSectionHeader("Available times");
            buildTimeSlots(effectiveDate, resp.getAvailableTimes());

            if (resp.getSuggestedDates() != null && !resp.getSuggestedDates().isEmpty()) {
                addSectionHeader("Or choose a different date:");
                buildSuggestions(resp.getSuggestedDates());
            }

            switchToStep2();
        }

            case FIRST_PHASE_SHOW_SUGGESTIONS -> {
                setInfo("No exact matches found.");
                addSectionHeader("Available dates in the coming week:");
                buildSuggestions(resp.getSuggestedDates());
                switchToStep2();
            }

            case SECOND_PHASE_CONFIRMED -> {
                setInfo("Reservation confirmed. Code: " + resp.getConfirmationCode());

                if (slotsContainer != null) {
                    slotsContainer.getChildren().clear();
                }
                switchToStep1();
            }

            case FIRST_PHASE_NO_AVAILABILITY_OR_SUGGESTIONS -> {
                setInfo("Fully booked for the next 7 days");
                switchToStep1();
            }
        }
    }

    private void addSectionHeader(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("h3");
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #34495e;");
        label.setMaxWidth(Double.MAX_VALUE);
        if (slotsContainer != null) slotsContainer.getChildren().add(label);
    }

    private void buildTimeSlots(LocalDate date, List<LocalTime> times) {
        TilePane grid = createNewGrid();

        ReservationSlotsUI ui = new ReservationSlotsUI(grid, slotInfoLabel, SLOT_WIDTH, SLOT_HEIGHT);
        ui.renderAvailability(
                date,
                times,
                null,
                (d, t) -> sendSecondPhaseRequest(d, t)
        );

        if (slotsContainer != null) slotsContainer.getChildren().add(grid);
    }

    private void buildSuggestions(Map<LocalDate, List<LocalTime>> suggestions) {
        TilePane grid = createNewGrid();

        ReservationSlotsUI ui = new ReservationSlotsUI(grid, slotInfoLabel, SLOT_WIDTH, SLOT_HEIGHT);
        ui.renderSuggestions(
                suggestions,
                null,
                (d, t) -> sendSecondPhaseRequest(d, t)
        );

        if (slotsContainer != null) slotsContainer.getChildren().add(grid);
    }

    private TilePane createNewGrid() {
        TilePane tp = new TilePane();
        tp.setHgap(10);
        tp.setVgap(10);
        tp.setPrefColumns(6);
        tp.setTileAlignment(Pos.CENTER_LEFT);
        return tp;
    }

    /**
     * SECOND PHASE: User selected a specific Slot
     */
    private void sendSecondPhaseRequest(LocalDate date, LocalTime time) {
        setInfo("Confirming reservation for " + date + " at " + time + "...");
        refreshIdentityFromController();
        String userId = guestMode ? null : this.userID;
        String guestContactLocal = guestMode ? this.guestContact : null;
        if (guestMode && (guestContactLocal == null || guestContactLocal.isBlank())) {
            setInfo("Guest contact is required to confirm a reservation.");
            return;
        }
        if (!guestMode && (userId == null || userId.isBlank())) {
            setInfo("Subscriber ID is missing. Please log in again.");
            return;
        }

        
        clientController.requestNewReservation(
                ReservationRequestType.SECOND_PHASE,
                date,
                time,
                currentPartySize,
                userId,
                guestContactLocal,
                0
        );
    }
    
    private void refreshIdentityFromController() {
        if (clientController == null) {
            return;
        }
        if (overrideIdentity) {
            this.userID = overrideUserId;
            this.guestContact = overrideGuestContact;
            this.guestMode = overrideGuestContact != null && !overrideGuestContact.isBlank();
            return;
        }

        if (clientController.isGuestSession()) {
            this.guestMode = true;
            this.userID = null;
            this.guestContact = clientController.getGuestContact();
            return;
        }

        String subId = clientController.getCurrentUserId();
        String username = clientController.getCurrentUsername();

        this.guestMode = false;
        this.userID = (subId != null && !subId.isBlank()) ? subId : username;
        this.guestContact = null;
    }
    public void setReservationIdentity(String userId, String guestContact) {
        this.overrideUserId = userId == null || userId.isBlank() ? null : userId.trim();
        this.overrideGuestContact = guestContact == null || guestContact.isBlank() ? null : guestContact.trim();
        this.overrideIdentity = this.overrideUserId != null || this.overrideGuestContact != null;
        refreshIdentityFromController();
        if (overrideIdentity) {
            String label = guestMode ? "guest" : "subscriber";
            String value = guestMode ? overrideGuestContact : overrideUserId;
            setInfo("Creating reservation for " + label + ": " + value);
        }
    }


    private void switchToStep1() {
        if (step1Pane != null) { step1Pane.setVisible(true); step1Pane.setManaged(true); }
        if (step2Pane != null) { step2Pane.setVisible(false); step2Pane.setManaged(false); }
    }

    private void switchToStep2() {
        if (step1Pane != null) { step1Pane.setVisible(false); step1Pane.setManaged(false); }
        if (step2Pane != null) { step2Pane.setVisible(true); step2Pane.setManaged(true); }
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }
    
    
    
}
