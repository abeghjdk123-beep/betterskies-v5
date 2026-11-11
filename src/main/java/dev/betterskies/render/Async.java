package dev.betterskies.render;

import dev.betterskies.BetterSkies;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A value computed on the background worker. Ask for it with a key every frame;
 * when the key changes a new job starts (one at a time, newest key wins) and the
 * previous value stays visible until the new one is ready.
 */
public final class Async<K, V> {
    private final String what;
    private K readyKey;
    private V ready;
    private K runningKey;
    private CompletableFuture<V> running;
    private K wantedKey;
    private Supplier<V> wantedJob;
    private boolean fresh;
    private String error;

    public Async(String what) {
        this.what = what;
    }

    /** Requests the value for key; returns whatever is ready (maybe for an older key, maybe null). */
    public V get(K key, Supplier<V> job) {
        poll();
        if (!Objects.equals(key, readyKey) && !Objects.equals(key, runningKey)) {
            wantedKey = key;
            wantedJob = job;
            start();
        }
        return ready;
    }

    /** True once after a new value became ready (time to upload it). */
    public boolean takeFresh() {
        poll();
        boolean f = fresh;
        fresh = false;
        return f;
    }

    public boolean busy() {
        return running != null && !running.isDone();
    }

    public String error() {
        return error;
    }

    public void clear() {
        ready = null;
        readyKey = null;
        wantedKey = null;
        wantedJob = null;
    }

    private void poll() {
        if (running != null && running.isDone()) {
            try {
                V v = running.join();
                ready = v;
                readyKey = runningKey;
                fresh = true;
                error = null;
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                error = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                BetterSkies.LOG.warn("[BetterSkies] {} failed: {}", what, error);
                ready = null;
                readyKey = runningKey;
                fresh = true;
            }
            running = null;
            runningKey = null;
            if (wantedJob != null) {
                start();
            }
        }
    }

    private void start() {
        if (running != null || wantedJob == null) {
            return;
        }
        Supplier<V> job = wantedJob;
        runningKey = wantedKey;
        wantedJob = null;
        wantedKey = null;
        running = CompletableFuture.supplyAsync(job, BetterSkies.WORKER);
    }
}
