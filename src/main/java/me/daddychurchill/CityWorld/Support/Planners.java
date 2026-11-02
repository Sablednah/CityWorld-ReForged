package me.daddychurchill.CityWorld.Support;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The one pool CityWorld plans on when it is not a chunk worker: {@code /cityfind}, the find API's ring searches,
 * the map overlay, the hover lookups, {@code lotAtAsync}. Bounded (two to four daemon threads, at the lowest
 * priority) because platmaps are built once however many threads ask — a second asker joins the first's future —
 * so more planners mostly meant more threads for the server thread to be preempted by. There were five pools and a
 * raw {@code Thread} per {@code /cityfind} before (the ZARP audit, 2026-11-02).
 *
 * <p>Nothing here may touch the level: a task that needs it hops back with {@code server.execute(...)}.
 */
public final class Planners {

    private Planners() {}

    private static final AtomicInteger N = new AtomicInteger();

    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "cityworld-planner-" + N.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    };

    /** The shared planning pool. */
    public static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)), FACTORY);

    /** Run a planning task on the pool (a raw {@code new Thread} was the pattern before). */
    public static void run(Runnable task) {
        POOL.execute(task);
    }
}
