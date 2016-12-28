package chatserver.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Map;

import model.User;

public class TCPConnection extends Thread {

	private Socket socket;
	private List<TCPConnection> allConnections;
	private Map<String, User> userMap;
	private User user;
	private String ipPort;
	private BufferedReader reader;
	private PrintWriter writer;

	public TCPConnection(Socket socket, List<TCPConnection> allConnections,
			Map<String, User> userMap) {
		this.socket = socket;
		this.allConnections = allConnections;
		this.userMap = userMap;
		this.user = null;
		this.ipPort = null;
	}

	public Socket getSocket() {
		return socket;
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
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(), true);

			String request;
			while (!socket.isClosed() && (request = reader.readLine()) != null) {
				if (request.startsWith("!login")) {
					writer.println(login(request));
				} else if (request.startsWith("!logout")) {
					writer.println(logout());
				} else if (request.startsWith("!send")) {
					send(request);
				} else if (request.startsWith("!register")) {
					writer.println(register(request));
				} else if (request.startsWith("!lookup")) {
					writer.println(lookup(request));
				} else if (request.startsWith("!exit")) {
					exit();
				} else {
					writer.println("Error: Unknown request.");
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
		return "!logout" + "Successfully logged out.";
	}

	public void send(String request) {
		PrintWriter publicWriter = null;
		if (user == null) {
			writer.println("!failedpublic" + "Not logged in.");
			return;
		}
		try {
			synchronized (allConnections) {
				for (TCPConnection conn : allConnections) {
					if (conn.getUser() != null) {
						if (conn.getSocket() != socket) {
							publicWriter = new PrintWriter(conn.getSocket().getOutputStream(),
									true);
							publicWriter.println(
									"!public" + user.getName() + ": " + request.substring(6));
						} else {
							writer.println("!successpublic" + "Public message successfully sent.");
						}
					}
				}
			}
		} catch (IOException e) {
			System.err.println("Error occurred while communicating with client: " + e.getMessage());
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
		this.ipPort = ipPort.substring(10);
		return "!register" + "Successfully registered address for " + user.getName() + ".";
	}

	public String lookup(String request) {
		if (user == null) {
			return "!lookup" + "Not logged in. -OR- Wrong username or user not registered.";
		}
		String lookupUsername = request.substring(8);
		synchronized (allConnections) {
			for (TCPConnection conn : allConnections) {
				if (conn.getUser() != null) {
					if (conn.getUser().getName().equals(lookupUsername)) {
						if (conn.getIpPort() != null) {
							return "!lookup" + conn.getIpPort();
						}
					}
				}

			}
		}
		return "!lookup" + "Wrong username or user not registered.";
	}

	public void exit() {
		try {
			allConnections.remove(this);
			if (reader != null) {
				reader.close();
			}
			if (writer != null) {
				writer.close();
			}
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			System.err.println(
					"Error occurred while closing client communication: " + e.getMessage());
		}
	}

}
