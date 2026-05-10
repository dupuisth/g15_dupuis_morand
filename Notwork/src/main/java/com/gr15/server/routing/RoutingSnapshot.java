package com.gr15.server.routing;

public record RoutingSnapshot(int originServerId, int sequence, int clientMask, int neighborMask) {
}
