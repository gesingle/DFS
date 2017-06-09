/*
*	@author Tiana Greisel and Garrett Singletary
*	@title	CSS434 - Program 4 Distributed File System
*	
*/

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class FileServer extends UnicastRemoteObject implements ServerInterface {

	int port;												//port the server is bound too			
	Vector<CachedFile> cache;								//vector of cached files	

	//Constructor - constructs a FileServer object.  
	public FileServer(int port) throws RemoteException {

		//set the port the server is bound to
		this.port = port;
		//create the vector of cached files
		cache = new Vector<CachedFile>();
	}

	//method called remotely to download a given file in a given mode.
	public FileContents download(String clientIP, String filename, String mode) throws RemoteException{
		
		//remove all the readers for the given files reader list since ownership is changing and their cache will be invalidated
		for (CachedFile file : cache) {
			if (!file.getName().equals(filename))
				file.removeReader(clientIP);
		}
		CachedFile file = getCachedFile(filename);

        // If file is not cached, cache it
		if (file == null) {

			try {
				//create a new file
				file = new CachedFile(filename);
			} catch (IOException e) {
				throw new RemoteException("Could not open file", e);
			}
			//add the file to the cache
			cache.add(file);
		}
        // Regiser client for access to this file.
		try {

			ClientProxy client = new ClientProxy(clientIP, Integer.toString(port));
			if (mode.equals("r")) {
				//register the client as a reader
				file.registerReader(client);
			} else {
				//add client as a writer (owner)
				file.addWriter(client);
			}
			//return contents of the file
			return file.getContents();
		} catch (IllegalArgumentException e) {
			throw new RemoteException("Invalid request", e);
		}
	}

	/** 
	 * Updates the contents of a file on client request
	 */
	public boolean upload( String client, String filename, FileContents contents ) throws RemoteException{
		
		//get the file to be uploaded and update it
		CachedFile file = getCachedFile(filename);
		return file.update(client, contents);
	}

	/**
	 * Searches the cache for a file. If the file is cached, it is 
	 * returned, if not, a new cache entry is created for the file.
	 */
	private CachedFile getCachedFile(String filename){
		
		//look for file with filename and return it
		for(CachedFile file : cache){
			if(file.getName().equals(filename)){
				return file;
			}
		}
		return null;		
	}

	//main function - creates a FileServer object and bind to name and port 
	//that clients can remotely request files server has cached.
	public static void main (String args[]) {

		if ( args.length != 1 ) {
			System.err.println( "usage: java FileServer port#" );
			System.exit( -1 );
		}

		try {
			int port = Integer.parseInt(args[0]);
			System.out.println("Starting server...");
			//create fileserver object
			FileServer server = new FileServer(port);
			//bind server name to ip adress
			Naming.rebind( "rmi://localhost:" + port + "/fileserver", server );
			System.out.println("Server started.");
		} 
		catch ( Exception e ) {
			e.printStackTrace();
			System.exit( 1 );
		}
	}
	

}