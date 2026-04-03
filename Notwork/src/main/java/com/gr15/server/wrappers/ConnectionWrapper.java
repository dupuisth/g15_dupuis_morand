package com.gr15.server.wrappers;

public abstract class ConnectionWrapper<T> {
    private final T connection;

    public ConnectionWrapper(T connection) {
        this.connection = connection;
    }

    public T getConnection() {
        return connection;
    }
}
