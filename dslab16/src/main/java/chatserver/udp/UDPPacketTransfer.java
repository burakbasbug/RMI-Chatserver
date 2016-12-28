package chatserver.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;

import model.User;

public class UDPPacketTransfer extends Thread {

	private DatagramSocket datagramSocket;
	private DatagramPacket packet;
	private byte[] buffer;
	private Map<String, User> userMap;

	public UDPPacketTransfer(DatagramSocket datagramSocket, DatagramPacket packet, byte[] buffer,
			Map<String, User> userMap) {
		this.datagramSocket = datagramSocket;
		this.packet = packet;
		this.buffer = buffer;
		this.userMap = userMap;
	}

	@Override
	public void run() {
		try {
			String request = new String(packet.getData());

			String response = "";
			if (request.startsWith("!list")) {
				response = list(request);
			} else {
				response = "Error: Unknown request.";
			}

			InetAddress address = packet.getAddress();
			int port = packet.getPort();
			buffer = response.getBytes();

			packet = new DatagramPacket(buffer, buffer.length, address, port);
			datagramSocket.send(packet);
		} catch (IOException e) {
			System.err.println("Error occurred while handling packets: " + e.getMessage());
		}
	}

	public String list(String request) {
		String response = "Online users:";
		synchronized (userMap) {
			for (Map.Entry<String, User> entry : userMap.entrySet()) {
				User user = (User) entry.getValue();
				if (user.isLoggedIn()) {
					response += "\n* " + user.getName();
				}
			}
		}
		return response;
	}

}
