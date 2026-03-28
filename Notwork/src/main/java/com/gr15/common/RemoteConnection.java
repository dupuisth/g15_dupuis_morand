package com.gr15.common;

import com.gr15.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

/**
 * Represent a connection to a remote device via a socket
 */
public abstract class RemoteConnection {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private final String hostname;
    private final int port;

    private final Object writeLock = new Object();
    private final Object readLock = new Object();

    public RemoteConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.hostname = socket.getInetAddress().getHostName();
        this.port = socket.getPort();
        bindStreams();
    }

    public RemoteConnection(String hostname, int port, boolean connectInstantly) throws IOException {
        this.hostname = hostname;
        this.port = port;
        this.socket = null;
        this.in = null;
        this.out = null;

        if (connectInstantly) {
            connect();
        }
    }

    public synchronized void connect() throws IOException {
        if (isConnected()) {
            Logger.warn("Connection is already running");
            return;
        }

        Logger.info("Connecting to " + hostname + ":" + port);
        this.socket = new Socket(hostname, port);
        bindStreams();
    }

    private void bindStreams() throws IOException {
        try {
            this.out = new DataOutputStream(socket.getOutputStream());
            this.in = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            Logger.error("Failed to bind socket output/input", e);
            throw e;
        }
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

    /**
     * Read a message (blocking)
     */
    public Message read() throws Exception {
        // No reason to have multiple readers, but ensure it
        synchronized (readLock) {
            // Read the length
            int length = in.readInt();

            // Read the message
            byte[] messageBytes = new byte[length];
            in.readFully(messageBytes);

            Message message = new Message(messageBytes);
            Logger.debug("Received a message length=" + length);

            // Build the message from the bytes
            return message;
        }
    }

    /**
     * Send a message on a given output stream
     */
    public void send(Message message) throws IOException {
        // Prevent sending two messages at the same time
        synchronized (writeLock) {
            // Get the total bytes written (this might send 1 to 3 more bits, since the unitary format is byte)
            int length = message.getWrittenByte();
            // Send the total length
            out.writeInt(length);

            // Then send all the data (but crop just to what was written)
            out.write(message.getData(), 0, length);

            // And assure the message is fully sent
            out.flush();

            Logger.debug("Sent a message length=" + length);
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
