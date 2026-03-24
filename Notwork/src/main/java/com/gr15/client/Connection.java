package com.gr15.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.logging.Logger;

public class Connection {
    private static final Logger LOGGER = Logger.getLogger(Connection.class.getName());

    private Socket socket;
    private SocketAddress socketAddress;

    public Connection(String serverHostname, int port) {
        this.socket = new Socket();
        this.socketAddress = new InetSocketAddress(serverHostname, port);
    }

    public void start() {
        try {
            socket.connect(socketAddress);
        } catch (IOException e) {
            LOGGER.warning("Failed to create the client socket " + e.getMessage());
        }

        LOGGER.info("Connected to server !");

        while (true)
        {
            try {
                Thread.sleep(1000);

            } catch (Exception e)
            {

            }
        }
    }
}
