package com.gr15.common.message.ats;

import com.gr15.common.Message;
import com.gr15.common.message.cts.MessageCTS;

public class ATS_Stop {
    public static final int ID = MessageATS.STOP.getId();

    private ATS_Stop() { }

    public static Message CreateMessage() {
        Message message = new Message(ID);
        return message;
    }

    public static ATS_Stop ReadMessage(Message message) {
        return new ATS_Stop();
    }
}
