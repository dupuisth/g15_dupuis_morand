package com.gr15.admin;

import com.gr15.Application;
import com.gr15.admin.console.AdminConsoleConfig;
import com.gr15.client.ClientConfig;
import com.gr15.utils.Logger;
import com.gr15.utils.ProcessUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AdminConsoleManager {
    private final List<AdminConsoleConfig> adminConsoles;

    public AdminConsoleManager() {
        adminConsoles = new ArrayList<>();
    }

    public void add(AdminConsoleConfig config) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add(Application.ADMIN_KEY);
        arguments.add(config.toCompactArgs());

        try {
            Process process = ProcessUtils.startApplicationInNewTerminal(arguments);
            adminConsoles.add(config);
            Logger.info("Created a new process");
        } catch (IOException e) {
            Logger.warn("Failed to create the client process");
        }
    }

    public List<AdminConsoleConfig> getAdminConsoles() {
        return adminConsoles;
    }
}
