package com.gr15.admin;

import com.gr15.Application;
import com.gr15.client.ClientConfig;
import com.gr15.utils.Logger;
import com.gr15.utils.ProcessUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-side process launcher for client applications.
 */
public class ClientManager {
    private final List<ClientConfig> clients;

    public ClientManager() {
        clients = new ArrayList<>();
    }

    public void addClient(ClientConfig clientConfig) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add(Application.CLIENT_KEY);
        arguments.addAll(clientConfig.toArgs());

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(arguments);
            clients.add(clientConfig);
            Logger.info("Created a new process");
        } catch (IOException e) {
            Logger.warn("Failed to create the client process");
        }
    }

    public List<ClientConfig> getClients() {
        return clients;
    }
}
