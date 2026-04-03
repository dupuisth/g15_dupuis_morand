package com.gr15.common.message.sta;

/**
 * Type of messages Server -> Admin
 */
public enum MessageSTA {
    LIST_NEIGHBOR(0);

    private final int val;

    private MessageSTA(int val) {
        this.val = val;
    }

    public int getId() {
        return val;
    }

    public static MessageSTA fromId(int id) {
        for (MessageSTA messageCTS : values()) {
            if (messageCTS.getId() == id) {
                return messageCTS;
            }
        }

        return null;
    }
}
