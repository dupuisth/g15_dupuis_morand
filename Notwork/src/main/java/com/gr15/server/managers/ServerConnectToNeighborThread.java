package com.gr15.server.managers;

import com.gr15.common.listening.ListeningThread;
import com.gr15.server.ServerConfig;
import com.gr15.server.connections.ServerConnection;
import com.gr15.server.connections.ServerWrapper;
import com.gr15.server.handlers.ServerHandler;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import static com.gr15.common.Constants.SERVER_POLL_DELAY_MS;

public class ServerConnectToNeighborThread extends Thread {
    private final ServerManager serverManager;

    private volatile boolean shouldStop = false;
    private volatile boolean pause = false;

    public ServerConnectToNeighborThread(ServerManager serverManager) {
        this.serverManager = serverManager;
    }

    @Override
    public void run() {
        super.run();

        // Try to connect to the neighbors !
        while (!shouldStop) {
            if (!ThreadUtils.safeSleep(SERVER_POLL_DELAY_MS)) {
                if (pause) continue;
            }

            Set<Integer> serverIds = serverManager.getServerIds();

            ArrayList<ServerConfig.NeighborServerInfo> notConnected = new ArrayList<>();
            for (ServerConfig.NeighborServerInfo neighborServerInfo : serverManager.server.getInitialConfig().getNeighbors()) {
                if (!serverIds.contains(neighborServerInfo.getServerId())) {
                    notConnected.add(neighborServerInfo);
                }
            }

            for (ServerConfig.NeighborServerInfo info : notConnected) {
                // The lower serverId should initiate the connection
                if (info.getServerId() < serverManager.server.getInitialConfig().getServerId()) continue;


                ServerConnection serverConnection;
                try {
                    serverConnection = new ServerConnection(info.getServerHostname(), info.getServerPort(), true, info.getServerId());
                } catch (IOException e) {
                    Logger.error("Failed to connect to neighbor server: " + info, e);
                    continue;
                }

                ServerHandler handler = new ServerHandler(serverConnection, serverManager.server, info);
                ListeningThread<ServerConnection> listener = new ListeningThread<>(serverConnection, serverManager::onMessageRead, serverManager::onListeningError);
                ServerWrapper wrapper = new ServerWrapper(serverConnection, listener, handler);

                synchronized (serverManager.getConnectionsLock()) {
                    serverManager.getConnections()[info.getServerId()] = wrapper;
                }

                handler.start();
                listener.start();
            }
        }

        Logger.info("ServerConnectToNeighborThread stopped");
    }

    public void setShouldStop() {
        this.shouldStop = true;
    }

    public void setPause(boolean pause) {
        this.pause = pause;
    }
}
