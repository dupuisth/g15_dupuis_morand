package com.gr15.common;

import com.gr15.utils.BitmaskUtils;

import static com.gr15.common.Constants.*;

/**
 * Use an int to represent the clientId.
 * The clientId is composed of the ServerId and the LocalId
 */
public class ClientId {
    public static int Create(int serverId, int localId) {
        int serverMask = TOTAL_CLIENT_ID_BITS_BITMASK >> (TOTAL_CLIENT_ID_BITS - SERVER_ID_BITS);
        int localMask = TOTAL_CLIENT_ID_BITS_BITMASK >> (TOTAL_CLIENT_ID_BITS - LOCAL_ID_BITS);

        int serverBits = (serverId & serverMask) << (TOTAL_CLIENT_ID_BITS - SERVER_ID_BITS);
        int clientBits = localId & localMask;

        return serverBits | clientBits;
    }

    public static int GetServerId(int clientId) {
        return clientId >> SERVER_ID_BITS;
    }

    public static int GetLocalId(int clientId) {
        return clientId & (TOTAL_CLIENT_ID_BITS_BITMASK >> TOTAL_CLIENT_ID_BITS - LOCAL_ID_BITS);
    }

    public static String toString(int clientId) {
        return clientId + "(" + GetServerId(clientId)  + ":" + GetLocalId(clientId) + ")";
    }
}
