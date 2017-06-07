import java.rmi.*;
import java.net.*;

/**
 * Establishes a connection to a remote client object.
 * 
 * Functions as a proxy to the remote client instead of using just ClientInterface to
 * encapsulate the process of connecting to the client.
 */
public class ClientProxy implements ClientInterface {

	private String ipName;				//ip name of the client
	private ClientInterface client;		//reference to the remote client

	//Constructor - constructors object for a client at given ip address and port.
	public ClientProxy(String ipAddress, String port) throws RemoteException {

		ipName = ipAddress;

		//look for the client instance that the server wants to access
		String clientAddress = String.format("rmi://%s:%s/fileclient", ipAddress, port);
		client = (ClientInterface) Naming.lookup(rmiClientAddress);
	}

	public ClientProxy(String name, ClientInterface client){
		this.ipName = name;
		this.client = client;
	}

	@Override
	public boolean invalidate( ) throws RemoteException {
		return client.invalidate();
	}

	@Override
	public boolean writeback( ) throws 	RemoteException {
		return client.writeback();
	}

	public String getName() {
		return ipName;
	}
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