import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Thread class to process a received connection between me and a peer
 */
public class PeerConnection extends Thread {

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
	 * Constructor. Is called while creating the thread that will "listen" for a
	 * connection request from peer
	 */
	public PeerConnection(Socket connection, Integer myid) throws IOException {

		this.connection = connection;
		this.myid = myid;
		this.peerid = -1; // Will be set by handshake signal
		this.out = new ObjectOutputStream(this.connection.getOutputStream());
		out.flush();
		this.in = new ObjectInputStream(this.connection.getInputStream());
		this.finished = false;
	}

	/**
	 * Constructor. Is called while creating the thread that will "send" a
	 * connection request to peer
	 */
	public PeerConnection(Socket connection, Integer myid, Integer peerid)
			throws IOException {

		this.connection = connection;
		this.myid = myid;
		this.peerid = peerid; // Will be set by handshake signal
		this.out = new ObjectOutputStream(this.connection.getOutputStream());
		out.flush();
		this.in = new ObjectInputStream(this.connection.getInputStream());
		this.finished = false;
	}

	public PeerConnection(Socket connection, String type) throws IOException {

		this.connection = connection;
		this.out = new ObjectOutputStream(this.connection.getOutputStream());
		out.flush();
		this.in = new ObjectInputStream(this.connection.getInputStream());
		this.finished = false;
		this.type = type;
	}

	public PeerConnection(Socket connection, String type,
			NeighborPeer connectedWith) throws IOException {

		this(connection, type);
		this.connectedWith = connectedWith;
	}

	public void run() {
		System.out.format("%d th: %d Up\n", this.myid, this.peerid);
		System.out.flush();

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

		System.out.format("%d th: %d Exiting\n", this.myid, this.peerid);
		System.out.flush();
	}

	private void exchangeHandshake(boolean isListener) {
		/**
		 * Send a handshake message to the connected peer
		 */
		try {
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

			System.out.format("%d rcvd header: %s from %d\n", this.myid,
					header, this.peerid);
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

			System.out.println(msg); // TEMPORARY

		} catch (Exception e) {
			// TODO: EOFException
			// System.out.println("There was a problem with an incoming message.");
			// e.printStackTrace();
		}
	}
}