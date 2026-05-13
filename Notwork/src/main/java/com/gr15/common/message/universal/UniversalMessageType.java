package com.gr15.common.message.universal;

public enum UniversalMessageType {
    DATA(1),
    TOPOLOGY(2),
    LIST_CLIENT(3),
    ERROR(4),
    PING(5),
    PONG(6),
    CONNECT(7);

    private final int id;

    UniversalMessageType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static UniversalMessageType fromId(int id) {
        for (UniversalMessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }
}
