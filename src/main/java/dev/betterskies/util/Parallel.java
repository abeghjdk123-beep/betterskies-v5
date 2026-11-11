package dev.betterskies.util;

import java.util.concurrent.ForkJoinPool;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Runs per-row texture work on a small pool that uses at most half the CPU cores,
 * so painting stars and clouds never starves world loading or chunk building.
 */
public final class Parallel {
    private Parallel() {
    }

    private static final ForkJoinPool POOL = new ForkJoinPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            pool -> {
                var t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("BetterSkies painter " + t.getPoolIndex());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }, null, false);

    public static void rows(int count, IntConsumer row) {
        POOL.submit(() -> IntStream.range(0, count).parallel().forEach(row)).join();
    }
}
