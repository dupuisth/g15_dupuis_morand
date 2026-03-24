package com.gr15.client;

import com.gr15.cli.CliHelper;

import java.util.logging.Logger;

public class ClientApp {
    public static final String HOSTNAME_KEY = "hostname=";
    public static final String PORT_KEY = "port=";

    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());

    private String serverHostname;
    private int serverPort;
    private Connection connection;

    public Connection getConnection() {
        return connection;
    }

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

    public void run() {
        LOGGER.info("Started new ClientApp");

        while (!connection.isConnected()) {
            connection.start();
        }

        // Open the listening thread
        // TODO : Keep a pointer on this
        ListeningThread listeningThread = new ListeningThread(this);
        listeningThread.start();

        // Send a message
        for (int i = 0; i < 15; i++) {
            connection.getOut().println("Le C4C4 (" + i + ")");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }
            
        }
    }

    public void onMessageReceived(String message)
    {
        LOGGER.info("Message received : " + message);
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}
