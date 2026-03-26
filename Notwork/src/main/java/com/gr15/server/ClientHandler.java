package com.gr15.server;

import com.gr15.common.Message;
import com.gr15.common.message.STC_MessageHello;
import com.gr15.common.message.STC_MessageNewClient;

import java.io.EOFException;
import java.io.IOException;
import java.util.logging.Logger;

public class ClientHandler extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private final ConnectionToClient connectionToClient;
    private final ServerApp server;

    public ClientHandler(ConnectionToClient connectionToClient, ServerApp server) {
        this.connectionToClient = connectionToClient;
        this.server = server;
    }

    @Override
    public void run() {
        super.run();

        // Send a welcome to the client
        Message welcomeMessage = STC_MessageHello.CreateMessage(connectionToClient.getClientId(), "Coucou !");
        try {
            connectionToClient.send(welcomeMessage);
        } catch (IOException e) {
            LOGGER.warning("Failed to send welcome message e="+e.getMessage());
        }

        // Send the "NEW_CLIENT" to everyone else
        Message newClientMessage = STC_MessageNewClient.CreateMessage(connectionToClient.getClientId());
        try {
            server.sendToClients(newClientMessage, connectionToClient);
        } catch (IOException e) {
            LOGGER.warning("Failed to send new client message e="+e.getMessage());
        }

        while (connectionToClient.isConnected()) {
            // Read
            try {
                Message readMessage = Message.readMessageFromSocket(connectionToClient.getIn());
                server.onMessageReceived(connectionToClient, readMessage);
            } catch (EOFException e) {
                // EOF is thrown when the socket is closed
                LOGGER.warning("Received a EOF when try to read from c=" + connectionToClient.getClientId() + ", closing the connection");
                connectionToClient.close();
                server.onClientDisconnected(connectionToClient);
            }   catch (Exception e) {
                LOGGER.warning("Failed to read message e=" + e.getMessage());
            }
        }
    }

    public ConnectionToClient getConnectionToClient() {
        return connectionToClient;
    }
}
