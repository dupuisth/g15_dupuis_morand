package com.gr15.server;

import com.gr15.common.Message;
import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ConnectionToClient {
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
            Logger.warn("Failed to bind socket output/input");
            throw e;
        }
    }

    public void send(Message message) throws IOException {
        Message.sendMessageToSocket(out, message);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            Logger.warn("Exception while closing socket c=" + clientId + ", e=" + e.getMessage());
        }
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
