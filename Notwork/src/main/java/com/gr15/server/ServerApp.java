package com.gr15.server;

import com.gr15.cli.CliHelper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.logging.Logger;

public class ServerApp {
    public static final String SERVER_ID_KEY = "serverId=";
    public static final String SERVER_PORT_KEY = "port=";

    private static final Logger LOGGER = Logger.getLogger(ServerApp.class.getName());

    private boolean isStopping = false;
    private int port;
    private int serverId;

    private ServerSocket serverSocket;
    private ArrayList<ConnectionToClient> connectionsToClient = new ArrayList<>();

    public ServerApp(String[] args) {
        // Default to bad values
        port = -1;
        serverId = -1;

        for (String arg : args) {
            if (arg.startsWith(SERVER_ID_KEY)) {
                try {
                    serverId = Integer.parseInt(arg.substring(SERVER_ID_KEY.length()));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            } else if (arg.startsWith(SERVER_PORT_KEY)) {
                try {
                    port = Integer.parseInt(arg.substring(SERVER_PORT_KEY.length()));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            }
        }

        if (this.serverId < 0) {
            this.serverId = CliHelper.inputInt("Enter the server ID", 0, 32);
        }
        if (this.port <= 0) {
            this.port = CliHelper.inputInt("Enter the server port", 2222, 8888);
        }
    }

    public ServerApp(int serverId, int port) {
        this.serverId = serverId;
        this.port = port;

        connectionsToClient = new ArrayList<>();
    }

    public void run() {
        LOGGER.info("Started new ServerApp");

        if (serverSocket != null) {
            LOGGER.warning("Server already started");
            return;
        }

        // Start the server socket
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            LOGGER.warning("Failed to create a socket for " + toString());
            return;
        }

        while (!isStopping) {
            Socket socket;
            try {
                // Block until a client open a connection
                socket = serverSocket.accept();
            } catch (IOException e) {
                LOGGER.warning("Failed to accept socket: " + e.getMessage());
                // Block again
                continue;
            }

            LOGGER.info("New client! inet=" + socket.getInetAddress() + " port=" + socket.getPort());

            // Create a new connection
            ConnectionToClient connectionToClient = null;
            try {
                connectionToClient = new ConnectionToClient(socket);

            } catch (IOException e) {
                LOGGER.warning("Failed to bind new client, disconnecting him");

                try {
                    socket.close();
                } catch (IOException ex) {
                    LOGGER.warning("Failed to close the connection");
                }
            }

            if (connectionToClient == null) {
                // Error while binding the client, ignore him
                continue;
            }

            // Add it to the clients list
            connectionsToClient.add(connectionToClient);

            // Start the handler
            ClientHandler clientHandler = new ClientHandler(connectionToClient, this);
            clientHandler.start();
        }

        // Destroy the objects
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOGGER.warning("Error while closing socket: " + e.getMessage());
            }
        }
    }

    public void stop() {
        isStopping = true;
    }

    public int getPort() {
        return port;
    }

    public int getServerId() {
        return serverId;
    }

    public ArrayList<ConnectionToClient> getConnectionsToClient() {
        return connectionsToClient;
    }

    /**
     * Send to all the clients
     */
    public void broadcast(String message)
    {
        // Send a message to all the clients
        synchronized (getConnectionsToClient())
        {
            for (ConnectionToClient connectionToClient : getConnectionsToClient())
            {
                synchronized (connectionToClient.getOut())
                {
                    connectionToClient.getOut().println(message);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "ServerApp{" +
                "port=" + port +
                ", serverId=" + serverId +
                '}';
    }
}
