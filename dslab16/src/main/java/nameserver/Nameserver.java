package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Command;
import cli.Shell;
import model.User;
import util.Config;

public class Nameserver implements INameserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private Shell shell;
	private ExecutorService pool;
	private SortedSet<Nameserver> subNameservers;
	private Map<String, String> userAddressMap;
	private Registry registry;	
	private INameserver ns;
	private boolean isRoot = false;
	
	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Nameserver(String componentName, Config config,InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		//componentNames: "ns-root", "ns-at", "ns-de", "ns-vienna-at"
		//configs: root_id, registry.host, registry.port, (managed) domain="at","de"..
		
		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		pool = Executors.newCachedThreadPool();
		subNameservers = Collections.synchronizedSortedSet(new TreeSet<Nameserver>());
		userAddressMap = Collections.synchronizedSortedMap(new TreeMap<String, String>());

		try{
			String domain = config.getString("domain");
		}catch (MissingResourceException e) {
			System.out.println("Its root!");
			isRoot=true;
		}
		
		
	}

	@Override
	public void run() {
		pool.execute(shell);
		
		
		try {
			System.out.println("Registering nameserver for zone \'" + domain() + "\'");
			if(isRoot){
				// create and export the registry instance on localhost at the specified port
				registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
				
				// create a remote object of ns
				ns = new NameserverStub();
				INameserver stub = (INameserver) UnicastRemoteObject.exportObject(ns, 0);
				
				// bind the obtained remote object on specified binding name in the registry
				registry.bind(config.getString("root_id"), stub);
			}else{
				registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
				
				ns = new NameserverStub();
				INameserver stub = (INameserver) UnicastRemoteObject.exportObject(ns, 0);
				
				registry.bind(config.getString("domain"), stub);
			}
			
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			// TODO throw meaningful exceptions and pass them back to the actual requester
			e.printStackTrace();
		}
		
		
	}

	@Override
	@Command
	public String nameservers() throws IOException {
		String nss = "";
		int c=1;
		for(Nameserver ns : subNameservers){
			nss += (c++) + ". " + ns.domain() + (c==subNameservers.size()?"":"\n");
		}
		return nss;
	}

	@Override
	@Command
	public String addresses() throws IOException {
		String as = "";
		int c = 1;
		for(Map.Entry<String, String> user_adress : userAddressMap.entrySet()){
			as += (c++) + ". " + user_adress.getKey() + " " + user_adress.getValue() + (c==userAddressMap.size()?"":"\n");
		}
		return as;
	}

	@Override
	@Command
	public String exit() throws IOException {
		pool.shutdown();
		
		/*if (userResponseStream != null) {
			userResponseStream.close();
		}
		if (userRequestStream != null) {
			userRequestStream.close();
		}
		*/

		if (shell != null) {
			shell.close();
		}
		
		
		try{			
			// unexport the previously exported remote object
			UnicastRemoteObject.unexportObject(ns, true);
			
			if(isRoot){
				// unbind the remote object so that a client can't find it anymore
				registry.unbind(config.getString("root_id"));	
			}			
		}catch (NoSuchObjectException e) {
			System.err.println("Error while unexporting object: " + e.getMessage());
		}catch (Exception e) {
			System.err.println("Error while unbinding object: " + e.getMessage());
		}
		return "Exiting nameserver.";
	}
	
	public String domain(){
		if(isRoot){
			return config.getString("root_id");
		}else{
			return config.getString("domain");
		}
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),System.in, System.out);
		nameserver.run(); //args[0] = "ns-root", "ns-at", "ns-de", "ns-vienna-at"
	}

}
