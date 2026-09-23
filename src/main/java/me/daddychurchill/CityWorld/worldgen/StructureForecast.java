package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * SPIKE (2026-09-23 night): what vanilla WILL put in a chunk, computed before the chunk exists.
 *
 * <p>Vanilla's {@code ChunkGenerator.createStructures} is a pure function of the seed, the structure
 * sets, the generator's own height/biome answers and the template files -- nothing in it reads a
 * chunk except to count references, which does not affect placement. So the same call can be made
 * from the planner, for any chunk at any distance, and must produce the same {@code StructureStart}
 * (same pieces, same boxes) that the chunk will later carry. This class makes that call; the probe
 * checks the claim against the stored starts.
 */
public final class StructureForecast {
    private StructureForecast() {
    }

    /** One forecast start per structure set that fires in this chunk (vanilla stores at most one per set). */
    public static List<StructureStart> forecast(CityWorldChunkGenerator generator,
            ChunkGeneratorStructureState state, RegistryAccess registries, StructureTemplateManager templates,
            LevelHeightAccessor height, ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        List<StructureStart> out = new ArrayList<>();
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            StructurePlacement placement = set.value().placement();
            if (!placement.isStructureChunk(state, chunkX, chunkZ))
                continue;
            List<StructureSet.StructureSelectionEntry> entries = set.value().structures();
            if (entries.size() == 1) {
                StructureStart start = tryGenerate(entries.get(0), generator, state, registries, templates, height,
                        dimension, pos);
                if (start != null)
                    out.add(start);
                continue;
            }
            // Exactly vanilla's weighted draw order, including the RNG it seeds for it.
            List<StructureSet.StructureSelectionEntry> list = new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(state.getLevelSeed(), chunkX, chunkZ);
            int total = 0;
            for (StructureSet.StructureSelectionEntry e : list)
                total += e.weight();
            while (!list.isEmpty()) {
                int roll = random.nextInt(total);
                int k = 0;
                for (StructureSet.StructureSelectionEntry e : list) {
                    roll -= e.weight();
                    if (roll < 0)
                        break;
                    k++;
                }
                StructureSet.StructureSelectionEntry chosen = list.get(k);
                StructureStart start = tryGenerate(chosen, generator, state, registries, templates, height,
                        dimension, pos);
                if (start != null) {
                    out.add(start);
                    break;
                }
                list.remove(k);
                total -= chosen.weight();
            }
        }
        return out;
    }

    private static StructureStart tryGenerate(StructureSet.StructureSelectionEntry entry,
            CityWorldChunkGenerator generator, ChunkGeneratorStructureState state, RegistryAccess registries,
            StructureTemplateManager templates, LevelHeightAccessor height, ResourceKey<Level> dimension,
            ChunkPos pos) {
        var structure = entry.structure().value();
        Predicate<Holder<Biome>> validBiome = structure.biomes()::contains;
        // references = 0: vanilla's fetchReferences counts existing references, which only feeds the
        // start's own counter and never its placement.
        StructureStart start = structure.generate(entry.structure(), dimension, registries, generator,
                generator.getBiomeSource(), state.randomState(), templates, state.getLevelSeed(), pos, 0, height,
                validBiome);
        return start.isValid() ? start : null;
    }
}
