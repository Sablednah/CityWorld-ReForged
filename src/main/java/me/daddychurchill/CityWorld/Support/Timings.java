package me.daddychurchill.CityWorld.Support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where worldgen time actually goes, per phase, behind a flag.
 *
 * <p><b>Why this exists.</b> The owner reported chunks stalling for "a good minute" next to a
 * Cataclysm structure. It was diagnosed twice from the code — first as the reservation scan, then as
 * the carve's cost — and a fix shipped for each. The second was measured afterwards against a control
 * and turned out to be inside the noise (ancient_city, 120 chunks: 125 ms/chunk before, 117/142/158
 * after). A third guess is not worth making. The stall only reproduces in an instance with Cataclysm
 * installed, which no dev checkout here has, so the measurement has to happen on the owner's machine.
 *
 * <p><b>Turn it on</b> with {@code -Dcityworld.timing=true} (it also follows
 * {@code -Dcityworld.padlog}). Two kinds of output:
 * <ul>
 *   <li>A <b>SLOW</b> line the instant any single call exceeds {@code -Dcityworld.timing.slowms}
 *       (default 250), naming the phase and the chunk. A minute-long stall is one call somewhere;
 *       this says which, and that is the whole question.
 *   <li>A <b>TIMING</b> table every {@code -Dcityworld.timing.every} chunks (default 200): total ms,
 *       call count, mean and worst per phase, worst first.
 * </ul>
 *
 * <p><b>Off costs nothing:</b> {@link #ON} is a static final boolean, so the JIT folds every guarded
 * block away once the class initialises. On, it costs two {@code System.nanoTime()} calls per phase
 * per chunk, which is the price of knowing.
 *
 * <p>⚠ This only ever logs. Nothing here stops, halts or exits anything — see CLAUDE.md on why the
 * shipped jar must contain no path to stopping a server.
 */
public final class Timings {

    private static final Logger LOGGER = LoggerFactory.getLogger("CityWorld");

    /** Whether to measure at all. Static final so the JIT can delete every call site when off. */
    public static final boolean ON = Boolean.parseBoolean(System.getProperty("cityworld.timing", "false"))
            || System.getProperty("cityworld.padlog") != null;

    private static final long SLOW_NANOS =
            Long.getLong("cityworld.timing.slowms", 250L) * 1_000_000L;
    private static final int EVERY = Integer.getInteger("cityworld.timing.every", 200);

    private static final Map<String, LongAdder> NANOS = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> CALLS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> WORST = new ConcurrentHashMap<>();
    private static final AtomicLong CHUNKS = new AtomicLong();

    private Timings() {
    }

    /**
     * In-flight phases, so the watchdog can catch one WHILE it is stuck.
     *
     * <p>A SLOW line is a post-mortem: it says a phase took 71 seconds, which is how the stall was
     * narrowed to plan.build, and then stops being useful — by the time it prints, the stack that
     * would name the actual method is gone. The watchdog samples the offending thread while it is
     * still in there.
     */
    private static final Map<Long, Object[]> INFLIGHT = new ConcurrentHashMap<>();
    private static final long STACK_NANOS =
            Long.getLong("cityworld.timing.stackms", 5000L) * 1_000_000L;
    private static volatile boolean watching;
    /**
     * How often the watchdog looks. Two seconds is right for hunting a 71-second stall and useless
     * for proving the watchdog works, because a phase that starts and ends inside one gap is never
     * seen — which is exactly what a first test showed (0 dumps, and nothing broken). Tunable so the
     * detector can be fired deliberately before it is trusted.
     */
    private static final long POLL_MS = Long.getLong("cityworld.timing.pollms", 2000L);

    /** Nanosecond clock, or 0 when off — pass the result straight back to {@link #stop}. */
    public static long start() {
        if (!ON)
            return 0L;
        long now = System.nanoTime();
        return now == 0L ? 1L : now;   // 0 is the "off" sentinel; never hand it back
    }

    /** As {@link #start}, but the watchdog may dump this thread's stack if it takes too long. */
    public static long startWatched(String phase) {
        if (!ON)
            return 0L;
        long t = start();
        INFLIGHT.put(Thread.currentThread().getId(),
                new Object[] { phase, t, Thread.currentThread(), Boolean.FALSE });
        ensureWatchdog();
        return t;
    }

    /** Pair with {@link #startWatched}. */
    public static void stopWatched(String phase, long started, int chunkX, int chunkZ) {
        if (!ON)
            return;
        INFLIGHT.remove(Thread.currentThread().getId());
        stop(phase, started, chunkX, chunkZ);
    }

    private static void ensureWatchdog() {
        if (watching)
            return;
        synchronized (Timings.class) {
            if (watching)
                return;
            watching = true;
            // ⚠ Daemon, and it only ever reads and logs. Nothing here stops, halts or exits
            // anything — see CLAUDE.md on why the shipped jar must contain no path to stopping a
            // server. A daemon thread also cannot hold the JVM open on its own.
            Thread t = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(POLL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    long now = System.nanoTime();
                    for (Object[] e : INFLIGHT.values()) {
                        if (Boolean.TRUE.equals(e[3]))
                            continue;                       // already dumped this one
                        long age = now - (Long) e[1];
                        if (age < STACK_NANOS)
                            continue;
                        e[3] = Boolean.TRUE;
                        Thread stuck = (Thread) e[2];
                        StringBuilder sb = new StringBuilder();
                        int shown = 0;
                        for (StackTraceElement f : stuck.getStackTrace()) {
                            String cn = f.getClassName();
                            if (!cn.startsWith("me.daddychurchill") && !cn.startsWith("net.minecraft"))
                                continue;
                            sb.append("\n        at ").append(f);
                            if (++shown >= 30)
                                break;
                        }
                        LOGGER.warn("TIMING STUCK: {} has been running {} ms on thread {}:{}",
                                e[0], age / 1_000_000L, stuck.getName(), sb);
                    }
                }
            }, "cityworld-timing-watchdog");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Record one call of {@code phase}. {@code chunkX,chunkZ} only appear in a SLOW line. */
    public static void stop(String phase, long started, int chunkX, int chunkZ) {
        if (!ON || started == 0L)
            return;
        long took = System.nanoTime() - started;
        NANOS.computeIfAbsent(phase, k -> new LongAdder()).add(took);
        CALLS.computeIfAbsent(phase, k -> new LongAdder()).increment();
        AtomicLong worst = WORST.computeIfAbsent(phase, k -> new AtomicLong());
        long seen;
        while (took > (seen = worst.get()) && !worst.compareAndSet(seen, took)) {
            // another thread raised it; re-read and try again
        }
        if (took >= SLOW_NANOS)
            LOGGER.warn("TIMING SLOW: {} took {} ms at chunk {},{}", phase, took / 1_000_000L,
                    chunkX, chunkZ);
    }

    /** Count a generated chunk, and print the table every {@code cityworld.timing.every} of them. */
    public static void chunkDone() {
        if (!ON)
            return;
        long n = CHUNKS.incrementAndGet();
        if (EVERY > 0 && n % EVERY == 0)
            report(n);
    }

    private static void report(long chunks) {
        StringBuilder out = new StringBuilder();
        NANOS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .forEach(e -> {
                    String phase = e.getKey();
                    long total = e.getValue().sum();
                    long calls = Math.max(1L, CALLS.get(phase).sum());
                    long worst = WORST.get(phase).get();
                    out.append(String.format("%n    %-14s %8d ms  %7d calls  mean %6.2f ms  worst %6d ms",
                            phase, total / 1_000_000L, calls,
                            total / 1_000_000.0 / calls, worst / 1_000_000L));
                });
        LOGGER.warn("TIMING after {} chunks:{}", chunks, out);
    }
}
