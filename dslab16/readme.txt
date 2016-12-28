Reflect about your solution!


Summary:


Additional classes:

TCPListener - ServerSocket listens for incoming TCP connections
TCPConnection - Represents each client TCP connection

UDPListener - DatagramSocket listens for incoming UDP packets
UDPPacketTransfer - Represents each incoming DatagramPacket

TCPResponseReader - Read responses from the chatserver
TCPPrivateMessageListener - Listens for incoming TCP connections from other clients, 
reads private messages and automatically replies with an acknowledgement

UDPResponseReader - Read the chatserver's UDP responses

User (Model)

All necessary commands are implemented; server and client functionalities are tested manually 
and with automated tests which are not included in the project. User commands are handled by the provided Shell class.
- Threading strategy: Thread pools (implementations of java.util.concurrent.ExecutorService)
- Synchronization with thread-safe list and map implementations and synchronized lock objects
- BlockingQueues to handle server responses