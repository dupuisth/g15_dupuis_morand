package com.gr15.admin.console;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.common.listening.ListeningThread;
import com.gr15.common.message.ats.*;
import com.gr15.common.message.sta.MessageSTA;
import com.gr15.common.message.sta.STA_ListConnections;
import com.gr15.common.message.sta.STA_ListNeighbor;
import com.gr15.common.message.sta.STA_ListTopology;
import com.gr15.common.message.stc.*;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.gr15.common.Constants.MAX_CLIENTS;
import static com.gr15.common.Constants.MAX_SERVERS;

public class AdminConsole {
    private static final int RESPONSE_TIMEOUT_SECONDS = 5;

    private AdminConsoleConfig config;

    private Connection connection;

    private int clientId;

    private final BlockingQueue<Message> receivedMessages = new LinkedBlockingQueue<>();

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

        List<MessageATS> actions = Arrays.asList(
                MessageATS.LIST_TOPOLOGY,
                MessageATS.LIST_CONNECTIONS,
                MessageATS.LIST_NEIGHBOR,
                MessageATS.ADD_NEIGHBOR,
                MessageATS.REMOVE_NEIGHBOR,
                MessageATS.RESET,
                MessageATS.STOP
        );
        List<String> actionsAsString = new ArrayList<>();
        for (MessageATS action : actions) {
            actionsAsString.add(formatAction(action));
        }

        // Prompt for a message
        while (connection.isConnected()) {
            // Take the input
            // THIS WILL BLOCK UNTIL THE USER PRESS ENTER, SO, IF A DISCONNECTION HAPPEN
            // THE USER WILL NOT BE NOTIFIED UNTIL THIS END
            // todo: FIX THIS LATER
            int option = CliHelper.selectChoices("Select an action", actionsAsString);
            MessageATS action = actions.get(option);

            switch (action) {
                case null -> {}
                case LIST_NEIGHBOR -> {
                    receivedMessages.clear();
                    if (connection.safeSend(ATS_ListNeighbor.CreateMessage())) {
                        waitAndHandleResponse();
                    }
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
                case LIST_CONNECTIONS -> {
                    receivedMessages.clear();
                    if (connection.safeSend(ATS_ListConnections.CreateMessage())) {
                        waitAndHandleResponse();
                    }
                }
                case LIST_TOPOLOGY -> {
                    receivedMessages.clear();
                    if (connection.safeSend(ATS_ListTopology.CreateMessage())) {
                        waitAndHandleResponse();
                    }
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
        receivedMessages.add(message);
    }

    private void waitAndHandleResponse() {
        try {
            Message message = receivedMessages.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (message == null) {
                CliHelper.show("No response received from server.");
                return;
            }

            handleResponse(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CliHelper.show("Interrupted while waiting for server response.");
        }
    }

    private void handleResponse(Message message) {
        // Read the message header
        int messageId = message.readInt(Message.MESSAGE_ID_BITS);
        MessageSTA messageType = MessageSTA.fromId(messageId);

        // Handle each cases
        switch (messageType) {
            case null -> {
                Logger.warn("Unknown message type, ignoring it (id=" + messageId + ")");
            }
            case LIST_NEIGHBOR -> {
                STA_ListNeighbor listNeighbor = STA_ListNeighbor.ReadMessage(message);
                List<STA_ListNeighbor.NeighborInfo> neighbors = listNeighbor.getNeighbors();
                if (neighbors.isEmpty()) {
                    CliHelper.show("No neighbors configured.");
                    return;
                }

                CliHelper.show("Configured neighbors:");
                for (STA_ListNeighbor.NeighborInfo neighbor : neighbors) {
                    String serverId = neighbor.serverId() == null ? "unknown" : neighbor.serverId().toString();
                    CliHelper.show("- serverId=" + serverId
                            + " hostname=" + neighbor.serverHostname()
                            + " port=" + neighbor.serverPort());
                }
            }
            case LIST_CONNECTIONS -> {
                STA_ListConnections listConnections = STA_ListConnections.ReadMessage(message);
                List<STA_ListConnections.ConnectionInfo> connections = listConnections.getConnections();
                if (connections.isEmpty()) {
                    CliHelper.show("No active connections.");
                    return;
                }

                CliHelper.show("Current connections:");
                for (STA_ListConnections.ConnectionInfo connection : connections) {
                    String type = connection.type() == null ? "UNKNOWN" : connection.type().toString();
                    String id = formatConnectionId(connection);
                    String status = connection.connected() ? "connected" : "disconnected";
                    CliHelper.show("- type=" + type
                            + " id=" + id
                            + " hostname=" + connection.hostname()
                            + " port=" + connection.port()
                            + " status=" + status);
                }
            }
            case LIST_TOPOLOGY -> {
                STA_ListTopology listTopology = STA_ListTopology.ReadMessage(message);
                List<STA_ListTopology.ServerTopologyInfo> servers = listTopology.getServers();
                if (servers.isEmpty()) {
                    CliHelper.show("No topology information known.");
                    return;
                }

                displayKnownTopology(servers);
            }
        }
    }

    private void displayKnownTopology(List<STA_ListTopology.ServerTopologyInfo> servers) {
        Set<Integer> knownServers = new TreeSet<>();
        Set<String> serverConnections = new TreeSet<>();

        for (STA_ListTopology.ServerTopologyInfo server : servers) {
            knownServers.add(server.serverId());
            for (int neighborId = 0; neighborId < MAX_SERVERS; neighborId++) {
                if (((server.neighborMask() >> neighborId) & 1) == 0) {
                    continue;
                }

                knownServers.add(neighborId);
                int first = Math.min(server.serverId(), neighborId);
                int second = Math.max(server.serverId(), neighborId);
                serverConnections.add(first + " <-> " + second);
            }
        }

        CliHelper.show("Known network topology:");
        CliHelper.show("Servers: " + formatIntegerSet(knownServers));

        CliHelper.show("Server connections:");
        if (serverConnections.isEmpty()) {
            CliHelper.show("- none");
        } else {
            for (String connection : serverConnections) {
                CliHelper.show("- " + connection);
            }
        }

        CliHelper.show("Clients:");
        boolean hasClient = false;
        for (STA_ListTopology.ServerTopologyInfo server : servers) {
            String clients = formatClientMask(server.serverId(), server.clientMask());
            if (!clients.equals("[]")) {
                hasClient = true;
                CliHelper.show("- serverId=" + server.serverId() + " clients=" + clients);
            }
        }
        if (!hasClient) {
            CliHelper.show("- none");
        }
    }

    private String formatAction(MessageATS action) {
        return switch (action) {
            case LIST_TOPOLOGY -> "Show known topology";
            case LIST_CONNECTIONS -> "Show active connections";
            case LIST_NEIGHBOR -> "Show configured neighbors";
            case ADD_NEIGHBOR -> "Add configured neighbor";
            case REMOVE_NEIGHBOR -> "Remove configured neighbor";
            case RESET -> "Reset runtime connections";
            case STOP -> "Stop server";
        };
    }

    private String formatConnectionId(STA_ListConnections.ConnectionInfo connection) {
        if (connection.id() == null) {
            return "pending";
        }

        if (connection.type() == STA_ListConnections.ConnectionType.CLIENT) {
            return ClientId.toString(connection.id());
        }

        return connection.id().toString();
    }

    private String formatClientMask(int serverId, int clientMask) {
        List<String> clientIds = new ArrayList<>();
        for (int localId = 0; localId < MAX_CLIENTS; localId++) {
            if (((clientMask >> localId) & 1) == 1) {
                clientIds.add(ClientId.toString(ClientId.Create(serverId, localId)));
            }
        }
        return clientIds.isEmpty() ? "[]" : "[" + String.join(", ", clientIds) + "]";
    }

    private String formatIntegerSet(Set<Integer> values) {
        List<String> strings = new ArrayList<>();
        for (Integer value : values) {
            strings.add(value.toString());
        }
        return strings.isEmpty() ? "[]" : "[" + String.join(", ", strings) + "]";
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "connection=" + connection +
                ", clientId=" + clientId +
                '}';
    }
}
