package com.gr15.server;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.common.message.CTS_Message;
import com.gr15.common.Message;
import com.gr15.common.message.MessageCTS;
import com.gr15.common.message.STC_Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

public class ServerApp {
    public static final String SERVER_ID_KEY = "serverId=";
    public static final String SERVER_PORT_KEY = "port=";

    private static final Logger LOGGER = Logger.getLogger(ServerApp.class.getName());

    private boolean isStopping = false;
    private int port;
    private int serverId;

    private ServerSocket serverSocket;
    private final ConnectionToClient[] connectionsToClient = new ConnectionToClient[ClientId.MAX_CLIENTS];

    public ServerApp(String[] args) {
        // Default to bad values
        port = -1;
        serverId = -1;

        for (String arg : args) {
            if (arg.startsWith(SERVER_ID_KEY)) {
                try {
                    serverId = Integer.parseInt(arg.substring(SERVER_ID_KEY.length()));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            } else if (arg.startsWith(SERVER_PORT_KEY)) {
                try {
                    port = Integer.parseInt(arg.substring(SERVER_PORT_KEY.length()));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            }
        }

        if (this.serverId < 0) {
            this.serverId = CliHelper.inputInt("Enter the server ID", 0, 32);
        }
        if (this.port <= 0) {
            this.port = CliHelper.inputInt("Enter the server port", 2222, 8888);
        }
    }

    public ServerApp(int serverId, int port) {
        this.serverId = serverId;
        this.port = port;
    }

    public void run() {
        LOGGER.info("Started new ServerApp");

        if (serverSocket != null) {
            LOGGER.warning("Server already started");
            return;
        }

        // Start the server socket
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            LOGGER.warning("Failed to create a socket for " + toString());
            return;
        }

        while (!isStopping) {
            Socket socket;
            try {
                // Block until a client open a connection
                socket = serverSocket.accept();
            } catch (IOException e) {
                LOGGER.warning("Failed to accept socket: " + e.getMessage());
                // Block again
                continue;
            }

            LOGGER.info("New client inet=" + socket.getInetAddress() + " port=" + socket.getPort());

            // Create a new connection
            ConnectionToClient connectionToClient = null;

            int nextClientId;
            try {
                nextClientId = getNextClientId();
            } catch (RuntimeException e) {
                LOGGER.warning("Failed to create the client id, e=" + e.getMessage());

                // Close
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Ignore
                }

                // Ignore this socket, we can't accept him
                continue;
            }

            try {
                connectionToClient = new ConnectionToClient(socket,  nextClientId);
            } catch (IOException e) {
                LOGGER.warning("Failed to bind new client, disconnecting him");

                try {
                    socket.close();
                } catch (IOException ex) {
                    LOGGER.warning("Failed to close the connection");
                }
            }

            if (connectionToClient == null) {
                // Error while binding the client, ignore him
                continue;
            }

            // Add it to the clients list
            connectionsToClient[ClientId.GetLocalId(nextClientId)] = connectionToClient;

            // Start the handler
            ClientHandler clientHandler = new ClientHandler(connectionToClient, this);
            clientHandler.start();

            LOGGER.info("Created new client, id="+nextClientId + " localId=" + ClientId.GetLocalId(nextClientId));
        }

        // Destroy the objects
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.warning("Error while closing socket: " + e.getMessage());
            }
        }
    }

    public void stop() {
        isStopping = true;
    }

    /**
     * When a message is received
     */
    public void onMessageReceived(ConnectionToClient client, Message message) {
        LOGGER.info("Received a message (CTS) ! from=" + ClientId.toString(client.getClientId())  + " / " + client.getSocket().getInetAddress() + ":" + client.getSocket().getPort()  + " length=" + message.getWrittenByte());

        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageCTS messageType = MessageCTS.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case MESSAGE -> {
                CTS_Message parsedMessage = CTS_Message.ReadMessage(message);
                handleMessage(client, parsedMessage);
            }
            case null -> {
                LOGGER.warning("Unknown message type, ignoring it (id=" + messageId + ")");
            }
        }
    }

    public void handleMessage(ConnectionToClient client, CTS_Message message) {
        LOGGER.info(message.toString());

        // Send to all clients except sender
        Message echoMessage = STC_Message.CreateMessage(client.getClientId(), message.getContent());
        try {
            sendToClients(echoMessage, client);
        } catch (IOException e) {
            LOGGER.warning("Failed to send ! e="+e.getMessage());
        }
    }

    public void sendToClient(ConnectionToClient client, Message message) throws IOException {
        client.send(message);
    }

    public void sendToClients(Message message) throws IOException {
        synchronized (connectionsToClient) {
            for (ConnectionToClient client : connectionsToClient) {
                if (client == null) continue;
                client.send(message);
            }
        }
    }

    public void sendToClients(Message message, ConnectionToClient except) throws IOException {
        synchronized (connectionsToClient) {
            for (ConnectionToClient client : connectionsToClient) {
                if (client == null || client == except) continue;
                client.send(message);
            }
        }
    }

    public int getNextClientId() throws RuntimeException{
        for (int i = 0; i < ClientId.MAX_CLIENTS; i++) {
            if (connectionsToClient[i] == null) {
                return ClientId.Create(serverId, i);
            }
        }

        throw new RuntimeException("No more space in the network !");
    }

    public int getPort() {
        return port;
    }

    public int getServerId() {
        return serverId;
    }

    public ConnectionToClient[] getConnectionsToClient() {
        return connectionsToClient;
    }

    @Override
    public String toString() {
        return "ServerApp{" +
                "port=" + port +
                ", serverId=" + serverId +
                '}';
    }
}
