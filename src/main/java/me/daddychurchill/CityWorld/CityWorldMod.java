package me.daddychurchill.CityWorld;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.daddychurchill.CityWorld.worldgen.CityWorldRegistries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * CityWorld — main mod entrypoint (common: loaded on both client and dedicated server).
 *
 * <p>A modern NeoForge rewrite of the classic CityWorld Bukkit plugin, which procedurally
 * generates endless cities, roads, buildings, mines, sewers, farms and nature. The generation
 * "brain" (PlatMap / PlatLot / Context / providers) is being ported largely intact; the Bukkit
 * block-writing layer ({@code AbstractBlocks} and friends) is being reimplemented against modern
 * {@code ChunkAccess}/{@code BlockState}, and the whole thing is wrapped in a codec-registered
 * custom {@code ChunkGenerator} exposed as both a dimension and a world preset.
 *
 * <p>See {@code PORTING.md} in the reference (Bukkit) repo for the phased plan. This is Phase 0:
 * an empty, buildable NeoForge scaffold.
 */
@Mod(CityWorldMod.MODID)
public class CityWorldMod {

    /** The mod id — must match {@code mod_id} in gradle.properties and the modId in neoforge.mods.toml. */
    public static final String MODID = "cityworld";

    /** Shared logger. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public CityWorldMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the custom ChunkGenerator codec (cityworld:city). The dimension and world
        // preset that reference it live in src/main/resources/data/cityworld/...
        CityWorldRegistries.register(modEventBus);

        // Per-world settings are datapack-driven (the cityworld:world_settings registry, registered
        // above), not a per-instance config — see CityWorldSettingsData / PORTING.md top risk #4.

        // Server-side registrations on the game event bus (commands: /cityinfo, /cityworld).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(CityWorldServerEvents.class);

        // Client-only: the create-world "Customize" button for the CityWorld world type. Guarded so
        // the dedicated server never loads the @OnlyIn(CLIENT) preset-editor classes.
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT)
            me.daddychurchill.CityWorld.client.CityWorldClient.init(modEventBus);

        // Create the drop-in folder (config/cityworld/schematics/) so players can add their own
        // schematics without a rebuild; the library scans it alongside the bundled set.
        me.daddychurchill.CityWorld.Clipboard.SchematicLibrary.ensureExternalFolder();

        // Drop a copy-and-edit settings example (config/cityworld/settings-example/) on first run:
        // a full default datapack + a plain-text reference explaining every knob (P7).
        me.daddychurchill.CityWorld.worldgen.SettingsExample.ensureExampleFolder();

        LOGGER.info("CityWorld {} initialising (NeoForge port)",
                modContainer.getModInfo().getVersion());
    }
}
