package com.gr15.common.message.ats;

/**
 * Type of messages Admin -> Server
 */
public enum MessageATS {
    LIST_NEIGHBORS(0),
    ADD_NEIGHBOR(1),
    REMOVE_NEIGHBOR(2),
    STOP(3),
    RESET(4);



    private final int val;

    private MessageATS(int val) {
        this.val = val;
    }

    public int getId() {
        return val;
    }

    public static MessageATS fromId(int id) {
        for (MessageATS messageCTS : values()) {
            if (messageCTS.getId() == id) {
                return messageCTS;
            }
        }

        return null;
    }
}
