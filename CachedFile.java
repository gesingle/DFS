/*
*	@author Tiana Greisel and Garrett Singletary
*	@title	CSS434 - Program 4 Distributed File System
*	
*/

import java.io.*;
import java.rmi.RemoteException;
import java.util.Vector;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.List;

/**
 * Class represents a file cached in the server
 * 
 * @version CSS434 DFS 
 */
public class CachedFile {
	
	private LinkedList<ClientProxy> readers;					//list of readers of this cached file
	private ClientProxy owner;									//owner of the cached file											
	private File file;											//cached file
	private byte[] data;										//data of the cached file

	/** 
	 * Constructor
	 * 	
	 * Caches a new file by reading and storing 
	 * its contents from disk 
	 */
	public CachedFile(String filename) throws IOException{

		// file path
		file = new File("tmp/" + filename);
		// get the file's data from disk
		data = new byte[(int) file.length()];
		FileInputStream fis = new FileInputStream(file);
		fis.read(data);
		//create readers list
		readers = new LinkedList<ClientProxy>();
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
		
		//add reader to the readers list only if the client isn't already in the list
		if(!readers.contains(client)){
			readers.add(client);
		}
	}

	/**
	 * Adds a new reader to the list of readers - synchronized for concurrent thread access 
	 */
	public synchronized void registerReader(ClientProxy client) {

			//calls addReader() but synchroniously 
			addReader(client);
	}
	
	/**
	 * Remove a client from the readers list
	 */
	public synchronized void removeReader(String client){
		
		//create an iterator to go over the reader list
		Iterator<ClientProxy> it = readers.iterator();
		// Remove readers matching client name.
        while (it.hasNext()) {
			ClientProxy reader = it.next();
			if (reader.getName().equals(client))
				it.remove();
		}
	}
	
	/**
	 * Add a writer (owner) to the cached file
	 */
	public void addWriter(ClientProxy client) throws RemoteException{
		
		// If the client is present in the reader list, remove client from reader list
		readers.remove(client);
		if (!client.equals(owner)) {
			synchronized (this) {
                // Wait until the previous owner has released ownership
				while (owner != null) {
					try {
						//write the cached file back to the server
						owner.writeback();
						wait();	//wait for current owner to release ownership
					} catch (InterruptedException e) {
						continue;
					} catch (RemoteException e) {
						throw new RemoteException("writeback to owner failed", e);
					}
				}
			}
		} 
		owner = client;				//set the new owner to the parameter clientproxy object
	}
	
	/**
	 * Updates the contents of this file. 
	 * 
	 * The current owner relinquishes ownership and all readers
	 * are invalidated
	 */
	public boolean update(String clientName, FileContents contents) throws RemoteException{

		//error handling
		if (owner == null) {
			return false;
		}
        // Invalidate readers.
		while (!readers.isEmpty()) {
			ClientProxy reader = readers.remove();
			//have reader invalidate their cached file contents
			reader.invalidate();
		}
        	readers.add(owner);			//add owner to the readers list
		owner = null;					
		data = contents.get();			//get contents from the cached file to be updated
		
		//create a new thread to asynchroniously update the file to avoid a deadlock situation
		(new Thread() {
    			public void run() {
    				try {
    					//update the contents of the cached file
    					FileOutputStream f = new FileOutputStream(file);
						f.write(contents.get());
    					
    				}
    				catch(IOException e){
    					e.printStackTrace();
    				}
    			}

    		}).start();

		//notify any waiting threads that were waiting for ownership change/ or a the file to be updated
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