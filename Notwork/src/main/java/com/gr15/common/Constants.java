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
}
