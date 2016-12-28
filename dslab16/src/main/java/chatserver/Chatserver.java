package chatserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import chatserver.tcp.TCPListener;
import chatserver.udp.UDPListener;
import cli.Command;
import cli.Shell;
import model.User;
import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private ServerSocket serverSocket;
	private DatagramSocket datagramSocket;
	private Shell shell;
	private ExecutorService pool;
	private Map<String, User> userMap;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Chatserver(String componentName, Config config, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);

		pool = Executors.newCachedThreadPool();

		userMap = Collections.synchronizedMap(new TreeMap<String, User>());
		Config userProperties = new Config("user");
		Set<String> userKeys = userProperties.listKeys();
		synchronized (userMap) {
			for (String s : userKeys) {
				User u = new User(s.substring(0, s.length() - 9), userProperties.getString(s));
				if (!userMap.containsKey(u.getName())) {
					userMap.put(u.getName(), u);
				}
			}
		}
	}

	@Override
	public void run() {
		pool.execute(shell);

		try {
			serverSocket = new ServerSocket(config.getInt("tcp.port"));
			TCPListener tcpListener = new TCPListener(serverSocket, pool, userMap);
			pool.execute(tcpListener);
		} catch (IOException e) {
			throw new RuntimeException("Cannot listen on TCP port.", e);
		}
		try {
			datagramSocket = new DatagramSocket(config.getInt("udp.port"));
			UDPListener udpListener = new UDPListener(datagramSocket, pool, userMap);
			pool.execute(udpListener);
		} catch (IOException e) {
			throw new RuntimeException("Cannot listen on UDP port.", e);
		}
		System.out.println("Chatserver up and waiting for commands!");
	}

	@Override
	@Command
	public String users() throws IOException {
		String response = "";
		int counter = 1;
		synchronized (userMap) {
			for (Map.Entry<String, User> entry : userMap.entrySet()) {
				User user = (User) entry.getValue();
				String status = user.isLoggedIn() ? "online" : "offline";
				response += counter + ". " + user.getName() + " " + status + "\n";
				++counter;
			}
		}

		if (response.length() > 0) {
			response = response.substring(0, response.length() - 1);
		}
		return response;
	}

	@Override
	@Command
	public String exit() throws IOException {
		pool.shutdown();

		if (userResponseStream != null) {
			userResponseStream.close();
		}
		if (userRequestStream != null) {
			userRequestStream.close();
		}

		if (shell != null) {
			shell.close();
		}

		if (serverSocket != null && !serverSocket.isClosed()) {
			serverSocket.close();
		}
		if (datagramSocket != null && !datagramSocket.isClosed()) {
			datagramSocket.close();
		}

		try {
			if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
					System.err.println("Pool did not terminate.");
				}
			}
		} catch (InterruptedException e) {
			pool.shutdownNow();
		}

		return "Exiting server.";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 */
	public static void main(String[] args) {
		Chatserver chatserver = new Chatserver(args[0], new Config("chatserver"), System.in,
				System.out);
		chatserver.run();
	}

}
