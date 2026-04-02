package com.gr15.server.managers;

import com.gr15.common.Constants;
import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.BroadcastId;
import com.gr15.common.message.stc.STC_Message;
import com.gr15.common.message.sts.BroadcastData;
import com.gr15.common.message.sts.MessageSTS;
import com.gr15.common.message.sts.STS_BroadcastChat;
import com.gr15.common.message.sts.STS_Identify;
import com.gr15.server.ServerApp;
import com.gr15.server.ServerConfig;
import com.gr15.server.connections.ServerConnection;
import com.gr15.server.connections.ServerWrapper;
import static com.gr15.common.Constants.*;

import com.gr15.server.handlers.ServerHandler;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class ServerManager extends Manager<ServerConnection, ServerWrapper> {
    /** Array of all the servers connected (index => serverId) */
    private final ServerWrapper[] connectionsToServer = new ServerWrapper[MAX_SERVERS];
    private final ArrayList<ServerWrapper> pendingAuthentification = new ArrayList<>();

    private final Object connectionsLock = new Object();

    private final Queue<ServerConnection> connectionsToRemoveQueue = new LinkedList<>();
    private final Queue<MessageReceived> messageReceivedQueue = new LinkedList<>();
    private final Queue<MessageToSend> messageToSendQueue = new LinkedList<>();

    private final HashMap<Integer, LocalDateTime> broadcastMap = new HashMap<>();
    int currentLocalBroadcastId = 0;

    ServerConnectToNeighborThread connectToNeighborThread;

    public ServerManager(ServerApp server) {
        super(server);
    }

    @Override
    public void start() throws RuntimeException {
        super.start();

        connectToNeighborThread = new ServerConnectToNeighborThread(this);
        connectToNeighborThread.start();
    }

    @Override
    public void stop() {
        super.stop();

        if (connectToNeighborThread != null) {
            connectToNeighborThread.setShouldStop();
            try {
                connectToNeighborThread.join(1000);
            } catch (InterruptedException e) {
                Logger.error("Exception while trying waiting for connectToNeighborThread ending", e);
            }
        }
    }

    @Override
    public void pollEvents() {
        while (true) {
            ServerConnection connectionToRemove;
            synchronized (connectionsToRemoveQueue) {
                connectionToRemove = connectionsToRemoveQueue.poll();
            }
            if (connectionToRemove == null) {
                break;
            }
            stopConnection(connectionToRemove);
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
        Logger.info("New server socket inet=" + socket.getInetAddress() + ":" + socket.getPort());

        // Create a new connection
        ServerConnection serverConnection;
        try {
            serverConnection = new ServerConnection(socket, null);
        } catch (IOException e) {
            Logger.error("Failed to bind new server, disconnecting it", e);
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.error("Exception while closing socket", e);
            }

            return;
        }

        // Create a handler
        ServerHandler handler = new ServerHandler(serverConnection, server, null);
        // Create the listening thread
        ListeningThread<ServerConnection> listener = createDefaultListeningThread(serverConnection);

        ServerWrapper wrapper = new ServerWrapper(serverConnection, listener, handler);

        // Add to the list
        synchronized (pendingAuthentification) {
            pendingAuthentification.add(wrapper);
        }

        // Then start acting
        listener.start();
        handler.start();
    }

    @Override
    protected void stopConnection(ServerConnection connection) {
        synchronized (getConnectionsLock()) {
            ServerWrapper wrapper;

            boolean isPending = connection.getServerId() == null;
            if (isPending) {
                // Find the wrapper in the pending list
                synchronized (pendingAuthentification) {
                    wrapper = null;
                    for (ServerWrapper sw : pendingAuthentification) {
                        if (sw.getConnection() == connection) {
                            wrapper = sw;
                            break;
                        }
                    }
                }
            } else {
                // Else we can just fetch it from the connections array
                wrapper = getWrapped(connection);
            }

            if (wrapper == null) {
                Logger.warn("Trying to stop a server connection that is not registered: " + connection);
                return;
            }

            wrapper.getListeningThread().setShouldStop();
            wrapper.getConnection().close();
            wrapper.getListeningThread().interrupt();
            wrapper.getHandler().setShouldStop();
            wrapper.getHandler().interrupt();

            try {
                wrapper.getListeningThread().join(1000);
            } catch (InterruptedException e) {
                Logger.error("Interrupted while joining listening thread", e);
                Thread.currentThread().interrupt();
            }

            try {
                wrapper.getHandler().join(1000);
            } catch (InterruptedException e) {
                Logger.error("Interrupted while joining handler thread", e);
                Thread.currentThread().interrupt();
            }

            // Remove the connection
            if (isPending) {
                synchronized (pendingAuthentification) {
                    pendingAuthentification.remove(wrapper);
                }
            } else {
                connectionsToServer[connection.getServerId()] = null;
            }

        }
        Logger.info("Fully stopped connection to " + connection);
    }

    @Override
    protected void onMessageRead(ServerConnection remoteConnection, Message message) {
        Logger.info("Received a message from " + remoteConnection + ", length=" + message);

        synchronized (messageReceivedQueue) {
            MessageReceived received = new MessageReceived(remoteConnection, message);
            messageReceivedQueue.add(received);
        }
    }

    protected void handleMessage(ServerConnection fromServer, Message message) {
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageSTS messageType = MessageSTS.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case null -> {
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
            case HELLO -> {
            }
            case IDENTIFY -> {
                STS_Identify parsed = STS_Identify.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
            case BROADCAST_CHAT -> {
                STS_BroadcastChat parsed = STS_BroadcastChat.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
        }
    }

    private void handleMessage(ServerConnection fromServer, STS_Identify identify) {
        Logger.info("Received identify " + identify);

        // Register the identity
        fromServer.setServerId(identify.getFromServerId());

        ServerWrapper pendingWrapper = null;
        synchronized (pendingAuthentification) {
            for (int i = 0; i < pendingAuthentification.size(); i++) {
                if (pendingAuthentification.get(i).getConnection() != fromServer) continue;
                pendingWrapper = pendingAuthentification.get(i);
                pendingAuthentification.remove(i);
                break;
            }
        }
        
        if (pendingWrapper != null) {
            synchronized (getConnectionsLock()) {
                connectionsToServer[fromServer.getServerId()] = pendingWrapper;
            }
        } else {
            Logger.warn("Received an identity message but the server is already registered: " + identify.getFromServerId());
        }

        if (identify.getRebounds() >= 1) {
            return;
        }

        // Create the response
        Message response = STS_Identify.CreateMessage(server.getInitialConfig().getServerId(), identify.getRebounds() + 1);
        send(fromServer, response);
    }

    private void handleMessage(ServerConnection fromServer, STS_BroadcastChat broadcastChat) {
        Logger.info("Received " + broadcastChat);


        if (!shouldHandleBroadcast(broadcastChat.getBroadcastData())) {
            return;
        }


        // Broadcast it to my clients
        Message message = STC_Message.CreateMessage(broadcastChat.getFromClientId(), broadcastChat.getContent());
        server.getClientManager().sendToAll(message);

        if (broadcastChat.getBroadcastData().getTtl() > 0) {
            // Broadcast to neighbors (except from the one we received)
            sendToAll(STS_BroadcastChat.CreateMessage(broadcastChat.getFromClientId(), broadcastChat.getContent(), broadcastChat.getBroadcastData().decrementTtl()), fromServer);
        }
    }

    private boolean shouldHandleBroadcast(BroadcastData broadcastData) {
        boolean shouldHandle = true;

        LocalDateTime currentDateTime = LocalDateTime.now();

        // Check if I've already handled this message (or if it is mine)
        int broadcastId = broadcastData.getBroadcastId();
        if (BroadcastId.GetServerId(broadcastId) == server.getInitialConfig().getServerId()) {
            // It's my message, do nothing
            Logger.debug("Received my own broadcast, doing nothing");
            shouldHandle = false;
        }
        else { // Check if the broadcastId was already handled (or if it expired)
            synchronized (broadcastMap) {
                if (broadcastMap.containsKey(broadcastId)) {
                    // This id is already in the map, check if it was long ago
                    LocalDateTime dateTime = broadcastMap.get(broadcastId);
                    Duration gap = Duration.between(dateTime, currentDateTime);

                    // Check if the gap is enough to forget it (if not, don't handle)
                    if (gap.getSeconds() < BROADCAST_ID_FORGER_AFTER_SECONDS) {
                        shouldHandle = false;
                    }
                }
            }
        }

        if (shouldHandle) {
            // Update the map
            synchronized (broadcastMap) {
                broadcastMap.put(broadcastId, currentDateTime);
            }
        }

        return shouldHandle;
    }

    @Override
    protected boolean onListeningError(ServerConnection remoteConnection, Exception e) {
        // This is called from the ListeningThread, so make sure this is running from the main thread
        synchronized (connectionsToRemoveQueue) {
            connectionsToRemoveQueue.add(remoteConnection);
        }

        // All exception are critical
        return true;
    }

    @Override
    public void send(ServerConnection remoteConnection, Message message) {
        synchronized (messageToSendQueue) {
            messageToSendQueue.add(new MessageToSend(remoteConnection, message));
        }
    }

    @Override
    public void sendToAll(Message message) {
        synchronized (getConnectionsLock()) {
            synchronized (messageToSendQueue) {
                for (ServerWrapper wrapper : connectionsToServer) {
                    if (wrapper == null) continue;
                    messageToSendQueue.add(new MessageToSend(wrapper.getConnection(), message));
                }
            }
        }
    }

    @Override
    public void sendToAll(Message message, ServerConnection except) {
        synchronized (getConnectionsLock()) {
            synchronized (messageToSendQueue) {
                for (ServerWrapper wrapper : connectionsToServer) {
                    if (wrapper == null || wrapper.getConnection() == null || wrapper.getConnection() == except) continue;
                    messageToSendQueue.add(new MessageToSend(wrapper.getConnection(), message));
                }
            }
        }
    }

    @Override
    public void send(List<ServerConnection> remoteConnections, Message message) {
        synchronized (messageToSendQueue) {
            for (ServerConnection connection : remoteConnections) {
                messageToSendQueue.add(new MessageToSend(connection, message));
            }
        }
    }

    Set<Integer> getServerIds() {
        Set<Integer> serverIds = new HashSet<>();
        synchronized (getConnectionsLock()) {
            for (ServerWrapper sw : connectionsToServer) {
                if (sw == null) continue;
                serverIds.add(sw.getConnection().getServerId());
            }
        }
        synchronized (pendingAuthentification) {
            for (ServerWrapper sw : pendingAuthentification) {
                if (sw == null) continue;
                serverIds.add(sw.getConnection().getServerId());
            }
        }
        return serverIds;
    }

    /**
     * Create a broadcastId from currentLocalBroadcastId, increment currentLocalBroadcastId and return the created broadcastId
     */
    public int getNextBroadcastId() {
        int localId = currentLocalBroadcastId++;
        if (currentLocalBroadcastId > Math.powExact(2, BROADCAST_ID_LOCAL_BITS) - 1) {
            currentLocalBroadcastId = 0;
            Logger.info("Reached the max of localBroadcastId, resetting");
        }

        int broadcastId = BroadcastId.Create(server.getInitialConfig().getServerId(), localId);
        return broadcastId;
    }

    @Override
    public int getPort() {
        return server.getInitialConfig().getServerSocketPort();
    }

    @Override
    public Object getConnectionsLock() {
        return connectionsLock;
    }

    @Override
    public ServerWrapper[] getConnections() {
        return connectionsToServer;
    }

    public record MessageReceived(ServerConnection connection, Message message) { }
    public record MessageToSend(ServerConnection connection, Message message) { }
}
