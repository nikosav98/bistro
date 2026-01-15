package desktop_views;

import controllers.ClientController;
import controllers.ClientControllerAware;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class AnalyticsViewController implements ClientControllerAware {

    @FXML private Label infoLabel;

    private ClientController clientController;
    private boolean connected;

    @FXML
    private void initialize() {
        setInfo("Analytics dashboard (demo).");
    }

    @Override
    public void setClientController(ClientController controller, boolean connected) {
        this.clientController = controller;
        this.connected = connected;
    }

    private void setInfo(String msg) {
        if (infoLabel != null) infoLabel.setText(msg);
    }
}
