package client.tcp;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.Key;

import channels.TCPChannel;
import crypto.Cryptography;
import crypto.Cryptography.HMAC_ALGORITHM;

public class TCPPrivateMessageListener extends Thread {

	private ServerSocket serverSocket;
	private String username;
	private PrintStream userResponseStream;
	private Key hmacKey;
    private TCPChannel channel;

	public TCPPrivateMessageListener(ServerSocket serverSocket, String username, PrintStream userResponseStream, Key hmacKey) {
		this.serverSocket = serverSocket;
		this.username = username;
		this.userResponseStream = userResponseStream;
		this.hmacKey = hmacKey;
	}

	public void run() {
		
		while (true) {
			try {
				
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				
				channel = new TCPChannel(serverSocket.accept());
				
				String privateMsg = channel.recvStr();
				String reply = null;

				if(Cryptography.checkHMacInMessage(hmacKey, HMAC_ALGORITHM.HmacSHA256, privateMsg, true)){
					privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
					privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
					reply = Cryptography.genMessageWithHMac(hmacKey, HMAC_ALGORITHM.HmacSHA256, username + " replied with !ack.");
				}else{
					privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
					privateMsg = privateMsg.substring(privateMsg.indexOf(" ") + 1, privateMsg.length());
					reply = Cryptography.genMessageWithHMac(hmacKey, HMAC_ALGORITHM.HmacSHA256, "!tampered " + privateMsg);
					privateMsg += "\n<NOTE: This message has been tampered!>";
				}
				
				userResponseStream.println(privateMsg);
				channel.send(reply);
				
			} catch (SocketException e) {
				try {
					if(channel!=null)
						channel.close();
				} catch (IOException e1) {
					System.err.println("Error occurred while closing streams for private messaging: " + e.getMessage());
				}
				break;
			} catch (IOException e) {
				System.err.println("Error occurred while waiting for client: " + e.getMessage());
				break;
			}
		}

	}
}
