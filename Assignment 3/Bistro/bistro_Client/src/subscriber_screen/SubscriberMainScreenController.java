package subscriber_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import controllers.ClientUIHandler;
import desktop_screen.DesktopScreenController;
import desktop_views.ReservationsViewController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import responses.ManagerResponse;
import responses.ReservationResponse;
import responses.SeatingResponse;
import requests.ReservationRequest.ReservationRequestType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public class SubscriberMainScreenController implements ClientControllerAware, ClientUIHandler {

    @FXML private TableView<ReservationResponse> reservationsTable;
    @FXML private TableColumn<ReservationResponse, String> colDate;
    @FXML private TableColumn<ReservationResponse, String> colStartTime;
    @FXML private TableColumn<ReservationResponse, String> colPartySize;
    @FXML private TableColumn<ReservationResponse, String> colConfirmationCode;
    @FXML private TableColumn<ReservationResponse, Void> colEdit;
    @FXML private TableColumn<ReservationResponse, Void> colCancel;
    @FXML private Label infoLabel;
   

    private final ObservableList<ReservationResponse> reservations = FXCollections.observableArrayList();
    
    private ClientController clientController;
    private boolean connected;
    private Runnable onLogout;
    private boolean requested;
    private ReservationsViewController reservationsViewController;
    private Consumer<ReservationResponse> onEditReservation;
    @FXML private Label qrLabel;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private void initialize() {
        if (colDate != null) 
            colDate.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatDate(cd.getValue() == null ? null : cd.getValue().getNewDate())));
                                       
        if (colStartTime != null) 
            colStartTime.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatTime(cd.getValue() == null ? null : cd.getValue().getNewTime())));
                    
            
        
        if (colPartySize != null) colPartySize.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatInt(cd.getValue() == null ? 0 : cd.getValue().getNewPartySize())));
                                           
        
        if (colConfirmationCode != null) {
            colConfirmationCode.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                    formatInt(cd.getValue() == null ? 0 : cd.getValue().getConfirmationCode())
            ));
        }
        if (colEdit != null) {
            colEdit.setCellFactory(col -> new TableCell<>() {
                private final Button editButton = buildActionButton("Edit");

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : editButton);
                }

                private Button buildActionButton(String label) {
                    Button button = new Button(label);
                    button.getStyleClass().add("ghost");
                    button.setOnAction(event -> {
                        ReservationResponse row = getRowReservation();
                        if (row == null) return;
                        handleEditReservation(row);
                    });
                    return button;
                }

                private ReservationResponse getRowReservation() {
                    int index = getIndex();
                    if (index < 0 || index >= getTableView().getItems().size()) return null;
                    return getTableView().getItems().get(index);
                }
            });
        }
        if (colCancel != null) {
            colCancel.setCellFactory(col -> new TableCell<>() {
                private final Button cancelButton = buildCancelButton();

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : cancelButton);
                }

                private Button buildCancelButton() {
                    Button button = new Button("Cancel");
                    button.getStyleClass().add("ghost");
                    button.setOnAction(event -> {
                        ReservationResponse row = getRowReservation();
                        if (row == null) return;
                        handleCancelReservation(row);
                    });
                    return button;
                }

                private ReservationResponse getRowReservation() {
                    int index = getIndex();
                    if (index < 0 || index >= getTableView().getItems().size()) return null;
                    return getTableView().getItems().get(index);
                }
            });
        }
        if (reservationsTable != null) {
            reservationsTable.setItems(reservations);
        }
        
        setInfo("Loading upcoming reservations...");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
        updateQrLabel();
        if (connected && !requested) {
            requested = true;
            requestUpcomingReservations();
        }
    }

    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public void setOnEditReservation(Consumer<ReservationResponse> onEditReservation) {
        this.onEditReservation = onEditReservation;
    }

    @FXML
    private void onRefresh() {
        requestUpcomingReservations();
    }

    @FXML
    private void onShowQr() {
        updateQrLabel();
        showQrPopup();
    }

    

    @FXML
    private void onLogout() {
        if (clientController != null) {
            clientController.logout();
        }
        if (onLogout != null) {
            onLogout.run();
        }
    }

    private void requestUpcomingReservations() {
        if (!connected || clientController == null) {
            setInfo("Not connected to server.");
            return;
        }
        setInfo("Loading upcoming reservations...");
        clientController.requestUpcomingReservations();
    }

    private void loadReservationsView() {
        
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/desktop_views/ReservationsView.fxml"));
            loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof ReservationsViewController rvc) {
                reservationsViewController = rvc;
                reservationsViewController.setClientController(clientController, connected);
            }
        } catch (Exception e) {
            setInfo("Failed to load reservations screen.");
            e.printStackTrace();
        }
    }
    
    private void updateQrLabel() {
        if (qrLabel == null) return;
        String userId = clientController == null ? null : clientController.getCurrentUserId();
        qrLabel.setText("User ID: " + (userId == null || userId.isBlank() ? "-" : userId));
    }

    private void showQrPopup() {
        String userId = clientController == null ? null : clientController.getCurrentUserId();
        String displayId = userId == null || userId.isBlank() ? "-" : userId;

        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("QR Code");
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);

        javafx.scene.control.Label label = new javafx.scene.control.Label(displayId);
        label.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12, label);
        content.setStyle("-fx-alignment: center; -fx-padding: 20;");
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    @Override
    public void showInfo(String title, String message) {
        setInfo(message);
    }
    
    @Override
    public void showWarning(String title, String message) {
        setInfo(message);
    }

    @Override
    public void showError(String title, String message) {
        setInfo(message);
    }

    @Override
    public void showPayload(Object payload) {
        setInfo(String.valueOf(payload));
    }

    @Override
    public void routeToDesktop(DesktopScreenController.Role role, String username) {
        // already in subscriber screen
    }
    @Override
    public void onReportResponse(responses.ReportResponse reportResponse) {
        // Subscriber home screen doesn't display reports
    }

    @Override
    public void onReservationResponse(ReservationResponse response) {
        if (reservationsViewController != null) {
            reservationsViewController.handleServerResponse(response);
        }
        if (response != null) {
            handleReservationResponse(response);
        }
    }

    @Override
    public void onSeatingResponse(SeatingResponse response) {
        // not used here
    }

    @Override
    public void onUserHistoryResponse(List<responses.UserHistoryResponse> rows) {
        // not used here
    }

    @Override
    public void onUserHistoryError(String message) {
        // not used here
    }

    @Override
    public void onUpcomingReservationsResponse(List<ReservationResponse> rows) {
        reservations.clear();
        if (rows != null) {
            reservations.addAll(rows);
        }
        setInfo(reservations.isEmpty() ? "No upcoming reservations." : "Upcoming reservations loaded.");
    }

    @Override
    public void onUpcomingReservationsError(String message) {
        reservations.clear();
        setInfo(message == null || message.isBlank() ? "Failed to load upcoming reservations." : message);
    }

    @Override
    public void onManagerResponse(ManagerResponse response) {
        // not used here
    }

    @Override
    public void onBillTotal(double baseTotal, boolean isCash) {
        // not used here
    }

    @Override
    public void onBillPaid(Integer tableNumber) {
        // not used here
    }

    @Override
    public void onBillError(String message) {
        // not used here
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

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }
    
    private void handleEditReservation(ReservationResponse row) {
        if (row == null) return;
        if (onEditReservation != null) {
            onEditReservation.accept(row);
        } else {
            setInfo("Edit screen unavailable.");
        }
    }

    private void handleCancelReservation(ReservationResponse row) {
        if (row == null) return;
        if (clientController == null || !connected) {
            setInfo("Not connected to server.");
            return;
        }
        Integer confirmationCode = row.getConfirmationCode();
        if (confirmationCode == null || confirmationCode <= 0) {
            setInfo("Missing confirmation code.");
            return;
        }
        setInfo("Cancelling reservation " + confirmationCode + "...");
        clientController.requestNewReservation(
                ReservationRequestType.CANCEL_RESERVATION,
                null,
                null,
                0,
                null,
                null,
                confirmationCode
        );
    }

    public void handleReservationResponse(ReservationResponse response) {
        if (response == null) return;
        ReservationResponse.ReservationResponseType type = response.getType();
        if (type == null) return;
        switch (type) {
            case CANCEL_RESERVATION -> {
                Integer code = response.getConfirmationCode();
                if (code != null) {
                    reservations.removeIf(row -> code.equals(row.getConfirmationCode()));
                }
                setInfo("Reservation cancelled.");
            }
            case EDIT_RESERVATION -> {
                setInfo("Reservation updated.");
                requestUpcomingReservations();
            }
            default -> {
            }
        }
    }
}