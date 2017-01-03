package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.Key;

import crypto.Cryptography;
import crypto.Cryptography.HMAC_ALGORITHM;

public class TCPPrivateMessageListener extends Thread {

	private ServerSocket serverSocket;
	private String username;
	private PrintStream userResponseStream;
	private BufferedReader reader;
	private PrintWriter writer;
	private Key hmacKey;

	public TCPPrivateMessageListener(ServerSocket serverSocket, String username, PrintStream userResponseStream, Key hmacKey) {
		this.serverSocket = serverSocket;
		this.username = username;
		this.userResponseStream = userResponseStream;
		this.hmacKey = hmacKey;
	}

	public void run() {
		
		while (true) {
			Socket socket = null;
			try {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				socket = serverSocket.accept();
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintWriter(socket.getOutputStream(), true);
				
				String privateMsg = reader.readLine();
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
				writer.println(reply);
				
				if (reader != null) {
					reader.close();
				}
				if (writer != null) {
					writer.close();
				}
				if (socket != null && !socket.isClosed()) {
					socket.close();
				}
			} catch (SocketException e) {
				try {
					if (reader != null) {
						reader.close();
					}
					if (writer != null) {
						writer.close();
					}
				} catch (IOException e1) {
					System.err
							.println("Error occurred while closing streams for private messaging: "
									+ e.getMessage());
				}
				break;
			} catch (IOException e) {
				System.err.println("Error occurred while waiting for client: " + e.getMessage());
				break;
			}
		}

	}
}
