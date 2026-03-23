package com.gr15.admin;

import com.gr15.Application;
import com.gr15.client.ClientApp;
import com.gr15.utils.ProcessUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ClientManager {
    private static final Logger LOGGER = Logger.getLogger(ClientManager.class.getName());


    private final List<Process> clients;

    public ClientManager() {
        clients = new ArrayList<>();
    }

    public void addClient(String hostname, int port) {
        StringBuilder argumentBuilder = new StringBuilder(Application.CLIENT_KEY);
        if (hostname != null) {
            argumentBuilder.append(" ");
            argumentBuilder.append(ClientApp.HOSTNAME_KEY);
            argumentBuilder.append(hostname);
        }

        if (port >= 0) {
            argumentBuilder.append(" ");
            argumentBuilder.append(ClientApp.PORT_KEY);
            argumentBuilder.append(port);
        }

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(argumentBuilder.toString());
            clients.add(process);
            LOGGER.info("Created a new process");
        } catch (IOException e) {
            LOGGER.warning("Failed to create the client process");
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
