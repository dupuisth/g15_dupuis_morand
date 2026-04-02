package com.gr15.common.message;

import com.gr15.common.Constants;

import static com.gr15.common.Constants.*;

/**
 * Use an int to represent the broadcastId.
 * The broadcastId is composed of the ServerId and the LocalBroadcastId
 */
public class BroadcastId {
    public static int Create(int serverId, int localId) {
        int serverBits = serverId << (BROADCAST_ID_TOTAL_BITS - BROADCAST_ID_SERVER_BITS);
        int localBits = localId & (BROADCAST_ID_BITS_BITMASK >> (BROADCAST_ID_TOTAL_BITS - BROADCAST_ID_LOCAL_BITS));

        return serverBits | localBits;
    }

    public static int GetServerId(int broadcastId) {
        return broadcastId >> BROADCAST_ID_LOCAL_BITS;
    }

    public static int GetLocalId(int broadcastId) {
        return broadcastId & (BROADCAST_ID_BITS_BITMASK >> BROADCAST_ID_TOTAL_BITS - BROADCAST_ID_LOCAL_BITS);
    }

    public static String toString(int broadcastId) {
        return broadcastId + "(" + GetServerId(broadcastId)  + ":" + GetLocalId(broadcastId) + ")";
    }
}
