import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

/**
 * Thread class to process a received connection between me and a peer
 */
public class PeerConnection extends Thread {
    private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("project.networking.connection");

	private Integer myid; // my ID
	private Integer peerid; // ID of peer connected to me
	private Socket connection; // socket
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Boolean finished;

	// The neighbor we are connected with, possibly unknown
	private NeighborPeer connectedWith = null;

	String type; // Type of connection. For example, "HANDSHAKE"

	/* TODO: Add file-pointer variable 
	 * Since each peer-thread is reading/writing the file independently, each 
	 * peer-thread must have a separate file-pointer.	*/
	
	/**
	 * Constructor. Is called while creating the thread that will "send" a
	 * connection request to peer
	 */
	public PeerConnection(Integer myid, NeighborPeer peer)
			throws IOException {
        this.logger.setLevel(Level.DEBUG);

		this.myid = myid;
        this.connectedWith = peer;
        this.peerid = peer.getID();
		this.finished = false;
	}

	/**
	 * Constructor. Is called while creating the thread that will "listen" for a
	 * connection request from peer
	 */
	public PeerConnection(Socket connection, Integer myid) {
        this.logger.setLevel(Level.DEBUG);
		this.connection = connection;
		this.myid = myid;
		this.peerid = -1; // Will be set by handshake signal
		this.finished = false;
	}

	public void run() {
        logger.info("new connection (peer = {}, self = {})", this.peerid, this.myid);
        // create the socket if it doesn't exist
        while(this.connection == null && this.connectedWith != null) {
            String hostname = this.connectedWith.getHostName();
            int port = this.connectedWith.getPort();
            logger.debug("creating socket ({}:{})", hostname, port);
            try {
                this.connection = new Socket(hostname, port);
            } catch(java.net.ConnectException _e) {
                logger.debug("failed to create socket ({}:{}). retrying in 5s", hostname, port);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch(Exception e) {
                    logger.error("failed to create socket ({}:{})", hostname, port);
                    e.printStackTrace();
                    return;
                }
            } catch(java.net.UnknownHostException _e) {
                logger.error("failed to find host {}", hostname);
                return;
            } catch(IOException e) {
                logger.error("socket creation failed with IOException: {}", e);
                return;
            }

        }

        try {
            this.out = new ObjectOutputStream(this.connection.getOutputStream());
            out.flush();
            this.in = new ObjectInputStream(this.connection.getInputStream());
        } catch(IOException e) {
            logger.error("failed creating streams: {}", e);
            return;
        }

		// Exchange a handshake signal with the peer.
		// If I was a listener then , till now this thread doesn't know the
		// peerid of the peer. The handshake signal facilitates that.
		boolean isListener = (this.peerid == -1);
		this.exchangeHandshake(isListener);

		// try {
		//
		// // Depending on type of this connection, different things to be
		// // done
		// switch (this.type) {
		// case "LISTEN": // Received message, need to read
		// readMessage();
		// break;
		// case "HANDSHAKE": // Send initial handshake message
		// sendHandshake();
		// readMessage(); // Should get handshake response
		// break;
		// }
		//
		// in.close();
		// out.close();
		// connection.close();
		//
		// } catch (Exception e) {
		// System.out
		// .println("There was a problem communicating with a peer.");
		// e.printStackTrace();
		// }

		// ---------- Commented code to get the code to compile
		// // Loop till p.finished is false
		// while (p.finished == false){
		//
		// // Loop till preferred and optimistic neighbors stay same
		// while (true){
		// Boolean restart_cycle = false;
		//
		// /********* Sending ***********/
		// // Loop over all preferred neighbors
		// for (int i = 0; i < p.prefNbr.size(); i++) {
		// // TODO: Send piece to one neighbor
		//
		// // if either preferred or optimistic neighbor has changed,
		// // then restart the sending and receiving loops
		// if (p.checkTimeout() == true){
		// p.updateNbrs();
		// restart_cycle = true;
		// break;
		// }
		// }
		//
		// if (restart_cycle) break;
		//
		// /********* Receiving ***********/
		// // Loop over all preferred neighbors
		// for (int i = 0; i < p.prefNbr.size(); i++) {
		// // TODO: Receive piece from one neighbor
		// // TODO: Write to file
		// // TODO: Update own bit-field
		//
		// // if either preferred or optimistic neighbor has changed,
		// // then restart the sending and receiving loops
		// if (p.checkTimeout() == true){
		// p.updateNbrs();
		// restart_cycle = true;
		// break;
		// }
		// }
		//
		// if (restart_cycle) break;
		// }
		// }

        logger.info("closing connection thread (peer = {}, self = {})", this.peerid, this.myid);
	}

	private void exchangeHandshake(boolean isListener) {
		/**
		 * Send a handshake message to the connected peer
		 */
		try {
            logger.debug("handshaking {} (self = {})", this.peerid, this.myid);
			String handshakeStr = "P2PFILESHARINGPROJ" + "\0\0\0\0\0\0\0\0\0\0"
					+ this.myid;
			this.out.writeObject(handshakeStr);
			this.out.flush();
		} catch (Exception e) {
			// System.out.println("There was a problem doing a handshake message.");
			// e.printStackTrace();
			// TODO: Always getting EOFException ...
		}

		/**
		 * Wait and receive a handshake message from the connected peer
		 */
		try {
			String handshakeStr = (String) this.in.readObject();
			String header = handshakeStr.substring(0, 18);
			// TODO: Check the validity of header
			this.peerid = Integer.parseInt(handshakeStr.substring(28));

            logger.debug("received handshake from {} (self = {})", this.peerid, this.myid);
		} catch (Exception e) {
			// System.out.println("There was a problem doing a handshake message.");
			// e.printStackTrace();
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

            logger.trace("received message: {}", msg);
		} catch (Exception e) {
			// TODO: EOFException
			// System.out.println("There was a problem with an incoming message.");
			// e.printStackTrace();
		}
	}
}
