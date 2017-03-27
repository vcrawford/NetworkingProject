import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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
		this.numPieces = (Integer) this.fileSize / this.pieceSize;
		this.myBitField = new BitSet(this.numPieces);
		this.myBitField.set(0, this.numPieces, hasFile);
		// this.myBitField would contain some extra bits to align it to 4-byte
		// boundary. Set those bits to true. This is required, otherwise the
		// toByteArray function in Message.bitfield() is making the all-zero
		// BitSet to a size 0 BitSet
		this.myBitField.set(this.numPieces, this.myBitField.size(), true);

		this.peerBitFields = new HashMap<Integer, BitSet>();
		this.idxBeingRequested = new HashSet<Integer>();
		this.bwScores = new HashMap<Integer, Double>();

		// Random object to generate random index to be requested
		this.rand = new Random(1337);

		/* Open TheFile.dat */
		String fileNameWithPath = "peer_" + this.myid.toString() + File.separatorChar + this.fileName;
		try {
			f = new RandomAccessFile(fileNameWithPath, "rw");
		} catch (FileNotFoundException e) {
			logger.error("Unable to open: {}", fileNameWithPath);
			e.printStackTrace();
		}
		// Allocate on disk to enable random seeks
		if (hasFile == false) {
			try {
				f.setLength(this.fileSize);
			} catch (IOException e) {
				logger.error("Unable to allocate {} bytes memory", this.fileSize);
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
	 * Peer-thread calls this function. Whenever I receive a piece from peer, I update my own bit-field with this newly
	 * received piece
	 */
	public void updateBitfield(Integer pieceIndex) {
		// Set the bit at pieceIndex to True
		// lock the file handle
		synchronized (lock) {
			this.myBitField.set(pieceIndex);
		}
	}

	/**
	 * Peer-thread calls this function. I am supposed to have bit-fields of each peer I am connected to. Whenever
	 * Peer-thread receives a complete bit-field from peer, it calls this function to store peer's bit-field. This would
	 * be called only once, right after receiving the bit-field from peer.
	 */
	public void setBitfield(Integer peerid, BitSet peerBitField) {
		// Store peer's bit-field
		this.peerBitFields.put(peerid, peerBitField);

		// TODO: Put proper bandwidth score
		this.bwScores.put(peerid, 0.0);
	}

	/**
	 * Is called by peer-thread to determine if I am interested in a peer whose bit-field is given as argument
	 */
	public boolean checkInterest(Integer peerid) {
		// TODO: proper implementation
		return (! this.hasFile);
	}

	/**
	 * Peer-thread calls this function. Function returns a piece-index that will be Peer-thread will request from
	 * connected peer
	 */
	public Integer getPieceIndexToReceive() {
		/*
		 * Based on bit-field of peer, randomly returns a piece-index that the peer-thread has to request from connected
		 * peer
		 */
		// In case the full file have been received, return -1 directly
		Integer pieceIdx = -1;

		synchronized (lock) {
			/*
			 * get a random pieceIdx from myBitField that is not yet received also that is not present in
			 * idxBeingRequested
			 */
			while (this.hasFile == false) {
				Integer randIdx = this.rand.nextInt(this.numPieces);

				// First check if this piece-index is already received
				if (this.myBitField.get(randIdx) == true)
					continue;

				// Then check if it is already being requested
				if (this.idxBeingRequested.contains(randIdx) == false) {
					pieceIdx = randIdx;
					break;
				}
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
	public void writePiece(Integer pieceIdx, byte[] piece, Integer pieceLen) {
		try {
			f.write(piece, pieceIdx * this.pieceSize, pieceLen);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Is called by the peer-thread.
	 * 
	 * @param pieceIndex
	 * @param piece
	 *            A byte-array. Must be allocated by the caller
	 * @param maxPieceLen
	 *            Size of byte-array
	 * @return Length of piece that was read from file. In case of last-piece of file, the length may be lesser than
	 *         maxPieceLen.
	 */
	public Integer getPieceToSend(Integer pieceIndex, byte[] piece, Integer maxPieceLen) {
		Integer pieceLen = 0;

		try {
			pieceLen = f.read(piece, pieceIndex * this.pieceSize, maxPieceLen);
		} catch (IndexOutOfBoundsException e) {
			/*
			 * Only able to read few bytes because this piecce was located near EOF. Data in piece is still valid
			 */
			return pieceLen;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return pieceLen;
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
}