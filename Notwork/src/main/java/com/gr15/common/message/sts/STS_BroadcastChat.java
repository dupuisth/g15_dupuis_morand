package com.gr15.common.message.sts;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.utils.Logger;
import static com.gr15.common.Constants.*;


public class STS_BroadcastChat {
    public static final int ID = MessageSTS.BROADCAST_CHAT.getId();

    private final int fromClientId;
    private final String content;
    private final BroadcastData broadcastData;

    private STS_BroadcastChat(int fromClientId, String content, BroadcastData broadcastData) {
        this.fromClientId = fromClientId;
        this.content = content;
        this.broadcastData = broadcastData;
    }

    public static Message CreateMessage(int fromClientId, String content, BroadcastData broadcastData) {
        Message message = new Message(ID);
        BroadcastData.WriteToMessage(message, broadcastData);
        message.addInt(fromClientId, TOTAL_CLIENT_ID_BITS);
        message.addString(content);
        return message;
    }

    public static STS_BroadcastChat ReadMessage(Message message) {
        BroadcastData broadcastData = BroadcastData.ReadFromMessage(message);
        int fromClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String content = message.readString();
        return new STS_BroadcastChat(fromClientId, content, broadcastData) ;
    }

    public int getFromClientId() {
        return fromClientId;
    }

    public String getContent() {
        return content;
    }


    public BroadcastData getBroadcastData() {
        return broadcastData;
    }

    @Override
    public String toString() {
        return "STS_BroadcastChat{" +
                "fromClientId=" + fromClientId +
                ", content='" + content + '\'' +
                ", broadcastData=" + broadcastData +
                '}';
    }
}
