package me.daddychurchill.CityWorld.worldgen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * Waiting for another thread's build-once value (a platmap, a river region, a spring's course) without running
 * anyone else's work meanwhile.
 *
 * <p>{@link CompletableFuture#join} on a ForkJoin worker — every chunk-generation worker is one — does not just
 * wait: it takes other queued tasks from the pool and runs them on the waiting thread. One of those can be a chunk
 * that needs the very value this thread is building further down its own stack, and the thread then waits on
 * itself for ever. The owner's 26.2 world hung at "Preparing spawn area" that way (2026-10-13): a worker building a
 * river region waited for a spring's course, ran a neighbouring chunk's biome task while it waited, and that task
 * waited for the region; every other worker then queued behind the same region.
 */
public final class Waiting {

    private Waiting() {
    }

    /** {@code future}'s value, waited for by parking only (the pool may add a spare worker meanwhile). */
    public static <T> T join(CompletableFuture<T> future) {
        if (!future.isDone() && Thread.currentThread() instanceof ForkJoinWorkerThread) {
            CountDownLatch done = new CountDownLatch(1);
            future.whenComplete((value, failure) -> done.countDown());
            boolean interrupted = false;
            while (!future.isDone()) {
                try {
                    ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                        @Override
                        public boolean block() throws InterruptedException {
                            done.await();
                            return true;
                        }

                        @Override
                        public boolean isReleasable() {
                            return future.isDone();
                        }
                    });
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
        return future.join(); // done by now on a worker, so this only unwraps
    }
}
