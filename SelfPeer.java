import java.net.*;
import java.io.*;

/**
 * Represents the peer that is running on this process
 */
public class SelfPeer {

   private int peerid; // the id of this peer
   private int listenport; // port number this peer listens on
   private ServerSocket listener; // socket this peer listens on
   private NeighborPeer[] neighbors; // other peers

   /**
    * Constructor for this peer
    * port is the port number this peer will listen on
    * TODO: right now, all peers are on localhost, need to change
    */
   public SelfPeer(int id, int port) throws IOException {

      this.peerid = id;
      this.listenport = port;
      this.listener = new ServerSocket(this.listenport);

   }

   /**
    * Read in neighbor info from file, build neighbors array
    */
   private void readNeighbors(String filename) {

   }

   /**
    * Listen for a message
    * TODO: Need to actually continue listening after one message
    * TODO: Multithreading
    */
   public void listenForConnection() throws Exception {

      // Listen for connection from another peer. Waits until connected. 
      Socket connection = this.listener.accept();

      // Connection started. Go do whatever.
      this.processConnection(connection);

      connection.close();
   }

   /**
    * Do whatever needs to be done with this connection
    * TODO: Probably need to be a new thread
    */
   public void processConnection(Socket connection) throws Exception {

      ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
      // read in message
      String msg = (String) in.readObject();
      System.out.println(msg);
      in.close();

   }

   /**
    * Send a message to another peer
    * TODO: Not just send messages to localhost
    * TODO: Send things over just one TCP connection
    */
   public void sendMessage(int port) throws IOException {

      Socket connection = new Socket("localhost", port);
      ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
      // write message
      out.flush();
      connection.close();
   } 

}

