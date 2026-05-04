package com.gr15.server.wrappers;

import com.gr15.common.listening.ListeningThread;
import com.gr15.server.connections.AdminConnection;
import com.gr15.server.handlers.AdminHandler;
import com.gr15.server.handlers.ClientHandler;

public final class AdminWrapper extends ConnectionWrapper<AdminConnection> {
    private final ListeningThread<AdminConnection> listeningThread;
    private final AdminHandler handler;

    public AdminWrapper(AdminConnection connection, ListeningThread<AdminConnection> listeningThread, AdminHandler handler) {
        super(connection);
        this.listeningThread = listeningThread;
        this.handler = handler;
    }

    public ListeningThread<AdminConnection> getListeningThread() {
        return listeningThread;
    }

    public AdminHandler getHandler() {
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
