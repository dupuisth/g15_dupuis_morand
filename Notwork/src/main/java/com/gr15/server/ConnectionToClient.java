package com.gr15.server;

import com.gr15.common.ClientId;
import com.gr15.common.Message;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class ConnectionToClient {
    private static final Logger LOGGER = Logger.getLogger(ConnectionToClient.class.getName());

    private int clientId;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public ConnectionToClient(Socket socket, int clientId) throws IOException {
        this.socket = socket;
        this.clientId = clientId;
        try {
            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.warning("Failed to bind socket output/input");
            throw e;
        }
    }

    public void send(Message message) throws IOException {
        Message.sendMessageToSocket(out, message);
    }

    public Socket getSocket() {
        return socket;
    }

    public DataOutputStream getOut() {
        return out;
    }

    public DataInputStream getIn() {
        return in;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public int getClientId() {
        return clientId;
    }
}
