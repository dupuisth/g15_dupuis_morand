package com.gr15.client;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.message.*;
import com.gr15.utils.Logger;

import java.io.IOException;

public class ClientApp {
    public static final String HOSTNAME_KEY = "hostname=";
    public static final String PORT_KEY = "port=";

    private String serverHostname;
    private int serverPort;
    private Connection connection;
    private ListeningThread listeningThread;

    private int clientId;

    public ClientApp(String[] args) {
        serverHostname = null;
        serverPort = -1;

        for (String arg : args) {
            if (arg.startsWith(HOSTNAME_KEY)) {
                serverHostname = arg.substring(HOSTNAME_KEY.length());
            } else if (arg.startsWith(PORT_KEY)) {
                try {
                    serverPort = Integer.parseInt(arg.substring(PORT_KEY.length()));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            }
        }

        if (this.serverHostname == null) {
            this.serverHostname = CliHelper.inputString("Enter the server hostname", 0, 0);
        }

        if (this.serverPort <= 0) {
            this.serverPort = CliHelper.inputInt("Enter the server port", 2222, 8888);
        }

        this.connection = new Connection(this.serverHostname, this.serverPort);
    }

    public ClientApp(String serverHostname, int serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
        this.connection = new Connection(this.serverHostname, this.serverPort);
    }

    public Connection getConnection() {
        return connection;
    }

    public void run() {
        Logger.info("Started new ClientApp");

        while (!connection.isConnected()) {
            connection.start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }

        // Open the listening thread
        listeningThread = new ListeningThread(this);
        listeningThread.start();

        // Prompt for a message
        while (connection.isConnected()) {
            // Take the client input

            String input = CliHelper.inputString(null, 0, 0);
            Message message = CTS_Message.CreateMessage(input);
            try {
                Message.sendMessageToSocket(connection.getOut(), message);
            } catch (IOException e) {
                Logger.warn("Failed to send message to server e=" + e.getMessage());
            }
        }
    }

    public void onMessageReceived(Message message) {
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
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}
