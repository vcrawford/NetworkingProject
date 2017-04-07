import java.util.Arrays;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Class to serve as an interface to all file related tasks
 */
public class FileHandle {
    private static final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
        .getLogger("project.networking.connection");
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private Integer myid;
    private String fileName;
    private Integer fileSize;
    private Integer pieceSize;
    private Integer numPieces; // file_size/piece_size
    BitSet myBitField;
    HashMap<Integer, BitSet> peerBitFields;
    HashMap<Integer, Integer> idxBeingRequested;
    RandomAccessFile f;
    Random rand;
    HashMap<Integer, Double> bwScores;

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
        this.idxBeingRequested = new HashMap<Integer, Integer>();
        this.bwScores = new HashMap<Integer, Double>();

        // Random object to generate random index to be requested
        this.rand = new Random(); // no seeding is necessary

        /* Open TheFile.dat */
        String fileNameWithPath = "peer_" + this.myid.toString() + File.separatorChar + this.fileName;
        try {
            f = new RandomAccessFile(fileNameWithPath, "rwd");
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
        return (BitSet)this.myBitField.clone();
    }

    public int getNumMissing() {
        lock.readLock().lock();
        int missing = 0;
        for(int i = 0; i < this.numPieces; i++) {
            if(!this.myBitField.get(i)) {
                missing += 1;
            }
        }
        lock.readLock().unlock();

        return missing;
    }

    public boolean allComplete() {
        lock.readLock().lock();
        if(getNumMissing() > 0) {
            lock.readLock().unlock();
            logger.debug("don't have complete file, not done (self = {})", myid);
            return false;
        }

        boolean done = true;
        for(Map.Entry<Integer, BitSet> entry : peerBitFields.entrySet()) {
            BitSet bs = entry.getValue();
            int missing = 0;
            for(int i = 0; i < this.numPieces; i++) {
                if(!bs.get(i)) {
                    missing += 1;
                }
            }
            if(missing > 0) {
                logger.debug("peer {} is missing {} pieces (self = {})", entry.getKey(), missing, myid);
                done = false;
            } else {
                logger.debug("peer {} has complete file (self = {})", entry.getKey(), myid);
            }
        }
        lock.readLock().unlock();

        return done;
    }

    /**
     * Whenever I receive a piece from peer, I update my own bit-field with this newly
     * received piece
     */
    private Boolean updateBitfield(Integer pieceIndex) {
        lock.writeLock().lock();
        if(pieceIndex >= this.numPieces) {
            logger.error("invalid piece given {} (max = {}) (self = {})", pieceIndex, this.numPieces, myid);
        }
        // Set the bit at pieceIndex to True
        this.myBitField.set(pieceIndex);

        logger.debug("Updating bitfield to have received piece {} (self={})",
                pieceIndex, this.myid);
        lock.writeLock().unlock();
        return checkAvailability();
    }


    /**
     * Returns True if I still need more pieces. Returns False otherwise
     */
    public Boolean checkAvailability() {
        return getNumMissing() > 0;
    }

    /**
     * Peer-thread calls this function. I am supposed to have bit-fields of each peer I am connected to. Whenever
     * Peer-thread receives a complete bit-field from peer, it calls this function to store peer's bit-field. This would
     * be called only once, right after receiving the bit-field from peer.
     */
    public void setBitfield(Integer peerid, BitSet peerBitField) {
        lock.writeLock().lock();
        // Store peer's bit-field
        this.peerBitFields.put(peerid, peerBitField);

        logger.debug("Storing the bitfield {} of peer {} (self={})",
                this.printableBitSet(peerBitField), peerid, this.myid);

        // TODO: Put proper bandwidth score
        this.bwScores.put(peerid, 0.0);
        lock.writeLock().unlock();
    }

    /**
     * Update whether a peer has a certain piece (when received have message)
     */
    public void updateHasPiece(Integer peerid, Integer piece) {
        lock.writeLock().lock();
        if(piece >= this.numPieces) {
            logger.error("invalid piece given {} (max = {}) (self = {})", piece, this.numPieces, myid);
        }

        BitSet peer_bits = this.peerBitFields.get(peerid);
        peer_bits.set(piece);
        logger.debug("Peer {} has been updated to have bit set {}.", peerid,
                printableBitSet(peer_bits));
        lock.writeLock().unlock();
    }

    /**
     * Check whether this peer is interested in that piece
     */
    public boolean interestedInPiece(Integer piece) {
        lock.readLock().lock();
        boolean interested = !this.myBitField.get(piece);
        lock.readLock().unlock();
        return interested;
    }

    /**
     * Get neighbor with id peerid's bitfield
     */
    private BitSet getBitfield(Integer peerid) {
        return this.peerBitFields.get(peerid);
    }

    /**
     * Return a BitSet with true at any index where the peer has a piece but
     * we do not
     */
    public BitSet interestingBits(Integer peerid) {
        lock.readLock().lock();
        BitSet neighbor_bits = this.getBitfield(peerid);

        if (neighbor_bits == null) {
            lock.readLock().unlock();
            // don't have this neighbor's bits
            return new BitSet(this.numPieces);
        }

        BitSet interesting_bits = (BitSet) neighbor_bits.clone();

        // find if they have something we don't
        interesting_bits.andNot(this.myBitField);

        lock.readLock().unlock();
        return interesting_bits;
    }

    /**
     * Is called by peer-thread to determine if I am interested in a peer whose bit-field is given as argument
     */
    public boolean checkInterest(Integer peerid) {
        logger.debug("checkInterest");
        // clone peer's bit field so we can do bit operations on it
        BitSet neighbor_bits = this.interestingBits(peerid);
        return (! neighbor_bits.isEmpty());
    }

    /**
     * Peer-thread calls this function. Function returns a piece-index that will be Peer-thread will request from
     * connected peer with id peerid
     */
    public Integer getPieceIndexToReceive(Integer peerid) {
        lock.readLock().lock();
        /*
         * Based on bit-field of peer, randomly returns a piece-index that the peer-thread has to request from connected
         * peer
         */
        // In case the full file have been received, return -1 directly
        Integer pieceIdx = -1;

        // Find what pieces that this peer has that we are interested in
        BitSet interesting_bits = this.interestingBits(peerid);

        // Has nothing interesting
        if (interesting_bits.isEmpty()) {
            lock.readLock().unlock();
            return -1;
        };

        ArrayList<Integer> interesting_indices = new ArrayList<Integer>();
        for (int i = 0; i < this.numPieces; i++) {
            if(interesting_bits.get(i) && !this.idxBeingRequested.containsValue(i)) {
                interesting_indices.add(i);
            }
        } 

        if(interesting_indices.size() != 0) {
            lock.readLock().unlock();
            lock.writeLock().lock();
            pieceIdx = interesting_indices.get(this.rand.nextInt(interesting_indices.size()));
            // Note down that this pieceIdx is being requested from this peerid
            // This is to make sure that no other peers are requested this piece
            this.idxBeingRequested.put(peerid, pieceIdx);
            logger.debug("Peer {} will request piece {} from {}", this.myid, pieceIdx, peerid);
            lock.writeLock().unlock();
        } else {
            lock.readLock().unlock();
            logger.debug("Peer {} has no interesting pieces to request from {}", this.myid, peerid);
        }

        return pieceIdx;
    }

    /**
     * This function is called in case the requested pieceIdx wasn't received because of timeout on the other peer's
     * side. In such case remove the pieceIdx from the idxBeingRequested. Now, any connected peer may get asked for this
     * pieceIdx
     * 
     * @param peerid
     */
    public void cancelPieceIndexRequest(Integer peerid) {
        lock.writeLock().lock();
        this.idxBeingRequested.remove(peerid);
        lock.writeLock().unlock();
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
        lock.writeLock().lock();
        idxBeingRequested.remove(pieceIdx);
        try {
            if(piece.length <= 0) {
                lock.writeLock().unlock();
                logger.error("Cannot write 0-length piece at index {}", pieceIdx);
                return false;
            }
            if(piece.length != this.pieceSize && pieceIdx != numPieces - 1) {
                lock.writeLock().unlock();
                logger.error("Piece {} is wrong size (actual = {}, expected = {}, self = {})", pieceIdx, piece.length, pieceSize, myid);
                return false;
            }
            if(pieceIdx * this.pieceSize + piece.length > f.length()) {
                lock.writeLock().unlock();
                logger.error("Cannot write piece {}, {} bytes is too large", pieceIdx, piece.length);
                return false;
            }
            f.seek(pieceIdx * this.pieceSize);
            f.write(piece);
            lock.writeLock().unlock();
            assert(f.getFilePointer() == pieceIdx * this.pieceSize + piece.length);
        } catch (IOException e) {
            lock.writeLock().unlock();
            logger.error("Failed writing {} of length {}", pieceIdx, piece.length);
            e.printStackTrace();
            return false; // this piece failed to write, we still need at least it again
        }

        logger.info("wrote piece {} successfully (self = {})", pieceIdx, myid);
        // Add this piece to my bit-field
        this.updateBitfield(pieceIdx);
        return true;
    }

    /**
     * Is called by the peer-thread.
     * 
     * @param pieceIdx
     * @return Length of piece that was read from file. In case of last-piece of file, 
     *         the length may be lesser than maxPieceLen.
     */
    public byte [] getPieceToSend(Integer pieceIdx) {
        lock.readLock().lock();
        Integer pieceLen = 0;
        Integer maxPieceLen = this.pieceSize;
        byte [] piece = new byte[maxPieceLen];

        try {
            f.seek(pieceIdx * this.pieceSize);
            pieceLen = f.read(piece);
        } catch (IOException e) {
            lock.readLock().unlock();
            logger.error("Failed reading piece {} to send", pieceIdx);
            e.printStackTrace();
            return null;
        }

        byte[] buf = Arrays.copyOfRange(piece, 0, pieceLen);
        lock.readLock().unlock();
        return buf;
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
        lock.readLock().lock();
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
        lock.writeLock().unlock();

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

    public String printableBitfield() {
        return printableBitSet(this.myBitField);
    }

    public void close() throws IOException {
        lock.writeLock().lock();
        this.f.getFD().sync();
        this.f.close();
        lock.writeLock().unlock();
    }
}
