package com.gr15.common.message.stc;

import com.gr15.common.ClientId;
import com.gr15.common.Constants;
import com.gr15.common.Message;
import static com.gr15.common.Constants.*;

public class STC_MessageRemoveClient {
    public static final int ID = MessageSTC.REMOVE_CLIENT.getId();

    private final int clientId;

    private STC_MessageRemoveClient(int clientId) {
        this.clientId = clientId;
    }

    public static Message CreateMessage(int clientId) {
        Message message = new Message(ID);
        message.addInt(clientId, TOTAL_CLIENT_ID_BITS);
        return message;
    }

    public static STC_MessageRemoveClient ReadMessage(Message message) {
        int clientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        return new STC_MessageRemoveClient(clientId);
    }

    public int getClientId() {
        return clientId;
    }

    @Override
    public String toString() {
        return "STC_MessageRemoveClient{" +
                "clientId=" + clientId +
                '}';
    }
}
