package com.gr15.server;

import java.net.Socket;

public class ConnectionToClient {
    private Socket socket;

    public ConnectionToClient(Socket socket) {
        this.socket = socket;
    }

    public Socket getSocket() {
        return socket;
    }
}
