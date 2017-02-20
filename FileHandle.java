/**
 * Class to serve as an interface to all file related tasks
 */
public class FileHandle{
	private String FileName;
	private int FileSize;
	private int PieceSize;
	
	/**
	 * Constructor.
	 */	
	public FileHandle(boolean haveFile){
		//TODO: Initialize these variable with correct values
		this.FileName = "";
		this.FileSize = 0;
		this.PieceSize = 0;
		
		if (haveFile){
			//TODO: Open TheFile.dat file here, if supposed to be available
			//TODO: Compute bitfield. All bits should be 1.
		}
		else{
			//TODO: Compute bitfield. All bits should be 0.			
		}
		
		//TODO: Data structure to lock a piece that a peer-thread is receiving
		// We don't want more than one thread receiving/requesting same piece
	}
	
	/**
	 * Peer-thread calls this function and used the returned bitfield to send 
	 * to connected peer/s
	 */		
	public void getBitfield(){
		//TODO: returns current status of bitfield
	}
	
	/**
	 * Is called by peer-thread to determine if I am intrested in a peer whose
	 * bitfield is given as argument
	 */		
	public boolean checkInterest(/* bitfield*/){
		//TODO: Check if I am interested in a peer
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
	
}