package client.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;

public class TCPResponseReader extends Thread {

	private BufferedReader serverReader;
	private PrintStream userResponseStream;
	private BlockingQueue<String> loginQueue;
	private BlockingQueue<String> logoutQueue;
	private BlockingQueue<String> registerQueue;
	private BlockingQueue<String> lookupQueue;
	private StringBuilder lastMsg;

	public TCPResponseReader(BufferedReader serverReader, PrintStream userResponseStream,
			BlockingQueue<String> loginQueue, BlockingQueue<String> logoutQueue,
			BlockingQueue<String> registerQueue, BlockingQueue<String> lookupQueue,
			StringBuilder lastMsg) {
		this.serverReader = serverReader;
		this.userResponseStream = userResponseStream;
		this.loginQueue = loginQueue;
		this.logoutQueue = logoutQueue;
		this.registerQueue = registerQueue;
		this.lookupQueue = lookupQueue;
		this.lastMsg = lastMsg;
	}

	@Override
	public void run() {
		try {
			String response;
			while ((response = serverReader.readLine()) != null) {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				if (response.startsWith("!public")) {
					lastMsg.setLength(0);
					lastMsg.append(response.substring(7));
					userResponseStream.println(lastMsg.toString());
				} else if (response.startsWith("!successpublic")) {
					userResponseStream.println(response.substring(14));
				} else if (response.startsWith("!failedpublic")) {
					userResponseStream.println(response.substring(13));
				} else if (response.startsWith("!login")) {
					loginQueue.put(response.substring(6));
				} else if (response.startsWith("!logout")) {
					logoutQueue.put(response.substring(7));
				} else if (response.startsWith("!register")) {
					registerQueue.put(response.substring(9));
				} else if (response.startsWith("!lookup")) {
					lookupQueue.put(response.substring(7));
				} else {
					userResponseStream.println("Error in reading server response.");
				}
			}
		} catch (IOException e) {
			System.err.println("Error occurred while communicating with server: " + e.getMessage());
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
	}
}
