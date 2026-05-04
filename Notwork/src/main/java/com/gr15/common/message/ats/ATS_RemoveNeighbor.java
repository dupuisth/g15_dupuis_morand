package com.gr15.common.message.ats;

import com.gr15.common.Message;

public class ATS_RemoveNeighbor {
    public static final int ID = MessageATS.REMOVE_NEIGHBOR.getId();

    // https://fr.wikipedia.org/wiki/Puissance_de_deux
    // Port max = 65535
    // => 2^16

    private final String hostname;
    private final int port;

    private ATS_RemoveNeighbor(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public static Message CreateMessage(String hostname, int port) {
        Message message = new Message(ID);
        message.addString(hostname);
        message.addInt(port, 16);
        return message;
    }

    public static ATS_RemoveNeighbor ReadMessage(Message message) {
        String hostname = message.readString();
        int port = message.readInt(16);
        return new ATS_RemoveNeighbor(hostname, port);
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }
}
