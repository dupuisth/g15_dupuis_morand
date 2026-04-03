package com.gr15.common.message.ats;

import com.gr15.common.Message;

public class ATS_ListNeighbor {
    public static final int ID = MessageATS.LIST_NEIGHBORS.getId();

    private ATS_ListNeighbor() { }

    public static Message CreateMessage() {
        Message message = new Message(ID);
        return message;
    }

    public static ATS_ListNeighbor ReadMessage(Message message) {
        return new ATS_ListNeighbor();
    }
}
