import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.BitSet;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;


/**
 * Thread class to process a received connection between me and a peer
 */
public class PeerConnection extends Thread {

	private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger("project.networking.connection");

	private Integer myid; // my ID
	private Integer peerid; // ID of peer connected to me
	private Socket connection; // socket
	// peer process this was spawned from
	// This might have become useless after inclusion of fileHandle. Remove if so.
	private final PeerProcess parent;
	private final FileHandle fH;

	// The neighbor we are connected with, possibly unknown
	private NeighborPeer connectedWith = null;

	private static final String handshake_header = "P2PFILESHARINGPROJ";

	/**
	 * Constructor. Is called while creating the thread that will "send" a connection request to peer.
	 */
	public PeerConnection(PeerProcess parent, Integer myid, NeighborPeer peer, FileHandle fH) throws IOException {

		this.logger.setLevel(Level.DEBUG);
		this.parent = parent;
		this.fH = fH;
		this.myid = myid;
		this.connectedWith = peer;
		this.peerid = peer.getID();
	}

	/**
	 * Constructor. Is called while creating the thread that will "listen" for a connection request from peer.
	 */
	public PeerConnection(PeerProcess parent, Socket connection, Integer myid, FileHandle fH) {

		this.logger.setLevel(Level.DEBUG);
		this.parent = parent;
		this.fH = fH;
		this.connection = connection;
		this.myid = myid;
		this.peerid = -1; // We don't know the peer yet, will be set by handshake signal
	}

	/**
	 * Run the thread
	 */
	public void run() {

		logger.debug("new connection (peer = {}, self = {})", this.peerid, this.myid);

		// create the socket if it doesn't exist
		// (this peer is the initiator of the communication)
		while (this.connection == null && this.connectedWith != null) {

			// Need hostname, port
			String hostname = this.connectedWith.getHostName();
			int port = this.connectedWith.getPort();
			logger.debug("creating socket ({}:{})", hostname, port);

			try {

				// Try to start connection
				this.connection = new Socket(hostname, port);

			} catch (java.net.ConnectException _e) {

				logger.debug("failed to create socket ({}:{}). retrying in 5s", hostname, port);
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (Exception e) {
					logger.error("failed to create socket ({}:{})", hostname, port);
					e.printStackTrace();
					return;
				}

			} catch (java.net.UnknownHostException _e) {
				logger.error("failed to find host {}", hostname);
				return;

			} catch (IOException e) {
				logger.error("socket creation failed with IOException: {}", e);
				return;
			}
		}
		
		// Generate log depending on who initiated the connection
		if (this.myid < this.peerid)
			logger.info("Peer {} makes a connection to Peer {}.", this.myid, this.peerid);
		else
			logger.info("Peer {} is connected from Peer {}", this.myid, this.peerid);

		// We have a connection, time to handshake ...
		this.exchangeHandshake();

		try {
			// Now exchange bitfields ...
			this.exchangeBitfield();

			// Notify whether we are interested in any of these bitfields
			this.notifyInterested();

		} catch (IOException e) {
			logger.debug("Error: Exchange bit-field of Interested msg failed");
			return;
		}
		
		/********************************************************/
		/******** Threads enters send/receive loop **************/
		/********************************************************/
		logger.debug("Peer {} thread enters send/receive loop", this.peerid);
		
		// Flag to indicate if all peers have complete files it's time to exit
		// Enable it to start working on sending/receiving. That code has bugs right now.
		Boolean all_complete = true;
		
		while(all_complete == false){
			Integer rcvPieceIdx = 0;
			
			// Send request for a piece
			if(true /*this.fH.checkInterest(this.peerid)*/){
				Message msg;
				rcvPieceIdx = this.fH.getPieceIndexToReceive();
				logger.debug("Sending request for piece {} to peer {}", rcvPieceIdx, this.peerid);
				msg = Message.index(Message.Type.Request, rcvPieceIdx);
				try {
					msg.to_stream(this.connection.getOutputStream());
					logger.debug("Sent request for piece {} to peer {}", rcvPieceIdx, this.peerid);
				} catch (IOException e) {
					logger.debug("Error: Failed sending request for piece {} to peer {}", rcvPieceIdx, this.peerid);
					e.printStackTrace();
					this.exitThread();
				} 
			}
			
			// Receive request for piece
			Integer timeout = 0;
			logger.debug("Receiving request for piece from peer {}", this.peerid);
			Integer sndPieceIdx = receivePieceRequest(timeout);
			logger.debug("Received request for piece {} to peer {}", sndPieceIdx, this.peerid);
			
			// Send piece
			// Right now, if a peer has complete file, it sends -1 as piece index
			// Hence skip sending piece to such peer 
			if (sndPieceIdx != -1){
				byte [] sndPiece = this.fH.getPieceToSend(sndPieceIdx);
				Message msg = Message.piece(Message.Type.Piece, sndPiece);
				logger.debug("Sending piece {} of len {} to peer {}", sndPieceIdx, msg.len, this.peerid);
				Boolean status = sendMsg(msg);	
				logger.debug("Sent piece {} to peer {}", sndPieceIdx, this.peerid);
			}
			
			// Receive piece
			// If I already have all the pieces I would have sent rcvPieceIdx as
			// -1. In such case I don't expect peer to return me a piece
			if (rcvPieceIdx != -1){
				try {
					logger.debug("Receiving piece {} from peer {}", rcvPieceIdx, this.peerid);
					Message rcvMsg = Message.from_stream(this.connection.getInputStream());
					logger.debug("Received piece {} of len {} from peer {}", rcvPieceIdx, rcvMsg.len, this.peerid);
					byte [] rcvPiece = rcvMsg.payload.array();
					//write piece to file
					this.fH.writePiece(rcvPieceIdx, rcvPiece, rcvPiece.length);
				} catch (IOException e) {
					
					e.printStackTrace();
					this.exitThread();
				}		
			}

			
			
			
			// recompute all_complete
		}
		
		// Connection complete
		this.exitThread();
	}

	/**
	 * Function to print that thread is exiting 
	 */
	private void exitThread(){
		logger.debug("closing connection thread (peer = {}, self = {})", this.peerid, this.myid);
		System.exit(0);
	}
	
	/**
	 * Send handshake message, and receive handshake message from peer.
	 */
	private void exchangeHandshake() {

		// Send a handshake message
		try {
			// It's possible that the peerid is still -1, since we might've initiated
			logger.debug("handshaking {} (self = {})", this.peerid, this.myid);

			// Send handshake bytes
			ByteBuffer buf = ByteBuffer.allocate(32);
			buf.put(handshake_header.getBytes());
			buf.putInt(28, this.myid);
			this.connection.getOutputStream().write(buf.array());
		} catch (Exception e) {

			logger.error("failed to send handshake {}", e);
		}

		// Receive handshake
		try {
			// Read in message
			ByteBuffer buf = ByteBuffer.allocate(32);
			this.connection.getInputStream().read(buf.array(), 0, 32);

			// Test whether it is in fact a handshake message
			byte[] test_handshake_header = new byte[handshake_header.length()];
			buf.get(test_handshake_header, 0, handshake_header.length());
			String test = new String(test_handshake_header);

			if (!test.equals(handshake_header)) {
				// Was not the handshake message
				logger.error("received invalid handshake from {} (handshake_header = {}, self = {})", this.peerid,
						test, this.myid);
			}

			// Get the peer that we're talking to
			this.peerid = buf.getInt(28);
			this.connectedWith = new NeighborPeer(this.peerid, this.connection.getPort(), this.connection
					.getInetAddress().getHostName());

			logger.debug("received handshake from {} (self = {})", this.peerid, this.myid);

		} catch (Exception e) {

			logger.error("failed to receive handshake {}", e);
		}
	}

	// Returns either the next message or null if the peer sent a
	// bit-field message.
	private void exchangeBitfield() throws IOException {

		// Send own bit-field to peer
		// TODO: For now sending bit-field irrespective of hasFile field. Project
		// description, however, says that it is optional if hasFile is false
		try {
			// Form bit-field message
			BitSet myBitField = this.fH.getBitfield();
			Message my_bits_msg = Message.bitfield(myBitField);

			// Send bit-field message
			logger.debug("Peer {} sending bitfield {} of size {} to {}", this.myid, myBitField.toString(),
					myBitField.size(), this.peerid);
			my_bits_msg.to_stream(this.connection.getOutputStream());

		} catch (IOException e) {
			logger.error("failed to send bitfield to {}: {}", this.peerid, e);
			throw e;
		}

		// Read in peer's bitfield
		try {
			// Get message
			Message response = Message.from_stream(this.connection.getInputStream());

			// Check if bit-field message
			if (response.type == Message.Type.Bitfield) {
				// Extract bit-field from message
				BitSet peerBitField = ((Message.BitfieldPayload) response.getPayload()).bitfield;
				logger.debug("Received bitfield response {} of size {} from {}", peerBitField.toString(),
						peerBitField.size(), this.peerid);

				// Set bit-field in the file handle
				this.fH.setBitfield(this.peerid, peerBitField);
			} else {
				logger.debug("failed to receive bitfield from {} (actual type: {})", this.peerid, response.type);
			}

		} catch (IOException e) {

			logger.error("failed to exchange bitfield with {}: {}", this.peerid, e);
			throw e;
		}
	}

	/**
	 * Send either an interested message, or a not interested message
	 */
	private void notifyInterested() throws IOException {
		try {
			boolean interest = this.fH.checkInterest(this.peerid);

			if (interest == true) {
				// There are some pieces we are interested in
				logger.debug("sending interested message to {}", this.peerid);
				Message.empty(Message.Type.Interested).to_stream(this.connection.getOutputStream());
			} else {
				// There are no pieces we are interested in
				logger.debug("sending not-interested message to {}", this.peerid);
				Message.empty(Message.Type.NotInterested).to_stream(this.connection.getOutputStream());
			}
		} catch (IOException e) {
			logger.error("failed to notify {} of interest: {}", this.peerid, e);
			throw e;
		}
	}
	
	/**
	 * See if I have a request for piece. Wait till the time specified by the 
	 * timeout argument. If a request if found within this timeout, then return
	 * the correct piece index, otherwise return -1
	 * 
	 * @param timeout
	 */
	private Integer receivePieceRequest(Integer timeout){
		Integer pieceIdx = -1;
		
		// TODO: implement timeout based processing
		
		try {
			Message response = Message.from_stream(this.connection.getInputStream());
			
			if (response.type == Message.Type.Request){
				assert (response.len == 4); //Length of response must be 4 bytes
				pieceIdx = response.payload.getInt();
			}
		} catch (IOException e) {
			logger.debug("Error: Failed receiving request from peer {}", this.peerid);
			e.printStackTrace();
			this.exitThread();
		}
		
		return pieceIdx;
	}
	
	/**
	 * Sends a message to peer
	 * 
	 * @param m
	 * @return sendStatus true if sent successfully
	 */
	private Boolean sendMsg(Message m){
		Boolean sendStatus = false;
		
		try {
			m.to_stream(this.connection.getOutputStream());
			sendStatus = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return sendStatus;
	}	
}
