package com.gr15.server.managers;

import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.sta.STA_ListConnections;
import com.gr15.common.message.stc.STC_Message;
import com.gr15.common.message.sts.MessageSTS;
import com.gr15.common.message.sts.STS_BroadcastChat;
import com.gr15.common.message.sts.STS_Identify;
import com.gr15.common.message.sts.STS_Ping;
import com.gr15.common.message.sts.STS_Pong;
import com.gr15.common.message.sts.STS_RoutedError;
import com.gr15.common.message.sts.STS_RoutedMessage;
import com.gr15.common.message.sts.STS_RoutingUpdate;
import com.gr15.server.ServerApp;
import com.gr15.server.connections.ServerConnection;
import com.gr15.server.routing.RoutingSnapshot;
import com.gr15.server.wrappers.ServerWrapper;
import static com.gr15.common.Constants.*;

import com.gr15.server.handlers.ServerHandler;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/**
 * Manages neighbor server connections and server-to-server messages.
 *
 * New server sockets start in a pending state until an identity message confirms
 * the remote server id. Routing decisions are delegated to
 * ServerRoutingCoordinator, while this class keeps ownership of sockets, queues
 * and connection cleanup.
 */
public class ServerManager extends Manager<ServerConnection, ServerWrapper> {
    /** Array of all the servers connected (index => serverId) */
    private final ServerWrapper[] connectionsToServer = new ServerWrapper[MAX_SERVERS];
    private final ArrayList<ServerWrapper> pendingAuthentification = new ArrayList<>();

    private final Queue<ServerConnection> connectionsToRemoveQueue = new LinkedList<>();
    private final Queue<MessageReceived> messageReceivedQueue = new LinkedList<>();
    private final Queue<MessageToSend> messageToSendQueue = new LinkedList<>();

    private final ServerBroadcastTracker broadcastTracker;
    private final ServerRoutingCoordinator routingCoordinator;

    ServerConnectToNeighborThread connectToNeighborThread;

    public ServerManager(ServerApp server) {
        super(server);
        broadcastTracker = new ServerBroadcastTracker();
        routingCoordinator = new ServerRoutingCoordinator(server, this);
    }

    @Override
    public void start() throws RuntimeException {
        super.start();

        connectToNeighborThread = new ServerConnectToNeighborThread(this);
        connectToNeighborThread.start();
        publishLocalRoutingUpdate();
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

        // Force stop directly
        synchronized (getConnectionsLock()) {
            for (int i = connectionsToServer.length - 1; i >= 0 ; i--) {
                if (connectionsToServer[i] != null) {
                    stopConnection(connectionsToServer[i].getConnection());
                }
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
        publishLocalRoutingUpdate();
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
            case ROUTED_MESSAGE -> {
                STS_RoutedMessage parsed = STS_RoutedMessage.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
            case ROUTING_UPDATE -> {
                STS_RoutingUpdate parsed = STS_RoutingUpdate.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
            case ROUTED_ERROR -> {
                STS_RoutedError parsed = STS_RoutedError.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
            case PING -> {
                STS_Ping parsed = STS_Ping.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
            case PONG -> {
                STS_Pong parsed = STS_Pong.ReadMessage(message);
                handleMessage(fromServer, parsed);
            }
        }
    }

    private void handleMessage(ServerConnection fromServer, STS_Identify identify) {
        Logger.info("Received identify " + identify);

        if (identify.getFromServerId() < 0 || identify.getFromServerId() >= MAX_SERVERS) {
            Logger.warn("Received invalid server identity: " + identify.getFromServerId());
            stopConnection(fromServer);
            return;
        }

        if (identify.getFromServerId() == server.getInitialConfig().getServerId()) {
            Logger.warn("Rejected self server connection");
            stopConnection(fromServer);
            return;
        }

        ServerWrapper existingWrapper;
        synchronized (getConnectionsLock()) {
            existingWrapper = connectionsToServer[identify.getFromServerId()];
        }

        if (existingWrapper != null && existingWrapper.getConnection() != fromServer) {
            Logger.warn("Rejected duplicate connection for serverId=" + identify.getFromServerId());
            stopConnection(fromServer);
            return;
        }

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
            publishLocalRoutingUpdate();
        } else {
            Logger.warn("Received an identity message but the server is already registered: " + identify.getFromServerId());
        }

        if (identify.getRebounds() >= 1) {
            return;
        }

        // Create the response
        Message response = STS_Identify.CreateMessage(server.getInitialConfig().getServerId(), identify.getRebounds() + 1);
        send(fromServer, response);
        publishLocalRoutingUpdate();
        sendKnownRoutingTable(fromServer);
    }

    private void handleMessage(ServerConnection fromServer, STS_RoutedMessage routedMessage) {
        routingCoordinator.handleRoutedMessage(fromServer, routedMessage);
    }

    private void handleMessage(ServerConnection fromServer, STS_RoutedError routedError) {
        routingCoordinator.handleRoutedError(fromServer, routedError);
    }

    private void handleMessage(ServerConnection fromServer, STS_Ping ping) {
        Logger.debug("Received " + ping + " from " + fromServer);
        send(fromServer, STS_Pong.CreateMessage());
    }

    private void handleMessage(ServerConnection fromServer, STS_Pong pong) {
        Logger.debug("Received " + pong + " from " + fromServer);
    }

    private void handleMessage(ServerConnection fromServer, STS_RoutingUpdate routingUpdate) {
        routingCoordinator.handleRoutingUpdate(fromServer, routingUpdate);
    }

    public boolean routeClientMessage(int fromClientId, int destinationClientId, String content) {
        return routingCoordinator.routeClientMessage(fromClientId, destinationClientId, content);
    }

    public boolean routeClientError(int recipientClientId, int destinationClientId, String errorMessage) {
        return routingCoordinator.routeClientError(recipientClientId, destinationClientId, errorMessage);
    }

    public void publishLocalRoutingUpdate() {
        routingCoordinator.publishLocalRoutingUpdate();
    }

    public Set<Integer> getKnownClientIds() {
        return routingCoordinator.getKnownClientIds();
    }

    public List<RoutingSnapshot> getKnownRoutingSnapshots() {
        return routingCoordinator.getKnownSnapshots();
    }

    private void sendKnownRoutingTable(ServerConnection serverConnection) {
        routingCoordinator.sendKnownRoutingTable(serverConnection);
    }

    private void handleMessage(ServerConnection fromServer, STS_BroadcastChat broadcastChat) {
        Logger.info("Received " + broadcastChat);


        if (!broadcastTracker.shouldHandleBroadcast(broadcastChat.getBroadcastData(), server.getInitialConfig().getServerId())) {
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
     * Create the next broadcast id for a locally originated broadcast.
     */
    public int getNextBroadcastId() {
        return broadcastTracker.getNextBroadcastId(server.getInitialConfig().getServerId());
    }

    public void reset() {
        synchronized (getConnectionsLock()) {
            synchronized (connectionsToRemoveQueue) {
                for (int i = 0; i < connectionsToServer.length; i++) {
                    if (connectionsToServer[i] != null) {
                        connectionsToRemoveQueue.add(connectionsToServer[i].getConnection());
                    }
                }
            }
        }
    }

    @Override
    public int getPort() {
        return server.getInitialConfig().getServerSocketPort();
    }

    @Override
    public Object getConnectionsLock() {
        return connectionsToServer;
    }

    @Override
    public ServerWrapper[] getConnections() {
        return connectionsToServer;
    }

    public ArrayList<ServerWrapper> getPendingAuthentificationConnections() {
        return pendingAuthentification;
    }

    /**
     * Return all current connections (pending authentification and fully initialized)
     */
    public ArrayList<ServerConnection> getAllConnections() {
        ArrayList<ServerConnection> allConnections = new ArrayList<>();

        synchronized (getConnectionsLock()) {
            for (ServerWrapper wrapper : connectionsToServer) {
                if (wrapper == null || wrapper.getConnection() == null) continue;
                allConnections.add(wrapper.getConnection());
            }
        }

        synchronized (pendingAuthentification) {
            for (ServerWrapper wrapper : pendingAuthentification) {
                allConnections.add(wrapper.getConnection());
            }
        }

        return allConnections;
    }

    public List<STA_ListConnections.ConnectionInfo> getConnectionInfos() {
        List<STA_ListConnections.ConnectionInfo> connections = new ArrayList<>();

        synchronized (getConnectionsLock()) {
            for (ServerWrapper wrapper : connectionsToServer) {
                if (wrapper == null || wrapper.getConnection() == null) {
                    continue;
                }

                ServerConnection connection = wrapper.getConnection();
                connections.add(new STA_ListConnections.ConnectionInfo(
                        STA_ListConnections.ConnectionType.SERVER,
                        connection.getServerId(),
                        connection.getHostname(),
                        connection.getPort(),
                        connection.isConnected()
                ));
            }
        }

        synchronized (pendingAuthentification) {
            for (ServerWrapper wrapper : pendingAuthentification) {
                if (wrapper == null || wrapper.getConnection() == null) {
                    continue;
                }

                ServerConnection connection = wrapper.getConnection();
                connections.add(new STA_ListConnections.ConnectionInfo(
                        STA_ListConnections.ConnectionType.SERVER,
                        connection.getServerId(),
                        connection.getHostname(),
                        connection.getPort(),
                        connection.isConnected()
                ));
            }
        }

        return connections;
    }

    public record MessageReceived(ServerConnection connection, Message message) { }
    public record MessageToSend(ServerConnection connection, Message message) { }
}
