package com.gr15.client;

import com.gr15.cli.CliHelper;
import com.gr15.common.*;
import com.gr15.common.message.CTS_Message;
import com.gr15.common.message.MessageSTC;
import com.gr15.common.message.STC_Message;

import java.io.IOException;
import java.util.logging.Logger;

public class ClientApp {
    public static final String HOSTNAME_KEY = "hostname=";
    public static final String PORT_KEY = "port=";

    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());

    private String serverHostname;
    private int serverPort;
    private Connection connection;

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
        LOGGER.info("Started new ClientApp");

        while (!connection.isConnected()) {
            connection.start();

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }

        // Open the listening thread
        // TODO : Keep a pointer on this
        ListeningThread listeningThread = new ListeningThread(this);
        listeningThread.start();

        // Prompt for a message
        while (connection.isConnected()) {
            // Take the client input

            String input = CliHelper.inputString("What do you want to say ?", 0, 0);
            Message message = CTS_Message.CreateMessage(input);
            try {
                Message.sendMessageToSocket(connection.getOut(), message);
            } catch (IOException e) {
                LOGGER.warning("Failed to send message to server e=" + e.getMessage());
            }
        }
    }

    public void onMessageReceived(Message message) {
        LOGGER.fine("New message received : length=" + message.getData().length + " data=" + message.getDataAsBitsInString());

        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageSTC messageType = MessageSTC.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case HELLO -> {
            }
            case MESSAGE -> {
                STC_Message parsedMessage = STC_Message.ReadMessage(message);
                handleMessage(parsedMessage);
            }
            case null -> {
                LOGGER.warning("Unknown message type, ignoring it (id=" + messageId + ")");
            }
        }
    }

    public void handleMessage(STC_Message message) {
        LOGGER.info(message.toString());
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}
