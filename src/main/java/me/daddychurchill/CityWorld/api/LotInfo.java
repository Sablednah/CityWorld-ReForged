package me.daddychurchill.CityWorld.api;

import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * A read-only snapshot of what CityWorld planned for one chunk — the modern, typed successor to the
 * {@code HashMap<String,String>} that the original Bukkit {@code CityWorldAPI.getFullInfo} returned
 * (contributed to upstream by Sablednah, PR #4/#5). Everything here is derived from the
 * seed-deterministic plan, so it is correct without the chunk having been generated. Obtain one via
 * {@link CityWorldAPI#lotAt}.
 *
 * @param dimension     the level this lot lives in
 * @param chunk         the chunk this lot occupies
 * @param contextFamily the district family ({@code NATURE}, {@code CITY}, …) — the coarse "what kind of place"
 * @param contextClass  the district context's simple class name (e.g. {@code MidriseContext})
 * @param lotStyle      the lot's coarse style ({@code NATURE}/{@code STRUCTURE}/{@code ROAD}/{@code ROUNDABOUT})
 * @param lotClass      the lot's simple class name (e.g. {@code StoreBuildingLot})
 * @param naturePercent how wild this platmap graded (0.0 = dense city … 1.0 = wilderness)
 * @param roadCount     number of road lots in this lot's platmap
 * @param schematicName the placed schematic's name, or {@code null} if this lot is not a schematic
 * @param shop          this lot's {@link ShopType}, or {@code null} if it is not a shop
 * @param interior      what the interior is furnished as ("Courthouse", "Apartments"…), or {@code null}
 */
public record LotInfo(
        ResourceKey<Level> dimension,
        ChunkPos chunk,
        SchematicFamily contextFamily,
        String contextClass,
        LotStyle lotStyle,
        String lotClass,
        double naturePercent,
        int roadCount,
        @Nullable String schematicName,
        @Nullable ShopType shop,
        @Nullable String interior) {

    /** True if this lot is a placed schematic (see {@link #schematicName()}). */
    public boolean isSchematic() {
        return schematicName != null;
    }

    /** True if this lot is a shop (see {@link #shop()} and {@link CityWorldShops}). */
    public boolean isShop() {
        return shop != null;
    }
}
