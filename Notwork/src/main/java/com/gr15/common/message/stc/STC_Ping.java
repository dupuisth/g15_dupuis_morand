package com.gr15.common.message.stc;

import com.gr15.common.Message;

public class STC_Ping {
    public static final int ID = MessageSTC.PING.getId();

    private STC_Ping() {
    }

    public static Message CreateMessage() {
        return new Message(ID);
    }

    public static STC_Ping ReadMessage(Message message) {
        return new STC_Ping();
    }

    @Override
    public String toString() {
        return "STC_Ping{}";
    }
}
