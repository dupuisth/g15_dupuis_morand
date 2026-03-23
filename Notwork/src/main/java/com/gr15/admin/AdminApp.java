package com.gr15.admin;

import com.gr15.cli.CliHelper;

import java.util.ArrayList;
import java.util.List;

public class AdminApp {
    private final ClientManager clientManager;
    private final ServerManager serverManager;

    public AdminApp(String[] args) {
        clientManager = new ClientManager();
        serverManager = new ServerManager();
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
                // Add a new Server
                int serverId = CliHelper.inputInt("Enter serverId", 0, 20);
                int port = CliHelper.inputInt("Enter port", 2222, 8888);
                serverManager.addServer(serverId, port);
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
        menu();
    }
}
