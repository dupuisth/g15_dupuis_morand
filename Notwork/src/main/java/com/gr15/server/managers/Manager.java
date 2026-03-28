package com.gr15.server.managers;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;
import com.gr15.common.listening.ListeningThread;
import com.gr15.server.ServerApp;
import com.gr15.server.SocketAcceptingThread;
import com.gr15.server.connections.ConnectionWrapper;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public abstract class Manager<T extends RemoteConnection, K extends ConnectionWrapper<T>> {
    protected final ServerApp server;

    /** Socket  */
    private ServerSocket serverSocket;
    /** Thread that accepts the socket automatically */
    private SocketAcceptingThread acceptingThread;

    public Manager(ServerApp server) {
        this.server = server;
    }

    /**
     * Start the manager
     */
    public void start() throws RuntimeException {
        // Prevent running the server two times
        if (serverSocket != null) {
            Logger.warn("Server socket already started");
            throw new RuntimeException("Server socket already started");
        }

        // Start the server socket
        try {
            serverSocket = new ServerSocket(getPort());
        } catch (IOException e) {
            Logger.error("Failed to create the server socket", e);
            throw new RuntimeException("Failed to create the server socket", e);
        }

        // Start the server accepting thread
        acceptingThread = new SocketAcceptingThread(serverSocket, this::handleNewSocket);
        acceptingThread.start();
    }

    /**
     * Stop the manager
     */
    public void stop() {
        // Destroy the objects
        Logger.info("Cleaning up");
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.error("Error while closing socket: " + e.getMessage(), e);
            }

            acceptingThread.setShouldStop();
            acceptingThread.interrupt();

            try {
                acceptingThread.join(1000);
            } catch (InterruptedException e) {
                Logger.error("Exception while trying waiting for serverSocketAcceptingThread ending", e);
            }
        }
        Logger.info("Cleanup done, exiting");
    }

    /**
     * Poll the events
     */
    public abstract void pollEvents();

    /**
     * Called when a new socket is accepted
     */
    protected abstract void handleNewSocket(Socket socket);

    /**
     * Ensure the connection is stopped and clean it up
     */
    protected abstract void stopConnection(T remoteConnection);

    /**
     * Called when a message is read from a connection
     */
    protected abstract void onMessageRead(T remoteConnection, Message message);

    /**
     * Called when a listening thread receive an exception
     * @return true if the exception will close the connection
     */
    protected abstract boolean onListeningError(T remoteConnection, Exception e);

    /**
     * Send a message to a connection (will queue)
     */
    public abstract void send(T remoteConnection, Message message);

    /**
     * Send a message to all connections (will queue)
     */
    public abstract void sendToAll(Message message);

    /**
     * Send a message to all the connection except the given one (will queue)
     */
    public abstract void sendToAll(Message message, T except);

    /**
     * Send a message to a list of connection (will queue)
     */
    public abstract void send(List<T> remoteConnection, Message message);


    /**
     * Return the wrapper of the connection
     */
    protected K getWrapped(T connection) {
        synchronized (getConnectionsLock()) {
            for (K wrapped : getConnections()) {
                if (wrapped != null && wrapped.getConnection() != null && wrapped.getConnection() == connection) {
                    return wrapped;
                }
            }
        }
        return null;
    }

    public abstract K[] getConnections();

    public abstract Object getConnectionsLock();

    public abstract int getPort();

    /**
     * Create a listening thread for the given communication, will have the default values
     */
    protected ListeningThread<T> createDefaultListeningThread(T remoteConnection) {
        ListeningThread<T> listeningThread = new ListeningThread<>(
                remoteConnection, this::onMessageRead, this::onListeningError
        );
        return listeningThread;
    }
}
