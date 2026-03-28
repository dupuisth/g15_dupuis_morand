package com.gr15.server.connections;

import com.gr15.common.connections.RemoteConnection;

import java.io.IOException;
import java.net.Socket;

/**
 * Represent a connection to a client
 */
public class ClientConnection extends RemoteConnection {
    private final int clientId;

    public ClientConnection(Socket socket, int clientId) throws IOException {
        super(socket);
        this.clientId = clientId;
    }

    public int getClientId() {
        return clientId;
    }
}
