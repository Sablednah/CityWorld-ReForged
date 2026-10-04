package me.daddychurchill.CityWorld.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The biome source of a vanilla-terrain world ({@code cityworld:vanilla}): another biome source — vanilla's
 * {@code multi_noise} overworld, in the shipped preset — asked with the right climate.
 *
 * <p><b>Why not simply name vanilla's source in the preset.</b> A multi-noise source answers from the
 * {@code Climate.Sampler} it is handed, and Minecraft builds a real one only for a
 * {@code NoiseBasedChunkGenerator}; any other generator's is a dummy that answers zero everywhere, so every
 * question — structure placement, {@code /locate biome}, spawn — would get one biome for the whole world. The chunk
 * generator makes the real sampler for this world's seed and binds it here; from then on the sampler passed in is
 * ignored. The same reason {@link CityWorldEndBiomeSource} exists.
 *
 * <p>Because the wrapped source is vanilla's own class, whatever mixes into it comes along.
 */
public class CityWorldVanillaBiomeSource extends BiomeSource {

    public static final MapCodec<CityWorldVanillaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(CityWorldVanillaBiomeSource::source))
            .apply(i, CityWorldVanillaBiomeSource::new));

    private final BiomeSource source;
    private volatile Climate.Sampler sampler;

    public CityWorldVanillaBiomeSource(BiomeSource source) {
        this.source = source;
    }

    /** The wrapped source, for the vanilla generator the chunk generator builds around it. */
    public BiomeSource source() {
        return source;
    }

    /** This world's real climate; rebinding is allowed (a client makes one generator per world it opens). */
    public void bind(Climate.Sampler sampler) {
        this.sampler = sampler;
    }

    /** One instance: codec dispatch compares by identity (see CityWorldEndBiomeSource.DISPATCH). */
    public static final com.mojang.serialization.Codec<CityWorldVanillaBiomeSource> DISPATCH = CODEC.codec();

    @Override
    protected com.mojang.serialization.Codec<? extends BiomeSource> codec() {
        return DISPATCH; // 1.20.1's BiomeSource dispatches on a plain Codec
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return source.possibleBiomes().stream().filter(java.util.Objects::nonNull);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler handed) {
        Climate.Sampler real = sampler;
        return source.getNoiseBiome(x, y, z, real != null ? real : handed);
    }
}
