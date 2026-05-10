package com.gr15.server.managers;

import com.gr15.common.message.BroadcastId;
import com.gr15.common.message.sts.BroadcastData;
import com.gr15.utils.Logger;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;

import static com.gr15.common.Constants.BROADCAST_ID_FORGER_AFTER_SECONDS;
import static com.gr15.common.Constants.BROADCAST_ID_LOCAL_BITS;

/**
 * Tracks broadcast identifiers so loop prevention stays separate from socket
 * and routing concerns.
 */
class ServerBroadcastTracker {
    private final HashMap<Integer, LocalDateTime> broadcastMap = new HashMap<>();
    private int currentLocalBroadcastId = 0;
    private final Object currentLocalBroadcastIdLock = new Object();

    boolean shouldHandleBroadcast(BroadcastData broadcastData, int localServerId) {
        LocalDateTime currentDateTime = LocalDateTime.now();

        int broadcastId = broadcastData.getBroadcastId();
        if (BroadcastId.GetServerId(broadcastId) == localServerId) {
            Logger.debug("Received my own broadcast, doing nothing");
            return false;
        }

        synchronized (broadcastMap) {
            if (broadcastMap.containsKey(broadcastId)) {
                LocalDateTime dateTime = broadcastMap.get(broadcastId);
                Duration gap = Duration.between(dateTime, currentDateTime);

                if (gap.getSeconds() < BROADCAST_ID_FORGER_AFTER_SECONDS) {
                    return false;
                }
            }

            broadcastMap.put(broadcastId, currentDateTime);
        }

        return true;
    }

    int getNextBroadcastId(int localServerId) {
        int localId;
        synchronized (currentLocalBroadcastIdLock) {
            localId = currentLocalBroadcastId++;
            if (currentLocalBroadcastId > (1 << BROADCAST_ID_LOCAL_BITS) - 1) {
                currentLocalBroadcastId = 0;
            }
        }

        return BroadcastId.Create(localServerId, localId);
    }
}
