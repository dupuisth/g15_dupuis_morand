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
import com.gr15.common.message.sts.STS_RoutedError;
import com.gr15.common.message.sts.STS_RoutedMessage;
import com.gr15.common.message.sts.STS_RoutingUpdate;
import com.gr15.server.ServerApp;
import com.gr15.server.connections.ServerConnection;
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

    private final Object routingLock = new Object();
    private final Map<Integer, Set<Integer>> topology = new HashMap<>();
    private final Map<Integer, Set<Integer>> clientsByServer = new HashMap<>();
    private final Map<Integer, Integer> lastRoutingSequenceByServer = new HashMap<>();
    private final Map<Integer, RoutingSnapshot> routingSnapshotsByServer = new HashMap<>();
    private int currentLocalRoutingSequence = 0;

    ServerConnectToNeighborThread connectToNeighborThread;

    public ServerManager(ServerApp server) {
        super(server);
        synchronized (routingLock) {
            topology.put(server.getInitialConfig().getServerId(), new HashSet<>());
            clientsByServer.put(server.getInitialConfig().getServerId(), new HashSet<>());
            lastRoutingSequenceByServer.put(server.getInitialConfig().getServerId(), -1);
            routingSnapshotsByServer.put(server.getInitialConfig().getServerId(), new RoutingSnapshot(server.getInitialConfig().getServerId(), -1, 0, 0));
        }
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

        if (!isKnownClient(destinationClientId)) {
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

    private boolean isKnownClient(int clientId) {
        int serverId = ClientId.GetServerId(clientId);
        synchronized (routingLock) {
            Set<Integer> clients = clientsByServer.get(serverId);
            return clients != null && clients.contains(clientId);
        }
    }

    private ServerConnection getNextHopConnection(int destinationServerId) {
        Integer nextHopServerId = getNextHopServerId(destinationServerId);
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

    private Integer getNextHopServerId(int destinationServerId) {
        int localServerId = server.getInitialConfig().getServerId();
        if (destinationServerId == localServerId) {
            return localServerId;
        }

        synchronized (routingLock) {
            Queue<RouteStep> queue = new LinkedList<>();
            Set<Integer> visited = new HashSet<>();

            visited.add(localServerId);
            for (Integer neighborId : getSortedNeighbors(localServerId)) {
                if (!visited.add(neighborId)) {
                    continue;
                }
                queue.add(new RouteStep(neighborId, neighborId));
            }

            while (!queue.isEmpty()) {
                RouteStep current = queue.poll();
                if (current.serverId() == destinationServerId) {
                    return current.firstHopServerId();
                }

                for (Integer neighborId : getSortedNeighbors(current.serverId())) {
                    if (!visited.add(neighborId)) {
                        continue;
                    }
                    queue.add(new RouteStep(neighborId, current.firstHopServerId()));
                }
            }
        }

        return null;
    }

    private List<Integer> getSortedNeighbors(int serverId) {
        Set<Integer> neighbors = topology.get(serverId);
        if (neighbors == null) {
            return Collections.emptyList();
        }

        ArrayList<Integer> sorted = new ArrayList<>(neighbors);
        Collections.sort(sorted);
        return sorted;
    }

    public void publishLocalRoutingUpdate() {
        int localServerId = server.getInitialConfig().getServerId();
        int sequence;
        int clientMask = server.getClientManager().getLocalClientMask();
        int neighborMask = getConnectedNeighborMask();

        synchronized (routingLock) {
            sequence = currentLocalRoutingSequence++;
            if (currentLocalRoutingSequence >= (1 << ROUTING_SEQUENCE_BITS)) {
                currentLocalRoutingSequence = 0;
            }

            lastRoutingSequenceByServer.put(localServerId, sequence);
            topology.put(localServerId, serverIdsFromMask(neighborMask));
            clientsByServer.put(localServerId, new HashSet<>(server.getClientManager().getLocalClientIds()));
            routingSnapshotsByServer.put(localServerId, new RoutingSnapshot(localServerId, sequence, clientMask, neighborMask));
        }

        sendToAll(STS_RoutingUpdate.CreateMessage(localServerId, sequence, clientMask, neighborMask));
    }

    public Set<Integer> getKnownClientIds() {
        Set<Integer> allClients = new HashSet<>();
        synchronized (routingLock) {
            for (Set<Integer> clients : clientsByServer.values()) {
                allClients.addAll(clients);
            }
        }
        allClients.addAll(server.getClientManager().getLocalClientIds());
        return allClients;
    }

    private boolean applyRoutingUpdate(STS_RoutingUpdate routingUpdate) {
        Set<Integer> previousClients;
        Set<Integer> nextClients = routingUpdate.getClientIds();

        synchronized (routingLock) {
            Integer previousSequence = lastRoutingSequenceByServer.get(routingUpdate.getOriginServerId());
            if (previousSequence != null && !isNewRoutingSequence(previousSequence, routingUpdate.getSequence())) {
                return false;
            }

            lastRoutingSequenceByServer.put(routingUpdate.getOriginServerId(), routingUpdate.getSequence());
            topology.put(routingUpdate.getOriginServerId(), routingUpdate.getNeighborServerIds());
            routingSnapshotsByServer.put(routingUpdate.getOriginServerId(), new RoutingSnapshot(
                    routingUpdate.getOriginServerId(),
                    routingUpdate.getSequence(),
                    routingUpdate.getClientMask(),
                    routingUpdate.getNeighborMask()
            ));
            previousClients = clientsByServer.get(routingUpdate.getOriginServerId());
            if (previousClients == null) {
                previousClients = Collections.emptySet();
            }
            clientsByServer.put(routingUpdate.getOriginServerId(), nextClients);
        }

        notifyLocalClientsOfClientChanges(previousClients, nextClients);
        return true;
    }

    private boolean isNewRoutingSequence(int previousSequence, int candidateSequence) {
        if (previousSequence < 0) {
            return true;
        }

        if (candidateSequence > previousSequence) {
            return true;
        }

        int maxSequence = 1 << ROUTING_SEQUENCE_BITS;
        return previousSequence > maxSequence - 100 && candidateSequence < 100;
    }

    private void sendKnownRoutingTable(ServerConnection serverConnection) {
        ArrayList<RoutingSnapshot> snapshots;
        synchronized (routingLock) {
            snapshots = new ArrayList<>(routingSnapshotsByServer.values());
        }

        for (RoutingSnapshot snapshot : snapshots) {
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

    private Set<Integer> serverIdsFromMask(int serverMask) {
        Set<Integer> serverIds = new HashSet<>();
        for (int serverId = 0; serverId < MAX_SERVERS; serverId++) {
            if (((serverMask >> serverId) & 1) == 1) {
                serverIds.add(serverId);
            }
        }
        return serverIds;
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
    private record RouteStep(int serverId, int firstHopServerId) { }
    private record RoutingSnapshot(int originServerId, int sequence, int clientMask, int neighborMask) { }
}
