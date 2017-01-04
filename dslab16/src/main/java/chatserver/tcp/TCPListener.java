package chatserver.tcp;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import channels.Channel;
import channels.RSAChannel;
import channels.TCPChannel;
import model.User;
import util.Config;
import util.Keys;

/**
 * Thread to listen for incoming connections on the given socket.
 */
public class TCPListener extends Thread {

	private ServerSocket serverSocket;
	private ExecutorService pool;
	private Map<String, User> userMap;
	private List<TCPConnection> allConnections;
	private PrintStream userResponseStream;
	private Key chatserverPrivateKey;

	public TCPListener(ServerSocket serverSocket, ExecutorService pool, Map<String, User> userMap,
			PrintStream userResponseStream) {
		this.serverSocket = serverSocket;
		this.pool = pool;
		this.userMap = userMap;
		this.allConnections = Collections.synchronizedList(new ArrayList<TCPConnection>());
		this.userResponseStream = userResponseStream;

		try {
			this.chatserverPrivateKey = Keys
					.readPrivatePEM(new File(new Config("chatserver").getString("key")));
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	public void run() {

		while (true) {
			Socket socket = null;
			try {
				socket = serverSocket.accept();

				Channel tcpChannel = new RSAChannel(new TCPChannel(socket), chatserverPrivateKey);
				TCPConnection tcpConn = new TCPConnection(tcpChannel, allConnections, userMap,
						userResponseStream);
				allConnections.add(tcpConn);
				pool.execute(tcpConn);

			} catch (SocketException e) {
				try {
					synchronized (allConnections) {
						for (TCPConnection conn : allConnections) {
							if (conn.getTcpChannel() != null) {
								conn.getTcpChannel().close();
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
