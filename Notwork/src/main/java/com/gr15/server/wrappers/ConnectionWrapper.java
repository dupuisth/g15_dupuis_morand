package com.gr15.server.wrappers;

/**
 * Base holder for a typed connection and its runtime companions.
 *
 * Concrete wrappers add the listening thread and handler thread that must be
 * stopped with the connection during cleanup.
 */
public abstract class ConnectionWrapper<T> {
    private final T connection;

    public ConnectionWrapper(T connection) {
        this.connection = connection;
    }

    public T getConnection() {
        return connection;
    }
}
