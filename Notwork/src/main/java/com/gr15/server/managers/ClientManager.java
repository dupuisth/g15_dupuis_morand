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

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

public class ClientManager extends Manager<ClientConnection, ClientWrapper> {
    /** Array of all the clients connected (index => localId) */
    private final ClientWrapper[] connectionsToClient = new ClientWrapper[ClientId.MAX_CLIENTS];

    private final Object connectionsLock = new Object();

    private final ArrayList<ClientWrapper> clientsToRemove = new ArrayList<>();

    public ClientManager(ServerApp server) {
        super(server);
    }

    @Override
    public void pollEvents() {
        synchronized (clientsToRemove) {
            synchronized (getConnectionsLock()) {
                for (ClientWrapper wrapper : clientsToRemove) {
                    stopConnection(wrapper);
                }
            }
            clientsToRemove.clear();
        }
    }

    @Override
    protected void handleNewSocket(Socket socket) {
        Logger.info("New client socket inet=" + socket.getInetAddress() + ":" + socket.getPort());

        // Create a new connection
        ClientConnection clientConnection = null;

        synchronized (getConnectionsLock()) {
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

            // Create and start the listening thread
            ListeningThread<ClientConnection> listeningThread = createListeningThread(clientConnection);
            listeningThread.start();

            // Create and start the handler
            ClientHandler clientHandler = new ClientHandler(clientConnection, server);
            clientHandler.start();

            // Add it to the clients list
            ClientWrapper wrapper = new ClientWrapper(clientConnection, listeningThread, clientHandler);
            connectionsToClient[ClientId.GetLocalId(nextClientId)] = wrapper;
        }

        Logger.info("Created new client, c=" + ClientId.toString(clientConnection.getClientId()));
    }

    @Override
    protected void handleConnectionLoosed(ClientConnection client) {
        try {
            ClientWrapper wrapped = getWrapped(client);
            if (wrapped == null) {
                throw new RuntimeException("Could not get the wrapper for " + client);
            }

            stopConnection(wrapped);

            // Notify clients
            Message notifyMessage = STC_MessageRemoveClient.CreateMessage(client.getClientId());
            sendToAll(notifyMessage); // No need to except, since the client is already removed
        } catch (Exception e) {
            Logger.error("Exception while handling client disconnection e=" + e.getMessage(), e);
        }
    }

    @Override
    protected void stopConnection(ClientWrapper wrapper) {
        synchronized (getConnectionsLock()) {
            int localId = ClientId.GetLocalId(wrapper.getConnection().getClientId());

            Logger.debug("Stopping gracefully the wrapper " + wrapper);

            wrapper.getConnection().close();
            wrapper.getListeningThread().setShouldStop();
            wrapper.getListeningThread().interrupt();
            wrapper.getHandler().setShouldStop();
            wrapper.getHandler().interrupt();


            try {
                wrapper.getListeningThread().join(1000);
            } catch (InterruptedException e) {
                Logger.error("", e);
            }
            try {
                wrapper.getHandler().join(1000);
            } catch (InterruptedException e) {
                Logger.error("", e);
            }

            // Remove the connection
            getConnections()[localId] = null;
        }
        Logger.info("Fully stopped connection to " + wrapper);
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
    protected boolean onListeningError(ClientConnection remoteConnection, Exception e) {
        // This is called from the ListeningThread, so make sure this is running from the main thread
        synchronized (getConnectionsLock()) {
            clientsToRemove.add(getWrapped(remoteConnection));
        }

        // All exception are critical
        return true;
    }

    private void handleMessage(ClientConnection client, CTS_Message message) {
        Logger.info(message.toString() + " clientId=" + ClientId.toString(client.getClientId()));

        // Send to all clients except sender
        Message echoMessage = STC_Message.CreateMessage(client.getClientId(), message.getContent());
        try {
            sendToAll(echoMessage, client);
        } catch (IOException e) {
            Logger.warn("Failed to send ! e="+e.getMessage());
        }
    }

    private int getNextClientId() throws RuntimeException{
        synchronized (getConnectionsLock())
        {
            for (int i = 0; i < ClientId.MAX_CLIENTS; i++) {
                if (connectionsToClient[i] == null) {
                    return ClientId.Create(server.getInitialConfig().getServerId(), i);
                }
            }
        }

        throw new RuntimeException("No more space in the network !");
    }

    @Override
    public int getPort() {
        return server.getInitialConfig().getClientSocketPort();
    }

    @Override
    public ClientWrapper[] getConnections() {
        return connectionsToClient;
    }

    @Override
    public Object getConnectionsLock() {
        return connectionsLock;
    }
}
