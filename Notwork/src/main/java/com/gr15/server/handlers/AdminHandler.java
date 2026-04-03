package com.gr15.server.handlers;

import com.gr15.server.ServerApp;
import com.gr15.server.connections.AdminConnection;
import com.gr15.utils.Logger;
import com.gr15.utils.ThreadUtils;

/**
 * Thread created when an admin connects to the server, listen for message from the admin
 */
public class AdminHandler extends Thread {
    private final AdminConnection adminConnection;
    private final ServerApp server;

    private volatile boolean shouldStop = false;

    public AdminHandler(AdminConnection adminConnection, ServerApp server) {
        this.adminConnection = adminConnection;
        this.server = server;
    }

    @Override
    public void run() {
        super.run();

        // Do something later on, maybe implement the ping-pong stuff...
        while (!shouldStop && adminConnection.isConnected()) {
            if (!ThreadUtils.safeSleep(1000)) {
                break;
            }
        }

        Logger.info("Stopped Admin handler");
    }

    public void setShouldStop() {
        shouldStop = true;
        this.interrupt();
    }
}
