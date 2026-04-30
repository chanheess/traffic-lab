package com.trafficlab.cache;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class CacheMetrics {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public void hit() {
        hits.incrementAndGet();
    }

    public void miss() {
        misses.incrementAndGet();
    }

    public void reset() {
        hits.set(0);
        misses.set(0);
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public double hitRatio() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0 : (double) hits.get() / total * 100;
    }
}
