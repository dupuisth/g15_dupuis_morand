package com.gr15.server;

import com.gr15.common.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class ConnectionToClient {
    private static final Logger LOGGER = Logger.getLogger(ConnectionToClient.class.getName());

    private Socket socket;
    private SocketChannel channel;
    private OutputStream out;
    private BufferedReader in;

    public ConnectionToClient(Socket socket) throws IOException {
        this.socket = socket;
        try {
            this.channel = socket.getChannel();
            this.out = socket.getOutputStream();
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException e) {
            LOGGER.warning("Failed to bind socket output/input");
            throw e;
        }
    }

    public void send(Message message) throws IOException {
        // Write the length
        ByteBuffer lengthBuffer = ByteBuffer.allocate(32);
        lengthBuffer.putInt(message.getData().length);
        lengthBuffer.flip();

        synchronized (channel) {
            while (lengthBuffer.hasRemaining()) {
                channel.write(lengthBuffer);
            }

            ByteBuffer dataBuffer = ByteBuffer.wrap(message.getData());
            while (dataBuffer.hasRemaining()) {
                channel.write(dataBuffer);
            }
        }

    }

    public Socket getSocket() {
        return socket;
    }

    public OutputStream getOut() {
        return out;
    }

    public BufferedReader getIn() {
        return in;
    }

    public boolean isConnected() {
        return socket.isConnected();
    }
}
