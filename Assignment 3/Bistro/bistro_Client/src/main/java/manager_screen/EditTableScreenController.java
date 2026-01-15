package manager_screen;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;
import requests.ManagerRequest;
import requests.ManagerRequest.ManagerCommand;
import requests.TableInfo;
import responses.ManagerResponse;
import responses.ManagerResponse.ManagerResponseCommand;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

public class EditTableScreenController implements ClientControllerAware {

    @FXML private Label infoLabel;
    @FXML private TextField newTableNumberField;
    @FXML private TextField newCapacityField;
    @FXML private TableView<TableRow> tablesTable;
    @FXML private TableColumn<TableRow, Integer> colTableNo;
    @FXML private TableColumn<TableRow, Integer> colSeats;
    @FXML private TableColumn<TableRow, String> colStatus;
    @FXML private TableColumn<TableRow, String> colNotes;
    @FXML private TableColumn<TableRow, Void> colEdit;
    @FXML private TableColumn<TableRow, Void> colDisable;

    private ClientController clientController;
    private boolean connected;
    private final ObservableList<TableRow> tableItems = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        if (colTableNo != null) {
            colTableNo.setCellValueFactory(new PropertyValueFactory<>("tableNumber"));
        }
        if (colSeats != null) {
        	colSeats.setEditable(true);
            colSeats.setCellValueFactory(new PropertyValueFactory<>("capacity"));
            colSeats.setCellFactory(column -> new TextFieldTableCell<>(new IntegerStringConverter()) {
                @Override
                public void startEdit() {
                    super.startEdit();
                    if (getGraphic() instanceof TextField textField) {
                        textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                            if (!newVal) {
                                commitEdit(getConverter().fromString(textField.getText()));
                            }
                        });
                    }
                }
            });
            colSeats.setOnEditCommit(event -> {
                TableRow row = event.getRowValue();
                Integer newValue = event.getNewValue();
                if (row == null) return;
                if (newValue == null || newValue <= 0) {
                    setInfo("Seats must be a positive number.");
                    row.setCapacity(event.getOldValue());
                    tablesTable.refresh();
                    return;
                }
                row.setCapacity(newValue);
            });
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        }
        if (colNotes != null) {
            colNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));
        }
        if (colEdit != null) {
            colEdit.setCellFactory(column -> new TableCell<>() {
                private final Button button = new Button("Save");

                {
                    button.getStyleClass().add("primary");
                    button.setOnAction(event -> {
                        TableRow row = getTableView().getItems().get(getIndex());
                        if (row != null) {
                            sendEditRequest(row);
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : button);
                }
            });
        }
        if (colDisable != null) {
            colDisable.setCellFactory(column -> new TableCell<>() {
                private final Button button = new Button("Disable");

                {
                    button.getStyleClass().add("ghost");
                    button.setOnAction(event -> {
                        TableRow row = getTableView().getItems().get(getIndex());
                        if (row != null) {
                            sendDisableRequest(row);
                        }
                    });
                }

                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : button);
                }
            });
        }
        if (tablesTable != null) {
            tablesTable.setItems(tableItems);
            tablesTable.setEditable(true);
        }
        initNumericField(newTableNumberField, 4);
        initNumericField(newCapacityField, 3);
        setInfo("Load tables to begin editing.");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onRefresh() {
        if (!readyForServer()) return;
        clientController.requestManagerAction(new ManagerRequest(ManagerCommand.VIEW_ALL_TABLES));
        setInfo("Fetching tables...");
    }
    
    @FXML
    private void onAddTable() {
        if (!readyForServer()) return;
        Integer tableNumber = parsePositiveInt(newTableNumberField, "Table number");
        if (tableNumber == null) return;
        Integer capacity = parsePositiveInt(newCapacityField, "Seats");
        if (capacity == null) return;

        clientController.requestManagerAction(
                new ManagerRequest(ManagerCommand.ADD_NEW_TABLE, tableNumber, capacity)
        );
        setInfo("Adding table " + tableNumber + "...");
        clearAddFields();
    }

    public void requestInitialData() {
        onRefresh();
    }

    public void handleManagerResponse(ManagerResponse response) {
        if (response == null || response.getResponseCommand() == null) return;

        if (response.getResponseCommand() == ManagerResponseCommand.SHOW_ALL_TABLES_RESPONSE) {
            tableItems.clear();
            if (response.getTables() != null) {
                for (Object item : response.getTables()) {
                    if (item instanceof TableInfo info) {
                        tableItems.add(new TableRow(info.getTableNumber(), info.getCapacity()));
                    }
                }
            }
            setInfo("Tables updated.");
        } else if (response.getResponseCommand() == ManagerResponseCommand.EDIT_TABLE_RESPONSE){
                setInfo("Table number "+response.getTable().getTableNumber()+" was edited\n Nubmer of cancelled reservations "+response.getTables().size());  // the getTables recvies contacts if later we want to show               
            onRefresh();
        }
        else if(response.getResponseCommand() == ManagerResponseCommand.NEW_TABLE_RESPONSE) {
        	setInfo("Table "+response.getTable().getTableNumber() +" added successfully");
        	onRefresh();
        }
        else if(response.getResponseCommand() == ManagerResponseCommand.DELETED_TABLE_RESPONSE) {
        	setInfo("Table disabled, number of cancelled reservations: "+response.getTables().size());
        }
    }

    private void sendEditRequest(TableRow row) {
        if (!readyForServer()) return;
        if (row.getCapacity() <= 0) {
            setInfo("Seats must be a positive number.");
            return;
        }
        clientController.requestManagerAction(new ManagerRequest(ManagerCommand.EDIT_TABLES, row.getTableNumber(), row.getCapacity()));
                        
        setInfo("Saving table " + row.getTableNumber() + "...");
    }

    private void sendDisableRequest(TableRow row) {
        if (!readyForServer()) return;
        clientController.requestManagerAction(
                new ManagerRequest(ManagerCommand.DELETE_TABLE, row.getTableNumber())
        );
        setInfo("Disabling table " + row.getTableNumber() + "...");
    }

    private boolean readyForServer() {
        if (!connected || clientController == null) {
            setInfo("Not connected to server.");
            return false;
        }
        return true;
    }
    
    private void initNumericField(TextField field, int maxLength) {
        if (field == null) return;
        field.setTextFormatter(new TextFormatter<String>(change -> {
            String newText = change.getControlNewText();
            if (!newText.matches("\\d*")) return null;
            if (newText.length() > maxLength) return null;
            return change;
        }));
    }

    private Integer parsePositiveInt(TextField field, String label) {
        if (field == null) {
            setInfo(label + " is missing.");
            return null;
        }
        String raw = field.getText() == null ? "" : field.getText().trim();
        if (raw.isEmpty()) {
            setInfo(label + " is required.");
            return null;
        }
        int value = Integer.parseInt(raw);
        if (value <= 0) {
            setInfo(label + " must be a positive number.");
            return null;
        }
        return value;
    }

    private void clearAddFields() {
        if (newTableNumberField != null) newTableNumberField.clear();
        if (newCapacityField != null) newCapacityField.clear();
    }


    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg == null ? "" : msg);
    }

    public static class TableRow {
        private int tableNumber;
        private int capacity;
        private String status;
        private String notes;

        public TableRow(int tableNumber, int capacity) {
            this.tableNumber = tableNumber;
            this.capacity = capacity;
            this.status = "Active";
            this.notes = "";
        }

        public int getTableNumber() {
            return tableNumber;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public String getStatus() {
            return status;
        }

        public String getNotes() {
            return notes;
        }
    }
}