package com.mattmofdoom.logging.log4j2.seqappender;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"UnnecessaryThis", "UnnecessaryFinalOnLocalVariableOrParameter", "BusyWait"})
public class SeqAppenderCache<K, T> {

    private final long timeToLive;
    private final ConcurrentHashMap<K, cacheObject> cacheMap;

    @SuppressWarnings("UnnecessaryFinalOnLocalVariableOrParameter")
    protected class cacheObject {
        public long lastAccessed = System.currentTimeMillis();
        public final T value;

        protected cacheObject(final T value) {
            this.value = value;
        }
    }

    public SeqAppenderCache(final long timeToLive, final long timerInterval) {
        this.timeToLive = timeToLive * 1000;

        this.cacheMap = new ConcurrentHashMap<>();

        if (timeToLive > 0 && timerInterval > 0) {

            final Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(timerInterval * 1000);
                    } catch (final InterruptedException ignored) {
                    }
                    this.cleanup();
                }
            });

            t.setDaemon(true);
            t.start();
        }
    }

    public void put(final K key, final T value) {
            this.cacheMap.put(key, new cacheObject(value));
    }

    public T get(final K key) {
            final cacheObject c = this.cacheMap.get(key);

            if (c == null)
                return null;
            else {
                c.lastAccessed = System.currentTimeMillis();
                return c.value;
            }
    }

    public void remove(final K key) {
        this.cacheMap.remove(key);
    }

    @SuppressWarnings("unused")
    public int size() {
        return this.cacheMap.size();
    }

    public void cleanup() {

        final long now = System.currentTimeMillis();
        final ArrayList<K> deleteKey;

        final Iterator<Map.Entry<K, cacheObject>> itr = this.cacheMap.entrySet().iterator();

        deleteKey = new ArrayList<>((this.cacheMap.size() / 2) + 1);

        while (itr.hasNext()) {
            final var entry = itr.next();
            final var k = entry.getKey();
            final var c = entry.getValue();

            if (c != null && (now > (this.timeToLive + c.lastAccessed))) {
                deleteKey.add(k);
            }
        }


        for (final K key : deleteKey) {
            this.cacheMap.remove(key);

            Thread.yield();
        }
    }
}