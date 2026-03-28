package com.gr15.server;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.message.CTS_Message;
import com.gr15.common.message.MessageCTS;
import com.gr15.common.message.STC_Message;
import com.gr15.common.message.STC_MessageRemoveClient;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Application to run for the server
 */
public class ServerApp {
    public static final String SERVER_ID_KEY = "serverId=";
    public static final String SERVER_PORT_KEY = "port=";

    private boolean isStopping = false;
    private int port;
    private int serverId;

    /** Socket used for the clients */
    private ServerSocket serverSocket;
    /** Array of all the clients connected (index => localId) */
    private final ConnectionToClient[] connectionsToClient = new ConnectionToClient[ClientId.MAX_CLIENTS];

    public ServerApp(String[] args) {
        // Default to bad values
        port = -1;
        serverId = -1;

        // Read the args to get configuration from it
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
        Logger.info("Started new ServerApp");

        // Prevent running the server two times
        if (serverSocket != null) {
            Logger.warn("Server already started");
            return;
        }

        // Start the server socket
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            Logger.error("Failed to create the server socket", e);
            return;
        }

        // Start the server accepting thread
        SocketAcceptingThread serverSocketAcceptingThread = new SocketAcceptingThread(serverSocket, this::handleNewClientSocket);
        serverSocketAcceptingThread.start();

        // Keep alive
        while (!isStopping) {
            ThreadUtils.safeSleep(1000);
        }

        // Destroy the objects
        Logger.info("Cleaning up");
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocketAcceptingThread.setShouldStop();
            serverSocketAcceptingThread.interrupt();

            try {
                serverSocketAcceptingThread.join(1000);
            } catch (InterruptedException e) {
                Logger.error("Exception while trying waiting for serverSocketAcceptingThread ending", e);
            }

            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.error("Error while closing socket: " + e.getMessage(), e);
            }
        }
        Logger.info("Cleanup done, exiting");
    }

    public void stop() {
        Logger.debug("Stop received");
        isStopping = true;
    }

    private void handleNewClientSocket(Socket socket) {
        Logger.info("New client socket inet=" + socket.getInetAddress() + ":" + socket.getPort());

        // Create a new connection
        ConnectionToClient connectionToClient = null;

        int nextClientId;
        try {
            nextClientId = getNextClientId();
        } catch (RuntimeException e) {
            Logger.error("Failed to create the client id", e);

            // Close
            try {
                socket.close();
            } catch (IOException ex) {
                // Ignore
            }

            // Ignore this socket, we can't accept him
            return;
        }

        try {
            connectionToClient = new ConnectionToClient(socket,  nextClientId);
        } catch (IOException e) {
            Logger.error("Failed to bind new client, disconnecting it", e);
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.error("Failed to close the connection", ex);
            }
        }

        if (connectionToClient == null) {
            // Error while binding the client, ignore him
            return;
        }

        // Add it to the clients list
        connectionsToClient[ClientId.GetLocalId(nextClientId)] = connectionToClient;

        // Start the handler
        ClientHandler clientHandler = new ClientHandler(connectionToClient, this);
        clientHandler.start();

        Logger.info("Created new client, c=" + ClientId.toString(nextClientId));
    }

    public void onClientDisconnected(ConnectionToClient client) {
        // Remove the client from the list, and notify clients

        try {
            int localId = ClientId.GetLocalId(client.getClientId());
            if (localId < 0 || localId >= ClientId.MAX_CLIENTS) {
                // The id is not valid, something is wrong
                throw new RuntimeException("The given clientId is not valid, something is wrong clientId=" + ClientId.toString(client.getClientId()));
            }

            // Remove it
            synchronized (connectionsToClient) {
                connectionsToClient[localId] = null;
            }
            // Notify clients
            Message notifyMessage = STC_MessageRemoveClient.CreateMessage(client.getClientId());
            sendToClients(notifyMessage); // No need to except, since the client is already removed
        } catch (Exception e) {
            Logger.error("Exception while handling client disconnection e=" + e.getMessage(), e);
        }
    }

    /**
     * When a message is received from a client
     */
    public void onMessageReceived(ConnectionToClient client, Message message) {
        Logger.debug("Received a message (CTS) ! from=" + ClientId.toString(client.getClientId())  + " / " + client.getSocket().getInetAddress() + ":" + client.getSocket().getPort()  + " length=" + message.getWrittenByte());

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
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
        }
    }

    /**
     * Handle a message from a client
     */
    public void handleMessage(ConnectionToClient client, CTS_Message message) {
        Logger.info(message.toString() + " clientId=" + ClientId.toString(client.getClientId()));

        // Send to all clients except sender
        Message echoMessage = STC_Message.CreateMessage(client.getClientId(), message.getContent());
        try {
            sendToClients(echoMessage, client);
        } catch (IOException e) {
            Logger.warn("Failed to send ! e="+e.getMessage());
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
        synchronized (connectionsToClient)
        {
            for (int i = 0; i < ClientId.MAX_CLIENTS; i++) {
                if (connectionsToClient[i] == null) {
                    return ClientId.Create(serverId, i);
                }
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
