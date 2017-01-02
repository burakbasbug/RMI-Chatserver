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

				StringBuilder stringBuilder = new StringBuilder();
				String[] privateMsgSplit = privateMsg.split(" ");
				for(int i = 1; i < privateMsgSplit.length; i++){
					stringBuilder.append(privateMsgSplit[i]);
					if(i<privateMsgSplit.length-1)
						stringBuilder.append(" ");
				}
				//check the hmac and get the reply for the sending client.
				if(Cryptography.checkHMacInMessage(hmacKey, privateMsg.split(" "))){
					reply = Cryptography.genMessageWithHMac(hmacKey, username + " replied with !ack.");
				}else{
					reply = Cryptography.genMessageWithHMac(hmacKey, "!tampered " + stringBuilder.toString());
				}
				
				userResponseStream.println(stringBuilder.toString());
				
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
