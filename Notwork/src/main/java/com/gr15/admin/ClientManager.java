package com.gr15.admin;

import com.gr15.Application;
import com.gr15.client.ClientApp;
import com.gr15.client.ClientConfig;
import com.gr15.utils.Logger;
import com.gr15.utils.ProcessUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientManager {
    private final List<Process> clients;

    public ClientManager() {
        clients = new ArrayList<>();
    }

    public void addClient(ClientConfig clientConfig) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add(Application.CLIENT_KEY);
        arguments.addAll(clientConfig.toArgs());

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(arguments);
            clients.add(process);
            Logger.info("Created a new process");
        } catch (IOException e) {
            Logger.warn("Failed to create the client process");
        }
    }

    /**
     * Return a COPY of the client list (this copy is only valid for a short period, any modifications on the real list will not propagate to this one
     */
    public List<Process> getClients() {
        // Create a copy, so that we prevent all async errors
        List<Process> result;
        synchronized (clients) {
            result = new ArrayList<>(clients);
        }
        return result;
    }
}
