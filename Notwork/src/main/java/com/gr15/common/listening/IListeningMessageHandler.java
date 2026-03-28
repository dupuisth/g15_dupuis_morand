package com.gr15.common.listening;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;

public interface IListeningMessageHandler<T extends RemoteConnection> {
    void handleMessage(T remoteConnection, Message message);
}
