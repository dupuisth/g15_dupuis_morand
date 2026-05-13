package com.gr15.server.handlers;

import com.gr15.common.Message;
import com.gr15.common.Constants;
import com.gr15.common.message.stc.STC_MessageHello;
import com.gr15.common.message.stc.STC_MessageNewClient;
import com.gr15.common.message.stc.STC_Ping;
import com.gr15.server.ServerApp;
import com.gr15.server.connections.ClientConnection;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;

/**
 * Background behavior attached to one local client connection.
 *
 * It sends the initial hello, announces known clients, publishes local routing
 * changes and periodically pings the client. Incoming client messages are still
 * read by the separate ListeningThread.
 */
public class ClientHandler extends Thread {
    private final ClientConnection clientConnection;
    private final ServerApp server;

    private volatile boolean shouldStop = false;

    public ClientHandler(ClientConnection clientConnection, ServerApp server) {
        this.clientConnection = clientConnection;
        this.server = server;
    }

    @Override
    public void run() {
        super.run();

        // Send a welcome to the client
        Message welcomeMessage = STC_MessageHello.CreateMessage(clientConnection.getClientId(), "Coucou !");
        try {
            clientConnection.send(welcomeMessage);
        } catch (IOException e) {
            Logger.warn("Failed to send welcome message e="+e.getMessage());
        }

        for (Integer knownClientId : server.getServerManager().getKnownClientIds()) {
            if (knownClientId == clientConnection.getClientId()) {
                continue;
            }
            server.getClientManager().send(clientConnection, STC_MessageNewClient.CreateMessage(knownClientId));
        }

        // Send the "NEW_CLIENT" to everyone else
        Message newClientMessage = STC_MessageNewClient.CreateMessage(clientConnection.getClientId());
        server.getClientManager().sendToAll(newClientMessage, clientConnection);
        server.getServerManager().publishLocalRoutingUpdate();

        while (!shouldStop && clientConnection.isConnected()) {
            try {
                clientConnection.send(STC_Ping.CreateMessage());
            } catch (IOException e) {
                Logger.warn("Failed to send ping to client " + clientConnection + " e=" + e.getMessage());
                clientConnection.close();
                break;
            }

            if (!ThreadUtils.safeSleep(Constants.CLIENT_PING_INTERVAL_MS)) {
                break;
            }
        }

        Logger.info("Stopped Client handler");
    }

    public void setShouldStop() {
        shouldStop = true;
        this.interrupt();
    }
}
