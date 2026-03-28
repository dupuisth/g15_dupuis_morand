package com.gr15.server.managers;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.CTS_Message;
import com.gr15.common.message.MessageCTS;
import com.gr15.common.message.STC_Message;
import com.gr15.server.ServerApp;
import com.gr15.server.connections.ClientConnection;
import com.gr15.server.connections.ClientWrapper;
import com.gr15.server.handlers.ClientHandler;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class ClientManager extends Manager<ClientConnection, ClientWrapper> {
    /** Array of all the clients connected (index => localId) */
    private final ClientWrapper[] connectionsToClient = new ClientWrapper[ClientId.MAX_CLIENTS];

    private final Object connectionsLock = new Object();

    private final Queue<ClientConnection> connectionsToRemoveQueue = new LinkedList<>();
    private final Queue<MessageReceived> messageReceivedQueue = new LinkedList<>();
    private final Queue<MessageToSend> messageToSendQueue = new LinkedList<>();

    public ClientManager(ServerApp server) {
        super(server);
    }

    @Override
    public void pollEvents() {
        synchronized (connectionsToRemoveQueue) {
            synchronized (getConnectionsLock()) {
                while (!connectionsToRemoveQueue.isEmpty()) {
                    ClientConnection connectionToRemove = connectionsToRemoveQueue.poll();
                    stopConnection(connectionToRemove);
                }
            }
        }

        synchronized (messageReceivedQueue) {
            while (!messageReceivedQueue.isEmpty()) {
                MessageReceived received = messageReceivedQueue.poll();

                // Handle the message
                handleMessage(received.connection(), received.message());
            }
        }

        synchronized (messageToSendQueue) {
            while (!messageToSendQueue.isEmpty()) {
                MessageToSend toSend = messageToSendQueue.poll();
                toSend.connection.safeSend(toSend.message);
            }
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
            ListeningThread<ClientConnection> listeningThread = createDefaultListeningThread(clientConnection);
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
    protected void stopConnection(ClientConnection connection) {
        synchronized (getConnectionsLock()) {
            ClientWrapper wrapper = getWrapped(connection);

            int localId = ClientId.GetLocalId(wrapper.getConnection().getClientId());

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
        Logger.info("Fully stopped connection to " + connection);
    }

    @Override
    protected void onMessageRead(ClientConnection remoteConnection, Message message) {
        Logger.info("Received a message from " + remoteConnection + ", length=" + message);

        synchronized (messageReceivedQueue) {
            MessageReceived received = new MessageReceived(remoteConnection, message);
            messageReceivedQueue.add(received);
        }
    }

    protected void handleMessage(ClientConnection fromClient, Message message) {
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
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
            connectionsToRemoveQueue.add(remoteConnection);
        }

        // All exception are critical
        return true;
    }

    @Override
    public void send(ClientConnection remoteConnection, Message message) {
        synchronized (messageToSendQueue) {
            messageToSendQueue.add(new MessageToSend(remoteConnection, message));
        }
    }

    @Override
    public void sendToAll(Message message) {
        synchronized (messageToSendQueue) {
            synchronized (getConnectionsLock()) {
                for (ClientWrapper wrapper : connectionsToClient) {
                    if (wrapper == null) continue;
                    messageToSendQueue.add(new MessageToSend(wrapper.getConnection(), message));
                }
            }
        }
    }

    @Override
    public void sendToAll(Message message, ClientConnection except) {
        synchronized (messageToSendQueue) {
            synchronized (getConnectionsLock()) {
                for (ClientWrapper wrapper : connectionsToClient) {
                    if (wrapper == null || wrapper.getConnection() == null || wrapper.getConnection() == except) continue;
                    messageToSendQueue.add(new MessageToSend(wrapper.getConnection(), message));
                }
            }
        }
    }

    private void handleMessage(ClientConnection client, CTS_Message message) {
        Logger.info(message.toString() + " clientId=" + ClientId.toString(client.getClientId()));

        // Send to all clients except sender
        Message echoMessage = STC_Message.CreateMessage(client.getClientId(), message.getContent());
        sendToAll(echoMessage, client);
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

    record MessageReceived(ClientConnection connection, Message message) {}
    record MessageToSend(ClientConnection connection, Message message) {}
}
