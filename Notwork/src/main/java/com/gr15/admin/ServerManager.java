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

    private final List<ServerConfig> servers;

    public ServerManager() {
        servers = new ArrayList<>();
    }

    public void addServer(ServerConfig config) {
        java.util.ArrayList<String> arguments = new ArrayList<>();
        arguments.add(Application.SERVER_KEY);
        arguments.addAll(config.toArgs());

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(arguments);
            servers.add(config);
            Logger.info("Created a new process");
        } catch (IOException e) {
            Logger.warn("Failed to create the client process");
        }
    }


    public List<ServerConfig> getServers() {
        return servers;
    }
}
