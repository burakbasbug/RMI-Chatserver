package channels;

import java.io.IOException;
import java.net.SocketException;

import javax.crypto.Cipher;

import org.bouncycastle.util.encoders.Base64;

import crypto.Cryptography;

public class AESChannel extends Base64Channel {

	private byte[] ivParameter;
	private byte[] secretKeyInBytes;

	public AESChannel(Channel decoratedChannel, byte[] ivParameter, byte[] secretKeyInBytes) {
		super(decoratedChannel);
		this.ivParameter = ivParameter;
		this.secretKeyInBytes = secretKeyInBytes;
	}

	@Override
	public void send(String msg) {
		byte[] encrypted = Cryptography.cryptoAES(Cipher.ENCRYPT_MODE, ivParameter,
				secretKeyInBytes, msg.getBytes());
		decoratedChannel.send(new String(Base64.encode(encrypted)));
	}

	@Override
	public byte[] recv() throws SocketException, IOException {
		//System.out.println("aes recv");
		byte[] decoded = Base64.decode(decoratedChannel.recv());
		byte[] decrypted = Cryptography.cryptoAES(Cipher.DECRYPT_MODE, ivParameter,
				secretKeyInBytes, decoded);
		return decrypted;
	}

}
