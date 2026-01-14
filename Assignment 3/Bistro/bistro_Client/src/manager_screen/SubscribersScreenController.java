package manager_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import requests.ManagerRequest;
import requests.ManagerRequest.ManagerCommand;
import responses.LoginResponse;
import responses.ManagerResponse;
import responses.ManagerResponse.ManagerResponseCommand;
import responses.UserHistoryResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SubscribersScreenController implements ClientControllerAware {

    @FXML private TableView<LoginResponse> subscribersTable;
    @FXML private TableColumn<LoginResponse, String> colSubscriberId;
    @FXML private TableColumn<LoginResponse, String> colSubscriberName;
    @FXML private TableColumn<LoginResponse, String> colSubscriberPhone;
    @FXML private TableColumn<LoginResponse, String> colSubscriberEmail;
    @FXML private TableColumn<LoginResponse, Void> colSubscriberHistory;

    @FXML private TableView<UserHistoryResponse> historyTable;
    @FXML private TableColumn<UserHistoryResponse, String> colDate;
    @FXML private TableColumn<UserHistoryResponse, String> colReservedFor;
    @FXML private TableColumn<UserHistoryResponse, String> colCheckIn;
    @FXML private TableColumn<UserHistoryResponse, String> colCheckOut;
    @FXML private TableColumn<UserHistoryResponse, String> colTable;
    @FXML private TableColumn<UserHistoryResponse, String> colParty;
    @FXML private TableColumn<UserHistoryResponse, String> colTotal;
    @FXML private TableColumn<UserHistoryResponse, String> colStatus;

    @FXML private Label historyTargetLabel;
    @FXML private Label infoLabel;

    private final ObservableList<LoginResponse> subscriberItems = FXCollections.observableArrayList();
    private final ObservableList<UserHistoryResponse> historyItems = FXCollections.observableArrayList();

    private ClientController clientController;
    private boolean connected;
    private String requestedUsername;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private void initialize() {
        if (colSubscriberId != null) colSubscriberId.setCellValueFactory(new PropertyValueFactory<>("userID"));
        if (colSubscriberName != null) colSubscriberName.setCellValueFactory(new PropertyValueFactory<>("username"));
        if (colSubscriberPhone != null) colSubscriberPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));
        if (colSubscriberEmail != null) colSubscriberEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        if (subscribersTable != null) subscribersTable.setItems(subscriberItems);

        if (colSubscriberHistory != null) {
            colSubscriberHistory.setCellFactory(col -> new TableCell<>() {
                private final Button button = new Button("View History");

                {
                    button.getStyleClass().add("ghost");
                    button.setOnAction(event -> {
                        LoginResponse subscriber = getTableView().getItems().get(getIndex());
                        requestHistory(subscriber);
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : button);
                }
            });
        }

        initHistoryColumns();
        if (historyTable != null) historyTable.setItems(historyItems);

        setHistoryTarget(null);
        setInfo("Load subscribers to view history.");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
        if (connected) {
            requestSubscribers();
        }
    }

    @FXML
    private void onRefresh() {
        requestSubscribers();
    }

    public void requestInitialData() {
        requestSubscribers();
    }

    public void handleManagerResponse(ManagerResponse response) {
        if (response == null || response.getResponseCommand() == null) return;

        if (response.getResponseCommand() == ManagerResponseCommand.ALL_SUBSCRIBERS_RESPONSE) {
            subscriberItems.clear();
            if (response.getTables() != null) {
                for (Object item : response.getTables()) {
                    if (item instanceof LoginResponse subscriber) {
                        subscriberItems.add(subscriber);
                    }
                }
            }
            setInfo("Subscribers updated.");
        }
    }

    public void renderHistory(List<UserHistoryResponse> rows) {
        historyItems.clear();
        if (rows != null) {
            historyItems.addAll(rows);
        }
        String name = requestedUsername == null ? "subscriber" : requestedUsername;
        setInfo(historyItems.isEmpty() ? "No history found for " + name + "." : "History loaded for " + name + ".");
    }

    public void showHistoryError(String message) {
        historyItems.clear();
        setInfo(message == null || message.isBlank() ? "Failed to load history." : message);
    }

    private void requestSubscribers() {
        if (!readyForServer()) return;
        clientController.requestManagerAction(new ManagerRequest(ManagerCommand.VIEW_SUBSCRIBERS));
        setInfo("Refreshing subscribers...");
    }

    private void requestHistory(LoginResponse subscriber) {
        if (subscriber == null) {
            setInfo("Select a subscriber first.");
            return;
        }

        String username = subscriber.getUsername();
        username = username == null ? null : username.trim();
        if (username == null || username.isEmpty()) {
            setInfo("Subscriber username is missing.");
            return;
        }

        requestedUsername = username;
        setHistoryTarget(username);
        historyItems.clear();
        setInfo("Loading history for " + username + "...");

        if (!readyForServer()) return;
        clientController.requestUserHistoryForSubscriber(username);
    }

    private boolean readyForServer() {
        if (!connected || clientController == null) {
            setInfo("Not connected to server.");
            return false;
        }
        return true;
    }

    private void setHistoryTarget(String username) {
        if (historyTargetLabel != null) {
            if (username == null || username.isBlank()) {
                historyTargetLabel.setText("Select a subscriber");
            } else {
                historyTargetLabel.setText("for " + username);
            }
        }
    }

    private void initHistoryColumns() {
        if (colDate != null) {
            colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatDate(cd.getValue() == null ? null : cd.getValue().getReservationDate())
            ));
        }
        if (colReservedFor != null) {
            colReservedFor.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatTime(cd.getValue() == null ? null : cd.getValue().getReservedForTime())
            ));
        }
        if (colCheckIn != null) {
            colCheckIn.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatTime(cd.getValue() == null ? null : cd.getValue().getCheckInTime())
            ));
        }
        if (colCheckOut != null) {
            colCheckOut.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatTime(cd.getValue() == null ? null : cd.getValue().getCheckOutTime())
            ));
        }
        if (colTable != null) {
            colTable.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatInt(cd.getValue() == null ? 0 : cd.getValue().getTableNumber())
            ));
        }
        if (colParty != null) {
            colParty.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatInt(cd.getValue() == null ? 0 : cd.getValue().getPartySize())
            ));
        }
        if (colTotal != null) {
            colTotal.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatTotal(cd.getValue() == null ? null : cd.getValue().getTotalPrice())
            ));
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(inferStatus(cd.getValue())));
        }
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE_FMT);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "-" : time.format(TIME_FMT);
    }

    private String formatInt(int value) {
        return value <= 0 ? "-" : String.valueOf(value);
    }

    private String formatTotal(Double total) {
        if (total == null) return "-";
        return String.format("₪%.2f", total);
    }

    private String inferStatus(UserHistoryResponse response) {
        if (response == null) return "-";
        if (response.getCheckInTime() == null && response.getCheckOutTime() == null) return "Reserved";
        if (response.getCheckInTime() != null && response.getCheckOutTime() == null) return "Seated";
        if (response.getCheckInTime() != null && response.getCheckOutTime() != null) return "Completed";
        return "-";
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }
}