package com.gr15.common.message.stc;

import com.gr15.common.Message;

import static com.gr15.common.Constants.TOTAL_CLIENT_ID_BITS;

public class STC_MessageError {
    public static final int ID = MessageSTC.ERROR.getId();

    private final int destinationClientId;
    private final String errorMessage;

    private STC_MessageError(int destinationClientId, String errorMessage) {
        this.destinationClientId = destinationClientId;
        this.errorMessage = errorMessage;
    }

    public static Message CreateMessage(int destinationClientId, String errorMessage) {
        Message message = new Message(ID);
        message.addInt(destinationClientId, TOTAL_CLIENT_ID_BITS);
        message.addString(errorMessage);
        return message;
    }

    public static STC_MessageError ReadMessage(Message message) {
        int destinationClientId = message.readInt(TOTAL_CLIENT_ID_BITS);
        String errorMessage = message.readString();
        return new STC_MessageError(destinationClientId, errorMessage);
    }

    public int getDestinationClientId() {
        return destinationClientId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "STC_MessageError{" +
                "destinationClientId=" + destinationClientId +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
