package me.daddychurchill.CityWorld.worldgen;

import com.mojang.serialization.MapCodec;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration of CityWorld's worldgen types:
 * <ul>
 *   <li>the custom {@link ChunkGenerator} codec, under {@code cityworld:city} — the id referenced by
 *       the dimension and world preset JSON in {@code src/main/resources/data/cityworld/...};
 *   <li>the {@code cityworld:world_settings} <b>datapack registry</b> whose entries are
 *       {@link CityWorldSettingsData} — the per-world settings a generator references by holder
 *       (PORTING.md P7 / top risk #4). Entries load from
 *       {@code data/<namespace>/cityworld/world_settings/<name>.json}; the bundled
 *       {@code cityworld:default} carries the upstream defaults.
 * </ul>
 */
public final class CityWorldRegistries {

    private CityWorldRegistries() {
    }

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, CityWorldMod.MODID);

    /** Root key of the per-world settings datapack registry. */
    public static final ResourceKey<Registry<CityWorldSettingsData>> WORLD_SETTINGS =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "world_settings"));

    /** The bundled default profile — what {@code cityworld:city} and the presets reference. */
    public static final ResourceKey<CityWorldSettingsData> DEFAULT_SETTINGS =
            ResourceKey.create(WORLD_SETTINGS, Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "default"));

    static {
        CHUNK_GENERATORS.register("city", () -> CityWorldChunkGenerator.CODEC);
    }

    /** Wire the deferred registers and datapack-registry listener onto the mod event bus. */
    public static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        modEventBus.addListener(CityWorldRegistries::onNewDataPackRegistry);
    }

    private static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        // Unsynced (no network codec): the settings only drive server-side worldgen, so a client need
        // not carry them — and the mod itself is required on both sides for the custom generator.
        event.dataPackRegistry(WORLD_SETTINGS, CityWorldSettingsData.CODEC);
    }
}
