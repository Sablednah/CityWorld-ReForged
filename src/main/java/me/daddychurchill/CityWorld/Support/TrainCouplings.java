package me.daddychurchill.CityWorld.Support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import me.daddychurchill.CityWorld.CityWorldMod;

/**
 * Couples a parked train once it is in the world ({@link Railway#parkTrain}). A train mod keeps its couplings in
 * its own world data, keyed by the vehicles' ids, so a train cannot be coupled while its chunk is still being
 * generated; each parked carriage instead carries a tag naming the vehicle ahead of it, and when it joins the
 * world it is queued; once a second, for half a minute, the server tick looks both vehicles up by id and, when
 * both are in the world, asks the mod to couple their two nearest ends. Then the tag goes. (A vehicle that has
 * joined is not found by id until its chunk is in the world proper, which under load is many ticks later; and a
 * dedicated server with nobody on it stops ticking after a minute, so a headless check must switch that off.) Choo Choo is the one mod it knows, by reflection, so nothing here loads
 * without it; a carriage of any other mod keeps standing where it was parked, uncoupled.
 */
public final class TrainCouplings {

    private TrainCouplings() {
    }

    /** From the entity-join event: a parked carriage names the vehicle it couples to. */
    public static void joined(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level))
            return;
        String tag = null;
        for (String t : entity.entityTags())
            if (t.startsWith(Railway.COUPLE_TAG)) {
                tag = t;
                break;
            }
        if (tag == null)
            return;
        UUID ahead;
        try {
            ahead = UUID.fromString(tag.substring(Railway.COUPLE_TAG.length()));
        } catch (IllegalArgumentException e) {
            entity.removeTag(tag);
            return;
        }
        PENDING.add(new Pending(level, entity.getUUID(), ahead, tag, level.getServer().getTickCount() + 20, 30));
    }

    /** A carriage waiting for the vehicle ahead to be in the world. */
    private record Pending(ServerLevel level, UUID back, UUID ahead, String tag, int due, int tries) {
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<Pending> PENDING = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /**
     * From the server's tick: try each waiting carriage once a second, for half a minute. A train's vehicles
     * join with their chunk but are not found by id until the chunk is in the world proper, which under load
     * can be many ticks later (the first probe looked on the next tick, twenty times, and never found one).
     * One that never finds its partner keeps its tag, and the next load tries again.
     */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty())
            return;
        int now = server.getTickCount();
        for (int i = PENDING.size(); i > 0; i--) {
            Pending p = PENDING.poll();
            if (p == null)
                break;
            if (now < p.due()) {
                PENDING.add(p);
                continue;
            }
            // both by id: the entity a join event hands over is not always the one that stays in the world
            Entity front = p.level().getEntity(p.ahead()), back = p.level().getEntity(p.back());
            if (front == null || back == null) {
                if (p.tries() > 0)
                    PENDING.add(new Pending(p.level(), p.back(), p.ahead(), p.tag(), now + 20, p.tries() - 1));
                continue;
            }
            if (couple(p.level(), front, back))
                back.removeTag(p.tag());
        }
    }

    /** A server stopped: nothing waiting outlives it. */
    public static void clear() {
        PENDING.clear();
    }

    private static volatile Object[] chooChoo; // {get, End(UUID, boolean), connect, anchor, chain item id}

    private static Object[] chooChoo() {
        Object[] c = chooChoo;
        if (c != null)
            return c.length == 0 ? null : c;
        try {
            Class<?> data = Class.forName("com.choochoo.train.CouplingData");
            Class<?> end = Class.forName("com.choochoo.train.CouplingData$End");
            Class<?> cart = Class.forName("com.choochoo.train.CoupledMinecart");
            c = new Object[] { data.getMethod("get", ServerLevel.class), end.getConstructor(UUID.class, boolean.class),
                    data.getMethod("connect", end, end, ServerLevel.class, ItemStack.class),
                    cart.getMethod("anchor", boolean.class, float.class), cart };
        } catch (ReflectiveOperationException | LinkageError e) {
            c = new Object[0];
        }
        chooChoo = c;
        return c.length == 0 ? null : c;
    }

    /** Couple {@code back} behind {@code front} at their two nearest ends. */
    static boolean couple(ServerLevel level, Entity front, Entity back) {
        Object[] c = chooChoo();
        if (c == null || !((Class<?>) c[4]).isInstance(front) || !((Class<?>) c[4]).isInstance(back))
            return false;
        try {
            Method anchor = (Method) c[3];
            boolean bestA = true, bestB = false;
            double best = Double.MAX_VALUE;
            for (boolean ra : new boolean[] { true, false })
                for (boolean rb : new boolean[] { true, false }) {
                    double d = ((Vec3) anchor.invoke(front, ra, 1f)).distanceTo((Vec3) anchor.invoke(back, rb, 1f));
                    if (d < best) {
                        best = d;
                        bestA = ra;
                        bestB = rb;
                    }
                }
            @SuppressWarnings("unchecked")
            Constructor<Object> end = (Constructor<Object>) c[1];
            Object data = ((Method) c[0]).invoke(null, level);
            var chain = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("choochoo", "coupling_chain"));
            boolean ok = (Boolean) ((Method) c[2]).invoke(data, end.newInstance(front.getUUID(), bestA),
                    end.newInstance(back.getUUID(), bestB), level, new ItemStack(chain));
            if (!ok)
                CityWorldMod.LOGGER.warn("CityWorld: Choo Choo would not couple {} to {} ({} apart)", back, front, best);
            return ok;
        } catch (ReflectiveOperationException | RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld: could not couple a parked train: {}", e.toString());
            return true; // drop the tag: trying again would fail the same way
        }
    }
}
