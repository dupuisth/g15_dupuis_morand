package com.gr15.admin;

import com.gr15.cli.CliHelper;
import com.gr15.client.ClientConfig;
import com.gr15.server.ServerConfig;
import com.gr15.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class AdminApp {
    public static final String CLIENT_KEY = "client=";

    private final ClientManager clientManager;
    private final ServerManager serverManager;

    private final String[] args;

    public AdminApp(String[] args) {
        clientManager = new ClientManager();
        serverManager = new ServerManager();

        this.args = args;
    }


    private void list() {
        System.out.println("Servers:");
        List<Process> servers = serverManager.getServers();
        for (Process server : servers) {
            System.out.println(server);
        }

        System.out.println("Clients:");
        List<Process> clients = clientManager.getClients();
        for (Process client : clients) {
            System.out.println(client.info().toString());
        }
    }

    private void menu() {
        while (true) {
            CliHelper.clear();

            List<String> choices = new ArrayList<>();
            choices.add("Add a new Server");
            choices.add("Add a new Client");
            choices.add("List devices");
            choices.add("Exit");

            int option = CliHelper.selectChoices("", choices);

            if (option == 0) {
                serverManager.addServer(ServerConfig.FromCli());
            } else if (option == 1) {
                clientManager.addClient(ClientConfig.FromCli());
            } else if (option == 2) {
                // List all the devices
                list();
            } else {
                // Exit
                break;
            }

        }
    }

    public void run() {
        // If there were argument to create server / clients, handle them
        for (String arg : args) {
            try {
                if (arg.startsWith(ServerConfig.ARG_COMPACT_KEY)) {
                    ServerConfig serverConfig = ServerConfig.FromCompactArgs(arg);
                    serverManager.addServer(serverConfig);
                }
                else if (arg.startsWith(ClientConfig.ARG_COMPACT_KEY)) {
                    ClientConfig clientConfig = ClientConfig.FromCompactArgs(arg);
                    clientManager.addClient(clientConfig);
                }
            } catch (Exception e) {
                Logger.error("Exception while parsing argument e=" + e.getMessage(), e);
            }

        }

        menu();
    }
}
