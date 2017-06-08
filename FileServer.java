import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class FileServer extends UnicastRemoteObject implements ServerInterface {

	int port;								
	Vector<CachedFile> cache;		

	public FileServer(int port) throws RemoteException {

		this.port = port;
		cache = new Vector<CachedFile>();

	}

	public FileContents download(String clientIP, String filename, String mode) throws RemoteException{
		
		// pointer for the file
		CachedFile file = getCachedFile(filename);
		
		// if file is not in cache, read it from disk 
		if(file == null){
			try{
				file = new CachedFile(filename);
			}
			catch(IOException e){
				throw new RemoteException("Requested file is not on disk.");
			}
			cache.add(file);
		}
		
		System.out.println("File created: " +  filename);
		// Register the client with the file
		ClientProxy client = new ClientProxy(clientIP, Integer.toString(port));
		
		// if read requested, add the client as a reader and update
		// state if necessary
		if(mode.equals("r")){
			file.addReader(client);
			if((file.state != CachedFile.WRITE_SHARED) && (file.state != CachedFile.OWNERSHIP_CHANGE)){
				file.state = CachedFile.READ_SHARED;
			}
		}
		// if write requested, add the client as a writer and update 
		// state if necessary
		else if(mode.equals("w")){
			file.addWriter(client);
			if(file.state == CachedFile.READ_SHARED){
				file.state = CachedFile.WRITE_SHARED;
			}
			else if(file.state == CachedFile.WRITE_SHARED){
				file.state = CachedFile.OWNERSHIP_CHANGE;
			}
		}	
		
		return file.getContents();
	}

	/** 
	 * Updates the contents of a file on client request
	 */
	public boolean upload( String client, String filename, FileContents contents ) throws RemoteException{
		
		CachedFile file = getCachedFile(filename);
		
		// return false if file is not in cache
		if((file == null) || (file.state == CachedFile.NOT_SHARED) || (file.state == CachedFile.READ_SHARED)){
			return false;
		}
		
		return file.update(contents);
		
	}

	/**
	 * Searches the cache for a file. If the file is cached, it is 
	 * returned, if not, a new cache entry is created for the file.
	 */
	private CachedFile getCachedFile(String filename){
		
		for(CachedFile file : cache){
			if(file.getName().equals(filename)){
				return file;
			}
		}
		
		return null;
				
	}

	public static void main (String args[]) {

		if ( args.length != 1 ) {
			System.err.println( "usage: java FileServer port#" );
			System.exit( -1 );
		}

		try {
			int port = Integer.parseInt(args[0]);
			System.out.println("Starting server...");
			//startRegistry(port);
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