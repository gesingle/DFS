
public class CachedFile {

	
	private ArrayList<ClientProxy> readers;
	private String owner;
	private String state;
	private File storedFile;		//reference to the file stored on disk
	private byte[] data;			//file's current contents - an in-memory cache

	public CachedFile() {



	}
}