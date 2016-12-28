package chatserver.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import model.User;

/**
 * Thread to listen for incoming data packets on the given socket.
 */
public class UDPListener extends Thread {

	private DatagramSocket datagramSocket;
	private ExecutorService pool;
	private Map<String, User> userMap;

	public UDPListener(DatagramSocket datagramSocket, ExecutorService pool,
			Map<String, User> userMap) {
		this.datagramSocket = datagramSocket;
		this.pool = pool;
		this.userMap = userMap;
	}

	public void run() {

		byte[] buffer;
		DatagramPacket packet;
		try {
			while (true) {
				buffer = new byte[1024];
				packet = new DatagramPacket(buffer, buffer.length);

				datagramSocket.receive(packet);
				UDPPacketTransfer udpTransfer = new UDPPacketTransfer(datagramSocket, packet,
						buffer, userMap);
				pool.execute(udpTransfer);
			}

		} catch (SocketException e) {
		} catch (IOException e) {
			System.err.println("Error occurred while waiting for UDP packets: " + e.getMessage());
		}

	}
}
