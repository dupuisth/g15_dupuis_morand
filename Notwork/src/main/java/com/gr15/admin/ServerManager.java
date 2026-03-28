package com.gr15.admin;

import com.gr15.Application;
import com.gr15.server.ServerApp;
import com.gr15.server.ServerConfig;
import com.gr15.utils.Logger;
import com.gr15.utils.ProcessUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServerManager {

    private final List<Process> servers;

    public ServerManager() {
        servers = new ArrayList<>();
    }

    public void addServer(ServerConfig config) {
        java.util.ArrayList<String> arguments = new ArrayList<>();
        arguments.add(Application.SERVER_KEY);
        arguments.addAll(config.toArgs());

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(arguments);
            servers.add(process);
            Logger.info("Created a new process");
        } catch (IOException e) {
            Logger.warn("Failed to create the client process");
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
