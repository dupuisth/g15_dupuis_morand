package com.gr15.common.listening;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;
import com.gr15.utils.Logger;

/**
 * Dedicated reader thread for one remote connection.
 *
 * The thread performs blocking reads only. Received messages and read failures
 * are delegated to manager callbacks so socket threads do not mutate server
 * state directly.
 */
public class ListeningThread<T extends RemoteConnection> extends Thread {
    private final T remoteConnection;
    private IListeningMessageHandler<T> messageHandler;
    private IListeningExceptionHandler<T> exceptionHandler;

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
                if (shouldStop) {
                    break;
                }
                Logger.error("Exception while trying to read message", e);
                // Delegate the exception
                if (exceptionHandler.handleException(remoteConnection, e)) {
                    break;
                }
            }
        }

        Logger.info("Stopped listening thread");
    }

    public void setShouldStop() {
        shouldStop = true;
        this.interrupt();
    }
}
