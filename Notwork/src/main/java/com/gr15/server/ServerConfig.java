package com.gr15.server;

/**
 * Configuration for the server
 */
public class ServerConfig {
    private final int serverId;
    private final int clientSocketPort;

    public ServerConfig(int serverId, int clientSocketPort) {
        this.serverId = serverId;
        this.clientSocketPort = clientSocketPort;
    }

    public static ServerConfig FromCli() {

    }
}
