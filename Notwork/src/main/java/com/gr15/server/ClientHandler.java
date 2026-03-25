package com.gr15.server;

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

        // Listen to the client
        while (connectionToClient.isConnected()) {


        }
    }

    private String getMessage() {

        String message = null;
        while (message == null) {
            synchronized (connectionToClient.getIn()) {
                try {
                    message = connectionToClient.getIn().readLine();
                } catch (IOException e) {
                    // Do nothing
                }
            }
        }
        return message;
    }

    public ConnectionToClient getConnectionToClient() {
        return connectionToClient;
    }
}
