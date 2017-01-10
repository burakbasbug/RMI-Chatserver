package channels;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.Key;

import crypto.Cryptography;
import crypto.Cryptography.HMAC_ALGORITHM;

public class PrivateChannel implements Channel{

	private TCPChannel decoratedChannel;
	private Key ownKey;
	
	public PrivateChannel(TCPChannel decoratedChannel, Key ownKey){
		this.decoratedChannel = decoratedChannel;
		this.ownKey = ownKey;
	}
	
	@Override
	public Socket getSocket() {
		return decoratedChannel.getSocket();
	}

	@Override
	public Channel getDecoratedChannel() {
		return decoratedChannel;
	}

	@Override
	public void send(String msg) {
		msg = Cryptography.genMessageWithHMac(ownKey, HMAC_ALGORITHM.HmacSHA256, msg);
		decoratedChannel.send(msg);
	}

	@Override
	public byte[] recvByte() throws SocketException, IOException {
		return decoratedChannel.recvByte();
	}
	
	public String recvString() throws SocketException, IOException {
		return decoratedChannel.recvString();
	}

	@Override
	public void close() throws SocketException {
		decoratedChannel.close();
		
	}

	@Override
	public void setOwnKey(Key ownKey) {}

	@Override
	public void setOppositeKey(Key oppositeKey) {}

	
	
}
