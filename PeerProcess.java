import java.net.*; //classes related to sockets
import java.io.*;
import java.util.*; //HashMap
import java.util.BitSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

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

    // Controls when we should re-determine preferred neighbors
    private Timer chokeTimer;
    // When we should optimistically unchoke one neighbor
    private Timer optimisticTimer;

    // Number of peers to wait for contact from (ids greater than my id)
    private int num_wait;

    private boolean hasFile; // whether we start with the file or not
    FileHandle fH;

    // Status of neighbors (Choked, Unchoked, or Optimistic)
    private HashMap<Integer, PeerStatus> neighborStatus = new HashMap<Integer, PeerStatus>();

    // Status of neighbors as far as interested in this peers file pieces
    private HashMap<Integer, PeerInterestedStatus> neighborInterestedStatus = new HashMap<Integer,
        PeerInterestedStatus>();

    // Status of self that the neighbor has (same statuses as above) 
    private HashMap<Integer, PeerStatus> selfStatus = new HashMap<Integer, PeerStatus>();

    // Number of bytes sent in previous interval
    private HashMap<Integer, Integer> neighborVolume = new HashMap<Integer, Integer>();

    private enum PeerStatus {
        Choked, Unchoked, Optimistic;
    }

    private enum PeerInterestedStatus {
        Interested, NotInterested;
    }

    // For choosing optimistically unchoked neighbor
    Random rand;

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

                this.rand = new Random(System.currentTimeMillis());
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

		logger.debug("{} is done initiating connections to peers with lower id", this.myid);
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
                                                // All neighbors start out as choked
						this.neighborStatus.put(id, PeerStatus.Choked);
                                                // and uninterested
						this.neighborInterestedStatus.put(id,
                                                    PeerInterestedStatus.NotInterested);
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
        dispatcher.subscribe(topic("interval/optimistic"), Boolean.class, new OptimisticIntervalHandler());
        dispatcher.subscribe(topic("complete"), Boolean.class, new CompleteHandler());

        // message receipt
        dispatcher.subscribe(topic("recv/bitfield"), PeerConnection.PeerMessage.class, new BitfieldHandler());
        dispatcher.subscribe(topic("recv/choke"), PeerConnection.PeerMessage.class, new ChokeHandler());
        dispatcher.subscribe(topic("recv/unchoke"), PeerConnection.PeerMessage.class, new UnchokeHandler());
        dispatcher.subscribe(topic("recv/interested"), PeerConnection.PeerMessage.class,
            new InterestedHandler());
        dispatcher.subscribe(topic("recv/not-interested"), PeerConnection.PeerMessage.class,
            new NotInterestedHandler());
        dispatcher.subscribe(topic("recv/request"), PeerConnection.PeerMessage.class, new RequestHandler());
        dispatcher.subscribe(topic("recv/piece"), PeerConnection.PeerMessage.class, new PieceHandler());
        dispatcher.subscribe(topic("recv/have"), PeerConnection.PeerMessage.class, new HaveHandler());

        logger.info("Message handlers for peer {} have been registered.", this.myid);
    }


    /**
     * Deal with a new connection (could be initiated by either peer) 
     */
    private class ConnectedHandler implements Subscriber<NeighborPeer> {
        public void onEvent(Event<NeighborPeer> event) {
        	//Initialize volume score
        	neighborVolume.put(event.getSource().getID(), 0);

            // new connection, we need to send this peer our bitfield
            message(event.getSource().getID(), Message.bitfield(PeerProcess.this.fH.getBitfield()));

            logger.info("Sent {} bitfield {} (self={}).", event.getSource().getID(),
                fH.printableBitSet(fH.getBitfield()), PeerProcess.this.myid);

           // Since this is a new neighbor, we should make sure it is set as choked
	   neighborStatus.put(event.getSource().getID(), PeerStatus.Choked);
        }
    }

    /**
     * Received a bitfield from a peer
     */
    private class BitfieldHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            // Get peer's bitfield
            BitSet peerBitfield = ((Message.BitfieldPayload)event.getSource().msg.getPayload()).bitfield;

            logger.info("Received bitfield {} from {} (self = {}).",
                PeerProcess.this.fH.printableBitSet(peerBitfield), event.getSource().id,
                PeerProcess.this.myid);

            // Record the peer's bitfield
            PeerProcess.this.fH.setBitfield(event.getSource().id, peerBitfield);

            if(PeerProcess.this.fH.checkInterest(event.getSource().id)) {

                // We are interested in this bitfield
                message(event.getSource().id, Message.empty(Message.Type.Interested));

                logger.info("Interested in bitfield from {} (self = {}).",
                    event.getSource().id, PeerProcess.this.myid);
            } else {

                // Not interested
                message(event.getSource().id, Message.empty(Message.Type.NotInterested));

                logger.info("Not interested in bitfield from {} (self = {}).",
                    event.getSource().id, PeerProcess.this.myid);
            }
        }
    }

    /**
     * Deal with a choke message from a peer (that peer choked this one)
     */    
    private class ChokeHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            // Add this status
            selfStatus.put(event.getSource().id, PeerStatus.Choked);

            // TODO: cancel request timer

            logger.info("Choked by {} (self = {}).", event.getSource().id, myid);
            fH.cancelPieceIndexRequest(event.getSource().id);
        }
    }

    /**
     * Request piece of file from peer with id peer
     */
    private void requestPiece(int peer) {

        // which piece we want to request from this peer
        Integer idx = fH.getPieceIndexToReceive(peer);

        if(idx < 0) {

            // don't need pieces from this peer (or at all)
            logger.info("Not requesting any pieces from {} (self = {})", peer, this.myid);
        } else {

            // request piece
            message(peer, Message.index(Message.Type.Request, idx));
            logger.info("requesting piece {} from {} (self = {})", idx, peer, this.myid);

            // TODO: start timer that cancels the request.
        }
    }

    /**
     * Deal with unchoke message from a peer (that peer unchoked this one)
     */
    private class UnchokeHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            // Change status of self wrt peer
            selfStatus.put(event.getSource().id, PeerStatus.Unchoked);

            logger.info("Received unchoke message from {} (self = {})",
                event.getSource().id, myid);

            // Now that we are unchoked, request a piece of the file
            requestPiece(event.getSource().id);

        }
    }

    /**
     * Deal with interested message from a peer
     */
    private class InterestedHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            neighborInterestedStatus.put(event.getSource().id,
                PeerInterestedStatus.Interested);
            logger.info("Peer {} received the 'interested' message from {}",
                myid, event.getSource().id);
        }
    }

    /**
     * Deal with not-interested message from a peer
     */
    private class NotInterestedHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            neighborInterestedStatus.put(event.getSource().id,
                PeerInterestedStatus.NotInterested);
            logger.info("Peer {} received the 'not interested' message from {}",
                myid, event.getSource().id);

        }
    }


    /**
     * Deal with a piece request from a peer
     */
    private class RequestHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            // Which piece did they request
            Integer idx = ((Message.IndexPayload)event.getSource().msg.getPayload()).index;
            logger.info("Received request message for {} from {} (self = {})", idx, 
                event.getSource().id, PeerProcess.this.myid);

            if(neighborStatus.get(event.getSource().id) == PeerStatus.Choked) {

                // they are choked, ignore the request
                logger.info("ignoring request from {}, they are choked (self = {})", 
                    event.getSource().id, PeerProcess.this.myid);

            } else if(idx < 0 || idx > fH.maxPiece()) {

                // invalid piece
                logger.info("ignoring request from {}, invalid piece {} requested (self = {})", 
                    event.getSource().id, idx, PeerProcess.this.myid);

            } else {
                // Get piece
                byte[] piece = fH.getPieceToSend(idx);

                if(piece.length <= 0) {

                    logger.error("file handle returned empty piece, ignoring request (self = {})",
                        PeerProcess.this.myid);

                } else {

                    // Send it
                    message(event.getSource().id, Message.piece(idx, piece));
                    logger.info("Send piece {} to {} (self = {})", idx, 
                        event.getSource().id, PeerProcess.this.myid);
                }
            }
        }
    }

    /**
     * Deal with a piece that has been sent by a peer
     */
    private class PieceHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            // Get the piece out of the message
            Message.PiecePayload payload = ((Message.PiecePayload)event.getSource().msg.getPayload());
            byte[] content = new byte[payload.length];
            payload.content.get(content);

            logger.info("Received piece {} from {} (self = {})", payload.index, event.getSource().id, myid);

            // Write it to our file
            Boolean needMore = fH.writePiece(payload.index, content);

            logger.info("Current bitfield is {} (self={})", fH.printableBitSet(fH.getBitfield()), myid);

            // Send out have message to all peers
            logger.info("Sending have piece {} message to all peers (self={})", payload.index, myid);

            Integer[] peerids = neighbors.keySet().toArray(new Integer[neighbors.size()]);
                       
            for (int i = 0; i < peerids.length; i++) { 
               message(peerids[i], Message.index(Message.Type.Have, payload.index));
            }

            // Set hasFile flag so that Preferred nbrs are chosen randomly in next Unchoke interval
            if (needMore == false){
            	PeerProcess.this.hasFile = true;
                logger.info("Peer {} has downloaded the complete file.", PeerProcess.this.myid);

                if(fH.allComplete()) {
                    PeerProcess.dispatcher.publish(topic("complete"), true);
                }
        	}
            
            // Increment the volume score
            neighborVolume.put(event.getSource().id, neighborVolume.get(event.getSource().id) + 1); 

            // Find are they choked or not
            PeerStatus neighborStat = selfStatus.get(event.getSource().id);

            if (neighborStat == null) {
                neighborStat = PeerStatus.Choked;
            } 

            if(neighborStat == PeerStatus.Unchoked) {
                requestPiece(event.getSource().id);   
            }
        }
    }

    /**
     * A peer has received a new piece
     */
    private class HaveHandler implements Subscriber<PeerConnection.PeerMessage> {
        public void onEvent(Event<PeerConnection.PeerMessage> event) {

            // Get which piece out of the message
            Integer idx = ((Message.IndexPayload)event.getSource().msg.getPayload()).index;
            logger.info("Neighbor {} has piece {} (self = {})", event.getSource().id, idx, myid);

            fH.updateHasPiece(event.getSource().id, idx);
            if(fH.allComplete()) {
                PeerProcess.dispatcher.publish(topic("complete"), true);
            }

            // See if we are now interested in this neighbor
            if (fH.interestedInPiece(idx)) {
                message(event.getSource().id, Message.empty(Message.Type.Interested));
                logger.info("Interested in the pieces of neighbor {} (self={})",
                    event.getSource().id, myid);
            }
            
        }
    }


    /**
     * Deal with time to update preferred neighbors
     */
    private class UnchokeIntervalHandler implements Subscriber<Boolean> {
        public void onEvent(Event<Boolean> ignored) {

            logger.info("Unchoking interval. Missing {} pieces. Finding preferred neighbors (self = {})",
                fH.getNumMissing(), PeerProcess.this.myid);

            if(fH.allComplete()) {
                logger.info("all complete; some state transition was missed (self = {})", myid);
            }


            // All our peer ids
            ArrayList<Integer> peers = new ArrayList<Integer>(neighbors.keySet());

            // sort in descending order, neighbors who have sent us the most
            // file pieces at the top
            Collections.sort(peers, new Comparator<Integer>() {
                    public int compare(Integer a, Integer b) {
                    	if (PeerProcess.this.hasFile == true){
                    		// return one of {-1, +1) randomly
            				return (new Random().nextBoolean()) ? -1 : +1;
                    	}
                    	else{
	                        Integer vola = neighborVolume.get(a);
	                        Integer volb = neighborVolume.get(b);
	
	                        if (vola == null) {
	                            vola = 0;
	                        }
	
	                        if (volb == null) {
	                            volb = 0;
	                        }
	                        
	                        return volb - vola;
                    	}
                    }
                });
            
            //Reset volume score
            for (Integer key : neighborVolume.keySet()){
            	neighborVolume.put(key, 0);
            }
            
            // Send choke and unchoke messages
            int i = 0;
            // Go through top ones that will be preferred neighbors and
            // sent an unchoke message, if they weren't already
            for(; i < NumberOfPreferredNeighbors && i < peers.size(); i++) {

                // previous status
                PeerStatus old = neighborStatus.get(peers.get(i));

                // have them as unchoked now
                neighborStatus.put(peers.get(i), PeerStatus.Unchoked);

                // only send them an unchoke message if they were previously unchoked
                if(old.equals(PeerStatus.Choked)) {
                    message(peers.get(i), Message.empty(Message.Type.Unchoke));
                    logger.info("Added {} to preferred neighbors (self = {})",
                        peers.get(i), PeerProcess.this.myid);
                }
            }

            // Send choke message (unless this is the optimistically unchoked neighbor)
            for(; i < peers.size(); i++) {

                // previous status
                PeerStatus old = neighborStatus.get(peers.get(i));

                // update choked status unless this is the optimistic neighbor
                if(!old.equals(PeerStatus.Optimistic)) {

                    neighborStatus.put(peers.get(i), PeerStatus.Choked);
                }

                // send choked message if it wasn't choked already 
                if(old.equals(PeerStatus.Unchoked)) {
                    message(peers.get(i), Message.empty(Message.Type.Choke));
                    logger.info("Removed {} from preferred neighbors (self = {})", peers.get(i), PeerProcess.this.myid);
                }
            }

        }
    }

    /**
     * Deal with time to update optimistically unchoked neighbor
     * TODO: Need to only choose neighbor that is interested in our pieces
     */
    private class OptimisticIntervalHandler implements Subscriber<Boolean> {
        public void onEvent(Event<Boolean> ignored) {

            logger.info("Optimistic interval. Finding optimistic neighbor (self={})", myid);

            // All neighbor ids
            Set<Integer> n_ids = neighbors.keySet();
            Iterator<Integer> n_ids_it = n_ids.iterator();

            // Find potential optimistic neighbors, ones that are choked and interested
            ArrayList<Integer> potential = new ArrayList<Integer>();
            Integer n_id;

            while (n_ids_it.hasNext()) {

                n_id = n_ids_it.next();

                // Unset previous optimistic neighbor
                if (neighborStatus.get(n_id) == PeerStatus.Optimistic) {
                    neighborStatus.put(n_id, PeerStatus.Choked);
	            message(n_id, Message.empty(Message.Type.Choke));
	            logger.info("Choked previous optimistic neighbor {} (self={})",
                        n_id, myid);
                }

                if ((neighborStatus.get(n_id) == PeerStatus.Choked)&&
                    (neighborInterestedStatus.get(n_id) == PeerInterestedStatus.Interested)) {
                    // This neighbor is choked and interested
                    potential.add(n_id);
                }
            }

            // If possible, pick a random optimistic peer
            if (potential.size() > 0) {
               
	        int randint = rand.nextInt(potential.size());
	        // Send unchoke message to the opportunistically unchoked neighbor
	        message(potential.get(randint), Message.empty(Message.Type.Unchoke));
                neighborStatus.put(potential.get(randint), PeerStatus.Optimistic);
	        logger.info("Optimistic neighbor {} chosen (self={})", potential.get(randint), myid);

	    }
            else {
	        logger.info("No optimistic neighbor chosen (self={})", myid);
            }

        }
    }

    /**
     * A peer has received a new piece
     */
    private class CompleteHandler implements Subscriber<Boolean> {
        public void onEvent(Event<Boolean> event) throws Exception {
            logger.info("all done, shutting down (self = {})", myid);
            for(Integer peer : neighbors.keySet()) {
                PeerProcess.dispatcher.publish(topic(String.format("peer/%d/close", peer)), true);
            }
            fH.close();
            System.exit(0);
        }
    }

    // send a message to peer id
    private static void message(int id, Message msg) {
        PeerProcess.dispatcher.publish(topic(PeerConnection.sendTopic(id)), msg);
    }

    private void registerTimers() {
        this.chokeTimer = new Timer("choke/unchoke");
        this.chokeTimer.scheduleAtFixedRate(new MessageTask<Boolean>(topic("interval/unchoke"), true),
            0, this.UnchokingInterval * 1000);

        this.optimisticTimer = new Timer("optimistic");
        this.optimisticTimer.scheduleAtFixedRate(new MessageTask<Boolean>(topic("interval/optimistic"), true),
            0, this.OptimisticUnchokingInterval * 1000);
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
