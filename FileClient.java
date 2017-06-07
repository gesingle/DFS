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
	private String localPath;			//local path to file cache
	//private String accessMode;			//current file access mode such as read or write
	private boolean ownership;			//indicates if client owns the file - or is exclusively writing the file
	private CacheState state = CacheState.INVALID;	//current state of the cache

	//possible states of the cache
	private enum CacheState {INVALID, READ_SHARED, WRITE_OWNED, MODIFIED_OWNED, RELEASE_OWNERSHIP};

	public FileClient(ServerInterface fileServer) throws RemoteException {

		this.fileServer = fileServer;
		String username = System.getProperty("user.name");
		localPath = "/tmp/" + username + ".txt";	//set local path to file cache
		cachedFile = new File("/tmp/" + username + ".txt").toPath());		//create a file to store into “/tmp/useraccount.txt”
		if(!cachedFile.exists()){
			cachedFile.createNewFile();
			cachedFile.setReadable(true);	//allow file to be readable by default
		}
		//get ip name of the client
		ipName = InetAddress.getLocalHost().getHostName();
	}

	public synchronized void openFile(String fname, char mode) throws IOException {

		//check if the cached file matches the user requested file
		if(!fileName.equals(fname)){
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
				if(mode == READ){
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
				if(mode == WRITE){
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

	private boolean uploadFile() throws IOException {

		return fileServer.upload(ipName, fileName, getFileContents());
	}

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
		writer.setWritable(mode == WRITE);	//set file to writable if client opened file for writing
	}

	private FileContents getFileContents() throws FileNotFoundException, IOException {

		//byte array to read file contents into that is the size of the currently cached file
		byte[] data = new byte[][(int) cachedFile.length()];
		FileInputStream reader = new FileInputSteam(cachedFile);
		FileContents contents = new FileContents(reader.read(data)); //create FileContents object with contents of cached file
		return contents;
	}


	public void runEmacs() {
        try {
        	String[] command = new String[] {"emacs", localPath};
	   	 	Runtime runtime = Runtime.getRuntime( );
	    	Process process = Runtime.exec( command );	//execute command 
	    	p.waitFor();		//have process wait for emacs session to complete.  Necessary to have session complete before writeback, etc. 
	    }
		catch ( IOException e ) {
	    	e.printStackTrace( )
		} 
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
	
	public synchronized boolean invalidate( ) throws RemoteException {

		 // set the DFS client’s file state to “Invalid”
		if(state == CacheState.READ_SHARED){
			state = CacheState.INVALID;
			return true;
		}
		return false;
	}

    public synchronized boolean writeback( ) throws 	RemoteException {
    	//transfer of ownership, change state to release ownership - file uploaded after emacs session
    	if(state == CacheState.WRITE_OWNED){
    		state = CacheState.RELEASE_OWNERSHIP;
    		return true;
    	}
    	else if(state == CacheState.MODIFIED_OWNED) {
    		//cached file was modified but user's emacs session is complete so upload file to server now
    		//final FileContents contents?
    		FileContents contents;
    		try {
    			//get FileContents from cached file
    			contents = getCurrentContents();
    		}
    		catch(IOException e){}
    		//write back file - use seperate thread to avoid deadlock
    		(new Thread() {
    			public void run() {
    				try {
    					//upload file to server
    					if(fileServer.upload(ipName, fileName, contents)){
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

	            System.out.println("FileClient: Next file to open?\n" + "Filename: " );
				String filename = input.next( );  // read a file name to operate   
				System.out.println("How(r/w: ");	  
				char mode = input.next().charAt(0);	//get mode from user

				//open file for reading or writing
				openFile(filename, mode);
				//start emacs session
				runEmacs();
				//complete user session with file
				completeSession();
			}	
		}
		catch ( Exception e ) {
		    e.printStackTrace( );
		    System.exit( 1 );
		}



	}



}