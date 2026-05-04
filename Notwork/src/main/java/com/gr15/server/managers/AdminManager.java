package com.gr15.server.managers;

import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.ats.*;
import com.gr15.server.ServerApp;
import com.gr15.server.ServerConfig;
import com.gr15.server.connections.AdminConnection;
import com.gr15.server.wrappers.AdminWrapper;
import com.gr15.server.handlers.AdminHandler;
import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.gr15.common.Constants.*;

public class AdminManager extends Manager<AdminConnection, AdminWrapper> {
    /** Array of all the admins connected */
    private final AdminWrapper[] connectionsToAdmin = new AdminWrapper[MAX_ADMINS];

    private final Object connectionsLock = new Object();

    private final Queue<AdminConnection> connectionsToRemoveQueue = new LinkedList<>();
    private final Queue<MessageReceived> messageReceivedQueue = new LinkedList<>();
    private final Queue<MessageToSend> messageToSendQueue = new LinkedList<>();

    public AdminManager(ServerApp server) {
        super(server);
    }

    @Override
    public void pollEvents() {
        while (true) {
            AdminConnection connectionToRemove;
            synchronized (connectionsToRemoveQueue) {
                connectionToRemove = connectionsToRemoveQueue.poll();
            }
            if (connectionToRemove == null) {
                break;
            }
            stopConnection(connectionToRemove);
        }

        synchronized (messageReceivedQueue) {
            while (!messageReceivedQueue.isEmpty()) {
                MessageReceived received = messageReceivedQueue.poll();

                // Handle the message
                handleMessage(received.connection(), received.message());
            }
        }

        synchronized (messageToSendQueue) {
            while (!messageToSendQueue.isEmpty()) {
                MessageToSend toSend = messageToSendQueue.poll();
                toSend.connection.safeSend(toSend.message);
            }
        }
    }

    @Override
    protected void handleNewSocket(Socket socket) {
        Logger.info("New admin socket inet=" + socket.getInetAddress() + ":" + socket.getPort());

        // Create a new connection
        AdminConnection adminConnection = null;

        synchronized (getConnectionsLock()) {
            int adminId;
            try {
                adminId = nextAdminId();
            } catch (RuntimeException e) {
                Logger.error("Failed to create new adminId e=" + e.getMessage());
                return;
            }


            try {
                adminConnection = new AdminConnection(socket, adminId);
            } catch (IOException e) {
                Logger.error("Failed to bind new admin, disconnecting it", e);
                try {
                    socket.close();
                } catch (IOException ex) {
                    Logger.error("Failed to close the connection", ex);
                }
            }

            if (adminConnection == null) {
                // Error while binding the client, ignore him
                return;
            }

            // Create and start the listening thread
            ListeningThread<AdminConnection> listeningThread = createDefaultListeningThread(adminConnection);
            listeningThread.start();

            // Create and start the handler
            AdminHandler adminHandler = new AdminHandler(adminConnection, server);
            adminHandler.start();

            // Add it to the clients list
            AdminWrapper wrapper = new AdminWrapper(adminConnection, listeningThread, adminHandler);
            connectionsToAdmin[adminId] = wrapper;
        }

        Logger.info("Created new admin");
    }

    @Override
    protected void stopConnection(AdminConnection connection) {
        synchronized (getConnectionsLock()) {
            AdminWrapper wrapper = getWrapped(connection);
            if (wrapper == null) {
                Logger.warn("Connection already removed: " + connection);
                return;
            }

            int adminId = wrapper.getConnection().getAdminId();

            wrapper.getListeningThread().setShouldStop();
            wrapper.getConnection().close();
            wrapper.getListeningThread().interrupt();
            wrapper.getHandler().setShouldStop();
            wrapper.getHandler().interrupt();

            try {
                wrapper.getListeningThread().join(1000);
            } catch (InterruptedException e) {
                Logger.error("Interrupted while joining listening thread", e);
                Thread.currentThread().interrupt();
            }

            try {
                wrapper.getHandler().join(1000);
            } catch (InterruptedException e) {
                Logger.error("Interrupted while joining handler thread", e);
                Thread.currentThread().interrupt();
            }

            // Remove the connection
            getConnections()[adminId] = null;
        }
        Logger.info("Fully stopped connection to " + connection);
    }

    @Override
    protected void onMessageRead(AdminConnection remoteConnection, Message message) {
        Logger.info("Received a message from " + remoteConnection + ", length=" + message);

        synchronized (messageReceivedQueue) {
            MessageReceived received = new MessageReceived(remoteConnection, message);
            messageReceivedQueue.add(received);
        }
    }

    protected void handleMessage(AdminConnection fromAdmin, Message message) {
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageATS messageType = MessageATS.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case null -> {
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
            case LIST_NEIGHBORS -> {
                // TODO: Do this later
            }
            case ADD_NEIGHBOR -> {
                handleMessage(fromAdmin, ATS_AddNeighbor.ReadMessage(message));
            }
            case REMOVE_NEIGHBOR -> {
                handleMessage(fromAdmin, ATS_RemoveNeighbor.ReadMessage(message));
            }
            case STOP -> {
                handleMessage(fromAdmin, ATS_Stop.ReadMessage(message));
            }
            case RESET -> {
                handleMessage(fromAdmin, ATS_Reset.ReadMessage(message));
            }
        }
    }

    private void handleMessage(AdminConnection from, ATS_Stop message) {
        server.setShouldStop();
    }

    private void handleMessage(AdminConnection from, ATS_Reset message) {
        server.getClientManager().reset();
        server.getServerManager().reset();
    }

    private void handleMessage(AdminConnection from, ATS_AddNeighbor message) {
        server.getInitialConfig().addNeighbor(new ServerConfig.NeighborServerInfo(null, message.getHostname(), message.getPort()));
    }

    private void handleMessage(AdminConnection from, ATS_RemoveNeighbor  message) {
        server.getInitialConfig().removeNeighbor(new ServerConfig.NeighborServerInfo(null, message.getHostname(), message.getPort()));
    }

    private void handleMessage(AdminConnection from, ATS_ListNeighbor message) {
        // Implement later
    }


    @Override
    protected boolean onListeningError(AdminConnection remoteConnection, Exception e) {
        // This is called from the ListeningThread, so make sure this is running from the main thread
        synchronized (connectionsToRemoveQueue) {
            connectionsToRemoveQueue.add(remoteConnection);
        }

        // All exception are critical
        return true;
    }

    @Override
    public void send(AdminConnection remoteConnection, Message message) {
        synchronized (messageToSendQueue) {
            messageToSendQueue.add(new MessageToSend(remoteConnection, message));
        }
    }

    @Override
    public void sendToAll(Message message) {
        synchronized (getConnectionsLock()) {
            synchronized (messageToSendQueue) {
                for (AdminWrapper wrapper : connectionsToAdmin) {
                    if (wrapper == null) continue;
                    messageToSendQueue.add(new MessageToSend(wrapper.getConnection(), message));
                }
            }
        }
    }

    @Override
    public void sendToAll(Message message, AdminConnection except) {
        synchronized (getConnectionsLock()) {
            synchronized (messageToSendQueue) {
                for (AdminWrapper wrapper : connectionsToAdmin) {
                    if (wrapper == null || wrapper.getConnection() == null || wrapper.getConnection() == except) continue;
                    messageToSendQueue.add(new MessageToSend(wrapper.getConnection(), message));
                }
            }
        }
    }

    @Override
    public void send(List<AdminConnection> remoteConnections, Message message) {
        synchronized (messageToSendQueue) {
            for (AdminConnection connection : remoteConnections) {
                messageToSendQueue.add(new MessageToSend(connection, message));
            }
        }
    }
    
    private int nextAdminId() {
        synchronized (connectionsToAdmin) {
            for (int i = 0; i < MAX_ADMINS; i++) {
                if (connectionsToAdmin[i] == null) {
                    return i;
                }
            }
        }

        throw new RuntimeException("No space left for admins");
    }

    @Override
    public void stop() {
        super.stop();

        // Force stop directly
        synchronized (getConnectionsLock()) {
            for (int i = connectionsToAdmin.length - 1; i >= 0 ; i--) {
                if (connectionsToAdmin[i] != null) {
                    stopConnection(connectionsToAdmin[i].getConnection());
                }
            }
        }
    }

    @Override
    public int getPort() {
        return server.getInitialConfig().getAdminSocketPort();
    }

    @Override
    public AdminWrapper[] getConnections() {
        return connectionsToAdmin;
    }

    @Override
    public Object getConnectionsLock() {
        return connectionsLock;
    }

    record MessageReceived(AdminConnection connection, Message message) {}
    record MessageToSend(AdminConnection connection, Message message) {}
}
