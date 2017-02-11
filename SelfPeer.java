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
   private Boolean finished = false; // whether the peer sharing process is finished

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

      while (!this.finished) {
         // Listen for connection from another peer. 
         // Once connection started, does whatever in a separate thread.
         new ProcessConnection(this.listener.accept()).start();
      }
   }

   /**
    * Thread class to process a connection between this and another peer
    */
   private static class ProcessConnection extends Thread {
      
      private Socket connection; // the connection the peers are communicating on
      private ObjectInputStream in;

      public ProcessConnection(Socket connection) {
         
         this.connection = connection;
      }

      public void run() {

         try {
            this.in = new ObjectInputStream(this.connection.getInputStream());
            String msg = (String) in.readObject();
            System.out.println(msg);
            in.close();
            connection.close();
         } catch (Exception e) {
            //TODO: something ...
         }
      }

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
      out.writeObject("Hello World!");
      out.flush();
      connection.close();
   } 

}

