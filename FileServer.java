import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;

public class FileServer extends UnicastRemoteObject implements ServerInterface {

	int port;						//port number
	ArrayList<CachedFile> fileCache;		//all files currently cached

	public FileServer(String port) throws RemoteException {

		this.port = port;
		fileCache = new ArrayList<CachedFile>();

	}


	public static void main (String args[]) {

		
		if ( args.length != 1 ) {
	    	System.err.println( "usage: java FileServer port#" );
	    	System.exit( -1 );
		}

		try {
			System.out.println("Starting server...");
		    FileServer server = new FileServer(Integer.parseInt(args[0]));

		    //bind server name to ip adress
		    Naming.rebind( "rmi://localhost:" + args[0] + "/fileserver", server );
			System.out.println("Server started.");

		} catch ( Exception e ) {
		    e.printStackTrace( );
		    System.exit( 1 );
		}
		
	}
	public FileContents download( String client, String filename, String mode )
	throws RemoteException;
    public boolean upload( String client, String filename, 
			   FileContents contents ) throws RemoteException;



    private CachedFile getCachedFile(String filename){
    	for(CachedFile file : fileCache){
    		if(file.getFileName().equals(filename)){
    			return file;
    		}
    	}
    	return null;
    }



}