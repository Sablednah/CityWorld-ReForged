package me.daddychurchill.CityWorld.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * A shop located in the world: its {@link ShopType} plus <em>where</em> it is (the chunk it anchors to
 * and the dimension it lives in). This is what {@link CityWorldShops} hands back to callers asking
 * "what shop is here / near me". Each chunk of a multi-chunk store building is a shop of its own, with
 * its own trade (since 5.18.0; before that a building reported one {@code ShopType} at every chunk).
 */
public record ShopInfo(ShopType type, ChunkPos chunk, ResourceKey<Level> dimension) {

    public ShopScale scale() {
        return type.scale();
    }

    public ShopTrade trade() {
        return type.trade();
    }
}
