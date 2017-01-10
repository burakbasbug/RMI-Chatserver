package channels;

import java.io.IOException;
import java.net.SocketException;
import java.security.Key;

import javax.crypto.Cipher;

import org.bouncycastle.util.encoders.Base64;

import crypto.Cryptography;

public class RSAChannel extends Base64Channel {

	private Key ownKey;
	private Key oppositeKey;

	public RSAChannel(Channel decoratedChannel, Key ownKey) {
		super(decoratedChannel);
		this.ownKey = ownKey;
	}

	@Override
	public void setOwnKey(Key ownKey) {
		this.ownKey = ownKey;
	}
	
	@Override
	public void setOppositeKey(Key oppositeKey) {
		this.oppositeKey = oppositeKey;
	}

	@Override
	public void send(String msg) {
		if (oppositeKey == null) {
			System.err.println("rsa send oppsitekey null");
			return;
		}
		byte[] encrypted = Cryptography.cryptoRSA(Cipher.ENCRYPT_MODE, oppositeKey, msg.getBytes());
		decoratedChannel.send(new String(Base64.encode(encrypted)));
	}

	@Override
	public byte[] recvByte() throws SocketException, IOException {
		//System.out.println("rsa recv");
		byte[] decoded = Base64.decode(decoratedChannel.recvByte());
		byte[] decrypted = Cryptography.cryptoRSA(Cipher.DECRYPT_MODE, ownKey, decoded);
		return decrypted;
	}

	@Override
	public String recvString() throws SocketException {
		return null;
	}
}
