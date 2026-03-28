package com.gr15.server;

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
    private volatile boolean isStopping = false;

    private final ServerConfig initialConfig;

    /** Socket used for the clients */
    private ServerSocket serverSocket;
    /** Array of all the clients connected (index => localId) */
    private final ClientConnection[] connectionsToClient = new ClientConnection[ClientId.MAX_CLIENTS];

    public ServerApp(ServerConfig initialConfig) {
        this.initialConfig = initialConfig;

        if (!initialConfig.validateConfiguration()) {
            Logger.error("Invalid configuration config=" + initialConfig);
            throw new IllegalArgumentException("Invalid configuration");
        }
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
            serverSocket = new ServerSocket(initialConfig.getClientSocketPort());
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
        ClientConnection clientConnection = null;

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
            clientConnection = new ClientConnection(socket,  nextClientId);
        } catch (IOException e) {
            Logger.error("Failed to bind new client, disconnecting it", e);
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.error("Failed to close the connection", ex);
            }
        }

        if (clientConnection == null) {
            // Error while binding the client, ignore him
            return;
        }

        // Add it to the clients list
        connectionsToClient[ClientId.GetLocalId(nextClientId)] = clientConnection;

        // Start the handler
        ClientHandler clientHandler = new ClientHandler(clientConnection, this);
        clientHandler.start();

        Logger.info("Created new client, c=" + ClientId.toString(nextClientId));
    }

    public void onClientDisconnected(ClientConnection client) {
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
    public void onMessageReceived(ClientConnection client, Message message) {
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
    public void handleMessage(ClientConnection client, CTS_Message message) {
        Logger.info(message.toString() + " clientId=" + ClientId.toString(client.getClientId()));

        // Send to all clients except sender
        Message echoMessage = STC_Message.CreateMessage(client.getClientId(), message.getContent());
        try {
            sendToClients(echoMessage, client);
        } catch (IOException e) {
            Logger.warn("Failed to send ! e="+e.getMessage());
        }
    }

    public void sendToClient(ClientConnection client, Message message) throws IOException {
        client.send(message);
    }

    public void sendToClients(Message message) throws IOException {
        synchronized (connectionsToClient) {
            for (ClientConnection client : connectionsToClient) {
                if (client == null) continue;
                client.send(message);
            }
        }
    }

    public void sendToClients(Message message, ClientConnection except) throws IOException {
        synchronized (connectionsToClient) {
            for (ClientConnection client : connectionsToClient) {
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
                    return ClientId.Create(initialConfig.getServerId(), i);
                }
            }
        }

        throw new RuntimeException("No more space in the network !");
    }
}
