package serverGUI;

import database.DBManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.collections.*;
import server.BistroEchoServer;

public class ServerMainScreenControl {
	
	private BistroEchoServer bistroServer;
	private final ObservableList<ClientTableRow> clients =  javafx.collections.FXCollections.observableArrayList();
	
	@FXML
	private Button btnStart;
	
	@FXML
	private Label lblHostName;
	
	@FXML
	private Label lblPort;
	
	@FXML
	private Label lblHostIp;
	
	@FXML
	private Label lblStatus;
	
	@FXML
	private TableView<ClientTableRow> clientTable; 
	
	@FXML
	private TableColumn<ClientTableRow, String> colUserId;
	
	@FXML
	private TableColumn<ClientTableRow, String> colUsername;
	
	@FXML
	private TableColumn<ClientTableRow, String> colRole;
	
	@FXML
	private TableColumn<ClientTableRow, String> colIp;
	
	@FXML
	private Button btnExit;
	
	@FXML
	public void initialize() {
		colUserId .setCellValueFactory(c->c.getValue().userIdProperty());
		colUsername.setCellValueFactory(c->c.getValue().usernameProperty());
		colRole.setCellValueFactory(c->c.getValue().roleProperty());
		colIp.setCellValueFactory(c->c.getValue().ipProperty());
		
		clientTable.setItems(clients);
	}
	
	public void setServer(BistroEchoServer server) {
		this.bistroServer = server;
	}
	
	public void onClientConnected(String ip) {
		Platform.runLater(()->clients.add(new ClientTableRow(ip)));
	}
	
	public void onClientLogin(String userID,String username,String role,String ip) {
		Platform.runLater(() -> {
	        for (ClientTableRow row : clients) {
	            if (row.getIp().equals(ip)) {          
	                row.setUsername(username);
	                row.setUserId(userID);             
	                row.setRole(role);
	                return;
	            }
	        }
	        //login happened before connect event
	        ClientTableRow row = new ClientTableRow(ip);
	        row.setUserId(userID);
	        row.setUsername(username);
	        row.setRole(role);
	        clients.add(row);
	    });
	}
	
	public void onClientDisconnected(String ip) {
	    Platform.runLater(() -> {
	        System.out.println("UI disconnect ip=" + ip);	        
	        boolean removed = clients.removeIf(r -> ip != null && ip.equals(r.getIp()));	       
	        clientTable.refresh(); 
	    });
	}

	
	@FXML
	private void startServer(ActionEvent event) {
		
		if (bistroServer == null) {
	        lblStatus.setText("Server status: no server instance");
	        return;
	    }

	    try {
	    	if (bistroServer == null) {
	            lblStatus.setText("Server status: no server instance");
	            return;
	        }
	    	DBManager.init();
	        bistroServer.listen();

	        btnStart.setVisible(false);
	        lblStatus.setText("Server status: running");
	        lblHostName.setText("Host name: " + bistroServer.getCurrentSession().getHostName());
	        lblPort.setText("PORT: " + bistroServer.getPort());
	        lblHostIp.setText("Host ip: " + bistroServer.getCurrentSession().getHostIP());

	    } catch (Exception e) {
	        lblStatus.setText("Server status: failed to start");
	        e.printStackTrace();
	    }
	}
	
	@FXML
	private void stopServer(ActionEvent event) {
		try {
            if (bistroServer != null) {
            	bistroServer.stopListening();   
            	bistroServer.close();           
                System.out.println("Server stopped and closed.");
            }
        } catch (Exception e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        try {
        	bistroServer.close();
		} catch (Exception e) {
			
			System.err.println("failed to close server");
		}
        stage.close();
        Platform.exit();
        System.exit(0);
	}
	
}
