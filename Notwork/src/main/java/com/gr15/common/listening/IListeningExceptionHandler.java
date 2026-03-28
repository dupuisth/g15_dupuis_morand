package com.gr15.common.listening;

import com.gr15.common.Message;
import com.gr15.common.connections.RemoteConnection;

public interface IListeningExceptionHandler<T extends RemoteConnection> {
    /**
     * Handle the exception
     * @return true if the exception will close the connection
     */
    boolean handleException(T remoteConnection, Exception exception);
}
