package com.gr15.client;

import com.gr15.cli.CliHelper;
import com.gr15.common.Message;

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

        }
    }

    public void onMessageReceived(Message message) {
        LOGGER.info("New message received : \nlength=" + message.getData().length + "\ndata=" + message.getDataAsBitsInString());

        byte messageId = message.ReadByte(Message.MESSAGE_ID_BITS);
        LOGGER.info("MessageId="+messageId + " int="+ message.ReadByte(5));
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}
