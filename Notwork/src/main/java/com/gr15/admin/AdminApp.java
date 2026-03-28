package com.gr15.admin;

import com.gr15.cli.CliHelper;
import com.gr15.server.ServerConfig;
import com.gr15.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class AdminApp {
    public static final String CLIENT_KEY = "client=";

    private final ClientManager clientManager;
    private final ServerManager serverManager;

    private String[] args;

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
                // Add a new Client
                String serverAddress = CliHelper.inputString("Enter serverAddress", 0, 0);
                int serverPort = CliHelper.inputInt("Enter port", null, null);
                clientManager.addClient(serverAddress, serverPort);
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
                else if (arg.startsWith(CLIENT_KEY)) {
                    // Form : client=HOSTNAME:PORT
                    String dataString = arg.substring(CLIENT_KEY.length());

                    int separator = dataString.indexOf(':');
                    if (separator == -1) {
                        throw new IllegalArgumentException("Bad format for client argument arg=" + arg);
                    }

                    String serverHostname = dataString.substring(0, separator);
                    int serverPort = Integer.parseInt(dataString.substring(separator + 1));
                    clientManager.addClient(serverHostname, serverPort);
                }
            } catch (Exception e) {
                Logger.error("Exception while parsing argument e=" + e.getMessage(), e);
            }

        }

        menu();
    }
}
