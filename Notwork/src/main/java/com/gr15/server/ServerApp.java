package com.gr15.server;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.message.CTS_Message;
import com.gr15.common.message.MessageCTS;
import com.gr15.common.message.STC_Message;
import com.gr15.common.message.STC_MessageRemoveClient;
import com.gr15.server.connections.ClientConnection;
import com.gr15.server.handlers.ClientHandler;
import com.gr15.server.managers.ClientManager;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Application to run for the server
 */
public class ServerApp {
    private volatile boolean isStopping = false;

    private final ServerConfig initialConfig;

    private final ClientManager clientManager;


    public ServerApp(ServerConfig initialConfig) {
        this.initialConfig = initialConfig;

        if (!initialConfig.validateConfiguration()) {
            Logger.error("Invalid configuration config=" + initialConfig);
            throw new IllegalArgumentException("Invalid configuration");
        }

        clientManager = new ClientManager(this);
    }

    public void run() {
        Logger.info("Started new ServerApp");

        try {
            clientManager.start();
        } catch (RuntimeException e) {
            Logger.error("Failed to start the client manager", e);
            return;
        }

        // Keep alive
        while (!isStopping) {
            ThreadUtils.safeSleep(1000);
        }

        clientManager.stop();

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
}
