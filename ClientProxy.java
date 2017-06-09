/*
*	@author Tiana Greisel and Garrett Singletary
*	@title	CSS434 - Program 4 Distributed File System
*	
*/

import java.rmi.*;
import java.net.*;

//Estalishes a connect to a client object.  Functions as a proxy to the remote client instead
//of using just ClientInterface to encapsulate the process of connecting to the client.
public class ClientProxy implements ClientInterface {

	private String ipName;				//ip name of the client
	private ClientInterface client;		//reference to the remote client

	//Constructor - constructors object for a client at given ip address and port.
	public ClientProxy(String ipAddress, String port) throws RemoteException {

		ipName = ipAddress;

		//look for the client instance that the server wants to access
		String clientAddress = String.format("rmi://%s:%s/fileclient", ipAddress, port);
		try{
			client = (ClientInterface) Naming.lookup(clientAddress);
		}
		catch(NotBoundException e){}
		catch(MalformedURLException e){}
	}

	//Constructor - sets the ipName and client of the ClientProxy object
	public ClientProxy(String name, ClientInterface client){
		this.ipName = name;
		this.client = client;
	}

	//calls the ClientInterface object's invalidate function
	@Override
	public boolean invalidate( ) throws RemoteException {
		return client.invalidate();
	}

	//calls the ClientInterface object's writeback function
	@Override
	public boolean writeback( ) throws 	RemoteException {
		return client.writeback();
	}

	//returns the ipAddress of the ClientProxy object
	public String getName() {
		return ipName;
	}

	//equals functions if the ClientProxy object is equal to the parameter object
	@Override
	public boolean equals(Object obj) {
		if(obj == null){
			return false;
		}
		try {
			ClientProxy comparedObject = (ClientProxy) obj;
			return comparedObject.getName().equals(ipName);
		}
		catch(ClassCastException e){
			return false;
		}
	}
}