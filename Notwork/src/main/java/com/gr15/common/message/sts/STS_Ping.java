package com.gr15.common.message.sts;

import com.gr15.common.Message;

public class STS_Ping {
    public static final int ID = MessageSTS.PING.getId();

    private STS_Ping() {
    }

    public static Message CreateMessage() {
        return new Message(ID);
    }

    public static STS_Ping ReadMessage(Message message) {
        return new STS_Ping();
    }

    @Override
    public String toString() {
        return "STS_Ping{}";
    }
}
