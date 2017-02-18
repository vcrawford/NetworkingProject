import java.net.*; //classes related to sockets
import java.io.*;
import java.util.*; //HashMap

/**
 * Represents the peer that is running on this process
 */
public class PeerProcess {

	private int peerid; // the id of this peer
	private int listenport; // port number this peer listens on
	private ServerSocket listener; // socket this peer listens on
	HashMap<Integer, NeighborPeer> neighbors = new HashMap<Integer, NeighborPeer>(); //peerid to peer
	private Boolean finished = false; // whether the peer sharing process is
										// finished
	private static final String commonCfgFileName = "Common.cfg";
	private static final String peerInfoFileName = "PeerInfo.cfg";

	// variables set in config file
	private int NumberOfPreferredNeighbors;
	private int UnchokingInterval;
	private int OptimisticUnchokingInterval;
	private String FileName;
	private int FileSize;
	private int PieceSize;
	
	private ArrayList<NeighborPeer> prefNbr; //preferred neighbors
	private ArrayList<NeighborPeer> waitNbr; //non-preferrend or waiting neighbors 

	/**
	 * Main function: Starting point
	 */		
	public static void main(String[] args) throws Exception {
		int id = Integer.parseInt(args[0]);
		
		// Create and initialize this peer's instance
		PeerProcess p = new PeerProcess(id);

		// Start thread for handshaking each neighbor
		p.handshakeNeighbors();
		
		p.listenForConnection();	

		//TODO: Create thread for other clients trying to make first connection
		
		//---------- Commented code to get the code to compile
//		// Loop till p.finished is false
//		while (p.finished == false){
//			
//			// Loop till preferred and optimistic neighbors stay same
//			while (true){
//				Boolean restart_cycle = false; 
//				
//				/********* Sending ***********/
//				// Loop over all preferred neighbors
//				for (int i = 0; i < p.prefNbr.size(); i++) {
//					// TODO: Send piece to one neighbor
//					
//					// if either preferred or optimistic neighbor has changed,
//					// then restart the sending and receiving loops
//					if (p.checkTimeout() == true){
//						p.updateNbrs();
//						restart_cycle = true;
//						break;
//					}
//				}
//				
//				if (restart_cycle) break;
//				
//				/********* Receiving ***********/
//				// Loop over all preferred neighbors
//				for (int i = 0; i < p.prefNbr.size(); i++) {
//					// TODO: Receive piece from one neighbor
//					// TODO: Write to file
//					// TODO: Update own bit-field	
//					
//					// if either preferred or optimistic neighbor has changed,
//					// then restart the sending and receiving loops
//					if (p.checkTimeout() == true){
//						p.updateNbrs();
//						restart_cycle = true;
//						break;
//					}					
//				}
//				
//				if (restart_cycle) break;				
//			}
//		}
		
		return;
	}
	
	/**
	 * Checks if a timeout event has happened
	 * TODO: implementation
	 */	
	public boolean checkTimeout(){
//		/********* Check for timeouts ***********/
//		if (UnChokingInterval){
//			//Update preferred neighbors
//		}
//		
//		if (OptimisticUnchokingInterval){
//			//Update optimistic neighbors
//		}
		
		return true;
	}
	
	/**
	 * In case of timeout, updates p.prefNbr and p.waitNbr lists
	 * TODO: implementation
	 */	
	public void updateNbrs(){
		return;
	}
	

	/**
	 * Constructor for this peer port is the port number this peer will listen
	 * on TODO: right now, all peers are on localhost, need to change
	 */
	public PeerProcess(int id) throws Exception {
		this.peerid = id;

		// Read in config files
		readCommonCfgFile();
		readPeerInfoCfgFile();

		// Setup listening socket
		this.listener = new ServerSocket(this.listenport);
		
	}


	/**
	 * Start handshake process with each neighbor of lesser peerid
	 */
	private void handshakeNeighbors() throws IOException {

		Set<Integer> peerids = this.neighbors.keySet();
		Iterator i = peerids.iterator();

		// Iterate through neighbors
		while (i.hasNext()) {

			Integer pid = (Integer) i.next();

			// Contact peers if lower peerid
			if (pid < this.peerid) {
				NeighborPeer peer = this.neighbors.get(pid);
				this.handshakePeer(peer);
			}
		}

	}

	/**
	 * Prints neighbor information on screen. Used for debugging purpose only.
	 */
	private void printNeighbors() {
		for (NeighborPeer nbr : this.neighbors.values()) {
			System.out.format("%d %d\n", nbr.getID(), nbr.getPort());
		}

		return;
	}

	/**
	 * Read in variables from config file, set all appropriate variables
	 */
	public void readPeerInfoCfgFile() {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(
					this.peerInfoFileName));
			String line = reader.readLine();
			String[] split_line = line.split(" ");

			// Loop over all lines in config file
			while (line != null) {
				if (!line.startsWith("#")) { // ignore comments
					split_line = line.split(" ");
					int id = Integer.parseInt(split_line[0]);
					String hostname = split_line[1];
					int port = Integer.parseInt(split_line[2]);
					boolean hasFile = (split_line[3].equals("1"));

                                        // Don't add self to neighbors
					if (id != this.peerid) {
						NeighborPeer nbr = new NeighborPeer(id, port, hostname);
						// Add neighboring peers' info to NeighborPeer hash-map
						this.neighbors.put(id, nbr);
					}
                                        else {
						// Set current peer's port number
                                        	this.listenport = port;
                                        }
                                        
				}

				line = reader.readLine();
			}
			reader.close();

		} catch (Exception e) {
			System.out.println("Error reading in peer info file");
			e.printStackTrace();
			// TODO: something ...
		}
	}

	/**
	 * Read in variables from config file, set all appropriate variables
	 */
	private void readCommonCfgFile() {

		try {
			BufferedReader reader = new BufferedReader(new FileReader(
					this.commonCfgFileName));
			String line = reader.readLine();

			while (line != null) {
				String[] split_line = line.split(" ");

				switch (split_line[0]) {
				case "NumberOfPreferredNeighbors":
					this.NumberOfPreferredNeighbors = Integer
							.parseInt(split_line[1]);
					break;
				case "UnchokingInterval":
					this.UnchokingInterval = Integer.parseInt(split_line[1]);
					break;
				case "OptimisticUnchokingInterval":
					this.OptimisticUnchokingInterval = Integer
							.parseInt(split_line[1]);
					break;
				case "FileName":
					this.FileName = split_line[1];
					break;
				case "FileSize":
					this.FileSize = Integer.parseInt(split_line[1]);
					break;
				case "PieceSize":
					this.PieceSize = Integer.parseInt(split_line[1]);
					break;
				}

				line = reader.readLine();
			}

			reader.close();

		} catch (Exception e) {
			System.out.println("Error reading in config file");
			e.printStackTrace();
			// TODO: something ...
		}

	}

	/**
	 * Listen for a message TODO: Need to actually continue listening after one
	 * message TODO: Multithreading
	 */
	public void listenForConnection() throws Exception {

		while (true) {
			// Listen for connection from another peer.
			Socket connection = this.listener.accept();
			// Once connection started, does whatever in a separate thread.
			new PeerConnection(connection, "LISTEN").start();
		}
	}

	/**
	 * Thread class to process a received connection between this and another peer
	 */
	private class PeerConnection extends Thread {

		private Socket connection; // the connection the peers are communicating
									// on
		private ObjectInputStream in;
		private ObjectOutputStream out;
		private Boolean finished;

		// The neighbor we are connected with, possibly unknown
		private NeighborPeer connectedWith = null;

		String type; // Type of connection. For example, "HANDSHAKE"

		public PeerConnection(Socket connection, String type) throws IOException {

			this.connection = connection;
			this.out = new ObjectOutputStream(this.connection.getOutputStream());
			out.flush();
			this.in = new ObjectInputStream(this.connection.getInputStream());
			this.finished = false;
			this.type = type;

		}

		public PeerConnection(Socket connection, String type, NeighborPeer connectedWith) throws IOException {

			this(connection, type);
			this.connectedWith = connectedWith;
		}

		public void run() {

			try {

				// Depending on type of this connection, different things to be done
				switch (this.type) {
					case "LISTEN": // Received message, need to read
						readMessage();
						break;
					case "HANDSHAKE": // Send initial handshake message
						sendHandshake();
						readMessage(); // Should get handshake response
						break;
				}

				in.close();
				out.close();
				connection.close();

			} catch (Exception e) {
				System.out.println("There was a problem communicating with a peer.");
				e.printStackTrace();
			}

		}

		/**
		 * Run a handshake connection
		 * 1. This peer sends initial handshake message
		 * 2. Wait for other peer to respond
		 * TODO: Send correct handshake message in bytes
		 * TODO: Finish actual handshake process ...
		 */
		private void sendHandshake() {
			try {
				// initial handshake message
				this.out.writeObject("Handshake from " + peerid); // Not the actual message
				this.out.flush();
			}
			catch (Exception e) {
				//System.out.println("There was a problem doing a handshake message.");
				//e.printStackTrace();
				// TODO: Always getting EOFException ...
			}

		}

		/**
		 * Incoming message from this connection, decide what to do.
		 */
		private void readMessage() {

			try {
				// read in message and decide what to do
				String msg = (String) this.in.readObject();
				// do something ...

				System.out.println(msg); // TEMPORARY

			} catch (Exception e) {
				//TODO: EOFException
				//System.out.println("There was a problem with an incoming message.");
				//e.printStackTrace();
			}
		
		}
	}

	/**
	 * Handshake interaction with peer
	 * Separate thread created for this communication
	 * TODO: Actually get it to send correct message in bytes
	 */
	public void handshakePeer(NeighborPeer peer) throws IOException {

		this.peerConnection(peer, "HANDSHAKE");
	}

	/**
	 * Start connection with peer, send message
	 * Separate thread will handle this communication
	 * type is what sort of connection (for example, handshake)
	 * msg is what message should initially be sent
	 */
	public void peerConnection(NeighborPeer peer, String type) throws IOException {

		Socket connection = new Socket(peer.getHostName(), peer.getPort());

		// separate thread to communicate
		new PeerConnection(connection, type).start();
	}

}
