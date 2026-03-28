package com.gr15;


import com.gr15.admin.AdminApp;
import com.gr15.cli.CliHelper;
import com.gr15.client.ClientApp;
import com.gr15.server.ServerApp;
import com.gr15.server.ServerConfig;

import java.util.ArrayList;
import java.util.List;

public class Application {
    public static final String ADMIN_KEY = "admin";
    public static final String SERVER_KEY = "server";
    public static final String CLIENT_KEY = "client";
    public static final String Logger_KEY = "Logger";

    static ApplicationType selectApplicationType() {
        List<String> choices = new ArrayList<>();
        choices.add("Admin");
        choices.add("Server");
        choices.add("Client");
        int result = CliHelper.selectChoices("Select an application", choices);

        if (result == 0) {
            return ApplicationType.Admin;
        } else if (result == 1) {
            return ApplicationType.Server;
        } else {
            return ApplicationType.Client;
        }
    }

    static void main(String[] args) {
        // Check the arguments to check if there is any matches
        ApplicationType applicationType = null;

        for (String arg : args) {
            if (arg.compareToIgnoreCase(ADMIN_KEY) == 0) {
                applicationType = ApplicationType.Admin;
            } else if (arg.compareToIgnoreCase(SERVER_KEY) == 0) {
                applicationType = ApplicationType.Server;
            } else if (arg.compareToIgnoreCase(CLIENT_KEY) == 0) {
                applicationType = ApplicationType.Client;
            }
        }

        // Not defined via arguments
        if (applicationType == null) {
            applicationType = selectApplicationType();
        }

        // Launch the desired application
        switch (applicationType) {
            case Admin -> {
                AdminApp app = new AdminApp(args);
                app.run();
            }

            case Server -> {
                ServerConfig serverConfig = ServerConfig.FromArgs(args);
                ServerApp app = new ServerApp(serverConfig);
                app.run();
            }

            case Client -> {
                ClientApp app = new ClientApp(args);
                app.run();
            }
        }
    }

    private enum ApplicationType {
        Admin,
        Server,
        Client,
    }
}
