package com.gr15.admin;

import com.gr15.admin.console.AdminConsole;
import com.gr15.admin.console.AdminConsoleConfig;
import com.gr15.cli.CliHelper;
import com.gr15.client.ClientConfig;
import com.gr15.common.Constants;
import com.gr15.server.ServerConfig;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

import java.util.ArrayList;
import java.util.List;

public class AdminApp {
    private final ClientManager clientManager;
    private final ServerManager serverManager;
    private final AdminConsoleManager adminConsoleManager;

    private final String[] args;

    public AdminApp(String[] args) {
        clientManager = new ClientManager();
        serverManager = new ServerManager();
        adminConsoleManager = new AdminConsoleManager();

        this.args = args;
    }

    private void menu() {
        while (true) {
            CliHelper.clear();

            List<String> choices = new ArrayList<>();
            choices.add("Add a new Server");
            choices.add("Add a new Client");
            choices.add("Add a new Admin");
            choices.add("Exit");

            int option = CliHelper.selectChoices("", choices);

            if (option == 0) {
                serverManager.addServer(ServerConfig.FromCli());
            } else if (option == 1) {
                clientManager.addClient(ClientConfig.FromCli());
            } else if (option == 2) {
                addAdmin();
            } else {
                // Exit
                break;
            }

        }
    }

    private void addAdmin() {
        List<ServerConfig> servers = serverManager.getServers();

        List<String> choices = new ArrayList<>();
        for (ServerConfig serverConfig : servers) {
            choices.add("ServerId " + serverConfig.getServerId() + "(" + serverConfig.getClientSocketPort() + ":" + serverConfig.getServerSocketPort()  + ":" + serverConfig.getAdminSocketPort() +")");
        }
        choices.add("Return");

        int option = CliHelper.selectChoices("Select the server", choices);

        if (option >= choices.size()) {
            return;
        }

        ServerConfig serverConfig = servers.get(option);
        AdminConsoleConfig consoleConfig = new AdminConsoleConfig("127.0.0.1", serverConfig.getAdminSocketPort());
        adminConsoleManager.add(consoleConfig);
    }

    public void run() {
        // If the app should start as console, then do it

        // Contains a value if the admin should be a console
        AdminConsoleConfig consoleConfig = null;
        for (String arg : args) {
            try {
                if (arg.startsWith(AdminConsoleConfig.ARG_COMPACT_KEY)) {
                    consoleConfig = AdminConsoleConfig.FromCompactArgs(arg);
                }
            } catch (Exception e) {
                Logger.error("Exception while parsing argument e=" + e.getMessage(), e);
            }
        }
        // If it should be a console, then be it
        if (consoleConfig != null) {
            AdminConsole console = new AdminConsole(consoleConfig);
            console.run();
            // Exit right after
            return;
        }


        // If there are argument to create server / clients, handle them
        for (String arg : args) {
            try {
                if (arg.startsWith(ServerConfig.ARG_COMPACT_KEY)) {
                    ServerConfig serverConfig = ServerConfig.FromCompactArgs(arg);
                    serverManager.addServer(serverConfig);
                    Logger.info(serverConfig.toString());

                    // Create an admin console for each server
                    AdminConsoleConfig adminConsoleConfig = new AdminConsoleConfig("127.0.0.1", serverConfig.getAdminSocketPort());
                    adminConsoleManager.add(adminConsoleConfig);
                }
                else if (arg.startsWith(ClientConfig.ARG_COMPACT_KEY)) {
                    ClientConfig clientConfig = ClientConfig.FromCompactArgs(arg);
                    clientManager.addClient(clientConfig);
                    Logger.info(clientConfig.toString());
                }
            } catch (Exception e) {
                Logger.error("Exception while parsing argument e=" + e.getMessage(), e);
            }
        }


        menu();
    }
}
