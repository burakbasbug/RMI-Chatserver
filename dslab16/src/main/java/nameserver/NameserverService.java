package nameserver;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;


public class NameserverService implements INameserver, Serializable{
	
	private static final long serialVersionUID = 1L;
	private SortedMap<String, INameserver> subNameservers;
	
	public NameserverService(SortedMap<String, INameserver> subNameservers) {
		this.subNameservers = subNameservers;
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		System.out.println("register user invoked!!!!");

	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		System.out.println("get nameserver invoked!!!");
		return null;
	}

	@Override
	public String lookup(String username) throws RemoteException {
		System.out.println("lookup invokation!!!");
		return null;
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
				
		synchronized (subNameservers) {
			if(subNameservers.keySet().contains(domain)){
				throw new AlreadyRegisteredException("This domain is already registered!");
			}else{				
				int lastIndex = domain.lastIndexOf(".");
				if(lastIndex==-1){
					System.out.println("Registering nameserver for zone \'" + domain + "\'");
					subNameservers.put(domain,nameserver);
				}else{
					String beforeDot = domain.substring(0,lastIndex);
					String afterDot = domain.substring(lastIndex+1);
					
					INameserver subNs = subNameservers.get(afterDot);
					if(subNs==null){
						throw new InvalidDomainException("An intermediary zone does not exist!");
					}
					subNs.registerNameserver(beforeDot, nameserver, nameserverForChatserver);
				}
			}
		}		
	}
	
	public SortedMap<String, INameserver> getSubNameservers(){
		return this.subNameservers;
	}

	

}
