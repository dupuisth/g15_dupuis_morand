package com.gr15.common;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Represent a connection to a remote device via a socket
 */
public class RemoteConnection {
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;

    public RemoteConnection(Socket socket) throws IOException {
        this.socket = socket;
        try {
            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            Logger.error("Failed to bind socket output/input", e);
            throw e;
        }
    }

    /**
     * Send a message to the remote
     * @throws IOException Exception while sending
     */
    public void send(Message message) throws IOException {
        Message.sendMessageToSocket(out, message);
    }

    /**
     * Send a message but catches the exception if happened
     * @return true if no exception was thrown, else false
     */
    public boolean safeSend(Message message) {
        try {
            send(message);
            return true;
        } catch (IOException e) {
            Logger.error("Exception thrown when sending message", e);
            return false;
        }
    }

    /**
     * Close the socket
     */
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            Logger.error("Exception while closing socket", e);
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
}
