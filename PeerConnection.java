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

import static com.mycila.event.Topics.*;
import static com.mycila.event.Topic.*;
import com.mycila.event.*;

/**
 * Thread class to process a received connection between me and a peer
 */
public class PeerConnection extends Thread {

	private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger("project.networking.connection");

	private Integer myid; // my ID
	private Socket connection; // socket

	// The neighbor we are connected with, possibly unknown
	private NeighborPeer peer = null;

	private static final String handshake_header = "P2PFILESHARINGPROJ";

    private PeerConnection(Integer myid) {
        this.myid = myid;
	}

    /**
     * Initiate connection to the given peer.
     * Called for each peer of lower id than this one
     */
    public static PeerConnection connectTo(Integer myid, NeighborPeer peer) throws IOException {
        PeerConnection pc = new PeerConnection(myid);
        pc.peer = peer;
        logger.debug("Initiating a peer connection with peer {} (self={})", peer.getID(), myid);
        return pc;
    }

    /**
     * Handle a connection initiated by another peer.
     * Should receive connections for each peer of higher id
     */
    public static PeerConnection handleConnection(Integer myid, Socket sock) throws IOException {
        PeerConnection pc = new PeerConnection(myid);
        pc.connection = sock;
        logger.debug("Peer connection initated from unknown peer (self={})", myid);
        return pc;
    }

    /**
     * Depending on who initiated, either: 
     * 1. Connect to a remote peer, establishing the `connection`
     * variable.
     * 2. Wait for a handshake message on the connection, establishing
     * the `peer` variable.
     *
     * Upon completion, both `peer` and `connection` are guaranteed to
     * be set.
     */
    private void preProtocol() throws Exception {

        // Finish handshaking. Different behavior depending on who was the initiator.
        if(this.peer == null) {
            // We received the connection, don't know who from

            // Connection must exist
            assert(this.connection != null);

            // Expect a handshake from them first
            this.peer = this.awaitHandshake();

            // After the handshake, we should know who the peer is
            assert(this.peer != null);

            // Send them our handshake
            this.sendHandshake();

            logger.debug("Pre protocol with initiating peer {} is complete (self={})",
                this.peer.getID(), this.myid);

        } else if(this.connection == null) {
            // We are initiaing the connection

            // We should know to whom
            assert(this.peer != null);

            // Initiate TCP connection and send handshake
            this.connection = new Socket(this.peer.getHostName(), this.peer.getPort());
            this.sendHandshake();

            // Should get handshake back
            NeighborPeer validate = this.awaitHandshake();

            if(this.peer.getID() != validate.getID()) {
                // Something went wrong with handshake response ...
                logger.error("connected peer returned mismatching handshake! (target = {}, actual = {})",
                    this.peer.getID(), validate.getID());
            }

            logger.debug("Pre protocol with initiating self to peer {} is complete (seld={})",
                this.peer.getID(), this.myid);
        }
    }

	/**
	 * Run the thread
	 */
	public void run() {

            try {
                // Handshaking protocol
                preProtocol(); // sets up assumed preconditions

            } catch(Exception e) {
                logger.error("failed to handshake, closing connection: {}", e);
                this.exitThread();
            }

	    logger.debug("new connection (peer = {}, self = {})", this.peer.getID(), this.myid);


            // Listen for when we need to send a message on this connection
            PeerProcess.dispatcher.subscribe(topic(String.format("peer/%d/send", this.peer.getID())),
                Message.class, new SendHandler());

            // Listen for when we need to close this connection
            PeerProcess.dispatcher.subscribe(topic(String.format("peer/%d/close", this.peer.getID())),
                Void.class, new CloseHandler());

            // Send out connection notification
            PeerProcess.dispatcher.publish(topic("connected"), this.peer);
		
	    /********************************************************/
	    /******** Threads enters send/receive loop **************/
	    /********************************************************/
	    logger.debug("Peer {} thread enters send/receive loop (self={})",
                this.peer.getID(), this.myid);

	    while(true) {

		Message msg;

		try {
                     // Receive incoming message
		     msg = Message.from_stream(this.connection.getInputStream());

		} catch(Exception e) {
		    PeerProcess.dispatcher.publish(topic("recv/error"), e);
		    continue;
		}

                // Take action according to message type
		switch (msg.type) {
		    case Choke:
			PeerProcess.dispatcher.publish(topic("recv/choke"), peer(msg));
			break;
		    case Unchoke:
			PeerProcess.dispatcher.publish(topic("recv/unchoke"), peer(msg));
			break;
		    case Interested:
			PeerProcess.dispatcher.publish(topic("recv/interested"), peer(msg));
			break;
		    case NotInterested:
			PeerProcess.dispatcher.publish(topic("recv/not-interested"), peer(msg));
			break;
		    case Have:
			PeerProcess.dispatcher.publish(topic("recv/have"), peer(msg));
			break;
		    case Bitfield:
			PeerProcess.dispatcher.publish(topic("recv/bitfield"), peer(msg));
			break;
		    case Request:
			PeerProcess.dispatcher.publish(topic("recv/request"), peer(msg));
			break;
		    case Piece:
			PeerProcess.dispatcher.publish(topic("recv/piece"), peer(msg));
			break;
		    default:
			PeerProcess.dispatcher.publish(topic("recv/malformed"), peer(msg));
			break;
		}
	    }
	}

	/**
	 * Function to print that thread is exiting 
	 */
	private void exitThread(){
		logger.debug("closing connection thread (peer = {}, self = {})", this.peer.getID(), this.myid);
		System.exit(0);
	}


        /**
         * Send a handshake message to this peer
         */
        private void sendHandshake() {

		try {
			// Send handshake bytes
			ByteBuffer buf = ByteBuffer.allocate(32);
			buf.put(handshake_header.getBytes());
			buf.putInt(28, this.myid);
			this.connection.getOutputStream().write(buf.array());
			logger.debug("Sent handshake message to {} (self={})", this.peer.getID(), this.myid);

		} catch (Exception e) {
			logger.error("failed to send handshake {}", e);
		}
        }

    /**
     * Wait for handshake message from this peer
     */
    private NeighborPeer awaitHandshake() throws Exception{

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
				logger.error("received invalid handshake from {} (header = {}, self = {})",
                                    this.peer.getID(), test, this.myid);
                                throw new Exception("invalid handshake header");
			}

			// Get the peer that we're talking to
			int id = buf.getInt(28);
			NeighborPeer peer = new NeighborPeer(id, this.connection.getPort(), this.connection
					.getInetAddress().getHostName());

			logger.debug("received handshake from {} (self = {})", peer.getID(), this.myid);
                        return peer;

		} catch (Exception e) {
			logger.error("failed to receive handshake {}", e);
                        throw e;
		}
	}

    /**
     * On send event, put the message into the output stream
     */
    private class SendHandler implements Subscriber<Message> {
        public void onEvent(Event<Message> event) throws IOException {
            event.getSource().to_stream(PeerConnection.this.connection.getOutputStream());
        }
    }

    /**
     * On close event, close this connection
     */
    private class CloseHandler implements Subscriber<Void> {
        public void onEvent(Event<Void> event) {
            PeerConnection.this.exitThread();
        }
    }

    /**
     * Represent message from peer with id
     */
    public class PeerMessage {
        public final Integer id;
        public final Message msg;

        public PeerMessage(Integer id, Message msg) {
            this.id = id;
            this.msg = msg;
        }
    }

    private PeerMessage peer(Message msg) {
        return new PeerMessage(this.peer.getID(), msg);
    }

    public static String sendTopic(int peerid) {
        return String.format("peer/%d/send", peerid);
    }
}
