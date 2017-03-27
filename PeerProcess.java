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
	private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger("project.networking");

	private int myid; // the id of this peer
	private int listenport; // port number this peer listens on

	// Map neighbor peerid to peer
	HashMap<Integer, NeighborPeer> neighbors = new HashMap<Integer, NeighborPeer>();

	// Configuration file names
	private static final String commonCfgFileName = "Common.cfg";
	private static final String peerInfoFileName = "PeerInfo.cfg";

	// variables set in config file
	private int NumberOfPreferredNeighbors;
	private int UnchokingInterval;
	private int OptimisticUnchokingInterval;
	private String FileName;
	private int FileSize;
	private int PieceSize;

	// Number of peers to wait for contact from (ids greater than my id)
	private int num_wait;

	private boolean hasFile; // whether we start with the file or not
	FileHandle fH;

	// Store nbr IDs
	private ArrayList<Integer> prefNbr; // preferred neighbors's IDs
	private ArrayList<Integer> waitNbr; // non-preferrend or waiting neighbors

	/**
	 * Main function: Starting point
	 */
	public static void main(String[] args) throws Exception {

		// Get ID of this peer from input
		int id = Integer.parseInt(args[0]);

		// Create and initialize this peer's instance
		PeerProcess p = new PeerProcess(id);

		// Separate thread to listen for new connections
		// Peer connections spawn from either here or connectNeighbors
		// Should receive connection from each of peers with higher id
		p.listenForConnection();

		// Start connections with neighbors w/ peerid lower than myid
		p.connectNeighbors();

		// TODO: File torrenting here ...

		return;
	}

	/**
	 * Constructor
	 * 
	 * @param id
	 *            myid
	 * @throws Exception
	 */
	public PeerProcess(int id) throws Exception {

		logger.setLevel(Level.DEBUG);
		logger.debug("peer starting (id = {})", id);

		// peer id
		this.myid = id;

		// Read in config files
		readCommonCfgFile();
		readPeerInfoCfgFile();

		// Create file-handle instance
		this.fH = new FileHandle(this.myid, this.hasFile, this.FileName, this.FileSize, this.PieceSize);
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
	 * In case of timeout, updates p.prefNbr and p.waitNbr lists TODO: implementation
	 */
	public void updateNbrs() {
		return;
	}

	/**
	 * Start connection with each neighbor with ID less than myid
	 */
	private void connectNeighbors() throws IOException {

		logger.debug("connecting to peers with lower ids");

		// All neighbor ids (read from peer info file)
		Set<Integer> peerids = this.neighbors.keySet();
		Iterator i = peerids.iterator();

		// Iterate through neighbors, connect to lower id ones
		while (i.hasNext()) {

			Integer pid = (Integer) i.next();

			// Contact peers if lower peerid
			if (pid < this.myid) {
				NeighborPeer peer = this.neighbors.get(pid);
				this.peerConnection(peer);
			}
		}

		logger.debug("done connecting to peers");
	}

	/**
	 * Read in variables from config file, set all appropriate variables
	 */
	public void readPeerInfoCfgFile() {

		logger.debug("reading peer config");

		this.num_wait = 0;

		try {
			BufferedReader reader = new BufferedReader(new FileReader(this.peerInfoFileName));
			String line = reader.readLine();
			String[] split_line = line.split(" ");

			// Loop over all lines in config file
			while (!line.trim().isEmpty()) {

				if (!line.startsWith("#")) { // ignore comments

					split_line = line.split(" ");
					int id = Integer.parseInt(split_line[0]);
					String hostname = split_line[1];
					int port = Integer.parseInt(split_line[2]);

					// Is this peer self or not
					if (id != this.myid) {
						NeighborPeer nbr = new NeighborPeer(id, port, hostname);
						// Add neighboring peers' info to NeighborPeer hash-map
						this.neighbors.put(id, nbr);
						if (id > this.myid) {
							this.num_wait++;
						}
					} else {
						// Set current peer's port number
						this.listenport = port;
						// Whether it has the file or not
						this.hasFile = (Integer.parseInt(split_line[3]) == 1);
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
			BufferedReader reader = new BufferedReader(new FileReader(this.commonCfgFileName));
			String line = reader.readLine();

			while (line != null) {

				String[] split_line = line.split(" ");

				switch (split_line[0]) {
				case "NumberOfPreferredNeighbors":
					this.NumberOfPreferredNeighbors = Integer.parseInt(split_line[1]);
					break;
				case "UnchokingInterval":
					this.UnchokingInterval = Integer.parseInt(split_line[1]);
					break;
				case "OptimisticUnchokingInterval":
					this.OptimisticUnchokingInterval = Integer.parseInt(split_line[1]);
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
	 * Listens for other neighbors to connect. Since only the peers with peerid greater than myid will send request,
	 * this Thread is responsible for listening to such connection requests
	 */
	public void listenForConnection() throws Exception {

		ServerSocket listener = new ServerSocket(this.listenport);

		logger.debug("Peer {} is beginning PeerListener", this.myid);
		new PeerListener(this, this.myid, this.num_wait, listener, this.fH).start();
	}

	/**
	 * Start connection with peer, send message Separate thread will handle this communication type is what sort of
	 * connection (for example, handshake) msg is what message should initially be sent
	 */
	public void peerConnection(NeighborPeer peer) throws IOException {
		// Create a separate thread for all future communication w/ this peer
		logger.debug("spinning up connection thead");
		new PeerConnection(this, this.myid, peer, this.fH).start();
	}

}
