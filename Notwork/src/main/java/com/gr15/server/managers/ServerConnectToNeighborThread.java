package com.gr15.server.managers;

import com.gr15.common.listening.ListeningThread;
import com.gr15.server.ServerConfig;
import com.gr15.server.connections.ServerConnection;
import com.gr15.server.wrappers.ServerWrapper;
import com.gr15.server.handlers.ServerHandler;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.gr15.common.Constants.SERVER_NEIGHBOR_RECONNECT_INTERVAL_MS;
import static com.gr15.common.Constants.SERVER_POLL_DELAY_MS;

public class ServerConnectToNeighborThread extends Thread {
    private final ServerManager serverManager;
    private final Map<ServerConfig.NeighborServerInfo, Long> nextConnectionAttemptMs = new HashMap<>();

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
                break;
            }

            if (pause) continue;

            ArrayList<ServerConnection> connections = serverManager.getAllConnections();

            ArrayList<ServerConfig.NeighborServerInfo> configuredNeighbors = serverManager.server.getInitialConfig().getNeighbors();
            nextConnectionAttemptMs.keySet().retainAll(configuredNeighbors);

            Set<ServerConfig.NeighborServerInfo> notConnected = new HashSet<>();
            for (ServerConfig.NeighborServerInfo neighborServerInfo : configuredNeighbors) {
                boolean found = false;
                for (ServerConnection connection : connections) {
                    // Check if we are already connected
                    if ((neighborServerInfo.getServerId() != null && neighborServerInfo.getServerId().equals(connection.getServerId()))
                            || (neighborServerInfo.getServerHostname().equals(connection.getHostname()) && neighborServerInfo.getServerPort() == connection.getPort())) {
                        found = true;
                        break;
                    }
                }
                if (found) continue;
                notConnected.add(neighborServerInfo);
            }

            long currentTimeMs = System.currentTimeMillis();
            for (ServerConfig.NeighborServerInfo info : notConnected) {
                // Only the higher server id initiates the TCP connection. This
                // prevents two configured neighbors from opening duplicate
                // sockets toward each other at the same time.
                if (info.getServerId() != null && info.getServerId() < serverManager.server.getInitialConfig().getServerId()) continue;

                Long nextAttemptMs = nextConnectionAttemptMs.get(info);
                if (nextAttemptMs != null && currentTimeMs < nextAttemptMs) {
                    continue;
                }

                ServerConnection serverConnection;
                try {
                    // The universal CONNECT packet is the source of truth for
                    // the remote id, so outbound sockets also start pending
                    // authentication.
                    serverConnection = new ServerConnection(info.getServerHostname(), info.getServerPort(), true, null);
                } catch (IOException e) {
                    nextConnectionAttemptMs.put(info, currentTimeMs + SERVER_NEIGHBOR_RECONNECT_INTERVAL_MS);
                    Logger.warn("Failed to connect to neighbor server, retrying in " + SERVER_NEIGHBOR_RECONNECT_INTERVAL_MS + "ms: " + info + " (" + e.getMessage() + ")");
                    continue;
                }
                nextConnectionAttemptMs.remove(info);

                ServerHandler handler = new ServerHandler(serverConnection, serverManager.server, info);
                ListeningThread<ServerConnection> listener = new ListeningThread<>(serverConnection, serverManager::onMessageRead, serverManager::onListeningError);
                ServerWrapper wrapper = new ServerWrapper(serverConnection, listener, handler);

                synchronized (serverManager.getPendingAuthentificationConnections()) {
                    serverManager.getPendingAuthentificationConnections().add(wrapper);
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
