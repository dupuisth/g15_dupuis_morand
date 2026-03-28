package com.gr15.server;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;

import java.util.ArrayList;

/**
 * Configuration for the server
 */
public class ServerConfig {
    public static final String ARG_SERVER_ID_KEY = "serverId=";
    public static final String ARG_SERVER_CLIENT_PORT_KEY = "clientport=";

    public static final String ARG_COMPACT_KEY = "server=";

    // Arbitrary values, should just not be using the well known ports
    public static final int PORT_MAX = 4300;
    public static final int PORT_MIN = 2000;

    private Integer serverId;
    private Integer clientSocketPort;

    public ServerConfig() {
        serverId = null;
        clientSocketPort = null;
    }

    public ServerConfig(int serverId, int clientSocketPort) {
        this.serverId = serverId;
        this.clientSocketPort = clientSocketPort;
    }

    public boolean validateConfiguration() {
        if (serverId == null || serverId < 0 || serverId >= ClientId.MAX_SERVERS) {
            return false;
        }

        if (clientSocketPort == null || clientSocketPort < PORT_MIN || clientSocketPort > PORT_MAX) {
            return false;
        }

        return true;
    }

    public static ServerConfig FromCli() {
        int serverId = CliHelper.inputInt("Enter serverId", 0, ClientId.MAX_SERVERS);
        int clientSocketPort = CliHelper.inputInt("Enter client socket port", PORT_MIN, PORT_MAX);

        return new ServerConfig(serverId, clientSocketPort);
    }

    public static ServerConfig FromArgs(String[] args) {
        ServerConfig config = new ServerConfig();

        // Read the args to get configuration from it
        for (String arg : args) {
            if (arg.startsWith(ARG_SERVER_ID_KEY)) {
                try {
                    config.setServerId(Integer.parseInt(arg.substring(ARG_SERVER_ID_KEY.length())));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            } else if (arg.startsWith(ARG_SERVER_CLIENT_PORT_KEY)) {
                try {
                    config.setClientSocketPort(Integer.parseInt(arg.substring(ARG_SERVER_CLIENT_PORT_KEY.length())));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            }
        }

        return config;
    }

    public static ServerConfig FromCompactArgs(String arg) {
        // Form : SERVER_KEY=ID:CLIENT_SOCKET_PORT
        String dataString = arg.substring(ARG_COMPACT_KEY.length());

        int separator = dataString.indexOf(':');
        if (separator == -1) {
            throw new IllegalArgumentException("Bad format for server argument arg=" + arg);
        }

        ServerConfig config = new ServerConfig();
        try {
            config.setServerId(Integer.parseInt(dataString.substring(0, separator)));
        } catch (NumberFormatException e) {
            // ok
        }

        try {
            config.setClientSocketPort(Integer.parseInt(dataString.substring(separator + 1)));
        } catch (NumberFormatException e) {
            // ok
        }
        return config;
    }

    public ArrayList<String> toArgs() {
        ArrayList<String> args = new ArrayList<>();
        args.add(ServerConfig.ARG_SERVER_ID_KEY +  serverId);
        args.add(ServerConfig.ARG_SERVER_CLIENT_PORT_KEY + clientSocketPort);
        return args;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
    }

    public int getClientSocketPort() {
        return clientSocketPort;
    }

    public void setClientSocketPort(int clientSocketPort) {
        this.clientSocketPort = clientSocketPort;
    }

    @Override
    public String toString() {
        return "ServerConfig{" +
                "serverId=" + serverId +
                ", clientSocketPort=" + clientSocketPort +
                '}';
    }
}
