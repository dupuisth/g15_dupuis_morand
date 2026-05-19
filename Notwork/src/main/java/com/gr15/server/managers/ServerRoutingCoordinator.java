package com.gr15.server.managers;

import com.gr15.common.ClientId;
import com.gr15.common.message.stc.STC_MessageNewClient;
import com.gr15.common.message.stc.STC_MessageRemoveClient;
import com.gr15.common.message.sts.STS_RoutedError;
import com.gr15.common.message.sts.STS_RoutedMessage;
import com.gr15.common.message.sts.STS_RoutingUpdate;
import com.gr15.server.ServerApp;
import com.gr15.server.connections.ServerConnection;
import com.gr15.server.routing.RoutingSnapshot;
import com.gr15.server.routing.RoutingTable;
import com.gr15.server.wrappers.ServerWrapper;
import com.gr15.utils.Logger;

import java.util.List;
import java.util.Set;

/**
 * Owns server-to-server routing behavior while ServerManager owns sockets,
 * queues, and connection lifecycle.
 */
class ServerRoutingCoordinator {
    private final ServerApp server;
    private final ServerManager serverManager;
    private final RoutingTable routingTable;

    ServerRoutingCoordinator(ServerApp server, ServerManager serverManager) {
        this.server = server;
        this.serverManager = serverManager;
        this.routingTable = new RoutingTable(server.getInitialConfig().getServerId());
    }

    void handleRoutedMessage(ServerConnection fromServer, STS_RoutedMessage routedMessage) {
        Logger.info("Received " + routedMessage);

        int destinationServerId = ClientId.GetServerId(routedMessage.getDestinationClientId());
        if (destinationServerId == server.getInitialConfig().getServerId()) {
            Logger.info("[ROUTING] Delivering routed message locally from="
                    + ClientId.toString(routedMessage.getFromClientId())
                    + " to=" + ClientId.toString(routedMessage.getDestinationClientId()));
            boolean delivered = server.getClientManager().sendClientMessage(
                    routedMessage.getFromClientId(),
                    routedMessage.getDestinationClientId(),
                    routedMessage.getContent()
            );

            if (!delivered) {
                Logger.warn("[ROUTING] Local destination client is disconnected, sending error to="
                        + ClientId.toString(routedMessage.getFromClientId()));
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
            Logger.warn("[ROUTING] Cannot forward message from="
                    + ClientId.toString(routedMessage.getFromClientId())
                    + " to=" + ClientId.toString(routedMessage.getDestinationClientId())
                    + " destinationServer=" + destinationServerId
                    + " nextHop=" + nextHop);
            routeClientError(
                    routedMessage.getFromClientId(),
                    routedMessage.getDestinationClientId(),
                    "Destination server is unreachable"
            );
            return;
        }

        Logger.info("[ROUTING] Forwarding routed message from="
                + ClientId.toString(routedMessage.getFromClientId())
                + " to=" + ClientId.toString(routedMessage.getDestinationClientId())
                + " destinationServer=" + destinationServerId
                + " nextHopServer=" + nextHop.getServerId());
        serverManager.send(nextHop, STS_RoutedMessage.CreateMessage(
                routedMessage.getFromClientId(),
                routedMessage.getDestinationClientId(),
                routedMessage.getContent()
        ));
    }

    void handleRoutedError(ServerConnection fromServer, STS_RoutedError routedError) {
        Logger.info("Received " + routedError);

        int recipientServerId = ClientId.GetServerId(routedError.getRecipientClientId());
        if (recipientServerId == server.getInitialConfig().getServerId()) {
            Logger.info("[ROUTING] Delivering routed error locally recipient="
                    + ClientId.toString(routedError.getRecipientClientId())
                    + " destination=" + ClientId.toString(routedError.getDestinationClientId()));
            server.getClientManager().sendError(
                    routedError.getRecipientClientId(),
                    routedError.getDestinationClientId(),
                    routedError.getErrorMessage()
            );
            return;
        }

        ServerConnection nextHop = getNextHopConnection(recipientServerId);
        if (nextHop == null || nextHop == fromServer) {
            Logger.warn("[ROUTING] Cannot route error back to "
                    + ClientId.toString(routedError.getRecipientClientId())
                    + " recipientServer=" + recipientServerId
                    + " nextHop=" + nextHop);
            return;
        }

        Logger.info("[ROUTING] Forwarding routed error recipient="
                + ClientId.toString(routedError.getRecipientClientId())
                + " destination=" + ClientId.toString(routedError.getDestinationClientId())
                + " nextHopServer=" + nextHop.getServerId());
        serverManager.send(nextHop, STS_RoutedError.CreateMessage(
                routedError.getRecipientClientId(),
                routedError.getDestinationClientId(),
                routedError.getErrorMessage()
        ));
    }

    void handleRoutingUpdate(ServerConnection fromServer, STS_RoutingUpdate routingUpdate) {
        Logger.info("Received " + routingUpdate);

        boolean accepted = applyRoutingUpdate(routingUpdate);
        if (!accepted) {
            Logger.info("[ROUTING] Ignored stale routing update originServer="
                    + routingUpdate.getOriginServerId()
                    + " sequence=" + routingUpdate.getSequence());
            return;
        }

        Logger.info("[ROUTING] Accepted routing update originServer="
                + routingUpdate.getOriginServerId()
                + " sequence=" + routingUpdate.getSequence()
                + " clientMask=" + routingUpdate.getClientMask()
                + " neighborMask=" + routingUpdate.getNeighborMask()
                + ", forwarding to other neighbors");
        serverManager.sendToAll(STS_RoutingUpdate.CreateMessage(
                routingUpdate.getOriginServerId(),
                routingUpdate.getSequence(),
                routingUpdate.getClientMask(),
                routingUpdate.getNeighborMask()
        ), fromServer);
    }

    boolean routeClientMessage(int fromClientId, int destinationClientId, String content) {
        int destinationServerId = ClientId.GetServerId(destinationClientId);

        if (destinationServerId == server.getInitialConfig().getServerId()) {
            Logger.info("[ROUTING] Client message stays local from="
                    + ClientId.toString(fromClientId)
                    + " to=" + ClientId.toString(destinationClientId));
            return server.getClientManager().sendClientMessage(fromClientId, destinationClientId, content);
        }

        if (!routingTable.isKnownClient(destinationClientId)) {
            Logger.warn("[ROUTING] Unknown destination client="
                    + ClientId.toString(destinationClientId)
                    + " for source=" + ClientId.toString(fromClientId));
            return false;
        }

        ServerConnection nextHop = getNextHopConnection(destinationServerId);
        if (nextHop == null) {
            Logger.warn("[ROUTING] No next hop for destinationServer=" + destinationServerId
                    + " destinationClient=" + ClientId.toString(destinationClientId));
            return false;
        }

        Logger.info("[ROUTING] Routing client message from="
                + ClientId.toString(fromClientId)
                + " to=" + ClientId.toString(destinationClientId)
                + " destinationServer=" + destinationServerId
                + " nextHopServer=" + nextHop.getServerId());
        serverManager.send(nextHop, STS_RoutedMessage.CreateMessage(fromClientId, destinationClientId, content));
        return true;
    }

    boolean routeClientError(int recipientClientId, int destinationClientId, String errorMessage) {
        int recipientServerId = ClientId.GetServerId(recipientClientId);

        if (recipientServerId == server.getInitialConfig().getServerId()) {
            Logger.info("[ROUTING] Client error stays local recipient="
                    + ClientId.toString(recipientClientId)
                    + " destination=" + ClientId.toString(destinationClientId));
            server.getClientManager().sendError(recipientClientId, destinationClientId, errorMessage);
            return true;
        }

        ServerConnection nextHop = getNextHopConnection(recipientServerId);
        if (nextHop == null) {
            Logger.warn("[ROUTING] No next hop for routed error recipient="
                    + ClientId.toString(recipientClientId)
                    + " recipientServer=" + recipientServerId);
            return false;
        }

        Logger.info("[ROUTING] Routing client error recipient="
                + ClientId.toString(recipientClientId)
                + " destination=" + ClientId.toString(destinationClientId)
                + " nextHopServer=" + nextHop.getServerId());
        serverManager.send(nextHop, STS_RoutedError.CreateMessage(recipientClientId, destinationClientId, errorMessage));
        return true;
    }

    void publishLocalRoutingUpdate() {
        int localServerId = server.getInitialConfig().getServerId();
        int clientMask = server.getClientManager().getLocalClientMask();
        int neighborMask = getConnectedNeighborMask();
        RoutingSnapshot snapshot = routingTable.updateLocal(server.getClientManager().getLocalClientIds(), clientMask, neighborMask);
        Logger.info("[ROUTING] Publishing local routing update originServer="
                + localServerId
                + " sequence=" + snapshot.sequence()
                + " clientMask=" + clientMask
                + " neighborMask=" + neighborMask);
        serverManager.sendToAll(STS_RoutingUpdate.CreateMessage(localServerId, snapshot.sequence(), clientMask, neighborMask));
    }

    Set<Integer> getKnownClientIds() {
        Set<Integer> allClients = routingTable.getKnownClientIds();
        allClients.addAll(server.getClientManager().getLocalClientIds());
        return allClients;
    }

    List<RoutingSnapshot> getKnownSnapshots() {
        return routingTable.getKnownSnapshots();
    }

    void sendKnownRoutingTable(ServerConnection serverConnection) {
        for (RoutingSnapshot snapshot : routingTable.getKnownSnapshots()) {
            if (snapshot.sequence() < 0) {
                continue;
            }
            Logger.info("[ROUTING] Sending known routing snapshot to server="
                    + serverConnection.getServerId()
                    + " originServer=" + snapshot.originServerId()
                    + " sequence=" + snapshot.sequence()
                    + " clientMask=" + snapshot.clientMask()
                    + " neighborMask=" + snapshot.neighborMask());
            serverManager.send(serverConnection, STS_RoutingUpdate.CreateMessage(
                    snapshot.originServerId(),
                    snapshot.sequence(),
                    snapshot.clientMask(),
                    snapshot.neighborMask()
            ));
        }
    }

    private boolean applyRoutingUpdate(STS_RoutingUpdate routingUpdate) {
        RoutingTable.RoutingChange change = routingTable.applyRemoteUpdate(routingUpdate);
        if (!change.accepted()) {
            return false;
        }

        notifyLocalClientsOfClientChanges(change.previousClients(), change.nextClients());
        return true;
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

    private ServerConnection getNextHopConnection(int destinationServerId) {
        Integer nextHopServerId = routingTable.getNextHopServerId(destinationServerId);
        if (nextHopServerId == null) {
            return null;
        }

        synchronized (serverManager.getConnectionsLock()) {
            ServerWrapper wrapper = serverManager.getConnections()[nextHopServerId];
            if (wrapper == null) {
                return null;
            }
            return wrapper.getConnection();
        }
    }

    private int getConnectedNeighborMask() {
        int neighborMask = 0;
        synchronized (serverManager.getConnectionsLock()) {
            for (ServerWrapper wrapper : serverManager.getConnections()) {
                if (wrapper == null || wrapper.getConnection() == null || wrapper.getConnection().getServerId() == null) {
                    continue;
                }
                neighborMask |= 1 << wrapper.getConnection().getServerId();
            }
        }
        return neighborMask;
    }
}
