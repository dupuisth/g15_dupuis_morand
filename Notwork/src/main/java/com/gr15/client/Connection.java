package com.gr15.client;

import com.gr15.common.connections.RemoteConnection;

import java.io.IOException;

/**
 * Represent the connection to the server
 */
public class Connection extends RemoteConnection {
    public Connection(String serverHostname, int port, boolean connectInstantly) throws IOException {
        super(serverHostname, port, connectInstantly);
    }
}
