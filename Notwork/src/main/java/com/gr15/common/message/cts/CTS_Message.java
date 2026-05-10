package com.gr15.common.message.cts;

import com.gr15.common.Message;
import static com.gr15.common.Constants.*;

public class CTS_Message {
    public static final int ID = MessageCTS.MESSAGE.getId();

    private final int destinationClientId;
    private final String content;


    private CTS_Message(int destinationClientId, String content) {
        this.destinationClientId = destinationClientId;
        this.content = content;
    }

    public static Message CreateMessage(int destinationClientId, String content) {
        Message message = new Message(ID);
        message.addInt(destinationClientId, TOTAL_CLIENT_ID_BITS);
        message.addString(content);
        return message;
    }

    public static CTS_Message ReadMessage(Message message) {
        int destinationClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String content = message.readString();
        return new CTS_Message(destinationClientId, content);
    }

    public int getDestinationClientId() {
        return destinationClientId;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "CTS_Message{" +
                "destinationClientId=" + destinationClientId +
                ", content='" + content + '\'' +
                '}';
    }
}
