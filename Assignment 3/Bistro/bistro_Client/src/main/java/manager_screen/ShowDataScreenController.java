package manager_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import requests.ManagerRequest;
import requests.ManagerRequest.ManagerCommand;
import responses.CurrentSeatingResponse;
import responses.LoginResponse;
import responses.ManagerResponse;
import responses.ManagerResponse.ManagerResponseCommand;
import responses.WaitingListResponse;

import java.time.LocalDateTime;

public class ShowDataScreenController implements ClientControllerAware {

    

    @FXML private TableView<WaitingListResponse> waitingTable;
    @FXML private TableColumn<WaitingListResponse, String> colWaitingPriority;
    @FXML private TableColumn<WaitingListResponse, LocalDateTime> colWaitingTime;
    @FXML private TableColumn<WaitingListResponse, String> colWaitingContact;

    @FXML private TableView<SeatingRow> seatingTable;
    @FXML private TableColumn<SeatingRow, Integer> colSeatingTable;
    @FXML private TableColumn<SeatingRow, Integer> colSeatingParty;
    @FXML private TableColumn<SeatingRow, LocalDateTime> colSeatingCheckIn;
    @FXML private TableColumn<SeatingRow, Integer> colSeatingCode;
    @FXML private TableColumn<SeatingRow, String> colSeatingType;

    @FXML private Label infoLabel;

    private ClientController clientController;
    private boolean connected;

    private final javafx.collections.ObservableList<WaitingListResponse> waitingItems = javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.ObservableList<SeatingRow> seatingItems = javafx.collections.FXCollections.observableArrayList();

    @FXML
    private void initialize() {
       
        if (colWaitingPriority != null) colWaitingPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
        if (colWaitingTime != null) colWaitingTime.setCellValueFactory(new PropertyValueFactory<>("dateTime"));
        if (colWaitingContact != null) colWaitingContact.setCellValueFactory(new PropertyValueFactory<>("contact"));
        if (waitingTable != null) waitingTable.setItems(waitingItems);

        if (colSeatingTable != null) colSeatingTable.setCellValueFactory(new PropertyValueFactory<>("tableNumber"));
        if (colSeatingParty != null) colSeatingParty.setCellValueFactory(new PropertyValueFactory<>("partySize"));
        if (colSeatingCheckIn != null) colSeatingCheckIn.setCellValueFactory(new PropertyValueFactory<>("checkInTime"));
        if (colSeatingCode != null) colSeatingCode.setCellValueFactory(new PropertyValueFactory<>("confirmationCode"));
        if (colSeatingType != null) colSeatingType.setCellValueFactory(new PropertyValueFactory<>("type"));
        if (seatingTable != null) seatingTable.setItems(seatingItems);
        

        setInfo("Load manager data to view waiting list and seating.");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onRefresh() {
        requestAll();
    }

    public void requestInitialData() {
        requestAll();
    }

    public void handleManagerResponse(ManagerResponse response) {
        if (response == null || response.getResponseCommand() == null) return;

        ManagerResponseCommand command = response.getResponseCommand();

        if (command == ManagerResponseCommand.CURRENT_WAITING_LIST_RESPONSE) {
            waitingItems.clear();
            if (response.getTables() != null) {
                for (Object item : response.getTables()) {
                    if (item instanceof WaitingListResponse waiting) {
                        waitingItems.add(waiting);
                    }
                }
            }
            setInfo("Waiting list updated.");
        }

        else if (command == ManagerResponseCommand.VIEW_CURRENT_SEATING_RESPONSE) {
            seatingItems.clear();
            if (response.getTables() != null) {
                for (Object item : response.getTables()) {
                    if (item instanceof CurrentSeatingResponse seating) {
                        seatingItems.add(SeatingRow.from(seating));
                    }
                }
            }
            setInfo("Current seating updated.");
        }
    }

    private void requestAll() {
        if (!readyForServer()) return;

        
        clientController.requestManagerAction(new ManagerRequest(ManagerCommand.VIEW_WAITING_LIST));
        clientController.requestManagerAction(new ManagerRequest(ManagerCommand.VIEW_CURRENT_SEATING));

        setInfo("Refreshing manager data...");
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

    public static class SeatingRow {
        private final int tableNumber;
        private final int partySize;
        private final LocalDateTime checkInTime;
        private final int confirmationCode;
        private final String type;

        private SeatingRow(int tableNumber, int partySize, LocalDateTime checkInTime, int confirmationCode, String type) {
            this.tableNumber = tableNumber;
            this.partySize = partySize;
            this.checkInTime = checkInTime;
            this.confirmationCode = confirmationCode;
            this.type = type;
        }

        public static SeatingRow from(CurrentSeatingResponse seating) {
            int tableNumber = seating.getTable() == null ? 0 : seating.getTable().getTableNumber();
            String type = seating.getUserID() == null || seating.getUserID().isBlank() ? "Guest" : "Subscriber";
            return new SeatingRow(
                    tableNumber,
                    seating.getPartySize(),
                    seating.getCheckInTime(),
                    seating.getConfrimationCode(),
                    type
            );
        }

        public int getTableNumber() {
            return tableNumber;
        }

        public int getPartySize() {
            return partySize;
        }

        public LocalDateTime getCheckInTime() {
            return checkInTime;
        }

        public int getConfirmationCode() {
            return confirmationCode;
        }

        public String getType() {
            return type;
        }
    }
}
