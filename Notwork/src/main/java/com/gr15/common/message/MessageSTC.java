package com.gr15.common.message;

/**
 * Types of message Server -> Client
 */
public enum MessageSTC {
    HELLO(0),
    MESSAGE(1),
    NEW_CLIENT(2),
    REMOVE_CLIENT(3);

    private final int val;

    private MessageSTC(int val) {
        this.val = val;
    }

    public int getId() {
        return val;
    }

    public static MessageSTC fromId(int id) {
        for (MessageSTC messageSTC : values()) {
            if (messageSTC.getId() == id) {
                return messageSTC;
            }
        }

        return null;
    }
}
