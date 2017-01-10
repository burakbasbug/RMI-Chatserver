package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

public class Nameserver implements INameserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private Shell shell;
	private ExecutorService pool;
	private Registry registry;	
	private String domain;
	private SortedMap<String, String> userAddressMap;	
	private SortedMap<String, INameserver> subNameservers;
	private boolean isRoot;
	private NameserverService nameserverService;
	private INameserver remote;

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
		this.shell = new Shell(componentName, userRequestStream, userResponseStream);
		this.shell.register(this);
		this.pool = Executors.newCachedThreadPool();	
		this.userAddressMap = Collections.synchronizedSortedMap(new TreeMap<String, String>());
		this.subNameservers = Collections.synchronizedSortedMap(new TreeMap<String, INameserver>());

		if(config.listKeys().contains("domain")){
			this.domain = config.getString("domain");
		}else{
			this.isRoot = true;
			this.domain = config.getString("root_id");
		}
	}

	@Override
	public void run() {
		pool.execute(shell);
		try {
			if(isRoot){
				registry = LocateRegistry.createRegistry(config.getInt("registry.port"));
				nameserverService = new NameserverService(subNameservers,userAddressMap);
				remote = (INameserver) UnicastRemoteObject.exportObject(nameserverService, 0);
				try{
					registry.bind(config.getString("root_id"), remote);
				} catch (AlreadyBoundException e) {
					printAndExit("Object can not be bound! " + e.getMessage());
				}
			}else{
				registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
				nameserverService = new NameserverService(subNameservers,userAddressMap);
				remote = (INameserver) UnicastRemoteObject.exportObject(nameserverService,0);
				INameserver nsServericeOfRoot;
				try {
					nsServericeOfRoot = (INameserver) registry.lookup(config.getString("root_id"));
					nsServericeOfRoot.registerNameserver(config.getString("domain") , remote,  remote);
				} catch (NotBoundException e) {
					printAndExit("Registry has no associated binding with this name!");
				} catch (AlreadyRegisteredException | InvalidDomainException e) {
					printAndExit("Register operation failed: " + e.getMessage());
				} 
			}
			System.out.println("\'" + domain + "\' is ready...");
		} catch (RemoteException e) {
			printAndExit("Nameserver can not be started! " + e.getMessage());
		}
	}

	@Override
	@Command
	public String nameservers() throws IOException {
		String nss = "";
		synchronized (subNameservers) {
			int c=1;
			for(Map.Entry<String,INameserver> ns : subNameservers.entrySet()){
				nss += (c) + ". " + ns.getKey() + (c++<=subNameservers.size()?"\n":"");
			}	
		}
		return nss;
	}

	@Override
	@Command
	public String addresses() throws IOException {
		String as = "";
		int c = 1;
		for(Map.Entry<String, String> user_adress : userAddressMap.entrySet()){
			as += (c) + ". " + user_adress.getKey() + " " + user_adress.getValue() + (c<=userAddressMap.size()?"\n":"");
		}
		return as;
	}
	
	private void printAndExit(String msg){
		try{
			if(shell!=null){
				shell.writeLine(msg);
			}
			this.exit();
		}catch (IOException e) {
			//Unimportant case. 
		}
	}

	@Override
	@Command
	public String exit() throws IOException {
		if (shell != null) {
			shell.writeLine("Exiting nameserver");
			shell.close();
		}
		
		pool.shutdown();
		
		if (userResponseStream != null) {
			userResponseStream.close();
		}
		if (userRequestStream != null) {
			userRequestStream.close();
		}
		
		try{			
			// unexport the previously exported remote object
			UnicastRemoteObject.unexportObject(nameserverService, true);
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
