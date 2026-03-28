package com.gr15.common.listening;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;
import com.gr15.utils.Logger;

/**
 * Thread that handle accepting on a socket
 */
public class ListeningThread<T extends RemoteConnection> extends Thread {
    private final T remoteConnection;
    private final IListeningMessageHandler<T> messageHandler;
    private final IListeningExceptionHandler<T> exceptionHandler;

    private volatile boolean shouldStop = false;

    public ListeningThread(T remoteConnection, IListeningMessageHandler<T> messageHandler, IListeningExceptionHandler<T> exceptionHandler) {
        this.remoteConnection = remoteConnection;
        this.messageHandler = messageHandler;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void run() {
        while (!shouldStop && remoteConnection.isConnected()) {
            try {
                Message message = remoteConnection.read();
                messageHandler.handleMessage(remoteConnection, message);
            } catch (Exception e) {
                Logger.error("Exception while trying to read message", e);
                // Delegate the exception
                exceptionHandler.handleException(remoteConnection, e);
            }
        }

        Logger.info("Stopped listening thread");
    }

    public void setShouldStop() {
        shouldStop = true;
    }
}
