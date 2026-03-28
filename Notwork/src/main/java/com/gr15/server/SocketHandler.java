package com.gr15.server;

import java.net.Socket;

/**
 * Handles a socket
 */
public interface SocketHandler {
    void handle(Socket socket);
}
