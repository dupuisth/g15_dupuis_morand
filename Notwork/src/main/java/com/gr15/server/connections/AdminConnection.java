package com.gr15.server.connections;

import com.gr15.common.ClientId;
import com.gr15.common.connections.RemoteConnection;

import java.io.IOException;
import java.net.Socket;

/**
 * Represent a connection to an admin
 */
public class AdminConnection extends RemoteConnection {
    private final int adminId;

    public AdminConnection(Socket socket, int adminId) throws IOException {
        super(socket);
        this.adminId = adminId;
    }

    public int getAdminId() {
        return adminId;
    }
}
