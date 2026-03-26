package com.gr15.common.message;

import com.gr15.common.ClientId;
import com.gr15.common.Message;

public class STC_MessageHello {
    public static final int ID = MessageSTC.HELLO.getId();

    private final String welcomeMessage;
    private final int clientId;

    private STC_MessageHello(int clientId, String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
        this.clientId = clientId;
    }

    public static Message CreateMessage(int clientId, String welcomeMessage) {
        Message message = new Message(ID);
        message.addInt(clientId, ClientId.TOTAL_BITS);
        message.addString(welcomeMessage);
        return message;
    }   

    public static STC_MessageHello ReadMessage(Message message) {
        int clientId = message.readInt(ClientId.TOTAL_BITS);
        String welcome = message.readString();
        return new STC_MessageHello(clientId, welcome);
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public int getClientId() {
        return clientId;
    }

    @Override
    public String toString() {
        return "STC_MessageHello{" +
                "welcomeMessage='" + welcomeMessage + '\'' +
                ", clientId=" + clientId +
                '}';
    }
}
