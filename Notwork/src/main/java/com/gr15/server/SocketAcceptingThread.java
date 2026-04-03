package com.gr15.server;

import com.gr15.utils.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Thread that handle accepting on a socket
 */
public class SocketAcceptingThread extends Thread {
    /**
     * Socket that we will accept from
     */
    private final ServerSocket serverSocket;

    /**
     * Used when a new socket is accepted
     */
    private final SocketHandler handler;

    /**
     * If the thread should stop
     */
    private volatile boolean shouldStop = false;

    public SocketAcceptingThread(ServerSocket serverSocket, SocketHandler handler) {
        this.serverSocket = serverSocket;
        this.handler = handler;
    }

    @Override
    public void run() {
        while (!shouldStop && !serverSocket.isClosed() && serverSocket.isBound()) {
            try {
                // Accept the new socket
                Socket newSocket = serverSocket.accept();
                // Callback
                handler.handle(newSocket);
            } catch (Exception e) {
                Logger.error("Exception while accepting socket from serverSocket=" + serverSocket.getInetAddress(), e);
            }
        }

        Logger.info("Stopped socket accepting thread");
    }

    public void setShouldStop() {
        shouldStop = true;
        this.interrupt();
    }
}
