package com.gr15.server.connections;

import com.gr15.common.ClientId;
import com.gr15.common.connections.RemoteConnection;

import java.io.IOException;
import java.net.Socket;

/**
 * Server-side connection to one local client.
 *
 * The assigned client id is stable for the lifetime of the connection and is
 * used by ClientManager to index the local client array.
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

    @Override
    public String toString() {
        return ClientId.toString(clientId);
    }
}
