package com.gr15.client;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.Message;
import com.gr15.common.message.cts.CTS_Message;
import com.gr15.common.message.stc.*;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;

public class ClientApp {
    private ClientConfig config;

    private Connection connection;

    private final Thread mainThread;

    private int clientId;

    public ClientApp(ClientConfig config) {
        this.config = config;
        if (!config.validateConfiguration()) {
            throw new IllegalArgumentException("Invalid ClientConfig: " + config);
        }

        mainThread = Thread.currentThread();

        try {
            // Should never throw since we do not connect directly
            this.connection = new Connection(config.getServerHostname(), config.getServerPort(), false);
        } catch (IOException e) {
            Logger.error("Exception while creating new connection", e);
            throw new IllegalStateException("Failed to create the connection");
        }
    }

    public void run() {
        Logger.info("Started new ClientApp");

        while (!connection.isConnected()) {
            try {
                connection.connect();
            } catch (IOException e) {
                Logger.error("Failed to connect", e);
            }

            if (!ThreadUtils.safeSleep(1000)) {
                connection.close();
                return;
            }
        }

        // Open the listening thread
        ListeningThread<Connection> listenThread = new ListeningThread<>(connection, this::onMessageReceived, this::onCriticalListeningError);
        listenThread.start();

        // Prompt for a message
        while (connection.isConnected()) {
            // Take the client input
            // THIS WILL BLOCK UNTIL THE USER PRESS ENTER, SO, IF A DISCONNECTION HAPPEN
            // THE USER WILL NOT BE NOTIFIED UNTIL THIS END
            // todo: FIX THIS LATER
            String input = CliHelper.inputString(null, 0, 0);
            Message message = CTS_Message.CreateMessage(input);
            try {
                connection.send(message);
            } catch (IOException e) {
                Logger.warn("Failed to send message to server e=" + e.getMessage());
            }
        }
        Logger.info("Stopping everything");


        listenThread.setShouldStop();
        connection.close();
        listenThread.interrupt();
        try {
            listenThread.join(500);
        } catch (InterruptedException e) {
            Logger.error("Exception while joining listening thread", e);
        }

    }

    public boolean onCriticalListeningError(Connection connection, Exception e) {
        Logger.error("Critical error, closing the socket", e);

        // Stop the socket
        connection.close();

        // Interrupt the main thread (this will not interrupt the scanner)
        mainThread.interrupt();

        return true;
    }

    public void onMessageReceived(Connection connection, Message message) {
        Logger.debug("New message received : length=" + message.getData().length + " data=" + message.getDataAsBitsInString());

        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageSTC messageType = MessageSTC.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case null -> {
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
            case HELLO -> {
                STC_MessageHello parsedMessage = STC_MessageHello.ReadMessage(message);
                handleMessage(parsedMessage);
            }
            case MESSAGE -> {
                STC_Message parsedMessage = STC_Message.ReadMessage(message);
                handleMessage(parsedMessage);
            }
            case NEW_CLIENT -> {
                STC_MessageNewClient parsedMessage = STC_MessageNewClient.ReadMessage(message);
                handleMessage(parsedMessage);
            }
            case REMOVE_CLIENT -> {
                STC_MessageRemoveClient parsedMessage = STC_MessageRemoveClient.ReadMessage(message);
                handleMessage(parsedMessage);
            }
        }
    }

    public void handleMessage(STC_Message message) {
        Logger.debug(message.toString());

        CliHelper.show("[Client][" + ClientId.toString(message.getFromClientId()) + "]: " + message.getContent());
    }

    public void handleMessage(STC_MessageHello message) {
        Logger.debug(message.toString());
        this.clientId = message.getClientId();

        CliHelper.show("[Server][Welcome]: \"" + message.getWelcomeMessage() + "\", clientId=" + ClientId.toString(message.getClientId()));
    }

    public void handleMessage(STC_MessageNewClient message) {
        Logger.debug(message.toString());

        CliHelper.show("[Server][NewClient]: clientId=" + message.getClientId());
    }

    public void handleMessage(STC_MessageRemoveClient message) {
        Logger.debug(message.toString());

        CliHelper.show("[Server][RemoveClient]: clientId=" + message.getClientId());
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "connection=" + connection +
                ", clientId=" + clientId +
                '}';
    }
}
