package com.gr15.common.message.sts;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import com.gr15.utils.Logger;
import static com.gr15.common.Constants.*;


public class STS_BroadcastChat {
    public static final int ID = MessageSTS.BROADCAST_CHAT.getId();
    public static final int MAX_TTL = 15;
    public static final int TTL_BITS = Math.ceilDiv(MAX_TTL, 2);

    private final int fromClientId;
    private final String content;
    private int ttl;

    private STS_BroadcastChat(int fromClientId, String content, int ttl) {
        this.fromClientId = fromClientId;
        this.content = content;
        this.ttl = ttl;
    }

    public static Message CreateMessage(int fromClientId, String content, int ttl) {
        Message message = new Message(ID);
        message.addInt(fromClientId, TOTAL_CLIENT_ID_BITS);
        message.addString(content);
        message.addInt(ttl, TTL_BITS);
        return message;
    }

    public static STS_BroadcastChat ReadMessage(Message message) {
        int fromClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String content = message.readString();
        int ttl = message.readInt(TTL_BITS);
        return new STS_BroadcastChat(fromClientId, content, ttl) ;
    }

    public int getFromClientId() {
        return fromClientId;
    }

    public String getContent() {
        return content;
    }

    public int getTtl() {
        return ttl;
    }

    public void decrementTtl() {
        this.ttl -= 1;
    }

    @Override
    public String toString() {
        return "STS_BroadcastChat{" +
                "fromClientId=" + fromClientId +
                ", content='" + content + '\'' +
                ", ttl=" + ttl +
                '}';
    }
}
