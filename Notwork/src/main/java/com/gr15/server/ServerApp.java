package com.gr15.server;

import com.gr15.server.managers.ClientManager;
import com.gr15.server.managers.ServerManager;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

/**
 * Application to run for the server
 */
public class ServerApp {
    private volatile boolean isStopping = false;

    private final ServerConfig initialConfig;

    private final ClientManager clientManager;
    private final ServerManager serverManager;

    public static final int POLL_SLEEP = 1000 / 5; // 5 refresh/s


    public ServerApp(ServerConfig initialConfig) {
        this.initialConfig = initialConfig;

        if (!initialConfig.validateConfiguration()) {
            Logger.error("Invalid configuration config=" + initialConfig);
            throw new IllegalArgumentException("Invalid configuration");
        }

        clientManager = new ClientManager(this);
        serverManager = new ServerManager(this);
    }

    public void run() {
        Logger.info("Started new ServerApp");

        try {
            clientManager.start();
        } catch (RuntimeException e) {
            Logger.error("Failed to start the client manager", e);
            return;
        }

        try {
            serverManager.start();
        } catch (RuntimeException e) {
            Logger.error("Failed to start the server manager", e);
            return;
        }

        // Keep alive
        while (!isStopping) {
            ThreadUtils.safeSleep(POLL_SLEEP);

            clientManager.pollEvents();
            serverManager.pollEvents();
        }

        clientManager.stop();
        serverManager.stop();
    }

    public void stop() {
        Logger.debug("Stop received");
        isStopping = true;
    }

    public ServerConfig getInitialConfig() {
        return initialConfig;
    }

    public ClientManager getClientManager() {
        return clientManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }
}
