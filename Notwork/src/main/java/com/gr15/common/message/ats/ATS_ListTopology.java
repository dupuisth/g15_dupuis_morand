package com.gr15.common.message.ats;

import com.gr15.common.Message;

public class ATS_ListTopology {
    public static final int ID = MessageATS.LIST_TOPOLOGY.getId();

    private ATS_ListTopology() { }

    public static Message CreateMessage() {
        return new Message(ID);
    }

    public static ATS_ListTopology ReadMessage(Message message) {
        return new ATS_ListTopology();
    }
}
