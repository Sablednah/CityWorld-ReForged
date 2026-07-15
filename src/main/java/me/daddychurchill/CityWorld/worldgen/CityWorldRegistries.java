package me.daddychurchill.CityWorld.worldgen;

import com.mojang.serialization.MapCodec;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration of CityWorld's worldgen types. For now just the custom {@link ChunkGenerator}
 * codec, registered under {@code cityworld:city} — the id referenced by the dimension and world
 * preset JSON in {@code src/main/resources/data/cityworld/...}.
 */
public final class CityWorldRegistries {

    private CityWorldRegistries() {
    }

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, CityWorldMod.MODID);

    static {
        CHUNK_GENERATORS.register("city", () -> CityWorldChunkGenerator.CODEC);
    }

    /** Wire the deferred registers onto the mod event bus. Called from the mod constructor. */
    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}
