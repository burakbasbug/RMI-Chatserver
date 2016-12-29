package nameserver;

import java.rmi.Remote;
import java.rmi.RemoteException;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

public interface INameserver extends INameserverForChatserver, Remote {

	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
			throws RemoteException, AlreadyRegisteredException,
			InvalidDomainException;

}
