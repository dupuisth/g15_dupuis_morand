package com.gr15.common.message.sta;

import com.gr15.common.Message;

import java.util.ArrayList;
import java.util.List;

import static com.gr15.common.Constants.SERVER_ID_BITS;

public class STA_ListNeighbor {
    public static final int ID = MessageSTA.LIST_NEIGHBOR.getId();

    private final List<NeighborInfo> neighbors;

    private STA_ListNeighbor(List<NeighborInfo> neighbors) {
        this.neighbors = neighbors;
    }

    public static Message CreateMessage(List<NeighborInfo> neighbors) {
        Message message = new Message(ID);
        message.addInt(neighbors.size(), 16);
        for (NeighborInfo neighbor : neighbors) {
            Integer serverId = neighbor.serverId();
            message.addInt(serverId == null ? 0 : 1, 1);
            if (serverId != null) {
                message.addInt(serverId, SERVER_ID_BITS);
            }
            message.addString(neighbor.serverHostname());
            message.addInt(neighbor.serverPort(), 16);
        }
        return message;
    }

    public static STA_ListNeighbor ReadMessage(Message message) {
        int neighborCount = message.readInt(16);
        List<NeighborInfo> neighbors = new ArrayList<>();
        for (int i = 0; i < neighborCount; i++) {
            Integer serverId = null;
            if (message.readInt(1) == 1) {
                serverId = message.readInt(SERVER_ID_BITS);
            }
            String hostname = message.readString();
            int port = message.readInt(16);
            neighbors.add(new NeighborInfo(serverId, hostname, port));
        }

        return new STA_ListNeighbor(neighbors);
    }

    public List<NeighborInfo> getNeighbors() {
        return new ArrayList<>(neighbors);
    }

    public record NeighborInfo(Integer serverId, String serverHostname, int serverPort) { }
}
