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
     */
    public static PeerConnection connectTo(Integer myid, NeighborPeer peer) throws IOException {
        PeerConnection pc = new PeerConnection(myid);
        pc.peer = peer;
        return pc;
    }

    /**
     * Handle a connection initiated by another peer.
     */
    public static PeerConnection handleConnection(Integer myid, Socket sock) throws IOException {
        PeerConnection pc = new PeerConnection(myid);
        pc.connection = sock;
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
        if(this.peer == null) {
            assert(this.connection != null);
            this.peer = this.awaitHandshake();
            assert(this.peer != null);
            this.sendHandshake();
        } else if(this.connection == null) {
            assert(this.peer != null);
            this.connection = new Socket(this.peer.getHostName(), this.peer.getPort());
            this.sendHandshake();
            NeighborPeer validate = this.awaitHandshake();
            if(this.peer.getID() != validate.getID()) {
                logger.error("connected peer returned mismatching handshake! (target = {}, actual = {})", this.peer.getID(), validate.getID());
            }
        }
    }

	/**
	 * Run the thread
	 */
	public void run() {
        try {
            preProtocol(); // sets up assumed preconditions
        } catch(Exception e) {
            logger.error("failed to handshake, closing connection: {}", e);
            this.exitThread();
        }

		logger.debug("new connection (peer = {}, self = {})", this.peer.getID(), this.myid);

		// Generate log depending on who initiated the connection
		if (this.myid < this.peer.getID())
        {
            logger.info("Peer {} makes a connection to Peer {}.", this.myid, this.peer.getID());
        }
		else
        {
            logger.info("Peer {} is connected from Peer {}", this.myid, this.peer.getID());
        }

        PeerProcess.dispatcher.subscribe(topic(String.format("peer/%d/send", this.peer.getID())), Message.class, new SendHandler());
        PeerProcess.dispatcher.subscribe(topic(String.format("peer/%d/close", this.peer.getID())), Void.class, new CloseHandler());

        PeerProcess.dispatcher.publish(topic("connected"), this.peer);
		
		/********************************************************/
		/******** Threads enters send/receive loop **************/
		/********************************************************/
		logger.debug("Peer {} thread enters send/receive loop", this.peer.getID());

        while(true) {
            Message msg;
            try {
                 msg = Message.from_stream(this.connection.getInputStream());
            } catch(Exception e) {
                PeerProcess.dispatcher.publish(topic("recv/error"), e);
                continue;
            }
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

    private void sendHandshake() {
		// Send a handshake message
		try {
			// It's possible that the peer.getID() is still -1, since we might've initiated
			logger.debug("handshaking {} (self = {})", this.peer.getID(), this.myid);

			// Send handshake bytes
			ByteBuffer buf = ByteBuffer.allocate(32);
			buf.put(handshake_header.getBytes());
			buf.putInt(28, this.myid);
			this.connection.getOutputStream().write(buf.array());
		} catch (Exception e) {
			logger.error("failed to send handshake {}", e);
		}
    }

    private NeighborPeer awaitHandshake() throws Exception{
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
				logger.error("received invalid handshake from {} (handshake_header = {}, self = {})", this.peer.getID(),
						test, this.myid);
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

    private class SendHandler implements Subscriber<Message> {
        public void onEvent(Event<Message> event) throws IOException {
            event.getSource().to_stream(PeerConnection.this.connection.getOutputStream());
        }
    }

    private class CloseHandler implements Subscriber<Void> {
        public void onEvent(Event<Void> event) {
            PeerConnection.this.exitThread();
        }
    }

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
