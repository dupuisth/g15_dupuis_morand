package com.gr15.server;

import com.gr15.common.Message;

import java.io.IOException;
import java.util.logging.Logger;

public class ClientHandler extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());

    private ConnectionToClient connectionToClient;
    private ServerApp server;

    public ClientHandler(ConnectionToClient connectionToClient, ServerApp server) {
        this.connectionToClient = connectionToClient;
        this.server = server;
    }

    @Override
    public void run() {
        super.run();

        // Send a welcome message !
        Message message = new Message((byte)1);
        message.addInt(0xA0, 8);

        try {
            connectionToClient.send(message);
        } catch (IOException e) {
            LOGGER.warning("Failed to send the message !");
        }

        // Listen to the client
        int i = 0;
        while (connectionToClient.isConnected()) {
            // Read
            try {
                Message readMessage = Message.readMessageFromSocket(connectionToClient.getIn());
                server.onMessageReceived(connectionToClient, readMessage);
            } catch (IOException e) {
                LOGGER.warning("Failed to read message e=" + e.getMessage());
            }
        }
    }

    public ConnectionToClient getConnectionToClient() {
        return connectionToClient;
    }
}
