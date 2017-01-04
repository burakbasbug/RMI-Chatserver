package channels;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.Key;

import org.bouncycastle.util.encoders.Base64;

public abstract class Base64Channel implements Channel {

	protected Channel decoratedChannel;

	public Base64Channel(Channel decoratedChannel) {
		this.decoratedChannel = decoratedChannel;
	}

	@Override
	public Channel getDecoratedChannel() {
		return decoratedChannel;
	}

	@Override
	public Socket getSocket() {
		return decoratedChannel.getSocket();
	}

	@Override
	public void send(String msg) {
		decoratedChannel.send(new String(Base64.encode(msg.getBytes())));
	}

	@Override
	public byte[] recv() throws SocketException, IOException {
		return Base64.decode(decoratedChannel.recv());
	}

	@Override
	public void close() throws IOException {
		decoratedChannel.close();
	}

	@Override
	public void setOwnKey(Key ownKey){}
	
	@Override
	public void setOppositeKey(Key oppositeKey){}

}
