package com.trafficlab.pool;

public record PoolStats(
        int activeConnections,
        int idleConnections,
        int threadsAwaitingConnection,
        int totalConnections,
        int maximumPoolSize
) {
}
