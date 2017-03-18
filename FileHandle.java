import java.util.BitSet;
import java.util.HashMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Class to serve as an interface to all file related tasks
 */
public class FileHandle{
	private Integer myid;
	private String fileName;
	private int fileSize;
	private int pieceSize;
	BitSet myBitField;
	HashMap<Integer, BitSet> peerBitFields;
	// One of fin or fout will be NULL and won't be used during lifetime of process
	InputStream fin;
	OutputStream fout;
	
	/**
	 * Constructor.
	 */	
	public FileHandle(
			Integer myid,
			boolean haveFile, 
			String fileName, 
			Integer fileSize,
			Integer pieceSize){
		this.myid = myid;
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.pieceSize = pieceSize;
		
		// Set bit-field. All bits should be True or False
		Integer numPieces = (Integer) this.fileSize/this.pieceSize;
		this.myBitField = new BitSet(numPieces);
		this.myBitField.set(0, numPieces, haveFile);
		
		// Open TheFile.dat
		String fileNameWithPath = "peer_" + this.myid.toString()
				+ File.separatorChar + this.fileName;
		if (haveFile){
			// Open existing file
			try {
				InputStream fin = new FileInputStream(fileNameWithPath);
			} catch (FileNotFoundException e) {; 
				System.out.format("Unable to open: ", fileNameWithPath);
				e.printStackTrace();
			}
		}
		else{
			// Create TheFile.dat file
			try {
				OutputStream fout = new FileOutputStream(fileNameWithPath);
			} catch (FileNotFoundException e) {; 
				System.out.format("Unable to open: ", fileNameWithPath);
				e.printStackTrace();
			}
		}
		
		//TODO: Data structure to lock a piece that a peer-thread is receiving
		// We don't want more than one thread receiving/requesting same piece
		
	}
	
	/**
	 * Peer-thread calls this function and use the returned bit-field (which 
	 * represents my available pieces) to send to connected peer/s
	 */		
	public BitSet getBitfield(){
		return this.myBitField;
	}
	
	/**
	 * Peer-thread calls this function. Whenever I receive a piece from peer, I
	 * update my own bit-field with this newly received piece
	 */		
	public void updateBitfield(Integer pieceIndex){
		// Set the bit at pieceIndex to True
		//TODO: lock the file handle
		this.myBitField.set(pieceIndex);
		//TODO: unlock the file handle
	}	
	
	/**
	 * Peer-thread calls this function. I am supposed to have bit-fields of each 
	 * peer I am connected to. Whenever Peer-thread receives a complete  
	 * bit-field from peer, it calls this function to store peer's bitfield.
	 * This would be called only once, right after receiving the bit-field from 
	 * peer.
	 */		
	public void setBitfield(Integer peerid, BitSet peerBitField){
		//Store peer's bit-field
		this.peerBitFields.put(peerid, peerBitField);
	}
	
	/**
	 * Is called by peer-thread to determine if I am interested in a peer whose
	 * bit-field is given as argument
	 */		
	public boolean checkInterest(Integer peerid){
		//
		return true;
	}	
	
	/**
	 * Peer-thread calls this function with peer's bitfield as a parameter.
	 * Function returns a piece-index that will be requested by Peer-thread 
	 * from connected peer
	 */		
	public void getPieceIndexToReceive(/*bit-field from peer */){
		/* Based on bit-field of peer, randomly returns a piece-index that
		 * the peer-thread has to request from connected peer */
	}
	
	/**
	 * After receiving a piece from peer, the peer thread calls this function
	 * to write the acquired piece into disk
	 */		
	public void writePiece(Integer pieceIndex /*, data structure for piece*/){
		
	}
	
	/**
	 * Is called by the peer-thread. Returns piece based on the pieceIndex
	 * parameter. The peer-thread then sends the piece to connected peer.
	 */		
	public void getPieceToSend(Integer pieceIndex ){
		//TODO: Return the piece corresponding to pieceIndex
		
		return /*, data structure for piece*/;
	}	
	
	
	/**
	 * Is called by the peer-thread. Returns an array of scores of all connected
	 * peers. This score is used to determine Preferred neighbors in case of 
	 * Unchoking Interval timeout. Higer score represents higher bandwidth. 
	 */		
	public void getBandwidthScore(Integer peerid ){
		//TODO: implementation
		
		return /* array of bandwidth scores */;
	}	
}