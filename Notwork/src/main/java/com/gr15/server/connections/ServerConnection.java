package com.gr15.server.connections;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;
import com.gr15.common.message.universal.UniversalMessageAdapter;

import java.io.IOException;
import java.net.Socket;

/**
 * Represents a server-to-server connection.
 */
public class ServerConnection extends RemoteConnection {
    private volatile Integer serverId;
    private final UniversalMessageAdapter universalMessageAdapter = new UniversalMessageAdapter();

    public ServerConnection(Socket socket, Integer serverId) throws IOException {
        super(socket);
        this.serverId = serverId;
    }

    public ServerConnection(String hostname, int port, boolean connectInstantly, Integer serverId) throws IOException {
        super(hostname, port, connectInstantly);
        this.serverId = serverId;
    }

    public Integer getServerId() {
        return serverId;
    }

    public void setServerId(Integer serverId) {
        this.serverId = serverId;
    }

    @Override
    public Message read() throws IOException {
        return universalMessageAdapter.read(getIn());
    }

    @Override
    public void send(Message message) throws IOException {
        universalMessageAdapter.write(getOut(), message);
    }

    @Override
    public String toString() {
        return "ServerConnection{" +
                "serverId=" + serverId +
                '}';
    }
}
