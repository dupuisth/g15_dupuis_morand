package com.gr15.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.logging.Logger;

public class Connection {
    private static final Logger LOGGER = Logger.getLogger(Connection.class.getName());

    private Socket socket;
    private SocketAddress socketAddress;
    private PrintWriter out;
    private BufferedReader in;

    public Connection(String serverHostname, int port) {
        this.socket = new Socket();
        this.socketAddress = new InetSocketAddress(serverHostname, port);
    }

    public boolean start() {
        try {
            LOGGER.info("Trying to connect to inet=" + socketAddress);
            socket.connect(socketAddress);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            LOGGER.warning("Failed to create the client socket " + e.getMessage());
            return false;
        }

        LOGGER.info("Connected to server !");
        return true;
    }

    public BufferedReader getIn() {
        return in;
    }

    public PrintWriter getOut() {
        return out;
    }

    public boolean isConnected() {
        return socket.isConnected();
    }
}
