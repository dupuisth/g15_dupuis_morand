package com.gr15.client;

import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class Connection {
    private Socket socket;
    private SocketAddress socketAddress;
    private DataOutputStream out;
    private DataInputStream in;

    public Connection(String serverHostname, int port) {
        this.socketAddress = new InetSocketAddress(serverHostname, port);
    }

    public boolean start() {
        try {
            Logger.info("Trying to connect to inet=" + socketAddress);
            this.socket = new Socket();
            this.socket.connect(socketAddress);
            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            Logger.warn("Failed to create the client socket " + e.getMessage());
            return false;
        }

        Logger.info("Connected to server !");
        return true;
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            Logger.warn("Exception while closing, e=" + e.getMessage());
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
