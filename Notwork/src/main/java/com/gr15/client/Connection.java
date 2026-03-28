package com.gr15.client;

import com.gr15.common.RemoteConnection;
import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Represent the connection to the server
 */
public class Connection extends RemoteConnection {
    public Connection(String serverHostname, int port, boolean connectInstantly) throws IOException {
        super(serverHostname, port, connectInstantly);
    }
}
