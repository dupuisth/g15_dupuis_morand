package com.gr15.server.managers;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.CTS_Message;
import com.gr15.common.message.MessageCTS;
import com.gr15.common.message.STC_Message;
import com.gr15.common.message.STC_MessageRemoveClient;
import com.gr15.server.ServerApp;
import com.gr15.server.SocketAcceptingThread;
import com.gr15.server.connections.ClientConnection;
import com.gr15.server.handlers.ClientHandler;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ClientManager extends Manager<ClientConnection> {

    /** Socket used for the clients */
    private ServerSocket serverSocket;
    /** Array of all the clients connected (index => localId) */
    private final ClientConnection[] connectionsToClient = new ClientConnection[ClientId.MAX_CLIENTS];
    /** Thread that accepts the socket automatically */
    private SocketAcceptingThread acceptingThread;

    @Override
    public ClientConnection[] getConnections() {
        return connectionsToClient;
    }

    @Override
    public int getPort() {
        return server.getInitialConfig().getClientSocketPort();
    }

    public ClientManager(ServerApp server) {
        super(server);
    }


    @Override
    protected void handleNewSocket(Socket socket) {
        Logger.info("New client socket inet=" + socket.getInetAddress() + ":" + socket.getPort());

        // Create a new connection
        ClientConnection clientConnection = null;

        synchronized (connectionsToClient) {
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
            createListeningThread(clientConnection);
        }

        // Start the handler
        ClientHandler clientHandler = new ClientHandler(clientConnection, server);
        clientHandler.start();

        Logger.info("Created new client, c=" + ClientId.toString(clientConnection.getClientId()));
    }

    @Override
    protected void handleConnectionLoosed(ClientConnection client) {
        // Remove the client from the list, and notify clients

        try {
            try {
                // Force to close the socket (in case not already done)
                client.close();
            } catch (Exception ignored) {

            }

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
            sendToAll(notifyMessage); // No need to except, since the client is already removed
        } catch (Exception e) {
            Logger.error("Exception while handling client disconnection e=" + e.getMessage(), e);
        }

        Logger.info("Removed client c=" + client);
    }

    @Override
    protected void dispatchMessage(ClientConnection fromClient, int messageId, Message message) {
        MessageCTS messageType = MessageCTS.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case MESSAGE -> {
                CTS_Message parsedMessage = CTS_Message.ReadMessage(message);
                handleMessage(fromClient, parsedMessage);
            }
            case null -> {
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
        }
    }

    @Override
    protected void onListeningError(ClientConnection remoteConnection, Exception e) {
        // Nothing special to do...
        handleConnectionLoosed(remoteConnection);
    }

    public void handleMessage(ClientConnection client, CTS_Message message) {
        Logger.info(message.toString() + " clientId=" + ClientId.toString(client.getClientId()));

        // Send to all clients except sender
        Message echoMessage = STC_Message.CreateMessage(client.getClientId(), message.getContent());
        try {
            sendToAll(echoMessage, client);
        } catch (IOException e) {
            Logger.warn("Failed to send ! e="+e.getMessage());
        }
    }


    public int getNextClientId() throws RuntimeException{
        synchronized (connectionsToClient)
        {
            for (int i = 0; i < ClientId.MAX_CLIENTS; i++) {
                if (connectionsToClient[i] == null) {
                    return ClientId.Create(server.getInitialConfig().getServerId(), i);
                }
            }
        }

        throw new RuntimeException("No more space in the network !");
    }
}
