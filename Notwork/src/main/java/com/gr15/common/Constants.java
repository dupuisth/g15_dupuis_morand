package com.gr15.common;

import com.gr15.utils.BitmaskUtils;

public class Constants {
    public static final int SERVER_ID_BITS = 4;
    public static final int LOCAL_ID_BITS = 4;
    public static final int TOTAL_CLIENT_ID_BITS = SERVER_ID_BITS + LOCAL_ID_BITS;
    public static final int TOTAL_CLIENT_ID_BITS_BITMASK = BitmaskUtils.GetBitmask(TOTAL_CLIENT_ID_BITS);

    // Calculate the max clients of a server from the LOCAL_ID_BITS
    public static final int MAX_CLIENTS = Math.powExact(2, LOCAL_ID_BITS);

    // Calculate the max servers on the network from the SERVER_ID_BITS
    public static final int MAX_SERVERS = Math.powExact(2, SERVER_ID_BITS);

    public static final int BROADCAST_ID_SERVER_BITS = SERVER_ID_BITS;
    public static final int BROADCAST_ID_LOCAL_BITS = 16;
    public static final int BROADCAST_ID_TOTAL_BITS = BROADCAST_ID_SERVER_BITS + BROADCAST_ID_LOCAL_BITS;
    public static final int BROADCAST_ID_BITS_BITMASK = BitmaskUtils.GetBitmask(BROADCAST_ID_TOTAL_BITS);
    public static final int BROADCAST_ID_FORGER_AFTER_SECONDS = 20;

    public static final int TTL_BITS = 4;
    public static final int TTL_MAX_VALUE = Math.powExact(2, TTL_BITS) - 1;
    public static final int TTL_DEFAULT_VALUE = TTL_MAX_VALUE;

    public static final int SERVER_POLL_RATE = 24;
    public static final int SERVER_POLL_DELAY_MS = 1000 / SERVER_POLL_RATE;
}
