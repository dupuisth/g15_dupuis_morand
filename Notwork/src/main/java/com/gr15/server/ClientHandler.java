package com.gr15.server;

import com.gr15.common.Message;
import com.gr15.common.message.STC_MessageHello;
import com.gr15.common.message.STC_MessageNewClient;
import com.gr15.utils.Logger;

import java.io.EOFException;
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
            server.sendToClients(newClientMessage, clientConnection);
        } catch (IOException e) {
            Logger.warn("Failed to send new client message e="+e.getMessage());
        }

        while (clientConnection.isConnected()) {
            // Read
            try {
                Message readMessage = clientConnection.read();
                server.onMessageReceived(clientConnection, readMessage);
            } catch (EOFException e) {
                // EOF is thrown when the socket is closed
                Logger.warn("Received a EOF when try to read from c=" + clientConnection.getClientId() + ", closing the connection");
                clientConnection.close();
                server.onClientDisconnected(clientConnection);
            }   catch (Exception e) {
                Logger.warn("Failed to read message e=" + e.getMessage());
            }
        }
    }
}
