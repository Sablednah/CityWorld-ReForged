package me.daddychurchill.CityWorld.worldgen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import org.jspecify.annotations.Nullable;

/**
 * Lets <b>every installed TerraBlender biome mod</b> contribute biomes to a CityWorld world.
 *
 * <p>Biomes O' Plenty and most other modern biome mods do not register biomes themselves — they
 * register <em>regions</em> with <a href="https://github.com/Glitchfiend/TerraBlender">TerraBlender</a>,
 * which injects them into vanilla's {@code MultiNoiseBiomeSource}. CityWorld replaces the biome source
 * outright, so none of that reaches us: a CityWorld world with BoP installed gets BoP's 450 blocks
 * (they ride vanilla block tags) and none of its 69 biomes. One integration fixes that for the whole
 * family of mods at once, which is why this is worth a class of its own.
 *
 * <p><b>The read path is public, which is the part that was in doubt.</b> TerraBlender's API exists to
 * <em>register</em> regions, but {@code Regions.get(RegionType)} is public and static, and
 * {@code Region.addBiomes} hands out every {@code (Climate.ParameterPoint → biome)} pair a mod
 * declared. Harvest those into a {@link Climate.ParameterList} and it answers "which biome for this
 * climate", which is exactly what a biome source needs.
 *
 * <p><b>Reflection, deliberately, and this is the trade.</b> A compile-time dependency would have to be
 * resolved for all three Minecraft branches and would make TerraBlender a build requirement for a
 * feature that is optional at runtime. The API used here is three calls and one enum constant, so the
 * reflection is small and its failure mode is contained: anything unexpected disables the bridge and
 * leaves the built-in palette, rather than breaking world generation.
 *
 * <p><b>Nothing outside this class may reference TerraBlender</b>, even in a signature. The JVM
 * class-loads eagerly, so a stray import would make a world <em>without</em> TerraBlender die on
 * {@code NoClassDefFoundError}. Everything here is {@code Object} and reflection for that reason.
 */
public final class TerraBlenderBridge {

    private static final String MOD_ID = "terrablender";
    private static final String REGIONS = "terrablender.api.Regions";
    private static final String REGION_TYPE = "terrablender.api.RegionType";

    /** Whether TerraBlender is installed. Checked once; the answer cannot change while the game runs. */
    private static final boolean PRESENT = detect();

    private static boolean detect() {
        try {
            return net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false; // no mod list (datagen, tests) — treat as absent
        }
    }

    public static boolean present() {
        return PRESENT;
    }

    private final Climate.ParameterList<Holder<Biome>> parameters;
    private final List<Holder<Biome>> biomes;

    private TerraBlenderBridge(Climate.ParameterList<Holder<Biome>> parameters, List<Holder<Biome>> biomes) {
        this.parameters = parameters;
        this.biomes = biomes;
    }

    /**
     * Harvests every overworld region every TerraBlender mod registered, or {@code null} if TerraBlender
     * is absent, contributed nothing, or anything at all went wrong.
     *
     * <p>Call once per world, after registries are frozen. {@code biomeRegistry} must be a real
     * {@link Registry} because that is what {@code Region.addBiomes} takes.
     */
    public static @Nullable TerraBlenderBridge harvest(Registry<Biome> biomeRegistry, HolderGetter<Biome> biomes) {
        if (!PRESENT)
            return null;
        try {
            Class<?> regionsClass = Class.forName(REGIONS);
            @SuppressWarnings("unchecked")
            Class<Enum<?>> regionTypeClass = (Class<Enum<?>>) Class.forName(REGION_TYPE);
            Object overworld = null;
            for (Object constant : regionTypeClass.getEnumConstants())
                if ("OVERWORLD".equals(((Enum<?>) constant).name()))
                    overworld = constant;
            if (overworld == null)
                return null;

            Method get = regionsClass.getMethod("get", regionTypeClass);
            Object regionList = get.invoke(null, overworld);
            if (!(regionList instanceof List<?> regions) || regions.isEmpty())
                return null;

            List<Pair<Climate.ParameterPoint, Holder<Biome>>> collected = new ArrayList<>();
            java.util.LinkedHashSet<Holder<Biome>> distinct = new java.util.LinkedHashSet<>();

            for (Object region : regions) {
                Method addBiomes = region.getClass().getMethod("addBiomes", Registry.class,
                        java.util.function.Consumer.class);
                addBiomes.setAccessible(true);
                java.util.function.Consumer<Object> sink = pair -> {
                    // Pair<Climate.ParameterPoint, ResourceKey<Biome>> — vanilla types on both sides,
                    // so no TerraBlender class escapes this method.
                    if (!(pair instanceof Pair<?, ?> p))
                        return;
                    if (!(p.getFirst() instanceof Climate.ParameterPoint point))
                        return;
                    if (!(p.getSecond() instanceof ResourceKey<?> key))
                        return;
                    @SuppressWarnings("unchecked")
                    ResourceKey<Biome> biomeKey = (ResourceKey<Biome>) key;
                    biomes.get(biomeKey).ifPresent(holder -> {
                        Holder<Biome> h = holder;
                        collected.add(Pair.of(point, h));
                        distinct.add(h);
                    });
                };
                try {
                    addBiomes.invoke(region, biomeRegistry, sink);
                } catch (Throwable perRegion) {
                    // One bad region must not lose the rest — skip it and carry on.
                }
            }

            if (collected.isEmpty())
                return null;
            return new TerraBlenderBridge(new Climate.ParameterList<>(collected), List.copyOf(distinct));
        } catch (Throwable t) {
            // Any surprise at all — API moved, mod half-loaded — means no bridge, not a broken world.
            return null;
        }
    }

    /**
     * Every biome this bridge can produce.
     *
     * <p><b>These must go into the biome source's {@code possibleBiomes()}.</b> Vanilla filters biome
     * features and drops structure sets against that set, so a biome this can return but that set does
     * not list would generate bare and take its structures with it — the same failure that made ancient
     * cities impossible before the biome map became 3D.
     */
    public List<Holder<Biome>> biomes() {
        return biomes;
    }

    /**
     * The modded biome for a climate point, or {@code null} if none matched.
     *
     * <p>{@code findValue} always returns <em>something</em> when the list is non-empty (it picks the
     * nearest point in seven-dimensional space), so a null here really means "no list".
     */
    public @Nullable Holder<Biome> find(Climate.TargetPoint target) {
        return parameters == null ? null : parameters.findValue(target);
    }
}
