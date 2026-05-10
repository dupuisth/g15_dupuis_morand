package com.gr15.common.message.sts;

import com.gr15.common.ClientId;
import com.gr15.common.Message;

import java.util.HashSet;
import java.util.Set;

import static com.gr15.common.Constants.*;

public class STS_RoutingUpdate {
    public static final int ID = MessageSTS.ROUTING_UPDATE.getId();

    private final int originServerId;
    private final int sequence;
    private final int clientMask;
    private final int neighborMask;

    private STS_RoutingUpdate(int originServerId, int sequence, int clientMask, int neighborMask) {
        this.originServerId = originServerId;
        this.sequence = sequence;
        this.clientMask = clientMask;
        this.neighborMask = neighborMask;
    }

    public static Message CreateMessage(int originServerId, int sequence, int clientMask, int neighborMask) {
        Message message = new Message(ID);
        message.addInt(originServerId, SERVER_ID_BITS);
        message.addInt(sequence, ROUTING_SEQUENCE_BITS);
        message.addInt(clientMask, MAX_CLIENTS);
        message.addInt(neighborMask, MAX_SERVERS);
        return message;
    }

    public static STS_RoutingUpdate ReadMessage(Message message) {
        int originServerId = message.readInt(SERVER_ID_BITS);
        int sequence = message.readInt(ROUTING_SEQUENCE_BITS);
        int clientMask = message.readInt(MAX_CLIENTS);
        int neighborMask = message.readInt(MAX_SERVERS);
        return new STS_RoutingUpdate(originServerId, sequence, clientMask, neighborMask);
    }

    public Set<Integer> getClientIds() {
        Set<Integer> clientIds = new HashSet<>();
        for (int localId = 0; localId < MAX_CLIENTS; localId++) {
            if (((clientMask >> localId) & 1) == 1) {
                clientIds.add(ClientId.Create(originServerId, localId));
            }
        }
        return clientIds;
    }

    public Set<Integer> getNeighborServerIds() {
        Set<Integer> serverIds = new HashSet<>();
        for (int serverId = 0; serverId < MAX_SERVERS; serverId++) {
            if (((neighborMask >> serverId) & 1) == 1) {
                serverIds.add(serverId);
            }
        }
        return serverIds;
    }

    public int getOriginServerId() {
        return originServerId;
    }

    public int getSequence() {
        return sequence;
    }

    public int getClientMask() {
        return clientMask;
    }

    public int getNeighborMask() {
        return neighborMask;
    }

    @Override
    public String toString() {
        return "STS_RoutingUpdate{" +
                "originServerId=" + originServerId +
                ", sequence=" + sequence +
                ", clientMask=" + clientMask +
                ", neighborMask=" + neighborMask +
                '}';
    }
}
