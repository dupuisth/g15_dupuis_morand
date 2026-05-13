package com.gr15.common.message.universal;

import java.util.Arrays;

/**
 * Immutable representation of one universal protocol packet after the global
 * header has been decoded.
 */
public class UniversalPacket {
    public static final int MAGIC = 1;
    public static final int DEFAULT_TTL = 128;
    public static final int DEFAULT_OPTION = 0;

    private final int id;
    private final int ttl;
    private final int option;
    private final UniversalMessageType type;
    private final byte[] payload;

    public UniversalPacket(int id, int ttl, int option, UniversalMessageType type, byte[] payload) {
        if (type == null) {
            throw new IllegalArgumentException("Universal packet type cannot be null");
        }
        this.id = id;
        this.ttl = ttl;
        this.option = option;
        this.type = type;
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    public static UniversalPacket create(UniversalMessageType type, byte[] payload) {
        return new UniversalPacket(0, DEFAULT_TTL, DEFAULT_OPTION, type, payload);
    }

    public int getId() {
        return id;
    }

    public int getTtl() {
        return ttl;
    }

    public int getOption() {
        return option;
    }

    public UniversalMessageType getType() {
        return type;
    }

    public byte[] getPayload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
