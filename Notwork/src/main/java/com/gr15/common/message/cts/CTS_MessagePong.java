package com.gr15.common.message.cts;
import com.gr15.common.Message;
import static com.gr15.common.Constants.*;

public class CTS_MessagePong {
    public static final int ID = MessageCTS.PONG.getId();
    private final String pongMessage;

    private CTS_MessagePong(String pongMessage){
        this.pongMessage = pongMessage;
    }

    public static Message CreateMessage(String PongMessage){
        Message message = new Message(ID);
        message.addString(PongMessage);
        return message;
    }

    public static CTS_MessagePong ReadMessage(Message message){
        String pong = message.readString();
        return new CTS_MessagePong(pong);
    }

    public String getPongMessage(){
        return pongMessage;
    }
}
