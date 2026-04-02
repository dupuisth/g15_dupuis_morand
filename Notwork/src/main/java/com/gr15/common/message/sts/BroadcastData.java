package com.gr15.common.message.sts;

import com.gr15.common.Constants;
import com.gr15.common.Message;
import com.gr15.utils.Logger;

/**
 * Contains the data required to broadcast
 */
public class BroadcastData {
    private int ttl;
    private int broadcastId;

    public BroadcastData(int ttl, int broadcastId) {
        if (ttl < 0 || ttl > Constants.TTL_MAX_VALUE) {
            Logger.warn("Given ttl value is not in range ttl=" + ttl);
        }

        this.ttl = ttl;
        this.broadcastId = broadcastId;
    }

    public BroadcastData decrementTtl() {
        return new BroadcastData(ttl - 1, broadcastId);
    }

    public static BroadcastData ReadFromMessage(Message message) {
        int broadcastId = message.readInt(Constants.BROADCAST_ID_TOTAL_BITS);
        int ttl = message.readInt(Constants.TTL_BITS);
        BroadcastData data = new BroadcastData(ttl, broadcastId);
        return data;
    }

    public static void WriteToMessage(Message message, BroadcastData data) {
        message.addInt(data.getBroadcastId(), Constants.BROADCAST_ID_TOTAL_BITS);
        message.addInt(data.getTtl(), Constants.TTL_BITS);
    }

    public int getTtl() {
        return ttl;
    }

    public int getBroadcastId() {
        return broadcastId;
    }

    @Override
    public String toString() {
        return "BroadcastData{" +
                "ttl=" + ttl +
                ", broadcastId=" + broadcastId +
                '}';
    }
}
