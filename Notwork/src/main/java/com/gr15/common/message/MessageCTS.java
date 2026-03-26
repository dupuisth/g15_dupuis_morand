package com.gr15.common.message;

/**
 * Type of messages Client -> Server
 */
public enum MessageCTS {
    MESSAGE(0);

    private final int val;

    private MessageCTS(int val) {
        this.val = val;
    }

    public int getId() {
        return val;
    }

    public static MessageCTS fromId(int id) {
        for (MessageCTS messageCTS : values()) {
            if (messageCTS.getId() == id) {
                return messageCTS;
            }
        }

        return null;
    }
}
