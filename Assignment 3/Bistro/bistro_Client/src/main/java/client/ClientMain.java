package client;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import controllers.ClientController;
import main_screen.MainScreenController;

public class ClientMain {
	private static String host;
	
	private static void loadServerDetails() {
		Properties props = new Properties();
		boolean loaded = false;
		try {
		
			try (InputStream external = new FileInputStream("serverDetails.properties")){
				props.load(external);
				System.out.println("Loaded External details file for server.");
	            loaded = true;
			}catch(Exception e) {
				System.out.println("External files for client not found. Trying INTERNAL file...");
			}
			
			
			if(!loaded) {
				try(InputStream internal = ClientMain.class.getClassLoader().getResourceAsStream("serverDetails.properties")){
					if(internal !=null) {
						props.load(internal);
						System.out.println("Loaded INTERNAL details file.");
						loaded=true;
					}
				}
			}
			host = props.getProperty("hostIP").trim();
			
			if(host == null || host.isBlank()) throw new RuntimeException("Missing property: hostIP");
			 System.out.println(host);
			
		}catch(Exception e) {
			
		}
	}

    public static void main(String[] args) {

        // config connection
        //String host = "localhost";
        int port = 5555;

        // networking
        BistroEchoClient echoClient = new BistroEchoClient(host, port);

        //application controller
        ClientController controller = new ClientController(echoClient);
        echoClient.setClientController(controller);

        // connect (non-fatal)
        boolean connected = false;
        try {
            echoClient.openConnection();
            connected = true;
            System.out.println("[CLIENT] Connected to " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("[CLIENT] Connection failed: " + e.getMessage());
        }

        // launch JavaFX UI
        MainScreenController.launchUI(controller, connected);
    }
}
