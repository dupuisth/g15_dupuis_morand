package com.gr15.server.managers;

import com.gr15.common.listening.ListeningThread;
import com.gr15.server.connections.ClientConnection;
import com.gr15.server.handlers.ClientHandler;

public final class ClientWrapper extends ConnectionWrapper<ClientConnection> {
    private final ListeningThread<ClientConnection> listeningThread;
    private final ClientHandler handler;

    public ClientWrapper(ClientConnection connection, ListeningThread<ClientConnection> listeningThread, ClientHandler handler) {
        super(connection);
        this.listeningThread = listeningThread;
        this.handler = handler;
    }

    public ListeningThread<ClientConnection> getListeningThread() {
        return listeningThread;
    }

    public ClientHandler getHandler() {
        return handler;
    }

    @Override
    public String toString() {
        return "{connection=" + getConnection() +
                "listeningThread=" + listeningThread +
                ", handler=" + handler +
                '}';
    }
}
