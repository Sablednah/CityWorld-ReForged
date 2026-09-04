package me.daddychurchill.CityWorld.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.ClipboardLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * CityWorld's public "what did the generator plan here?" API — the modern port of the Bukkit
 * {@code CityWorldAPI} (contributed to upstream by Sablednah, PR #4/#5). Read-only introspection over
 * the seed-deterministic plan: it re-derives a chunk's context, lot, schematic and shop from the plan
 * (the same path {@code /cityinfo} uses), so answers are correct even for chunks that were never
 * generated, and there is nothing to persist.
 *
 * <p>Two ways in: {@link #lotAt} returns a typed {@link LotInfo}; {@link #getFullInfo} returns the
 * stringly-typed {@code Map} shape the original Bukkit API returned, for continuity. Shops have their
 * own focused facade in {@link CityWorldShops}. Everything fails soft — a non-CityWorld level yields an
 * empty result rather than throwing. Call on the server thread (the underlying plan is deterministic
 * and safe to read).
 */
public final class CityWorldAPI {

    private CityWorldAPI() {}

    /** The plan for the chunk containing {@code pos}, or empty if {@code level} is not a CityWorld level. */
    public static Optional<LotInfo> lotAt(ServerLevel level, BlockPos pos) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return Optional.empty();
        return lotInfo(context, level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * The plan as a {@code Map<String,String>} with the keys the original Bukkit
     * {@code CityWorldAPI.getFullInfo} used — {@code context}, {@code contextclass}, {@code lot},
     * {@code lotclass}, {@code at}, and {@code schematic} (only when this lot is a schematic) — plus two
     * additive keys, {@code roads} and {@code shop} (only when this lot is a shop). Empty map off a
     * non-CityWorld level.
     */
    public static Map<String, String> getFullInfo(ServerLevel level, BlockPos pos) {
        return lotAt(level, pos).map(CityWorldAPI::toMap).orElseGet(Map::of);
    }

    // --- internals ------------------------------------------------------------------------------

    private static CityWorldGenerator contextFor(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator cityGenerator)
            return cityGenerator.getContext(level);
        return null;
    }

    private static Optional<LotInfo> lotInfo(CityWorldGenerator context, ResourceKey<Level> dim, int cx, int cz) {
        try {
            PlatMap platmap = context.getPlatMap(cx, cz);
            PlatLot lot = platmap.getMapLot(cx, cz);
            String schematic = lot instanceof ClipboardLot clip ? clip.getClip().name : null;
            return Optional.of(new LotInfo(
                    dim,
                    new ChunkPos(cx, cz),
                    platmap.context.getSchematicFamily(),
                    platmap.context.getClass().getSimpleName(),
                    lot.style,
                    lot.getClass().getSimpleName(),
                    platmap.getNaturePercent(),
                    platmap.getNumberOfRoads(),
                    schematic,
                    lot.getShopType(),
                    lot.getInteriorDescription()));
        } catch (RuntimeException e) {
            return Optional.empty(); // never let a lookup throw into a caller
        }
    }

    private static Map<String, String> toMap(LotInfo i) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("context", i.contextFamily().toString());
        m.put("contextclass", i.contextClass());
        m.put("lot", i.lotStyle().toString());
        if (i.interior() != null)
            m.put("interior", i.interior());
        m.put("lotclass", i.lotClass());
        m.put("at", i.chunk().x() + "|" + i.chunk().z());
        m.put("roads", Integer.toString(i.roadCount()));
        if (i.schematicName() != null)
            m.put("schematic", i.schematicName());
        if (i.shop() != null)
            m.put("shop", i.shop().describe());
        return m;
    }
}
