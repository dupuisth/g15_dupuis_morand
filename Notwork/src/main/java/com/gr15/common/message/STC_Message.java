package com.gr15.common.message;

import com.gr15.common.Message;

public class STC_Message {
    public static final int ID = MessageSTC.MESSAGE.getId();

    private final String content;


    private STC_Message(String content) {
        this.content = content;
    }

    public static Message CreateMessage(String content) {
        Message message = new Message(ID);
        message.addString(content);
        return message;
    }

    public static STC_Message ReadMessage(Message message) {
        String content = message.readString();
        return new STC_Message(content);
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "STC_Message{" +
                "content='" + content + '\'' +
                '}';
    }
}
