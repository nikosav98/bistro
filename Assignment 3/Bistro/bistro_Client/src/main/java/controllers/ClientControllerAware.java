package controllers;

public interface ClientControllerAware {
    void setClientController(ClientController controller, boolean connected);
}