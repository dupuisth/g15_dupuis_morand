package com.gr15.common.message.sts;

/**
 * Types of message Server -> Server
 */
public enum MessageSTS {
    HELLO(0),
    IDENTIFY(1);

    private final int val;

    private MessageSTS(int val) {
        this.val = val;
    }

    public int getId() {
        return val;
    }

    public static MessageSTS fromId(int id) {
        for (MessageSTS messageSTS : values()) {
            if (messageSTS.getId() == id) {
                return messageSTS;
            }
        }

        return null;
    }
}
