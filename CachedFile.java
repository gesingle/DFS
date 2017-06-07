import java.io.*;
import java.rmi.RemoteException;
import java.util.Vector;

/**
 * Class represents a file cached in the server
 * 
 * @version CSS434 DFS 
 */
public class CachedFile {

	public static final int NOT_SHARED = 0;
	public static final int READ_SHARED = 1;
	public static final int WRITE_SHARED = 2;
	public static final int OWNERSHIP_CHANGE = 3;
	

	private Vector<ClientProxy> readers;
	private ClientProxy owner;
	public int state;
	private File file;
	private byte[] data;	
	private FileInputStream fis;
	private FileOutputStream fos;

	/** 
	 * Constructor
	 * 	
	 * Caches a new file by reading and storing 
	 * its contents from disk 
	 */
	public CachedFile(String filename) throws IOException{

		// file path
		file = new File("/tmp/" + filename);
		// get the file's data from disk
		data = new byte[(int) file.length()];
		fis = new FileInputStream(file);
		fis.read(data);
		
		readers = new Vector<ClientProxy>();
		state = NOT_SHARED;
	}
	
	/**
	 * Getter for file name
	 */
	public String getName(){
		
		return file.getName();
	}
	
	
	/**
	 * Adds a new reader to the list of readers
	 */
	public void addReader(ClientProxy client){
		
		if(!readers.contains(client)){
			readers.add(client);
		}
	}
	
	/**
	 * Remove a client from the readers list
	 */
	public void removeReader(String client){
		
		for(ClientProxy cp : readers){
			if(cp.getName().equals(client)){
				readers.remove(cp);
			}
		}
	}
	
	public void addWriter(ClientProxy client) throws RemoteException{
		
		if (!client.equals(owner)) {
			synchronized (this) {
                // Wait for current owner to writeBack() and release the file
				while (owner != null) {
					try {
						owner.writeback();
						wait();
					} 
					catch (InterruptedException e) {
						continue;
					}
					catch (RemoteException e) {
						throw new RemoteException("Previous owner failed to writeback.");
					}
				}
			}
		} 
		
		owner = client;
	}
	
	/**
	 * Updates the contents of this file. 
	 * 
	 * The current owner relinquishes ownership and all readers
	 * are invalidated
	 */
	public boolean update(FileContents contents) throws RemoteException{
		
		if (owner == null) {
			return false;
		}
		
        // Invalidate all readers.
		for(ClientProxy cp : readers){
			cp.invalidate();
			readers.remove(cp);
		}
		
		// previous owner is still a reader
        readers.add(owner);
        // but no longer owns the file
		owner = null;
		// update file contents in cache
		data = contents.get();
		// and on disk
		try{
			fos = new FileOutputStream(file);
			fos.write(contents.get(), 0, contents.get().length);
		}
		catch(IOException e){}
		
		// update state
		state = (state == WRITE_SHARED) ? NOT_SHARED : WRITE_SHARED;
		
		synchronized (this) {
			notifyAll();
		}
		
		return true;
	}
	
	/**
     * Getter for file contents
     */
	public FileContents getContents() {
		
		return new FileContents(data);
	}
	

}