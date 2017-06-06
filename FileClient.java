import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;
import java.net.*;

public class FileClient extends UnicastRemoteObject implements ServerInterface {

	private String ipName;				//ip name of the client
	private ServerInterface fileServer;	//remote server for DFS
	//private int port;
	
	private String fileName;			//name of the file cached locally
	private File cachedFile;			//reference to the cached file
	private String accessMode;			//current file access mode such as read or write
	private boolean ownership;			//indicates if client owns the file - or is exclusively writing the file
	private CacheState state = CacheState.INVALID;	//current state of the cache

	//possible states of the cache
	private enum CacheState { INVALID, READ_SHARED, WRITE_OWNED, MODIFIED_OWNED, RELEASE_OWNERSHIP};


	public FileClient(ServerInterface fileServer) throws RemoteException {

		this.fileServer = fileServer;
		String username = System.getProperty("user.name");
		cachedFile = new File("/tmp/" + username + ".txt").toPath());
		if(!cachedFile.exists()){
			cachedFile.createNewFile();
			cachedFile.setReadable(true);
		}
		//get ip name of the client
		ipName = InetAddress.getLocalHost().getHostName();
	}


	public static void main (String args[]) {

		if ( args.length != 1 ) {
		    System.err.println( "usage: java FileClient ipAddress port#" );
		    System.exit( -1 );
		}
		//port = Integer.parseInt(args[0]);

		try {
			System.out.println("Connecting to server ...");
			//look for the server instance that the client wants to access
			String serverAddress = String.format("rmi://%s:%s/fileserver", args[0], args[1]);
			fileServer =  ( ServerInterface ) Naming.lookup( serverAddress, fileserver );

			System.out.println("Starting client ...");
		    FileClient client = new FileServer(fileServer);
		    //bind client name to ip address
		    Naming.rebind( "rmi://localhost:" + args[0] + "/fileclient", client );

		    //to get user input
		    Scanner input = new Scanner( System.in );		
		
		
		
			while(true){

	            System.out.print( "FileClient: file [r/w]: " );
	            String filename = input.next( );  // read a file name to operate                   
	            String mode = input.next( );      // check the read/write mode of this file

	            System.out.println("FileClient: Next file to open");
				String filename = input.next( );  // read a file name to operate   
				System.out.println("How(r/w");	  
				char mode = input.next().charAt(0);	//get mode from user
			}	




		}
		catch ( Exception e ) {
		    e.printStackTrace( );
		    System.exit( 1 );
		}



	}

	
	public synchronized boolean invalidate( ) throws RemoteException {

		 // set the DFS client’s file state to “Invalid”
		if(state == CacheState.READ_SHARED){
			state = CacheState.INVALID;
			return true;
		}
		return false;
	}

    public boolean writeback( ) throws 	RemoteException;





}