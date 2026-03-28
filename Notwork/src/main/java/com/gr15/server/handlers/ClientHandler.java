package com.gr15.server.handlers;

import com.gr15.common.Message;
import com.gr15.common.message.STC_MessageHello;
import com.gr15.common.message.STC_MessageNewClient;
import com.gr15.server.ServerApp;
import com.gr15.server.connections.ClientConnection;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;

/**
 * Thread created when a client connects to the server, listen for message from the client
 */
public class ClientHandler extends Thread {
    private final ClientConnection clientConnection;
    private final ServerApp server;

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

        // Send the "NEW_CLIENT" to everyone else
        Message newClientMessage = STC_MessageNewClient.CreateMessage(clientConnection.getClientId());
        try {
            server.getClientManager().sendToAll(newClientMessage, clientConnection);
        } catch (IOException e) {
            Logger.warn("Failed to send new client message e="+e.getMessage());
        }

        // Do something later on, maybe implement the ping-pong stuff...
        while (clientConnection.isConnected()) {
            if (!ThreadUtils.safeSleep(1000)) {
                break;
            }
        }
    }
}
