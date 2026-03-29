package com.gr15.server.connections;

import com.gr15.common.ClientId;
import com.gr15.common.connections.RemoteConnection;

import java.io.IOException;
import java.net.Socket;

/**
 * Represent a connection to a server
 */
public class ServerConnection extends RemoteConnection {
    private Integer serverId;

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
    public String toString() {
        return "ServerConnection{" +
                "serverId=" + serverId +
                '}';
    }
}