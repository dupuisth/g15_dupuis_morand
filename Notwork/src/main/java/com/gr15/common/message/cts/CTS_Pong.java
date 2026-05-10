package com.gr15.common.message.cts;

import com.gr15.common.Message;

public class CTS_Pong {
    public static final int ID = MessageCTS.PONG.getId();

    private CTS_Pong() {
    }

    public static Message CreateMessage() {
        return new Message(ID);
    }

    public static CTS_Pong ReadMessage(Message message) {
        return new CTS_Pong();
    }

    @Override
    public String toString() {
        return "CTS_Pong{}";
    }
}
