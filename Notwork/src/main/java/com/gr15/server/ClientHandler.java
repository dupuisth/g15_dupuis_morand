package com.gr15.server;

import java.io.IOException;
import java.util.logging.Logger;

public class ClientHandler extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());


    private ConnectionToClient connectionToClient;

    public ClientHandler(ConnectionToClient connectionToClient)  {
        this.connectionToClient = connectionToClient;
    }

    @Override
    public void run() {
        super.run();

        // Listen to the client
        while (connectionToClient.isConnected())
        {
            String message = getMessage();
            LOGGER.info("Received message from inet=" + connectionToClient.getSocket().getInetAddress() + ", message=\""+message + "\"");
        }
    }

    private String getMessage() {

        String message = null;
        while (message == null)
        {
            // Block connectionToClient.getIn() => Si d'autres process veulent le pipe, alors ils seront en attentes
            synchronized (connectionToClient.getIn())
            {
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
