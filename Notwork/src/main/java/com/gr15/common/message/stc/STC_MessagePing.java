package com.gr15.common.message.stc;

import com.gr15.common.Message;
import static com.gr15.common.Constants.*;

public class STC_MessagePing
{
    public static final int ID = MessageSTC.PING.getId(); //utiliser dans manager
    private final String PingMessage;
    private final int clientId;
    private STC_MessagePing(int clientId, String PingMessage){
        this.PingMessage = PingMessage;
        this.clientId = clientId;
    }

    public static Message CreateMessage(int clientId, String PingMessage){
        Message message = new Message(ID);
        message.addInt(clientId, TOTAL_CLIENT_ID_BITS);
        message.addString(PingMessage);
        return message;
    }

    public static STC_MessagePing ReadMessage(Message message){
        int clientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String pingmsg = message.readString();
        return new STC_MessagePing(clientId, pingmsg);
    }

    public String getPingMessage() { return PingMessage;}

    public int getClientId() { return clientId;}

    @Override
    public String toString() {
        return "STC_MessagePing{"+
                "3...2...1....'" + PingMessage + '\''+
                ", clientId =" + clientId +
                '}';
    }
}
