package com.gr15.common.message.stc;

import com.gr15.common.ClientId;
import com.gr15.common.Message;

public class STC_MessageRemoveClient {
    public static final int ID = MessageSTC.REMOVE_CLIENT.getId();

    private final int clientId;

    private STC_MessageRemoveClient(int clientId) {
        this.clientId = clientId;
    }

    public static Message CreateMessage(int clientId) {
        Message message = new Message(ID);
        message.addInt(clientId, ClientId.TOTAL_BITS);
        return message;
    }

    public static STC_MessageRemoveClient ReadMessage(Message message) {
        int clientId = message.readInt(ClientId.TOTAL_BITS);
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
