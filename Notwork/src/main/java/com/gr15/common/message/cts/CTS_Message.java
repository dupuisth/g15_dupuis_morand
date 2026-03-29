package com.gr15.common.message.cts;

import com.gr15.common.Message;

public class CTS_Message {
    public static final int ID = MessageCTS.MESSAGE.getId();

    private final String content;


    private CTS_Message(String content) {
        this.content = content;
    }

    public static Message CreateMessage(String content) {
        Message message = new Message(ID);
        message.addString(content);
        return message;
    }

    public static CTS_Message ReadMessage(Message message) {
        String content = message.readString();
        return new CTS_Message(content);
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "CTS_Message{" +
                "content='" + content + '\'' +
                '}';
    }
}
