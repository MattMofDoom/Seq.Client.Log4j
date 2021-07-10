package com.mattmofdoom.logging.log4j2.seqappender;

import java.util.ArrayList;
import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.map.LRUMap;

@SuppressWarnings({"UnnecessaryThis", "UnnecessaryFinalOnLocalVariableOrParameter", "BusyWait"})
public class SeqAppenderCache<K, T> {

    private final long timeToLive;
    private final LRUMap<K, cacheObject> cacheMap;

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

        this.cacheMap = new LRUMap<>();

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
        synchronized (this.cacheMap) {
            this.cacheMap.put(key, new cacheObject(value));
        }
    }

    public T get(final K key) {
        synchronized (this.cacheMap) {
            final cacheObject c = this.cacheMap.get(key);

            if (c == null)
                return null;
            else {
                c.lastAccessed = System.currentTimeMillis();
                return c.value;
            }
        }
    }

    public void remove(final K key) {
        synchronized (this.cacheMap) {
            this.cacheMap.remove(key);
        }
    }

    @SuppressWarnings("unused")
    public int size() {
        synchronized (this.cacheMap) {
            return this.cacheMap.size();
        }
    }

    public void cleanup() {

        final long now = System.currentTimeMillis();
        final ArrayList<K> deleteKey;

        synchronized (this.cacheMap) {
            final MapIterator<K, cacheObject> itr = this.cacheMap.mapIterator();

            deleteKey = new ArrayList<>((this.cacheMap.size() / 2) + 1);
            K key;
            cacheObject c;

            while (itr.hasNext()) {
                key = itr.next();
                c = itr.getValue();

                if (c != null && (now > (this.timeToLive + c.lastAccessed))) {
                    deleteKey.add(key);
                }
            }
        }

        for (final K key : deleteKey) {
            synchronized (this.cacheMap) {
                this.cacheMap.remove(key);
            }

            Thread.yield();
        }
    }
}