/**
 * Represents neighbor peers in communication with this process
 */
public class NeighborPeer{

   private int peerid; // id of this peer
   private int port; // port number this neighbor listens on
   private String hostName;

   public NeighborPeer(int id, int port, String host) {
      this.peerid = id;
      this.port = port;
      this.hostName = host;
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

}


