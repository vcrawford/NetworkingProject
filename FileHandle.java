import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Random;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Class to serve as an interface to all file related tasks
 */
public class FileHandle {
	private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger("project.networking.connection");

	private Integer myid;
	private boolean hasFile;
	private String fileName;
	private Integer fileSize;
	private Integer pieceSize;
	private Integer numPieces; // file_size/piece_size
	BitSet myBitField;
	HashMap<Integer, BitSet> peerBitFields;
	HashSet<Integer> idxBeingRequested;
	RandomAccessFile f;
	Random rand;
	HashMap<Integer, Double> bwScores;

	// Data structure to apply a lock before self-inquiring which piece to
	// request. We don't want more than one thread requesting same piece
	Object lock;

	/**
	 * Constructor
	 * 
	 * @param myid
	 *            my ID
	 * @param haveFile
	 *            A boolean if I have the complete file when process starts
	 * @param fileName
	 *            The data file without path.
	 * @param fileSize
	 * @param pieceSize
	 */
	public FileHandle(Integer myid, boolean hasFile, String fileName, Integer fileSize, Integer pieceSize) {

		this.logger.setLevel(Level.DEBUG);
		this.myid = myid;
		this.hasFile = hasFile;
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.pieceSize = pieceSize;

		// Set bit-field. All bits should be True or False
		this.numPieces = (int) Math.ceil(this.fileSize / (double)this.pieceSize);
		this.myBitField = new BitSet(this.numPieces);
		this.myBitField.set(0, this.numPieces, hasFile);

		// this.myBitField would contain some extra bits to align it to 4 or 
		// 8-byte boundary. Set those bits to true. This is required, otherwise
		// the toByteArray function in Message.bitfield() is making the all-zero
		// BitSet to a size 0 BitSet
		this.myBitField.set(this.numPieces, this.myBitField.size(), true);

                // log, but without extra bits
                logger.debug("Peer {} has initiated a file handler with bitfield set to {}",
                    myid, this.printableBitSet(this.myBitField));

		this.peerBitFields = new HashMap<Integer, BitSet>();
		this.idxBeingRequested = new HashSet<Integer>();
		this.bwScores = new HashMap<Integer, Double>();

		this.lock = new Object();
		
		// Random object to generate random index to be requested
                // seed at id so peers generate different random numbers
		this.rand = new Random(myid);

		/* Open TheFile.dat */
		String fileNameWithPath = "peer_" + this.myid.toString() + File.separatorChar + this.fileName;
		try {
			f = new RandomAccessFile(fileNameWithPath, "rw");
		} catch (FileNotFoundException e) {
			logger.error("Failed to open: {}", fileNameWithPath);
			e.printStackTrace();
		}
		// Allocate on disk to enable random seeks
		if (hasFile == false) {
			try {
				f.setLength(this.fileSize);
			} catch (IOException e) {
				logger.error("Failed to allocate {} bytes memory", this.fileSize);
				e.printStackTrace();
			}
		}
	}

	/**
	 * Peer-thread calls this function and use the returned bit-field (which represents my available pieces) to send to
	 * connected peer/s
	 */
	public BitSet getBitfield() {
		return this.myBitField;
	}

	/**
	 * Whenever I receive a piece from peer, I update my own bit-field with this newly
	 * received piece
	 */
	private Boolean updateBitfield(Integer pieceIndex) {
		// Set the bit at pieceIndex to True
		// lock the file handle
		synchronized (lock) {
			this.myBitField.set(pieceIndex);
		}

		logger.debug("Updating bitfield to have received piece {} (self={})",
		    pieceIndex, this.myid);
		
		return checkAvailability();
	}


	/**
	 * Returns True if I still need more pieces. Returns False otherwise
	 */
	private Boolean checkAvailability() {
		Boolean needMore = (this.myBitField.nextClearBit(0) < this.numPieces); 
		return needMore;
	}
	
	/**
	 * Peer-thread calls this function. I am supposed to have bit-fields of each peer I am connected to. Whenever
	 * Peer-thread receives a complete bit-field from peer, it calls this function to store peer's bit-field. This would
	 * be called only once, right after receiving the bit-field from peer.
	 */
	public void setBitfield(Integer peerid, BitSet peerBitField) {
		// Store peer's bit-field
		this.peerBitFields.put(peerid, peerBitField);

                logger.debug("Storing the bitfield {} of peer {} (self={})",
                    this.printableBitSet(peerBitField), peerid, this.myid);

		// TODO: Put proper bandwidth score
		this.bwScores.put(peerid, 0.0);
	}

        /**
         * Get neighbor with id peerid's bitfield
         */
        public BitSet getBitfield(Integer peerid) {

            return this.peerBitFields.get(peerid);

        }

        /**
         * Return a BitSet with true at any index where the peer has a piece but
         * we do not
         */
        public BitSet interestingBits(Integer peerid) {

                BitSet neighbor_bits = this.getBitfield(peerid);

                if (neighbor_bits == null) {
                    // don't have this neighbor's bits
		    return new BitSet(this.numPieces);
                }

                BitSet interesting_bits = (BitSet) neighbor_bits.clone();

                // find if they have something we don't
                neighbor_bits.andNot(this.myBitField);

                return neighbor_bits;
        }

	/**
	 * Is called by peer-thread to determine if I am interested in a peer whose bit-field is given as argument
	 */
	public boolean checkInterest(Integer peerid) {

                // clone peer's bit field so we can do bit operations on it
                BitSet neighbor_bits = this.interestingBits(peerid);

                logger.debug("Interested in pieces {} from peer {} (self={})",
                    this.printableBitSet(neighbor_bits), peerid, this.myid);

		return (! neighbor_bits.isEmpty());
	}

	/**
	 * Peer-thread calls this function. Function returns a piece-index that will be Peer-thread will request from
	 * connected peer with id peerid
	 */
	public Integer getPieceIndexToReceive(Integer peerid) {
		/*
		 * Based on bit-field of peer, randomly returns a piece-index that the peer-thread has to request from connected
		 * peer
		 */
		// In case the full file have been received, return -1 directly
		Integer pieceIdx = -1;

                // Find what pieces that this peer has that we are interested in
                BitSet interesting_bits = this.interestingBits(peerid);

                // Has nothing interesting
                if (interesting_bits.isEmpty()) return -1;

		synchronized (lock) {
			/*
			 * get a random pieceIdx from myBitField that is not yet received also that is not present in
			 * idxBeingRequested
			 */
			while (this.hasFile == false) {

                                Integer rand_num = this.rand.nextInt(this.numPieces);

                                // Take this random number to a bit that is "interesting"
                                // One must exist, it's either next or previous
                                Integer randIdx = interesting_bits.nextSetBit(rand_num);
                                if (randIdx == -1) {
                                         // There did not exist a next set bit
                                         randIdx = interesting_bits.previousSetBit(rand_num);  
                                }

				// Check if index is already being requested
				if (this.idxBeingRequested.contains(randIdx) == false) {
					pieceIdx = randIdx;
					break;
				}

                                logger.debug("Peer {} will request piece {} from {}", this.myid, randIdx, peerid);
			}

			if (pieceIdx != -1) {
				// Note down that this pieceIdx is being requested from this peerid
				// This is to make sure that no other peers are requested this piece
				this.idxBeingRequested.add(pieceIdx);
			}
		}

		return pieceIdx;
	}

	/**
	 * This function is called in case the requested pieceIdx wasn't received because of timeout on the other peer's
	 * side. In such case remove the pieceIdx from the idxBeingRequested. Now, any connected peer may get asked for this
	 * pieceIdx
	 * 
	 * @param peerid
	 * @param pieceIdx
	 */
	public void cancelPieceIndexRequest(Integer pieceIdx) {
		this.idxBeingRequested.add(pieceIdx);
	}

	/**
	 * After receiving a piece from peer, the peer thread calls this function to write the acquired piece into disk
	 * 
	 * @param pieceIdx
	 * @param piece
	 *            Byte-array holding piece inside it
	 * @param pieceLen
	 *            Length of piece. For the last pieceIdx this parameter should specify the length of valid bytes inside
	 *            piece
	 */
	public Boolean writePiece(Integer pieceIdx, byte[] piece) {
		try {
            if(piece.length <= 0) {
                logger.error("Cannot write 0-length piece at index {}", pieceIdx);
                return checkAvailability();
            }
            if(pieceIdx * this.pieceSize + piece.length > f.length()) {
                logger.error("Cannot write piece {}, {} bytes is too large", pieceIdx, piece.length);
                return checkAvailability();
            }
            f.seek(pieceIdx * this.pieceSize);
            f.write(piece);
		} catch (IOException e) {
			logger.error("Failed writing {} of length {}", pieceIdx, piece.length);
			e.printStackTrace();
		}
		
		// Add this piece to my bit-field
		this.updateBitfield(pieceIdx);
		return checkAvailability();
	}

	/**
	 * Is called by the peer-thread.
	 * 
	 * @param pieceIdx
	 * @return Length of piece that was read from file. In case of last-piece of file, 
	 *         the length may be lesser than maxPieceLen.
	 */
	public byte [] getPieceToSend(Integer pieceIdx) {
		Integer pieceLen = 0;
		Integer maxPieceLen = this.pieceSize;
		byte [] piece = new byte[maxPieceLen];

		try {
            f.seek(pieceIdx * this.pieceSize);
			pieceLen = f.read(piece);
		} catch (IOException e) {
			logger.error("Failed reading piece {} to send", pieceIdx);
			e.printStackTrace();
            return null;
		}

		return Arrays.copyOfRange(piece, 0, pieceLen);
	}

	/**
	 * Is called by the peer-thread. Returns an array of scores of all connected peers. This score is used to determine
	 * Preferred neighbors in case of Unchoking Interval timeout. Higher score represents higher bandwidth.
	 * 
	 * @param k
	 *            Number of preferred neighbors
	 * @return Integer array of k peer IDs
	 */
	public Integer[] getPreferredNbrs(Integer k) {
		/*
		 * TODO: Populate array by bwScore values. Currently just sending the first neighbors in the hashmap
		 */
		Integer numPeers = this.bwScores.size();
		Integer[] ids = new Integer[k];
		Integer id_counter = 0;

		for (Integer id : this.bwScores.keySet()) {
			ids[id_counter] = id;
			id_counter++;
			if (id_counter == k)
				break;
		}

		/*
		 * Towards the start, when numbers of peers that are connected are lesser than k, fill rest of the peer ids as
		 * -1. Caller should take care of this
		 */
		for (Integer i = numPeers; i < k; i++) {
			ids[i] = -1;
		}

		return ids;
	}

    public Integer maxPiece() {
        return numPieces;
    }

    /**
     * For printing bits without the end bits
     */
    public String printableBitSet(BitSet bits) {
        return bits.get(0, this.numPieces).toString();
    }
}
