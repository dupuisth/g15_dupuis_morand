package com.gr15.client;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class Connection {
    private static final Logger LOGGER = Logger.getLogger(Connection.class.getName());

    private Socket socket;
    private SocketAddress socketAddress;
    private DataOutputStream out;
    private DataInputStream in;

    public Connection(String serverHostname, int port) {
        this.socketAddress = new InetSocketAddress(serverHostname, port);
    }

    public boolean start() {
        try {
            LOGGER.info("Trying to connect to inet=" + socketAddress);
            this.socket = new Socket();
            this.socket.connect(socketAddress);
            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.warning("Failed to create the client socket " + e.getMessage());
            return false;
        }

        LOGGER.info("Connected to server !");
        return true;
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.warning("Exception while closing, e=" + e.getMessage());
        }
    }

    public DataInputStream getIn() {
        return in;
    }

    public DataOutputStream getOut() {
        return out;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
