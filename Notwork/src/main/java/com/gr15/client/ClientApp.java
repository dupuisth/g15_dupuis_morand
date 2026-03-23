package com.gr15.client;

import com.gr15.cli.CliHelper;

import java.util.logging.Logger;

public class ClientApp {
    public static final String HOSTNAME_KEY = "hostname=";
    public static final String PORT_KEY = "port=";

    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());

    private String serverHostname;
    private int serverPort;

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
    }

    public ClientApp(String serverHostname, int serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
    }

    public void run() {
        LOGGER.info("Started new ClientApp");
    }

    @Override
    public String toString() {
        return "ClientApp{" +
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}
