package com.gr15.server;

import com.gr15.common.Message;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class ConnectionToClient {
    private static final Logger LOGGER = Logger.getLogger(ConnectionToClient.class.getName());

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public ConnectionToClient(Socket socket) throws IOException {
        this.socket = socket;
        try {
            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.warning("Failed to bind socket output/input");
            throw e;
        }
    }

    public void send(Message message) throws IOException {
        int length = message.getWrittenByte();
        out.writeInt(length);
        out.write(message.getData(), 0, length);
        out.flush();

        LOGGER.info("Sent a message to client:\nlength=" + length + "\ndata=" + message.getDataAsBitsInString());
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
