import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.concurrent.TimeUnit;
import java.util.BitSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

/**
 * Thread class to listen for new peers
 */
public class PeerListener extends Thread {

	private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger("project.networking.connection");

	private Integer myid; // my ID
	ServerSocket listener;
	private int num_wait; // number of peers that we should wait for (number of peers with > ids)
	PeerProcess parent; // the peer process that spawned this
	FileHandle fH;

	/**
	 * Constructor. Is called from PeerProcess in order to wait on connections with higher id peers
	 */
	public PeerListener(PeerProcess parent, Integer myid, Integer num_wait, ServerSocket listener, FileHandle fH)
			throws Exception {

		this.logger.setLevel(Level.DEBUG);
		this.parent = parent;
		this.fH = fH;
		this.myid = myid;
		this.num_wait = num_wait;
		this.listener = listener;
	}

	/**
	 * Run the thread
	 */
	public void run() {

		// keep listening until heard from all larger id neighbors
		int num_conn = 0;
		logger.debug("Peer {} is waiting for {} new peers to contact it", this.myid, this.num_wait);
		try {
			while (num_conn < this.num_wait) {
				// Listen for connection from another peer.
				Socket connection = this.listener.accept();

				// Create a separate thread for all future communication w/ this peer
                PeerConnection.handleConnection(this.myid, connection).start();

				num_conn++;
			}
		} catch (Exception e) {
			logger.debug("There was a problem when peer {} was listening for new peers", this.myid);
			e.printStackTrace();
		}

		logger.debug("Peer {} has stopped waiting for new peers to contact it", this.myid);
	}

}
