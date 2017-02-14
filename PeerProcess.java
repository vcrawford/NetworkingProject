import java.net.*;
import java.io.*;

/**
 * Represents the peer that is running on this process
 */
public class PeerProcess {

   private int peerid; // the id of this peer
   private int listenport; // port number this peer listens on
   private ServerSocket listener; // socket this peer listens on
   private NeighborPeer[] neighbors; // other peers
   private Boolean finished = false; // whether the peer sharing process is finished
   private static final String commonCfgFileName = "Common.cfg";
   private static final String peerInfoFileName = "PeerInfo.cfg";

   // variables set in config file
   private int NumberOfPreferredNeighbors;
   private int UnchokingInterval;
   private int OptimisticUnchokingInterval;
   private String FileName;
   private int FileSize;
   private int PieceSize;
   
   public static void main(String[] args) throws IOException {
	   int id = Integer.parseInt(args[0]);
	   PeerProcess peerProcess = new PeerProcess(id);
   }
   
   /**
    * Constructor for this peer
    * port is the port number this peer will listen on
    * TODO: right now, all peers are on localhost, need to change
    */   
   public PeerProcess(int id) throws IOException {
	      this.peerid = id;
	      readCommonCfgFile();
	      readPeerInfoCfgFile();

    	  System.out.format("Peer: %d Port: %d\n", 
    			  this.peerid, this.listenport);	      
	      
	      this.listener = new ServerSocket(this.listenport);
   }
   
   /**
    * Read in variables from config file, set all appropriate variables
    */
   public void readPeerInfoCfgFile() {
      try {
         BufferedReader reader = new BufferedReader(new FileReader(this.peerInfoFileName));
         String line = reader.readLine();
         String[] split_line = line.split(" ");

         //Loop over all lines in config file
         while (line != null) {
        	 if (! line.startsWith("#")){ //ignore comments
	        	 int id = Integer.parseInt(split_line[0]);
	        	 String hostname = split_line[1];
	        	 int port = Integer.parseInt(split_line[2]);
	        	 boolean hasFile = (split_line[3].equals("1"));
	        	 
	             //TODO: Add neighboring peers' info to NeighborPeer array here
	        	 
	        	 //Set current peer's port number
	        	 if (id == this.peerid){
	        		 this.listenport = port;
	        	 }
        	 }

             line = reader.readLine();
             split_line = line.split(" ");
          }         
         reader.close();

      } catch (Exception e) {
         // TODO: something ...
      }
   }   
   
   /**
    * Read in variables from config file, set all appropriate variables
    */
   private void readCommonCfgFile() {

      try {
         BufferedReader reader = new BufferedReader(new FileReader(this.commonCfgFileName));
         String line = reader.readLine();
         String[] split_line = line.split(" ");

         while (line != null) {

            switch (split_line[0]) {
               case "NumberOfPreferredNeighbors":
                  this.NumberOfPreferredNeighbors = Integer.parseInt(split_line[1]);
                  break;
               case "UnchokingInterval":
                  this.UnchokingInterval = Integer.parseInt(split_line[1]);
                  break;                  
               case "OptimisticUnchokingInterval":
                  this.OptimisticUnchokingInterval = Integer.parseInt(split_line[1]);
                  break;                  
               case "FileName":
                  this.FileName = split_line[1];
                  break;                  
               case "FileSize":
                  this.FileSize = Integer.parseInt(split_line[1]);
                  break;                  
               case "PieceSize":
                  this.PieceSize = Integer.parseInt(split_line[1]);
                  break;                  
            }

            line = reader.readLine();
            split_line = line.split(" ");
         }

         reader.close();

      } catch (Exception e) {
         // TODO: something ...
      }

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

