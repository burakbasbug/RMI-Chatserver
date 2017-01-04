package client.tcp;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;

import channels.Channel;

public class TCPResponseReader extends Thread {

	private Channel tcpChannel;
	private PrintStream userResponseStream;
	private BlockingQueue<String> loginQueue;
	private BlockingQueue<String> logoutQueue;
	private BlockingQueue<String> registerQueue;
	private BlockingQueue<String> lookupQueue;
	private StringBuilder lastMsg;

	public TCPResponseReader(Channel tcpChannel, PrintStream userResponseStream,
			BlockingQueue<String> loginQueue, BlockingQueue<String> logoutQueue,
			BlockingQueue<String> registerQueue, BlockingQueue<String> lookupQueue,
			StringBuilder lastMsg) {
		this.tcpChannel = tcpChannel;
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
			while ((response = new String(tcpChannel.recv())) != null) {
				//System.out.println("tcpresponsereader while: " + tcpChannel.getClass());
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
					break;
				} else if (response.startsWith("!register")) {
					registerQueue.put(response.substring(9));
				} else if (response.startsWith("!lookup")) {
					lookupQueue.put(response.substring(7));
				} else {
					userResponseStream.println("Error in reading server response: " + response);
				}
			}
		} catch (IOException e) {
			System.err.println("Error occurred while communicating with server: " + e.getMessage());
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		} /*
			 * catch (NullPointerException e) {
			 * System.out.println("responsereader closed"); return; }
			 */
	}
}
