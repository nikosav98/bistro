package server;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import serverGUI.*;

public class ServerMain extends Application {

    
	@Override
    public void start(Stage stage) throws Exception {

        // Load FXML
        FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/serverGUI/ServerMainScreen.fxml"));

        Scene scene = new Scene(loader.load());

        
        ServerMainScreenControl controller = loader.getController();

     
        int port = 5555;
        BistroEchoServer server = new BistroEchoServer(port, controller);

        
        controller.setServer(server);

        // Show UI
        stage.setTitle("Bistro Server");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
