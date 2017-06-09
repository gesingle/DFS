/*
*	@author Tiana Greisel and Garrett Singletary
*	@title	CSS434 - Program 4 Distributed File System
*	
*/

import java.io.*;
import java.util.*;
import java.rmi.*;
import java.rmi.server.*;
import java.net.*;
import java.net.UnknownHostException;

public class FileClient extends UnicastRemoteObject implements ClientInterface {

	private String ipName;				//ip name of the client
	private ServerInterface fileServer;	//remote server for DFS
	private String fileName = null;			//name of the file cached locally
	private File cachedFile;			//reference to the cached file
	private String localPath;			//local path to file cache
	//private String accessMode;			//current file access mode such as read or write
	private boolean ownership;			//indicates if client owns the file - or is exclusively writing the file
	private CacheState state = CacheState.INVALID;	//current state of the cache

	//possible states of the cache
	private enum CacheState {INVALID, READ_SHARED, WRITE_OWNED, MODIFIED_OWNED, RELEASE_OWNERSHIP};

	//Constructor, constructs a FileClient by passing it the FileServer object it wishes
	//to connect with and sets the local path, and default file for the user.
	public FileClient(ServerInterface fileServer) throws RemoteException, IOException{

		this.fileServer = fileServer;
		String username = System.getProperty("user.name");
		localPath = "/tmp/" + username + ".txt";	//set local path to file cache
		cachedFile = new File("/tmp/" + username + ".txt");		//create a file to store into “/tmp/useraccount.txt”
		if(!cachedFile.exists()){
			cachedFile.createNewFile();
			cachedFile.setReadable(true);	//allow file to be readable by default
		}
		//get ip name of the client
		try{
			ipName = InetAddress.getLocalHost().getHostName();
		}
		catch(UnknownHostException e){}
	}

	//opens a file given the parameter's filename and the mode requested by the user.
	public synchronized void openFile(String fname, char mode) throws IOException {

		if(!fname.equals(fileName) && fname != null){
			//files don't match, upload current file to server if state is writeowned or modified owned
			if(state == CacheState.WRITE_OWNED || state == CacheState.MODIFIED_OWNED){
				uploadFile();
				state = CacheState.INVALID;		//set state to invalid so client can download desired file from server
			}
		}
		
		//check state of cache to determine if client downloads server file or not
		switch(state){

			case INVALID:
				//download requested file from server
				downloadFile(fname, mode);
				if(mode == 'r'){
					state = CacheState.READ_SHARED;
				}
				else{
					state = CacheState.WRITE_OWNED;
				}
				break;
			case WRITE_OWNED:
				//user can read or write in this state, nothing to do
				break;	//do nothing
			case READ_SHARED:
				//if user wants to open file for writing, must get writeowned permissions from server with file
				if(mode == 'w'){
					downloadFile(fname, mode);
					state = CacheState.WRITE_OWNED;
				}
				break;
			case MODIFIED_OWNED:
				//user already completed a session with file before, change state back to write since file was uploaded back to server
				state = CacheState.WRITE_OWNED;
				break;
			default:
				throw new IllegalStateException();

		}
	}

	//calls the server's remote upload function to upload a file back to the server.
	private boolean uploadFile() throws IOException {

		return fileServer.upload(ipName, fileName, getFileContents());
	}

	//calls the servers remote download function to download a new file from the server.
	private void downloadFile(String filename, char mode) throws IOException {

		//download filename from server and get FileContents
		FileContents contents = fileServer.download(ipName, filename, Character.toString(mode));
		//set the clients cached file name and allow file to be writable
		fileName = filename;
		cachedFile.setWritable(true);	//allow file to be modified to write filecontents to it

		//get contents from FileContents object and transfer to File object stored in client
		FileOutputStream writer = new FileOutputStream(cachedFile);
		writer.write(contents.get());		//write contents of FileContents to cached file
		writer.close();		//close file
		cachedFile.setWritable(mode == 'w');	//set file to writable if client opened file for writing
	}

	//returns the file contents of the currently cached file
	private FileContents getFileContents() throws FileNotFoundException, IOException {

		//byte array to read file contents into that is the size of the currently cached file
		byte[] data = new byte[(int) cachedFile.length()];
		FileInputStream reader = new FileInputStream(cachedFile);
		reader.read(data);
		FileContents contents = new FileContents(data); //create FileContents object with contents of cached file
		return contents;
	}

	//opens an emacs session for the client
	public void runEmacs() {

		System.out.println("Starting emacs session.");
        try {
        	String[] command = new String[] {"emacs", localPath};
	   	 	Runtime runtime = Runtime.getRuntime( );
	    	Process process = runtime.exec( command );	//execute command 
	    	process.waitFor();		//have process wait for emacs session to complete.  Necessary to have session complete before writeback, etc. 
	    }
		catch ( IOException e ) {
	    	e.printStackTrace();
		} 
        catch(InterruptedException e){}
        System.out.println("Emacs session complete.");
    }

    //completes the user editing session with the file
    public synchronized void completeSession() throws IOException {

    	//ownership transer required - upload fie to server and change state
		if (state == CacheState.RELEASE_OWNERSHIP) {	//state must have changed from write to release ownership during session
			uploadFile();		//now that emacs session is completed, user required to upload file to server
			state = CacheState.READ_SHARED;		//change state 
		//user emacs session complete, file is now modified so set to modified owned
		} else if (state == CacheState.WRITE_OWNED) {
			state = CacheState.MODIFIED_OWNED;
		}
	}
	
	//invalidates the clients currently cached file - sets state to invalid.  called remotely by server
	public synchronized boolean invalidate( ) throws RemoteException {

		 // set the DFS client’s file state to “Invalid”
		if(state == CacheState.READ_SHARED){
			state = CacheState.INVALID;
			return true;
		}
		return false;
	}

	//if there is an ownership change, the server calls this function to have the client write back its cached file contents
    public synchronized boolean writeback( ) throws RemoteException {
    	//transfer of ownership, change state to release ownership - file uploaded after emacs session
    	if(state == CacheState.WRITE_OWNED){
    		state = CacheState.RELEASE_OWNERSHIP;
    		return true;
    	}
    	else if(state == CacheState.MODIFIED_OWNED) {
    		//cached file was modified but user's emacs session is complete so upload file to server now
    		//write back file - use seperate thread to avoid deadlock
    		(new Thread() {
    			public void run() {
    				try {
    					//upload file to server
    					if(fileServer.upload(ipName, fileName, getFileContents())){
    						state = CacheState.READ_SHARED;		//change state
    					}
    				}
    				catch(IOException e){
    					e.printStackTrace();
    				}
    			}
    		}).start();
    		return true;
    	}
    	return false;
    }

    //main function - connects to the FileServer, constructs a FileClient, and starts a session with the user
    //continuously asking them for the name of the file they want to open, and the mode they wish to open the
    //file in, either (r/w) until the user wishes to end the session, at which time the cached file is uploaded
    //back to the server.
	public static void main (String args[]) {

		if ( args.length != 2 ) {
		    System.err.println( "usage: java FileClient ipAddress port#" );
		    System.exit( -1 );
		}
	
		try {
			System.out.println("Connecting to server ...");
			//look for the server instance that the client wants to access
			String serverIp = args[0];
			int port = Integer.parseInt(args[1]);
			ServerInterface fileServer =  (ServerInterface) Naming.lookup( "rmi://" + serverIp + ":" + port + "/fileserver" );

			System.out.println("Starting client ...");
		    FileClient client = new FileClient(fileServer);
		    //bind client name to ip address
		    Naming.rebind( "rmi://localhost:" + port + "/fileclient", client );

		    //to get user input
		    Scanner input = new Scanner( System.in );		
			
			//keep looping til user is done and wishes to exit
			while(true){
				//ask user if wish to exit
				System.out.print("Do you want to exit? [y/n]: ");
				if (input.next().equals("y")) {
					System.out.println("Writing any changes ...");
					client.writeback();
					client.completeSession();
					System.out.println("DONE");
					System.exit(0);
				}
				//prompt the user to enter filename for the file to open and the mode
	            System.out.println("FileClient: Next file to open?\n" + "Filename: " );
				String filename = input.next( );  // read a file name to operate   
				System.out.println("How(r/w): ");	  
				char mode = input.next().charAt(0);	//get mode from user

				//open file for reading or writing
				client.openFile(filename, mode);
				//start emacs session
				client.runEmacs();
				//complete user session with file
				client.completeSession();
			}	
		}
		catch ( Exception e ) {
		    e.printStackTrace( );
		    System.exit( 1 );
		}
	}
}