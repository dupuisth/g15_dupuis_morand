package com.gr15.server.managers;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.STC_MessageRemoveClient;
import com.gr15.server.ServerApp;
import com.gr15.server.SocketAcceptingThread;
import com.gr15.server.connections.ClientConnection;
import com.gr15.server.handlers.ClientHandler;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public abstract class Manager<T extends RemoteConnection> {
    protected final ServerApp server;

    /** Socket  */
    private ServerSocket serverSocket;
    /** Thread that accepts the socket automatically */
    private SocketAcceptingThread acceptingThread;

    public abstract T[] getConnections();

    public Manager(ServerApp server) {
        this.server = server;
    }

    public void start() {
        // Prevent running the server two times
        if (serverSocket != null) {
            Logger.warn("Socket already started");
            return;
        }

        // Start the server socket
        try {
            serverSocket = new ServerSocket(server.getInitialConfig().getClientSocketPort());
        } catch (IOException e) {
            Logger.error("Failed to create the socket", e);
            return;
        }

        // Start the server accepting thread
        acceptingThread = new SocketAcceptingThread(serverSocket, this::handleNewSocket);
        acceptingThread.start();
    }

    public void stop() {
        // Destroy the objects
        Logger.info("Cleaning up");
        if (serverSocket != null && !serverSocket.isClosed()) {
            acceptingThread.setShouldStop();
            acceptingThread.interrupt();

            try {
                acceptingThread.join(1000);
            } catch (InterruptedException e) {
                Logger.error("Exception while trying waiting for serverSocketAcceptingThread ending", e);
            }

            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.error("Error while closing socket: " + e.getMessage(), e);
            }
        }
        Logger.info("Cleanup done, exiting");
    }

    protected abstract void handleNewSocket(Socket socket);

    protected ListeningThread<T> createListeningThread(T remoteConnection) {
        ListeningThread<T> listeningThread = new ListeningThread<>(
                remoteConnection, this::onMessageReceived, this::onListeningError
        );
        listeningThread.start();
        return listeningThread;
    }

    protected abstract void handleConnectionLoosed(T remoteConnection);

    protected void onMessageReceived(T remoteConnection, Message message) {
        Logger.debug("Received a message  ! from=" +  remoteConnection + " length=" + message.getWrittenByte());

        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);

        dispatchMessage(remoteConnection, messageId, message);
    }

    protected abstract void dispatchMessage(T from, int messageId, Message message);

    protected abstract void onListeningError(T remoteConnection, Exception e);

    public void send(T remoteConnection, Message message) throws IOException {
        remoteConnection.send(message);
    }

    public void sendToAll(Message message) throws IOException {
        synchronized (getConnections()) {
            for (T remoteConnection : getConnections()) {
                if (remoteConnection == null) continue;
                remoteConnection.send(message);
            }
        }
    }

    public void sendToAll(Message message, T except) throws IOException {
        synchronized (getConnections()) {
            for (T remoteConnection : getConnections()) {
                if (remoteConnection == null || remoteConnection == except) continue;
                remoteConnection.send(message);
            }
        }
    }
}
