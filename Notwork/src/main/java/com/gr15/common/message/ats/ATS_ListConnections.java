package com.gr15.common.message.ats;

import com.gr15.common.Message;

public class ATS_ListConnections {
    public static final int ID = MessageATS.LIST_CONNECTIONS.getId();

    private ATS_ListConnections() { }

    public static Message CreateMessage() {
        Message message = new Message(ID);
        return message;
    }

    public static ATS_ListConnections ReadMessage(Message message) {
        return new ATS_ListConnections();
    }
}
