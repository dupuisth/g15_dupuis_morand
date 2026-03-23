package com.gr15.server;

import com.gr15.cli.CliHelper;

import java.util.logging.Logger;

public class ServerApp {
    public static final String SERVER_ID_KEY = "serverId=";
    public static final String SERVER_PORT_KEY = "port=";

    private static final Logger LOGGER = Logger.getLogger(ServerApp.class.getName());

    private int port;
    private int serverId;

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
    }

    public void run() {
        LOGGER.info("Started new ServerApp");


    }

    public int getPort() {
        return port;
    }

    public int getServerId() {
        return serverId;
    }

    @Override
    public String toString() {
        return "ServerApp{" +
                "port=" + port +
                ", serverId=" + serverId +
                '}';
    }
}
