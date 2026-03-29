package com.gr15.server.connections;

import com.gr15.common.listening.ListeningThread;
import com.gr15.server.handlers.ServerHandler;

public class ServerWrapper extends ConnectionWrapper<ServerConnection> {
    private final ListeningThread<ServerConnection> listeningThread;
    private final ServerHandler handler;

    public ServerWrapper(ServerConnection connection, ListeningThread<ServerConnection> listeningThread, ServerHandler handler) {
        super(connection);
        this.listeningThread = listeningThread;
        this.handler = handler;
    }

    public ListeningThread<ServerConnection> getListeningThread() {
        return listeningThread;
    }

    public ServerHandler getHandler() {
        return handler;
    }
}
