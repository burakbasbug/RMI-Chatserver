package chatserver.tcp;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.bouncycastle.util.encoders.Base64;
import channels.AESChannel;
import channels.Channel;
import model.User;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;
import util.Keys;

public class TCPConnection extends Thread {

	private Channel tcpChannel;
	private Channel rsaChannel;
	private Channel aesChannel;
	private List<TCPConnection> allConnections;
	private Map<String, User> userMap;
	private User user;
	private String ipPort;
	private Config config;
	private PrintStream userResponseStream;
	private INameserverForChatserver nameserver;

	public TCPConnection(Channel tcpChannel, List<TCPConnection> allConnections,
			Map<String, User> userMap, PrintStream userResponseStream) {
		this.tcpChannel = tcpChannel;
		this.rsaChannel = tcpChannel;
		this.allConnections = allConnections;
		this.userMap = userMap;
		this.userResponseStream = userResponseStream;
		this.user = null;
		this.ipPort = null;
		this.config = new Config("chatserver");
		this.nameserver = null;
	}

	public Channel getTcpChannel() {
		return tcpChannel;
	}

	public User getUser() {
		return user;
	}

	public String getIpPort() {
		return ipPort;
	}

	@Override
	public void run() {
		try {
			String request;
			while (!tcpChannel.getSocket().isClosed()
					&& (request = new String(tcpChannel.recvByte())) != null) {
				// System.out.println("tcpconnection while: " +
				// tcpChannel.getClass());
				if (request.startsWith("!login")) {
					tcpChannel.send(login(request));
				} else if (request.startsWith("!logout")) {
					tcpChannel.send(logout());
				} else if (request.startsWith("!send")) {
					send(request);
				} else if (request.startsWith("!register")) {
					tcpChannel.send(register(request));
				} else if (request.startsWith("!lookup")) {
					tcpChannel.send(lookup(request));
				} else if (request.startsWith("!authenticate")) {
					authenticate(request);
				} else if (request.startsWith("!exit")) {
					exit();
				} else {
					tcpChannel.send("Error: Unknown request.");
				}
			}
		} catch (SocketException e) {
			logout();
			exit();
		} catch (IOException e) {
			System.err.println("Error occurred while communicating with client: " + e.getMessage());
		}
	}

	public String login(String request) {
		synchronized (userMap) {
			String[] parts = request.split("\\s");
			if (userMap.containsKey(parts[1])) {
				if (userMap.get(parts[1]).getPassword().equals(parts[2])) {
					if (!userMap.get(parts[1]).isLoggedIn()) {
						if (this.user != null) {
							return "!login" + "Already logged in with another account.";
						} else {
							this.user = userMap.get(parts[1]);
							this.user.setLoggedIn(true);
							return "!login" + "Successfully logged in.";
						}
					} else {
						return "!login" + "Already logged in.";
					}
				} else {
					return "!login" + "Wrong username or password.";
				}
			} else {
				return "!login" + "Wrong username or password.";
			}
		}
	}

	public String logout() {
		if (user == null) {
			return "!logout" + "Not logged in.";
		}
		synchronized (userMap) {
			user.setLoggedIn(false);
			user = null;
		}
		ipPort = null;
		tcpChannel = rsaChannel;
		return "!logout" + "Successfully logged out.";
	}

	public void send(String request) {
		if (user == null) {
			tcpChannel.send("!failedpublic" + "Not logged in.");
			return;
		}
		synchronized (allConnections) {
			for (TCPConnection conn : allConnections) {
				if (conn.getUser() != null) {
					if (!(conn.getTcpChannel().getDecoratedChannel()
							.equals(tcpChannel.getDecoratedChannel()))) {
						conn.getTcpChannel()
								.send("!public" + user.getName() + ": " + request.substring(6));
					} else {
						tcpChannel.send("!successpublic" + "Public message successfully sent.");
					}
				}
			}
		}
	}

	public String register(String ipPort) {
		if (user == null) {
			return "!register" + "Not logged in.";
		}
		if (ipPort.length() == 9) {
			this.ipPort = null;
			return "!register" + "No <IP:port> specified";
		}
		
		try {
			Registry reg = LocateRegistry.getRegistry(config.getString("registry.host"),config.getInt("registry.port"));
			this.nameserver = (INameserverForChatserver) reg.lookup(config.getString("root_id"));
			this.ipPort = ipPort.substring(10);
			this.nameserver.registerUser(user.getName(), this.ipPort);
		} catch (RemoteException | NotBoundException | AlreadyRegisteredException | InvalidDomainException e) {
			this.ipPort = null;
			return "!register" + "Registration failed: " + e.getMessage();
		}
		return "!register" + "Successfully registered address for " + user.getName() + ".";
	}

	public String lookup(String request) {
		if (user == null) {
			return "!lookup" + "Not logged in. -OR- Wrong username or user not registered.";
		}else if(nameserver == null){
			return "!lookup" + "User is not registered.";
		}
			
		String addr = "";
		try {
			String username = request.substring(8);
			int lastIndex = username.lastIndexOf(".");
			INameserverForChatserver ns = this.nameserver;
			while(lastIndex!=-1){
				String zone = username.substring(lastIndex+1);
				username = username.substring(0,lastIndex);
				ns = ns.getNameserver(zone);			
				if(ns==null){
					throw new InvalidDomainException("Zone \'" + zone + "\' does not exist!");
				}else{
					lastIndex = username.lastIndexOf(".");
				}
			}
			addr = ns.lookup(username);
		} catch (RemoteException | InvalidDomainException e) {
			return "!lookup" + "Error: " + e.getMessage();
		}
		
		if(addr==null){			
			return "!lookup" + "Wrong username or user not registered.";
		}else{
			return "!lookup" + addr;
		}
	}

	private void authenticate(String firstMessage) {
		// System.out.println("called auth: \n" + firstMessage);
		String[] parts = firstMessage.split("\\s");
		String username = parts[1];

		// generates 32 byte secure random number
		SecureRandom secureRandom = new SecureRandom();
		final byte[] chatserverChallenge = new byte[32];
		secureRandom.nextBytes(chatserverChallenge);

		try {
			// generate 256 bit secret key
			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(256);
			SecretKey secretKey = generator.generateKey();

			// generate 16 byte ivParameter
			final byte[] ivParameter = new byte[16];
			secureRandom.nextBytes(ivParameter);

			// base64 encode all arguments
			byte[] encodedChatserverChallenge = Base64.encode(chatserverChallenge);
			byte[] encodedSecretKey = Base64.encode(secretKey.getEncoded());
			byte[] encodedIvParameter = Base64.encode(ivParameter);

			String secondMessage = "!ok " + parts[2] + " " + new String(encodedChatserverChallenge)
					+ " " + new String(encodedSecretKey) + " " + new String(encodedIvParameter);
			// System.out.println("finishedsecondmessage: " + secondMessage);

			String clientPublicKeyPath = config.getString("keys.dir") + "/" + username + ".pub.pem";
			PublicKey clientPublicKey = Keys.readPublicPEM(new File(clientPublicKeyPath));
			tcpChannel.setOppositeKey(clientPublicKey);
			tcpChannel.send(secondMessage);

			// AES channel
			aesChannel = new AESChannel(tcpChannel.getDecoratedChannel(), ivParameter,
					secretKey.getEncoded());
			tcpChannel = aesChannel;
			byte[] thirdMessage = tcpChannel.recvByte();

			// check server challenges
			// System.out.println("decryptedThirdMessage: " + new
			// String(thirdMessage));
			if (!new String(thirdMessage).equals(new String(encodedChatserverChallenge))) {
				userResponseStream
						.println("Server-challenges do not match. Closing the connection.");
				return;
			}
			synchronized (userMap) {
				if (userMap.containsKey(username)) {
					if (!userMap.get(parts[1]).isLoggedIn()) {
						this.user = userMap.get(parts[1]);
						this.user.setLoggedIn(true);
						userResponseStream.println("Client connected");
						// return "!login" + "Successfully logged in.";
					} else {
						// return "!login" + "Already logged in.";
					}
				} else {
					// return "!login" + "Wrong username or password.";
				}
			}
		} catch (NoSuchAlgorithmException e) {
			System.err.println(e.getMessage());
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	public void exit() {
		try {
			allConnections.remove(this);
			tcpChannel.close();
		} catch (IOException e) {
			System.err.println(
					"Error occurred while closing client communication: " + e.getMessage());
		}
	}

}
