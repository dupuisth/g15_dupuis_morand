package com.gr15.client;

import com.gr15.common.Message;

import java.io.EOFException;
import java.util.logging.Logger;

public class ListeningThread extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ListeningThread.class.getName());

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
                    Message message = Message.readMessageFromSocket(client.getConnection().getIn());
                    client.onMessageReceived(message);
                } catch (EOFException e) {
                    LOGGER.warning("Received a EOF when try to read, closing the connection");
                    client.getConnection().close();
                } catch (Exception e) {
                    LOGGER.warning("Error while trying to read message ! e=" + e.getMessage());
                }
            }
        }
}
