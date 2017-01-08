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
	private SortedMap<String, String> userAddressMap;
	
	public NameserverService(SortedMap<String, INameserver> subNameservers, SortedMap<String, String> userAddressMap) {
		this.subNameservers = subNameservers;
		this.userAddressMap = userAddressMap;
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		System.out.println("Register request for user \'" + username + "\' with \'" + address + "\'.");
		synchronized (userAddressMap) {
			if(userAddressMap.containsKey(username)){
				throw new AlreadyRegisteredException("User is already registered!");
			}
		}
		int lastIndex = username.lastIndexOf(".");
		if(lastIndex==-1){ //no "." in user name
			synchronized (userAddressMap) {
				userAddressMap.put(username, address);
			}
			System.out.println("\'" + username + "\' is registered");
		}else{
			
			String parentZone = username.substring(lastIndex+1);
			username = username.substring(0,lastIndex);
			INameserverForChatserver subNs = null;
			synchronized (subNameservers){						
				subNs = subNameservers.get(parentZone);
			}
			if(subNs==null){
				throw new InvalidDomainException("An intermediary zone \'" + parentZone +"\' does not exist!");
			}
			subNs.registerUser(username, address);
		}
	}

	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		System.out.println("Nameserver for \'" + zone + "\' requested by chatserver");
		return (INameserverForChatserver) subNameservers.get(zone);
	}

	@Override
	public String lookup(String username) throws RemoteException {
		System.out.println("User \'" + username + "\' requested by chatserver");
		return userAddressMap.get(username);
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		synchronized (subNameservers) {
			if(subNameservers.keySet().contains(domain)){
				throw new AlreadyRegisteredException("Zone \'" + domain +"\' is already registered!");
			}else{				
				int lastIndex = domain.lastIndexOf(".");
				if(lastIndex==-1){
					System.out.println("Registering nameserver for zone \'" + domain + "\'");
					subNameservers.put(domain,nameserver);
				}else{
					String childZone = domain.substring(0,lastIndex);
					String parentZone = domain.substring(lastIndex+1);
					INameserver subNs = subNameservers.get(parentZone);
					if(subNs==null){
						throw new InvalidDomainException("An intermediary zone does not exist!");
					}
					System.out.println("\'" + childZone + "\' will be sent for registration in next nameserver \'" + parentZone + "\'");
					subNs.registerNameserver(childZone, nameserver, nameserverForChatserver);
				}
			}
		}		
	}
}
