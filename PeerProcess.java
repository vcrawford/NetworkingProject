import java.net.*; //classes related to sockets
import java.io.*;
import java.util.*; //HashMap
import java.util.BitSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import static com.mycila.event.Topics.*;
import static com.mycila.event.Topic.*;
import com.mycila.event.*;

/**
 * Represents the peer that is running on this process
 */
public class PeerProcess {
	private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger("project.networking");
    public static final Dispatcher dispatcher = Dispatchers.asynchronousSafe(ErrorHandlers.rethrow());

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

    private Timer chokeTimer;
    private Timer optimisticTimer;

	// Number of peers to wait for contact from (ids greater than my id)
	private int num_wait;

	private boolean hasFile; // whether we start with the file or not
	FileHandle fH;

	// Store nbr IDs
    private HashMap<Integer, PeerStatus> neighborStatus = new HashMap<Integer, PeerStatus>(); // status of neighbor
    private HashMap<Integer, Integer> neighborVolume = new HashMap<Integer, Integer>(); // number of bytes sent in previous interval

    private enum PeerStatus {
        Choked, Unchoked, Optimistic;
    }

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

        // register event handlers
        registerHandlers();
        registerTimers();

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
                PeerConnection.connectTo(this.myid, peer).start();
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
                        this.neighborStatus.put(id, PeerStatus.Choked);
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
     * The architecture of the event system is basically that
     * PeerProcess is the brain, and PeerConnections are the workers
     * that send information to the brain and respond to its commands.
     *
     * The two responsibilities of PeerConnection instances are to wait
     * for messages from peers and notify PeerProcess when they are
     * receieved, and to send messages to peers.
     */
    private void registerHandlers() {
        dispatcher.subscribe(topic("connected"), NeighborPeer.class, new ConnectedHandler());
        dispatcher.subscribe(topic("interval/unchoke"), Boolean.class, new UnchokeIntervalHandler());

        // message receipt
        dispatcher.subscribe(topic("recv/bitfield"), PeerConnection.PeerMessage.class, new BitfieldHandler());
        dispatcher.subscribe(topic("recv/choke"), PeerConnection.PeerMessage.class, new ChokeHandler());
        dispatcher.subscribe(topic("recv/unchoke"), PeerConnection.PeerMessage.class, new UnchokeHandler());
    }

    private class ConnectedHandler implements Subscriber<NeighborPeer> {
        public void onEvent(Event<NeighborPeer> event) {
            neighbors.put(event.getSource().getID(), event.getSource());
            neighborStatus.put(event.getSource().getID(), PeerStatus.Choked);
            // new connection, we need to send this peer our bitfield
            message(event.getSource().getID(), Message.bitfield(PeerProcess.this.fH.getBitfield()));
        }
    }

    private class BitfieldHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {
            // received a bitfield, update our store and notify the
            // other party of whether we're interested.
            logger.info("received bitfield from {} (self = {})", event.getSource().id, PeerProcess.this.myid);
            BitSet peerBitfield = ((Message.BitfieldPayload)event.getSource().msg.getPayload()).bitfield;

            PeerProcess.this.fH.setBitfield(event.getSource().id, peerBitfield);

            if(PeerProcess.this.fH.checkInterest(event.getSource().id)) {
                message(event.getSource().id, Message.empty(Message.Type.Interested));
            } else {
                message(event.getSource().id, Message.empty(Message.Type.NotInterested));
            }
        }
    }
    
    private class ChokeHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {
            logger.info("received choke message from {} (self = {})", event.getSource().id, PeerProcess.this.myid);
        }
    }

    private class UnchokeHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {
            logger.info("received unchoke message from {} (self = {})", event.getSource().id, PeerProcess.this.myid);
        }
    }

    private class UnchokeIntervalHandler implements Subscriber<Boolean> {
        public void onEvent(Event<Boolean> ignored) {
            logger.info("updating choke/unchoke settings (self = {})", myid);
            ArrayList<Integer> peers = new ArrayList<Integer>(neighbors.keySet());

            // sort in descending order
            Collections.sort(peers, (a, b) -> neighborVolume.getOrDefault(b, 0) - neighborVolume.getOrDefault(a, 0));

            int i = 0;
            for(; i < NumberOfPreferredNeighbors && i < peers.size(); i++) {
                PeerStatus old = neighborStatus.get(peers.get(i));
                neighborStatus.put(peers.get(i), PeerStatus.Unchoked);

                if(old.equals(PeerStatus.Choked)) {
                    message(peers.get(i), Message.empty(Message.Type.Unchoke));
                }
            }

            for(; i < peers.size(); i++) {
                PeerStatus old = neighborStatus.get(peers.get(i));
                if(!old.equals(PeerStatus.Optimistic)) {
                    neighborStatus.put(peers.get(i), PeerStatus.Choked);
                }

                if(old.equals(PeerStatus.Unchoked)) {
                    message(peers.get(i), Message.empty(Message.Type.Choke));
                }
            }
        }
    }

    // send a message to peer id
    private static void message(int id, Message msg) {
        PeerProcess.dispatcher.publish(topic(PeerConnection.sendTopic(id)), msg);
    }

    private void registerTimers() {
        this.chokeTimer = new Timer("choke/unchoke");
        this.chokeTimer.scheduleAtFixedRate(new MessageTask<Boolean>(topic("interval/unchoke"), true), 0, this.UnchokingInterval * 1000);

        this.optimisticTimer = new Timer("optimistic");
        this.optimisticTimer.scheduleAtFixedRate(new MessageTask<Boolean>(topic("interval/optimistic"), true), 0, this.OptimisticUnchokingInterval * 1000);
    }

    private static class MessageTask<T> extends TimerTask {
        private Topic topic;
        private T value;
        public MessageTask(Topic t, T value) {
            this.topic = t;
            this.value = value;
        }

        public void run() {
            PeerProcess.dispatcher.publish(topic, value);
        }
    }
}
