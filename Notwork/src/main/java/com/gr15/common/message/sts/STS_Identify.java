package com.gr15.common.message.sts;

import com.gr15.common.ClientId;
import com.gr15.common.Message;
import static com.gr15.common.Constants.*;

public class STS_Identify {
    public static final int ID = MessageSTS.IDENTIFY.getId();

    private final int fromServerId;
    private final int rebounds;

    private STS_Identify(int fromServerId, int rebounds) {
        this.fromServerId = fromServerId;
        this.rebounds = rebounds;
    }

    public static Message CreateMessage(int fromServerId, int rebounds) {
        Message message = new Message(ID);
        message.addInt(fromServerId, SERVER_ID_BITS);
        message.addInt(rebounds, 1);
        return message;
    }

    public static STS_Identify ReadMessage(Message message) {
        int fromServerId = message.readInt(SERVER_ID_BITS);
        int rebounds = message.readInt(1);
        return new STS_Identify(fromServerId, rebounds);
    }

    public int getFromServerId() {
        return fromServerId;
    }

    public int getRebounds() {
        return rebounds;
    }

    @Override
    public String toString() {
        return "STS_Identify{" +
                "fromServerId=" + fromServerId +
                ", rebounds=" + rebounds +
                '}';
    }
}
