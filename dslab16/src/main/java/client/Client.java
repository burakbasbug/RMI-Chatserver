package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Key;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import cli.Command;
import cli.Shell;
import client.tcp.TCPPrivateMessageListener;
import client.tcp.TCPResponseReader;
import client.udp.UDPResponseReader;
import crypto.Cryptography;
import util.Config;
import util.Keys;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;

	private Socket socket;
	private BufferedReader serverReader;
	private PrintWriter serverWriter;
	private Shell shell;
	private ExecutorService pool;
	private BlockingQueue<String> loginQueue;
	private BlockingQueue<String> logoutQueue;
	private BlockingQueue<String> registerQueue;
	private BlockingQueue<String> lookupQueue;
	private BlockingQueue<String> listQueue;
	private String username;
	private StringBuilder lastMsg;
	private ServerSocket privateServer;
	private boolean loggedIn, registered;
	private Key hmacKey;

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
	public Client(String componentName, Config config, InputStream userRequestStream,
			PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		shell = new Shell(componentName, userRequestStream, userResponseStream);
		shell.register(this);
		pool = Executors.newCachedThreadPool();
		loginQueue = new LinkedBlockingQueue<>();
		logoutQueue = new LinkedBlockingQueue<>();
		registerQueue = new LinkedBlockingQueue<>();
		lookupQueue = new LinkedBlockingQueue<>();
		listQueue = new LinkedBlockingQueue<>();
		username = null;
		lastMsg = new StringBuilder();
		loggedIn = false;
		registered = false;
		Cryptography.init();
		
		try {
			hmacKey = Keys.readSecretKey(new File(config.getString("hmac.key")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		pool.execute(shell);
		System.out.println("Client up and waiting for commands!");

		try {
			socket = new Socket(config.getString("chatserver.host"),
					config.getInt("chatserver.tcp.port"));
			serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			serverWriter = new PrintWriter(socket.getOutputStream(), true);

			TCPResponseReader tcpReader = new TCPResponseReader(serverReader, userResponseStream,
					loginQueue, logoutQueue, registerQueue, lookupQueue, lastMsg);
			pool.execute(tcpReader);
		} catch (UnknownHostException e) {
			System.err.println("IP address of the host could not be determined: " + e.getMessage());
		} catch (IOException e) {
			System.err.println("Error occurred while communicating with server: " + e.getMessage());
		}
	}

	@Override
	@Command
	public String login(String username, String password) throws IOException {
		String input = "!login " + username + " " + password;
		serverWriter.println(input);
		try {
			String response = loginQueue.take();
			if (response.startsWith("Successfully")) {
				this.username = username;
				this.loggedIn = true;
			}
			return response;
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
		return "Login error.";
	}

	@Override
	@Command
	public String logout() throws IOException {
		String input = "!logout";
		serverWriter.println(input);
		try {
			username = null;
			loggedIn = false;
			registered = false;
			if (privateServer != null) {
				privateServer.close();
			}
			return logoutQueue.poll(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
		return "Logout error.";
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		String input = "!send " + message;
		serverWriter.println(input);
		return null;
	}

	@Override
	@Command
	public String list() throws IOException {
		DatagramSocket socket = null;

		try {
			socket = new DatagramSocket();
			byte[] buffer;
			DatagramPacket packet;
			String input = "!list";
			buffer = input.getBytes();
			packet = new DatagramPacket(buffer, buffer.length,
					InetAddress.getByName(config.getString("chatserver.host")),
					config.getInt("chatserver.udp.port"));
			socket.send(packet);
			buffer = new byte[1024];
			packet = new DatagramPacket(buffer, buffer.length);
			UDPResponseReader udpReader = new UDPResponseReader(socket, packet, listQueue);
			pool.execute(udpReader);
			return listQueue.take();
		} catch (UnknownHostException e) {
			System.err.println("Cannot connect to host: " + e.getMessage());
		} catch (IOException e) {
			System.err.println(
					"Error occurred while sending UDP packets to server: " + e.getMessage());
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
		return "List error.";
	}

	@Override
	@Command
	public String msg(String username, String message) throws IOException {
		String address = lookup(username);
		if (address.contains("Not logged in")) {
			return "Not logged in. -OR- Wrong username or user not reachable.";
		}
		if (address.contains("Wrong username or user not registered")) {
			return "Wrong username or user not reachable.";
		}
		address = address.replace(':', ' ');
		String[] ipPort = address.split("\\s");
		Socket socket = new Socket(InetAddress.getByName(ipPort[0]), Integer.parseInt(ipPort[1]));
		BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
		
		message = Cryptography.genMessageWithHMac(hmacKey, "!msg " + this.username + ": " + message);
		
		writer.println(message);
		
		String response = reader.readLine();
		
		if(response.contains("!tampered")){
			response = "The message sent to " + username + " has been tampered!";
		}
		if(!Cryptography.checkHMacInMessage(hmacKey, response)){
			response += "\nThe confirmation message received from " + username + " has been changed!"; 
		}
		
		
		
		if (reader != null) {
			reader.close();
		}
		if (writer != null) {
			writer.close();
		}
		if (socket != null && !socket.isClosed()) {
			socket.close();
		}
		return response;
	}
	

	@Override
	@Command
	public String lookup(String username) throws IOException {
		String input = "!lookup " + username;
		serverWriter.println(input);
		try {
			return lookupQueue.take();
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
		return "Lookup error.";
	}

	@Override
	@Command
	public String register(String privateAddress) throws IOException {
		if (registered) {
			return "Already registered and waiting for private messages.";
		}
		String input = "!register " + privateAddress;
		serverWriter.println(input);
		try {
			String response = registerQueue.take();
			if (response.startsWith("Successfully registered")) {
				privateAddress = privateAddress.replace(':', ' ');
				String[] ipPort = privateAddress.split("\\s");
				privateServer = new ServerSocket(Integer.parseInt(ipPort[1]));
				registered = true;
				TCPPrivateMessageListener tcpPMListener = new TCPPrivateMessageListener(privateServer, username, userResponseStream, hmacKey);
				pool.execute(tcpPMListener);
			}
			return response;
		} catch (BindException e) {
			String errorInput = "!register";
			serverWriter.println(errorInput);
			try {
				registerQueue.take();
				return "Port already in use.";
			} catch (InterruptedException e1) {
				System.err.println("Interrupted while waiting: " + e.getMessage());
			}
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
		return "Register error.";
	}

	@Override
	@Command
	public String lastMsg() throws IOException {
		if (lastMsg.toString().isEmpty()) {
			return "No message received!";
		}
		return lastMsg.toString();
	}

	@Override
	@Command
	public String exit() throws IOException {
		if (loggedIn || registered) {
			logout();
		}
		serverWriter.println("!exit");
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

		if (serverReader != null) {
			serverReader.close();
		}
		if (serverWriter != null) {
			serverWriter.close();
		}

		if (socket != null && !socket.isClosed()) {
			socket.close();
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

		return "Exiting client.";
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in, System.out);
		client.run();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
