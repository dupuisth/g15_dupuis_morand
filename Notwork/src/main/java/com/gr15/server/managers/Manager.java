package com.gr15.server.managers;

import com.gr15.client.Connection;
import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;
import com.gr15.common.listening.ListeningThread;
import com.gr15.server.ServerApp;
import com.gr15.server.SocketAcceptingThread;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class Manager<T extends RemoteConnection, K extends ConnectionWrapper<T>> {
    protected final ServerApp server;

    /** Socket  */
    private ServerSocket serverSocket;
    /** Thread that accepts the socket automatically */
    private SocketAcceptingThread acceptingThread;

    public abstract K[] getConnections();

    public abstract Object getConnectionsLock();

    public abstract int getPort();

    public Manager(ServerApp server) {
        this.server = server;
    }

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

    public abstract void pollEvents();

    protected abstract void handleNewSocket(Socket socket);

    protected ListeningThread<T> createListeningThread(T remoteConnection) {
        ListeningThread<T> listeningThread = new ListeningThread<>(
                remoteConnection, this::onMessageReceived, this::onListeningError
        );
        return listeningThread;
    }

    protected abstract void handleConnectionLoosed(T remoteConnection);

    protected abstract void stopConnection(K connection);

    protected void onMessageReceived(T remoteConnection, Message message) {
        Logger.debug("Received a message  ! from=" +  remoteConnection + " length=" + message.getWrittenByte());

        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);

        dispatchMessage(remoteConnection, messageId, message);
    }

    protected abstract void dispatchMessage(T from, int messageId, Message message);

    protected abstract boolean onListeningError(T remoteConnection, Exception e);

    protected K getWrapped(T connection) {
        synchronized (getConnectionsLock()) {
            for (K wrapped : getConnections()) {
                if (wrapped.getConnection() == connection) return wrapped;
            }
        }
        return null;
    }

    public void send(T remoteConnection, Message message) throws IOException {
        remoteConnection.send(message);
    }

    public void sendToAll(Message message) throws IOException {
        synchronized (getConnectionsLock()) {
            for (ConnectionWrapper<T> remoteConnection : getConnections()) {
                if (remoteConnection == null || remoteConnection.getConnection() == null) continue;
                remoteConnection.getConnection().send(message);
            }
        }
    }

    public void sendToAll(Message message, T except) throws IOException {
        synchronized (getConnectionsLock()) {
            for (ConnectionWrapper<T> remoteConnection : getConnections()) {
                if (remoteConnection == null || remoteConnection.getConnection() == null || remoteConnection.getConnection() == except) continue;
                remoteConnection.getConnection().send(message);
            }
        }
    }
}
