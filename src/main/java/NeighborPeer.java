import java.util.BitSet;

/**
 * Represents neighbor peers in communication with this process
 */
public class NeighborPeer{

   private int peerid; // id of this peer
   private int port; // port number this neighbor listens on
   private String hostName;
   private BitSet pieces;

   public NeighborPeer(int id, int port, String host) {
      this.peerid = id;
      this.port = port;
      this.hostName = host;
      this.pieces = new BitSet();
   }

   public int getID() {
      return this.peerid;
   }

   public int getPort() {
      return this.port;
   }

   public String getHostName() {
      return this.hostName;
   }

   public void addPieces(BitSet pcs) {
       this.pieces.or(pcs);
   }

   public Boolean interested(BitSet pcs) {
       BitSet bs = (BitSet)this.pieces.clone();
       bs.andNot(pcs);
       return !bs.isEmpty();
   }

}


