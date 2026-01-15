package client;

import controllers.ClientController;
import ocsf.client.AbstractClient;

/**
 * BistroEchoClient
 *
 * Thin OCSF client responsible ONLY for network communication.
 * This class must remain logic-free and UI-free.
 */
public class BistroEchoClient extends AbstractClient {

    private ClientController controller;

    public BistroEchoClient(String host, int port) {
        super(host, port);
    }

    // set up controller
    public void setClientController(ClientController controller) {
        this.controller = controller;
    }

    /**
     * Called automatically by OCSF when a message arrives from the server.
     * Simply forwards the message to ClientController.
     */
    // Server -> BistroEchoClient -> ClientController -> ClientUIHandler -> show alert / swap screen / update UI
    @Override
    protected void handleMessageFromServer(Object msg) {
        if (controller != null) {
            controller.handleServerResponse(msg);
        } else {
            System.err.println("[BistroEchoClient] No ClientController set");
        }
    }

    // lifecycle hooks

    @Override
    protected void connectionEstablished() {
        System.out.println("[BistroEchoClient] Connected to server");
    }

    @Override
    protected void connectionClosed() {
        System.out.println("[BistroEchoClient] Connection closed");
        if (controller != null) {
            controller.handleConnectionLost("Connection to server was closed.");
        }
    }

    @Override
    protected void connectionException(Exception exception) {
        System.err.println("[BistroEchoClient] Connection error: " + exception.getMessage());
        if (controller != null) {
            controller.handleConnectionLost("Connection error: " + exception.getMessage());
        }
    }
    
    
}
