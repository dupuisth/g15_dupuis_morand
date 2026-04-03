package com.gr15.admin.console;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.ats.*;
import com.gr15.common.message.sta.MessageSTA;
import com.gr15.common.message.stc.*;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminConsole {
    private AdminConsoleConfig config;

    private Connection connection;

    private int clientId;

    public AdminConsole(AdminConsoleConfig config) {
        this.config = config;
        if (!config.validateConfiguration()) {
            throw new IllegalArgumentException("Invalid AdminConfig: " + config);
        }

        try {
            // Should never throw since we do not connect directly
            this.connection = new Connection(config.getServerHostname(), config.getServerPort(), false);
        } catch (IOException e) {
            Logger.error("Exception while creating new connection", e);
            throw new IllegalStateException("Failed to create the connection");
        }
    }

    public void run() {
        Logger.info("Started the AdminConsole " + config);

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
        Logger.info("Connected to server");

        // Open the listening thread
        ListeningThread<Connection> listenThread = new ListeningThread<>(connection, this::onMessageReceived, this::onCriticalListeningError);
        listenThread.start();

        List<MessageATS> actions = Arrays.asList(MessageATS.values());
        List<String> actionsAsString = new ArrayList<>();
        for (MessageATS action : actions) {
            actionsAsString.add(action.toString());
        }

        // Prompt for a message
        while (connection.isConnected()) {
            // Take the input
            // THIS WILL BLOCK UNTIL THE USER PRESS ENTER, SO, IF A DISCONNECTION HAPPEN
            // THE USER WILL NOT BE NOTIFIED UNTIL THIS END
            // todo: FIX THIS LATER
            int option = CliHelper.selectChoices("Select an action", actionsAsString);
            MessageATS action = MessageATS.fromId(option);

            switch (action) {
                case null -> {}
                case LIST_NEIGHBORS -> {
                    // To implement later
                }
                case ADD_NEIGHBOR -> {
                    String hostname = CliHelper.inputString("Hostname", 0, 0);
                    int port = CliHelper.inputInt("Port", null, null);
                    connection.safeSend(ATS_AddNeighbor.CreateMessage(hostname, port));
                }
                case REMOVE_NEIGHBOR -> {
                    String hostname = CliHelper.inputString("Hostname", 0, 0);
                    int port = CliHelper.inputInt("Port", null, null);
                    connection.safeSend(ATS_RemoveNeighbor.CreateMessage(hostname, port));
                }
                case STOP -> {
                    connection.safeSend(ATS_Stop.CreateMessage());
                }
                case RESET -> {
                    connection.safeSend(ATS_Reset.CreateMessage());
                }
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

        return true;
    }

    public void onMessageReceived(Connection connection, Message message) {
        Logger.debug("New message received : length=" + message.getData().length + " data=" + message.getDataAsBitsInString());

        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageSTA messageType = MessageSTA.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case null -> {
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
            case LIST_NEIGHBOR -> {
            }
        }
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "connection=" + connection +
                ", clientId=" + clientId +
                '}';
    }
}
