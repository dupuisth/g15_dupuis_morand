package com.gr15.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public class ConnectionToClient {
    private static final Logger LOGGER = Logger.getLogger(ConnectionToClient.class.getName());

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ConnectionToClient(Socket socket) throws IOException {
        this.socket = socket;
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException e) {
            LOGGER.warning("Failed to bind socket output/input");
            throw e;
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public PrintWriter getOut() {
        return out;
    }

    public BufferedReader getIn() {
        return in;
    }

    public boolean isConnected()
    {
        return socket.isConnected();
    }
}
