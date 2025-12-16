package server;

import ocsf.server.*;

import requests.*;
import responses.*;
import serverGUI.ServerMainScreenControl;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import controllers.*;
import kyro.KryoUtil;


/**
 * All client-server communication is performed using @code byte
 * serialized and deserialized  via kryo 
 * 
 * the server handles:
 * 1.client login
 */
public class BistroEchoServer extends AbstractServer {
	
	private ServerSession serverSession;
	private final Map<ConnectionToClient,ClientSession> loggedUsers = new ConcurrentHashMap<>();
	private final ServerMainScreenControl serverGUI;
	
	//controllers
	private final UserControl userControl;
	 
	/**
	 * 
	 * @param port
	 * @param serverGUI for updating the client details in real time
	 */
	public BistroEchoServer(int port,ServerMainScreenControl serverGUI) {
		super(port);
		
		serverSession  = new ServerSession();//server details will be stored here		
		
		this.serverGUI = serverGUI;// server gui to fetch client details
		
		//controllers init
		userControl = new UserControl();
	}
	
	/**
	 * 
	 * @return object containing server metadata
	 */
	public ServerSession getCurrentSession() {
		return this.serverSession;
	}
	
	
	/**
	 * ClientSession stores the meta data of the connected client
	 * the server gui is updated using this
	 */
	@Override
	protected void clientConnected(ConnectionToClient client) {
		 String clientIP = client.getInetAddress().getHostAddress();
		 ClientSession clientSession = new ClientSession(clientIP);
		 System.out.println("Client connected from: " + clientIP);
		 loggedUsers.put(client,clientSession);
		 serverGUI.onClientConnected(clientIP);
	}
	
	
	
	@Override
	protected void clientDisconnected(ConnectionToClient client) {
		ClientSession clientSession = loggedUsers.remove(client);
		if(clientSession!=null) {
			System.out.println("Client disconnected: " + clientSession.getIp());
			serverGUI.onClientDisconnected(clientSession.getIp());
		}
	}
	
	
	/**
	 * The incoming message is expected to be a @code byte containing kyro serialized request object
	 * The outcoming message is @code byte containing kyro serialized response object
	 * 
	 * flow of events:
	 * Deserialize the incoming byte array using kryo
	 * Validate that the object is a request object
	 * Dispatch the request based on its command (enum)
	 * Invoke the appropriate controller logic
	 * Update client session and GUI state if necessary
	 * Serialize and send a Response object back to client 
	 */
	@Override
	protected void handleMessageFromClient(Object msg,ConnectionToClient client) {
		
		//the response that will be sent to client (use downcast)
		Response<?> response = null;

	    try {
	        Object decoded = msg;
	        if (msg instanceof byte[] bytes) {
	            decoded = KryoUtil.deserialize(bytes);
	        }

	        if (!(decoded instanceof Request<?> request)) {
	            response = new Response<>(false, "Invalid request type", null);
	        } else {

	            switch (request.getCommand()) {

	                case LOGIN_REQUEST -> {
	                    LoginRequest loginReq = (LoginRequest) request.getData();

	                    
	                    Response<LoginResponse> loginResp = userControl.login(loginReq);
	                    response = loginResp;

	                    
	                    if (loginResp.isSuccess()) {
	                    	ClientSession session = loggedUsers.get(client);
	                        if (session != null && loginResp.getData() != null) {

	                            String userId = loginResp.getData().getUserID();
	                            String ip = session.getIp();
	                            
	                            session.setUserId(userId);
	                           
	                            serverGUI.onClientLogin(userId,loginReq.getUsername(),loginResp.getData().getRole(),ip);	                                	                                	                                   	                                	                            
	                        }
	                    }
	                }

	                default -> response = new Response<>(false, "Unknown command", null);
	            }
	        }

	    } catch (Exception e) {
	        e.printStackTrace();
	        response = new Response<>(false, "Server error", null);
	    }

	    
	    try {
	    	// Serialize response using Kryo before sending to client
	        client.sendToClient(KryoUtil.serialize(response));
	    } catch (Exception e) {
	        System.out.println("Failed to send response to client");
	    }
	}
	
	
}
