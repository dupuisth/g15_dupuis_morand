package com.gr15.client;

import com.gr15.common.Message;

import java.util.logging.Logger;

public class ListeningThread extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ListeningThread.class.getName());
    private ClientApp client;

    public ListeningThread(ClientApp client) {
        this.client = client;
    }

    @Override
    public void run() {
        super.run();

            // Continuous listening
            while (client.getConnection().isConnected()) {
                try {
                    Message message = Message.readMessageFromSocket(client.getConnection().getIn());
                    client.onMessageReceived(message);
                } catch (Exception e) {
                    LOGGER.warning("Error while trying to read message ! e=" + e.getMessage());
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

}
