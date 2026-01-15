package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import requests.ManagerRequest;
import requests.ManagerRequest.ManagerCommand;
import requests.TableInfo;
import responses.ManagerResponse;
import responses.ManagerResponse.ManagerResponseCommand;

public class TablesViewController implements ClientControllerAware {

    @FXML private Label infoLabel;
    @FXML private TableView<TableInfo> tablesTable;
    @FXML private TableColumn<TableInfo, Integer> colTableNo;
    @FXML private TableColumn<TableInfo, Integer> colSeats;
    @FXML private TextField tableNumberField;
    @FXML private TextField capacityField;

    private ClientController clientController;
    private boolean connected;
    private final ObservableList<TableInfo> tableItems = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setInfo("Tables overview (demo).");
        setInfo("Tables overview.");
        if (colTableNo != null) {
            colTableNo.setCellValueFactory(new PropertyValueFactory<>("tableNumber"));
        }
        if (colSeats != null) {
            colSeats.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        }
        if (tablesTable != null) {
            tablesTable.setItems(tableItems);
        }
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    @FXML
    private void onRefreshTables() {
        setInfo("Refreshing tables (placeholder).");
        if (!readyForServer()) return;
        clientController.requestManagerAction(new ManagerRequest(ManagerCommand.VIEW_ALL_TABLES, 0));
        setInfo("Fetching tables...");
    }

    @FXML
    private void onAddTable() {
        Integer tableNumber = parseInt(tableNumberField, "Table number is required.");
        Integer capacity = parseInt(capacityField, "Capacity is required.");
        if (tableNumber == null || capacity == null) return;
        if (!readyForServer()) return;

        clientController.requestManagerAction(
                new ManagerRequest(ManagerCommand.ADD_NEW_TABLE, tableNumber, capacity)
        );
        setInfo("Adding table " + tableNumber + "...");
    }

    @FXML
    private void onEditTable() {
        Integer tableNumber = parseInt(tableNumberField, "Table number is required.");
        Integer capacity = parseInt(capacityField, "New capacity is required.");
        if (tableNumber == null || capacity == null) return;
        if (!readyForServer()) return;

        clientController.requestManagerAction(
                new ManagerRequest(ManagerCommand.EDIT_TABLES, tableNumber, capacity)
        );
        setInfo("Updating table " + tableNumber + "...");
    }

    @FXML
    private void onRemoveTable() {
        Integer tableNumber = parseInt(tableNumberField, "Table number is required.");
        if (tableNumber == null) return;
        if (!readyForServer()) return;

        clientController.requestManagerAction(
                new ManagerRequest(ManagerCommand.DELETE_TABLE, tableNumber)
        );
        setInfo("Removing table " + tableNumber + "...");
    }

    public void handleManagerResponse(ManagerResponse response) {
        if (response == null) return;
        ManagerResponseCommand command = response.getResponseCommand();
        if (command == null) return;

        switch (command) {
            case SHOW_ALL_TABLES_RESPONSE -> {
                tableItems.clear();
                if (response.getTables() != null) {
                    for (Object item : response.getTables()) {
                        if (item instanceof TableInfo info) {
                            tableItems.add(info);
                        }
                    }
                }
                setInfo("Tables updated.");
            }
            case NEW_TABLE_RESPONSE, EDIT_TABLE_RESPONSE, DELETED_TABLE_RESPONSE -> {
                onRefreshTables();
            }
            default -> {
            }
        }
    }

    private Integer parseInt(TextField field, String emptyMessage) {
        if (field == null) return null;
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            setInfo(emptyMessage);
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            setInfo("Must be a number.");
            return null;
        }
    }

    private boolean readyForServer() {
        if (!connected || clientController == null) {
            setInfo("Not connected to server.");
            return false;
        }
        return true;
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg);
    }
}