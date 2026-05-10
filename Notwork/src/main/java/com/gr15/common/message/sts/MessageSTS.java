package com.gr15.common.message.sts;

/**
 * Types of message Server -> Server
 */
public enum MessageSTS {
    HELLO(0),
    IDENTIFY(1),
    BROADCAST_CHAT(2),
    ROUTED_MESSAGE(3),
    ROUTING_UPDATE(4),
    ROUTED_ERROR(5);

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
