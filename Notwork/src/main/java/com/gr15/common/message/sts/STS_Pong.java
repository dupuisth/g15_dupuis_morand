package com.gr15.common.message.sts;

import com.gr15.common.Message;

public class STS_Pong {
    public static final int ID = MessageSTS.PONG.getId();

    private STS_Pong() {
    }

    public static Message CreateMessage() {
        return new Message(ID);
    }

    public static STS_Pong ReadMessage(Message message) {
        return new STS_Pong();
    }

    @Override
    public String toString() {
        return "STS_Pong{}";
    }
}
