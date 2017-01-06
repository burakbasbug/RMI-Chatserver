package channels;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.security.Key;

public class TCPChannel implements Channel {

	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;

	public TCPChannel(Socket socket) {
		this.socket = socket;
		try {
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			System.err.println(
					"Error occurred while setting up reader and writer: " + e.getMessage());
		}
	}

	@Override
	public Socket getSocket() {
		return socket;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TCPChannel other = (TCPChannel) obj;
		if (socket == null) {
			if (other.getSocket() != null)
				return false;
		} else if (socket != other.getSocket())
			return false;
		return true;
	}

	@Override
	public void send(String msg) {
		writer.println(msg);
	}

	@Override
	public byte[] recvByte() throws SocketException, IOException {
		return reader.readLine().getBytes();
	}
	
	@Override
	public String recvString() throws SocketException, IOException {
		return reader.readLine();
	}

	@Override
	public void close() throws IOException {
		if (reader != null) {
			reader.close();
		}
		if (writer != null) {
			writer.close();
		}
		if (socket != null && !socket.isClosed()) {
			socket.close();
		}
	}
	
	@Override
	public void setOwnKey(Key ownKey) {}

	@Override
	public void setOppositeKey(Key oppositeKey) {}

	@Override
	public Channel getDecoratedChannel() {
		return null;
	}
}
