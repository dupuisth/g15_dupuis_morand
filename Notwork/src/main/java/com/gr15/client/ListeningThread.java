package com.gr15.client;

import com.gr15.common.Message;
import com.gr15.utils.Logger;

import java.io.EOFException;

public class ListeningThread extends Thread {
    private final ClientApp client;

    public ListeningThread(ClientApp client) {
        this.client = client;
    }

    @Override
    public void run() {
        super.run();

            // Continuous listening
            while (client.getConnection().isConnected() ) {
                try {
                    Message message = client.getConnection().read();
                    client.onMessageReceived(message);
                } catch (EOFException e) {
                    Logger.error("Received a EOF when try to read, closing the connection", e);
                    client.getConnection().close();
                } catch (Exception e) {
                    Logger.error("Error while trying to read message ! e=" + e.getMessage(), e);
                }
            }
        }
}
