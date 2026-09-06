package com.unspektrawesome.camera.session;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded exact matcher for capture metadata keyed by sensor timestamp. */
public final class TimestampMetadataMatcher<T> {
    private final int capacity;
    private final LinkedHashMap<Long, T> values = new LinkedHashMap<>();
    private long evictions;

    public TimestampMetadataMatcher(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void put(long sensorTimestampNs, T metadata) {
        if (sensorTimestampNs <= 0) throw new IllegalArgumentException("Timestamp must be positive");
        if (metadata == null) throw new IllegalArgumentException("Metadata is null");
        values.remove(sensorTimestampNs);
        values.put(sensorTimestampNs, metadata);
        while (values.size() > capacity) {
            Iterator<Map.Entry<Long, T>> iterator = values.entrySet().iterator();
            iterator.next();
            iterator.remove();
            evictions++;
        }
    }

    public synchronized T take(long sensorTimestampNs) {
        return values.remove(sensorTimestampNs);
    }

    public synchronized int size() { return values.size(); }
    public synchronized long evictions() { return evictions; }
    public synchronized void clear() { values.clear(); }
}
