package com.gr15.common;

import com.gr15.utils.BitmaskUtils;

/**
 * Use an int to represent the clientId.
 * The clientId is composed of the ServerId and the LocalId
 */
public class ClientId {
    public static final int SERVER_ID_BITS = 4;
    public static final int LOCAL_ID_BITS = 4;
    public static final int TOTAL_BITS = SERVER_ID_BITS + LOCAL_ID_BITS;
    public static final int TOTAL_BITS_BITMASK = BitmaskUtils.GetBitmask(TOTAL_BITS);

    // Calculate the max clients from the LOCAL_ID_BITS
    public static final int MAX_CLIENTS = Math.powExact(2, LOCAL_ID_BITS);

    // Calculate the max servers from the SERVER_ID_BITS
    public static final int MAX_SERVERS = Math.powExact(2, SERVER_ID_BITS);

    public static int Create(int serverId, int localId) {
        int serverBits = serverId << (TOTAL_BITS - SERVER_ID_BITS);
        int clientBits = localId & (TOTAL_BITS_BITMASK >> (TOTAL_BITS - LOCAL_ID_BITS));

        return serverBits | clientBits;
    }

    public static int GetServerId(int clientId) {
        return clientId >> SERVER_ID_BITS;
    }

    public static int GetLocalId(int clientId) {
        return clientId & (TOTAL_BITS_BITMASK >> TOTAL_BITS - LOCAL_ID_BITS);
    }

    public static String toString(int clientId) {
        return clientId + "(" + GetServerId(clientId)  + ":" + GetLocalId(clientId) + ")";
    }
}
