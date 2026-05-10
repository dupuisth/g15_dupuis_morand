package com.gr15.server.managers;

import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.BroadcastId;
import com.gr15.common.ClientId;
import com.gr15.common.message.sta.STA_ListConnections;
import com.gr15.common.message.stc.STC_Message;
import com.gr15.common.message.stc.STC_MessageNewClient;
import com.gr15.common.message.stc.STC_MessageRemoveClient;
import com.gr15.common.message.sts.BroadcastData;
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
import com.gr15.server.routing.RoutingTable;
import com.gr15.server.wrappers.ServerWrapper;
import static com.gr15.common.Constants.*;

import com.gr15.server.handlers.ServerHandler;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

public class ServerManager extends Manager<ServerConnection, ServerWrapper> {
    /** Array of all the servers connected (index => serverId) */
    private final ServerWrapper[] connectionsToServer = new ServerWrapper[MAX_SERVERS];
    private final ArrayList<ServerWrapper> pendingAuthentification = new ArrayList<>();

    private final Queue<ServerConnection> connectionsToRemoveQueue = new LinkedList<>();
    private final Queue<MessageReceived> messageReceivedQueue = new LinkedList<>();
    private final Queue<MessageToSend> messageToSendQueue = new LinkedList<>();

    private final HashMap<Integer, LocalDateTime> broadcastMap = new HashMap<>();
    private int currentLocalBroadcastId = 0;
    private final Object currentLocalBroadcastIdLock = new Object();

    private final RoutingTable routingTable;

    ServerConnectToNeighborThread connectToNeighborThread;

    public ServerManager(ServerApp server) {
        super(server);
        routingTable = new RoutingTable(server.getInitialConfig().getServerId());
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
        Logger.info("Received " + routedMessage);

        int destinationServerId = ClientId.GetServerId(routedMessage.getDestinationClientId());
        if (destinationServerId == server.getInitialConfig().getServerId()) {
            boolean delivered = server.getClientManager().sendClientMessage(
                    routedMessage.getFromClientId(),
                    routedMessage.getDestinationClientId(),
                    routedMessage.getContent()
            );

            if (!delivered) {
                routeClientError(
                        routedMessage.getFromClientId(),
                        routedMessage.getDestinationClientId(),
                        "Destination client is no longer connected"
                );
            }
            return;
        }

        ServerConnection nextHop = getNextHopConnection(destinationServerId);
        if (nextHop == null || nextHop == fromServer) {
            routeClientError(
                    routedMessage.getFromClientId(),
                    routedMessage.getDestinationClientId(),
                    "Destination server is unreachable"
            );
            return;
        }

        send(nextHop, STS_RoutedMessage.CreateMessage(
                routedMessage.getFromClientId(),
                routedMessage.getDestinationClientId(),
                routedMessage.getContent()
        ));
    }

    private void handleMessage(ServerConnection fromServer, STS_RoutedError routedError) {
        Logger.info("Received " + routedError);

        int recipientServerId = ClientId.GetServerId(routedError.getRecipientClientId());
        if (recipientServerId == server.getInitialConfig().getServerId()) {
            server.getClientManager().sendError(
                    routedError.getRecipientClientId(),
                    routedError.getDestinationClientId(),
                    routedError.getErrorMessage()
            );
            return;
        }

        ServerConnection nextHop = getNextHopConnection(recipientServerId);
        if (nextHop == null || nextHop == fromServer) {
            Logger.warn("Cannot route error back to " + ClientId.toString(routedError.getRecipientClientId()));
            return;
        }

        send(nextHop, STS_RoutedError.CreateMessage(
                routedError.getRecipientClientId(),
                routedError.getDestinationClientId(),
                routedError.getErrorMessage()
        ));
    }

    private void handleMessage(ServerConnection fromServer, STS_Ping ping) {
        Logger.debug("Received " + ping + " from " + fromServer);
        send(fromServer, STS_Pong.CreateMessage());
    }

    private void handleMessage(ServerConnection fromServer, STS_Pong pong) {
        Logger.debug("Received " + pong + " from " + fromServer);
    }

    private void handleMessage(ServerConnection fromServer, STS_RoutingUpdate routingUpdate) {
        Logger.info("Received " + routingUpdate);

        boolean accepted = applyRoutingUpdate(routingUpdate);
        if (!accepted) {
            return;
        }

        sendToAll(STS_RoutingUpdate.CreateMessage(
                routingUpdate.getOriginServerId(),
                routingUpdate.getSequence(),
                routingUpdate.getClientMask(),
                routingUpdate.getNeighborMask()
        ), fromServer);
    }

    public boolean routeClientMessage(int fromClientId, int destinationClientId, String content) {
        int destinationServerId = ClientId.GetServerId(destinationClientId);

        if (destinationServerId == server.getInitialConfig().getServerId()) {
            return server.getClientManager().sendClientMessage(fromClientId, destinationClientId, content);
        }

        if (!routingTable.isKnownClient(destinationClientId)) {
            return false;
        }

        ServerConnection nextHop = getNextHopConnection(destinationServerId);
        if (nextHop == null) {
            return false;
        }

        send(nextHop, STS_RoutedMessage.CreateMessage(fromClientId, destinationClientId, content));
        return true;
    }

    public boolean routeClientError(int recipientClientId, int destinationClientId, String errorMessage) {
        int recipientServerId = ClientId.GetServerId(recipientClientId);

        if (recipientServerId == server.getInitialConfig().getServerId()) {
            server.getClientManager().sendError(recipientClientId, destinationClientId, errorMessage);
            return true;
        }

        ServerConnection nextHop = getNextHopConnection(recipientServerId);
        if (nextHop == null) {
            return false;
        }

        send(nextHop, STS_RoutedError.CreateMessage(recipientClientId, destinationClientId, errorMessage));
        return true;
    }

    private ServerConnection getNextHopConnection(int destinationServerId) {
        Integer nextHopServerId = routingTable.getNextHopServerId(destinationServerId);
        if (nextHopServerId == null) {
            return null;
        }

        synchronized (getConnectionsLock()) {
            ServerWrapper wrapper = connectionsToServer[nextHopServerId];
            if (wrapper == null) {
                return null;
            }
            return wrapper.getConnection();
        }
    }

    public void publishLocalRoutingUpdate() {
        int localServerId = server.getInitialConfig().getServerId();
        int clientMask = server.getClientManager().getLocalClientMask();
        int neighborMask = getConnectedNeighborMask();
        RoutingSnapshot snapshot = routingTable.updateLocal(server.getClientManager().getLocalClientIds(), clientMask, neighborMask);
        sendToAll(STS_RoutingUpdate.CreateMessage(localServerId, snapshot.sequence(), clientMask, neighborMask));
    }

    public Set<Integer> getKnownClientIds() {
        Set<Integer> allClients = routingTable.getKnownClientIds();
        allClients.addAll(server.getClientManager().getLocalClientIds());
        return allClients;
    }

    private boolean applyRoutingUpdate(STS_RoutingUpdate routingUpdate) {
        RoutingTable.RoutingChange change = routingTable.applyRemoteUpdate(routingUpdate);
        if (!change.accepted()) {
            return false;
        }

        notifyLocalClientsOfClientChanges(change.previousClients(), change.nextClients());
        return true;
    }

    private void sendKnownRoutingTable(ServerConnection serverConnection) {
        for (RoutingSnapshot snapshot : routingTable.getKnownSnapshots()) {
            if (snapshot.sequence() < 0) {
                continue;
            }
            send(serverConnection, STS_RoutingUpdate.CreateMessage(
                    snapshot.originServerId(),
                    snapshot.sequence(),
                    snapshot.clientMask(),
                    snapshot.neighborMask()
            ));
        }
    }

    private void notifyLocalClientsOfClientChanges(Set<Integer> previousClients, Set<Integer> nextClients) {
        for (Integer clientId : nextClients) {
            if (!previousClients.contains(clientId)) {
                server.getClientManager().sendToAll(STC_MessageNewClient.CreateMessage(clientId));
            }
        }

        for (Integer clientId : previousClients) {
            if (!nextClients.contains(clientId)) {
                server.getClientManager().sendToAll(STC_MessageRemoveClient.CreateMessage(clientId));
            }
        }
    }

    private int getConnectedNeighborMask() {
        int neighborMask = 0;
        synchronized (getConnectionsLock()) {
            for (ServerWrapper wrapper : connectionsToServer) {
                if (wrapper == null || wrapper.getConnection() == null || wrapper.getConnection().getServerId() == null) {
                    continue;
                }
                neighborMask |= 1 << wrapper.getConnection().getServerId();
            }
        }
        return neighborMask;
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
        LocalDateTime currentDateTime = LocalDateTime.now();

        // Check if I've already handled this message (or if it is mine)
        int broadcastId = broadcastData.getBroadcastId();
        if (BroadcastId.GetServerId(broadcastId) == server.getInitialConfig().getServerId()) {
            // It's my message, do nothing
            Logger.debug("Received my own broadcast, doing nothing");
            return false;
        }
        else { // Check if the broadcastId was already handled (or if it expired)
            synchronized (broadcastMap) {
                if (broadcastMap.containsKey(broadcastId)) {
                    // This id is already in the map, check if it was long ago
                    LocalDateTime dateTime = broadcastMap.get(broadcastId);
                    Duration gap = Duration.between(dateTime, currentDateTime);

                    // Check if the gap is enough to forget it (if not, don't handle)
                    if (gap.getSeconds() < BROADCAST_ID_FORGER_AFTER_SECONDS) {
                        return false;
                    }
                }

                // If we are here, then we can process it
                broadcastMap.put(broadcastId, currentDateTime);
            }
        }
        return true;
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
        int localId;
        synchronized (currentLocalBroadcastIdLock) {
            localId = currentLocalBroadcastId++;
            if (currentLocalBroadcastId > (1 << BROADCAST_ID_LOCAL_BITS) - 1) {
                currentLocalBroadcastId = 0;
            }
        }


        int broadcastId = BroadcastId.Create(server.getInitialConfig().getServerId(), localId);
        return broadcastId;
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
