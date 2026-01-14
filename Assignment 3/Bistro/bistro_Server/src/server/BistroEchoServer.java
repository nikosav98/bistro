package server;

import ocsf.server.*;

import requests.*;
import responses.*;
import serverGUI.ServerMainScreenControl;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import database.SeatingDAO;
import database.ReservationDAO;
import database.UserDAO;
import database.OpeningHoursDAO;

import controllers.NotificationControl;
import controllers.WaitingListControl;

import controllers.*;
import kryo.KryoUtil;


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
	private final ReservationControl reservationControl;
	private final SeatingControl seatingControl;
	private final ManagementControl managerControl;
	private final BillingControl billingControl;
	private final ReportControl reportControl;
	private final WaitingListControl waitingListControl;
	// scheduler
	private final BillingScheduler billingScheduler;

	 
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
		reservationControl = new ReservationControl();
		seatingControl = new SeatingControl();
		managerControl = new ManagementControl();
		billingControl = new BillingControl();
		reportControl = new ReportControl();
		waitingListControl=new WaitingListControl();
		billingScheduler = new BillingScheduler(
		        new SeatingDAO(),
		        billingControl,
		        new ReservationDAO(),
		        new UserDAO(),
		        new NotificationControl(),
		        reportControl,
		        waitingListControl,
		        new OpeningHoursDAO()
		);
	}
	@Override
	protected void serverStarted() {
	    System.out.println("SERVER started listening on port " + getPort());
	    billingScheduler.start(); 
	}
	@Override
	protected void serverStopped() {
	    System.out.println("SERVER stopped listening");
	    billingScheduler.stop();
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
		System.out.println("SERVER clientDisconnected fired!");
		ClientSession clientSession = loggedUsers.remove(client);
		if(clientSession!=null) {
			System.out.println("Client disconnected: " + clientSession.getIp());
			serverGUI.onClientDisconnected(clientSession.getIp());
		}
	}
	
	@Override
	protected void clientException(ConnectionToClient client, Throwable exception) {
	    System.out.println("Client exception (treat as disconnect): " + exception.getMessage());
	    clientDisconnected(client); // cleanup + GUI update
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
		ClientSession session = loggedUsers.get(client);//session holds the user info
		
	    try {
	        Object decoded = msg;
	        if (msg instanceof byte[] bytes)  decoded = KryoUtil.deserialize(bytes);	
	        System.out.println("SERVER decoded request ");
	                

	        if (!(decoded instanceof Request<?> request)) {response = new Response<>(false, "Invalid request type", null);}
	            
	         else {

	            switch (request.getCommand()) {

	                case USER_REQUEST -> {
	                	LoginRequest loginReq = (LoginRequest) request.getData();
	                    Response<LoginResponse> loginResp = userControl.handleUserRequest(loginReq);
	                    response = loginResp;
	                    handleLoginSuccess(client, loginReq, loginResp);
	                    if (loginReq != null && loginReq.getUserCommand() == LoginRequest.UserCommand.LOGIN_REQUEST) {
	                        if (session == null) {
	                            System.out.println("session=null");
	                        } else if (session.getUserId() == null || session.getUserId().isBlank()) {
	                            System.out.println("session userId=<unset>");
	                        } else {
	                            System.out.println("session userId=" + session.getUserId());
	                        }
	                    }
	                    
	                }
	                case RESERVATION_REQUEST->{
	                	ReservationRequest reservationReq = (ReservationRequest) request.getData();
	                   
	                     session = loggedUsers.get(client);
	                	if (session != null) {
	                		String existingUserId = reservationReq.getUserID();
	                		String existingContact = reservationReq.getGuestContact();
	                		boolean hasIdentity = (existingUserId != null && !existingUserId.isBlank())
	                				|| (existingContact != null && !existingContact.isBlank());
	                		if (!hasIdentity) {
	                			String sessionUserId = session.getUserId();
	                			if (sessionUserId != null && !sessionUserId.isBlank()) {
	                				reservationReq.setUserID(sessionUserId);
	                			}
	                		}
	                	}
	                    Response<ReservationResponse> reservationResp =
	                            reservationControl.handleReservationRequest(reservationReq);
	                    response = reservationResp;
	                }
	                case SEATING_REQUEST ->{
	                	SeatingRequest seatingReq = (SeatingRequest) request.getData();
	                	Response<SeatingResponse> seatingResp = seatingControl.handleSeatingRequest(seatingReq);
	                	response = seatingResp;
	                }
	                case MANAGER_REQUEST ->{
	                	if(!loggedUsers.get(client).getRole().equals("REPRESENTATIVE") && !loggedUsers.get(client).getRole().equals("MANAGER") ) {
	                		response = new Response<>(false,"No premissions",null);
	                		break;
	                	}
	                	ManagerRequest managerReq = (ManagerRequest)request.getData();
	                	Response<ManagerResponse> managerResp = managerControl.handleManagerRequest(managerReq);
	                	response = managerResp;	                	
	                }
	                case BILLING_REQUEST ->{
	                	BillRequest billReq = (BillRequest)request.getData();
	                	Response<BillResponse> billResp = billingControl.handleBillRequest(billReq);
	                	response = billResp;
	                }
	                case REPORT_REQUEST->{
	                	if(!loggedUsers.get(client).getRole().equals("MANAGER") ) {
	                		response = new Response<>(false,"No premissions",null);
	                		break;
	                	}
	                	ReportRequest repReq = (ReportRequest)request.getData();	                	
	                	Response<ReportResponse> repResp = reportControl.handleReportRequest(repReq);
	                	response= repResp;
	                }
	                case GUEST_REQUEST->{
	                	GuestRequest guestReq = (GuestRequest)request.getData();
	                	loggedUsers.get(client).setRole("GUEST");
	                	loggedUsers.get(client).setUsername(guestReq.getContact());
	                	response = new Response<>(true,"Welcome to McDickies",null);
	                }
	                case LOST_CODE-> {
	                	String contact = (String)request.getData();
	                	response = reservationControl.retrieveConfirmationCode(contact);
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
	
	
	/**
	 * method to update the client session 
	 * @param client current client that logged in
	 * @param logReq the deatils the client entered to login
	 * @param logRes the response that was handled by UserControl
	 */
	private void handleLoginSuccess(ConnectionToClient client,LoginRequest logReq, Response<LoginResponse> logRes) {
		if(!logRes.isSuccess() || logRes.getData() == null) return;
		
		ClientSession clientSession = loggedUsers.get(client);
		if(client == null) return;
		
		String userId = logRes.getData().getUserID();
		clientSession.setUserId(userId);
		clientSession.setUsername(logRes.getData().getUsername());
		clientSession.setRole(logRes.getData().getRole());		
		serverGUI.onClientLogin(userId,clientSession.getUsername(),clientSession.getRole(),clientSession.getIp());
		System.out.println("[LOGIN SUCCESS] role=" + logRes.getData().getRole()
		        + " userId=" + logRes.getData().getUserID()
		        + " username=" + logRes.getData().getUsername());
	}
	
	
}
