import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
	private Boolean finished;
    private final PeerProcess parent;
    
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
	public PeerConnection(PeerProcess parent, Integer myid, NeighborPeer peer)
			throws IOException {
        this.logger.setLevel(Level.DEBUG);

        this.parent = parent;
		this.myid = myid;
        this.connectedWith = peer;
        this.peerid = peer.getID();
		this.finished = false;
	}

	/**
	 * Constructor. Is called while creating the thread that will "listen" for a
	 * connection request from peer
	 */
	public PeerConnection(PeerProcess parent, Socket connection, Integer myid) {
        this.logger.setLevel(Level.DEBUG);

        this.parent = parent;
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

		// Exchange a handshake signal with the peer.
		// If I was a listener then , till now this thread doesn't know the
		// peerid of the peer. The handshake signal facilitates that.
		boolean isListener = (this.peerid == -1);
		this.exchangeHandshake(isListener);
        try {
            Message next = this.exchangeBitfield();
            this.notifyInterested();
        } catch(IOException e) {
            return;
        }

        logger.info("closing connection thread (peer = {}, self = {})", this.peerid, this.myid);
	}

    private static final String magic = "P2PFILESHARINGPROJ";
	private void exchangeHandshake(boolean isListener) {
		/**
		 * Send a handshake message to the connected peer
		 */
		try {
            logger.debug("handshaking {} (self = {})", this.peerid, this.myid);
            ByteBuffer buf = ByteBuffer.allocate(32);
            buf.put(magic.getBytes());
            buf.putInt(28, this.myid);
            this.connection.getOutputStream().write(buf.array());
		} catch (Exception e) {
            logger.error("failed to send handshake {}", e);
			// System.out.println("There was a problem doing a handshake message.");
			// e.printStackTrace();
			// TODO: Always getting EOFException ...
		}

		/**
		 * Wait and receive a handshake message from the connected peer
		 */
		try {
            ByteBuffer buf = ByteBuffer.allocate(32);
            this.connection.getInputStream().read(buf.array(), 0, 32);
            byte[] test_magic = new byte[magic.length()];
            buf.get(test_magic, 0, magic.length());
            String test = new String(test_magic);
            if(!test.equals(magic)) {
                logger.error("received invalid handshake from {} (magic = {}, self = {})", this.peerid, test, this.myid);
            }

            this.peerid = buf.getInt(28);
            this.connectedWith = new NeighborPeer(this.peerid, this.connection.getPort(), this.connection.getInetAddress().getHostName());
            logger.debug("received handshake from {} (self = {})", this.peerid, this.myid);
		} catch (Exception e) {
			// System.out.println("There was a problem doing a handshake message.");
			// e.printStackTrace();
			// TODO: Always getting EOFException ...
		}
	}

    // Returns either the next message or null if the peer sent a
    // bitfield message.
    private Message exchangeBitfield() throws IOException {
        try {
            logger.debug("acquiring pieces read lock");
            parent.pieces_lock.readLock().lock();
            logger.info("sending bitfield to {}", this.peerid);
            Message.bitfield(parent.pieces).to_stream(this.connection.getOutputStream());

            Message response = Message.from_stream(this.connection.getInputStream());
            if(response.type == Message.Type.Bitfield) {
                logger.info("received bitfield response from {}", this.peerid);
                this.connectedWith.addPieces(((Message.BitfieldPayload)response.getPayload()).bitfield);
                return null;
            } else {
                logger.info("received other response from {} (actual type: {})", this.peerid, response.type);
                return response;
            }

        } catch(IOException e) {
            logger.error("failed to exchange bitfield with {}: {}", this.peerid, e);
            throw e;
        } finally {
            logger.debug("releasing pieces read lock");
            parent.pieces_lock.readLock().unlock();
        }
    }

    private void notifyInterested() throws IOException {
        try {
            logger.debug("acquiring pieces read lock");
            parent.pieces_lock.readLock().lock();

            if(this.connectedWith.interested(this.parent.pieces)) {
                logger.info("sending interested message to {}", this.peerid);
                Message.empty(Message.Type.Interested).to_stream(this.connection.getOutputStream());
            } else {
                logger.info("sending not-interested message to {}", this.peerid);
                Message.empty(Message.Type.NotInterested).to_stream(this.connection.getOutputStream());
            }
        } catch(IOException e) {
            logger.error("failed to notify {} of interest: {}", this.peerid, e);
            throw e;
        } finally {
            logger.debug("releasing pieces read lock");
            parent.pieces_lock.readLock().unlock();
        }
    }

	/**
	 * Incoming message from this connection, decide what to do.
	 */
	private void readMessage() {
	}
}
