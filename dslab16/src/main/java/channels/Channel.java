package channels;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.Key;

public interface Channel {

	public Socket getSocket();
	
	public Channel getDecoratedChannel();

	public void send(String msg);

	public byte[] recvByte() throws SocketException, IOException;
	
	public String recvString() throws SocketException, IOException;

	public void close() throws SocketException;
	
	public void setOwnKey(Key ownKey);
	
	public void setOppositeKey(Key oppositeKey);
}
