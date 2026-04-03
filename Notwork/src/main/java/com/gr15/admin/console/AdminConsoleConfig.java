package com.gr15.admin.console;

import com.gr15.cli.CliHelper;
import com.gr15.server.ServerConfig;

public class AdminConsoleConfig {
    public static final String ARG_COMPACT_KEY = "console=";

    private String serverHostname;
    private Integer serverPort;

    public AdminConsoleConfig() {
        serverHostname = null;
        serverPort = null;
    }

    public AdminConsoleConfig(String serverHostname, Integer serverPort) {
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

    public static AdminConsoleConfig FromCli() {
        String serverHostname = CliHelper.inputString("Enter server hostname", 0, 0);
        int serverPort = CliHelper.inputInt("Enter server port", ServerConfig.PORT_MIN, ServerConfig.PORT_MAX);

        return new AdminConsoleConfig(serverHostname, serverPort);
    }

    public static AdminConsoleConfig FromCompactArgs(String arg) {
        // Form : COMPACT_KEY=HOSTNAME:PORT
        String dataString = arg.substring(ARG_COMPACT_KEY.length());

        int separator = dataString.indexOf(':');
        if (separator == -1) {
            throw new IllegalArgumentException("Bad format for admin console argument arg=" + arg);
        }

        AdminConsoleConfig config = new AdminConsoleConfig();
        try {
            config.setServerHostname(dataString.substring(0, separator));
            config.setServerPort(Integer.parseInt(dataString.substring(separator + 1)));

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid format for admin console argument arg=" + arg);
        }
        return config;
    }

    public String toCompactArgs() {
        return ARG_COMPACT_KEY + serverHostname + ":" + serverPort;
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
        return "AdminConsoleConfig{" +
                "serverHostname='" + serverHostname + '\'' +
                ", serverPort=" + serverPort +
                '}';
    }
}
