package com.gr15.common.listening;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;

public interface IListeningExceptionHandler<T extends RemoteConnection> {
    void handleException(T remoteConnection, Exception exception);
}
