package com.gr15.server.handlers;

import com.gr15.common.Constants;
import com.gr15.common.message.sts.STS_Identify;
import com.gr15.common.message.sts.STS_Ping;
import com.gr15.server.ServerApp;
import com.gr15.server.ServerConfig;
import com.gr15.server.connections.ServerConnection;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;

/**
 * Thread created when a server connects to the server
 */
public class ServerHandler extends Thread {
    private final ServerConnection serverConnection;
    private final ServerApp server;
    private final ServerConfig.NeighborServerInfo neighborServerInfo;

    private volatile boolean shouldStop = false;

    public ServerHandler(ServerConnection serverConnection, ServerApp server, ServerConfig.NeighborServerInfo neighborServerInfo) {
        this.serverConnection = serverConnection;
        this.server = server;
        this.neighborServerInfo = neighborServerInfo;
    }

    @Override
    public void run() {
        super.run();

        while (!shouldStop && serverConnection.isConnected()) {
            if (serverConnection.getServerId() == null) {
                server.getServerManager().send(serverConnection, STS_Identify.CreateMessage(server.getInitialConfig().getServerId(), 0));
            } else {
                server.getServerManager().send(serverConnection, STS_Ping.CreateMessage());
            }

            int sleepTime = Math.max(2 * Constants.SERVER_POLL_DELAY_MS, Constants.SERVER_PING_INTERVAL_MS);
            if (!ThreadUtils.safeSleep(sleepTime)) {
                break;
            }
        }

        Logger.info("Stopped Server handler");
    }

    public void setShouldStop() {
        shouldStop = true;
        this.interrupt();
    }
}
