package com.gr15.client;

import com.gr15.common.Message;
import com.gr15.utils.Logger;

/**
 * Thread that handle accepting on a socket
 */
public class ListeningThread extends Thread {
    private final ClientApp client;
    private final Connection connection;



    public ListeningThread(ClientApp client, Connection connection) {
        this.client = client;
        this.connection = connection;
    }

    @Override
    public void run() {
        while (connection.isConnected()) {
            try {
                Message message = connection.read();
                client.onMessageReceived(message);
            } catch (Exception e) {
                Logger.error("Exception while trying to read message", e);
                client.onCriticalListeningError(e);
            }
        }
    }
}
