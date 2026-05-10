package com.gr15.common.message.sta;

import com.gr15.common.Message;

import java.util.ArrayList;
import java.util.List;

import static com.gr15.common.Constants.TOTAL_CLIENT_ID_BITS;

public class STA_ListConnections {
    public static final int ID = MessageSTA.LIST_CONNECTIONS.getId();

    private final List<ConnectionInfo> connections;

    private STA_ListConnections(List<ConnectionInfo> connections) {
        this.connections = connections;
    }

    public static Message CreateMessage(List<ConnectionInfo> connections) {
        Message message = new Message(ID);
        message.addInt(connections.size(), 16);
        for (ConnectionInfo connection : connections) {
            message.addInt(connection.type().getId(), 2);

            Integer id = connection.id();
            message.addInt(id == null ? 0 : 1, 1);
            if (id != null) {
                message.addInt(id, TOTAL_CLIENT_ID_BITS);
            }

            message.addInt(connection.connected() ? 1 : 0, 1);
            message.addString(connection.hostname());
            message.addInt(connection.port(), 16);
        }
        return message;
    }

    public static STA_ListConnections ReadMessage(Message message) {
        int connectionCount = message.readInt(16);
        List<ConnectionInfo> connections = new ArrayList<>();
        for (int i = 0; i < connectionCount; i++) {
            ConnectionType type = ConnectionType.fromId(message.readInt(2));

            Integer id = null;
            if (message.readInt(1) == 1) {
                id = message.readInt(TOTAL_CLIENT_ID_BITS);
            }

            boolean connected = message.readInt(1) == 1;
            String hostname = message.readString();
            int port = message.readInt(16);
            connections.add(new ConnectionInfo(type, id, hostname, port, connected));
        }

        return new STA_ListConnections(connections);
    }

    public List<ConnectionInfo> getConnections() {
        return new ArrayList<>(connections);
    }

    public enum ConnectionType {
        ADMIN(0),
        CLIENT(1),
        SERVER(2);

        private final int id;

        ConnectionType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static ConnectionType fromId(int id) {
            for (ConnectionType type : values()) {
                if (type.getId() == id) {
                    return type;
                }
            }

            return null;
        }
    }

    public record ConnectionInfo(ConnectionType type, Integer id, String hostname, int port, boolean connected) { }
}
