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
				
				//check the hmac and get the reply for the sending client.
				String reply = checkHMac(privateMsg);
				
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
	
	/**
	 * checkHMac checks if the HMAC contained in the message received by the sending client is equal to the HMAC generated in this method.
	 * The method prints out the message from the sending client, independent from the check.	
	 * Depending on the result of the check a String is returned containing a reply for the sending client.
	 * 
	 * @param privateMsg ..The whole message received by the socket.
	 * @return the reply to the sending client.
	 */
	private String checkHMac(String privateMsg){
		
		StringBuilder stringBuilder = new StringBuilder();
		
		String[] privateMsgSplit = privateMsg.split(" ");
		byte[] hMac = privateMsgSplit[0].getBytes();
		
		for(int i = 2; i < privateMsgSplit.length; i++){
			stringBuilder.append(privateMsgSplit[i]);
			if(i<privateMsgSplit.length-1)
				stringBuilder.append(" ");
		}
		
		userResponseStream.println(stringBuilder.toString());
		boolean hmacIsEqual = Cryptography.validateHMAC(hmacKey, Cryptography.HMAC_ALGORITHM.HmacSHA256, stringBuilder.toString(), hMac, true);
		
		if(hmacIsEqual)
			return username + " replied with !ack.";
		
		byte[] newHMac = Cryptography.genHMAC(hmacKey, Cryptography.HMAC_ALGORITHM.HmacSHA256, stringBuilder.toString());
		newHMac = Cryptography.encodeIntoBase64(newHMac);
		return newHMac + " !tampered " + stringBuilder.toString();
		
	}
}
