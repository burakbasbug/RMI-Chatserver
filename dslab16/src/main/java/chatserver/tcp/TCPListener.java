package chatserver.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import model.User;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class TCPListener extends Thread {

	private ServerSocket serverSocket;
	private ExecutorService pool;
	private Map<String, User> userMap;
	private List<TCPConnection> allConnections;

	public TCPListener(ServerSocket serverSocket, ExecutorService pool, Map<String, User> userMap) {
		this.serverSocket = serverSocket;
		this.pool = pool;
		this.userMap = userMap;
		this.allConnections = Collections.synchronizedList(new ArrayList<TCPConnection>());
	}

	public void run() {

		while (true) {
			Socket socket = null;
			try {
				socket = serverSocket.accept();

				TCPConnection tcpConn = new TCPConnection(socket, allConnections, userMap);
				allConnections.add(tcpConn);
				pool.execute(tcpConn);

			} catch (SocketException e) {
				try {
					synchronized (allConnections) {
						for (TCPConnection conn : allConnections) {
							if (conn.getSocket() != null && !conn.getSocket().isClosed()) {
								conn.getSocket().close();
							}
						}
					}
				} catch (IOException e1) {
					System.err
							.println("Error while closing all TCP connections: " + e.getMessage());
				}
				break;
			} catch (IOException e) {
				System.err.println("Error occurred while waiting for client: " + e.getMessage());
				break;
			}

		}
	}
}
