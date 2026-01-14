package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import desktop_screen.DesktopScreenController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import responses.UserHistoryResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HistoryViewController implements ClientControllerAware {

    @FXML private Label infoLabel;

    @FXML private VBox discountBanner;
    @FXML private Label discountValueLabel;

    @FXML private ComboBox<String> monthFilter;

    @FXML private TableView<UserHistoryResponse> historyTable;
    @FXML private TableColumn<UserHistoryResponse, String> colDate;
    @FXML private TableColumn<UserHistoryResponse, String> colReservedFor;
    @FXML private TableColumn<UserHistoryResponse, String> colCheckIn;
    @FXML private TableColumn<UserHistoryResponse, String> colCheckOut;
    @FXML private TableColumn<UserHistoryResponse, String> colTable;
    @FXML private TableColumn<UserHistoryResponse, String> colParty;
    @FXML private TableColumn<UserHistoryResponse, String> colTotal;
    @FXML private TableColumn<UserHistoryResponse, String> colStatus;

    private final ObservableList<UserHistoryResponse> items = FXCollections.observableArrayList();

    private ClientController clientController;
    private boolean connected;
    private boolean historyRequested;

    private DesktopScreenController.Role role = DesktopScreenController.Role.GUEST;
    private DiscountInfo discountInfo = DiscountInfo.none();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private void initialize() {
        initTableColumns();

        if (historyTable != null) {
            historyTable.setItems(items);
        }

        initMonthFilter();
        applyDiscountVisibility();
        setInfo("Loading history...");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
        requestHistoryIfPossible();
    }

    public void setUserContext(DesktopScreenController.Role role, DiscountInfo discountInfo) {
        this.role = role == null ? DesktopScreenController.Role.GUEST : role;
        this.discountInfo = discountInfo == null ? DiscountInfo.none() : discountInfo;
        applyDiscountVisibility();
    }

    public void renderHistory(List<UserHistoryResponse> rows) {
        items.clear();
        if (rows != null) {
            items.addAll(rows);
        }
        setInfo(items.isEmpty() ? "No history found." : "History loaded.");
    }

    private void requestHistoryIfPossible() {
        if (historyRequested) return;
        if (clientController == null) return;
        if (!connected) return;

        historyRequested = true;
        setInfo("loading history...");
        clientController.requestUserHistory();
    }

    public void showHistoryError(String message) {
        items.clear();
        setInfo(message == null || message.isBlank() ? "failed to load history." : message);
    }

    private void initMonthFilter() {
        if (monthFilter == null) return;

        monthFilter.getItems().setAll(
                "All",
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        );
        monthFilter.getSelectionModel().select("All");

        monthFilter.valueProperty().addListener((obs, oldV, newV) -> {
            setInfo("Filter: " + (newV == null ? "All" : newV));
            // TODO: apply filtering on 'items' when you have real data
        });
    }

    private void initTableColumns() {
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

    private void applyDiscountVisibility() {
        boolean show = role == DesktopScreenController.Role.SUBSCRIBER
                && discountInfo != null
                && discountInfo.isActive();

        if (discountBanner != null) {
            discountBanner.setVisible(show);
            discountBanner.setManaged(show);
        }
        if (discountValueLabel != null) {
            discountValueLabel.setText(show ? discountInfo.getDisplayText() : "");
        }
    }

    private String formatDate(LocalDate d) {
        return d == null ? "-" : d.format(DATE_FMT);
    }

    private String formatTime(LocalTime t) {
        return t == null ? "-" : t.format(TIME_FMT);
    }

    private String formatInt(int v) {
        return v <= 0 ? "-" : String.valueOf(v);
    }

    private String formatTotal(Double total) {
        if (total == null) return "-";
        double value = total;
        if (role == DesktopScreenController.Role.SUBSCRIBER
                && discountInfo != null
                && discountInfo.isActive()
                && discountInfo.getRate() > 0) {
            value = roundMoney(total * (1.0 - discountInfo.getRate()));
        }
        return String.format("₪%.2f", value);
    }
    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String inferStatus(UserHistoryResponse r) {
        if (r == null) return "-";
        if (r.getCheckInTime() == null && r.getCheckOutTime() == null) return "Reserved";
        if (r.getCheckInTime() != null && r.getCheckOutTime() == null) return "Seated";
        if (r.getCheckInTime() != null && r.getCheckOutTime() != null) return "Completed";
        return "-";
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }

    public static final class DiscountInfo {
        private final boolean active;
        private final String displayText;
        private final double rate;

        private DiscountInfo(boolean active, String displayText, double rate) {
            this.active = active;
            this.displayText = displayText == null ? "" : displayText;
            this.rate = rate;
        }

        public static DiscountInfo none() {
        	return new DiscountInfo(false, "", 0.0);
        }

        public static DiscountInfo active(String displayText, double rate) {
            return new DiscountInfo(true, displayText, rate);
        }

        public boolean isActive() {
            return active;
        }

        public String getDisplayText() {
            return displayText;
        }
        public double getRate() {
            return rate;
        }
    }
}
