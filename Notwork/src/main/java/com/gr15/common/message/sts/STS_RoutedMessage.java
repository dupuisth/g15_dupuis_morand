package com.gr15.common.message.sts;

import com.gr15.common.Message;

import static com.gr15.common.Constants.TOTAL_CLIENT_ID_BITS;

/**
 * Routed chat payload between servers.
 *
 * The source and destination are full packed client ids. ServerManager uses the
 * destination server part to forward this message toward the next hop.
 */
public class STS_RoutedMessage {
    public static final int ID = MessageSTS.ROUTED_MESSAGE.getId();

    private final int fromClientId;
    private final int destinationClientId;
    private final String content;

    private STS_RoutedMessage(int fromClientId, int destinationClientId, String content) {
        this.fromClientId = fromClientId;
        this.destinationClientId = destinationClientId;
        this.content = content;
    }

    public static Message CreateMessage(int fromClientId, int destinationClientId, String content) {
        Message message = new Message(ID);
        message.addInt(fromClientId, TOTAL_CLIENT_ID_BITS);
        message.addInt(destinationClientId, TOTAL_CLIENT_ID_BITS);
        message.addString(content);
        return message;
    }

    public static STS_RoutedMessage ReadMessage(Message message) {
        int fromClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        int destinationClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String content = message.readString();
        return new STS_RoutedMessage(fromClientId, destinationClientId, content);
    }

    public int getFromClientId() {
        return fromClientId;
    }

    public int getDestinationClientId() {
        return destinationClientId;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "STS_RoutedMessage{" +
                "fromClientId=" + fromClientId +
                ", destinationClientId=" + destinationClientId +
                ", content='" + content + '\'' +
                '}';
    }
}
