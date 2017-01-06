package client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.util.encoders.Base64;

import channels.AESChannel;
import channels.Channel;
import channels.RSAChannel;
import channels.TCPChannel;
import cli.Command;
import cli.Shell;
import client.tcp.TCPPrivateMessageListener;
import client.tcp.TCPResponseReader;
import client.udp.UDPResponseReader;
import crypto.Cryptography;
import crypto.Cryptography.HMAC_ALGORITHM;
import util.Config;
import util.Keys;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private TCPResponseReader tcpReader;

	private Channel tcpChannel;
	private Channel rsaChannel;
	private Channel aesChannel;
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
			Socket socket = new Socket(config.getString("chatserver.host"),
					config.getInt("chatserver.tcp.port"));
			rsaChannel = new RSAChannel(new TCPChannel(socket), null);
			tcpChannel = rsaChannel;
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
		tcpChannel.send(input);
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
		if (!loggedIn)
			return "Not logged in.";
		String input = "!logout";
		tcpChannel.send(input);
		try {
			username = null;
			loggedIn = false;
			registered = false;
			if (privateServer != null) {
				privateServer.close();
			}
			String response = logoutQueue.poll(5, TimeUnit.SECONDS);
			tcpChannel = rsaChannel;
			return response;
		} catch (InterruptedException e) {
			System.err.println("Interrupted while waiting: " + e.getMessage());
		}
		return "Logout error.";
	}

	@Override
	@Command
	public String send(String message) throws IOException {
		if (!loggedIn)
			return "Not logged in.";
		String input = "!send " + message;
		tcpChannel.send(input);
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
		if (!loggedIn)
			return "Not logged in.";
		String address = lookup(username);
		if (address.contains("Not logged in")) {
			return "Not logged in. -OR- Wrong username or user not reachable.";
		}
		if (address.contains("Wrong username or user not registered")) {
			return "Wrong username or user not reachable.";
		}
		address = address.replace(':', ' ');
		String[] ipPort = address.split("\\s");
		TCPChannel channel = new TCPChannel(new Socket(InetAddress.getByName(ipPort[0]), Integer.parseInt(ipPort[1])));

		message = Cryptography.genMessageWithHMac(hmacKey, HMAC_ALGORITHM.HmacSHA256,
				"!msg " + this.username + ": " + message);

		channel.send(message);

		String response = channel.recvStr();

		boolean messageTampered = response.contains("!tampered");
		boolean replyTampered = !Cryptography.checkHMacInMessage(hmacKey, HMAC_ALGORITHM.HmacSHA256, response, true);

		response = response.substring(response.indexOf(" ") + 1, response.length());

		if (replyTampered) {
			response += "\n<NOTE: This comfirmation message sent from " + username
					+ " has been tampered!>";
		}
		if (messageTampered) {
			response += "\n<NOTE: Your message sent to " + username + " has been tampered";
			if (replyTampered) {
				response += " too";
			}
			response += "!>";
		}

		if(channel!=null){
			channel.close();
		}
		return response;
	}

	@Override
	@Command
	public String lookup(String username) throws IOException {
		if (!loggedIn)
			return "Not logged in.";
		String input = "!lookup " + username;
		tcpChannel.send(input);
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
		if (!loggedIn)
			return "Not logged in.";
		if (registered) {
			return "Already registered and waiting for private messages.";
		}
		String input = "!register " + privateAddress;
		tcpChannel.send(input);
		try {
			String response = registerQueue.take();
			if (response.startsWith("Successfully registered")) {
				privateAddress = privateAddress.replace(':', ' ');
				String[] ipPort = privateAddress.split("\\s");
				privateServer = new ServerSocket(Integer.parseInt(ipPort[1]));
				registered = true;
				TCPPrivateMessageListener tcpPMListener = new TCPPrivateMessageListener(
						privateServer, username, userResponseStream, hmacKey);
				pool.execute(tcpPMListener);
			}
			return response;
		} catch (BindException e) {
			String errorInput = "!register";
			tcpChannel.send(errorInput);
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
		tcpChannel.send("!exit");
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

		if (rsaChannel != null) {
			rsaChannel.close();
		}
		if (aesChannel != null) {
			aesChannel.close();
		}
		if (tcpChannel != null) {
			tcpChannel.close();
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
	@Command
	public String authenticate(String username) throws IOException {
		try {
			if (loggedIn)
				return "Already logged in.";

			// import private and public keys for communication
			String clientPrivateKeyPath = config.getString("keys.dir") + "/" + username + ".pem";
			PrivateKey clientPrivateKey = Keys.readPrivatePEM(new File(clientPrivateKeyPath));
			tcpChannel.setOwnKey(clientPrivateKey);

			String chatserverPublicKeyPath = config.getString("chatserver.key");
			PublicKey chatserverPublicKey = Keys.readPublicPEM(new File(chatserverPublicKeyPath));
			tcpChannel.setOppositeKey(chatserverPublicKey);

			//////////////////////////////////////////////////////////////////////////////////////////////

			String input = "!authenticate " + username;

			// generates 32 byte secure random clientChallenge
			SecureRandom secureRandom = new SecureRandom();
			final byte[] clientChallenge = new byte[32];
			secureRandom.nextBytes(clientChallenge);

			// encode clientChallenge and send
			byte[] encodedClientChallenge = Base64.encode(clientChallenge);
			input += " " + new String(encodedClientChallenge);
			//System.out.println("input with encodedchallenge: " + input);
			tcpChannel.send(input);

			//////////////////////////////////////////////////////////////////////////////////////////////

			String secondMessageResponse = new String(tcpChannel.recv());
			if (secondMessageResponse.startsWith("!ok")) {
				String[] secondMessageParts = secondMessageResponse.split("\\s");
				String clientChallengeFromServer = secondMessageParts[1];
				if (!new String(encodedClientChallenge).equals(clientChallengeFromServer)) {
					return "Client-challenges do not match. Stopping Handshake.";
				}
				String encodedChatserverChallenge = secondMessageParts[2];
				byte[] decodedSecretKeyString = Base64.decode(secondMessageParts[3].getBytes());
				byte[] decodedIvParameter = Base64.decode(secondMessageParts[4].getBytes());

				// AES channel
				aesChannel = new AESChannel(tcpChannel.getDecoratedChannel(), decodedIvParameter,
						decodedSecretKeyString);
				tcpChannel = aesChannel;
				tcpChannel.send(encodedChatserverChallenge);
				tcpReader = new TCPResponseReader(tcpChannel, userResponseStream, loginQueue,
						logoutQueue, registerQueue, lookupQueue, lastMsg);
				pool.execute(tcpReader);
				//System.out.println("serverch: " + encodedChatserverChallenge);
				this.username = username;
				this.loggedIn = true;
				return "Successfully logged in.";
			}
		} catch (FileNotFoundException e) {
			return "There exists no private key for the specified user.";
		}
		return "Authentication error.";
	}

}
