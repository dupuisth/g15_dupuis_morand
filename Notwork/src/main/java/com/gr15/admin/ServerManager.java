package com.gr15.admin;

import com.gr15.Application;
import com.gr15.server.ServerApp;
import com.gr15.utils.ProcessUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ServerManager {
    private static final Logger LOGGER = Logger.getLogger(ServerManager.class.getName());

    private final List<Process> servers;

    public ServerManager() {
        servers = new ArrayList<>();
    }

    public void addServer(int serverId, int serverPort) {
        java.util.ArrayList<String> arguments = new ArrayList<>();
        arguments.add(Application.SERVER_KEY);

        if (serverId > 0) {
            arguments.add(ServerApp.SERVER_ID_KEY + serverId);
        }

        if (serverPort > 0) {
            arguments.add(ServerApp.SERVER_PORT_KEY + serverPort);
        }

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(arguments);
            servers.add(process);
            LOGGER.info("Created a new process");
        } catch (IOException e) {
            LOGGER.warning("Failed to create the client process");
        }
    }

    /**
     * Return a COPY of the servers list (this copy is only valid for a short period, any modifications on the real list will not propagate to this one
     */
    public List<Process> getServers() {
        // Create a copy, so that we prevent all async errors
        List<Process> result;
        synchronized (servers) {
            result = new ArrayList<>(servers);
        }
        return result;
    }
}
