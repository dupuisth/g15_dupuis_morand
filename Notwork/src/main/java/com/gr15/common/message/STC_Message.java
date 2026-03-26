package com.gr15.common.message;

import com.gr15.common.ClientId;
import com.gr15.common.Message;

public class STC_Message {
    public static final int ID = MessageSTC.MESSAGE.getId();

    private final int fromClientId;
    private final String content;


    private STC_Message(int fromClientId, String content) {
        this.fromClientId = fromClientId;
        this.content = content;
    }

    public static Message CreateMessage(int fromClientId, String content) {
        Message message = new Message(ID);
        message.addInt(fromClientId, ClientId.TOTAL_BITS);
        message.addString(content);
        return message;
    }

    public static STC_Message ReadMessage(Message message) {
        int fromClientId = message.readInt(ClientId.TOTAL_BITS);
        String content = message.readString();
        return new STC_Message(fromClientId, content);
    }

    public String getContent() {
        return content;
    }



    @Override
    public String toString() {
        return "STC_Message{" +
                "fromClientId=" + fromClientId +
                ", content='" + content + '\'' +
                '}';
    }
}
