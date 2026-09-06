package com.unspektrawesome.camera.session;

/** Holds at most one frame and releases stale replacements immediately. */
final class NewestFrameSlot<T> {
    interface Releaser<T> { void release(T value); }

    static final class Entry<T> {
        final long timestampNs;
        final T value;
        Entry(long timestampNs, T value) { this.timestampNs = timestampNs; this.value = value; }
    }

    private final Releaser<T> releaser;
    private Entry<T> current;
    private long dropped;

    NewestFrameSlot(Releaser<T> releaser) { this.releaser = releaser; }

    synchronized void offer(long timestampNs, T value) {
        if (value == null) throw new IllegalArgumentException("Frame is null");
        if (current != null) {
            if (timestampNs <= current.timestampNs) {
                releaser.release(value);
                dropped++;
                return;
            }
            releaser.release(current.value);
            dropped++;
        }
        current = new Entry<>(timestampNs, value);
    }

    synchronized Entry<T> takeIfTimestamp(long timestampNs) {
        if (current == null || current.timestampNs != timestampNs) return null;
        Entry<T> result = current;
        current = null;
        return result;
    }

    synchronized void clear() {
        if (current != null) {
            releaser.release(current.value);
            current = null;
        }
    }

    synchronized long dropped() { return dropped; }
    synchronized boolean isEmpty() { return current == null; }
}
