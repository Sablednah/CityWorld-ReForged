package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * A named, configurable list of materials — the mechanism by which a world's palettes (road
 * pavement, ore types, tree woods, …) can be overridden per world.
 *
 * <p>A list holds two kinds of entry: <b>explicit materials</b>, and <b>tag pools</b> resolved from
 * block tags at world time (see {@link MaterialTags}). Both are drawn from by
 * {@link #getRandomMaterial}; the difference is that a tag pool's <em>contents</em> grow with the
 * game while its <em>weight</em> stays where the palette author put it.
 *
 * <p><b>Weight is why tag pools are not simply flattened in.</b> Upstream weights these lists by
 * repetition — a palette naming {@code GRAVEL} five times and {@code DIAMOND_ORE} once means gravel
 * piles are mostly gravel. If a run of six plank constants were replaced by a twelve-block planks
 * tag flattened into the list, wood would silently double its share of every wall in the world, and
 * a modpack adding thirty wood types would drown the palette entirely. So a pool occupies exactly
 * the number of slots the constants it replaced did: the odds of picking "some plank" are unchanged,
 * and only <em>which</em> plank widens. That keeps a 5.0.2 world and a 5.0.3 world recognisably the
 * same city, and keeps a heavily-modded world from looking like a timber yard.
 *
 * <p>Upstream stored Bukkit {@code ItemStack}s rather than materials, only so the config could
 * round-trip item names. Our {@code Material} shim already spans blocks and items, so this holds
 * materials directly.
 */
public class MaterialList {

	/**
	 * A block tag's worth of materials, standing in for {@code weight} slots of the list. Resolved
	 * once, at construction, and already sorted by registry id — {@link MaterialTags#resolve} owns
	 * the determinism guarantee.
	 */
	private record TagPool(List<Material> materials, int weight) {

		/** An unresolvable tag contributes nothing, so the list degrades to its explicit entries. */
		int effectiveWeight() {
			return materials.isEmpty() ? 0 : weight;
		}
	}

	private final String listName;
	private List<Material> items;
	private List<TagPool> pools;

	public MaterialList(String name) {
		super();
		listName = name;
	}

	public MaterialList(String name, Material... materials) {
		super();
		listName = name;
		add(materials);
	}

	public String getListName() {
		return listName;
	}

	private void init(boolean clear) {
		if (items == null)
			items = new ArrayList<>();
		else if (clear)
			items.clear();
	}

	public void add(Material... materials) {
		init(false);
		for (Material material : materials) {
			items.add(material);
		}
	}

	/**
	 * Adds a block tag as a sub-pool occupying {@code weight} slots of this list.
	 *
	 * @param weight how many entries this pool stands in for — pass the number of explicit constants
	 *               it replaced, so the palette's odds do not shift (see the class note)
	 */
	public void addTag(TagKey<Block> tag, int weight) {
		if (pools == null)
			pools = new ArrayList<>();
		pools.add(new TagPool(MaterialTags.resolve(tag), weight));
	}

	/** {@link #addTag} as a fluent call, so a palette can be declared in one field initialiser. */
	public MaterialList withTag(TagKey<Block> tag, int weight) {
		addTag(tag, weight);
		return this;
	}

	public void remove(Material material) {
		if (items != null)
			for (int i = items.size() - 1; i >= 0; i--)
				if (items.get(i) == material)
					items.remove(i);
	}

	/** Total weight of the list — explicit entries plus every pool's slot count. */
	public int count() {
		int total = items == null ? 0 : items.size();
		if (pools != null)
			for (TagPool pool : pools)
				total += pool.effectiveWeight();
		return total;
	}

	public Material getRandomMaterial(Odds odds) {
		return getRandomMaterial(odds, Material.AIR);
	}

	public Material getRandomMaterial(Odds odds, Material defaultMaterial) {
		int total = count();
		if (total == 0)
			return defaultMaterial;

		// One roll picks the slot. Explicit entries come first so that a list with no pools behaves
		// exactly as it did before tags existed — same seed, same sequence, same city.
		int slot = odds.getRandomInt(total);
		int explicit = items == null ? 0 : items.size();
		if (slot < explicit)
			return items.get(slot);

		slot -= explicit;
		for (TagPool pool : pools) {
			int weight = pool.effectiveWeight();
			if (slot < weight)
				// A second roll picks within the pool. Rolling again rather than reusing the slot
				// offset keeps the choice uniform across the pool however few slots it occupies —
				// a weight-1 tag would otherwise only ever yield its first block.
				return pool.materials().get(odds.getRandomInt(pool.materials().size()));
			slot -= weight;
		}
		return defaultMaterial;
	}

	/**
	 * The {@code index}th <em>explicit</em> material, for the handful of lists where order carries
	 * meaning (road pavement/lines/sidewalks, maze walls). Tag pools are deliberately not visible
	 * here: a positional palette wants one specific block per slot, not a set to choose from.
	 */
	public Material getNthMaterial(int index, Material defaultMaterial) {
		if (items == null || items.isEmpty() || index > items.size() - 1)
			return defaultMaterial;
		else
			return items.get(index);
	}
}
