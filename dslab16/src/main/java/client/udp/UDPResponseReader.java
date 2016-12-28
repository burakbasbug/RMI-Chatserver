package client.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.BlockingQueue;

public class UDPResponseReader extends Thread {

	private DatagramSocket socket;
	private DatagramPacket packet;
	private BlockingQueue<String> listQueue;

	public UDPResponseReader(DatagramSocket socket, DatagramPacket packet,
			BlockingQueue<String> listQueue) {
		this.socket = socket;
		this.packet = packet;
		this.listQueue = listQueue;
	}

	@Override
	public void run() {
		try {
			socket.receive(packet);
			String output = new String(packet.getData()).trim();
			listQueue.put(output);
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			System.err.println(
					"Error occurred while receiving packet from server: " + e.getMessage());
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}

	}
}
