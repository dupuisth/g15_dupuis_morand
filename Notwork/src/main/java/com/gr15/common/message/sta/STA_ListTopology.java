package com.gr15.common.message.sta;

import com.gr15.common.Message;

import java.util.ArrayList;
import java.util.List;

import static com.gr15.common.Constants.MAX_CLIENTS;
import static com.gr15.common.Constants.MAX_SERVERS;
import static com.gr15.common.Constants.ROUTING_SEQUENCE_BITS;
import static com.gr15.common.Constants.SERVER_ID_BITS;

public class STA_ListTopology {
    public static final int ID = MessageSTA.LIST_TOPOLOGY.getId();

    private final List<ServerTopologyInfo> servers;

    private STA_ListTopology(List<ServerTopologyInfo> servers) {
        this.servers = servers;
    }

    public static Message CreateMessage(List<ServerTopologyInfo> servers) {
        Message message = new Message(ID);
        message.addInt(servers.size(), 16);
        for (ServerTopologyInfo server : servers) {
            message.addInt(server.serverId(), SERVER_ID_BITS);
            message.addInt(server.sequence(), ROUTING_SEQUENCE_BITS);
            message.addInt(server.clientMask(), MAX_CLIENTS);
            message.addInt(server.neighborMask(), MAX_SERVERS);
        }
        return message;
    }

    public static STA_ListTopology ReadMessage(Message message) {
        int serverCount = message.readInt(16);
        List<ServerTopologyInfo> servers = new ArrayList<>();
        for (int i = 0; i < serverCount; i++) {
            int serverId = message.readInt(SERVER_ID_BITS);
            int sequence = message.readInt(ROUTING_SEQUENCE_BITS);
            int clientMask = message.readInt(MAX_CLIENTS);
            int neighborMask = message.readInt(MAX_SERVERS);
            servers.add(new ServerTopologyInfo(serverId, sequence, clientMask, neighborMask));
        }

        return new STA_ListTopology(servers);
    }

    public List<ServerTopologyInfo> getServers() {
        return new ArrayList<>(servers);
    }

    public record ServerTopologyInfo(int serverId, int sequence, int clientMask, int neighborMask) { }
}
