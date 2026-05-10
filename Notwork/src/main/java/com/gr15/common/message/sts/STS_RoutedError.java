package com.gr15.common.message.sts;

import com.gr15.common.Message;

import static com.gr15.common.Constants.TOTAL_CLIENT_ID_BITS;

public class STS_RoutedError {
    public static final int ID = MessageSTS.ROUTED_ERROR.getId();

    private final int recipientClientId;
    private final int destinationClientId;
    private final String errorMessage;

    private STS_RoutedError(int recipientClientId, int destinationClientId, String errorMessage) {
        this.recipientClientId = recipientClientId;
        this.destinationClientId = destinationClientId;
        this.errorMessage = errorMessage;
    }

    public static Message CreateMessage(int recipientClientId, int destinationClientId, String errorMessage) {
        Message message = new Message(ID);
        message.addInt(recipientClientId, TOTAL_CLIENT_ID_BITS);
        message.addInt(destinationClientId, TOTAL_CLIENT_ID_BITS);
        message.addString(errorMessage);
        return message;
    }

    public static STS_RoutedError ReadMessage(Message message) {
        int recipientClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        int destinationClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String errorMessage = message.readString();
        return new STS_RoutedError(recipientClientId, destinationClientId, errorMessage);
    }

    public int getRecipientClientId() {
        return recipientClientId;
    }

    public int getDestinationClientId() {
        return destinationClientId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "STS_RoutedError{" +
                "recipientClientId=" + recipientClientId +
                ", destinationClientId=" + destinationClientId +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
