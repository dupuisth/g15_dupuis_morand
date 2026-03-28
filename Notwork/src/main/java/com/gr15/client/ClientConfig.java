package com.gr15.client;

import com.gr15.cli.CliHelper;
import com.gr15.common.ClientId;
import com.gr15.server.ServerConfig;

import java.util.ArrayList;

public class ClientConfig {
    public static final String ARG_SERVER_HOSTNAME_KEY = "hostname=";
    public static final String ARG_SERVER_PORT_KEY = "port=";

    public static final String ARG_COMPACT_KEY = "client=";

    private String serverHostname;
    private Integer serverPort;

    public ClientConfig() {
        serverHostname = null;
        serverPort = null;
    }

    public ClientConfig(String serverHostname, Integer serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
    }

    public boolean validateConfiguration() {
        if (serverHostname == null || serverHostname.isEmpty()) {
            return false;
        }

        if (serverPort == null || serverPort < ServerConfig.PORT_MIN || serverPort > ServerConfig.PORT_MAX) {
            return false;
        }

        return true;
    }

    public static ClientConfig FromCli() {
        String serverHostname = CliHelper.inputString("Enter server hostname", 0, 0);
        int serverPort = CliHelper.inputInt("Enter server port", ServerConfig.PORT_MIN, ServerConfig.PORT_MAX);

        return new ClientConfig(serverHostname, serverPort);
    }

    public static ClientConfig FromArgs(String[] args) {
        ClientConfig config = new ClientConfig ();

        // Read the args to get configuration from it
        for (String arg : args) {
            if (arg.startsWith(ARG_SERVER_HOSTNAME_KEY)) {
                config.setServerHostname(arg.substring(ARG_SERVER_HOSTNAME_KEY.length()));
            } else if (arg.startsWith(ARG_SERVER_PORT_KEY)) {
                try {
                    config.setServerPort(Integer.parseInt(arg.substring(ARG_SERVER_PORT_KEY.length())));
                } catch (NumberFormatException e) {
                    // Do nothing, let it fail
                }
            }
        }

        return config;
    }

    public static ClientConfig FromCompactArgs(String arg) {
        // Form : COMPACT_KEY=HOSTNAME:PORT
        String dataString = arg.substring(ARG_COMPACT_KEY.length());

        int separator = dataString.indexOf(':');
        if (separator == -1) {
            throw new IllegalArgumentException("Bad format for client argument arg=" + arg);
        }

        ClientConfig config = new ClientConfig();
        try {
            config.setServerHostname(dataString.substring(0, separator));
            config.setServerPort(Integer.parseInt(dataString.substring(separator + 1)));

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid format for client argument arg=" + arg);
        }
        return config;
    }

    public ArrayList<String> toArgs() {
        ArrayList<String> args = new ArrayList<>();
        args.add(ARG_SERVER_HOSTNAME_KEY +  serverHostname);
        args.add(ARG_SERVER_PORT_KEY + serverPort);
        return args;
    }

    public String getServerHostname() {
        return serverHostname;
    }

    public void setServerHostname(String serverHostname) {
        this.serverHostname = serverHostname;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public String toString() {
        return "ClientConfig{" +
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }

}
