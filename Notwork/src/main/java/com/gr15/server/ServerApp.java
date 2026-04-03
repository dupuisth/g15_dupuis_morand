package com.gr15.server;

import com.gr15.common.Constants;
import com.gr15.server.managers.AdminManager;
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
    private final AdminManager adminManager;


    public ServerApp(ServerConfig initialConfig) {
        this.initialConfig = initialConfig;

        if (!initialConfig.validateConfiguration()) {
            Logger.error("Invalid configuration config=" + initialConfig);
            throw new IllegalArgumentException("Invalid configuration");
        }

        Logger.info("Config " + initialConfig);

        clientManager = new ClientManager(this);
        serverManager = new ServerManager(this);
        adminManager = new AdminManager(this);
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
            clientManager.stop();
            return;
        }

        try {
            adminManager.start();
        } catch (RuntimeException e) {
            Logger.error("Failed to start the admin manager", e);
            clientManager.stop();
            serverManager.stop();
            return;
        }

        // Keep alive
        while (!isStopping) {
            ThreadUtils.safeSleep(Constants.SERVER_POLL_DELAY_MS);

            clientManager.pollEvents();
            serverManager.pollEvents();
            adminManager.pollEvents();
        }

        clientManager.stop();
        serverManager.stop();
        adminManager.stop();
    }

    public void setShouldStop() {
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
