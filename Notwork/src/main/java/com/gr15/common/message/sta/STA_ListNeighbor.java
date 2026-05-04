package com.gr15.common.message.sta;

import com.gr15.common.Message;
import com.gr15.common.message.ats.MessageATS;

public class STA_ListNeighbor {
    public static final int ID = MessageATS.STOP.getId();


    private STA_ListNeighbor() { }

    public static Message CreateMessage() {
        Message message = new Message(ID);
        return message;
    }

    public static STA_ListNeighbor ReadMessage(Message message) {
        return new STA_ListNeighbor();
    }
}
