/**
 * 
 */
package nameserver;

import java.rmi.RemoteException;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;


public class NameserverStub implements INameserver {
	
	/* (non-Javadoc)
	 * @see nameserver.INameserverForChatserver#registerUser(java.lang.String, java.lang.String)
	 */
	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		System.out.println("register user invoked!!!!");

	}

	/* (non-Javadoc)
	 * @see nameserver.INameserverForChatserver#getNameserver(java.lang.String)
	 */
	@Override
	public INameserverForChatserver getNameserver(String zone) throws RemoteException {
		System.out.println("get nameserver invoookeddd!!!");
		return null;
	}

	/* (non-Javadoc)
	 * @see nameserver.INameserverForChatserver#lookup(java.lang.String)
	 */
	@Override
	public String lookup(String username) throws RemoteException {
		System.out.println("lookup invokation!!!!!!!!!!!");
		return null;
	}

	/* (non-Javadoc)
	 * @see nameserver.INameserver#registerNameserver(java.lang.String, nameserver.INameserver, nameserver.INameserverForChatserver)
	 */
	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
			throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
		System.out.println("register nameserver invokatioooooon!!!!!");
	}

}
