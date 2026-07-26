package me.daddychurchill.CityWorld.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * CityWorld's <b>public shop-lookup API</b> — the supported way for other mods to ask "is there a shop
 * here, and what does it sell?". This is the surface referred to when the docs say the shop layer is
 * exposed for other mods to react to.
 *
 * <p><b>Query-only, no persistence.</b> A shop's classification is decided seed-deterministically at
 * plan time, so these methods simply re-derive it from the world's CityWorld plan (the same path
 * {@code /cityinfo} uses) — no saved data, and correct even for chunks that have never been generated.
 * Repeated lookups in an area are cheap: platmap plans are cached after first use.
 *
 * <p><b>Threading.</b> Call on the server thread (as commands and normal mod ticking do). The
 * underlying plan is deterministic and thread-safe to read, but resolving the generator from a level
 * assumes a live server level. Every method fails soft: a non-CityWorld level yields empty results
 * rather than throwing.
 */
public final class CityWorldShops {

    private CityWorldShops() {}

    /** The shop at {@code pos}, if the lot covering that block is a shop and the level is a CityWorld level. */
    public static Optional<ShopInfo> shopAt(ServerLevel level, BlockPos pos) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return Optional.empty();
        return shopAtChunk(context, level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * Every shop within {@code chunkRadius} chunks of {@code center} (a square Chebyshev neighbourhood),
     * nearest not guaranteed first. Handy for "react to the shops around the player". A radius of 0 is
     * just the centre chunk; keep it modest (each chunk is one lot lookup, and a cold area plans its
     * platmaps on first touch).
     */
    public static List<ShopInfo> shopsNear(ServerLevel level, BlockPos center, int chunkRadius) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return List.of();
        ResourceKey<Level> dim = level.dimension();
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        int r = Math.max(0, chunkRadius);
        List<ShopInfo> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                shopAtChunk(context, dim, ccx + dx, ccz + dz).ifPresent(out::add);
        return out;
    }

    // --- internals ------------------------------------------------------------------------------

    private static CityWorldGenerator contextFor(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator cityGenerator)
            return cityGenerator.getContext(level);
        return null;
    }

    private static Optional<ShopInfo> shopAtChunk(CityWorldGenerator context, ResourceKey<Level> dim, int cx, int cz) {
        try {
            PlatMap platmap = context.getPlatMap(cx, cz);
            PlatLot lot = platmap.getMapLot(cx, cz);
            ShopType type = lot.getShopType();
            return type == null ? Optional.empty() : Optional.of(new ShopInfo(type, new ChunkPos(cx, cz), dim));
        } catch (RuntimeException e) {
            return Optional.empty(); // never let a lookup throw into a caller
        }
    }
}
