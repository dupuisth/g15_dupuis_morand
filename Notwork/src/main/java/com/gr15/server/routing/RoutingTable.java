package com.gr15.server.routing;

import com.gr15.common.ClientId;
import com.gr15.common.message.sts.STS_RoutingUpdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.gr15.common.Constants.*;

/**
 * Owns the logical network view used for shortest-path routing.
 *
 * Socket managers decide when updates arrive and where messages are sent; this
 * class keeps that routing decision deterministic and easy to test.
 */
public class RoutingTable {
    private final int localServerId;
    private final Object lock = new Object();
    private final Map<Integer, Set<Integer>> topology = new HashMap<>();
    private final Map<Integer, Set<Integer>> clientsByServer = new HashMap<>();
    private final Map<Integer, Integer> lastSequenceByServer = new HashMap<>();
    private final Map<Integer, RoutingSnapshot> snapshotsByServer = new HashMap<>();
    private int currentLocalSequence = 0;

    public RoutingTable(int localServerId) {
        this.localServerId = localServerId;
        topology.put(localServerId, new HashSet<>());
        clientsByServer.put(localServerId, new HashSet<>());
        lastSequenceByServer.put(localServerId, -1);
        snapshotsByServer.put(localServerId, new RoutingSnapshot(localServerId, -1, 0, 0));
    }

    public RoutingSnapshot updateLocal(Set<Integer> localClientIds, int clientMask, int neighborMask) {
        synchronized (lock) {
            int sequence = currentLocalSequence++;
            if (currentLocalSequence >= (1 << ROUTING_SEQUENCE_BITS)) {
                currentLocalSequence = 0;
            }

            RoutingSnapshot snapshot = new RoutingSnapshot(localServerId, sequence, clientMask, neighborMask);
            lastSequenceByServer.put(localServerId, sequence);
            topology.put(localServerId, serverIdsFromMask(neighborMask));
            clientsByServer.put(localServerId, new HashSet<>(localClientIds));
            snapshotsByServer.put(localServerId, snapshot);
            return snapshot;
        }
    }

    public RoutingChange applyRemoteUpdate(STS_RoutingUpdate routingUpdate) {
        Set<Integer> previousClients;
        Set<Integer> nextClients = routingUpdate.getClientIds();

        synchronized (lock) {
            Integer previousSequence = lastSequenceByServer.get(routingUpdate.getOriginServerId());
            if (previousSequence != null && !isNewRoutingSequence(previousSequence, routingUpdate.getSequence())) {
                return RoutingChange.rejected();
            }

            lastSequenceByServer.put(routingUpdate.getOriginServerId(), routingUpdate.getSequence());
            topology.put(routingUpdate.getOriginServerId(), routingUpdate.getNeighborServerIds());
            snapshotsByServer.put(routingUpdate.getOriginServerId(), new RoutingSnapshot(
                    routingUpdate.getOriginServerId(),
                    routingUpdate.getSequence(),
                    routingUpdate.getClientMask(),
                    routingUpdate.getNeighborMask()
            ));

            previousClients = clientsByServer.get(routingUpdate.getOriginServerId());
            if (previousClients == null) {
                previousClients = Collections.emptySet();
            }
            clientsByServer.put(routingUpdate.getOriginServerId(), new HashSet<>(nextClients));
        }

        return RoutingChange.accepted(previousClients, nextClients);
    }

    public boolean isKnownClient(int clientId) {
        int serverId = ClientId.GetServerId(clientId);
        synchronized (lock) {
            Set<Integer> clients = clientsByServer.get(serverId);
            return clients != null && clients.contains(clientId);
        }
    }

    public Set<Integer> getKnownClientIds() {
        Set<Integer> allClients = new HashSet<>();
        synchronized (lock) {
            for (Set<Integer> clients : clientsByServer.values()) {
                allClients.addAll(clients);
            }
        }
        return allClients;
    }

    public Integer getNextHopServerId(int destinationServerId) {
        if (destinationServerId == localServerId) {
            return localServerId;
        }

        synchronized (lock) {
            Queue<RouteStep> queue = new LinkedList<>();
            Set<Integer> visited = new HashSet<>();

            visited.add(localServerId);
            for (Integer neighborId : getSortedNeighbors(localServerId)) {
                if (visited.add(neighborId)) {
                    queue.add(new RouteStep(neighborId, neighborId));
                }
            }

            while (!queue.isEmpty()) {
                RouteStep current = queue.poll();
                if (current.serverId() == destinationServerId) {
                    return current.firstHopServerId();
                }

                for (Integer neighborId : getSortedNeighbors(current.serverId())) {
                    if (visited.add(neighborId)) {
                        queue.add(new RouteStep(neighborId, current.firstHopServerId()));
                    }
                }
            }
        }

        return null;
    }

    public List<RoutingSnapshot> getKnownSnapshots() {
        synchronized (lock) {
            return new ArrayList<>(snapshotsByServer.values());
        }
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

    private Set<Integer> serverIdsFromMask(int serverMask) {
        Set<Integer> serverIds = new HashSet<>();
        for (int serverId = 0; serverId < MAX_SERVERS; serverId++) {
            if (((serverMask >> serverId) & 1) == 1) {
                serverIds.add(serverId);
            }
        }
        return serverIds;
    }

    public record RoutingChange(boolean accepted, Set<Integer> previousClients, Set<Integer> nextClients) {
        private static RoutingChange accepted(Set<Integer> previousClients, Set<Integer> nextClients) {
            return new RoutingChange(true, previousClients, nextClients);
        }

        private static RoutingChange rejected() {
            return new RoutingChange(false, Collections.emptySet(), Collections.emptySet());
        }
    }

    private record RouteStep(int serverId, int firstHopServerId) {
    }
}
