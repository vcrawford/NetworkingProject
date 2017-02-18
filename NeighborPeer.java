/**
 * Represents neighbor peers in communication with this process
 */
public class NeighborPeer{

   private int peerid; // id of this peer
   private int port; // port number this neighbor listens on

   public NeighborPeer(int id, int port) {

      this.peerid = id;
      this.port = port;
   }

   public int getID() {
      return this.peerid;
   }

   public int getPort() {
      return this.port;
   }

}


