package com.gr15.client;

import com.gr15.common.Message;
import com.gr15.server.ServerApp;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.logging.Logger;

public class ListeningThread extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ServerApp.class.getName());
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
                    Message message = readMessage();
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


    private Message readMessage() throws IOException {
        DataInputStream in = client.getConnection().getIn();

        // Read the length
        int length = in.readInt();

        // Read the message
        byte[] messageBytes = new byte[length];
        in.readFully(messageBytes);

        // Build the message from the bytes
        return new Message(messageBytes);
    }

}
