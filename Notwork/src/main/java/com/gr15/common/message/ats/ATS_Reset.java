package com.gr15.common.message.ats;

import com.gr15.common.Message;

public class ATS_Reset {
    public static final int ID = MessageATS.RESET.getId();

    private ATS_Reset() { }

    public static Message CreateMessage() {
        Message message = new Message(ID);
        return message;
    }

    public static ATS_Reset ReadMessage(Message message) {
        return new ATS_Reset();
    }
}
