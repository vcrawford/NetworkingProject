import java.net.*; //classes related to sockets
import java.io.*;
import java.util.*; //HashMap
import java.util.BitSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

/**
 * Represents the peer that is running on this process
 */
public class PeerProcess {
    private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("project.networking");

	private int myid; // the id of this peer
	private int listenport; // port number this peer listens on
	private ServerSocket listener; // socket this peer listens on
	HashMap<Integer, NeighborPeer> neighbors = new HashMap<Integer, NeighborPeer>(); // peerid
																						// to
																						// peer
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

    // pieces known
    public final BitSet pieces;
    public final ReentrantReadWriteLock pieces_lock;

	// Store nbr IDs
	private ArrayList<Integer> prefNbr; // preferred neighbors's IDs
	private ArrayList<Integer> waitNbr; // non-preferrend or waiting neighbors

	/**
	 * Main function: Starting point
	 */
	public static void main(String[] args) throws Exception {
		int id = Integer.parseInt(args[0]);
		
		// Create and initialize this peer's instance
		PeerProcess p = new PeerProcess(id);

		// Start threads for handshaking neighbors w/ peerid lower than myid
		p.handshakeNeighbors();

		// Go into listen mode. 
		// Create a thread whenever a connection request comes
		p.listenForConnection();
		
		//TODO: Wait here for children threads to exit

		return;
	}

	/**
	 * Checks if a timeout event has happened TODO: implementation
	 */
	public boolean checkTimeout() {
		// /********* Check for timeouts ***********/
		// if (UnChokingInterval){
		// //Update preferred neighbors
		// }
		//
		// if (OptimisticUnchokingInterval){
		// //Update optimistic neighbors
		// }

		return true;
	}

	/**
	 * In case of timeout, updates p.prefNbr and p.waitNbr lists TODO:
	 * implementation
	 */
	public void updateNbrs() {
		return;
	}

	/**
	 * Constructor for peer process
	 */
	public PeerProcess(int id) throws Exception {
        logger.setLevel(Level.DEBUG);
        logger.info("peer starting (id = {})", id);
        this.pieces = new BitSet();
        this.pieces_lock = new ReentrantReadWriteLock();
		this.myid = id;

		// Read in config files
		readCommonCfgFile();
		readPeerInfoCfgFile();

		// Setup listening socket
		this.listener = new ServerSocket(this.listenport);
	}

	/**
	 * Start handshake process with each neighbor with ID < myid
	 */
	private void handshakeNeighbors() throws IOException {
        logger.info("connecting to peers with lower ids");

		Set<Integer> peerids = this.neighbors.keySet();
		Iterator i = peerids.iterator();

		// Iterate through neighbors
		while (i.hasNext()) {

			Integer pid = (Integer) i.next();

			// Contact peers if lower peerid
			if (pid < this.myid) {
				NeighborPeer peer = this.neighbors.get(pid);
				this.peerConnection(peer);
			}
		}
        logger.info("done connecting to peers");
	}

	/**
	 * Prints neighbor information on screen. Used for debugging purpose only.
	 */
	private void printNeighbors() {
		for (NeighborPeer nbr : this.neighbors.values()) {
			System.out.format("%d %d\n", nbr.getID(), nbr.getPort());
		}
	}

	/**
	 * Read in variables from config file, set all appropriate variables
	 */
	public void readPeerInfoCfgFile() {
        logger.debug("reading peer config");
		try {
			BufferedReader reader = new BufferedReader(new FileReader(
					this.peerInfoFileName));
			String line = reader.readLine();
			String[] split_line = line.split(" ");

			// Loop over all lines in config file
			while (! line.trim().isEmpty()) {
				if (!line.startsWith("#")) { // ignore comments
					split_line = line.split(" ");
					int id = Integer.parseInt(split_line[0]);
					String hostname = split_line[1];
					int port = Integer.parseInt(split_line[2]);
					boolean hasFile = (split_line[3].equals("1"));

					// Don't add self to neighbors
					if (id != this.myid) {
						NeighborPeer nbr = new NeighborPeer(id, port, hostname);
						// Add neighboring peers' info to NeighborPeer hash-map
						this.neighbors.put(id, nbr);
					} else {
						// Set current peer's port number
						this.listenport = port;
					}
				}

				line = reader.readLine();
			}
			reader.close();

		} catch (Exception e) {
            logger.error("Error reading peer info file");
			e.printStackTrace();
            System.exit(-1);
		}
        logger.debug("done reading peer config");
	}

	/**
	 * Read in variables from config file, set all appropriate variables
	 */
	private void readCommonCfgFile() {
        logger.debug("reading common config");

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
            logger.error("Error reading common file");
			e.printStackTrace();
            System.exit(-1);
		}

        logger.debug("done reading common config");
	}

	/**
	 * Listens for other neighbors to connect. Since only the peers with 
	 * peerid > myid will send request, this function is responsible for 
	 * listening to such connection requests
	 */
	public void listenForConnection() throws Exception {

		while (true) {
			// Listen for connection from another peer.
			Socket connection = this.listener.accept();
			// Create a separate thread for all future communication w/ this peer
			new PeerConnection(this, connection, this.myid).start();
		}
	}

	/**
	 * Start connection with peer, send message Separate thread will handle this
	 * communication type is what sort of connection (for example, handshake)
	 * msg is what message should initially be sent
	 */
	public void peerConnection(NeighborPeer peer)
			throws IOException {
        logger.info("connecting to peer (id = {}, self = {})", peer.getID(), this.myid);
		// Create a separate thread for all future communication w/ this peer
        logger.debug("spinning up connection thead");
		new PeerConnection(this, this.myid, peer).start();
	}

}
