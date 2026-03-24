package com.gr15.client;

import java.io.IOException;

public class ListeningThread extends Thread
{
    private ClientApp client;

    public ListeningThread(ClientApp client)
    {
        this.client = client;
    }

    @Override
    public void run() {
        super.run();

        // Continuous listening
        while (client.getConnection().isConnected())
        {
            String message = getMessage();
            client.onMessageReceived(message);
        }
    }

    private String getMessage() {

        String message = null;
        while (message == null)
        {
            synchronized (client.getConnection())
            {
                try {
                    message = client.getConnection().getIn().readLine();
                } catch (IOException e) {
                    // Do nothing
                }
            }
        }
        return message;
    }
}
